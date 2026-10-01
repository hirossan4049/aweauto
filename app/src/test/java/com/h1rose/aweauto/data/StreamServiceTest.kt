package com.h1rose.aweauto.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** サイトを足したときの書き忘れを見つける */
class StreamServiceTest {
    // Gradle の単体テストはモジュール (app/) を作業ディレクトリにして動く
    private val assets = File("src/main/assets")

    @Test
    fun everySiteHasItsStylesheet() {
        StreamService.entries.forEach {
            assertTrue("${it.id}: ${it.cssAsset} がありません", File(assets, it.cssAsset).isFile)
        }
    }

    @Test
    fun idsAreUnique() {
        val ids = StreamService.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun homeUrlBelongsToItsHosts() {
        StreamService.entries.forEach { s ->
            val host = s.homeUrl.substringAfter("://").substringBefore('/')
            assertTrue("${s.id}: $host が hosts にありません", s.hosts.any { host == it || host.endsWith(".$it") })
        }
    }

    @Test
    fun playbackPages() {
        assertTrue(StreamService.YOUTUBE.isPlaybackPage("https://m.youtube.com/watch?v=abc"))
        assertFalse(StreamService.YOUTUBE.isPlaybackPage("https://m.youtube.com/results?search_query=x"))
        assertFalse(StreamService.YOUTUBE.isPlaybackPage("https://m.youtube.com/watch_later"))
        assertTrue(StreamService.TVER.isPlaybackPage("https://tver.jp/episodes/epvfuv4v31"))
        assertFalse(StreamService.TVER.isPlaybackPage("https://tver.jp/series/srgbrwms3g"))
        // 別のサイトの再生ページは、そのサイトのものとして扱わない
        assertFalse(StreamService.TVER.isPlaybackPage("https://m.youtube.com/episodes/x"))
    }

    @Test
    fun posters() {
        assertEquals("https://i.ytimg.com/vi/abc/hqdefault.jpg", StreamService.YOUTUBE.poster("https://m.youtube.com/watch?v=abc&t=3"))
        assertEquals(
            "https://statics.tver.jp/images/content/thumbnail/episode/large/epvfuv4v31.jpg",
            StreamService.TVER.poster("https://tver.jp/episodes/epvfuv4v31?p=1"),
        )
        assertNull(StreamService.YOUTUBE.poster("https://m.youtube.com/"))
    }

    @Test
    fun siteForUrl() {
        assertEquals(StreamService.YOUTUBE, StreamService.forUrl("https://www.youtube.com/watch?v=a"))
        assertEquals(StreamService.TVER, StreamService.forUrl("https://tver.jp/"))
        assertNull(StreamService.forUrl("https://example.com/"))
    }

    @Test
    fun youtubeVideoIds() {
        assertEquals("abc", youtubeVideoId("https://m.youtube.com/watch?t=3&v=abc#x"))
        assertNull(youtubeVideoId("https://m.youtube.com/results?v=abc"))
        assertNull(youtubeVideoId(null))
    }
}
