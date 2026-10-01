package com.h1rose.aweauto.car

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
 * (タップした区画・行・棚がそのままスクロールする)。指が区画の端に着いたら、区画の中央で持ち直して続ける
 * (そうしないと、上の方をタップした直後は少ししかスクロールできない)。
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
     * [area] はドラッグを始める位置を含む区画 (WebView や地図枠など)。最初の 1 回だけ使う
     */
    fun scroll(distanceX: Float, distanceY: Float, area: Box): List<TouchEvent> {
        val now = clock()
        val events = mutableListOf<TouchEvent>()
        if (!dragging) {
            pane = area
            val resume = resumeAt?.takeIf { now - it.third <= RESUME_MS && area.contains(it.first, it.second) }
            val start = resume?.let { it.first to it.second }
                ?: lastTap?.takeIf { area.contains(it.first, it.second) }
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
     * 指を勢いよく離した。速さ (px/秒、指の向き) に合う動きを 1 フレーム分足してから離すので、
     * 各 View が慣性スクロールを付けられる
     */
    fun fling(velocityX: Float, velocityY: Float): List<TouchEvent> {
        if (!dragging) return emptyList()
        val t = maxOf(clock(), lastMoveTime + FRAME_MS)
        val dt = (t - lastMoveTime) / 1000f
        val fx = (x + velocityX * dt).coerceIn(pane.left, pane.right)
        val fy = (y + velocityY * dt).coerceIn(pane.top, pane.bottom)
        dragging = false
        resumeAt = null
        return listOf(
            TouchEvent(TouchEvent.Action.MOVE, fx, fy, downTime, t),
            TouchEvent(TouchEvent.Action.UP, fx, fy, downTime, t),
        )
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

        private const val FRAME_MS = 16L

        /** 区画の端からこれだけ内側で持ち直す (端ぎりぎりはスクロールの外側のことがある) */
        private const val EDGE = 8f
    }
}
