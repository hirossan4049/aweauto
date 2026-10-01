package com.h1rose.aweauto.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.h1rose.aweauto.BuildConfig
import com.h1rose.aweauto.adblock.AdBlocker
import com.h1rose.aweauto.map.DemoMapPane
import com.h1rose.aweauto.map.MapPanes
import com.h1rose.aweauto.map.NativeAppMapPane
import com.h1rose.aweauto.map.NavApps
import com.h1rose.aweauto.adblock.FilterList
import com.h1rose.aweauto.cast.CastStatus
import com.h1rose.aweauto.cast.LoungeReceiver
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.hud.Hud
import com.h1rose.aweauto.data.StreamService
import com.h1rose.aweauto.shizuku.ShizukuKeeper
import com.h1rose.aweauto.shizuku.ShizukuState
import com.h1rose.aweauto.shizuku.ShizukuStatus
import com.h1rose.aweauto.web.HlsCacheStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

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
    val tverOffline by Prefs.tverOffline.collectAsState()
    val mapApp by Prefs.mapApp.collectAsState()
    val hlsUsage by HlsCacheStore.usage.collectAsState()
    val hlsProgress by HlsCacheStore.progressFlow.collectAsState()
    val context = LocalContext.current
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
            item { SectionLabel("表示の最適化", AweIcons.Tune) }
            items(StreamService.entries) { service ->
                SettingRow(
                    title = "${service.label} を車の画面向けに最適化",
                    description = service.optimizeSummary,
                    checked = flags[service.id] ?: true,
                    onChange = { Prefs.setOptimized(service, it) },
                )
            }
            item { SectionLabel("スマホからキャスト", AweIcons.Cast) }
            item {
                SettingRow(
                    title = "YouTube アプリからのキャストを受ける",
                    description = "テレビコードでリンクすると、同じ Wi-Fi でなくてもキャストボタンからこの画面で再生できます",
                    checked = cast,
                    onChange = { Prefs.setCast(context, it) },
                )
            }
            if (cast) {
                item { PairingCard(castStatus) }
            }
            item { SectionLabel("通信と先読み", AweIcons.SignalCellularAlt) }
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
                    title = "TVer を裏で丸ごと先読みする",
                    description = "再生を始めた番組を最後まで端末に保存しながら再生します。保存済みの部分はトンネルや圏外でも止まりません",
                    checked = tverOffline,
                    onChange = { Prefs.setTverOffline(it) },
                )
            }
            if (tverOffline) {
                item {
                    val progressText = hlsProgress["video"]?.let { " ・ 再生中の番組 $it%" }.orEmpty()
                    ActionRow(
                        title = "先読みした動画",
                        description = "%.1f GB / 2 GB".format(hlsUsage / 1e9) + progressText,
                        action = "消去",
                        onClick = { HlsCacheStore.get(context).clear() },
                    )
                }
            }
            item {
                SettingRow(
                    title = "YouTube の先読みを増やす (実験的)",
                    description = "プレーヤーの先読みを約 30 秒から約 2 分に増やします。YouTube は丸ごとの先読みができません",
                    checked = prefetch,
                    onChange = { Prefs.setPrefetch(it) },
                )
            }
            item { SectionLabel("HUD・メーター", AweIcons.Navigation) }
            item {
                val hud by Prefs.hud.collectAsState()
                SettingRow(
                    title = "道案内を車の HUD・メーターに出す",
                    description = "地図枠の Google マップなどでナビ中の案内 (曲がる方向・距離・到着時刻) を車に送ります。" +
                        "どこまで表示されるかは車によります",
                    checked = hud,
                    onChange = { Prefs.setHud(it) },
                )
            }
            item {
                val listening by Hud.listening.collectAsState()
                val state by Hud.state.collectAsState()
                ActionRow(
                    title = "通知へのアクセス",
                    description = when {
                        !listening -> "未許可。地図アプリのナビ通知を読むために、スマホの設定で aweauto を許可してください"
                        state != null -> "許可済み・案内中: ${state!!.guidance.cue}"
                        else -> "許可済み。地図アプリでナビを始めると案内を送ります"
                    },
                    action = "設定を開く",
                    onClick = {
                        context.startActivity(
                            android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }
            item { SectionLabel("拡張 (Shizuku)", AweIcons.Extension) }
            item {
                val navApps = remember { NavApps.installed(context) }
                val selectedApp = remember(mapApp) { NavApps.resolve(context, mapApp) }
                ChoiceRow(
                    title = "地図枠に出すアプリ",
                    description = "分割表示の地図枠に、端末に入っている地図・カーナビアプリ本体を起動します。Shizuku の接続が必要です。" +
                        "地図枠に出ないアプリは、PC で scripts/aw.sh resizable を一度実行すると出せることがあります",
                    options = navApps.map { it.packageName to it.label },
                    selected = selectedApp.packageName,
                    onSelect = { pkg ->
                        Prefs.setMapApp(pkg)
                        context.getSharedPreferences("aweauto", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("demo_map_pane", false).apply()
                        val next = NavApps.resolve(context, pkg)
                        val current = MapPanes.provider.value
                        if (current is NativeAppMapPane) current.switchApp(next)
                        else MapPanes.register(NativeAppMapPane(context, next))
                    },
                )
            }
            item {
                val status by ShizukuState.status.collectAsState()
                ActionRow(
                    title = "Shizuku",
                    description = when (status) {
                        ShizukuStatus.READY -> "接続済み"
                        ShizukuStatus.NO_PERMISSION -> "起動中。aweauto への許可が必要です"
                        ShizukuStatus.NOT_RUNNING -> "未起動。スマホの再起動後は PC から起動コマンドを実行してください"
                    },
                    action = when (status) {
                        ShizukuStatus.NO_PERMISSION -> "許可"
                        else -> "再確認"
                    },
                    onClick = {
                        if (status == ShizukuStatus.NO_PERMISSION) ShizukuState.requestPermission() else ShizukuState.refresh()
                    },
                )
            }
            item {
                val auto by Prefs.shizukuAutoRestart.collectAsState()
                SettingRow(
                    title = "止まったら自動で起動し直す",
                    description = "車につないだときなどに Shizuku が止まったら、スマホ内の adb から起動し直します。" +
                        "スマホの再起動後に一度だけ PC で scripts/aw.sh tcpip を実行してください",
                    checked = auto,
                    onChange = { Prefs.setShizukuAutoRestart(it) },
                )
            }
            item {
                val keeper by ShizukuKeeper.status.collectAsState()
                ActionRow(
                    title = "今すぐ起動し直す",
                    description = when {
                        keeper.running -> "起動中…"
                        keeper.lastResult != null -> keeper.lastResult!!
                        else -> "初回はスマホに「USB デバッグを許可しますか」が出るので「常に許可」してください"
                    },
                    action = "起動",
                    onClick = { ShizukuKeeper.restartNow() },
                )
            }
            if (BuildConfig.DEBUG) {
                item {
                    val mute by Prefs.devMute.collectAsState()
                    SettingRow(
                        title = "動画をミュートする (開発用)",
                        description = "DHU での確認中に音を出さないようにします",
                        checked = mute,
                        onChange = { Prefs.setDevMute(it) },
                    )
                }
                item {
                    val provider by MapPanes.provider.collectAsState()
                    SettingRow(
                        title = "地図枠にテスト表示を出す (開発用)",
                        description = "MapPaneProvider の描画先とタッチの受け渡しを確かめます",
                        checked = provider is DemoMapPane,
                        onChange = {
                            context.getSharedPreferences("aweauto", android.content.Context.MODE_PRIVATE)
                                .edit().putBoolean("demo_map_pane", it).apply()
                            MapPanes.register(if (it) DemoMapPane() else NativeAppMapPane(context, NavApps.resolve(context, mapApp)))
                        },
                    )
                }
            }
            item { SectionLabel("広告ブロック", AweIcons.Shield) }
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
            item { SectionLabel("履歴", AweIcons.History) }
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

/**
 * スマホからのキャストのつなぎ方。
 * - 同じ Wi-Fi / テザリング: YouTube のキャストボタンに自動で出る (DIAL)。入力不要
 * - それ以外: 初回だけテレビコードでリンク。スマホではコピー、車の画面では同乗者向けに QR も出す
 * 一度つながったスマホがあれば「リンク済み」だけ出してコードは畳む
 */
@Composable
fun PairingCard(status: CastStatus, qrSize: androidx.compose.ui.unit.Dp = 120.dp) {
    val isCar = LocalIsCar.current
    var showCode by remember { mutableStateOf(false) }
    val linked = status.linked.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AweColors.Surface)
            .padding(18.dp),
    ) {
        StatusLine(status)
        if (linked && !showCode) {
            Spacer(Modifier.height(10.dp))
            Text(
                "リンク済み: " + status.linked.joinToString("、"),
                color = AweColors.OnSurface,
                fontSize = 15.sp,
            )
            Text(
                "YouTube アプリのキャストボタンから「${LoungeReceiver.screenName}」を選ぶだけで再生できます",
                color = AweColors.OnSurfaceDim,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            Pill("別のスマホを追加") { showCode = true }
            return@Column
        }

        Spacer(Modifier.height(12.dp))
        Text("同じ Wi-Fi・テザリングにいるスマホ", color = AweColors.Accent, fontSize = 13.sp)
        Text(
            "YouTube アプリのキャストボタンに「${LoungeReceiver.screenName}」が自動で出ます。入力は要りません",
            color = AweColors.OnSurface,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text("それ以外 (はじめの一度だけ)", color = AweColors.Accent, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    status.pairingCode ?: "取得中…",
                    color = AweColors.OnSurface,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                )
                Text(
                    "YouTube アプリ → マイページ → 設定 → テレビで見る → テレビコードでリンク に入力",
                    color = AweColors.OnSurfaceDim,
                    fontSize = 13.sp,
                )
                if (!isCar) {
                    Spacer(Modifier.height(12.dp))
                    CopyAndOpenButton()
                }
            }
            val code = status.pairingCode
            if (isCar && code != null) {
                Spacer(Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCode(code.replace(" ", ""), Modifier.size(qrSize))
                    Spacer(Modifier.height(4.dp))
                    Text("読み取るとコードをコピー", color = AweColors.OnSurfaceDim, fontSize = 11.sp)
                }
            }
        }
        if (linked) {
            Spacer(Modifier.height(12.dp))
            Pill("閉じる") { showCode = false }
        }
    }
}

@Composable
private fun StatusLine(status: CastStatus) {
    val (text, color) = when {
        status.remotes.isNotEmpty() -> "接続中: " + status.remotes.joinToString("、") to AweColors.Accent
        status.online -> "待機中 (オンライン)" to AweColors.Accent
        status.error != null -> "接続できません: ${status.error}" to AweColors.OnSurfaceDim
        else -> "接続中…" to AweColors.OnSurfaceDim
    }
    Text(text, color = color, fontSize = 13.sp)
}

/** その場で新しいコードを取ってコピーし、YouTube アプリを開く (スマホ側だけ) */
@Composable
private fun CopyAndOpenButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Pill("コードをコピーして YouTube を開く", primary = true) {
        scope.launch {
            val code = LoungeReceiver.freshPairingCode()?.replace(" ", "") ?: return@launch
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("テレビコード", code))
            Toast.makeText(context, "コピーしました。テレビコードでリンク の欄に貼り付けてください", Toast.LENGTH_LONG).show()
            context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")?.let(context::startActivity)
        }
    }
}

@Composable
private fun Pill(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .pressScale(onClick)
            .clip(RoundedCornerShape(50))
            .background(if (primary) AweColors.Chip else AweColors.SurfaceHigh)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(label, color = if (primary) AweColors.OnChip else AweColors.OnSurface, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
        // 地図アプリなど選択肢が多いときは折り返す
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
private fun SectionLabel(text: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AweColors.Accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = AweColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
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

/** 車の画面のホームから開く、キャストのつなぎ方だけの画面 */
@Composable
fun PairScreen(onBack: () -> Unit) {
    val status by LoungeReceiver.status.collectAsState()
    Column(Modifier.fillMaxSize().background(AweColors.Background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp + LocalTopEndReserve.current, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.pressScale(onBack).size(40.dp).clip(CircleShape).background(AweColors.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = AweColors.OnSurface)
            }
            Spacer(Modifier.width(16.dp))
            Text("スマホからキャスト", color = AweColors.OnSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.padding(horizontal = 24.dp)) {
            PairingCard(status, qrSize = 170.dp)
        }
    }
}
