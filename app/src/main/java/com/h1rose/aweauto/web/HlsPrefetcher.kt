package com.h1rose.aweauto.web

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

private const val TAG = "AweHls"

/**
 * HLS (VOD) の再生リストに並んでいるセグメントを、プレーヤーの先読み量に関係なく裏で全部ディスクに落とし、
 * WebView がそのセグメントを要求したらディスクから返す。
 *
 * プレーヤー (MSE) 側の先読みは少量のままなので容量超過は起きず、
 * 落とし終えた部分は圏外でも再生が続く。TVer (STREAKS) の固定 URL のセグメントを想定している。
 */
class HlsPrefetcher(
    context: Context,
    /** 画質の上限 (縦の画素数)。0 (自動) のときも 1 本に絞るため [DEFAULT_MAX_HEIGHT] を使う */
    maxHeight: Int,
) {
    private val pinHeight = if (maxHeight > 0) maxHeight else DEFAULT_MAX_HEIGHT
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = HlsCacheStore.get(context)
    private val http = HlsCacheStore.http

    /** 種類 (video/audio) ごとに 1 本だけ先読みする。画質が切り替わったら古い方は止める */
    private val jobs = ConcurrentHashMap<String, Job>()
    private val jobsUrl = ConcurrentHashMap<String, String>()

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (request.method != "GET") return null
        val url = request.url.toString()
        val path = request.url.path ?: return null
        return when {
            path.endsWith(".m3u8") -> playlist(url, request)
            path.endsWith(".ts") || path.endsWith(".aac") || path.endsWith(".m4s") ||
                path.endsWith(".mp4") || path.endsWith(".key") -> segment(url, request)
            else -> null
        }
    }

    fun release() = scope.cancel()

    private fun playlist(url: String, request: WebResourceRequest): WebResourceResponse? {
        val text = try {
            fetchText(url, request.requestHeaders).also { store.putText(url, it) }
        } catch (e: IOException) {
            // 圏外で再生リストを取り直そうとしたときは前回の内容を返す
            store.getText(url) ?: return null
        }
        val body = when {
            "#EXTINF" in text -> text.also { startPrefetch(url, it) }
            // マスターは画質を 1 本に絞る。途中で画質が切り替わると先読みした分が無駄になるため
            "#EXT-X-STREAM-INF" in text -> pinSingleVariant(text, pinHeight)
            else -> text
        }
        return response(ByteArrayInputStream(body.toByteArray()), "application/vnd.apple.mpegurl", request)
    }

    private fun segment(url: String, request: WebResourceRequest): WebResourceResponse? {
        val mime = if (url.endsWith(".key")) "application/octet-stream" else "video/mp2t"
        val range = request.requestHeaders.entries.firstOrNull { it.key.equals("Range", true) }?.value

        val file = store.cachedFile(url)
        if (file != null) {
            return if (range == null) {
                response(file.inputStream(), mime, request)
            } else {
                partial(file, range, mime, request)
            }
        }

        if (range != null) {
            // Range の初回要求をここで丸ごと保存完了まで待つと再生が詰まる。
            // この要求は WebView に任せ、裏で全体を保存して次回以降に使う。
            scope.launch { runCatching { store.fetchToCache(url, request.requestHeaders) } }
            return null
        }

        return try {
            response(store.streamToCache(url, request.requestHeaders), mime, request)
        } catch (e: IOException) {
            Log.w(TAG, "segment unavailable: $url (${e.message})")
            null
        }
    }

    private fun partial(file: File, range: String, mime: String, request: WebResourceRequest): WebResourceResponse {
        val length = file.length()
        val (startStr, endStr) = range.removePrefix("bytes=").split('-', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val start = startStr.toLongOrNull()?.coerceIn(0L, (length - 1).coerceAtLeast(0L)) ?: 0L
        val end = (endStr.toLongOrNull() ?: (length - 1)).coerceAtMost(length - 1)
        val headers = corsHeaders(request) + mapOf("Content-Range" to "bytes $start-$end/$length")
        return WebResourceResponse(mime, null, 206, "Partial Content", headers, RangeInputStream(file, start, end))
    }

    private class RangeInputStream(file: File, start: Long, private val end: Long) : InputStream() {
        private val raf = RandomAccessFile(file, "r")
        private var pos = start

        init {
            raf.seek(start)
        }

        override fun read(): Int {
            if (pos > end) return -1
            val v = raf.read()
            if (v >= 0) pos++
            return v
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (pos > end) return -1
            val max = minOf(length.toLong(), end - pos + 1).toInt()
            val n = raf.read(buffer, offset, max)
            if (n > 0) pos += n
            return n
        }

        override fun close() {
            raf.close()
        }
    }

    private fun startPrefetch(playlistUrl: String, text: String) {
        val kind = when {
            "/audio/" in playlistUrl -> "audio"
            "/video/" in playlistUrl -> "video"
            else -> playlistUrl
        }
        val urls = parseMediaPlaylist(playlistUrl, text)
        val previous = jobs[kind]
        if (previous?.isActive == true && jobsUrl[kind] == playlistUrl) return
        previous?.cancel()
        jobsUrl[kind] = playlistUrl
        jobs[kind] = scope.launch {
            Log.i(TAG, "prefetch $kind: ${urls.size} files")
            var done = 0
            for (u in urls) {
                var backoff = 2_000L
                while (isActive) {
                    try {
                        store.fetchToCache(u, emptyMap())
                        break
                    } catch (e: IOException) {
                        // 圏外などでは同じセグメントを待ってから取り直す (飛ばさない)
                        delay(backoff)
                        backoff = (backoff * 2).coerceAtMost(30_000L)
                    }
                }
                done++
                HlsCacheStore.progress.value = HlsCacheStore.progress.value + (kind to done * 100 / urls.size)
            }
            Log.i(TAG, "prefetch $kind done")
            store.trim()
        }
    }

    private fun fetchText(url: String, headers: Map<String, String>): String {
        val req = Request.Builder().url(url).apply { forwardHeaders(headers) }.build()
        return http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            res.body!!.string()
        }
    }

    private fun response(body: java.io.InputStream, mime: String, request: WebResourceRequest) =
        WebResourceResponse(mime, null, 200, "OK", corsHeaders(request), body)

    private fun corsHeaders(request: WebResourceRequest): Map<String, String> {
        val origin = request.requestHeaders.entries.firstOrNull { it.key.equals("Origin", true) }?.value ?: "*"
        return mapOf(
            "Access-Control-Allow-Origin" to origin,
            "Access-Control-Allow-Credentials" to "true",
            "Cache-Control" to "no-store",
        )
    }
}

private const val DEFAULT_MAX_HEIGHT = 720

/**
 * マスター再生リストを「上限以下で一番高い画質」1 本と、それが参照する音声だけに絞る。
 * コンテンツステアリング (配信経路の切り替え) も外して、再生中に取得先が変わらないようにする。
 */
internal fun pinSingleVariant(master: String, maxHeight: Int): String {
    val lines = master.lines()
    data class Variant(val inf: String, val uri: String, val height: Int, val audio: String?)
    val variants = lines.indices
        .filter { lines[it].startsWith("#EXT-X-STREAM-INF") && it + 1 < lines.size }
        .map { i ->
            val inf = lines[i]
            val height = Regex("""RESOLUTION=\d+x(\d+)""").find(inf)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val audio = Regex("""AUDIO="([^"]+)"""").find(inf)?.groupValues?.get(1)
            Variant(inf, lines[i + 1].trim(), height, audio)
        }
    if (variants.isEmpty()) return master
    val chosen = variants.filter { it.height <= maxHeight }.maxByOrNull { it.height }
        ?: variants.minBy { it.height }

    val out = StringBuilder()
    var skipNext = false
    for (line in lines) {
        when {
            skipNext -> skipNext = false
            line.startsWith("#EXT-X-CONTENT-STEERING") -> Unit
            line.startsWith("#EXT-X-STREAM-INF") -> skipNext = true
            line.startsWith("#EXT-X-MEDIA") && "TYPE=AUDIO" in line &&
                chosen.audio != null && "GROUP-ID=\"${chosen.audio}\"" !in line -> Unit
            else -> out.appendLine(line)
        }
    }
    out.appendLine(chosen.inf).appendLine(chosen.uri)
    return out.toString()
}

/** 再生リストから鍵とセグメントの URL を再生順に取り出す */
internal fun parseMediaPlaylist(playlistUrl: String, text: String): List<String> {
    val base = java.net.URI(playlistUrl)
    val out = LinkedHashSet<String>()
    text.lineSequence().map { it.trim() }.forEach { line ->
        when {
            line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-MAP") -> {
                Regex("""URI="([^"]+)"""").find(line)?.groupValues?.get(1)?.let { out += base.resolve(it).toString() }
            }
            line.isNotEmpty() && !line.startsWith("#") -> out += base.resolve(line).toString()
        }
    }
    return out.toList()
}

private fun Request.Builder.forwardHeaders(headers: Map<String, String>) {
    headers.forEach { (k, v) ->
        if (k.equals("Range", true) || k.equals("Accept-Encoding", true)) return@forEach
        header(k, v)
    }
}

/** セグメントのディスクキャッシュ。上限を超えたら古いものから消す */
class HlsCacheStore internal constructor(private val dir: File) {
    /**
     * 同じセグメントを先読みとプレーヤーが同時に書かないための排他。
     * 再生経路では WebView が別スレッド (Chrome_IOThread) でストリームを閉じたときに解放するので、
     * 取ったスレッド以外からも解放できる Semaphore を使う (ReentrantLock だと IllegalMonitorStateException で落ちる)。
     * URL ごとに作って消すと、古いものを待っている間に別の排他ができて二重に書くことがあるので、
     * URL のハッシュで選ぶ固定個にして消さない。
     */
    private val locks = Array(LOCK_STRIPES) { Semaphore(1) }

    private fun lockFor(url: String) = locks[Math.floorMod(url.hashCode(), LOCK_STRIPES)]

    fun cachedFile(url: String): File? =
        fileFor(url).takeIf { it.exists() }?.also { it.setLastModified(System.currentTimeMillis()) }

    /**
     * 再生経路用。ネットワークから読みながらプレーヤーに渡し、同時にディスクにも書く。
     * 最後まで読まれてから閉じられたときだけキャッシュとして残す。
     */
    fun streamToCache(url: String, headers: Map<String, String>): InputStream {
        cachedFile(url)?.let { return it.inputStream() }
        val file = fileFor(url)
        val lock = lockFor(url)
        if (!lock.tryAcquire()) {
            // 先読み側が同じ URL を保存中なら、再生経路は待たせず WebView の通常通信へ逃がす
            throw IOException("cache write already in progress")
        }
        var handedOff = false
        try {
            cachedFile(url)?.let { return it.inputStream() }
            Log.d(TAG, "stream ${url.substringAfterLast('/')}")
            val req = Request.Builder().url(url).apply { forwardHeaders(headers) }.build()
            val res = http.newCall(req).execute()
            if (!res.isSuccessful) {
                res.close()
                throw IOException("HTTP ${res.code} $url")
            }
            val tmp = File(dir, file.name + ".part")
            val out = try {
                tmp.outputStream()
            } catch (e: IOException) {
                res.close()
                throw e
            }
            val stream = CachingInputStream(res.body!!.byteStream(), out) { complete ->
                try {
                    res.close()
                    if (complete && tmp.renameTo(file)) usage.value = sizeBytes() else tmp.delete()
                } finally {
                    lock.release()
                }
            }
            handedOff = true
            return stream
        } finally {
            // ストリームを渡したら、解放はストリームを閉じたとき (CachingInputStream.close) に行う
            if (!handedOff) lock.release()
        }
    }

    /** 先読み用。保存し終わるまで待つ。プレーヤーが同じセグメントを保存中なら、それが終わるのを待って結果を使う */
    fun fetchToCache(url: String, headers: Map<String, String>): File {
        cachedFile(url)?.let { return it }
        val file = fileFor(url)
        val lock = lockFor(url)
        lock.acquire()
        try {
            if (file.exists()) return file
            Log.d(TAG, "download ${url.substringAfterLast('/')}")
            val req = Request.Builder().url(url).apply { forwardHeaders(headers) }.build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw IOException("HTTP ${res.code} $url")
                val tmp = File(dir, file.name + ".part")
                res.body!!.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (!tmp.renameTo(file)) throw IOException("rename failed")
            }
            return file
        } finally {
            lock.release()
        }
    }

    fun putText(url: String, text: String) = File(dir, key(url) + ".m3u8").writeText(text)

    fun getText(url: String): String? = File(dir, key(url) + ".m3u8").takeIf { it.exists() }?.readText()

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
        usage.value = 0L
    }

    /** 上限を超えていたら最後に使った時刻が古い順に消す */
    fun trim() {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= MAX_BYTES) break
            total -= f.length()
            f.delete()
        }
        usage.value = total
    }

    private fun fileFor(url: String) = File(dir, key(url))

    private fun key(url: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.substringBefore('?').toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private class CachingInputStream(
        private val input: InputStream,
        private val output: java.io.OutputStream,
        private val onClose: (complete: Boolean) -> Unit,
    ) : InputStream() {
        private var complete = false
        private var closed = false

        override fun read(): Int {
            val v = input.read()
            if (v >= 0) output.write(v) else complete = true
            return v
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val n = input.read(buffer, offset, length)
            if (n > 0) output.write(buffer, offset, n) else if (n < 0) complete = true
            return n
        }

        override fun close() {
            if (closed) return
            closed = true
            runCatching { input.close() }
            runCatching { output.close() }
            onClose(complete)
        }
    }

    companion object {
        const val MAX_BYTES = 2L * 1024 * 1024 * 1024
        private const val LOCK_STRIPES = 256

        val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /** 使用量 (設定画面の表示用) */
        val usage = MutableStateFlow(0L)

        /** 種類ごとの先読みの進み具合 (%) */
        val progress = MutableStateFlow<Map<String, Int>>(emptyMap())
        val progressFlow: StateFlow<Map<String, Int>> get() = progress.asStateFlow()

        @Volatile
        private var instance: HlsCacheStore? = null

        fun get(context: Context): HlsCacheStore = instance ?: synchronized(this) {
            instance ?: HlsCacheStore(File(context.cacheDir, "hls").apply { mkdirs() }).also {
                // 書きかけのまま落ちたときの .part を消す (まだ誰も書いていない起動時だけ消せる)
                it.dir.listFiles { f -> f.name.endsWith(".part") }?.forEach(File::delete)
                instance = it
                usage.value = it.sizeBytes()
            }
        }
    }
}
