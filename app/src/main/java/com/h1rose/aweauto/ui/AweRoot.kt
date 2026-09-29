package com.h1rose.aweauto.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.map.MapPane

/** 車載画面のルート。分割モードでは左に地図枠、右にいつもの画面 */
@Composable
fun AweRoot() {
    val split by Prefs.splitMap.collectAsState()
    AweTheme {
        if (split) {
            Row(Modifier.fillMaxSize()) {
                MapPane(Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.width(2.dp).fillMaxHeight().background(AweColors.Background))
                Box(Modifier.weight(1f).fillMaxHeight()) { Screens() }
            }
        } else {
            Screens()
        }
    }
}

@Composable
private fun Screens() {
    val stack by AweNav.backStack.collectAsState()
    run {
        AnimatedContent(
            targetState = stack.last(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "route",
        ) { route ->
            when (route) {
                Route.Home -> HomeScreen()
                Route.Settings -> SettingsScreen(onBack = { AweNav.back() })
                Route.Pair -> PairScreen(onBack = { AweNav.back() })
                is Route.Web -> WebScreen(route)
            }
        }
    }
}
