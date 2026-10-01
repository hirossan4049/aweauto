package com.h1rose.aweauto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.h1rose.aweauto.R
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.data.StreamService
import com.h1rose.aweauto.data.youtubeVideoId
import com.h1rose.aweauto.hud.NowPlaying
import com.h1rose.aweauto.map.MapLayouts
import com.h1rose.aweauto.map.MapMode

/**
 * スマホの画面。車の画面のリモコン: 検索 (スマホのキーボード)・再生の操作・地図の並べ方・履歴から再生。
 * 車の画面は見るだけにして、込み入った操作はここで行う。設定は右上から開く。
 */
@Composable
fun RemoteScreen(onSettings: () -> Unit) {
    val history by Prefs.history.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AweColors.Background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TopBar(onSettings) }
        item { NowPlayingCard() }
        item { SearchCard() }
        item { MapCard() }
        historySection(history)
    }
}

@Composable
private fun TopBar(onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppMark(28.dp)
        Spacer(Modifier.width(10.dp))
        Text("aweauto", color = AweColors.OnSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        RoundButton(Icons.Filled.Settings, stringResource(R.string.settings), onClick = onSettings)
    }
}

/** 車の画面にいま出ているもの + 再生の操作 */
@Composable
private fun NowPlayingCard() {
    val stack by AweNav.backStack.collectAsState()
    val title by NowPlaying.title.collectAsState()
    val playing by CarRemote.playing.collectAsState()
    val history by Prefs.history.collectAsState()
    val web = stack.lastOrNull() as? Route.Web
    // YouTube はページを読み込み直さずに動画を切り替えるので、ページのタイトルが取れないときは履歴の題名を使う
    val url = web?.let { sessions[it]?.currentUrl ?: it.url }
    val shown = title ?: youtubeVideoId(url)?.let { id -> history.firstOrNull { it.videoId == id }?.title?.ifBlank { null } }
    Card {
        Text(stringResource(R.string.remote_car_screen), color = AweColors.Accent, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (web != null) {
                BrandIcon(web.service, 22.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                when {
                    web == null -> stringResource(R.string.home)
                    shown != null -> shown
                    else -> web.service.label
                },
                color = AweColors.OnSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) { CarRemote.back() }
            RoundButton(AweIcons.Replay10, stringResource(R.string.remote_rewind), enabled = web != null) { CarRemote.seekBy(-10) }
            RoundButton(
                if (playing && web != null) AweIcons.Pause else AweIcons.PlayArrow,
                stringResource(if (playing) R.string.remote_pause else R.string.remote_play),
                big = true,
                enabled = web != null,
            ) { CarRemote.playPause() }
            RoundButton(AweIcons.Forward10, stringResource(R.string.remote_forward), enabled = web != null) { CarRemote.seekBy(10) }
            RoundButton(Icons.Filled.Home, stringResource(R.string.home)) { CarRemote.home() }
        }
    }
}

/** スマホのキーボードで検索して、結果を車の画面に出す */
@Composable
private fun SearchCard() {
    var query by rememberSaveable { mutableStateOf("") }
    var site by rememberSaveable { mutableStateOf(StreamService.YOUTUBE) }
    val focus = LocalFocusManager.current
    val submit = {
        if (query.isNotBlank()) {
            CarRemote.search(site, query)
            focus.clearFocus()
        }
    }
    Card {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.remote_search_hint, site.label), color = AweColors.OnSurfaceDim) },
            leadingIcon = { Icon(AweIcons.Search, contentDescription = null, tint = AweColors.OnSurfaceDim) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AweColors.OnSurface,
                unfocusedTextColor = AweColors.OnSurface,
                focusedBorderColor = AweColors.Accent,
                unfocusedBorderColor = AweColors.SurfaceHigh,
                cursorColor = AweColors.Accent,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StreamService.entries.forEach { s -> Pill(s.label, primary = s == site) { site = s } }
        }
    }
}

/** 地図を出す・入れ替える・小窓にする */
@Composable
private fun MapCard() {
    val on by Prefs.splitMap.collectAsState()
    val layout by MapLayouts.layout.collectAsState()
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AweIcons.Map, contentDescription = null, tint = AweColors.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.remote_map), color = AweColors.OnSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Pill(stringResource(if (on) R.string.remote_map_hide else R.string.remote_map_show), primary = on) { Prefs.toggleSplitMap() }
        }
        if (on) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.map_swap)) { MapLayouts.swap() }
                Pill(stringResource(if (layout.mode == MapMode.PIP) R.string.map_side_by_side else R.string.map_to_pip)) {
                    MapLayouts.togglePip()
                }
                if (layout.mode == MapMode.PIP) Pill(stringResource(R.string.map_resize)) { MapLayouts.cyclePipScale() }
            }
        }
    }
}

/** 最近見た動画。押すと車の画面で開く */
private fun LazyListScope.historySection(history: List<com.h1rose.aweauto.data.HistoryItem>) {
    if (history.isEmpty()) return
    item {
        Text(
            stringResource(R.string.shelf_continue),
            color = AweColors.Accent,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
    }
    items(history.take(20), key = { it.videoId }) { item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .pressScale { CarRemote.open(item.watchUrl) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                item.title.ifBlank { item.videoId },
                color = AweColors.OnSurface,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AweColors.Surface)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: String,
    big: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val size = if (big) 64.dp else 48.dp
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (big) AweColors.Chip else AweColors.SurfaceHigh)
            .then(if (enabled) Modifier.pressScale(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                !enabled -> AweColors.OnSurfaceDim.copy(alpha = 0.4f)
                big -> AweColors.OnChip
                else -> AweColors.OnSurface
            },
            modifier = Modifier.size(if (big) 32.dp else 24.dp),
        )
    }
}
