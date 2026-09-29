package com.hirossan.aweauto.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** 車載画面のルート */
@Composable
fun AweRoot() {
    val stack by AweNav.backStack.collectAsState()
    AweTheme {
        AnimatedContent(
            targetState = stack.last(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "route",
        ) { route ->
            when (route) {
                Route.Home -> HomeScreen()
                Route.Settings -> SettingsScreen(onBack = { AweNav.back() })
                is Route.Web -> WebScreen(route)
            }
        }
    }
}
