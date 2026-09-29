package com.hirossan.aweauto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hirossan.aweauto.data.Prefs
import com.hirossan.aweauto.data.StreamService

/** 車載画面とスマホの両方で使う設定画面。[onBack] が null なら戻るボタンを出さない */
@Composable
fun SettingsScreen(onBack: (() -> Unit)?) {
    val flags by Prefs.optimizeFlags.collectAsState()
    val history by Prefs.history.collectAsState()

    Column(Modifier.fillMaxSize().background(AweColors.Background)) {
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = AweColors.OnSurface)
                }
                Spacer(Modifier.width(16.dp))
            }
            Text("設定", color = AweColors.OnSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionLabel("表示の最適化") }
            items(StreamService.entries) { service ->
                SettingRow(
                    title = "${service.label} を車の画面向けに最適化",
                    description = service.optimizeSummary,
                    checked = flags[service.id] ?: true,
                    onChange = { Prefs.setOptimized(service, it) },
                )
            }
            item { SectionLabel("履歴") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AweColors.Surface)
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("再生履歴を消去", color = AweColors.OnSurface, fontSize = 16.sp)
                        Text("${history.size} 件", color = AweColors.OnSurfaceDim, fontSize = 13.sp)
                    }
                    Box(
                        Modifier
                            .pressScale { Prefs.clearHistory() }
                            .clip(RoundedCornerShape(50))
                            .background(AweColors.SurfaceHigh)
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    ) {
                        Text("消去", color = AweColors.OnSurface, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = AweColors.Accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
