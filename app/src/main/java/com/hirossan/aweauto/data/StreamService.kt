package com.hirossan.aweauto.data

import androidx.compose.ui.graphics.Color

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
    );

    companion object {
        fun byId(id: String) = entries.first { it.id == id }
    }
}

/** ホームの「ピックアップ」に並べるショートカット */
data class Shortcut(val title: String, val subtitle: String, val service: StreamService, val url: String)

val shortcuts = listOf(
    Shortcut("ホーム", "YouTube", StreamService.YOUTUBE, "https://m.youtube.com/"),
    Shortcut("音楽", "YouTube", StreamService.YOUTUBE, "https://m.youtube.com/channel/UC-9-kyTW8ZkZNDHQJ6FgpwQ"),
    Shortcut("登録チャンネル", "YouTube", StreamService.YOUTUBE, "https://m.youtube.com/feed/subscriptions"),
    Shortcut("ホーム", "TVer", StreamService.TVER, "https://tver.jp/"),
    Shortcut("ランキング", "TVer", StreamService.TVER, "https://tver.jp/rankings/all"),
)
