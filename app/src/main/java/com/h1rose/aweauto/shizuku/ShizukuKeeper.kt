package com.h1rose.aweauto.shizuku

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import com.h1rose.aweauto.R
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "AweShizukuKeeper"
private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
private const val ADB_PORT = 5555

data class KeeperStatus(
    val lastResult: String? = null,
    val lastAttemptAt: Long = 0,
    val running: Boolean = false,
)

/**
 * 車につないだときなどに Shizuku が止まったら、スマホ内の adb (127.0.0.1:5555) につないで起動し直す。
 *
 * Android 9 の Shizuku は adb からしか起動できず、USB のモードが Android Auto 用に切り替わると
 * adb ごと止まることがある。PC で一度 `adb tcpip 5555` を実行しておけば (再起動まで有効)、
 * 以後はスマホ単体で起動し直せる。初回だけスマホに「USB デバッグを許可しますか」が出るので「常に許可」する。
 */
object ShizukuKeeper {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private lateinit var keyDir: File
    private lateinit var appContext: Context

    private val _status = MutableStateFlow(KeeperStatus())
    val status: StateFlow<KeeperStatus> = _status.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        keyDir = File(context.filesDir, "adb")
    }

    /** 止まっていたら起動し直す。すでに試している最中なら何もしない */
    fun ensureRunning() {
        if (!::keyDir.isInitialized || job?.isActive == true) return
        if (ShizukuState.status.value != ShizukuStatus.NOT_RUNNING) return
        job = scope.launch { restart() }
    }

    /** 設定画面の「今すぐ起動」 */
    fun restartNow() {
        if (!::keyDir.isInitialized || job?.isActive == true) return
        job = scope.launch { restart() }
    }

    private suspend fun restart() {
        _status.value = _status.value.copy(running = true, lastAttemptAt = System.currentTimeMillis())
        val result = runCatching {
            Dadb.create("127.0.0.1", ADB_PORT, keyPair(), connectTimeout = 3_000, socketTimeout = 15_000).use { adb ->
                val dir = adb.shell("pm path $SHIZUKU_PKG").output.trim()
                    .removePrefix("package:").substringBeforeLast("/base.apk")
                if (dir.isEmpty()) throw RestartError(R.string.shizuku_not_installed)
                val res = adb.shell("$dir/lib/${abiDir()}/libshizuku.so")
                Log.i(TAG, res.allOutput.trim())
                if (res.exitCode != 0) throw RestartError(R.string.shizuku_start_failed, res.exitCode)
            }
        }
        // Shizuku のサーバーが立ち上がってアプリに通知されるまで少し待つ
        delay(2_000)
        ShizukuState.refresh()
        val message = result.fold(
            onSuccess = { appContext.getString(R.string.shizuku_started) },
            onFailure = {
                Log.w(TAG, "restart failed", it)
                when (it) {
                    is RestartError -> appContext.getString(it.reason, *it.args)
                    is java.net.ConnectException -> appContext.getString(R.string.shizuku_no_adb)
                    else -> it.message ?: it.javaClass.simpleName
                }
            },
        )
        _status.value = _status.value.copy(running = false, lastResult = message)
    }

    /** 設定画面に出す、起動し直せなかった理由 */
    private class RestartError(@StringRes val reason: Int, vararg val args: Any) : Exception()

    private fun keyPair(): AdbKeyPair {
        val private = File(keyDir, "adbkey")
        val public = File(keyDir, "adbkey.pub")
        if (!private.exists()) AdbKeyPair.generate(private, public)
        return AdbKeyPair.read(private, public)
    }

    private fun abiDir(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            abi.startsWith("arm64") -> "arm64"
            abi.startsWith("armeabi") -> "arm"
            abi == "x86_64" -> "x86_64"
            else -> "x86"
        }
    }
}
