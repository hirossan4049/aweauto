package com.h1rose.aweauto.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    val optimizeSummary: String,
    /** 最適化 ON のときに入れる Cookie (URL to 値)。サイトの設定 (ダークテーマなど) を Cookie で持つサイト向け */
    val optimizeCookies: List<Pair<String, String>> = emptyList(),
    /** HLS の番組を見ながら丸ごと端末に保存できる (HlsPrefetcher) */
    val offlineCache: Boolean = false,
    /** スマホの公式アプリからのキャストを受ける (CastBridge / LoungeReceiver)。今は YouTube の仕組みだけ */
    val castReceiver: Boolean = false,
) {
    YOUTUBE(
        id = "youtube",
        label = "YouTube",
        homeUrl = "https://m.youtube.com/",
        hosts = listOf("youtube.com", "m.youtube.com", "www.youtube.com"),
        cssAsset = "css/youtube.css",
        userAgent = UserAgentKind.MOBILE,
        brand = Color(0xFFFF0033),
        optimizeSummary = "ダークテーマ・3 列グリッド・全画面プレーヤー・自動ミュート解除。広告/ショート/下タブを隠す",
        // f6=400: ダークテーマ
        optimizeCookies = listOf("https://m.youtube.com" to "PREF=f6=400&hl=ja; domain=.youtube.com; path=/"),
        castReceiver = true,
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
        optimizeSummary = "ダークテーマ・全画面プレーヤー・再生前アンケートにダミー値で自動回答。アプリ誘導/フッターを隠す",
        offlineCache = true,
    );

    companion object {
        fun byId(id: String) = entries.first { it.id == id }
    }
}

/** ホームの「ピックアップ」に並べるショートカット */
data class Shortcut(
    val title: String,
    val subtitle: String,
    val service: StreamService,
    val url: String,
    val icon: ImageVector,
)

val shortcuts = listOf(
    Shortcut("ホーム", "YouTube", StreamService.YOUTUBE, "https://m.youtube.com/", Icons.Outlined.Home),
    Shortcut("音楽", "YouTube", StreamService.YOUTUBE, "https://m.youtube.com/channel/UC-9-kyTW8ZkZNDHQJ6FgpwQ", AweIcons.MusicNote),
    Shortcut("登録チャンネル", "YouTube", StreamService.YOUTUBE, "https://m.youtube.com/feed/subscriptions", AweIcons.Subscriptions),
    Shortcut("ホーム", "TVer", StreamService.TVER, "https://tver.jp/", Icons.Outlined.Home),
    Shortcut("ランキング", "TVer", StreamService.TVER, "https://tver.jp/rankings/all", AweIcons.Leaderboard),
)
