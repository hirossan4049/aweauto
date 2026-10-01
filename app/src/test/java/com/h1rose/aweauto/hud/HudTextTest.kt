package com.h1rose.aweauto.hud

import com.h1rose.aweauto.data.StreamService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class HudTextTest {
    private val noon = LocalTime.of(9, 5)

    @Test
    fun replacesTitleAndTime() {
        assertEquals("ミスド見守り 9:05", HudText.format("{title} {time}", "ミスド見守り", noon))
        assertEquals("安全運転で", HudText.format("安全運転で", null, noon))
    }

    @Test
    fun missingTitleLeavesNoGap() {
        assertEquals("9:05", HudText.format("{title}  {time}", null, noon))
        // 題名だけの設定で題名が無ければ何も出さない
        assertNull(HudText.format("{title}", null, noon))
        assertNull(HudText.format("   ", "x", noon))
    }

    @Test
    fun siteNameIsRemovedFromTitles() {
        assertEquals("【見守り】ミスド", NowPlaying.cleanTitle(StreamService.YOUTUBE, "【見守り】ミスド - YouTube"))
        assertEquals("天狗の台所", NowPlaying.cleanTitle(StreamService.TVER, "天狗の台所 | TVer"))
        // TVer の実際のタイトル
        assertEquals(
            "「天狗の台所 Ｓｅａｓｏｎ３」放送直前SP 9月24日(木)放送分",
            NowPlaying.cleanTitle(
                StreamService.TVER,
                "「天狗の台所 Ｓｅａｓｏｎ３」放送直前SP 9月24日(木)放送分 | ドラマ | 見逃し無料配信はTVer！人気の動画見放題",
            ),
        )
        // 題名の中の「 | 」は残す
        assertEquals(
            "Henri PFR WE1 | Tomorrowland 2026",
            NowPlaying.cleanTitle(StreamService.YOUTUBE, "Henri PFR WE1 | Tomorrowland 2026 - YouTube"),
        )
        assertNull(NowPlaying.cleanTitle(StreamService.YOUTUBE, "YouTube"))
        assertNull(NowPlaying.cleanTitle(StreamService.YOUTUBE, null))
    }
}
