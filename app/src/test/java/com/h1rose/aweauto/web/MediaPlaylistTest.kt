package com.h1rose.aweauto.web

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaPlaylistTest {
    @Test
    fun collectsKeyAndSegmentsInOrder() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:5
            #EXT-X-PLAYLIST-TYPE:VOD
            #EXT-X-KEY:METHOD=AES-128,URI="https://key.example/v/encryption.key",IV=0xA9
            #EXTINF:6.006,
            https://vod.example/v/seg_0.ts
            #EXTINF:6.006,
            seg_1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        assertEquals(
            listOf(
                "https://key.example/v/encryption.key",
                "https://vod.example/v/seg_0.ts",
                "https://variants.example/v5/video/seg_1.ts",
            ),
            parseMediaPlaylist("https://variants.example/v5/video/video_1.m3u8?vt=token", playlist),
        )
    }
}

class PinVariantTest {
    private val master = """
        #EXTM3U
        #EXT-X-CONTENT-STEERING:SERVER-URI="https://steering.example/x.hcsm"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="A-0_1",URI="https://v.example/audio/a01.m3u8"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="A-0_2",URI="https://v.example/audio/a02.m3u8"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="A-1_1",URI="https://v.example/audio/a11.m3u8"
        #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="captions",URI="https://t.example/ja.m3u8"
        #EXT-X-STREAM-INF:BANDWIDTH=4781287,RESOLUTION=1920x1080,AUDIO="A-0_1"
        https://v.example/video/1080.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=3270776,RESOLUTION=1280x720,AUDIO="A-0_2"
        https://v.example/video/720b.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=3270776,RESOLUTION=1280x720,AUDIO="A-0_1"
        https://v.example/video/720a.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=926819,RESOLUTION=640x360,AUDIO="A-1_1"
        https://v.example/video/360.m3u8
    """.trimIndent()

    @Test
    fun keepsBestVariantUnderCapWithItsAudioOnly() {
        val out = pinSingleVariant(master, maxHeight = 720)
        assertEquals(1, Regex("#EXT-X-STREAM-INF").findAll(out).count())
        assert("720b.m3u8" in out) { out }
        assert("a02.m3u8" in out && "a01.m3u8" !in out && "a11.m3u8" !in out) { out }
        assert("CONTENT-STEERING" !in out) { out }
        assert("captions" in out) { out }
    }

    @Test
    fun fallsBackToLowestWhenNothingFits() {
        val out = pinSingleVariant(master, maxHeight = 240)
        assert("360.m3u8" in out && "a11.m3u8" in out) { out }
    }
}
