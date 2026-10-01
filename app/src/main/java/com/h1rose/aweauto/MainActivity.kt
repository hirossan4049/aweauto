package com.h1rose.aweauto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.h1rose.aweauto.ui.AweColors
import com.h1rose.aweauto.ui.AweIcons
import com.h1rose.aweauto.ui.AweTheme
import com.h1rose.aweauto.ui.SettingsScreen

/** スマホ側の画面。車載画面とは別に、設定だけを軽く表示する。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AweTheme {
                Column(Modifier.fillMaxSize().background(AweColors.Background).statusBarsPadding()) {
                    Text(
                        "aweauto",
                        color = AweColors.OnSurfaceDim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
                    )
                    PhoneHeader(Modifier.padding(horizontal = 12.dp))
                    SettingsScreen(onBack = null)
                }
            }
        }
    }
}

/**
 * 以前はここに車載 UI をそのまま描いていたが、WebView セッションを車の Presentation と取り合って
 * 再生中の動画が重くなるため、スマホ側は静的な状態表示だけにする。
 */
@Composable
private fun PhoneHeader(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(800f / 480f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1B1D24), Color(0xFF182A31), Color(0xFF111318)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(AweIcons.DirectionsCar, contentDescription = null, tint = AweColors.OnSurface, modifier = Modifier.size(38.dp))
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.phone_header_title), color = AweColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.phone_header_desc), color = AweColors.OnSurfaceDim, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            androidx.compose.foundation.layout.Row {
                Icon(AweIcons.PlayCircle, contentDescription = null, tint = AweColors.OnSurfaceDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = AweColors.OnSurfaceDim, modifier = Modifier.size(18.dp))
            }
        }
    }
}
