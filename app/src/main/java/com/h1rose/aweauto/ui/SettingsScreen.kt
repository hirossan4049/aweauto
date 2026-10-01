package com.h1rose.aweauto.ui

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.h1rose.aweauto.BuildConfig
import com.h1rose.aweauto.LoginActivity
import com.h1rose.aweauto.R
import com.h1rose.aweauto.adblock.AdBlocker
import com.h1rose.aweauto.adblock.FilterList
import com.h1rose.aweauto.cast.LoungeReceiver
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.data.StreamService
import com.h1rose.aweauto.hud.Hud
import com.h1rose.aweauto.map.DemoMapPane
import com.h1rose.aweauto.map.MapPanes
import com.h1rose.aweauto.map.NativeAppMapPane
import com.h1rose.aweauto.map.NavApps
import com.h1rose.aweauto.shizuku.ShizukuKeeper
import com.h1rose.aweauto.shizuku.ShizukuState
import com.h1rose.aweauto.shizuku.ShizukuStatus
import com.h1rose.aweauto.web.HlsCacheStore
import com.h1rose.aweauto.web.YouTubeAccount
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** スマホの設定画面。[onBack] が null なら戻るボタンを出さない */
@Composable
fun SettingsScreen(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    // 出す行が変わる設定 (OFF なら下の行ごと隠す)
    val cast by Prefs.cast.collectAsState()
    val offlineCache by Prefs.offlineCache.collectAsState()
    val adblock by Prefs.adblock.collectAsState()
    val hud by Prefs.hud.collectAsState()
    val hudCustom by Prefs.hudCustom.collectAsState()
    var details by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(AweColors.Background)) {
        ScreenHeader(stringResource(R.string.settings), onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ふだん使うものだけを上に出し、ほかは「詳細」にたたむ
            everydaySection(context, adblock, hud)
            item {
                ActionRow(
                    title = stringResource(R.string.details_title),
                    description = stringResource(R.string.details_desc),
                    action = stringResource(if (details) R.string.close else R.string.open),
                    onClick = { details = !details },
                )
            }
            if (details) {
                optimizeSection()
                castSection(context, cast)
                networkSection(context, offlineCache)
                hudSection(context, hudCustom)
                shizukuSection(context)
                adblockSection(adblock)
                historySection()
                if (BuildConfig.DEBUG) developerSection(context)
            }
        }
    }
}

/** ふだん使う設定: アカウント・画質・広告ブロック・地図アプリ・HUD */
private fun LazyListScope.everydaySection(context: Context, adblock: Boolean, hud: Boolean) {
    accountSection(context)
    item {
        val maxHeight by Prefs.maxHeight.collectAsState()
        ChoiceRow(
            title = stringResource(R.string.max_height_title),
            description = stringResource(R.string.max_height_desc),
            options = listOf(0 to stringResource(R.string.max_height_auto), 720 to "720p", 480 to "480p", 360 to "360p"),
            selected = maxHeight,
            onSelect = { Prefs.setMaxHeight(it) },
        )
    }
    item {
        SettingRow(
            title = stringResource(R.string.adblock_title),
            description = stringResource(R.string.adblock_desc),
            checked = adblock,
            onChange = { Prefs.setAdblock(it) },
        )
    }
    item {
        val mapApp by Prefs.mapApp.collectAsState()
        val navApps = remember { NavApps.installed(context) }
        val selectedApp = remember(mapApp) { NavApps.resolve(context, mapApp) }
        ChoiceRow(
            title = stringResource(R.string.map_app_title),
            description = stringResource(R.string.map_app_desc),
            options = navApps.map { it.packageName to it.label },
            selected = selectedApp.packageName,
            onSelect = { pkg ->
                Prefs.setMapApp(pkg)
                setDemoMapPane(context, false)
                val next = NavApps.resolve(context, pkg)
                val current = MapPanes.provider.value
                if (current is NativeAppMapPane) current.switchApp(next)
                else MapPanes.register(NativeAppMapPane(context, next))
            },
        )
    }
    item {
        val hud by Prefs.hud.collectAsState()
        SettingRow(
            title = stringResource(R.string.hud_title),
            description = stringResource(R.string.hud_desc),
            checked = hud,
            onChange = { Prefs.setHud(it) },
        )
    }
    if (hud) {
        // 道案内を出すには通知へのアクセスが要る。許可されていなければここで案内する
        item {
            val listening by Hud.listening.collectAsState()
            if (!listening) {
                ActionRow(
                    title = stringResource(R.string.notif_access_title),
                    description = stringResource(R.string.notif_access_denied),
                    action = stringResource(R.string.open_settings),
                    onClick = {
                        context.startActivity(
                            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }
        }
    }
}

/** サイトごとの見た目の調整 */
private fun LazyListScope.optimizeSection() {
    item { SectionLabel(stringResource(R.string.section_optimize), AweIcons.Tune) }
    items(StreamService.entries) { service ->
        val flags by Prefs.optimizeFlags.collectAsState()
        SettingRow(
            title = stringResource(R.string.optimize_title, service.label),
            description = stringResource(service.optimizeSummary),
            checked = flags[service.id] ?: true,
            onChange = { Prefs.setOptimized(service, it) },
        )
    }
}

/** YouTube のアカウント。ログインはキーボードが出せるスマホの画面だけでできる */
private fun LazyListScope.accountSection(context: Context) {
    item {
        val signedIn by YouTubeAccount.signedIn.collectAsState()
        val isCar = LocalIsCar.current
        // 設定画面を開いたときに数え直す (スマホのログイン画面から戻ったときなど)
        LaunchedEffect(Unit) { YouTubeAccount.refresh() }
        ActionRow(
            title = stringResource(R.string.youtube_account_title),
            description = stringResource(
                when {
                    signedIn -> R.string.youtube_account_signed_in
                    isCar -> R.string.youtube_sign_in_on_phone
                    else -> R.string.youtube_account_signed_out
                },
            ),
            action = stringResource(if (signedIn) R.string.youtube_sign_out else R.string.youtube_sign_in),
            onClick = {
                when {
                    signedIn -> YouTubeAccount.signOut()
                    !isCar -> context.startActivity(Intent(context, LoginActivity::class.java))
                }
            },
        )
    }
}

private fun LazyListScope.castSection(context: Context, cast: Boolean) {
    item { SectionLabel(stringResource(R.string.section_cast), AweIcons.Cast) }
    item {
        SettingRow(
            title = stringResource(R.string.cast_receive_title),
            description = stringResource(R.string.cast_receive_desc),
            checked = cast,
            onChange = { Prefs.setCast(context, it) },
        )
    }
    if (cast) {
        item {
            val status by LoungeReceiver.status.collectAsState()
            PairingCard(status)
        }
    }
}

/** 番組の保存・先読み */
private fun LazyListScope.networkSection(context: Context, offlineCache: Boolean) {
    item { SectionLabel(stringResource(R.string.section_saving), AweIcons.SignalCellularAlt) }
    item {
        val sites = StreamService.entries.filter { it.offlineCache }
            .joinToString(stringResource(R.string.list_separator)) { it.label }
        SettingRow(
            title = stringResource(R.string.offline_title),
            description = stringResource(R.string.offline_desc, sites),
            checked = offlineCache,
            onChange = { Prefs.setOfflineCache(it) },
        )
    }
    if (offlineCache) {
        item {
            // 使用量はキャッシュを開いたときに数える。まだ開いていなければ (起動直後など) ここで開く
            LaunchedEffect(Unit) { withContext(Dispatchers.IO) { HlsCacheStore.get(context) } }
            val usage by HlsCacheStore.usage.collectAsState()
            val progress by HlsCacheStore.progressFlow.collectAsState()
            ActionRow(
                title = stringResource(R.string.offline_usage_title),
                description = stringResource(R.string.offline_usage, usage / 1e9) +
                    progress["video"]?.let { stringResource(R.string.offline_progress, it) }.orEmpty(),
                action = stringResource(R.string.clear),
                onClick = { HlsCacheStore.get(context).clear() },
            )
        }
    }
    item {
        val prefetch by Prefs.prefetch.collectAsState()
        SettingRow(
            title = stringResource(R.string.yt_readahead_title),
            description = stringResource(R.string.yt_readahead_desc),
            checked = prefetch,
            onChange = { Prefs.setPrefetch(it) },
        )
    }
}

/** HUD: 通知へのアクセスの状態と、好きな文字 */
private fun LazyListScope.hudSection(context: Context, hudCustom: Boolean) {
    item { SectionLabel(stringResource(R.string.section_hud), AweIcons.Navigation) }
    item {
        val listening by Hud.listening.collectAsState()
        val state by Hud.state.collectAsState()
        val guiding = state
        ActionRow(
            title = stringResource(R.string.notif_access_title),
            description = when {
                !listening -> stringResource(R.string.notif_access_denied)
                guiding != null -> stringResource(R.string.notif_access_guiding, guiding.guidance.cue)
                else -> stringResource(R.string.notif_access_ready)
            },
            action = stringResource(R.string.open_settings),
            onClick = {
                context.startActivity(
                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
    item {
        SettingRow(
            title = stringResource(R.string.hud_custom_title),
            description = stringResource(R.string.hud_custom_desc),
            checked = hudCustom,
            onChange = { Prefs.setHudCustom(it) },
        )
    }
    if (hudCustom) item { HudTextCard() }
}

/** 地図枠を動かす Shizuku */
private fun LazyListScope.shizukuSection(context: Context) {
    item { SectionLabel(stringResource(R.string.section_shizuku), AweIcons.Extension) }
    item {
        val status by ShizukuState.status.collectAsState()
        ActionRow(
            title = stringResource(R.string.shizuku),
            description = stringResource(
                when (status) {
                    ShizukuStatus.READY -> R.string.shizuku_ready
                    ShizukuStatus.NO_PERMISSION -> R.string.shizuku_no_permission
                    ShizukuStatus.NOT_RUNNING -> R.string.shizuku_not_running
                },
            ),
            action = stringResource(if (status == ShizukuStatus.NO_PERMISSION) R.string.allow else R.string.check_again),
            onClick = {
                if (status == ShizukuStatus.NO_PERMISSION) ShizukuState.requestPermission() else ShizukuState.refresh()
            },
        )
    }
    item {
        val auto by Prefs.shizukuAutoRestart.collectAsState()
        SettingRow(
            title = stringResource(R.string.shizuku_auto_title),
            description = stringResource(R.string.shizuku_auto_desc),
            checked = auto,
            onChange = { Prefs.setShizukuAutoRestart(it) },
        )
    }
    item {
        val keeper by ShizukuKeeper.status.collectAsState()
        ActionRow(
            title = stringResource(R.string.shizuku_restart_title),
            description = when {
                keeper.running -> stringResource(R.string.shizuku_restart_running)
                else -> keeper.lastResult ?: stringResource(R.string.shizuku_restart_hint)
            },
            action = stringResource(R.string.start),
            onClick = { ShizukuKeeper.restartNow() },
        )
    }
}

/** debug ビルドだけに出す */
private fun LazyListScope.developerSection(context: Context) {
    item {
        val mute by Prefs.devMute.collectAsState()
        SettingRow(
            title = stringResource(R.string.dev_mute_title),
            description = stringResource(R.string.dev_mute_desc),
            checked = mute,
            onChange = { Prefs.setDevMute(it) },
        )
    }
    item {
        val provider by MapPanes.provider.collectAsState()
        val mapApp by Prefs.mapApp.collectAsState()
        SettingRow(
            title = stringResource(R.string.dev_demo_map_title),
            description = stringResource(R.string.dev_demo_map_desc),
            checked = provider is DemoMapPane,
            onChange = {
                setDemoMapPane(context, it)
                MapPanes.register(if (it) DemoMapPane() else NativeAppMapPane(context, NavApps.resolve(context, mapApp)))
            },
        )
    }
}

private fun LazyListScope.adblockSection(adblock: Boolean) {
    item { SectionLabel(stringResource(R.string.section_filter_lists), AweIcons.Shield) }
    if (!adblock) return
    items(FilterList.entries) { list ->
        val lists by Prefs.filterLists.collectAsState()
        SettingRow(
            title = stringResource(list.label),
            description = stringResource(list.description),
            checked = list in lists,
            onChange = { Prefs.setFilterList(list, it) },
        )
    }
    item {
        val lists by Prefs.filterLists.collectAsState()
        val status by AdBlocker.status.collectAsState()
        val context = LocalContext.current
        val failed = status.failed
        val domains = pluralStringResource(R.plurals.filter_domains, status.domainCount, status.domainCount)
        val updatedAt = DateFormat.format(
            DateFormat.getBestDateTimePattern(context.resources.configuration.locales[0], "MdHmm"),
            Date(status.lastUpdated),
        )
        ActionRow(
            title = stringResource(R.string.filter_update_title),
            description = when {
                status.updating -> stringResource(R.string.filter_updating)
                failed != null -> stringResource(R.string.filter_failed, stringResource(failed.label))
                status.lastUpdated == 0L -> stringResource(R.string.filter_not_fetched, domains)
                else -> stringResource(R.string.filter_updated, domains, updatedAt)
            },
            action = stringResource(R.string.update),
            onClick = { AdBlocker.update(lists) },
        )
    }
}

private fun LazyListScope.historySection() {
    item { SectionLabel(stringResource(R.string.section_history), AweIcons.History) }
    item {
        val history by Prefs.history.collectAsState()
        ActionRow(
            title = stringResource(R.string.history_clear_title),
            description = pluralStringResource(R.plurals.history_count, history.size, history.size),
            action = stringResource(R.string.clear),
            onClick = { Prefs.clearHistory() },
        )
    }
}

/** 地図枠のテスト表示を使うか (debug ビルドで起動時に読む) */
private fun setDemoMapPane(context: Context, on: Boolean) {
    context.getSharedPreferences("aweauto", Context.MODE_PRIVATE).edit().putBoolean("demo_map_pane", on).apply()
}
