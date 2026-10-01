package com.h1rose.aweauto.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapDensityTest {
    @Test
    fun keepsDensityWhenPaneIsLargeEnough() {
        // 左右分割 (半分) は 638x600 px → 240dpi で 425x400dp あるのでそのまま
        assertEquals(240, mapDensityDpi(638, 600, 240))
    }

    @Test
    fun lowersDensityForSmallPip() {
        // 小窓 461x259 px は 240dpi だと 307x172dp と狭いので密度を下げる (高さが効く)
        assertEquals(129, mapDensityDpi(461, 259, 240))
    }

    @Test
    fun portraitPipLooksLikeAPhoneScreen() {
        // 縦長の小窓 405x540 px は 240dpi だと 270x360dp と狭い。180dpi にして 360x480dp として描かせる
        assertEquals(180, mapDensityDpi(405, 540, 240))
    }

    @Test
    fun doesNotGoBelowMinimum() {
        assertEquals(110, mapDensityDpi(200, 120, 240))
    }
}
