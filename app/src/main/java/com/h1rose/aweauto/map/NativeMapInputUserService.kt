package com.h1rose.aweauto.map

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import java.lang.reflect.Method

private const val INPUT_TAG = "NativeMapInput"

/**
 * Shizuku UserService として shell UID で動く入力注入サービス。
 *
 * Android 9 の InputEvent は displayId を持たないため、通常の InputManager.injectInputEvent()
 * は使わない。UserService 側で VirtualDisplay を作り、同じ UID のまま createInputForwarder()
 * した Binder に注入して、displayId を system_server 側で指定させる。
 */
class NativeMapInputUserService : IMapInputService.Stub {
    private val context: Context?

    constructor() : super() {
        context = null
        Log.i(INPUT_TAG, "input user service created without context")
    }

    constructor(context: Context) : super() {
        this.context = context
        Log.i(INPUT_TAG, "input user service created with context")
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var displayId: Int? = null
    private var inputForwarder: Any? = null
    private var forwarderMethod: Method? = null
    private var forwarderUsesMode = false
    private var attachedPackageName: String? = null

    override fun attach(surface: Surface, width: Int, height: Int, densityDpi: Int, mapPackageName: String): Int {
        val ctx = context ?: error("UserService context is not available")
        val vd = virtualDisplay
        val id = if (vd == null) {
            val displayContext = ctx.shellPackageContext()
            val created = displayContext.getSystemService(DisplayManager::class.java).createVirtualDisplay(
                "aweauto-native-map",
                width,
                height,
                densityDpi,
                surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
            )
            virtualDisplay = created
            val createdId = created.display.displayId
            displayId = createdId
            createInputForwarder(createdId)
            launchMap(createdId, mapPackageName)
            attachedPackageName = mapPackageName
            createdId
        } else {
            vd.resize(width, height, densityDpi)
            vd.setSurface(surface)
            val existingId = vd.display.displayId
            displayId = existingId
            if (inputForwarder == null || forwarderMethod == null) createInputForwarder(existingId)
            if (attachedPackageName != mapPackageName) {
                launchMap(existingId, mapPackageName)
                attachedPackageName = mapPackageName
            }
            existingId
        }
        Log.i(INPUT_TAG, "attached virtual display id=$id ${width}x$height dpi=$densityDpi package=$mapPackageName reused=${vd != null}")
        return id
    }

    override fun detach() {
        virtualDisplay?.setSurface(null)
        Log.i(INPUT_TAG, "detached surface display=$displayId")
    }

    override fun destroy() {
        releaseDisplay()
        Log.i(INPUT_TAG, "input user service destroyed")
        System.exit(0)
    }

    override fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        injectMotion(MotionEvent.ACTION_DOWN, down, down, x, y)
        injectMotion(MotionEvent.ACTION_UP, down, down + 40, x, y)
    }

    override fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long) {
        val duration = durationMs.coerceAtLeast(1L)
        val down = SystemClock.uptimeMillis()
        injectMotion(MotionEvent.ACTION_DOWN, down, down, startX, startY)

        val steps = (duration / 16L).coerceIn(1L, 24L).toInt()
        for (step in 1 until steps) {
            val f = step.toFloat() / steps
            injectMotion(
                action = MotionEvent.ACTION_MOVE,
                downTime = down,
                eventTime = down + (duration * f).toLong(),
                x = startX + (endX - startX) * f,
                y = startY + (endY - startY) * f,
            )
        }
        injectMotion(MotionEvent.ACTION_UP, down, down + duration, endX, endY)
    }

    override fun back() {
        val down = SystemClock.uptimeMillis()
        injectKey(KeyEvent.ACTION_DOWN, down, down)
        injectKey(KeyEvent.ACTION_UP, down, down + 40)
    }

    private fun createInputForwarder(displayId: Int) {
        runCatching {
            val inputBinder = Class.forName("android.os.ServiceManager")
                .getDeclaredMethod("getService", String::class.java)
                .invoke(null, "input") as IBinder
            val inputManager = Class.forName("android.hardware.input.IInputManager\$Stub")
                .getDeclaredMethod("asInterface", IBinder::class.java)
                .invoke(null, inputBinder)
            val forwarder = inputManager.javaClass
                .getMethod("createInputForwarder", Int::class.javaPrimitiveType)
                .invoke(inputManager, displayId)
            inputForwarder = forwarder
            val method = runCatching {
                forwarder.javaClass.getMethod("forwardEvent", InputEvent::class.java)
            }.getOrElse {
                forwarderUsesMode = true
                forwarder.javaClass.getMethod(
                    "injectInputEvent",
                    InputEvent::class.java,
                    Int::class.javaPrimitiveType,
                )
            }
            forwarderMethod = method
            Log.i(INPUT_TAG, "created input forwarder for display=$displayId method=${method.name}")
        }.onFailure {
            inputForwarder = null
            forwarderMethod = null
            forwarderUsesMode = false
            Log.w(INPUT_TAG, "createInputForwarder($displayId) failed; input will not be injected", it)
        }
    }

    private fun launchMap(displayId: Int, packageName: String) {
        runCatching {
            Runtime.getRuntime().exec(
                arrayOf(
                    "sh",
                    "-c",
                    // 検索語などは渡さず、ランチャーから開いたときと同じように前回の状態で開く。
                    // -p だけだと解決できないアプリ (Waze など) があるので、ランチャーの画面を名前で指定する
                    "am start --display $displayId -a android.intent.action.MAIN " +
                        "-c android.intent.category.LAUNCHER " +
                        (launcherComponent(packageName)?.let { "-n '$it'" } ?: "-p $packageName"),
                ),
            ).apply {
                errorStream.bufferedReader().use {
                    val text = it.readText()
                    if (text.isNotBlank()) Log.w(INPUT_TAG, text.trim())
                }
                waitFor()
            }
        }.onFailure {
            Log.w(INPUT_TAG, "launch map failed: $packageName display=$displayId", it)
        }
    }

    private fun launcherComponent(packageName: String): String? =
        runCatching {
            context?.packageManager?.getLaunchIntentForPackage(packageName)?.component?.flattenToShortString()
        }.getOrNull()

    private fun injectMotion(action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        inject(event)
    }

    private fun injectKey(action: Int, downTime: Long, eventTime: Long) {
        val event = KeyEvent(downTime, eventTime, action, KeyEvent.KEYCODE_BACK, 0).apply {
            source = InputDevice.SOURCE_KEYBOARD
        }
        inject(event)
    }

    private fun inject(event: InputEvent) {
        val id = displayId
        val forwarder = inputForwarder
        val method = forwarderMethod
        if (id == null || forwarder == null || method == null) {
            Log.w(INPUT_TAG, "no input forwarder; skip ${event.javaClass.simpleName}")
            event.recycleIfNeeded()
            return
        }
        runCatching {
            val result = if (forwarderUsesMode) {
                method.invoke(forwarder, event, 2)
            } else {
                method.invoke(forwarder, event)
            }
            Log.i(INPUT_TAG, "forward ${event.javaClass.simpleName} display=$id result=$result")
        }.onFailure {
            Log.w(INPUT_TAG, "forward input failed display=$id", it)
        }.also {
            event.recycleIfNeeded()
        }
    }

    private fun InputEvent.recycleIfNeeded() {
        if (this is MotionEvent) recycle()
    }

    private fun releaseDisplay() {
        inputForwarder = null
        forwarderMethod = null
        forwarderUsesMode = false
        attachedPackageName = null
        displayId = null
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun Context.shellPackageContext(): Context =
        runCatching {
            createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY).also {
                Log.i(INPUT_TAG, "using display context package=${it.packageName}")
            }
        }.getOrElse {
            Log.w(INPUT_TAG, "failed to create com.android.shell context", it)
            this
        }
}
