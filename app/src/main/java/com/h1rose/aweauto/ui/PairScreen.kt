package com.h1rose.aweauto.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.h1rose.aweauto.R
import com.h1rose.aweauto.cast.CastStatus
import com.h1rose.aweauto.cast.LoungeReceiver
import kotlinx.coroutines.launch

/** 車の画面のホームから開く、キャストのつなぎ方だけの画面 */
@Composable
fun PairScreen(onBack: () -> Unit) {
    val status by LoungeReceiver.status.collectAsState()
    Column(Modifier.fillMaxSize().background(AweColors.Background)) {
        ScreenHeader(stringResource(R.string.cast_from_phone), onBack)
        Box(Modifier.padding(horizontal = 24.dp)) {
            PairingCard(status, qrSize = 170.dp)
        }
    }
}

/**
 * スマホからのキャストのつなぎ方。
 * - 同じ Wi-Fi / テザリング: YouTube のキャストボタンに自動で出る (DIAL)。入力不要
 * - それ以外: 初回だけテレビコードでリンク。スマホではコピー、車の画面では同乗者向けに QR も出す
 * 一度つながったスマホがあれば「リンク済み」だけ出してコードは畳む
 */
@Composable
fun PairingCard(status: CastStatus, qrSize: Dp = 120.dp) {
    val isCar = LocalIsCar.current
    var showCode by remember { mutableStateOf(false) }
    val linked = status.linked.isNotEmpty()
    val separator = stringResource(R.string.list_separator)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AweColors.Surface)
            .padding(18.dp),
    ) {
        StatusLine(status)
        if (linked && !showCode) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.cast_linked, status.linked.joinToString(separator)),
                color = AweColors.OnSurface,
                fontSize = 15.sp,
            )
            Text(
                stringResource(R.string.cast_linked_hint, LoungeReceiver.screenName),
                color = AweColors.OnSurfaceDim,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            Pill(stringResource(R.string.cast_add_phone)) { showCode = true }
            return@Column
        }

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.cast_same_wifi), color = AweColors.Accent, fontSize = 13.sp)
        Text(
            stringResource(R.string.cast_same_wifi_hint, LoungeReceiver.screenName),
            color = AweColors.OnSurface,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.cast_elsewhere), color = AweColors.Accent, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    status.pairingCode ?: stringResource(R.string.loading),
                    color = AweColors.OnSurface,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                )
                Text(stringResource(R.string.cast_code_where), color = AweColors.OnSurfaceDim, fontSize = 13.sp)
                if (!isCar) {
                    Spacer(Modifier.height(12.dp))
                    CopyAndOpenButton()
                }
            }
            val code = status.pairingCode
            if (isCar && code != null) {
                Spacer(Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCode(code.replace(" ", ""), Modifier.size(qrSize))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.cast_qr_hint), color = AweColors.OnSurfaceDim, fontSize = 11.sp)
                }
            }
        }
        if (linked) {
            Spacer(Modifier.height(12.dp))
            Pill(stringResource(R.string.close)) { showCode = false }
        }
    }
}

@Composable
private fun StatusLine(status: CastStatus) {
    val separator = stringResource(R.string.list_separator)
    val (text, color) = when {
        status.remotes.isNotEmpty() ->
            stringResource(R.string.cast_status_connected, status.remotes.joinToString(separator)) to AweColors.Accent
        status.online -> stringResource(R.string.cast_status_online) to AweColors.Accent
        status.error != null -> stringResource(R.string.cast_status_error, status.error) to AweColors.OnSurfaceDim
        else -> stringResource(R.string.cast_status_connecting) to AweColors.OnSurfaceDim
    }
    Text(text, color = color, fontSize = 13.sp)
}

/** その場で新しいコードを取ってコピーし、YouTube アプリを開く (スマホ側だけ) */
@Composable
private fun CopyAndOpenButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Pill(stringResource(R.string.cast_copy_open), primary = true) {
        scope.launch {
            val code = LoungeReceiver.freshPairingCode()?.replace(" ", "") ?: return@launch
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.cast_clip_label), code))
            Toast.makeText(context, R.string.cast_copied, Toast.LENGTH_LONG).show()
            context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")?.let(context::startActivity)
        }
    }
}
