package com.h1rose.aweauto

import android.app.Application
import android.webkit.WebView
import com.h1rose.aweauto.adblock.AdBlocker
import com.h1rose.aweauto.data.Prefs

class AweApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        AdBlocker.init(this) { Prefs.filterLists.value }
        // chrome://inspect から WebView の DOM を覗けるようにしておく (CSS 調整用)
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
    }
}
