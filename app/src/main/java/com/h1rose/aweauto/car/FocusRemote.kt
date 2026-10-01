package com.h1rose.aweauto.car

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.webkit.WebView

/**
 * 画面に触れない車 (ダイヤル・タッチパッド・ボタンだけの車) 向けの操作。
 *
 * Android Auto はこの種類のアプリの画面にキー入力を渡さないが、右上の操作ボタン (ActionStrip) は
 * ダイヤルで選んで押せる。そこに「前へ・次へ・決定」を置き、Tab / Shift+Tab / Enter のキーとして
 * 車の画面に送る。ホームや設定 (Compose) はボタンやカードの間を、YouTube / TVer のページ (WebView) は
 * リンクの間を移動する。キーで操作し始めると、選んでいるところに枠が出る。
 */
class FocusRemote(private val target: () -> View?) {
    fun next() = key(KeyEvent.KEYCODE_TAB)

    fun previous() = key(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)

    fun select() = key(KeyEvent.KEYCODE_ENTER)

    private fun key(code: Int, meta: Int = 0) {
        val root = target() ?: return
        // 車の画面のウィンドウはタッチで操作されてきた (タッチモード) ので、最初はどこにもフォーカスが無い。
        // 画面 (Compose) にフォーカスを渡してから送る (渡さないとキーがどこにも届かない)
        if (root.findFocus() == null) root.requestFocusFromTouch()
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta)
        // ページ (WebView) の中にいる間は、ページに直接送ってリンクの間を移動させる。
        // Compose を通すと 1 回でページの外 (横のレールなど) に出てしまう。ページの最後まで行ったら Compose に任せる
        val web = webViewOf(root.findFocus())
        if (web != null && web.dispatchKeyEvent(down)) {
            web.dispatchKeyEvent(up)
            return
        }
        root.dispatchKeyEvent(down)
        root.dispatchKeyEvent(up)
    }

    private fun webViewOf(view: View?): WebView? {
        var v: View? = view
        while (v != null) {
            if (v is WebView) return v
            v = v.parent as? View
        }
        return null
    }

}
