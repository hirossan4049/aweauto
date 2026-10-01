package com.h1rose.aweauto.cast

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** スマホからの指示を受けて、車の画面のプレーヤーにさせること */
sealed interface PlayerCommand {
    data class Load(val videoId: String, val startSec: Double) : PlayerCommand
    data object Play : PlayerCommand
    data object Pause : PlayerCommand
    data class SeekTo(val sec: Double) : PlayerCommand
    data object Stop : PlayerCommand
    data class SetVolume(val volume: Int) : PlayerCommand
}

/** 1 つの出来事に対する反応。スマホへ送るメッセージと、プレーヤーへの指示 */
data class LoungeEffects(
    val send: List<OutgoingMessage> = emptyList(),
    val player: List<PlayerCommand> = emptyList(),
) {
    operator fun plus(other: LoungeEffects) = LoungeEffects(send + other.send, player + other.player)

    companion object {
        val NONE = LoungeEffects()
    }
}

/**
 * Lounge (YouTube のキャスト) の画面側の状態: 再生キュー・今の動画と再生位置。
 *
 * 通信も Android も使わず、「何が来たら、何を返して何をするか」だけを決める ([LoungeReceiver] が実行する)。
 * なのでキャストの振る舞いは JVM のテストで確かめられる。
 *
 * @param newCpn 動画ごとの再生 ID (cpn) を作る。テストでは決まった値を返す
 */
class LoungeSession(private val newCpn: () -> String) {
    private var videoIds = emptyList<String>()
    private var listId = ""
    private var currentIndex = 0
    private var cpn = newCpn()
    private var playback = Playback(videoId = "", state = -1, currentTime = 0.0, duration = 0.0)

    /** state: 1 再生中 / 2 一時停止 / 3 読み込み中 / 0 終了 / 4 停止 */
    private data class Playback(val videoId: String, val state: Int, val currentTime: Double, val duration: Double)

    val currentVideoId: String get() = playback.videoId

    /** スマホからの指示。知らない指示なら null */
    fun onMessage(name: String, p: JSONObject): LoungeEffects? = when (name) {
        "remoteConnected" -> LoungeEffects(send = fullState())
        "getNowPlaying" -> LoungeEffects(send = listOf(nowPlaying()))
        "setPlaylist" -> {
            videoIds = p.optString("videoIds").split(',').filter { it.isNotEmpty() }
            listId = p.optString("listId")
            currentIndex = p.optString("currentIndex", "0").toIntOrNull() ?: 0
            val videoId = p.optString("videoId").ifEmpty { videoIds.getOrNull(currentIndex).orEmpty() }
            val start = p.optString("currentTime", "0").toDoubleOrNull() ?: 0.0
            if (videoId.isNotEmpty()) startVideo(videoId, start) else LoungeEffects.NONE
        }
        "updatePlaylist" -> {
            videoIds = p.optString("videoIds").split(',').filter { it.isNotEmpty() }
            listId = p.optString("listId", listId)
            LoungeEffects(send = listOf(nowPlaying()))
        }
        "play" -> LoungeEffects(player = listOf(PlayerCommand.Play))
        "pause" -> LoungeEffects(player = listOf(PlayerCommand.Pause))
        "seekTo" -> p.optString("newTime").toDoubleOrNull()
            ?.let { LoungeEffects(player = listOf(PlayerCommand.SeekTo(it))) } ?: LoungeEffects.NONE
        "stopVideo" -> {
            playback = playback.copy(videoId = "", state = 4)
            LoungeEffects(send = listOf(OutgoingMessage("nowPlaying")), player = listOf(PlayerCommand.Stop))
        }
        "next" -> step(+1)
        "previous" -> step(-1)
        "setVolume" -> p.optString("volume").toIntOrNull()?.let { v ->
            LoungeEffects(send = listOf(volumeChanged(v)), player = listOf(PlayerCommand.SetVolume(v)))
        } ?: LoungeEffects.NONE
        "getVolume" -> LoungeEffects(send = listOf(volumeChanged(100)))
        "getSubtitlesTrack" ->
            LoungeEffects(send = listOf(OutgoingMessage("onSubtitlesTrackChanged", mapOf("videoId" to playback.videoId))))
        else -> null
    }

    /**
     * 車の画面のプレーヤーから再生状態が届いた。
     * スマホがつながっていなければ送らない (再生中は数秒ごとに呼ばれるので無駄な通信を減らす)。
     * つながったときは remoteConnected / loungeStatus で今の状態をまとめて送る。
     * 次の動画への自動再生は、スマホがつながっていなくても続ける。
     */
    fun onPlayback(videoId: String, state: Int, currentTime: Double, duration: Double, remotesConnected: Boolean): LoungeEffects {
        val changedVideo = videoId != playback.videoId
        playback = Playback(videoId, state, currentTime, duration)
        if (changedVideo) cpn = newCpn()
        val report = when {
            !remotesConnected -> LoungeEffects.NONE
            changedVideo -> LoungeEffects(send = listOf(nowPlaying(), stateChange()))
            else -> LoungeEffects(send = listOf(stateChange()))
        }
        return if (state == 0) report + step(+1) else report
    }

    /** スマホがつながったときに送る、今の状態の一式 */
    fun fullState(): List<OutgoingMessage> = listOf(
        hasPrevNext(),
        nowPlaying(),
        stateChange(),
        OutgoingMessage("onAutoplayModeChanged", mapOf("autoplayMode" to "UNSUPPORTED")),
    )

    private fun step(delta: Int): LoungeEffects {
        val next = videoIds.getOrNull(currentIndex + delta) ?: return LoungeEffects.NONE
        currentIndex += delta
        return startVideo(next, 0.0)
    }

    private fun startVideo(videoId: String, startSec: Double): LoungeEffects {
        cpn = newCpn()
        playback = Playback(videoId, state = 3, currentTime = startSec, duration = 0.0)
        currentIndex = videoIds.indexOf(videoId).takeIf { it >= 0 } ?: currentIndex
        return LoungeEffects(
            send = listOf(nowPlaying(), stateChange(), hasPrevNext()),
            player = listOf(PlayerCommand.Load(videoId, startSec)),
        )
    }

    private fun nowPlaying(): OutgoingMessage {
        if (playback.videoId.isEmpty()) return OutgoingMessage("nowPlaying")
        return OutgoingMessage(
            "nowPlaying",
            buildMap {
                put("videoId", playback.videoId)
                putAll(timing())
                put("state", playback.state.toString())
                if (listId.isNotEmpty()) put("listId", listId)
                put("currentIndex", currentIndex.toString())
            },
        )
    }

    private fun stateChange() = OutgoingMessage("onStateChange", timing() + ("state" to playback.state.toString()))

    private fun timing(): Map<String, String> {
        val loaded = if (playback.state in 1..3) playback.duration else 0.0
        return mapOf(
            "currentTime" to seconds(playback.currentTime),
            "duration" to seconds(playback.duration),
            "loadedTime" to seconds(loaded),
            "seekableStartTime" to "0",
            "seekableEndTime" to seconds(playback.duration),
            "cpn" to cpn,
        )
    }

    private fun hasPrevNext() = OutgoingMessage(
        "onHasPreviousNextChanged",
        mapOf("hasPrevious" to (currentIndex > 0).toString(), "hasNext" to (currentIndex + 1 < videoIds.size).toString()),
    )

    private fun volumeChanged(volume: Int) =
        OutgoingMessage("onVolumeChanged", mapOf("volume" to "$volume", "muted" to "false"))

    // 端末の言語で小数点がカンマになると YouTube 側が読めないので、Locale.US で書く
    private fun seconds(value: Double) = String.format(Locale.US, "%.3f", value)

    companion object {
        /** loungeStatus に載っている、つながっているスマホの名前 */
        fun remotesIn(status: JSONObject): List<String> {
            val devices = runCatching { JSONArray(status.optString("devices", "[]")) }.getOrNull() ?: return emptyList()
            return (0 until devices.length())
                .map { devices.getJSONObject(it) }
                .filter { it.optString("type") == "REMOTE_CONTROL" }
                .map { it.optString("name").ifEmpty { it.optString("clientName", "YouTube") } }
        }
    }
}
