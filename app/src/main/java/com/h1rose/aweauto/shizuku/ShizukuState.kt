package com.h1rose.aweauto.shizuku

import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class ShizukuStatus { NOT_RUNNING, NO_PERMISSION, READY }

/** Shizuku (adb の権限を貸してくれるアプリ) とのつながり。設定画面に状態を出す */
object ShizukuState {
    private const val REQUEST_CODE = 4201

    private val _status = MutableStateFlow(ShizukuStatus.NOT_RUNNING)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    /** 止まったら自動で起動し直すか (設定) */
    @Volatile
    var autoRestart = true

    fun init() {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            refresh()
            // 車につないだときなどに止まったら、スマホ内の adb から起動し直す
            if (autoRestart) ShizukuKeeper.ensureRunning()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        refresh()
    }

    fun refresh() {
        _status.value = when {
            !Shizuku.pingBinder() -> ShizukuStatus.NOT_RUNNING
            Shizuku.isPreV11() -> ShizukuStatus.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuStatus.NO_PERMISSION
            else -> ShizukuStatus.READY
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) Shizuku.requestPermission(REQUEST_CODE)
    }

    /** Android 9 では再起動のたびに PC から実行するコマンド */
    const val START_COMMAND =
        "adb shell \"\$(adb shell pm path moe.shizuku.privileged.api | sed 's/package://;s#/base.apk##')/lib/arm64/libshizuku.so\""
}
