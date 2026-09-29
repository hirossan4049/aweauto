package com.h1rose.aweauto.cast

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val TAG = "AweLounge"
private const val BASE = "https://www.youtube.com/api/lounge"
private const val APP = "ytcr"

/** 受信したコマンドを実際に再生する側 */
interface LoungePlayer {
    fun load(videoId: String, startSec: Double)
    fun play()
    fun pause()
    fun seekTo(sec: Double)
    fun stop()
    fun setVolume(volume: Int)
}

data class CastStatus(
    val online: Boolean = false,
    /** スマホの YouTube アプリで入力する 12 桁のテレビコード */
    val pairingCode: String? = null,
    val remotes: List<String> = emptyList(),
    val error: String? = null,
)

/**
 * YouTube の「テレビコードでリンク」(Lounge API) の受信側。
 * 同じ Wi-Fi に居なくてもクラウド経由でスマホの YouTube アプリからキャストできる。
 * 参考: patrickkfkan/yt-cast-receiver, aykevl/plaincast
 */
object LoungeReceiver {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // long-poll は数分つながったままになる
        .readTimeout(5, TimeUnit.MINUTES)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendLock = Mutex()

    private lateinit var sp: SharedPreferences
    private var job: Job? = null
    private var player: LoungePlayer? = null

    private val _status = MutableStateFlow(CastStatus())
    val status: StateFlow<CastStatus> = _status.asStateFlow()

    var screenName = "aweauto"

    // ---- セッション状態 ----
    private var screenId = ""
    private var deviceId = ""
    private var loungeToken = ""
    private var sid = ""
    private var gsessionId = ""
    private var rid = 0
    private var aid = -1
    private var ofs = 0

    // ---- 再生状態 (スマホ側のミニプレーヤー表示用) ----
    private var videoIds = emptyList<String>()
    private var listId = ""
    private var currentIndex = 0
    private var cpn = randomString(16)
    private var state = State(videoId = "", state = -1, currentTime = 0.0, duration = 0.0)

    private data class State(val videoId: String, val state: Int, val currentTime: Double, val duration: Double)

    fun init(context: Context) {
        sp = context.getSharedPreferences("lounge", Context.MODE_PRIVATE)
        // ID が変わるとリンクが切れてテレビコードの入れ直しになるので保存しておく
        deviceId = sp.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            sp.edit().putString("device_id", it).apply()
        }
        screenId = sp.getString("screen_id", "") ?: ""
    }

    fun start(player: LoungePlayer) {
        this.player = player
        if (job?.isActive == true) return
        job = scope.launch { runForever() }
    }

    fun stop() {
        job?.cancel()
        job = null
        _status.value = CastStatus()
    }

    // ------------------------------------------------------------------
    // 接続の維持
    // ------------------------------------------------------------------

    private suspend fun runForever() {
        var backoff = 2_000L
        while (scope.isActive) {
            try {
                setupToken()
                val pairing = scope.launch { refreshPairingCodeLoop() }
                try {
                    bindLoop()
                } finally {
                    pairing.cancel()
                }
                backoff = 2_000L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "lounge error", e)
                _status.value = _status.value.copy(online = false, error = e.message)
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        }
    }

    private fun setupToken() {
        if (screenId.isEmpty()) {
            screenId = get("$BASE/pairing/generate_screen_id".toHttpUrl()).trim()
            sp.edit().putString("screen_id", screenId).apply()
        }
        val body = post(
            "$BASE/pairing/get_lounge_token_batch".toHttpUrl(),
            mapOf("screen_ids" to screenId),
        )
        val screen = JSONObject(body).getJSONArray("screens").getJSONObject(0)
        loungeToken = screen.getString("loungeToken")
        Log.i(TAG, "lounge token ok (screen=$screenId)")
    }

    private suspend fun refreshPairingCodeLoop() {
        while (true) {
            runCatching {
                val code = post(
                    "$BASE/pairing/get_pairing_code?ctx=pair".toHttpUrl(),
                    mapOf(
                        "access_type" to "permanent",
                        "app" to APP,
                        "lounge_token" to loungeToken,
                        "screen_id" to screenId,
                        "screen_name" to screenName,
                        "device_id" to deviceId,
                    ),
                ).trim()
                _status.value = _status.value.copy(pairingCode = code.chunked(3).joinToString(" "))
                Log.i(TAG, "pairing code refreshed")
            }.onFailure { Log.w(TAG, "pairing code failed", it) }
            delay(5 * 60_000L)
        }
    }

    /** 初回 bind → long-poll をつなぎ直し続ける。SID が無効になったら bind からやり直す */
    private fun bindLoop() {
        connect()
        while (scope.isActive) {
            val url = bindUrl(
                "RID" to "rpc", "SID" to sid, "CI" to "0", "AID" to aid.toString(),
                "gsessionid" to gsessionId, "TYPE" to "xmlhttp",
            )
            http.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
                when (res.code) {
                    200 -> {
                        val parser = LoungeChunkParser()
                        val source = res.body!!.source()
                        val buffer = okio.Buffer()
                        while (source.read(buffer, 8192) != -1L) {
                            parser.feed(buffer.readUtf8()).forEach(::handle)
                        }
                    }
                    400 -> connect() // Unknown SID
                    else -> throw IOException("bind HTTP ${res.code}")
                }
            }
        }
    }

    private fun connect() {
        rid = Random.nextInt(41000, 49999)
        aid = -1
        ofs = 0
        sid = ""
        gsessionId = ""
        val url = bindUrl("deviceInfo" to DEVICE_INFO, "RID" to (rid++).toString(), "CVER" to "1")
        Log.i(TAG, "bind connecting")
        val body = post(url, mapOf("count" to "0"))
        Log.i(TAG, "bind response ${body.length} chars")
        LoungeChunkParser().feed(body).forEach(::handle)
        check(sid.isNotEmpty()) { "bind に SID が無い" }
        _status.value = _status.value.copy(online = true, error = null)
        Log.i(TAG, "bind ok")
    }

    // ------------------------------------------------------------------
    // 受信
    // ------------------------------------------------------------------

    private fun handle(msg: LoungeMessage) {
        if (msg.index <= aid && msg.name != "c" && msg.name != "S") return
        aid = maxOf(aid, msg.index)
        val p = msg.payload
        when (msg.name) {
            "c" -> sid = msg.args.optString(0)
            "S" -> gsessionId = msg.args.optString(0)
            "noop" -> Unit
            "loungeStatus" -> {
                val devices = runCatching { org.json.JSONArray(p.optString("devices", "[]")) }.getOrNull()
                val remotes = (0 until (devices?.length() ?: 0))
                    .map { devices!!.getJSONObject(it) }
                    .filter { it.optString("type") == "REMOTE_CONTROL" }
                    .map { it.optString("name").ifEmpty { it.optString("clientName", "スマホ") } }
                _status.value = _status.value.copy(remotes = remotes)
                if (remotes.isNotEmpty()) sendFullState()
            }
            "remoteConnected" -> sendFullState()
            "getNowPlaying" -> send(nowPlaying())
            "setPlaylist" -> {
                videoIds = p.optString("videoIds").split(',').filter { it.isNotEmpty() }
                listId = p.optString("listId")
                currentIndex = p.optString("currentIndex", "0").toIntOrNull() ?: 0
                val videoId = p.optString("videoId").ifEmpty { videoIds.getOrNull(currentIndex).orEmpty() }
                val start = p.optString("currentTime", "0").toDoubleOrNull() ?: 0.0
                if (videoId.isNotEmpty()) startVideo(videoId, start)
            }
            "updatePlaylist" -> {
                videoIds = p.optString("videoIds").split(',').filter { it.isNotEmpty() }
                listId = p.optString("listId", listId)
                send(nowPlaying())
            }
            "play" -> onMain { player?.play() }
            "pause" -> onMain { player?.pause() }
            "seekTo" -> p.optString("newTime").toDoubleOrNull()?.let { t -> onMain { player?.seekTo(t) } }
            "stopVideo" -> {
                onMain { player?.stop() }
                state = state.copy(videoId = "", state = 4)
                send(OutgoingMessage("nowPlaying"))
            }
            "next" -> videoIds.getOrNull(currentIndex + 1)?.let { currentIndex++; startVideo(it, 0.0) }
            "previous" -> videoIds.getOrNull(currentIndex - 1)?.let { currentIndex--; startVideo(it, 0.0) }
            "setVolume" -> p.optString("volume").toIntOrNull()?.let { v ->
                onMain { player?.setVolume(v) }
                send(OutgoingMessage("onVolumeChanged", mapOf("volume" to "$v", "muted" to "false")))
            }
            "getVolume" -> send(OutgoingMessage("onVolumeChanged", mapOf("volume" to "100", "muted" to "false")))
            "getSubtitlesTrack" -> send(OutgoingMessage("onSubtitlesTrackChanged", mapOf("videoId" to state.videoId)))
            else -> Log.d(TAG, "unhandled ${msg.name} $p")
        }
    }

    private fun startVideo(videoId: String, startSec: Double) {
        cpn = randomString(16)
        state = State(videoId, state = 3, currentTime = startSec, duration = 0.0)
        currentIndex = videoIds.indexOf(videoId).takeIf { it >= 0 } ?: currentIndex
        onMain { player?.load(videoId, startSec) }
        send(nowPlaying(), stateChange(), hasPrevNext())
    }

    // ------------------------------------------------------------------
    // 送信 (WebView 側の再生状態をスマホへ返す)
    // ------------------------------------------------------------------

    /** WebView のプレーヤーから呼ばれる。state: 1 再生中 / 2 一時停止 / 3 読み込み中 / 0 終了 */
    fun reportPlayback(videoId: String, playerState: Int, currentTime: Double, duration: Double) {
        if (sid.isEmpty() || videoId.isEmpty()) return
        val changedVideo = videoId != state.videoId
        state = State(videoId, playerState, currentTime, duration)
        if (changedVideo) {
            cpn = randomString(16)
            send(nowPlaying(), stateChange())
        } else {
            send(stateChange())
        }
        if (playerState == 0) videoIds.getOrNull(currentIndex + 1)?.let { currentIndex++; startVideo(it, 0.0) }
    }

    private fun sendFullState() = send(
        hasPrevNext(),
        nowPlaying(),
        stateChange(),
        OutgoingMessage("onAutoplayModeChanged", mapOf("autoplayMode" to "UNSUPPORTED")),
    )

    private fun nowPlaying(): OutgoingMessage {
        if (state.videoId.isEmpty()) return OutgoingMessage("nowPlaying")
        return OutgoingMessage(
            "nowPlaying",
            buildMap {
                put("videoId", state.videoId)
                putAll(timing())
                put("state", state.state.toString())
                if (listId.isNotEmpty()) put("listId", listId)
                put("currentIndex", currentIndex.toString())
            },
        )
    }

    private fun stateChange() = OutgoingMessage("onStateChange", timing() + ("state" to state.state.toString()))

    private fun timing(): Map<String, String> {
        val loaded = if (state.state in 1..3) state.duration else 0.0
        return mapOf(
            "currentTime" to "%.3f".format(state.currentTime),
            "duration" to "%.3f".format(state.duration),
            "loadedTime" to "%.3f".format(loaded),
            "seekableStartTime" to "0",
            "seekableEndTime" to "%.3f".format(state.duration),
            "cpn" to cpn,
        )
    }

    private fun hasPrevNext() = OutgoingMessage(
        "onHasPreviousNextChanged",
        mapOf("hasPrevious" to (currentIndex > 0).toString(), "hasNext" to (currentIndex + 1 < videoIds.size).toString()),
    )

    private fun send(vararg messages: OutgoingMessage) {
        if (sid.isEmpty()) return
        scope.launch {
            sendLock.withLock {
                runCatching {
                    val url = bindUrl(
                        "deviceInfo" to DEVICE_INFO, "SID" to sid, "RID" to (rid++).toString(),
                        "AID" to aid.toString(), "gsessionid" to gsessionId,
                    )
                    post(url, encodeOutgoing(messages.toList(), ofs))
                    ofs += messages.size
                }.onFailure { Log.w(TAG, "send failed: ${messages.map { it.name }}", it) }
            }
        }
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    private fun bindUrl(vararg extra: Pair<String, String>): HttpUrl {
        val b = "$BASE/bc/bind".toHttpUrl().newBuilder()
        listOf(
            "device" to "LOUNGE_SCREEN", "id" to deviceId, "obfuscatedGaiaId" to "", "name" to screenName,
            "app" to APP, "theme" to "cl", "capabilities" to "dsp,mic,dpa,ntb", "cst" to "m",
            "mdxVersion" to "2", "loungeIdToken" to loungeToken, "VER" to "8", "v" to "2",
            "zx" to randomString(12), "t" to "1",
        ).forEach { (k, v) -> b.addQueryParameter(k, v) }
        extra.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    private fun get(url: HttpUrl): String =
        http.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("GET ${url.encodedPath} HTTP ${res.code}")
            res.body!!.string()
        }

    private fun post(url: HttpUrl, form: Map<String, String>): String {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return http.newCall(Request.Builder().url(url).post(body).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("POST ${url.encodedPath} HTTP ${res.code}")
            res.body!!.string()
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private fun onMain(block: () -> Unit) {
        mainHandler.post(block)
    }

    private fun randomString(n: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..n).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private val DEVICE_INFO = JSONObject(
        mapOf(
            "brand" to "Generic", "model" to "SmartTV", "year" to 0, "os" to "Android", "osVersion" to "9",
            "chipset" to "", "clientName" to "TVHTML5", "dialAdditionalDataSupportLevel" to "unsupported",
            "mdxDialServerType" to "MDX_DIAL_SERVER_TYPE_UNKNOWN",
        )
    ).toString()
}
