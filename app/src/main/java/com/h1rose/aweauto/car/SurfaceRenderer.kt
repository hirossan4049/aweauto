package com.h1rose.aweauto.car

import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

private const val TAG = "AweSurface"

/** 地図用に貰える Surface を VirtualDisplay にして、そこへ [CarPresentation] を表示する */
class SurfaceRenderer(private val carContext: CarContext, lifecycle: Lifecycle) : DefaultLifecycleObserver {
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: CarPresentation? = null

    private val callback = object : SurfaceCallback {
        override fun onSurfaceAvailable(container: SurfaceContainer) {
            Log.i(TAG, "surface ${container.width}x${container.height} dpi=${container.dpi}")
            start(container)
        }

        override fun onSurfaceDestroyed(container: SurfaceContainer) = stop()

        override fun onStableAreaChanged(area: Rect) {
            Log.i(TAG, "stableArea=$area")
        }

        override fun onVisibleAreaChanged(area: Rect) {
            Log.i(TAG, "visibleArea=$area")
        }

        override fun onClick(x: Float, y: Float) {
            presentation?.touch?.click(x, y)
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            presentation?.touch?.scroll(distanceX, distanceY)
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
        stop()
    }

    private fun start(container: SurfaceContainer) {
        stop()
        val surface = container.surface ?: return
        val dm = carContext.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay(
            "aweauto",
            container.width,
            container.height,
            container.dpi,
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
        )
        virtualDisplay = vd
        presentation = CarPresentation(carContext, vd.display).also { it.show() }
    }

    private fun stop() {
        presentation?.dismiss()
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
    }
}
