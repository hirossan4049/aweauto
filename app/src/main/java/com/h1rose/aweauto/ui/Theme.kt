package com.h1rose.aweauto.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Google TV のダークテーマに寄せた配色 */
object AweColors {
    val Background = Color(0xFF111318)
    val Surface = Color(0xFF1B1D24)
    val SurfaceHigh = Color(0xFF2A2D36)
    val OnSurface = Color(0xFFE3E3E8)
    val OnSurfaceDim = Color(0xFF9AA0AC)
    val Accent = Color(0xFF8AB4F8)
    val Chip = Color(0xFFE3E3E8)
    val OnChip = Color(0xFF111318)
}

@Composable
fun AweTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AweColors.Accent,
            background = AweColors.Background,
            surface = AweColors.Surface,
            onSurface = AweColors.OnSurface,
        ),
        content = content,
    )
}

/**
 * 押している間だけ少し縮む。車載画面はフォーカス移動が無くタッチのみなので、
 * TV のフォーカス拡大の代わりにこれで押した感を出す。
 */
fun Modifier.pressScale(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "pressScale")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** 車の画面で描いているか (スマホのプレビュー・設定画面では false) */
val LocalIsCar = staticCompositionLocalOf { false }

/** 画面右上で Android Auto のボタンと重ならないように空けておく幅 (スマホのプレビューでは 0) */
val LocalTopEndReserve = staticCompositionLocalOf { 0.dp }
