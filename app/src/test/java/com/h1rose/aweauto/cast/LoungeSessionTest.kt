package com.h1rose.aweauto.cast

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LoungeSessionTest {
    private var cpnCount = 0
    private val session = LoungeSession { "cpn${++cpnCount}" }

    private fun msg(name: String, vararg fields: Pair<String, String>) =
        session.onMessage(name, JSONObject(fields.toMap()))!!

    private fun OutgoingMessage.arg(key: String) = params[key]

    @Test
    fun setPlaylistLoadsVideoAndReportsIt() {
        val e = msg("setPlaylist", "videoIds" to "a,b,c", "currentIndex" to "1", "videoId" to "b", "currentTime" to "12.5")
        assertEquals(listOf(PlayerCommand.Load("b", 12.5)), e.player)
        assertEquals(listOf("nowPlaying", "onStateChange", "onHasPreviousNextChanged"), e.send.map { it.name })
        val now = e.send[0]
        assertEquals("b", now.arg("videoId"))
        assertEquals("1", now.arg("currentIndex"))
        assertEquals("3", now.arg("state")) // 読み込み中
        assertEquals("true", e.send[2].arg("hasPrevious"))
        assertEquals("true", e.send[2].arg("hasNext"))
    }

    @Test
    fun nextAndPreviousStayInsideTheQueue() {
        msg("setPlaylist", "videoIds" to "a,b", "currentIndex" to "0", "videoId" to "a")
        assertEquals(listOf(PlayerCommand.Load("b", 0.0)), msg("next").player)
        assertTrue(msg("next").player.isEmpty()) // 最後の次は無い
        assertEquals(listOf(PlayerCommand.Load("a", 0.0)), msg("previous").player)
        assertTrue(msg("previous").player.isEmpty())
    }

    @Test
    fun endedVideoAdvancesEvenWithoutPhone() {
        msg("setPlaylist", "videoIds" to "a,b", "currentIndex" to "0", "videoId" to "a")
        // スマホとの接続が切れていても、終わったら次の動画へ進む (送信はしない)
        val e = session.onPlayback("a", state = 0, currentTime = 100.0, duration = 100.0, remotesConnected = false)
        assertEquals(listOf(PlayerCommand.Load("b", 0.0)), e.player)
        assertTrue(e.send.none { it.name == "onStateChange" && it.arg("state") == "0" })
    }

    @Test
    fun playbackIsNotSentWithoutPhone() {
        msg("setPlaylist", "videoIds" to "a", "videoId" to "a")
        assertEquals(LoungeEffects.NONE, session.onPlayback("a", 1, 5.0, 100.0, remotesConnected = false))
        val e = session.onPlayback("a", 1, 6.0, 100.0, remotesConnected = true)
        assertEquals(listOf("onStateChange"), e.send.map { it.name })
        assertEquals("6.000", e.send[0].arg("currentTime"))
    }

    @Test
    fun newVideoGetsNewCpn() {
        session.onPlayback("a", 1, 0.0, 10.0, remotesConnected = true)
        val first = session.onPlayback("a", 1, 1.0, 10.0, remotesConnected = true).send[0].arg("cpn")
        val second = session.onPlayback("b", 1, 0.0, 10.0, remotesConnected = true)
        assertEquals(listOf("nowPlaying", "onStateChange"), second.send.map { it.name })
        assertTrue(first != second.send[0].arg("cpn"))
    }

    @Test
    fun stopClearsNowPlaying() {
        msg("setPlaylist", "videoIds" to "a", "videoId" to "a")
        val e = msg("stopVideo")
        assertEquals(listOf(PlayerCommand.Stop), e.player)
        assertEquals(OutgoingMessage("nowPlaying"), e.send.single())
        assertEquals("", session.currentVideoId)
    }

    @Test
    fun playerControls() {
        assertEquals(listOf(PlayerCommand.Play), msg("play").player)
        assertEquals(listOf(PlayerCommand.Pause), msg("pause").player)
        assertEquals(listOf(PlayerCommand.SeekTo(42.0)), msg("seekTo", "newTime" to "42").player)
        val volume = msg("setVolume", "volume" to "30")
        assertEquals(listOf(PlayerCommand.SetVolume(30)), volume.player)
        assertEquals("30", volume.send.single().arg("volume"))
        assertNull(session.onMessage("somethingNew", JSONObject()))
    }

    @Test
    fun timesUseDotEvenInCommaLocales() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val e = session.onPlayback("a", 1, 1.5, 10.0, remotesConnected = true)
            assertEquals("1.500", e.send.first { it.name == "onStateChange" }.arg("currentTime"))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun remotesFromLoungeStatus() {
        val status = JSONObject().put(
            "devices",
            """[{"type":"LOUNGE_SCREEN","name":"aweauto"},{"type":"REMOTE_CONTROL","name":"Pixel"},{"type":"REMOTE_CONTROL","clientName":"iOS"}]""",
        )
        assertEquals(listOf("Pixel", "iOS"), LoungeSession.remotesIn(status))
        assertEquals(emptyList<String>(), LoungeSession.remotesIn(JSONObject().put("devices", "not json")))
    }
}
