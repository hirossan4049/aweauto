package com.h1rose.aweauto.web

import android.content.Context
import android.webkit.WebSettings
import com.h1rose.aweauto.data.UserAgentKind

object UserAgents {
    /**
     * WebView の既定 UA から "; wv" と "Version/4.0" を落として素の Chrome に見せる。
     * (WebView だと判定されると YouTube / TVer がアプリ誘導を出してくる)
     * Chrome のバージョン表記は実機のものをそのまま使う。
     */
    fun forKind(context: Context, kind: UserAgentKind): String {
        val base = WebSettings.getDefaultUserAgent(context)
        val chrome = Regex("""Chrome/[\d.]+""").find(base)?.value ?: "Chrome/138.0.0.0"
        return when (kind) {
            UserAgentKind.MOBILE -> base.replace("; wv", "").replace(Regex("""Version/[\d.]+ """), "")
            UserAgentKind.DESKTOP ->
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
        }
    }
}
