package com.h1rose.aweauto.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.map.MapLayout
import com.h1rose.aweauto.map.MapLayouts
import com.h1rose.aweauto.map.MapMode
import com.h1rose.aweauto.map.MapPane
import com.h1rose.aweauto.map.PipCorner

private val DividerWidth = 2.dp
private val PipMargin = 16.dp

/** 画面内の位置と大きさ */
private data class PaneRect(val x: Dp, val y: Dp, val w: Dp, val h: Dp)

/**
 * 車載画面のルート。地図 ON のときは「地図」と「いつもの画面」を並べる (左右 or PiP)。
 * 並べ方を変えても両方とも作り直さず、位置と大きさだけを変える (動画も地図も止まらない)。
 */
@Composable
fun AweRoot() {
    val mapOn by Prefs.splitMap.collectAsState()
    val layout by MapLayouts.layout.collectAsState()
    AweTheme {
        if (!mapOn) {
            Screens()
            return@AweTheme
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(AweColors.Background)) {
            val (mapRect, screenRect) = paneRects(layout, maxWidth, maxHeight)
            val pipIsMap = layout.mode == MapMode.PIP && !layout.mapFirst
            val mapAnim = animateRect(mapRect)
            val screenAnim = animateRect(screenRect)

            // 小窓の方を手前に (zIndex)。同じ高さのときは後に書いた方が上になる
            Box(Modifier.placed(mapAnim).zIndex(if (pipIsMap) 1f else 0f)) {
                MapPane(Modifier.fillMaxSize(), overlay = pipIsMap)
            }
            // Android Auto の戻るボタンは画面の右上に重なる。右上に接していない側は空けておく必要がない
            val touchesTopEnd = screenRect.x + screenRect.w >= maxWidth - 1.dp && screenRect.y < 1.dp
            val reserve = if (touchesTopEnd) LocalTopEndReserve.current else 0.dp
            Box(Modifier.placed(screenAnim).zIndex(if (layout.mode == MapMode.PIP && layout.mapFirst) 1f else 0f)) {
                CompositionLocalProvider(LocalTopEndReserve provides reserve) { Screens() }
            }

            when (layout.mode) {
                MapMode.SPLIT -> SplitDivider(layout, maxWidth)
                MapMode.PIP -> {
                    val small = if (layout.mapFirst) screenRect else mapRect
                    PipControls(small, maxHeight, layout)
                }
            }
        }
    }
}

private fun paneRects(layout: MapLayout, w: Dp, h: Dp): Pair<PaneRect, PaneRect> {
    return when (layout.mode) {
        MapMode.SPLIT -> {
            val leftW = (w - DividerWidth) * layout.ratio
            val rightX = leftW + DividerWidth
            val left = PaneRect(0.dp, 0.dp, leftW, h)
            val right = PaneRect(rightX, 0.dp, w - rightX, h)
            if (layout.mapFirst) left to right else right to left
        }
        MapMode.PIP -> {
            val full = PaneRect(0.dp, 0.dp, w, h)
            // 動画の小窓は 16:9。地図の小窓は、スマホ向けの地図アプリが縦画面前提の作りなので 3:4 の縦長にする
            val mapIsSmall = !layout.mapFirst
            val (pw, ph) = if (mapIsSmall) {
                val height = (h - PipMargin * 2) * (layout.pipScale * 2f).coerceAtMost(1f)
                height * 3f / 4f to height
            } else {
                val width = w * layout.pipScale
                width to width * 9f / 16f
            }
            val x = when (layout.pipCorner) {
                PipCorner.TOP_START, PipCorner.BOTTOM_START -> PipMargin
                else -> w - pw - PipMargin
            }
            val y = when (layout.pipCorner) {
                PipCorner.TOP_START, PipCorner.TOP_END -> PipMargin
                else -> h - ph - PipMargin
            }
            val small = PaneRect(x, y, pw, ph)
            // mapFirst: 地図が全面、いつもの画面 (動画) が小窓
            if (layout.mapFirst) full to small else small to full
        }
    }
}

@Composable
private fun animateRect(r: PaneRect): PaneRect {
    val x by animateDpAsState(r.x, label = "x")
    val y by animateDpAsState(r.y, label = "y")
    val w by animateDpAsState(r.w, label = "w")
    val h by animateDpAsState(r.h, label = "h")
    return PaneRect(x, y, w, h)
}

private fun Modifier.placed(r: PaneRect) = this.offset(r.x, r.y).size(r.w, r.h)

/** 左右の境目。つまみのドラッグで幅を変え、タップで 1/3・1/2・2/3 を切り替える。横に入れ替え・PiP ボタン */
@Composable
private fun SplitDivider(layout: MapLayout, width: Dp) {
    val density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }
    val current by rememberUpdatedState(layout.ratio)
    val x = (width - DividerWidth) * layout.ratio

    Column(
        modifier = Modifier
            .zIndex(2f)
            .fillMaxHeight()
            .offset(x = x - 24.dp)
            .width(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        ControlButton(Icons.Outlined.SwapHoriz, "入れ替え") { MapLayouts.swap() }
        // つまみ
        Box(
            Modifier
                .size(width = 28.dp, height = 72.dp)
                .pointerInput(widthPx) {
                    var ratio = current
                    detectDragGestures(onDragStart = { ratio = current }) { change, drag ->
                        change.consume()
                        ratio += drag.x / widthPx
                        MapLayouts.setRatio(ratio)
                    }
                }
                .pressScale { MapLayouts.cycleRatio() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 8.dp, height = 56.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(AweColors.Chip)
            )
        }
        ControlButton(Icons.Outlined.PictureInPictureAlt, "小窓にする") { MapLayouts.togglePip() }
    }
}

/**
 * 小窓の操作ボタン (移動・入れ替え・大きさ・左右分割に戻す)。小窓の外側に並べる。
 * 小窓の上には何も重ねない (重ねると小窓の中身へのタップを横取りしてしまうため)。
 * 地図が小窓のときは地図がウィンドウより上に描かれ、小窓の中のボタンは隠れてしまうためでもある。
 */
@Composable
private fun PipControls(small: PaneRect, h: Dp, layout: MapLayout) {
    val barHeight = 40.dp
    val top = layout.pipCorner == PipCorner.TOP_START || layout.pipCorner == PipCorner.TOP_END
    // 小窓が上側なら下に、下側なら上にボタンを出す
    val barY = if (top) small.y + small.h + 6.dp else small.y - barHeight - 6.dp
    Row(
        Modifier
            .zIndex(2f)
            .offset(small.x, barY.coerceIn(0.dp, h - barHeight))
            .size(small.w, barHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(Icons.Outlined.OpenWith, "隅を移動", small = true) { MapLayouts.cyclePipCorner() }
        ControlButton(Icons.Outlined.SwapHoriz, "入れ替え", small = true) { MapLayouts.swap() }
        ControlButton(Icons.Outlined.OpenInFull, "大きさ", small = true) { MapLayouts.cyclePipScale() }
        ControlButton(Icons.Outlined.ViewColumn, "左右に並べる", small = true) { MapLayouts.togglePip() }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, small: Boolean = false, onClick: () -> Unit) {
    val size = if (small) 32.dp else 40.dp
    Box(
        Modifier
            .pressScale(onClick)
            .size(size)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(AweColors.SurfaceHigh.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = AweColors.OnSurface, modifier = Modifier.size(if (small) 18.dp else 22.dp))
    }
}

@Composable
private fun Screens() {
    val stack by AweNav.backStack.collectAsState()
    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "route",
    ) { route ->
        when (route) {
            Route.Home -> HomeScreen()
            Route.Settings -> SettingsScreen(onBack = { AweNav.back() })
            Route.Pair -> PairScreen(onBack = { AweNav.back() })
            is Route.Web -> WebScreen(route)
        }
    }
}
