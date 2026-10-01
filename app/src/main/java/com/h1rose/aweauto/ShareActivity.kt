package com.h1rose.aweauto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.h1rose.aweauto.data.StreamService
import com.h1rose.aweauto.data.UrlParts
import com.h1rose.aweauto.ui.AweNav
import com.h1rose.aweauto.ui.Route

/**
 * スマホの YouTube / TVer アプリから「共有 → aweauto」で車の画面に送る。
 * 画面は出さずにトーストだけ出して閉じる。
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.dataString
        val route = text?.let(::routeFor)
        if (route != null) {
            AweNav.go(route)
            Toast.makeText(this, R.string.share_opening, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.share_unsupported, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        /** 共有された文章の中のリンクから、車の画面で開く先を決める。対応していないサイトなら null */
        fun routeFor(text: String): Route.Web? {
            val m = UrlParts.PATTERN.find(text) ?: return null
            val url = m.value
            val parts = UrlParts.from(m)
            val segments = parts.path.split('/').filter { it.isNotEmpty() }
            return when {
                parts.host == "youtu.be" -> segments.firstOrNull()?.let { youtube("https://m.youtube.com/watch?v=$it") }
                StreamService.YOUTUBE.ownsHost(parts.host) -> {
                    val shorts = segments.takeIf { it.firstOrNull() == "shorts" }?.getOrNull(1)
                    youtube(
                        if (shorts != null) "https://m.youtube.com/watch?v=$shorts"
                        // スマホ版のページにそろえる (www. や music. のままだと PC 版や別のサイトになる)
                        else "https://m.youtube.com${parts.path}${parts.query}${parts.fragment}"
                    )
                }
                // それ以外のサイトは StreamService.hosts で見分ける (サイトを足すときは StreamService に書くだけでいい)
                else -> StreamService.forUrl(url)?.let { Route.Web(it, url) }
            }
        }

        private fun youtube(url: String) = Route.Web(StreamService.YOUTUBE, url)
    }
}
