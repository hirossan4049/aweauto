package com.h1rose.aweauto

import com.h1rose.aweauto.data.StreamService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareRouteTest {
    private fun route(text: String) = ShareActivity.routeFor(text)

    @Test
    fun youtubeShareText() {
        // YouTube アプリの「共有」は、タイトルの後ろにリンクが付いてくる
        val r = route("【見守り】ミスタードーナツ食べるしちょっと見てて https://youtu.be/wZjVMPvYq_s?si=abc")!!
        assertEquals(StreamService.YOUTUBE, r.service)
        assertEquals("https://m.youtube.com/watch?v=wZjVMPvYq_s", r.url)
    }

    @Test
    fun youtubeHostsBecomeMobile() {
        assertEquals("https://m.youtube.com/watch?v=abc&t=30", route("https://www.youtube.com/watch?v=abc&t=30")!!.url)
        assertEquals("https://m.youtube.com/watch?v=abc", route("https://youtube.com/watch?v=abc")!!.url)
        assertEquals("https://m.youtube.com/results?search_query=ダイアン", route("https://m.youtube.com/results?search_query=ダイアン")!!.url)
    }

    @Test
    fun shortsOpenAsNormalVideo() {
        assertEquals("https://m.youtube.com/watch?v=xyz", route("https://youtube.com/shorts/xyz?feature=share")!!.url)
    }

    @Test
    fun otherSitesByHost() {
        val r = route("見逃し配信中 https://tver.jp/episodes/epvfuv4v31")!!
        assertEquals(StreamService.TVER, r.service)
        assertEquals("https://tver.jp/episodes/epvfuv4v31", r.url)
    }

    @Test
    fun unsupported() {
        assertNull(route("https://example.com/video"))
        assertNull(route("リンクなし"))
        // ホスト名の途中が一致するだけのものは別サイト
        assertNull(route("https://nottver.jp/episodes/x"))
    }
}
