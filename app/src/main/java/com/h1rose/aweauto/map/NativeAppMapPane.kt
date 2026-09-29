package com.h1rose.aweauto.map

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import com.h1rose.aweauto.shizuku.ShizukuState
import com.h1rose.aweauto.shizuku.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

private const val TAG = "NativeAppMapPane"

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
 * Android Auto から渡される Surface を VirtualDisplay にし、Shizuku の shell 権限で
 * `am start --display` と `input -d` を実行して外部アプリをその display へ載せる。
 */
class NativeAppMapPane(
    context: Context,
    private val app: NativeMapApp,
) : MapPaneProvider {
    override val label = app.label

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var virtualDisplay: VirtualDisplay? = null
    private var displayId: Int? = null

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var moved = false

    override fun attach(surface: Surface, width: Int, height: Int, densityDpi: Int) {
        detach()
        val dm = appContext.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay(
            "aweauto-${app.id}-map",
            width,
            height,
            densityDpi,
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
        )
        virtualDisplay = vd
        displayId = vd.display.displayId
        launchMap()
    }

    override fun detach() {
        displayId = null
        virtualDisplay?.release()
        virtualDisplay = null
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
                val id = displayId ?: return
                val duration = (SystemClock.uptimeMillis() - downAt).coerceAtLeast(1L)
                if (moved) {
                    runShell("input -d $id swipe ${downX.i()} ${downY.i()} ${lastX.i()} ${lastY.i()} $duration")
                } else {
                    runShell("input -d $id tap ${x.i()} ${y.i()}")
                }
            }
            MotionEvent.ACTION_CANCEL -> moved = false
        }
    }

    override fun onBack(): Boolean {
        val id = displayId ?: return false
        runShell("input -d $id keyevent BACK")
        return true
    }

    private fun launchMap() {
        val id = displayId ?: return
        if (ShizukuState.status.value != ShizukuStatus.READY) {
            Log.w(TAG, "Shizuku is not ready; cannot launch ${app.packageName} on display $id")
            return
        }
        if (!isInstalled(app.packageName)) {
            Log.w(TAG, "${app.packageName} is not installed")
            return
        }
        val command = "am start --display $id -a android.intent.action.VIEW -d 'geo:0,0?q=現在地' -p ${app.packageName}"
        runShell(command)
    }

    private fun isInstalled(packageName: String): Boolean =
        @Suppress("DEPRECATION")
        runCatching {
            appContext.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)

    private fun runShell(command: String) {
        if (ShizukuState.status.value != ShizukuStatus.READY) return
        scope.launch {
            runCatching {
                val process = shizukuProcess(arrayOf("sh", "-c", command))
                process.inputStream.bufferedReader().use { it.readText() }
                process.errorStream.bufferedReader().use { err ->
                    val text = err.readText()
                    if (text.isNotBlank()) Log.w(TAG, text.trim())
                }
                process.waitFor()
            }.onFailure { Log.w(TAG, "shell failed: $command", it) }
        }
    }

    private fun shizukuProcess(command: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, command, null, null) as Process
    }

    private fun Float.i(): Int = toInt().coerceAtLeast(0)
}
