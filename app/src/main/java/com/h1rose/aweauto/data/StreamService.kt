package com.h1rose.aweauto.data

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.h1rose.aweauto.R
import com.h1rose.aweauto.ui.AweIcons

enum class UserAgentKind { MOBILE, DESKTOP }

enum class StreamService(
    val id: String,
    val label: String,
    val homeUrl: String,
    /** このホストで開いているページにだけ CSS を注入する */
    val hosts: List<String>,
    val cssAsset: String,
    val userAgent: UserAgentKind,
    val brand: Color,
    /** 設定画面に出す「最適化」の中身 */
    @StringRes val optimizeSummary: Int,
    /** 最適化 ON のときに入れる Cookie (URL to 値)。サイトの設定 (ダークテーマなど) を Cookie で持つサイト向け */
    val optimizeCookies: List<Pair<String, String>> = emptyList(),
    /** HLS の番組を見ながら丸ごと端末に保存できる (HlsPrefetcher) */
    val offlineCache: Boolean = false,
    /** スマホの公式アプリからのキャストを受ける (CastBridge / LoungeReceiver)。今は YouTube の仕組みだけ */
    val castReceiver: Boolean = false,
    /** 動画を再生するページのパス。再生中はレールを隠して動画だけにし、読み込み中の画面を重ねる */
    val playbackPage: Regex? = null,
    /** 再生ページの URL から、読み込み中に出すサムネイルの URL を作る */
    val poster: (url: String) -> String? = { null },
) {
    YOUTUBE(
        id = "youtube",
        label = "YouTube",
        homeUrl = "https://m.youtube.com/",
        hosts = listOf("youtube.com", "m.youtube.com", "www.youtube.com"),
        cssAsset = "css/youtube.css",
        userAgent = UserAgentKind.MOBILE,
        brand = Color(0xFFFF0033),
        optimizeSummary = R.string.optimize_youtube,
        // f6=400: ダークテーマ
        optimizeCookies = listOf("https://m.youtube.com" to "PREF=f6=400&hl=ja; domain=.youtube.com; path=/"),
        castReceiver = true,
        playbackPage = Regex("^/watch$"),
        poster = { url -> youtubeVideoId(url)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" } },
    ),
    TVER(
        id = "tver",
        label = "TVer",
        homeUrl = "https://tver.jp/",
        hosts = listOf("tver.jp"),
        cssAsset = "css/tver.css",
        // スマホ用 Web は「アプリで見てね」になって再生できないので PC 版を開く
        userAgent = UserAgentKind.DESKTOP,
        brand = Color(0xFF12A4E8),
        optimizeSummary = R.string.optimize_tver,
        offlineCache = true,
        playbackPage = Regex("^/episodes/"),
        poster = { url ->
            Regex("/episodes/([^/?#]+)").find(url)?.groupValues?.get(1)
                ?.let { "https://statics.tver.jp/images/content/thumbnail/episode/large/$it.jpg" }
        },
    );

    fun ownsHost(host: String) = hosts.any { host == it || host.endsWith(".$it") }

    /** このサイトの、動画を再生するページか */
    fun isPlaybackPage(url: String): Boolean {
        val parts = UrlParts.parse(url) ?: return false
        return ownsHost(parts.host) && playbackPage?.containsMatchIn(parts.path) == true
    }

    companion object {
        fun byId(id: String) = entries.first { it.id == id }

        /** URL のホストからサイトを決める。対応していなければ null */
        fun forUrl(url: String): StreamService? = UrlParts.parse(url)?.let { p -> entries.firstOrNull { it.ownsHost(p.host) } }
    }
}

/**
 * URL をホスト・パス・クエリ・フラグメントに分ける。android.net.Uri を使わないので JVM のテストでも動く。
 * 共有の文章などは日本語が混ざるので、厳密な URI としては読まない。
 */
data class UrlParts(val host: String, val path: String, val query: String, val fragment: String) {
    companion object {
        val PATTERN = Regex("""https?://([^/?#\s]+)(/[^?#\s]*)?(\?[^#\s]*)?(#\S*)?""")

        fun parse(text: String): UrlParts? = PATTERN.find(text)?.let(::from)

        fun from(m: MatchResult) = UrlParts(
            host = m.groupValues[1].substringBefore(':').lowercase(),
            path = m.groupValues[2],
            query = m.groupValues[3],
            fragment = m.groupValues[4],
        )
    }
}

/** YouTube の視聴ページの動画 ID */
fun youtubeVideoId(url: String?): String? {
    val parts = url?.let(UrlParts::parse) ?: return null
    if (!StreamService.YOUTUBE.ownsHost(parts.host) || parts.path != "/watch") return null
    return Regex("[?&]v=([^&#]+)").find(parts.query)?.groupValues?.get(1)
}

/** ホームの「ピックアップ」に並べるショートカット */
data class Shortcut(
    @StringRes val title: Int,
    val service: StreamService,
    val url: String,
    val icon: ImageVector,
)

val shortcuts = listOf(
    Shortcut(R.string.shortcut_home, StreamService.YOUTUBE, "https://m.youtube.com/", Icons.Outlined.Home),
    Shortcut(R.string.shortcut_music, StreamService.YOUTUBE, "https://m.youtube.com/channel/UC-9-kyTW8ZkZNDHQJ6FgpwQ", AweIcons.MusicNote),
    Shortcut(R.string.shortcut_subscriptions, StreamService.YOUTUBE, "https://m.youtube.com/feed/subscriptions", AweIcons.Subscriptions),
    Shortcut(R.string.shortcut_home, StreamService.TVER, "https://tver.jp/", Icons.Outlined.Home),
    Shortcut(R.string.shortcut_ranking, StreamService.TVER, "https://tver.jp/rankings/all", AweIcons.Leaderboard),
)
