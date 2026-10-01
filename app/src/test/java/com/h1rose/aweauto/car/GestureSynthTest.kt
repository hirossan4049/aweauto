package com.h1rose.aweauto.car

import com.h1rose.aweauto.car.TouchEvent.Action.DOWN
import com.h1rose.aweauto.car.TouchEvent.Action.MOVE
import com.h1rose.aweauto.car.TouchEvent.Action.UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureSynthTest {
    private var now = 1_000L
    private val synth = GestureSynth { now }
    private val screen = Box(0f, 0f, 1280f, 600f)

    /** 16ms ごとに、指を上に dy ずつ動かす (コンテンツは下へスクロール) */
    private fun dragUp(times: Int, dy: Float, area: Box = screen): List<TouchEvent> =
        (1..times).flatMap { now += 16; synth.scroll(0f, dy, area) }

    /** 指の位置の総移動量 (持ち直しをまたいで、MOVE ごとの移動を足す) */
    private fun travelledY(events: List<TouchEvent>): Float {
        var total = 0f
        var last: TouchEvent? = null
        for (e in events) {
            if (e.action == MOVE && last != null && last.action != UP) total += last.y - e.y
            last = e
        }
        return total
    }

    @Test
    fun tapPressesThenReleasesShortlyAfter() {
        val e = synth.tap(100f, 200f)
        assertEquals(listOf(DOWN, UP), e.map { it.action })
        assertEquals(GestureSynth.TAP_MS, e[1].eventTime - e[0].eventTime)
        assertTrue(e.all { it.x == 100f && it.y == 200f })
    }

    @Test
    fun dragInsideAPaneStartsWhereYouLastTapped() {
        val webView = Box(0f, 0f, 1280f, 600f)
        synth.tap(300f, 400f)
        val e = synth.scroll(0f, 10f, webView)
        assertEquals(DOWN, e[0].action)
        assertEquals(300f to 400f, e[0].x to e[0].y)
        assertEquals(390f, e.last().y)
    }

    @Test
    fun verticalDragOnComposeScreenStartsInTheMiddle() {
        // 上のタブを押した直後の縦スクロールは、リストの中 (画面の縦の中央) から始める。横位置はタップのまま
        synth.tap(181f, 50f)
        val e = synth.scroll(0f, -30f, screen, wholeScreen = true)
        assertEquals(181f to 300f, e[0].x to e[0].y)
    }

    @Test
    fun horizontalDragOnComposeScreenStaysOnTheTappedRow() {
        // 横の棚をスクロールするときは、タップした行のまま
        synth.tap(500f, 200f)
        val e = synth.scroll(30f, 2f, screen, wholeScreen = true)
        assertEquals(500f to 200f, e[0].x to e[0].y)
    }

    @Test
    fun longScrollAfterTappingNearTheTopKeepsGoing() {
        // タブ (画面の上の方) を押した直後に、指を上へ大きく動かす
        synth.tap(400f, 30f)
        val events = dragUp(times = 40, dy = 25f)
        // 1000px 分ちゃんと動く (以前は上端に着いた時点で止まっていた)
        assertEquals(1000f, travelledY(events), 1f)
        // 端に着いたら離して持ち直している
        assertTrue(events.count { it.action == DOWN } > 1)
        assertTrue(events.all { it.y in 0f..600f })
    }

    @Test
    fun regripStaysInsideThePane() {
        val webView = Box(640f, 0f, 1280f, 600f)
        synth.tap(900f, 100f)
        val events = dragUp(times = 30, dy = 30f, area = webView)
        assertTrue(events.all { webView.contains(it.x, it.y) })
    }

    @Test
    fun flingAddsMotionMatchingTheVelocity() {
        synth.tap(400f, 300f)
        dragUp(times = 3, dy = 20f)
        val e = synth.fling(0f, -3000f)
        assertEquals(listOf(MOVE, MOVE, MOVE, UP), e.map { it.action })
        // 1 フレーム (16ms) ごとに 3000px/秒 → 48px ずつ上へ
        assertEquals(listOf(192f, 144f, 96f, 96f), e.map { it.y })
        assertEquals(listOf(16L, 16L, 0L), e.zipWithNext { a, b -> b.eventTime - a.eventTime })
        assertTrue(synth.fling(0f, -3000f).isEmpty())
    }

    @Test
    fun releasingAfterStoppingHasNoMomentum() {
        synth.tap(400f, 300f)
        dragUp(times = 3, dy = 20f)
        now += GestureSynth.IDLE_MS
        val e = synth.release()
        assertEquals(listOf(UP), e.map { it.action })
        assertEquals(240f, e[0].y)
    }

    @Test
    fun pauseInTheMiddleContinuesFromTheSamePlace() {
        synth.tap(400f, 300f)
        dragUp(times = 3, dy = 20f)
        now += GestureSynth.IDLE_MS
        synth.release()
        now += 100
        val e = synth.scroll(0f, 20f, screen)
        // 最初にタップした位置ではなく、止めた位置から押し直す
        assertEquals(DOWN, e[0].action)
        assertEquals(240f, e[0].y)
    }

    @Test
    fun tapDuringDragReleasesFirst() {
        synth.tap(400f, 300f)
        dragUp(times = 2, dy = 20f)
        val e = synth.tap(10f, 10f)
        assertEquals(listOf(UP, DOWN, UP), e.map { it.action })
    }

    @Test
    fun withoutAnyTapDragStartsInTheMiddle() {
        val e = synth.scroll(0f, 10f, screen)
        assertEquals(640f to 300f, e[0].x to e[0].y)
    }
}
