package com.h1rose.aweauto.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class LoungeChunkParserTest {
    @Test
    fun parsesChunksSplitAcrossReads() {
        val parser = LoungeChunkParser()
        val body = "115\n[[0,[\"c\",\"SID123\",\"\",8]],[1,[\"S\",\"gs456\"]]]\n" +
            "60\n[[2,[\"setPlaylist\",{\"videoId\":\"abc\",\"currentTime\":\"12\"}]]]\n" +
            "20\n[[3,[\"noop\"]]]\n"
        val cut = body.indexOf("setPlaylist") + 5

        val first = parser.feed(body.substring(0, cut))
        assertEquals(listOf("c", "S"), first.map { it.name })
        assertEquals("SID123", first[0].args.getString(0))

        val rest = parser.feed(body.substring(cut))
        assertEquals(listOf("setPlaylist", "noop"), rest.map { it.name })
        assertEquals(2, rest[0].index)
        assertEquals("abc", rest[0].payload.getString("videoId"))
    }

    @Test
    fun bracketsInsideStringsDoNotEndTheChunk() {
        val parser = LoungeChunkParser()
        val msgs = parser.feed("40\n[[5,[\"loungeStatus\",{\"devices\":\"[{\\\"a\\\":\\\"]\\\"}]\"}]]]\n")
        assertEquals(1, msgs.size)
        assertEquals("[{\"a\":\"]\"}]", msgs[0].payload.getString("devices"))
    }

    @Test
    fun encodesOutgoingWithDoubleUnderscore() {
        val form = encodeOutgoing(
            listOf(OutgoingMessage("onStateChange", mapOf("state" to "1")), OutgoingMessage("nowPlaying")),
            ofs = 3,
        )
        assertEquals(
            mapOf("count" to "2", "ofs" to "3", "req0__sc" to "onStateChange", "req0_state" to "1", "req1__sc" to "nowPlaying"),
            form,
        )
    }
}
