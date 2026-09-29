package com.h1rose.aweauto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.h1rose.aweauto.ui.AweColors
import com.h1rose.aweauto.ui.AweRoot
import com.h1rose.aweauto.ui.AweTheme
import com.h1rose.aweauto.ui.SettingsScreen

/** スマホ側の画面。車載 UI のプレビューと設定を並べる */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AweTheme {
                Column(Modifier.fillMaxSize().background(AweColors.Background).statusBarsPadding()) {
                    Text(
                        "車載画面プレビュー (800×480)",
                        color = AweColors.OnSurfaceDim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
                    )
                    CarPreview(Modifier.padding(horizontal = 12.dp))
                    SettingsScreen(onBack = null)
                }
            }
        }
    }
}

/** DHU の既定解像度と同じ 800×480dp で描いて、スマホの幅に縮小表示する */
@Composable
private fun CarPreview(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(800f / 480f)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        val scaled = Density(density = constraints.maxWidth / 800f, fontScale = 1f)
        CompositionLocalProvider(LocalDensity provides scaled) {
            AweRoot()
        }
    }
}
