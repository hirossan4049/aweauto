package com.h1rose.aweauto.hud

import androidx.car.app.navigation.model.Maneuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class NavGuidanceParserTest {
    @Test
    fun googleMapsNotification() {
        // 実機の Google マップ (ナビ開始直後) の通知そのまま
        val g = NavGuidanceParser.parse("0 m", "北に進む", "36 分 · 13 km · 17:54到着予定")!!
        assertEquals("北に進む", g.cue)
        assertEquals(0.0, g.stepMeters!!, 0.0)
        assertEquals(Maneuver.TYPE_DEPART, g.maneuverType)
        assertEquals(36 * 60L, g.remainingSeconds)
        assertEquals(13_000.0, g.remainingMeters!!, 0.0)
        assertEquals(LocalTime.of(17, 54), g.arrival)
    }

    @Test
    fun distances() {
        assertEquals(300.0, NavGuidanceParser.parseDistance("300 m")!!, 0.0)
        assertEquals(1200.0, NavGuidanceParser.parseDistance("1.2 km")!!, 0.001)
        assertEquals(1500.0, NavGuidanceParser.parseDistance("1,500 m")!!, 0.0)
        assertNull(NavGuidanceParser.parseDistance("北に進む"))
    }

    @Test
    fun summaryWithHours() {
        val s = NavGuidanceParser.parseSummary("1 時間 5 分 · 85 km · 19:00到着予定")
        assertEquals(65 * 60L, s.seconds)
        assertEquals(85_000.0, s.meters!!, 0.0)
        assertEquals(LocalTime.of(19, 0), s.arrival)
    }

    @Test
    fun summaryEnglish() {
        val s = NavGuidanceParser.parseSummary("12 min · 4.3 km · 5:54 PM ETA")
        assertEquals(12 * 60L, s.seconds)
        assertEquals(LocalTime.of(17, 54), s.arrival)
    }

    @Test
    fun maneuvers() {
        val cases = mapOf(
            "新御堂筋を右折" to Maneuver.TYPE_TURN_NORMAL_RIGHT,
            "左折して国道1号に入る" to Maneuver.TYPE_TURN_NORMAL_LEFT,
            "左車線を使って右折" to Maneuver.TYPE_TURN_NORMAL_RIGHT,
            "斜め右方向に進む" to Maneuver.TYPE_TURN_SLIGHT_RIGHT,
            "大きく左に曲がる" to Maneuver.TYPE_TURN_SHARP_LEFT,
            "Uターン" to Maneuver.TYPE_U_TURN_RIGHT,
            "右側の車線を維持" to Maneuver.TYPE_KEEP_RIGHT,
            "左の出口から降りる" to Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT,
            "阪神高速に合流" to Maneuver.TYPE_MERGE_SIDE_UNSPECIFIED,
            "分岐を左方向" to Maneuver.TYPE_FORK_LEFT,
            "目的地は右側です" to Maneuver.TYPE_DESTINATION_RIGHT,
            "道なりに進む" to Maneuver.TYPE_STRAIGHT,
            "Turn left onto Main St" to Maneuver.TYPE_TURN_NORMAL_LEFT,
            "Head north" to Maneuver.TYPE_DEPART,
        )
        cases.forEach { (cue, type) -> assertEquals(cue, type, NavGuidanceParser.maneuverType(cue)) }
    }

    @Test
    fun roundabout() {
        val cue = "ロータリーで 2 番目の出口に進む"
        assertEquals(Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW, NavGuidanceParser.maneuverType(cue))
        assertEquals(2, NavGuidanceParser.roundaboutExit(cue))
    }

    @Test
    fun distanceInsideCue() {
        // カーナビアプリによくある「300m先 右方向です」の 1 行に距離と案内が入っている形
        val g = NavGuidanceParser.parse(listOf("300m先 右方向です", "残り 12.5km 18:20 到着"))!!
        assertEquals(300.0, g.stepMeters!!, 0.0)
        assertEquals(Maneuver.TYPE_TURN_NORMAL_RIGHT, g.maneuverType)
        assertEquals(LocalTime.of(18, 20), g.arrival)
    }

    @Test
    fun distanceAfterCue() {
        // 案内文が先、距離が後ろの別の行
        val g = NavGuidanceParser.parse(listOf("梅田新道 左方向", "1.2 km"))!!
        assertEquals("梅田新道 左方向", g.cue)
        assertEquals(1200.0, g.stepMeters!!, 0.001)
        assertEquals(Maneuver.TYPE_TURN_NORMAL_LEFT, g.maneuverType)
    }

    @Test
    fun emptyIsNull() {
        assertNull(NavGuidanceParser.parse(null, " ", null))
    }
}
