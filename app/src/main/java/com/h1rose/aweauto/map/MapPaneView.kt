package com.h1rose.aweauto.map

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.h1rose.aweauto.ui.AweColors
import com.h1rose.aweauto.ui.LocalIsCar

/**
 * 地図枠。登録された [MapPaneProvider] に描画先とタッチを渡す。
 *
 * 描画先には TextureView を使う。SurfaceView は重なり順を変えるたびに作り直しが必要で、
 * そのたびに描画先が壊れて detach → attach が起き、中身 (他アプリの表示) が不安定になるため。
 * TextureView はふつうの View と同じ順で重なり、表示中は大きさが変わっても描画先を保ち続ける。
 */
@Composable
fun MapPane(modifier: Modifier = Modifier) {
    val provider by MapPanes.provider.collectAsState()
    Box(modifier.background(AweColors.Surface)) {
        val p = provider
        if (p == null) {
            Placeholder()
        } else if (!LocalIsCar.current) {
            // スマホのプレビューと車の画面で同じ中身を取り合わないよう、中身は車の画面にだけ出す
            Placeholder(title = "地図は車の画面に表示中", detail = p.label)
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> ProviderTextureView(ctx, p) },
                update = { it.provider = p },
                onRelease = { it.release() },
            )
        }
    }
}

@SuppressLint("ViewConstructor")
private class ProviderTextureView(
    context: android.content.Context,
    initial: MapPaneProvider,
) : TextureView(context), TextureView.SurfaceTextureListener {
    var provider: MapPaneProvider? = initial
        set(value) {
            if (field === value) return
            if (attached != null) field?.detach()
            field = value
            attached = null
            scheduleAttach()
        }

    private var surface: Surface? = null

    /** 最後に attach した大きさ。同じなら呼び直さない */
    private var attached: Pair<Int, Int>? = null
    private var surfaceSize = 0 to 0
    private val attachNow = Runnable { attachIfReady() }

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(texture)
        surfaceSize = width to height
        scheduleAttach()
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        // 描画先 (Surface) は同じまま。大きさが落ち着いたら同じ Surface で attach し直して大きさだけ伝える
        surfaceSize = width to height
        scheduleAttach()
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        removeCallbacks(attachNow)
        if (attached != null) provider?.detach()
        attached = null
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    fun release() {
        removeCallbacks(attachNow)
        if (attached != null) provider?.detach()
        attached = null
    }

    /**
     * 並べ方の切り替えでは大きさがアニメーションで毎フレーム変わる。そのたびに attach すると
     * 中身 (他アプリの表示など) が作り直しになって重いので、大きさが落ち着いてから 1 回だけ呼ぶ。
     */
    private fun scheduleAttach() {
        removeCallbacks(attachNow)
        postDelayed(attachNow, ATTACH_SETTLE_MS)
    }

    private fun attachIfReady() {
        val p = provider ?: return
        val s = surface?.takeIf { it.isValid } ?: return
        val (w, h) = surfaceSize
        if (w < MIN_SIZE_PX || h < MIN_SIZE_PX) return
        if (attached == surfaceSize) return
        attached = surfaceSize
        p.attach(s, w, h, mapDensityDpi(w, h, resources.displayMetrics.densityDpi))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        provider?.onTouch(event.actionMasked, event.x, event.y, event.downTime, event.eventTime) ?: return false
        return true
    }
}

private const val ATTACH_SETTLE_MS = 300L

/**
 * 地図アプリに見せる画面の最小サイズ (dp)。横長の枠は 400x320、縦長の枠はスマホの縦画面くらいの 360x480。
 * これくらいあれば検索欄・下のシート・ボタン類が重ならずに並ぶ。
 */
private const val MAP_MIN_LANDSCAPE_W_DP = 400f
private const val MAP_MIN_LANDSCAPE_H_DP = 320f
private const val MAP_MIN_PORTRAIT_W_DP = 360f
private const val MAP_MIN_PORTRAIT_H_DP = 480f

/** 小さくしすぎると文字が読めなくなるので、ここより下げない */
private const val MAP_MIN_DPI = 110

/**
 * 地図枠が小さいときは密度を下げて、地図アプリには「広い画面」として描かせる。
 * 文字やボタンは小さくなるが、検索欄や下のシートが画面を埋めて地図が見えなくなるのを防ぐ。
 */
internal fun mapDensityDpi(widthPx: Int, heightPx: Int, baseDpi: Int): Int {
    val portrait = heightPx > widthPx
    val minW = if (portrait) MAP_MIN_PORTRAIT_W_DP else MAP_MIN_LANDSCAPE_W_DP
    val minH = if (portrait) MAP_MIN_PORTRAIT_H_DP else MAP_MIN_LANDSCAPE_H_DP
    val fitWidth = widthPx * 160f / minW
    val fitHeight = heightPx * 160f / minH
    return minOf(baseDpi.toFloat(), fitWidth, fitHeight).toInt().coerceIn(minOf(MAP_MIN_DPI, baseDpi), baseDpi)
}
private const val MIN_SIZE_PX = 16

@Composable
private fun Placeholder(
    title: String = "地図の表示方法が設定されていません",
    detail: String = "MapPanes.register(...) で MapPaneProvider を登録すると、ここに表示されます",
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Map, contentDescription = null, tint = AweColors.OnSurfaceDim, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            color = AweColors.OnSurface,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            detail,
            color = AweColors.OnSurfaceDim,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
