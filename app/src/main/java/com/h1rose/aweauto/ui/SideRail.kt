package com.h1rose.aweauto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.h1rose.aweauto.R
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.data.StreamService

/** Web 画面の左端の操作レールの幅 */
internal val RailWidth = 64.dp

/** Web 画面の左端の操作レール (戻る・ホーム・再読込・地図・最適化の表示) */
@Composable
internal fun SideRail(
    service: StreamService,
    optimized: Boolean,
    translucent: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onReload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(RailWidth)
            .background(if (translucent) AweColors.Surface.copy(alpha = 0.85f) else AweColors.Surface)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BrandIcon(service, 40.dp)
        Spacer(Modifier.height(6.dp))
        RailButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onClick = onBack)
        RailButton(Icons.Filled.Home, stringResource(R.string.home), onClick = onHome)
        RailButton(Icons.Filled.Refresh, stringResource(R.string.reload), onClick = onReload)
        RailButton(AweIcons.Map, stringResource(R.string.show_map), onClick = { Prefs.toggleSplitMap() })
        Spacer(Modifier.weight(1f))
        if (optimized) {
            Text(stringResource(R.string.tweaks), color = AweColors.Accent, fontSize = 10.sp)
        }
    }
}

/** 没入モード中に左端に出す小さなつまみ。押すとレールを数秒だけ出す */
@Composable
internal fun RailHandle(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .pressScale(onClick)
            .size(width = 36.dp, height = 96.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = 6.dp)
                .size(width = 6.dp, height = 56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.35f))
        )
    }
}

@Composable
internal fun RailButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .pressScale(onClick)
            .size(44.dp)
            .clip(CircleShape)
            .background(AweColors.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = AweColors.OnSurface)
    }
}
