package com.h1rose.aweauto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.h1rose.aweauto.data.HistoryItem
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.data.Shortcut
import com.h1rose.aweauto.data.StreamService
import com.h1rose.aweauto.data.shortcuts
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HomeTab(val label: String) { FOR_YOU("おすすめ"), APPS("アプリ"), LIBRARY("ライブラリ") }

@Composable
fun HomeScreen() {
    var tab by rememberSaveable { mutableStateOf(HomeTab.FOR_YOU) }
    val history by Prefs.history.collectAsState()

    Column(Modifier.fillMaxSize().background(AweColors.Background)) {
        TopBar(tab = tab, onTab = { tab = it })
        when (tab) {
            HomeTab.FOR_YOU -> ForYou(history)
            HomeTab.APPS -> AppsGrid()
            HomeTab.LIBRARY -> Library(history)
        }
    }
}

@Composable
private fun TopBar(tab: HomeTab, onTab: (HomeTab) -> Unit) = BoxWithConstraints {
    // 地図と並べたときなど幅が狭いときは、時計を省いてタブを詰める
    val compact = maxWidth < 700.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (compact) 16.dp else 24.dp, end = 24.dp + LocalTopEndReserve.current, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) {
            AppMark(32.dp)
            Spacer(Modifier.width(18.dp))
        }
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        HomeTab.entries.forEach { t ->
            val selected = t == tab
            Box(
                modifier = Modifier
                    .pressScale { onTab(t) }
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) AweColors.Chip else Color.Transparent)
                    .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = 7.dp),
            ) {
                Text(
                    t.label,
                    maxLines = 1,
                    softWrap = false,
                    color = if (selected) AweColors.OnChip else AweColors.OnSurfaceDim,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        }
        if (!compact) {
            Clock()
            Spacer(Modifier.width(14.dp))
        }
        val split by Prefs.splitMap.collectAsState()
        Box(
            Modifier.pressScale { Prefs.toggleSplitMap() }.size(36.dp).clip(CircleShape)
                .background(if (split) AweColors.Chip else AweColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Map,
                contentDescription = "地図と並べる",
                tint = if (split) AweColors.OnChip else AweColors.OnSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.pressScale { AweNav.go(Route.Pair) }.size(36.dp).clip(CircleShape).background(AweColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Cast, contentDescription = "スマホからキャスト", tint = AweColors.OnSurface, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.pressScale { AweNav.go(Route.Settings) }.size(36.dp).clip(CircleShape).background(AweColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "設定", tint = AweColors.OnSurface, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    Text(
        SimpleDateFormat("H:mm", Locale.JAPAN).format(now),
        color = AweColors.OnSurface,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun ForYou(history: List<HistoryItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Hero(history.firstOrNull()) }
        item {
            Shelf("アプリ") {
                items(StreamService.entries) { AppCard(it, Modifier.width(180.dp)) }
            }
        }
        if (history.isNotEmpty()) {
            item {
                Shelf("続きを見る") {
                    items(history, key = { it.videoId }) { VideoCard(it) }
                }
            }
        }
        item {
            Shelf("ピックアップ") {
                items(shortcuts) { ShortcutCard(it) }
            }
        }
    }
}

/** Google TV のトップにある大きなバナー */
@Composable
private fun Hero(latest: HistoryItem?) {
    val (title, subtitle, action) = if (latest != null) {
        Triple(latest.title.ifBlank { "最後に見た動画" }, "YouTube ・ 続きから再生", "続きを見る")
    } else {
        Triple("YouTube をひらく", "車の画面向けに最適化した表示で再生します", "ひらく")
    }
    val open = {
        AweNav.go(Route.Web(StreamService.YOUTUBE, latest?.watchUrl ?: StreamService.YOUTUBE.homeUrl))
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF3A0A12), Color(0xFF1B1D24)))
            ),
    ) {
        if (latest != null) {
            AsyncImage(
                model = latest.heroImageUrl,
                contentDescription = null,
                // 古い動画には高解像度サムネが無いので通常サイズに落とす
                error = rememberAsyncImagePainter(latest.thumbnailUrl),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 文字を読みやすくするため左側を暗くする
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0xF0111318), 0.55f to Color(0x80111318), 1f to Color.Transparent)
            )
        )
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 28.dp, end = 180.dp),
        ) {
            Text(subtitle, color = AweColors.OnSurfaceDim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 30.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .pressScale(open)
                    .clip(RoundedCornerShape(50))
                    .background(AweColors.Chip)
                    .padding(start = 14.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = AweColors.OnChip)
                Spacer(Modifier.width(4.dp))
                Text(action, color = AweColors.OnChip, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun Shelf(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column {
        Text(
            title,
            color = AweColors.OnSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun AppCard(service: StreamService, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .pressScale { AweNav.go(Route.Web(service, service.homeUrl)) }
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(AweColors.Surface),
    ) {
        // Google TV のアプリ行のように、ブランド色を薄く敷いてロゴを真ん中に置く
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(service.brand.copy(alpha = 0.35f), Color.Transparent))
            )
        )
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandIcon(service, 56.dp)
            Spacer(Modifier.height(8.dp))
            Text(service.label, color = AweColors.OnSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun VideoCard(item: HistoryItem, modifier: Modifier = Modifier.width(200.dp)) {
    Column(modifier.pressScale { AweNav.go(Route.Web(StreamService.YOUTUBE, item.watchUrl)) }) {
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(AweColors.SurfaceHigh),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            item.title.ifBlank { "YouTube" },
            color = AweColors.OnSurface,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun ShortcutCard(shortcut: Shortcut) {
    Column(
        modifier = Modifier
            .pressScale { AweNav.go(Route.Web(shortcut.service, shortcut.url)) }
            .width(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AweColors.Surface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandIcon(shortcut.service, 22.dp)
            Spacer(Modifier.weight(1f))
            Icon(shortcut.icon, contentDescription = null, tint = AweColors.OnSurfaceDim, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(shortcut.title, color = AweColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(shortcut.subtitle, color = AweColors.OnSurfaceDim, fontSize = 12.sp)
    }
}

@Composable
private fun AppsGrid() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(200.dp),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(StreamService.entries) { AppCard(it) }
    }
}

@Composable
private fun Library(history: List<HistoryItem>) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("まだ再生履歴はありません", color = AweColors.OnSurfaceDim, fontSize = 15.sp)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(180.dp),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(history, key = { it.videoId }) { VideoCard(it, Modifier) }
    }
}
