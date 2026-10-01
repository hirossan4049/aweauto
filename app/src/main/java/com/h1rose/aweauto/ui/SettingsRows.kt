package com.h1rose.aweauto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.h1rose.aweauto.R

// 設定画面とキャストのつなぎ方の画面で使う部品

/** 画面の一番上の行 (戻るボタンと題名)。[onBack] が null なら戻るボタンを出さない */
@Composable
internal fun ScreenHeader(title: String, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp + LocalTopEndReserve.current, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.pressScale(onBack).size(40.dp).clip(CircleShape).background(AweColors.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = AweColors.OnSurface,
                )
            }
            Spacer(Modifier.width(16.dp))
        }
        Text(title, color = AweColors.OnSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SectionLabel(text: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AweColors.Accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = AweColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** ON/OFF の行。行のどこを押しても切り替わる */
@Composable
internal fun SettingRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AweColors.Surface)
            .pressScale { onChange(!checked) }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AweColors.OnSurface, fontSize = 16.sp)
            Text(description, color = AweColors.OnSurfaceDim, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AweColors.Accent),
        )
    }
}

/** 説明と、右端にボタンが1つある行 */
@Composable
internal fun ActionRow(title: String, description: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AweColors.Surface)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AweColors.OnSurface, fontSize = 16.sp)
            Text(description, color = AweColors.OnSurfaceDim, fontSize = 13.sp)
        }
        Pill(action, onClick = onClick)
    }
}

/** 選択肢から1つ選ぶ行。地図アプリなど選択肢が多いときは折り返す */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceRow(
    title: String,
    description: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AweColors.Surface)
            .padding(18.dp),
    ) {
        Text(title, color = AweColors.OnSurface, fontSize = 16.sp)
        Text(description, color = AweColors.OnSurfaceDim, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, label) -> Pill(label, primary = value == selected) { onSelect(value) } }
        }
    }
}

/** 角の丸いボタン。primary は塗りつぶし (選択中・おすすめの操作) */
@Composable
internal fun Pill(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .pressScale(onClick)
            .clip(RoundedCornerShape(50))
            .background(if (primary) AweColors.Chip else AweColors.SurfaceHigh)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (primary) AweColors.OnChip else AweColors.OnSurface, fontSize = 14.sp)
    }
}
