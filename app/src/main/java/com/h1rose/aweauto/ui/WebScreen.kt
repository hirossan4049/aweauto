package com.h1rose.aweauto.ui

import android.content.MutableContextWrapper
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.h1rose.aweauto.R
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.web.PlaybackConfig
import kotlinx.coroutines.delay

/** 先読み ON のときに溜める秒数 (実際は各サイトのプレーヤーと MSE の容量上限で頭打ちになる) */
private const val READAHEAD_SEC = 600

@Composable
fun WebScreen(route: Route.Web) {
    val optimizeFlags by Prefs.optimizeFlags.collectAsState()
    val optimized = optimizeFlags[route.service.id] ?: true
    val adblock by Prefs.adblock.collectAsState()
    val maxHeight by Prefs.maxHeight.collectAsState()
    val prefetch by Prefs.prefetch.collectAsState()
    val offlineCache by Prefs.offlineCache.collectAsState()
    val devMute by Prefs.devMute.collectAsState()
    val playback = PlaybackConfig(maxHeight = maxHeight, readaheadSec = if (prefetch) READAHEAD_SEC else 0)
    // 設定を切り替えたら WebView ごと作り直す (注入済みスクリプトを外す API が無いため)
    val key = listOf(optimized, adblock, playback, offlineCache, devMute)
    val context = LocalContext.current
    val session = remember(route, key) {
        sessions[route]?.takeIf { it.key == key } ?: run {
            sessions.remove(route)?.destroy()
            lateinit var created: WebSession
            val view = createWebView(
                // Presentation ごとに Context が変わるので、差し替えられるようにしておく
                MutableContextWrapper(context),
                route,
                optimized,
                adblock,
                playback,
                offlineCache,
                devMute,
                onFullscreen = { created.fullscreen = it },
                onUrl = { created.currentUrl = it },
                onVideoPlaying = {
                    created.videoStarted = true
                    created.playing = true
                },
                onVideoPaused = { created.playing = false },
            )
            created = WebSession(key, view, route.url)
            view.loadUrl(route.url)
            sessions[route] = created
            created
        }
    }
    val webView = session.view
    val currentUrl = session.currentUrl
    val fullscreen = session.fullscreen
    DisposableEffect(Unit) {
        onDispose { pruneSessions() }
    }
    // 再生ページではレールを隠して動画を横幅いっぱいに出す。左端のつまみで一時的に呼び出せる
    val immersive = route.service.isPlaybackPage(currentUrl)
    var railPeek by remember { mutableStateOf(false) }
    // 再生が始まるまでは自前の読み込み画面を重ねる (WebView の灰色のプレースホルダーを見せない)
    val videoStarted = session.videoStarted
    LaunchedEffect(currentUrl) {
        if (session.videoStarted) return@LaunchedEffect
        delay(15_000)
        session.videoStarted = true
    }
    LaunchedEffect(railPeek) {
        if (railPeek) {
            delay(4_000)
            railPeek = false
        }
    }
    // WebView の幅は動かさずに一度で変える (幅を少しずつ変えると、そのたびにページ全体の配置し直しと
    // 動画の大きさの変更が走って重い)。レール自体は横から滑り込ませる
    val railWidth = if (immersive) 0.dp else RailWidth

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        key(session) {
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(start = railWidth),
                factory = { ctx ->
                    (webView.context as MutableContextWrapper).baseContext = ctx
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView
                },
                // 画面が作り直されるときは外すだけ。捨てるのは履歴から外れたとき (pruneSessions)
                onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
            )
        }

        AnimatedVisibility(
            visible = immersive && !videoStarted,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            LoadingCover(route.service, currentUrl, onDismiss = { session.videoStarted = true })
        }

        AnimatedVisibility(
            visible = !immersive || railPeek,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
        ) {
            SideRail(
                service = route.service,
                optimized = optimized,
                translucent = immersive,
                onBack = { railPeek = false; AweNav.back() },
                onHome = { AweNav.home() },
                onReload = { webView.reload() },
            )
        }
        if (immersive && !railPeek) {
            RailHandle(Modifier.align(Alignment.CenterStart)) { railPeek = true }
        }

        fullscreen?.let { (view, callback) ->
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { (view.parent as? ViewGroup)?.removeView(view); view },
                    onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
                    modifier = Modifier.fillMaxSize(),
                )
                RailButton(
                    icon = Icons.Filled.Close,
                    label = stringResource(R.string.exit_fullscreen),
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    onClick = { callback.onCustomViewHidden() },
                )
            }
        }
    }

    DisposableEffect(webView, fullscreen) {
        AweNav.webBackHandler = {
            val current = fullscreen
            when {
                current != null -> { current.second.onCustomViewHidden(); true }
                webView.canGoBack() -> { webView.goBack(); true }
                else -> false
            }
        }
        onDispose { AweNav.webBackHandler = null }
    }
}
