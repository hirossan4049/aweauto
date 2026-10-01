package com.h1rose.aweauto.ui

import com.h1rose.aweauto.ShareActivity
import com.h1rose.aweauto.data.StreamService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder

/**
 * スマホの画面から車の画面を操作する。キーボードや細かいタッチはスマホの方が扱いやすいので、
 * 検索・再生の操作・地図の並べ方はここから行い、車の画面は見るだけにする。
 */
object CarRemote {
    private val _playing = MutableStateFlow(false)

    /** 車の画面で動画が再生中か */
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    internal fun onPlaying(session: WebSession, playing: Boolean) {
        if (session === current()) _playing.value = playing
    }

    /** いま車の画面に出ているページ (ホームなどなら null) */
    private fun current(): WebSession? = (AweNav.backStack.value.lastOrNull() as? Route.Web)?.let { sessions[it] }

    /** URL を車の画面で開く。対応していないサイトなら false */
    fun open(url: String): Boolean {
        val route = ShareActivity.routeFor(url) ?: return false
        AweNav.go(route)
        return true
    }

    /** サイトの検索結果を車の画面で開く */
    fun search(service: StreamService, query: String) {
        val q = query.trim().takeIf { it.isNotEmpty() } ?: return
        AweNav.go(Route.Web(service, searchUrl(service, q)))
    }

    internal fun searchUrl(service: StreamService, query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return when (service) {
            StreamService.YOUTUBE -> "https://m.youtube.com/results?search_query=$encoded"
            StreamService.TVER -> "https://tver.jp/search/${encoded.replace("+", "%20")}"
        }
    }

    fun playPause() = video(if (_playing.value) "v.pause()" else "v.play()")

    /** 再生位置を秒だけ進める (負なら戻す) */
    fun seekBy(seconds: Int) = video("v.currentTime = Math.max(0, v.currentTime + ($seconds))")

    fun back() = AweNav.back()

    fun home() = AweNav.home()

    private fun video(body: String) {
        current()?.view?.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(v){$body;}})()",
            null,
        )
    }
}
