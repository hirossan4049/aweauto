package com.h1rose.aweauto.data

import org.junit.Assert.assertEquals
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
}
