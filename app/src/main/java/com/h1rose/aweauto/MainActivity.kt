package com.h1rose.aweauto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.h1rose.aweauto.ui.AweColors
import com.h1rose.aweauto.ui.AweTheme
import com.h1rose.aweauto.ui.RemoteScreen
import com.h1rose.aweauto.ui.SettingsScreen

/** スマホ側の画面。車の画面のリモコンと、設定 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AweTheme {
                var settings by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = settings) { settings = false }
                Box(Modifier.fillMaxSize().background(AweColors.Background).statusBarsPadding()) {
                    if (settings) SettingsScreen(onBack = { settings = false })
                    else RemoteScreen(onSettings = { settings = true })
                }
            }
        }
    }
}
