package com.hirossan.aweauto.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.hirossan.aweauto.data.Prefs
import com.hirossan.aweauto.data.StreamService
import com.hirossan.aweauto.web.SiteTweaks
import com.hirossan.aweauto.web.UserAgents

@Composable
fun WebScreen(route: Route.Web) {
    val optimizeFlags by Prefs.optimizeFlags.collectAsState()
    val optimized = optimizeFlags[route.service.id] ?: true
    var fullscreen by remember { mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxSize()) {
            SideRail(
                service = route.service,
                optimized = optimized,
                onBack = { AweNav.back() },
                onHome = { AweNav.home() },
                onReload = { webView?.reload() },
            )
            // CSS 設定を切り替えたら WebView ごと作り直す (注入済みスクリプトを外す API が無いため)
            key(route, optimized) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        createWebView(ctx, route, optimized, onFullscreen = { fullscreen = it }).also {
                            webView = it
                            it.loadUrl(route.url)
                        }
                    },
                    onRelease = { it.destroy() },
                )
            }
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
    onFullscreen: (Pair<View, WebChromeClient.CustomViewCallback>?) -> Unit,
): WebView {
    val service = route.service
    val tweaks = if (optimized) SiteTweaks(ctx, service) else null
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
        tweaks?.install(this)

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                tweaks?.onPage(view, url)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                tweaks?.onPage(view, url)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
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

private fun youtubeVideoId(url: String?): String? {
    val uri = url?.let(Uri::parse) ?: return null
    if (uri.host?.endsWith("youtube.com") != true || uri.path != "/watch") return null
    return uri.getQueryParameter("v")
}

@Composable
private fun SideRail(
    service: StreamService,
    optimized: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onReload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(64.dp)
            .background(AweColors.Surface)
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
