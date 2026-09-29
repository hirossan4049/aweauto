package com.h1rose.aweauto.map

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
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

/** 地図枠。登録された [MapPaneProvider] に描画先とタッチを渡す */
@Composable
fun MapPane(modifier: Modifier = Modifier) {
    val provider by MapPanes.provider.collectAsState()
    Box(modifier.background(AweColors.Surface)) {
        val p = provider
        if (p == null) {
            Placeholder()
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> ProviderSurfaceView(ctx, p) },
                update = { it.provider = p },
                onRelease = { it.provider?.detach() },
            )
        }
    }
}

@SuppressLint("ViewConstructor")
private class ProviderSurfaceView(
    context: android.content.Context,
    initial: MapPaneProvider,
) : SurfaceView(context), SurfaceHolder.Callback {
    var provider: MapPaneProvider? = initial
        set(value) {
            if (field === value) return
            field?.detach()
            field = value
            if (holder.surface?.isValid == true && width > 0) {
                value?.attach(holder.surface, width, height, resources.displayMetrics.densityDpi)
            }
        }

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        provider?.attach(holder.surface, width, height, resources.displayMetrics.densityDpi)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        provider?.detach()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        provider?.onTouch(event.actionMasked, event.x, event.y, event.downTime, event.eventTime) ?: return false
        return true
    }
}

@Composable
private fun Placeholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Map, contentDescription = null, tint = AweColors.OnSurfaceDim, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            "地図の表示方法が設定されていません",
            color = AweColors.OnSurface,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            "MapPanes.register(...) で MapPaneProvider を登録すると、ここに表示されます",
            color = AweColors.OnSurfaceDim,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
