package com.h1rose.aweauto

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
            Toast.makeText(this, "YouTube か TVer のリンクを共有してください", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        private val urlPattern = Regex("""https?://\S+""")

        fun routeFor(text: String): Route.Web? {
            val url = urlPattern.find(text)?.value ?: return null
            val uri = Uri.parse(url)
            val host = uri.host ?: return null
            return when {
                host == "youtu.be" -> uri.lastPathSegment?.let { youtube("https://m.youtube.com/watch?v=$it") }
                host.endsWith("youtube.com") -> {
                    val shorts = uri.pathSegments.takeIf { it.firstOrNull() == "shorts" }?.getOrNull(1)
                    youtube(
                        if (shorts != null) "https://m.youtube.com/watch?v=$shorts"
                        else uri.buildUpon().authority("m.youtube.com").build().toString()
                    )
                }
                host.endsWith("tver.jp") -> Route.Web(StreamService.TVER, url)
                else -> null
            }
        }

        private fun youtube(url: String) = Route.Web(StreamService.YOUTUBE, url)
    }
}
