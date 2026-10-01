package com.h1rose.aweauto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.h1rose.aweauto.data.StreamService
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
            Toast.makeText(this, "車の画面で開きます", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "対応している配信サイトのリンクを共有してください", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        /** http(s)://ホスト/パス?クエリ#フラグメント。共有の文章は日本語などが混ざるので、厳密な URI としては読まない */
        private val urlPattern = Regex("""https?://([^/?#\s]+)(/[^?#\s]*)?(\?[^#\s]*)?(#\S*)?""")

        /** 共有された文章の中のリンクから、車の画面で開く先を決める。対応していないサイトなら null */
        fun routeFor(text: String): Route.Web? {
            val m = urlPattern.find(text) ?: return null
            val url = m.value
            val host = m.groupValues[1].substringBefore(':').lowercase()
            val path = m.groupValues[2]
            val segments = path.split('/').filter { it.isNotEmpty() }
            return when {
                host == "youtu.be" -> segments.firstOrNull()?.let { youtube("https://m.youtube.com/watch?v=$it") }
                host == "youtube.com" || host.endsWith(".youtube.com") -> {
                    val shorts = segments.takeIf { it.firstOrNull() == "shorts" }?.getOrNull(1)
                    youtube(
                        if (shorts != null) "https://m.youtube.com/watch?v=$shorts"
                        // スマホ版のページにそろえる (www. や music. のままだと PC 版や別のサイトになる)
                        else "https://m.youtube.com$path${m.groupValues[3]}${m.groupValues[4]}"
                    )
                }
                // それ以外のサイトは StreamService.hosts で見分ける (サイトを足すときは StreamService に書くだけでいい)
                else -> StreamService.entries
                    .firstOrNull { s -> s.hosts.any { host == it || host.endsWith(".$it") } }
                    ?.let { Route.Web(it, url) }
            }
        }

        private fun youtube(url: String) = Route.Web(StreamService.YOUTUBE, url)
    }
}
