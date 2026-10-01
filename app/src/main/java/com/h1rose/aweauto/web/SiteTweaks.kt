package com.h1rose.aweauto.web

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.h1rose.aweauto.data.StreamService
import org.json.JSONObject

/**
 * サイトに差し込む CSS と JS。
 * - 「表示の最適化」ON: assets/css/<service>.css を <style id="aweauto-css"> として入れ、
 *   assets/js/<service>.js があれば実行する。YouTube は SPA で <head> を作り直すことがあるので、消されたら入れ直す。
 * - 「広告ブロック」ON: assets/js/adblock-<service>.js があれば実行する。
 * - 画質の上限・先読み: assets/js/prefetch-<service>.js に window.__aweautoConfig で値を渡す。
 */
class SiteTweaks(
    context: Context,
    private val service: StreamService,
    optimize: Boolean,
    adblock: Boolean,
    playback: PlaybackConfig,
    /** 開発用: 動画を常にミュートする (DHU で毎回音が出ないように) */
    mute: Boolean = false,
) {
    private val script: String = buildString {
        if (mute) append(MUTE_SCRIPT).append('\n')
        if (playback.maxHeight > 0 || playback.readaheadSec > 0) {
            append("window.__aweautoConfig = ")
            append(JSONObject().put("maxHeight", playback.maxHeight).put("readaheadSec", playback.readaheadSec))
            append(";\n")
            append(context.assetOrEmpty("js/prefetch-${service.id}.js")).append('\n')
        }
        // 広告除去はプレーヤー設定を読み込む前に仕込む必要があるので先頭に置く
        if (adblock) append(context.assetOrEmpty("js/adblock-${service.id}.js")).append('\n')
        if (optimize) {
            val css = context.assetOrEmpty(service.cssAsset)
            append(
                """
                (function () {
                  if (window.__aweautoCss) return;
                  window.__aweautoCss = true;
                  var ID = 'aweauto-css';
                  function add() {
                    document.documentElement.setAttribute('data-aweauto', '${service.id}');
                    if (document.getElementById(ID)) return;
                    var s = document.createElement('style');
                    s.id = ID;
                    s.textContent = ${JSONObject.quote(css)};
                    (document.head || document.documentElement).appendChild(s);
                  }
                  add();
                  document.addEventListener('DOMContentLoaded', add);
                  new MutationObserver(add).observe(document.documentElement, { childList: true });
                })();
                """.trimIndent()
            ).append('\n')
            append(context.assetOrEmpty("js/${service.id}.js")).append('\n')
        }
        // キャスト受信用の再生状態通知 (window.AweCast が無い WebView では何もしない)
        append(context.assetOrEmpty("js/cast-${service.id}.js")).append('\n')
        append(context.assetOrEmpty("js/video-state.js"))
    }

    private val usesDocumentStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    /** WebView 生成直後に呼ぶ。対応していれば以後の全ページ遷移で読み込み前に注入される */
    fun install(webView: WebView) {
        if (!usesDocumentStart || script.isBlank()) return
        val origins = service.hosts.map { "https://$it" }.toSet()
        WebViewCompat.addDocumentStartJavaScript(webView, script, origins)
    }

    /** document-start 非対応の WebView 向けのフォールバック */
    fun onPage(webView: WebView, url: String?) {
        if (usesDocumentStart || script.isBlank() || !matches(url)) return
        webView.evaluateJavascript(script, null)
    }

    private fun matches(url: String?): Boolean {
        val host = url?.let { Uri.parse(it).host } ?: return false
        return service.hosts.any { host == it || host.endsWith(".$it") }
    }
}

/** 通信が不安定な車内向けの再生設定。0 は「サイトに任せる」 */
private const val MUTE_SCRIPT = """
(function () {
  window.__aweautoMute = true;
  function mute(e) { if (e.target instanceof HTMLMediaElement && !e.target.muted) e.target.muted = true; }
  ['loadedmetadata', 'play', 'playing', 'volumechange'].forEach(function (t) {
    document.addEventListener(t, mute, true);
  });
})();
"""

data class PlaybackConfig(val maxHeight: Int, val readaheadSec: Int)

private fun Context.assetOrEmpty(path: String): String =
    runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("")
