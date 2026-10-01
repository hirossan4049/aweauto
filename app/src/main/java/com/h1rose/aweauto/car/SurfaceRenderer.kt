package com.h1rose.aweauto.car

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

private const val TAG = "AweSurface"

/**
 * 車の画面 (仮想ディスプレイ + Presentation) をアプリが動いている間ずっと持ち続ける。
 *
 * Android Auto で別の画面に切り替えたりバックカメラが割り込んだりすると、描画先の Surface は
 * 破棄・再作成されるが、仮想ディスプレイと画面は作り直さずに Surface だけ差し替える。
 * こうしないと、戻るたびに画面の状態 (ホームのタブやスクロール位置、WebView の大きさ) が初期化される。
 */
object CarDisplayHost {
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: CarPresentation? = null
    private var size = Triple(0, 0, 0)

    val touch: TouchInjector? get() = presentation?.touch

    fun attach(context: Context, surface: Surface, width: Int, height: Int, dpi: Int) {
        val vd = virtualDisplay
        if (vd == null) {
            val dm = context.applicationContext.getSystemService(DisplayManager::class.java)
            virtualDisplay = dm.createVirtualDisplay(
                "aweauto", width, height, dpi, surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            )
            size = Triple(width, height, dpi)
            presentation = CarPresentation(context.applicationContext, virtualDisplay!!.display).also { it.show() }
            Log.i(TAG, "created display ${width}x$height dpi=$dpi")
            return
        }
        if (size != Triple(width, height, dpi)) {
            vd.resize(width, height, dpi)
            size = Triple(width, height, dpi)
            Log.i(TAG, "resized display ${width}x$height dpi=$dpi")
        }
        vd.surface = surface
    }

    /** Surface が無くなった。画面と中身 (再生中の動画など) は残しておく */
    fun detach() {
        virtualDisplay?.surface = null
    }
}

/** Car App Library の地図用 Surface を [CarDisplayHost] に渡す */
class SurfaceRenderer(private val carContext: CarContext, lifecycle: Lifecycle) : DefaultLifecycleObserver {
    private val callback = object : SurfaceCallback {
        override fun onSurfaceAvailable(container: SurfaceContainer) {
            Log.i(TAG, "surface ${container.width}x${container.height} dpi=${container.dpi}")
            val surface = container.surface ?: return
            CarDisplayHost.attach(carContext, surface, container.width, container.height, container.dpi)
        }

        override fun onSurfaceDestroyed(container: SurfaceContainer) = CarDisplayHost.detach()

        override fun onStableAreaChanged(area: Rect) {
            Log.i(TAG, "stableArea=$area")
        }

        override fun onVisibleAreaChanged(area: Rect) {
            Log.i(TAG, "visibleArea=$area")
        }

        override fun onClick(x: Float, y: Float) {
            CarDisplayHost.touch?.click(x, y)
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            CarDisplayHost.touch?.scroll(distanceX, distanceY)
        }

        override fun onFling(velocityX: Float, velocityY: Float) {
            // 慣性は TouchInjector が送る MOVE → UP の速度から各 View が自前で付ける
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(callback)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        CarDisplayHost.detach()
    }
}
