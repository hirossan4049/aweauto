package com.h1rose.aweauto

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.h1rose.aweauto.web.YouTubeAccount

/**
 * スマホで YouTube (Google アカウント) にログインする画面。
 *
 * ユーザーエージェントは WebView の既定のまま変えない (Google はアプリ内のブラウザでのログインを制限していて、
 * それを偽って通すことはしない)。Google が許せば、ここでのログインが車の画面の YouTube にも使われる。
 * ログインが終わって YouTube に戻ったら閉じる。
 */
class LoginActivity : Activity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    // ログインが終わって YouTube に戻ってきた
                    if (!YouTubeAccount.isSignInPage(url) && url.orEmpty().contains("youtube.com")) finishSignIn()
                }
            }
        }
        setContentView(webView)
        if (savedInstanceState == null) webView.loadUrl(YouTubeAccount.SIGN_IN_URL) else webView.restoreState(savedInstanceState)
    }

    private fun finishSignIn() {
        if (isFinishing) return
        CookieManager.getInstance().flush()
        YouTubeAccount.refresh()
        Toast.makeText(
            this,
            if (YouTubeAccount.signedIn.value) R.string.youtube_signed_in else R.string.youtube_sign_in_not_done,
            Toast.LENGTH_SHORT,
        ).show()
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    @Deprecated("Activity の戻る")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else {
            YouTubeAccount.refresh()
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
