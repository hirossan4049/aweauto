package com.h1rose.aweauto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.h1rose.aweauto.adblock.AdBlocker
import com.h1rose.aweauto.adblock.FilterList
import com.h1rose.aweauto.cast.CastStatus
import com.h1rose.aweauto.cast.LoungeReceiver
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.data.StreamService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 車載画面とスマホの両方で使う設定画面。[onBack] が null なら戻るボタンを出さない */
@Composable
fun SettingsScreen(onBack: (() -> Unit)?) {
    val flags by Prefs.optimizeFlags.collectAsState()
    val history by Prefs.history.collectAsState()
    val adblock by Prefs.adblock.collectAsState()
    val lists by Prefs.filterLists.collectAsState()
    val adStatus by AdBlocker.status.collectAsState()
    val maxHeight by Prefs.maxHeight.collectAsState()
    val prefetch by Prefs.prefetch.collectAsState()
    val cast by Prefs.cast.collectAsState()
    val castStatus by LoungeReceiver.status.collectAsState()

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
            item { SectionLabel("スマホからキャスト") }
            item {
                SettingRow(
                    title = "YouTube アプリからのキャストを受ける",
                    description = "テレビコードでリンクすると、同じ Wi-Fi でなくてもキャストボタンからこの画面で再生できます",
                    checked = cast,
                    onChange = { Prefs.setCast(it) },
                )
            }
            if (cast) {
                item { PairingCard(castStatus) }
            }
            item { SectionLabel("通信と先読み") }
            item {
                ChoiceRow(
                    title = "画質の上限",
                    description = "低いほど同じ通信量で長く先読みでき、電波が弱くても止まりにくくなります",
                    options = listOf(0 to "自動", 720 to "720p", 480 to "480p", 360 to "360p"),
                    selected = maxHeight,
                    onSelect = { Prefs.setMaxHeight(it) },
                )
            }
            item {
                SettingRow(
                    title = "電波の良いうちに先読みする",
                    description = "TVer は約 7 分、YouTube は約 2 分先まで読み込みます (標準はどちらも 20〜30 秒)",
                    checked = prefetch,
                    onChange = { Prefs.setPrefetch(it) },
                )
            }
            item { SectionLabel("広告ブロック") }
            item {
                SettingRow(
                    title = "広告ブロック",
                    description = "フィルタリストに載っているドメインへの通信を止め、YouTube の動画広告を取り除きます",
                    checked = adblock,
                    onChange = { Prefs.setAdblock(it) },
                )
            }
            if (adblock) {
                items(FilterList.entries) { list ->
                    SettingRow(
                        title = list.label,
                        description = list.description,
                        checked = list in lists,
                        onChange = { Prefs.setFilterList(list, it) },
                    )
                }
                item {
                    ActionRow(
                        title = "フィルタリストを更新",
                        description = when {
                            adStatus.updating -> "更新中…"
                            adStatus.error != null -> adStatus.error!!
                            adStatus.lastUpdated == 0L -> "${adStatus.domainCount} ドメイン (未取得)"
                            else -> "${adStatus.domainCount} ドメイン ・ " +
                                SimpleDateFormat("M/d H:mm", Locale.JAPAN).format(Date(adStatus.lastUpdated)) + " 更新"
                        },
                        action = "更新",
                        onClick = { AdBlocker.update(lists) },
                    )
                }
            }
            item { SectionLabel("履歴") }
            item {
                ActionRow(
                    title = "再生履歴を消去",
                    description = "${history.size} 件",
                    action = "消去",
                    onClick = { Prefs.clearHistory() },
                )
            }
        }
    }
}

/** テレビコードと接続状態 */
@Composable
private fun PairingCard(status: CastStatus) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AweColors.Surface)
            .padding(18.dp),
    ) {
        Text("テレビコード", color = AweColors.OnSurfaceDim, fontSize = 13.sp)
        Text(
            status.pairingCode ?: "取得中…",
            color = AweColors.OnSurface,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "YouTube アプリ → 設定 → テレビで見る → テレビコードでリンク で入力。一度リンクすれば以後はキャストボタンに「aweauto (車)」が出ます",
            color = AweColors.OnSurfaceDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        val line = when {
            status.remotes.isNotEmpty() -> "接続中: " + status.remotes.joinToString("、")
            status.online -> "待機中 (オンライン)"
            status.error != null -> "接続できません: ${status.error}"
            else -> "接続中…"
        }
        Text(line, color = if (status.online) AweColors.Accent else AweColors.OnSurfaceDim, fontSize = 13.sp)
    }
}

@Composable
private fun <T> ChoiceRow(
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                val on = value == selected
                Box(
                    Modifier
                        .pressScale { onSelect(value) }
                        .clip(RoundedCornerShape(50))
                        .background(if (on) AweColors.Chip else AweColors.SurfaceHigh)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(label, color = if (on) AweColors.OnChip else AweColors.OnSurface, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(title: String, description: String, action: String, onClick: () -> Unit) {
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
        Box(
            Modifier
                .pressScale(onClick)
                .clip(RoundedCornerShape(50))
                .background(AweColors.SurfaceHigh)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(action, color = AweColors.OnSurface, fontSize = 14.sp)
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
