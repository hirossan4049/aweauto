package com.h1rose.aweauto.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.h1rose.aweauto.data.StreamService

/** サービスのアイコン。画像ファイルは持たずに描く (オフラインでも出る) */
@Composable
fun BrandIcon(service: StreamService, size: Dp, modifier: Modifier = Modifier) {
    when {
        service == StreamService.YOUTUBE -> YouTubeMark(size, modifier)
        service == StreamService.TVER -> TVerMark(size, modifier)
        // 専用のマークを用意していないサイトは、ブランド色の角丸にサイト名の頭文字
        else -> LetterMark(service, size, modifier)
    }
}

@Composable
private fun LetterMark(service: StreamService, size: Dp, modifier: Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(service.brand),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            service.label.take(1),
            color = Color.White,
            fontSize = (size.value * 0.46f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 赤い角丸に白い再生マーク */
@Composable
private fun YouTubeMark(size: Dp, modifier: Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = w * 0.70f
        val top = (this.size.height - h) / 2
        drawRoundRect(
            color = StreamService.YOUTUBE.brand,
            topLeft = Offset(0f, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(h * 0.28f),
        )
        val cx = w / 2
        val cy = top + h / 2
        val t = h * 0.42f
        val play = Path().apply {
            moveTo(cx - t * 0.40f, cy - t / 2)
            lineTo(cx + t * 0.55f, cy)
            lineTo(cx - t * 0.40f, cy + t / 2)
            close()
        }
        drawPath(play, Color.White)
    }
}

/** 水色の角丸に「TVer」 */
@Composable
private fun TVerMark(size: Dp, modifier: Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(StreamService.TVER.brand),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "TVer",
            color = Color.White,
            fontSize = (size.value * 0.30f).sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            letterSpacing = (-0.5).sp,
        )
    }
}

/** 車載画面の左上に出すアプリのマーク (ランチャーアイコンと同じ図柄) */
@Composable
fun AppMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width
        drawCircle(AweColors.Accent)
        val w = s * 0.52f
        val h = s * 0.36f
        val left = (s - w) / 2
        val top = s * 0.28f
        drawRoundRect(AweColors.OnChip, Offset(left, top), Size(w, h), CornerRadius(s * 0.06f))
        val cx = s / 2
        val cy = top + h / 2
        val t = h * 0.46f
        drawPath(
            Path().apply {
                moveTo(cx - t * 0.4f, cy - t / 2); lineTo(cx + t * 0.55f, cy); lineTo(cx - t * 0.4f, cy + t / 2); close()
            },
            AweColors.Accent,
        )
        drawRoundRect(AweColors.OnChip, Offset(cx - s * 0.12f, top + h + s * 0.07f), Size(s * 0.24f, s * 0.045f), CornerRadius(s * 0.02f))
    }
}
