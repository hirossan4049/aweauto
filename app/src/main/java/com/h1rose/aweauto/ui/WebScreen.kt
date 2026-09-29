package com.h1rose.aweauto.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.h1rose.aweauto.adblock.AdBlocker
import com.h1rose.aweauto.cast.CastBridge
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.data.StreamService
import com.h1rose.aweauto.web.PlaybackConfig
import com.h1rose.aweauto.web.SiteTweaks
import com.h1rose.aweauto.web.UserAgents
import kotlinx.coroutines.delay

private val RailWidth = 64.dp

/** 先読み ON のときに溜める秒数 (実際は各サイトのプレーヤーと MSE の容量上限で頭打ちになる) */
private const val READAHEAD_SEC = 600

@Composable
fun WebScreen(route: Route.Web) {
    val optimizeFlags by Prefs.optimizeFlags.collectAsState()
    val optimized = optimizeFlags[route.service.id] ?: true
    val adblock by Prefs.adblock.collectAsState()
    val maxHeight by Prefs.maxHeight.collectAsState()
    val prefetch by Prefs.prefetch.collectAsState()
    val playback = PlaybackConfig(maxHeight = maxHeight, readaheadSec = if (prefetch) READAHEAD_SEC else 0)
    var fullscreen by remember { mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember(route) { mutableStateOf(route.url) }
    // 再生ページではレールを隠して動画を横幅いっぱいに出す。左端のつまみで一時的に呼び出せる
    val immersive = isPlaybackUrl(currentUrl)
    var railPeek by remember { mutableStateOf(false) }
    LaunchedEffect(railPeek) {
        if (railPeek) {
            delay(4_000)
            railPeek = false
        }
    }
    val railWidth by animateDpAsState(if (immersive) 0.dp else RailWidth, label = "railWidth")

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 設定を切り替えたら WebView ごと作り直す (注入済みスクリプトを外す API が無いため)
        key(route, optimized, adblock, playback) {
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(start = railWidth),
                factory = { ctx ->
                    createWebView(
                        ctx,
                        route,
                        optimized,
                        adblock,
                        playback,
                        onFullscreen = { fullscreen = it },
                        onUrl = { currentUrl = it },
                    ).also {
                        webView = it
                        it.loadUrl(route.url)
                    }
                },
                onRelease = {
                    CastBridge.detach(it)
                    it.destroy()
                },
            )
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
                onReload = { webView?.reload() },
            )
        }
        if (immersive && !railPeek) {
            RailHandle(Modifier.align(Alignment.CenterStart)) { railPeek = true }
        }

        fullscreen?.let { (view, callback) ->
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
                RailButton(
                    icon = Icons.Filled.Close,
                    label = "全画面を終了",
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
                webView?.canGoBack() == true -> { webView?.goBack(); true }
                else -> false
            }
        }
        onDispose { AweNav.webBackHandler = null }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    ctx: android.content.Context,
    route: Route.Web,
    optimized: Boolean,
    adblock: Boolean,
    playback: PlaybackConfig,
    onFullscreen: (Pair<View, WebChromeClient.CustomViewCallback>?) -> Unit,
    onUrl: (String) -> Unit,
): WebView {
    val service = route.service
    val tweaks = SiteTweaks(ctx, service, optimize = optimized, adblock = adblock, playback = playback)
    val cookies = CookieManager.getInstance()
    if (optimized && service == StreamService.YOUTUBE) {
        // f6=400: ダークテーマ
        cookies.setCookie("https://m.youtube.com", "PREF=f6=400&hl=ja; domain=.youtube.com; path=/")
    }

    return WebView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(android.graphics.Color.BLACK)
        cookies.setAcceptThirdPartyCookies(this, true)
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = UserAgents.forKind(ctx, service.userAgent)
        }
        tweaks.install(this)
        if (service == StreamService.YOUTUBE) CastBridge.attach(this)

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                tweaks.onPage(view, url)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                tweaks.onPage(view, url)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!adblock) return null
                val host = request.url.host ?: return null
                if (!AdBlocker.isBlocked(host)) return null
                // 空のレスポンスを返して読み込ませない
                return WebResourceResponse("text/plain", "utf-8", 204, "Blocked", emptyMap(), null)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                url?.let(onUrl)
                youtubeVideoId(url)?.let { Prefs.recordWatch(it, null) }
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                val id = youtubeVideoId(view.url) ?: return
                Prefs.recordWatch(id, title?.removeSuffix(" - YouTube"))
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                onFullscreen(view to callback)
            }

            override fun onHideCustomView() {
                onFullscreen(null)
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                // TVer の DRM 付き番組向け
                val allowed = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
                if (allowed.isNotEmpty()) request.grant(allowed.toTypedArray()) else request.deny()
            }
        }
    }
}

/** 動画を再生するページか (YouTube の視聴ページ / TVer の番組ページ) */
private fun isPlaybackUrl(url: String): Boolean {
    val uri = Uri.parse(url)
    val host = uri.host ?: return false
    val path = uri.path.orEmpty()
    return (host.endsWith("youtube.com") && path == "/watch") ||
        (host.endsWith("tver.jp") && path.startsWith("/episodes/"))
}

private fun youtubeVideoId(url: String?): String? {
    val uri = url?.let(Uri::parse) ?: return null
    if (uri.host?.endsWith("youtube.com") != true || uri.path != "/watch") return null
    return uri.getQueryParameter("v")
}

@Composable
private fun SideRail(
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
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(service.brand),
            contentAlignment = Alignment.Center,
        ) {
            if (service == StreamService.YOUTUBE) {
                Icon(Icons.Filled.PlayArrow, contentDescription = service.label, tint = Color.White)
            } else {
                Text(service.label.take(1), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(6.dp))
        RailButton(Icons.AutoMirrored.Filled.ArrowBack, "戻る", onClick = onBack)
        RailButton(Icons.Filled.Home, "ホーム", onClick = onHome)
        RailButton(Icons.Filled.Refresh, "再読込", onClick = onReload)
        Spacer(Modifier.weight(1f))
        if (optimized) {
            Text("最適化", color = AweColors.Accent, fontSize = 10.sp)
        }
    }
}

/** 没入モード中に左端に出す小さなつまみ。押すとレールを数秒だけ出す */
@Composable
private fun RailHandle(modifier: Modifier = Modifier, onClick: () -> Unit) {
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
private fun RailButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
