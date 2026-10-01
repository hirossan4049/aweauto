package com.h1rose.aweauto.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.h1rose.aweauto.car.CarDisplayHost

/**
 * 開発用: Android Auto と同じ形 (タップ・スクロール量・フリングの速さ) のタッチを車の画面に送る。
 * DHU からはスクロールを送れないので、タッチの確認に使う。
 *
 *   adb shell am broadcast -a com.h1rose.aweauto.debug.TOUCH --es cmd tap --ef x 300 --ef y 200
 *   adb shell am broadcast -a com.h1rose.aweauto.debug.TOUCH --es cmd scroll --ef dx 0 --ef dy 30 --ei count 20 --ei interval 16
 *   (scroll に --ef vx / --ef vy を付けると最後にフリングする)
 */
class TouchDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val touch = CarDisplayHost.touch
        if (touch == null) {
            Log.w(TAG, "car screen is not shown")
            return
        }
        val handler = Handler(Looper.getMainLooper())
        when (intent.getStringExtra("cmd")) {
            "tap" -> touch.click(intent.getFloatExtra("x", 0f), intent.getFloatExtra("y", 0f))
            "scroll" -> {
                val dx = intent.getFloatExtra("dx", 0f)
                val dy = intent.getFloatExtra("dy", 0f)
                val count = intent.getIntExtra("count", 10)
                val interval = intent.getIntExtra("interval", 16).toLong()
                repeat(count) { i -> handler.postDelayed({ touch.scroll(dx, dy) }, i * interval) }
                if (intent.hasExtra("vx") || intent.hasExtra("vy")) {
                    handler.postDelayed(
                        { touch.fling(intent.getFloatExtra("vx", 0f), intent.getFloatExtra("vy", 0f)) },
                        (count - 1) * interval + 1,
                    )
                }
            }
            else -> Log.w(TAG, "unknown cmd ${intent.getStringExtra("cmd")}")
        }
    }

    private companion object {
        const val TAG = "AweTouchDebug"
    }
}
