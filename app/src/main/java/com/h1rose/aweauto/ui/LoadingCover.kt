package com.h1rose.aweauto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.h1rose.aweauto.R
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.data.StreamService
import com.h1rose.aweauto.data.youtubeVideoId

/** 再生が始まるまで重ねる Google TV 風の読み込み画面。触ると閉じて下のページを操作できる */
@Composable
internal fun LoadingCover(service: StreamService, url: String, onDismiss: () -> Unit) {
    val history by Prefs.history.collectAsState()
    val title = youtubeVideoId(url)?.let { id -> history.firstOrNull { it.videoId == id }?.title }
    Box(
        Modifier
            .fillMaxSize()
            .background(AweColors.Background)
            .pressScale(onDismiss),
    ) {
        service.poster(url)?.let { poster ->
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x66111318), 0.6f to Color(0xCC111318), 1f to Color(0xF2111318))
            )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 40.dp, end = 40.dp, bottom = 32.dp),
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AweColors.Accent,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.loading), color = AweColors.OnSurfaceDim, fontSize = 15.sp)
            }
        }
    }
}
