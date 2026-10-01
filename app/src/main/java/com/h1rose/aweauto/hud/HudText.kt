package com.h1rose.aweauto.hud

import com.h1rose.aweauto.data.StreamService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

/** HUD に出す好きな文字。{title} と {time} を置き換える */
object HudText {
    const val TITLE = "{title}"
    const val TIME = "{time}"

    /** 置き換えた結果。空になったら null (HUD には何も出さない) */
    fun format(template: String, title: String?, now: LocalTime): String? {
        val text = template
            .replace(TITLE, title.orEmpty())
            .replace(TIME, "%d:%02d".format(now.hour, now.minute))
            .replace(Regex("\\s+"), " ")
            .trim()
        return text.ifEmpty { null }
    }
}

/** 車の画面で再生しているページのタイトル。HUD の {title} に使う */
object NowPlaying {
    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    /** WebView がページのタイトルを受け取ったとき。再生ページ以外なら空にする */
    fun onTitle(service: StreamService, url: String?, title: String?) {
        _title.value = if (url != null && service.isPlaybackPage(url)) cleanTitle(service, title) else null
    }

    /**
     * ページのタイトルからサイト名の付け足しを外す。
     * - 末尾がサイト名そのもの: 「動画名 - YouTube」→「動画名」
     * - 最後の「 | 」の区切りにサイト名が入っている: 「番組名 | ドラマ | 見逃し無料配信はTVer！…」→「番組名」
     * YouTube の題名には「 | 」が普通に入るので、先に前者を試す
     */
    internal fun cleanTitle(service: StreamService, title: String?): String? {
        val t = title?.trim().orEmpty()
        if (t.isEmpty() || t.equals(service.label, ignoreCase = true)) return null
        val suffix = Regex("""\s*[-|｜–—]\s*${Regex.escape(service.label)}\s*$""", RegexOption.IGNORE_CASE)
        if (suffix.containsMatchIn(t)) return t.replace(suffix, "").ifEmpty { null }
        val parts = t.split(" | ", " ｜ ")
        if (parts.size > 1 && parts.last().contains(service.label, ignoreCase = true)) return parts.first().trim().ifEmpty { null }
        return t
    }
}
