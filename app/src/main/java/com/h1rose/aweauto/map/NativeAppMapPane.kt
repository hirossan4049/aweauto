package com.h1rose.aweauto.map

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import com.h1rose.aweauto.BuildConfig
import com.h1rose.aweauto.shizuku.ShizukuState
import com.h1rose.aweauto.shizuku.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

private const val TAG = "NativeAppMapPane"
private const val INPUT_BIND_TIMEOUT_MS = 2_000L
private const val MAX_PENDING_INPUTS = 8

enum class NativeMapApp(
    val id: String,
    val label: String,
    val packageName: String,
) {
    Google("google", "Google Maps", "com.google.android.apps.maps"),
    Yahoo("yahoo", "Yahoo! MAP", "jp.co.yahoo.android.apps.map");

    companion object {
        fun fromId(id: String?): NativeMapApp =
            entries.firstOrNull { it.id == id } ?: Google
    }
}

/**
 * 左の地図枠に端末の地図アプリ本体を起動する。
 *
 * Android Auto から渡される Surface を Shizuku UserService へ渡し、shell UID 側で
 * VirtualDisplay の作成・地図アプリ起動・InputForwarder 経由のタッチ注入を行う。
 */
class NativeAppMapPane(
    context: Context,
    private val app: NativeMapApp,
) : MapPaneProvider {
    override val label = app.label

    private val appContext = context.applicationContext
    private val inputScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var inputService: IMapInputService? = null
    private var bindingInputService = false
    private var inputBindStartedAt = 0L
    private var attached = false
    private var pendingAttach: PendingAttach? = null
    private val pendingInputs = ArrayDeque<PendingInput>()

    private val inputConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val connected = IMapInputService.Stub.asInterface(service)
            inputService = connected
            bindingInputService = false
            Log.i(TAG, "input service connected")
            inputScope.launch { attachRemote(connected) }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            inputService = null
            bindingInputService = false
            Log.w(TAG, "input service disconnected")
        }

        override fun onBindingDied(name: ComponentName) {
            inputService = null
            bindingInputService = false
            Log.w(TAG, "input service binding died")
        }

        override fun onNullBinding(name: ComponentName) {
            inputService = null
            bindingInputService = false
            Log.w(TAG, "input service null binding")
        }
    }

    private val staleInputConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) = Unit
        override fun onServiceDisconnected(name: ComponentName) = Unit
    }

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var moved = false

    override fun attach(surface: Surface, width: Int, height: Int, densityDpi: Int) {
        if (!isInstalled(app.packageName)) {
            Log.w(TAG, "${app.packageName} is not installed")
            return
        }
        pendingAttach = PendingAttach(surface, width, height, densityDpi)
        ensureInputService()
        inputService?.let { service -> inputScope.launch { attachRemote(service) } }
    }

    override fun detach() {
        pendingAttach = null
        attached = false
        inputService?.let { service ->
            inputScope.launch {
                if (pendingAttach != null) return@launch
                runCatching { service.detach() }
                    .onFailure { Log.w(TAG, "remote detach failed", it) }
            }
        }
        synchronized(pendingInputs) { pendingInputs.clear() }
    }

    override fun onTouch(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long) {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                lastX = x
                lastY = y
                downAt = SystemClock.uptimeMillis()
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(x - downX) > 8f || kotlin.math.abs(y - downY) > 8f) moved = true
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_UP -> {
                val duration = (SystemClock.uptimeMillis() - downAt).coerceAtLeast(1L)
                injectTouch(moved, x, y, duration)
            }
            MotionEvent.ACTION_CANCEL -> moved = false
        }
    }

    override fun onBack(): Boolean {
        ensureInputService()
        val service = inputService
        if (service == null || !attached) {
            enqueueInput(PendingInput.Back)
            service?.let { inputScope.launch { attachRemote(it) } }
            return true
        }
        inputScope.launch {
            runCatching {
                service.back()
            }.onFailure {
                Log.w(TAG, "back inject failed", it)
                if (it is RemoteException) inputService = null
            }
        }
        return true
    }

    private fun ensureInputService() {
        if (inputService != null) return
        if (bindingInputService) {
            val elapsed = SystemClock.uptimeMillis() - inputBindStartedAt
            if (elapsed < INPUT_BIND_TIMEOUT_MS) return
            Log.w(TAG, "input service bind timed out after ${elapsed}ms; retrying")
            bindingInputService = false
        }
        if (ShizukuState.status.value != ShizukuStatus.READY) {
            Log.w(TAG, "Shizuku is not ready; cannot bind input service")
            return
        }
        bindingInputService = true
        inputBindStartedAt = SystemClock.uptimeMillis()
        runCatching {
            val args = inputServiceArgs()
            Log.i(TAG, "removing stale input service")
            Shizuku.unbindUserService(args, staleInputConnection, true)
            Log.i(TAG, "binding input service")
            Shizuku.bindUserService(args, inputConnection)
        }.onFailure {
            bindingInputService = false
            Log.w(TAG, "failed to bind input service", it)
        }
    }

    private fun inputServiceArgs(): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, NativeMapInputUserService::class.java.name),
        )
            .daemon(false)
            .debuggable(BuildConfig.DEBUG)
            .processNameSuffix("map_input")
            .tag("aweauto_map_input")
            .version(BuildConfig.VERSION_CODE)

    private fun attachRemote(service: IMapInputService) {
        val request = pendingAttach ?: return
        runCatching {
            val id = service.attach(request.surface, request.width, request.height, request.densityDpi, app.packageName)
            attached = true
            Log.i(TAG, "remote map display attached id=$id")
            flushPendingInputs(service)
        }.onFailure {
            attached = false
            Log.w(TAG, "remote attach failed", it)
            if (it is RemoteException) inputService = null
        }
    }

    private fun injectTouch(isSwipe: Boolean, x: Float, y: Float, duration: Long) {
        ensureInputService()
        val service = inputService
        if (service == null || !attached) {
            enqueueInput(
                if (isSwipe) {
                    PendingInput.Swipe(downX, downY, lastX, lastY, duration)
                } else {
                    PendingInput.Tap(x, y)
                },
            )
            service?.let { inputScope.launch { attachRemote(it) } }
            return
        }
        val startX = downX
        val startY = downY
        val endX = lastX
        val endY = lastY
        inputScope.launch {
            runCatching {
                if (isSwipe) {
                    service.swipe(startX, startY, endX, endY, duration)
                } else {
                    service.tap(x, y)
                }
            }.onFailure {
                Log.w(TAG, "touch inject failed", it)
                if (it is RemoteException) inputService = null
            }
        }
    }

    private fun enqueueInput(input: PendingInput) {
        synchronized(pendingInputs) {
            if (pendingInputs.size >= MAX_PENDING_INPUTS) pendingInputs.removeFirst()
            pendingInputs.addLast(input)
        }
        Log.w(TAG, "input service is not connected; queued ${input.name}")
    }

    private fun flushPendingInputs(service: IMapInputService) {
        while (true) {
            val input = synchronized(pendingInputs) {
                if (pendingInputs.isEmpty()) null else pendingInputs.removeFirst()
            } ?: return
            runCatching {
                input.send(service)
            }.onFailure {
                Log.w(TAG, "pending ${input.name} inject failed", it)
                if (it is RemoteException) {
                    inputService = null
                    synchronized(pendingInputs) { pendingInputs.addFirst(input) }
                    return
                }
            }
        }
    }

    private fun isInstalled(packageName: String): Boolean =
        @Suppress("DEPRECATION")
        runCatching {
            appContext.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)

    private data class PendingAttach(
        val surface: Surface,
        val width: Int,
        val height: Int,
        val densityDpi: Int,
    )

    private sealed class PendingInput(val name: String) {
        abstract fun send(service: IMapInputService)

        data class Tap(val x: Float, val y: Float) : PendingInput("tap") {
            override fun send(service: IMapInputService) = service.tap(x, y)
        }

        data class Swipe(
            val startX: Float,
            val startY: Float,
            val endX: Float,
            val endY: Float,
            val durationMs: Long,
        ) : PendingInput("swipe") {
            override fun send(service: IMapInputService) =
                service.swipe(startX, startY, endX, endY, durationMs)
        }

        data object Back : PendingInput("back") {
            override fun send(service: IMapInputService) = service.back()
        }
    }
}
