package com.h1rose.aweauto.car

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View

/**
 * Car App Library は Surface へのタッチを「タップ座標」と「スクロール量」でしか渡してくれない。
 * それを MotionEvent に組み立て直して Presentation の View ツリーに流す。
 */
class TouchInjector(private val target: () -> View?) {
    private val handler = Handler(Looper.getMainLooper())
    private val endDrag = Runnable { finishDrag() }

    private var dragging = false
    private var downTime = 0L
    private var x = 0f
    private var y = 0f

    /** onScroll には座標が無いので、直前にタップした位置をドラッグの起点にする */
    private var anchorX = -1f
    private var anchorY = -1f

    fun click(px: Float, py: Float) {
        finishDrag()
        anchorX = px
        anchorY = py
        val t = SystemClock.uptimeMillis()
        send(t, t, MotionEvent.ACTION_DOWN, px, py)
        send(t, t + 40, MotionEvent.ACTION_UP, px, py)
    }

    /** distance は GestureDetector と同じく「前回位置 - 今回位置」 */
    fun scroll(distanceX: Float, distanceY: Float) {
        val view = target() ?: return
        if (!dragging) {
            dragging = true
            downTime = SystemClock.uptimeMillis()
            x = if (anchorX >= 0) anchorX else view.width / 2f
            y = if (anchorY >= 0) anchorY else view.height / 2f
            send(downTime, downTime, MotionEvent.ACTION_DOWN, x, y)
        }
        x = (x - distanceX).coerceIn(0f, view.width - 1f)
        y = (y - distanceY).coerceIn(0f, view.height - 1f)
        send(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, y)
        handler.removeCallbacks(endDrag)
        handler.postDelayed(endDrag, 120)
    }

    private fun finishDrag() {
        handler.removeCallbacks(endDrag)
        if (!dragging) return
        dragging = false
        send(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y)
    }

    private fun send(down: Long, time: Long, action: Int, px: Float, py: Float) {
        val view = target() ?: return
        val ev = MotionEvent.obtain(down, time, action, px, py, 0)
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        view.dispatchTouchEvent(ev)
        ev.recycle()
    }
}
