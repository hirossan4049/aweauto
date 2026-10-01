package com.h1rose.aweauto.ui

import android.annotation.SuppressLint
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.graphics.Color
import com.h1rose.aweauto.R
import com.h1rose.aweauto.adblock.AdBlocker
import com.h1rose.aweauto.cast.CastBridge
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.data.youtubeVideoId
import com.h1rose.aweauto.web.HlsPrefetcher
import com.h1rose.aweauto.web.PlaybackConfig
import com.h1rose.aweauto.web.SiteTweaks
import com.h1rose.aweauto.web.UserAgents

/**
 * サイトを開く WebView を作る。サイトごとの CSS / JS (SiteTweaks)、番組の保存 (HlsPrefetcher)、
 * キャストの橋渡し、再生状態を受け取る JS ブリッジを付ける。
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun createWebView(
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
