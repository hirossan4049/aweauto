package com.h1rose.aweauto.map

import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「地図＋動画」モードの左側 (地図枠) の中身。
 *
 * aweauto は地図枠の描画先 [Surface] とタッチを渡すだけで、何を描くかは実装側に任せる。
 * 車の画面は Android Auto 側の都合 (別の画面に切り替え・バックカメラなど) や、並べ方の切り替え
 * (左右分割 ⇄ PiP・入れ替え・幅の変更) で [attach] / [detach] が何度も呼ばれる。
 *
 * 中身の状態は detach されても保持すること。特に他アプリを仮想ディスプレイに出す実装では、
 * - [detach] では仮想ディスプレイを release せず、`VirtualDisplay.setSurface(null)` で外すだけにする
 * - 2 回目以降の [attach] では作り直さず、`resize(width, height, densityDpi)` と `setSurface(surface)` で差し替える
 * ディスプレイを作り直すと、その上のアプリは表示先を失って落ちる (Google マップは
 * `Display.getDisplayAdjustments()` の NullPointerException で落ちることを確認済み)。
 * すべてメインスレッドから呼ばれる。
 */
interface MapPaneProvider {
    /** 表示名 (設定画面に出す) */
    val label: String

    /**
     * 地図枠が表示された / サイズが変わった。surface はこの大きさ (px) で描く。
     * 並べ方の切り替え中は呼ばれず、大きさが落ち着いてから 1 回だけ呼ばれる。
     */
    fun attach(surface: Surface, width: Int, height: Int, densityDpi: Int)

    /** 地図枠が隠れた。surface はもう使えない */
    fun detach()

    /**
     * 地図枠へのタッチ。座標は地図枠の左上を原点とした px。
     * action は MotionEvent.ACTION_DOWN / ACTION_MOVE / ACTION_UP / ACTION_CANCEL
     */
    fun onTouch(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long)

    /** 車の「戻る」を地図側で処理したら true */
    fun onBack(): Boolean = false
}

/** 使う [MapPaneProvider] を登録する場所。未登録なら地図枠には案内だけを出す */
object MapPanes {
    private val _provider = MutableStateFlow<MapPaneProvider?>(null)
    val provider: StateFlow<MapPaneProvider?> = _provider.asStateFlow()

    fun register(provider: MapPaneProvider?) {
        _provider.value?.detach()
        _provider.value = provider
    }
}
