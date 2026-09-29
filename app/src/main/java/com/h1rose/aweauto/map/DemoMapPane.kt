package com.h1rose.aweauto.map

import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.Surface

/**
 * 地図枠の配線を確かめるためのテスト用の中身 (debug ビルドの設定から ON にする)。
 * 格子と大きさを描き、触った位置に点を打つ。
 */
class DemoMapPane : MapPaneProvider {
    override val label = "テスト表示"

    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var touch: Pair<Float, Float>? = null
    private var pressed = false

    override fun attach(surface: Surface, width: Int, height: Int, densityDpi: Int) {
        this.surface = surface
        this.width = width
        this.height = height
        draw()
    }

    override fun detach() {
        surface = null
    }

    override fun onTouch(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long) {
        touch = x to y
        pressed = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE
        draw()
    }

    private fun draw() {
        val s = surface?.takeIf { it.isValid } ?: return
        val canvas = s.lockCanvas(null)
        try {
            canvas.drawColor(Color.rgb(0x1B, 0x1D, 0x24))
            val grid = Paint().apply { color = Color.rgb(0x2A, 0x2D, 0x36); strokeWidth = 2f }
            val step = 80
            for (x in 0..width step step) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), grid)
            for (y in 0..height step step) canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), grid)
            val text = Paint().apply { color = Color.rgb(0xE3, 0xE3, 0xE8); textSize = 32f; isAntiAlias = true }
            canvas.drawText("MapPaneProvider テスト  ${width}×$height", 24f, 56f, text)
            touch?.let { (x, y) ->
                val dot = Paint().apply {
                    color = if (pressed) Color.rgb(0x8A, 0xB4, 0xF8) else Color.rgb(0x9A, 0xA0, 0xAC)
                    isAntiAlias = true
                }
                canvas.drawCircle(x, y, 28f, dot)
                canvas.drawText("(${x.toInt()}, ${y.toInt()})", x + 36f, y + 12f, text)
            }
        } finally {
            s.unlockCanvasAndPost(canvas)
        }
    }
}
