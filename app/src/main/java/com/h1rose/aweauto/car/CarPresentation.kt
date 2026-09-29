package com.h1rose.aweauto.car

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.unit.dp
import com.h1rose.aweauto.ui.AweRoot
import com.h1rose.aweauto.ui.LocalTopEndReserve

/**
 * 車の Surface に紐づいた VirtualDisplay 上に出す画面。
 * Activity ではないので、Compose が必要とする Lifecycle などは自前で持つ。
 */
class CarPresentation(
    context: Context,
    display: Display,
) : Presentation(context, display), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    val touch = TouchInjector { window?.decorView }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@CarPresentation)
            setViewTreeSavedStateRegistryOwner(this@CarPresentation)
            setViewTreeViewModelStoreOwner(this@CarPresentation)
            setContent { CarContent() }
        }
        setContentView(view)
    }

    @androidx.compose.runtime.Composable
    private fun CarContent() {
        val metrics = context.resources.displayMetrics
        // 横幅がおよそ 760dp 以上になるように密度を抑える (小さい車載画面で UI がはみ出さないように)
        val density = minOf(metrics.density, metrics.widthPixels / 760f)
        CompositionLocalProvider(
            LocalDensity provides Density(density, fontScale = 1f),
            // 右上には Android Auto の「戻る」ボタンが重なる (数秒で自動的に隠れる)
            LocalTopEndReserve provides 110.dp,
        ) {
            AweRoot()
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onStop()
    }
}
