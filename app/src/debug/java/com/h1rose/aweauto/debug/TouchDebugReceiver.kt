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
        if (intent.getStringExtra("cmd") == "sample") {
            sampleMainThread(intent.getIntExtra("seconds", 5))
            return
        }
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

    /**
     * メインスレッドが何をしているかを、5ms ごとにスタックを見て数える (端末にプロファイラが無いため)。
     * 結果は logcat の AweSample に出る。scripts/aw.sh sample [秒]
     */
    private fun sampleMainThread(seconds: Int) {
        val main = Looper.getMainLooper().thread
        Thread {
            val counts = HashMap<String, Int>()
            var total = 0
            var idle = 0
            val end = android.os.SystemClock.uptimeMillis() + seconds * 1000L
            while (android.os.SystemClock.uptimeMillis() < end) {
                val stack = main.stackTrace
                total++
                // 待ちの状態 (MessageQueue.nativePollOnce) は数えない
                if (stack.firstOrNull()?.methodName == "nativePollOnce") idle++
                else stack.take(25).map { "${it.className}.${it.methodName}" }.distinct().forEach { counts[it] = (counts[it] ?: 0) + 1 }
                Thread.sleep(5)
            }
            Log.i("AweSample", "samples=$total busy=${total - idle} (${100 * (total - idle) / maxOf(total, 1)}%)")
            counts.entries.sortedByDescending { it.value }.take(40).forEach { Log.i("AweSample", "${it.value} ${it.key}") }
        }.start()
    }

    private companion object {
        const val TAG = "AweTouchDebug"
    }
}
