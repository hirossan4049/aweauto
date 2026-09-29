package com.hirossan.aweauto.web

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.hirossan.aweauto.data.StreamService
import org.json.JSONObject

/**
 * 「表示の最適化」が ON のときに差し込む CSS と JS。
 * - CSS: assets/css/<service>.css を <style id="aweauto-css"> として入れる。
 *   YouTube は SPA で <head> を作り直すことがあるので、消されたら入れ直す。
 * - JS: assets/js/<service>.js があれば一度だけ実行する。
 */
class SiteTweaks(context: Context, private val service: StreamService) {
    private val script: String = run {
        val css = context.assets.open(service.cssAsset).bufferedReader().use { it.readText() }
        val js = runCatching {
            context.assets.open("js/${service.id}.js").bufferedReader().use { it.readText() }
        }.getOrDefault("")
        """
        (function () {
          if (window.__aweauto) return;
          window.__aweauto = true;
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
        $js
        """.trimIndent()
    }

    private val usesDocumentStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    /** WebView 生成直後に呼ぶ。対応していれば以後の全ページ遷移で読み込み前に注入される */
    fun install(webView: WebView) {
        if (!usesDocumentStart) return
        val origins = service.hosts.map { "https://$it" }.toSet()
        WebViewCompat.addDocumentStartJavaScript(webView, script, origins)
    }

    /** document-start 非対応の WebView 向けのフォールバック */
    fun onPage(webView: WebView, url: String?) {
        if (usesDocumentStart || !matches(url)) return
        webView.evaluateJavascript(script, null)
    }

    private fun matches(url: String?): Boolean {
        val host = url?.let { Uri.parse(it).host } ?: return false
        return service.hosts.any { host == it || host.endsWith(".$it") }
    }
}
