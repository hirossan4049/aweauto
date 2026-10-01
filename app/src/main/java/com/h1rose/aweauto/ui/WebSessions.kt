package com.h1rose.aweauto.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.h1rose.aweauto.R
import com.h1rose.aweauto.cast.CastBridge
import com.h1rose.aweauto.web.HlsPrefetcher

/**
 * 表示中のページの WebView と状態。Android Auto で別の画面 (地図・電話など) に切り替えると
 * 車の画面 (Presentation) は作り直されるが、WebView はここに残しておき、戻ったらつなぎ直す。
 * これで再生が止まったり最初からになったりしない。
 */
internal class WebSession(val key: List<Any>, val view: WebView, initialUrl: String) {
    var currentUrl by mutableStateOf(initialUrl)
    var videoStarted by mutableStateOf(false)
    var fullscreen by mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null)

    /** いま動画が再生中か (ページからの playing / pause 通知) */
    var playing = false

    /** 車の画面が割り込み (バックカメラなど) で隠れたときに再生中だったら、戻ったときに再開する */
    var resumeOnReturn = false

    fun destroy() {
        (view.getTag(R.id.hls_prefetcher) as? HlsPrefetcher)?.release()
        CastBridge.detach(view)
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }
}

internal val sessions = mutableMapOf<Route.Web, WebSession>()

/**
 * 車の画面が割り込み (バックカメラ・360 度カメラなど) で隠れた / 戻ったときに呼ぶ。
 * 隠れている間は止めたままにし、戻ったら割り込み前に再生していた動画だけ再開する。
 */
fun onCarScreenVisibilityChanged(visible: Boolean) {
    for (session in sessions.values) {
        if (!visible) {
            session.resumeOnReturn = session.playing
        } else if (session.resumeOnReturn) {
            session.resumeOnReturn = false
            resumePlayback(session.view, attempt = 0)
        }
    }
}

// 戻った直後は音の出力がまだ Android Auto 側にあって再生してもすぐ止まるので、間を置いて数回試す
private val RESUME_DELAYS_MS = longArrayOf(800, 1500, 3000, 5000)

private fun resumePlayback(view: WebView, attempt: Int) {
    if (attempt >= RESUME_DELAYS_MS.size) return
    view.postDelayed({
        view.evaluateJavascript(
            "(function(){var p=document.getElementById('movie_player');" +
                "if(p&&p.playVideo)p.playVideo();else{var v=document.querySelector('video');if(v)v.play();}})()",
            null,
        )
        // 少し待って本当に動いているか確かめ、止まっていればもう一度
        view.postDelayed({
            view.evaluateJavascript("(function(){var v=document.querySelector('video');return !!v&&!v.paused;})()") {
                if (it != "true") resumePlayback(view, attempt + 1)
            }
        }, 700)
    }, RESUME_DELAYS_MS[attempt])
}

/** 戻る・ホームで履歴から外れた画面の WebView を捨てる */
internal fun pruneSessions() {
    val alive = AweNav.backStack.value.toSet()
    sessions.entries.removeAll { (route, session) ->
        (route !in alive).also { if (it) session.destroy() }
    }
}
