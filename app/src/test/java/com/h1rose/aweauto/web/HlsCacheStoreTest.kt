package com.h1rose.aweauto.web

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * WebView は shouldInterceptRequest と別のスレッド (Chrome_IOThread) でストリームを閉じる。
 * 実機ではそこで排他の解放に失敗してアプリが落ちたので、同じ状況をここで再現する。
 */
class HlsCacheStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var store: HlsCacheStore
    private val segment = ByteArray(64 * 1024) { (it % 251).toByte() }
    private val pool = Executors.newFixedThreadPool(2)

    @Before
    fun setUp() {
        server.start()
        store = HlsCacheStore(tmp.newFolder("hls"))
    }

    @After
    fun tearDown() {
        pool.shutdownNow()
        server.shutdown()
    }

    private fun url(name: String = "seg1.ts") = server.url("/$name").toString()

    private fun enqueueSegment(throttle: Boolean = false) {
        server.enqueue(
            MockResponse().setBody(Buffer().write(segment)).apply {
                if (throttle) throttleBody(16 * 1024, 200, TimeUnit.MILLISECONDS)
            },
        )
    }

    /** 別スレッドで読み切って閉じる (WebView と同じ) */
    private fun readOnOtherThread(stream: java.io.InputStream): ByteArray =
        pool.submit<ByteArray> { stream.use { it.readBytes() } }.get(5, TimeUnit.SECONDS)

    @Test
    fun streamClosedOnAnotherThreadIsCachedAndReleased() {
        enqueueSegment()
        val stream = store.streamToCache(url(), emptyMap())
        assertArrayEquals(segment, readOnOtherThread(stream))

        // 保存されていて、排他も解放されている (先読みが待たされず、通信もしない)
        val file = store.cachedFile(url())
        assertNotNull(file)
        assertArrayEquals(segment, file!!.readBytes())
        assertEquals(file, pool.submit<java.io.File> { store.fetchToCache(url(), emptyMap()) }.get(2, TimeUnit.SECONDS))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun streamClosedHalfwayIsNotCachedButReleased() {
        enqueueSegment()
        val stream = store.streamToCache(url(), emptyMap())
        pool.submit { stream.use { it.read(ByteArray(1024)) } }.get(5, TimeUnit.SECONDS)

        // 途中までのデータはキャッシュに残さない
        assertEquals(null, store.cachedFile(url()))
        assertFalse(tmp.root.walk().any { it.name.endsWith(".part") })
        // 排他は解放されているので、もう一度取りに行ける
        enqueueSegment()
        assertArrayEquals(segment, readOnOtherThread(store.streamToCache(url(), emptyMap())))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun prefetchWaitsForPlaybackAndReusesIt() {
        enqueueSegment(throttle = true)
        val stream = store.streamToCache(url(), emptyMap())
        // 再生経路が保存中に先読みが来たら、終わるまで待ってその結果を使う
        val prefetch = pool.submit<java.io.File> { store.fetchToCache(url(), emptyMap()) }
        assertArrayEquals(segment, readOnOtherThread(stream))
        assertArrayEquals(segment, prefetch.get(5, TimeUnit.SECONDS).readBytes())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun playbackDoesNotWaitForPrefetch() {
        enqueueSegment(throttle = true)
        val prefetch = pool.submit<java.io.File> { store.fetchToCache(url(), emptyMap()) }
        while (server.requestCount == 0) Thread.sleep(10)
        // 先読みが保存中なら、再生経路は待たずに WebView の通常の通信へ回す
        val error = runCatching { store.streamToCache(url(), emptyMap()) }.exceptionOrNull()
        assertTrue(error is IOException)
        assertArrayEquals(segment, prefetch.get(5, TimeUnit.SECONDS).readBytes())
    }

    @Test
    fun httpErrorReleasesLock() {
        server.enqueue(MockResponse().setResponseCode(403))
        assertTrue(runCatching { store.streamToCache(url(), emptyMap()) }.exceptionOrNull() is IOException)
        enqueueSegment()
        assertArrayEquals(segment, store.fetchToCache(url(), emptyMap()).readBytes())
    }
}
