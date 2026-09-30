package com.h1rose.aweauto.map

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MapMode { SPLIT, PIP }

enum class PipCorner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

/**
 * 地図と動画の並べ方。
 * - SPLIT: 左右に並べる。[mapFirst] なら地図が左、[ratio] は左側の幅の割合
 * - PIP: 片方を全面、もう片方を小窓。[mapFirst] なら地図が全面 (動画が小窓)
 */
data class MapLayout(
    val mode: MapMode = MapMode.SPLIT,
    val mapFirst: Boolean = true,
    val ratio: Float = 0.5f,
    val pipCorner: PipCorner = PipCorner.BOTTOM_END,
    /** 小窓の幅 (画面幅に対する割合) */
    val pipScale: Float = 0.36f,
) {
    companion object {
        const val MIN_RATIO = 0.25f
        const val MAX_RATIO = 0.75f
        val RATIO_PRESETS = listOf(1f / 3f, 0.5f, 2f / 3f)
        val PIP_SCALES = listOf(0.28f, 0.36f, 0.46f)
    }
}

object MapLayouts {
    private lateinit var sp: SharedPreferences
    private val _layout = MutableStateFlow(MapLayout())
    val layout: StateFlow<MapLayout> = _layout.asStateFlow()

    fun init(context: Context) {
        sp = context.getSharedPreferences("aweauto", Context.MODE_PRIVATE)
        _layout.value = MapLayout(
            mode = runCatching { MapMode.valueOf(sp.getString("map_mode", null)!!) }.getOrDefault(MapMode.SPLIT),
            mapFirst = sp.getBoolean("map_first", true),
            ratio = sp.getFloat("map_ratio", 0.5f),
            pipCorner = runCatching { PipCorner.valueOf(sp.getString("pip_corner", null)!!) }.getOrDefault(PipCorner.BOTTOM_END),
            pipScale = sp.getFloat("pip_scale", 0.36f),
        )
    }

    private fun update(block: (MapLayout) -> MapLayout) {
        val next = block(_layout.value)
        _layout.value = next
        if (::sp.isInitialized) {
            sp.edit()
                .putString("map_mode", next.mode.name)
                .putBoolean("map_first", next.mapFirst)
                .putFloat("map_ratio", next.ratio)
                .putString("pip_corner", next.pipCorner.name)
                .putFloat("pip_scale", next.pipScale)
                .apply()
        }
    }

    /** 左右 (PiP では大小) を入れ替える */
    fun swap() = update { it.copy(mapFirst = !it.mapFirst, ratio = if (it.mode == MapMode.SPLIT) 1f - it.ratio else it.ratio) }

    fun togglePip() = update { it.copy(mode = if (it.mode == MapMode.PIP) MapMode.SPLIT else MapMode.PIP) }

    /** 境目のドラッグ中に呼ぶ */
    fun setRatio(ratio: Float) = update { it.copy(ratio = ratio.coerceIn(MapLayout.MIN_RATIO, MapLayout.MAX_RATIO)) }

    /** 境目のタップで 1/3 → 1/2 → 2/3 と切り替える */
    fun cycleRatio() = update { l ->
        val next = MapLayout.RATIO_PRESETS.firstOrNull { it > l.ratio + 0.01f } ?: MapLayout.RATIO_PRESETS.first()
        l.copy(ratio = next)
    }

    fun cyclePipScale() = update { l ->
        val next = MapLayout.PIP_SCALES.firstOrNull { it > l.pipScale + 0.01f } ?: MapLayout.PIP_SCALES.first()
        l.copy(pipScale = next)
    }

    fun setPipCorner(corner: PipCorner) = update { it.copy(pipCorner = corner) }
}
