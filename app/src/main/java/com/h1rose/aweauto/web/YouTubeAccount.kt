package com.h1rose.aweauto.web

import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * YouTube (Google アカウント) のログイン状態。
 *
 * WebView の Cookie はアプリ全体で 1 つなので、スマホ側のログイン画面 ([com.h1rose.aweauto.LoginActivity]) で
 * ログインすれば、車の画面の YouTube もそのアカウントで開く。
 */
object YouTubeAccount {
    /** ログイン画面。終わったら YouTube に戻る */
    const val SIGN_IN_URL =
        "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F"

    private val YOUTUBE = listOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com")
    private val GOOGLE = listOf("https://accounts.google.com", "https://www.google.com", "https://google.com")

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    /** Cookie を見てログイン状態を数え直す */
    fun refresh() {
        _signedIn.value = isSignedIn(CookieManager.getInstance().getCookie(YOUTUBE.first()))
    }

    /** ログイン済みなら YouTube の Cookie にアカウントの印 (SAPISID / LOGIN_INFO) がある */
    internal fun isSignedIn(cookieHeader: String?): Boolean =
        cookieHeader.orEmpty().split(';').map { it.trim().substringBefore('=') }
            .any { it == "SAPISID" || it == "__Secure-3PAPISID" || it == "LOGIN_INFO" }

    /** Google と YouTube の Cookie だけ消す (TVer など他のサイトのログインは残す) */
    fun signOut() {
        val cookies = CookieManager.getInstance()
        for (url in YOUTUBE + GOOGLE) {
            val domain = "." + url.substringAfter("://").removePrefix("www.").removePrefix("m.").removePrefix("accounts.")
            for (name in cookieNames(cookies.getCookie(url))) {
                // 同じ名前の Cookie がホスト単位とドメイン単位の両方にあることがあるので、両方とも期限切れにする
                cookies.setCookie(url, "$name=; Max-Age=0; Path=/")
                cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=$domain")
            }
        }
        cookies.flush()
        refresh()
    }

    internal fun cookieNames(cookieHeader: String?): Set<String> =
        cookieHeader.orEmpty().split(';').map { it.trim().substringBefore('=') }.filter { it.isNotEmpty() }.toSet()

    /** Google のログインのページか (車の画面では開かずにスマホでのログインを案内する) */
    fun isSignInPage(url: String?): Boolean {
        val host = url?.substringAfter("://", "")?.substringBefore('/')?.substringBefore(':') ?: return false
        return host == "accounts.google.com" || host.endsWith(".accounts.google.com")
    }
}
