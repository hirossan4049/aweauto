package com.hirossan.aweauto

import android.app.Application
import android.webkit.WebView
import com.hirossan.aweauto.data.Prefs

class AweApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        // chrome://inspect から WebView の DOM を覗けるようにしておく (CSS 調整用)
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
    }
}
