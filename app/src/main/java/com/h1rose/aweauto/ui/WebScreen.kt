package com.h1rose.aweauto.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.content.MutableContextWrapper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.h1rose.aweauto.adblock.AdBlocker
import com.h1rose.aweauto.cast.CastBridge
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.data.StreamService
import com.h1rose.aweauto.R
import com.h1rose.aweauto.web.HlsPrefetcher
import com.h1rose.aweauto.web.PlaybackConfig
import com.h1rose.aweauto.web.SiteTweaks
import com.h1rose.aweauto.web.UserAgents
import kotlinx.coroutines.delay

private val RailWidth = 64.dp

/** 先読み ON のときに溜める秒数 (実際は各サイトのプレーヤーと MSE の容量上限で頭打ちになる) */
private const val READAHEAD_SEC = 600

/**
 * 表示中のページの WebView と状態。Android Auto で別の画面 (地図・電話など) に切り替えると
 * 車の画面 (Presentation) は作り直されるが、WebView はここに残しておき、戻ったらつなぎ直す。
 * これで再生が止まったり最初からになったりしない。
 */
private class WebSession(val key: List<Any>, val view: WebView, initialUrl: String) {
    var currentUrl by mutableStateOf(initialUrl)
    var videoStarted by mutableStateOf(false)
    var fullscreen by mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null)

    /** いま動画が再生中か (ページからの playing / pause 通知) */
    var playing = false

    /** 車の画面が割り込み (バックカメラなど) で隠れたときに再生中だったら、戻ったときに再開する */
    var resumeOnReturn = false

    fun destroy() {
        (view.getTag(R.id.hls_prefetcher) as? HlsPrefetcher)?.release()
        CastBridge.detach(view)
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }
}

private val sessions = mutableMapOf<Route.Web, WebSession>()

/**
 * 車の画面が割り込み (バックカメラ・360 度カメラなど) で隠れた / 戻ったときに呼ぶ。
 * 隠れている間は止めたままにし、戻ったら割り込み前に再生していた動画だけ再開する。
 */
fun onCarScreenVisibilityChanged(visible: Boolean) {
    for (session in sessions.values) {
        if (!visible) {
            session.resumeOnReturn = session.playing
        } else if (session.resumeOnReturn) {
            session.resumeOnReturn = false
            resumePlayback(session.view, attempt = 0)
        }
    }
}

// 戻った直後は音の出力がまだ Android Auto 側にあって再生してもすぐ止まるので、間を置いて数回試す
private val RESUME_DELAYS_MS = longArrayOf(800, 1500, 3000, 5000)

private fun resumePlayback(view: WebView, attempt: Int) {
    if (attempt >= RESUME_DELAYS_MS.size) return
    view.postDelayed({
        view.evaluateJavascript(
            "(function(){var p=document.getElementById('movie_player');" +
                "if(p&&p.playVideo)p.playVideo();else{var v=document.querySelector('video');if(v)v.play();}})()",
            null,
        )
        // 少し待って本当に動いているか確かめ、止まっていればもう一度
        view.postDelayed({
            view.evaluateJavascript("(function(){var v=document.querySelector('video');return !!v&&!v.paused;})()") {
                if (it != "true") resumePlayback(view, attempt + 1)
            }
        }, 700)
    }, RESUME_DELAYS_MS[attempt])
}

/** 戻る・ホームで履歴から外れた画面の WebView を捨てる */
private fun pruneSessions() {
    val alive = AweNav.backStack.value.toSet()
    sessions.entries.removeAll { (route, session) ->
        (route !in alive).also { if (it) session.destroy() }
    }
}

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
    val immersive = isPlaybackUrl(currentUrl)
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
    val railWidth by animateDpAsState(if (immersive) 0.dp else RailWidth, label = "railWidth")

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
            LoadingCover(currentUrl, onDismiss = { session.videoStarted = true })
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
                webView.canGoBack() -> { webView.goBack(); true }
                else -> false
            }
        }
        onDispose { AweNav.webBackHandler = null }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    ctx: MutableContextWrapper,
    route: Route.Web,
    optimized: Boolean,
    adblock: Boolean,
    playback: PlaybackConfig,
    offlineCache: Boolean,
    mute: Boolean,
    onFullscreen: (Pair<View, WebChromeClient.CustomViewCallback>?) -> Unit,
    onUrl: (String) -> Unit,
    onVideoPlaying: () -> Unit,
    onVideoPaused: () -> Unit,
): WebView {
    val service = route.service
    val tweaks = SiteTweaks(ctx, service, optimize = optimized, adblock = adblock, playback = playback, mute = mute)
    val hls = if (service.offlineCache && offlineCache) HlsPrefetcher(ctx, playback.maxHeight) else null
    val cookies = CookieManager.getInstance()
    if (optimized) service.optimizeCookies.forEach { (url, value) -> cookies.setCookie(url, value) }

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
        setTag(R.id.hls_prefetcher, hls)
        if (service.castReceiver) CastBridge.attach(this)
        addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onPlaying() {
                    post(onVideoPlaying)
                }

                @JavascriptInterface
                fun onPaused() {
                    post(onVideoPaused)
                }
            },
            "AweVideo",
        )

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                tweaks.onPage(view, url)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                tweaks.onPage(view, url)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val host = request.url.host ?: return null
                if (adblock && AdBlocker.isBlocked(host)) {
                    // 空のレスポンスを返して読み込ませない
                    return WebResourceResponse("text/plain", "utf-8", 204, "Blocked", emptyMap(), null)
                }
                return hls?.intercept(request)
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

            // 再生前に出る灰色の「動画」アイコンを透明にする
            override fun getDefaultVideoPoster(): Bitmap =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

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

/** 再生ページで動画のサムネイルとして使う画像 */
private fun posterFor(url: String): String? {
    youtubeVideoId(url)?.let { return "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
    val uri = Uri.parse(url)
    if (uri.host?.endsWith("tver.jp") == true && uri.path.orEmpty().startsWith("/episodes/")) {
        return "https://statics.tver.jp/images/content/thumbnail/episode/large/${uri.lastPathSegment}.jpg"
    }
    return null
}

/** 再生が始まるまで重ねる Google TV 風の読み込み画面。触ると閉じて下のページを操作できる */
@Composable
private fun LoadingCover(url: String, onDismiss: () -> Unit) {
    val history by Prefs.history.collectAsState()
    val title = youtubeVideoId(url)?.let { id -> history.firstOrNull { it.videoId == id }?.title }
    Box(
        Modifier
            .fillMaxSize()
            .background(AweColors.Background)
            .pressScale(onDismiss),
    ) {
        posterFor(url)?.let { poster ->
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x66111318), 0.6f to Color(0xCC111318), 1f to Color(0xF2111318))
            )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 40.dp, end = 40.dp, bottom = 32.dp),
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AweColors.Accent,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text("読み込み中…", color = AweColors.OnSurfaceDim, fontSize = 15.sp)
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
        BrandIcon(service, 40.dp)
        Spacer(Modifier.height(6.dp))
        RailButton(Icons.AutoMirrored.Filled.ArrowBack, "戻る", onClick = onBack)
        RailButton(Icons.Filled.Home, "ホーム", onClick = onHome)
        RailButton(Icons.Filled.Refresh, "再読込", onClick = onReload)
        RailButton(AweIcons.Map, "地図と並べる", onClick = { Prefs.toggleSplitMap() })
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
