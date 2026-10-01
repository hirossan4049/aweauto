package com.h1rose.aweauto.car

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView

/**
 * Car App Library は Surface へのタッチを「タップ位置」「スクロール量」「フリングの速さ」でしか渡してくれない。
 * [GestureSynth] でそれを指 1 本のタッチの流れに組み立て直し、Presentation の View ツリーに流す。
 */
class TouchInjector(private val target: () -> View?) {
    private val handler = Handler(Looper.getMainLooper())
    private val synth = GestureSynth(SystemClock::uptimeMillis)
    private val release = Runnable { dispatch(synth.release()) }

    fun click(px: Float, py: Float) {
        handler.removeCallbacks(release)
        val events = synth.tap(px, py)
        // 押す・離すを同じ瞬間に送ると、WebView などがタップとして受け取らないことがあるので、離すのは少し後
        dispatch(events.dropLast(1))
        val up = events.last()
        handler.postDelayed({ dispatch(listOf(up.copy(eventTime = SystemClock.uptimeMillis()))) }, GestureSynth.TAP_MS)
    }

    /** distance は GestureDetector と同じく「前回位置 - 今回位置」 */
    fun scroll(distanceX: Float, distanceY: Float) {
        val root = target() ?: return
        val area = if (synth.isDragging) null else paneAt(root, synth.anchor())
        dispatch(synth.scroll(distanceX, distanceY, area ?: Box(0f, 0f, 0f, 0f)))
        handler.removeCallbacks(release)
        handler.postDelayed(release, GestureSynth.IDLE_MS)
    }

    /** velocity は指の動く向き (px/秒) */
    fun fling(velocityX: Float, velocityY: Float) {
        handler.removeCallbacks(release)
        dispatch(synth.fling(velocityX, velocityY))
    }

    private fun dispatch(events: List<TouchEvent>) {
        val view = target() ?: return
        events.forEach { e ->
            val ev = MotionEvent.obtain(
                e.downTime, e.eventTime, e.action.toMotion(), 1,
                arrayOf(FINGER),
                arrayOf(MotionEvent.PointerCoords().apply { x = e.x; y = e.y; pressure = 1f; size = 1f }),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
            )
            view.dispatchTouchEvent(ev)
            ev.recycle()
        }
    }

    private fun TouchEvent.Action.toMotion() = when (this) {
        TouchEvent.Action.DOWN -> MotionEvent.ACTION_DOWN
        TouchEvent.Action.MOVE -> MotionEvent.ACTION_MOVE
        TouchEvent.Action.UP -> MotionEvent.ACTION_UP
    }

    companion object {
        /** 指 1 本 (WebView のページには pointerType: touch として届く) */
        private val FINGER = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }

        /**
         * ドラッグを始める位置を含む区画。Web 画面 (WebView) や地図枠 (TextureView) の中ならその範囲、
         * それ以外 (ホームや設定などの Compose の画面) なら画面全体。端に着いたらこの中で持ち直す
         */
        internal fun paneAt(root: View, point: Pair<Float, Float>?): Box {
            val whole = Box(0f, 0f, root.width.toFloat(), root.height.toFloat())
            val (px, py) = point ?: return whole
            return findPane(root, px, py) ?: whole
        }

        private fun findPane(view: View, px: Float, py: Float): Box? {
            if (view.visibility != View.VISIBLE) return null
            val loc = IntArray(2).also(view::getLocationInWindow)
            val box = Box(loc[0].toFloat(), loc[1].toFloat(), (loc[0] + view.width).toFloat(), (loc[1] + view.height).toFloat())
            if (!box.contains(px, py)) return null
            if (view is WebView || view is TextureView) return box
            if (view is ViewGroup) {
                // 手前に描かれている子から探す
                for (i in view.childCount - 1 downTo 0) findPane(view.getChildAt(i), px, py)?.let { return it }
            }
            return null
        }
    }
}
