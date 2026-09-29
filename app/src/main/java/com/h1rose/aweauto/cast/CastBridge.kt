package com.h1rose.aweauto.cast

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.h1rose.aweauto.data.StreamService
import com.h1rose.aweauto.ui.AweNav
import com.h1rose.aweauto.ui.Route

/**
 * キャストのコマンドを YouTube の WebView に流し、WebView の再生状態を Lounge に返す。
 * 表示中の YouTube WebView は [attach] で登録される。
 */
object CastBridge : LoungePlayer {
    private var webView: WebView? = null

    /** JS から呼ばれる窓口。window.AweCast.onState(...) */
    const val JS_NAME = "AweCast"

    fun attach(view: WebView) {
        webView = view
        view.addJavascriptInterface(JsInterface, JS_NAME)
    }

    fun detach(view: WebView) {
        if (webView === view) webView = null
    }

    override fun load(videoId: String, startSec: Double) {
        val url = "https://m.youtube.com/watch?v=$videoId&t=${startSec.toInt()}s"
        val view = webView
        if (view != null) view.loadUrl(url) else AweNav.go(Route.Web(StreamService.YOUTUBE, url))
    }

    override fun play() = js("p.playVideo ? p.playVideo() : v && v.play()")
    override fun pause() = js("p.pauseVideo ? p.pauseVideo() : v && v.pause()")
    override fun seekTo(sec: Double) = js("p.seekTo ? p.seekTo($sec, true) : v && (v.currentTime = $sec)")
    override fun stop() = js("p.pauseVideo ? p.pauseVideo() : v && v.pause()")
    override fun setVolume(volume: Int) = js("p.setVolume && p.setVolume($volume)")

    private fun js(body: String) {
        webView?.evaluateJavascript(
            "(function(){var p=document.getElementById('movie_player')||{};var v=document.querySelector('video');$body;})()",
            null,
        )
    }

    private object JsInterface {
        @JavascriptInterface
        fun onState(videoId: String, state: Int, currentTime: Double, duration: Double) {
            LoungeReceiver.reportPlayback(videoId, state, currentTime, duration)
        }
    }
}
