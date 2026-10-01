package com.h1rose.aweauto.car

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.h1rose.aweauto.ui.LocalIsCar
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
    private fun CarContent() = BoxWithConstraints {
        val metrics = context.resources.displayMetrics
        // 横幅がおよそ 760dp 以上になるように密度を抑える (小さい車載画面で UI がはみ出さないように)。
        // 画面の大きさは Android Auto の表示の仕方で変わるので、実際の幅から毎回計算する
        val density = minOf(metrics.density, constraints.maxWidth / 760f)
        CompositionLocalProvider(
            LocalDensity provides Density(density, fontScale = 1f),
            // 右上には Android Auto の「戻る」ボタンが重なる (数秒で自動的に隠れる)
            LocalTopEndReserve provides 110.dp,
            LocalIsCar provides true,
        ) {
            AweRoot()
        }
    }

    /**
     * Presentation は表示先の大きさが変わると自分で閉じてしまう (cancel)。
     * 車の画面は表示の仕方で大きさが変わるたびに作り直さず使い続けたいので、自動で閉じるのは無視する。
     * 閉じるときは [dismiss] を直接呼ぶ。
     */
    override fun cancel() = Unit

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
