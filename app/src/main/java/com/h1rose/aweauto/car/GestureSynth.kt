package com.h1rose.aweauto.car

import kotlin.math.abs

/** 組み立てたタッチの 1 イベント。時刻は SystemClock.uptimeMillis と同じ基準 */
data class TouchEvent(val action: Action, val x: Float, val y: Float, val downTime: Long, val eventTime: Long) {
    enum class Action { DOWN, MOVE, UP }
}

/** 画面の中の長方形 (左上と右下)。区画の範囲に使う */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

/**
 * Android Auto の「タップ位置」「スクロール量」「フリングの速さ」から、指 1 本のタッチの流れを組み立てる。
 *
 * Android Auto はスクロールの開始位置を教えてくれないので、最後にタップした位置からドラッグを始める
 * (タップした区画・行・棚がそのままスクロールする)。ただしホームや設定 (Compose の画面) の縦スクロールは、
 * 縦だけ画面の中央から始める。上のタブや見出しはリストの外にあり、そこからドラッグしてもリストが動かないため。
 * 指が区画の端に着いたら、区画の中央で持ち直して続ける (そうしないと少ししかスクロールできない)。
 *
 * View も時刻も使わないので、JVM のテストで確かめられる。実際の送信とタイマーは [TouchInjector] が受け持つ。
 */
class GestureSynth(private val clock: () -> Long) {
    private var lastTap: Pair<Float, Float>? = null

    private var dragging = false
    private var downTime = 0L
    private var lastMoveTime = 0L
    private var x = 0f
    private var y = 0f
    private var pane = Box(0f, 0f, 0f, 0f)

    /** 指を止めただけで離していないかもしれないので、すぐ続きが来たらこの位置から続ける */
    private var resumeAt: Triple<Float, Float, Long>? = null

    val isDragging get() = dragging

    /** ドラッグを始める位置の目安 (区画を決めるのに使う)。タップしていなければ null */
    fun anchor(): Pair<Float, Float>? = resumeAt?.let { it.first to it.second } ?: lastTap

    /** タップ。押してから [TAP_MS] 後に離す */
    fun tap(px: Float, py: Float): List<TouchEvent> {
        val events = release()
        lastTap = px to py
        resumeAt = null
        val t = clock()
        return events + listOf(
            TouchEvent(TouchEvent.Action.DOWN, px, py, t, t),
            TouchEvent(TouchEvent.Action.UP, px, py, t, t + TAP_MS),
        )
    }

    /**
     * スクロール。distance は GestureDetector と同じく「前回位置 - 今回位置」(指の動きと逆向き)。
     * [area] はドラッグを始める位置を含む区画 (WebView や地図枠など)。最初の 1 回だけ使う。
     * [wholeScreen] は区画が見つからず画面全体 (Compose の画面) のとき
     */
    fun scroll(distanceX: Float, distanceY: Float, area: Box, wholeScreen: Boolean = false): List<TouchEvent> {
        val now = clock()
        val events = mutableListOf<TouchEvent>()
        if (!dragging) {
            pane = area
            val resume = resumeAt?.takeIf { now - it.third <= RESUME_MS && area.contains(it.first, it.second) }
            val vertical = abs(distanceY) >= abs(distanceX)
            val start = resume?.let { it.first to it.second }
                ?: lastTap?.takeIf { area.contains(it.first, it.second) }
                    ?.let { (tx, ty) -> if (wholeScreen && vertical) tx to area.centerY else tx to ty }
                ?: (area.centerX to area.centerY)
            events += press(start.first, start.second, now)
        }
        resumeAt = null

        var nx = x - distanceX
        var ny = y - distanceY
        // 区画の端を越えるなら、そこで離して区画の中央で持ち直す (動いている向きだけ中央に戻す)
        if (!inside(nx, ny)) {
            events += TouchEvent(TouchEvent.Action.UP, x, y, downTime, now)
            val gx = if (nx < pane.left + EDGE || nx > pane.right - EDGE) pane.centerX else x
            val gy = if (ny < pane.top + EDGE || ny > pane.bottom - EDGE) pane.centerY else y
            events += press(gx, gy, now)
            nx = (gx - distanceX).coerceIn(pane.left + EDGE, pane.right - EDGE)
            ny = (gy - distanceY).coerceIn(pane.top + EDGE, pane.bottom - EDGE)
        }
        x = nx
        y = ny
        lastMoveTime = now
        events += TouchEvent(TouchEvent.Action.MOVE, x, y, downTime, now)
        return events
    }

    /**
     * 指を勢いよく離した。速さ (px/秒、指の向き) に合う分だけ 1 フレーム先の位置で離すので、
     * 各 View が慣性スクロールを付けられる
     */
    fun fling(velocityX: Float, velocityY: Float): List<TouchEvent> {
        if (!dragging) return emptyList()
        dragging = false
        resumeAt = null
        // 各 View は直近の数フレームの位置と時刻から速さを出すので、その速さで動く指を数フレーム分作り、
        // 最後の位置で離す。時刻は送る側 (TouchInjector) が実際に送るときに付け直す
        val events = mutableListOf<TouchEvent>()
        var t = lastMoveTime
        // 区画の端に着いて動けなくなると速さが落ちるので、足りなければ先に持ち直す
        val travelX = velocityX * FRAME_MS * FLING_FRAMES / 1000f
        val travelY = velocityY * FRAME_MS * FLING_FRAMES / 1000f
        if (!inside(x + travelX, y + travelY)) {
            events += TouchEvent(TouchEvent.Action.UP, x, y, downTime, t)
            val gx = if (inside(x + travelX, y)) x else pane.centerX - travelX / 2
            val gy = if (inside(x, y + travelY)) y else pane.centerY - travelY / 2
            events += press(gx, gy, t)
            dragging = false
        }
        repeat(FLING_FRAMES) {
            t += FRAME_MS
            x = (x + velocityX * FRAME_MS / 1000f).coerceIn(pane.left, pane.right)
            y = (y + velocityY * FRAME_MS / 1000f).coerceIn(pane.top, pane.bottom)
            events += TouchEvent(TouchEvent.Action.MOVE, x, y, downTime, t)
        }
        events += TouchEvent(TouchEvent.Action.UP, x, y, downTime, t)
        return events
    }

    /**
     * スクロールがしばらく来なかった。止まってから離したことになるので慣性は付かない。
     * すぐに続きが来たら、同じ位置から押し直して続ける
     */
    fun release(): List<TouchEvent> {
        if (!dragging) return emptyList()
        dragging = false
        val now = clock()
        resumeAt = Triple(x, y, now)
        return listOf(TouchEvent(TouchEvent.Action.UP, x, y, downTime, now))
    }

    private fun press(px: Float, py: Float, now: Long): TouchEvent {
        dragging = true
        downTime = now
        x = px.coerceIn(pane.left + EDGE, pane.right - EDGE)
        y = py.coerceIn(pane.top + EDGE, pane.bottom - EDGE)
        return TouchEvent(TouchEvent.Action.DOWN, x, y, now, now)
    }

    private fun inside(px: Float, py: Float) =
        px >= pane.left + EDGE && px <= pane.right - EDGE && py >= pane.top + EDGE && py <= pane.bottom - EDGE

    companion object {
        /** タップの押してから離すまで */
        const val TAP_MS = 60L

        /** この間スクロールが来なければ指を離したとみなす */
        const val IDLE_MS = 250L

        /** 指を止めてから、この間に続きが来たら同じ位置から続ける */
        const val RESUME_MS = 1_000L

        const val FRAME_MS = 16L

        /** フリングで足すフレームの数 */
        const val FLING_FRAMES = 3

        /** 区画の端からこれだけ内側で持ち直す (端ぎりぎりはスクロールの外側のことがある) */
        private const val EDGE = 8f
    }
}
