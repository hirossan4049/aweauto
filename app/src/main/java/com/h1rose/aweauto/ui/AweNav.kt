package com.h1rose.aweauto.ui

import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.data.StreamService
import com.h1rose.aweauto.map.MapPanes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface Route {
    data object Home : Route
    data object Settings : Route
    data object Pair : Route
    data class Web(val service: StreamService, val url: String) : Route
}

/**
 * 車載画面の画面遷移。Car App 側のアクションボタンからも操作するのでシングルトンにしている。
 * Web 画面内の「戻る」は WebView の履歴を優先するため [webBackHandler] 経由で問い合わせる。
 */
object AweNav {
    private val stack = MutableStateFlow<List<Route>>(listOf(Route.Home))
    val backStack: StateFlow<List<Route>> = stack.asStateFlow()

    /** WebView が履歴を戻せたら true を返す */
    var webBackHandler: (() -> Boolean)? = null

    fun go(route: Route) {
        stack.value = if (route == Route.Home) listOf(Route.Home) else stack.value + route
    }

    fun home() = go(Route.Home)

    fun back() {
        if (Prefs.splitMap.value && MapPanes.provider.value?.onBack() == true) return
        if (webBackHandler?.invoke() == true) return
        if (stack.value.size > 1) stack.value = stack.value.dropLast(1)
    }
}
