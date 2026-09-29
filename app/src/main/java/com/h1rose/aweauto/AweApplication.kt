package com.h1rose.aweauto

import android.app.Application
import android.webkit.WebView
import com.h1rose.aweauto.adblock.AdBlocker
import com.h1rose.aweauto.cast.CastBridge
import com.h1rose.aweauto.cast.LoungeReceiver
import com.h1rose.aweauto.data.Prefs

class AweApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        AdBlocker.init(this) { Prefs.filterLists.value }
        LoungeReceiver.init(this)
        LoungeReceiver.screenName = "aweauto (車)"
        if (Prefs.cast.value) LoungeReceiver.start(CastBridge)
        // chrome://inspect から WebView の DOM を覗けるようにしておく (CSS 調整用)
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
    }
}
