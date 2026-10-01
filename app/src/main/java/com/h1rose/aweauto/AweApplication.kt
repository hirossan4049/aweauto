package com.h1rose.aweauto

import android.app.Application
import android.webkit.WebView
import com.h1rose.aweauto.adblock.AdBlocker
import com.h1rose.aweauto.cast.CastBridge
import com.h1rose.aweauto.cast.DialServer
import com.h1rose.aweauto.cast.LoungeReceiver
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.data.StreamService
import com.h1rose.aweauto.map.DemoMapPane
import com.h1rose.aweauto.map.MapLayouts
import com.h1rose.aweauto.map.MapPanes
import com.h1rose.aweauto.map.NativeAppMapPane
import com.h1rose.aweauto.map.NavApps
import com.h1rose.aweauto.shizuku.ShizukuKeeper
import com.h1rose.aweauto.shizuku.ShizukuState
import com.h1rose.aweauto.ui.reloadSessions
import com.h1rose.aweauto.web.YouTubeAccount
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class AweApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        MapLayouts.init(this)
        AdBlocker.init(this) { Prefs.filterLists.value }
        LoungeReceiver.init(this)
        LoungeReceiver.screenName = getString(R.string.cast_screen_name)
        if (Prefs.cast.value) {
            LoungeReceiver.start(CastBridge)
            DialServer.start(this)
        }
        ShareTargets.publish(this)
        ShizukuKeeper.init(this)
        ShizukuState.init()
        YouTubeAccount.refresh()
        // ログイン・ログアウトしたら、開いている YouTube の画面をそのアカウントで読み込み直す
        MainScope().launch { YouTubeAccount.signedIn.drop(1).collect { reloadSessions(StreamService.YOUTUBE) } }
        // 開発用: adb shell am start ... --ez demo_map true で地図枠のテスト表示を出せるようにする代わりに、
        // debug ビルドでは起動時に DEMO_MAP_PANE が有効ならテスト表示を登録する
        if (BuildConfig.DEBUG && getSharedPreferences("aweauto", MODE_PRIVATE).getBoolean("demo_map_pane", false)) {
            MapPanes.register(DemoMapPane())
        } else {
            MapPanes.register(NativeAppMapPane(this, NavApps.resolve(this, Prefs.mapApp.value)))
        }
        // chrome://inspect から WebView の DOM を覗けるようにしておく (CSS 調整用)
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
    }
}
