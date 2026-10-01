package com.h1rose.aweauto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.h1rose.aweauto.R
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.hud.HudText
import com.h1rose.aweauto.hud.NowPlaying
import java.time.LocalTime

/**
 * HUD に出す好きな文字の入力欄と、今の表示のプレビュー。
 * 車の画面ではキーボードを出せないので、入力はスマホの設定画面だけでできる
 */
@Composable
internal fun HudTextCard() {
    val template by Prefs.hudCustomText.collectAsState()
    val title by NowPlaying.title.collectAsState()
    val stack by AweNav.backStack.collectAsState()
    val preview = HudText.format(template, title.takeIf { stack.lastOrNull() is Route.Web }, LocalTime.now())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AweColors.Surface)
            .padding(18.dp),
    ) {
        Text(stringResource(R.string.hud_custom_text_title), color = AweColors.OnSurface, fontSize = 16.sp)
        Text(stringResource(R.string.hud_custom_hint), color = AweColors.OnSurfaceDim, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        if (LocalIsCar.current) {
            Text(template, color = AweColors.OnSurface, fontSize = 15.sp)
            Text(stringResource(R.string.hud_custom_edit_on_phone), color = AweColors.OnSurfaceDim, fontSize = 12.sp)
        } else {
            OutlinedTextField(
                value = template,
                onValueChange = Prefs::setHudCustomText,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AweColors.OnSurface,
                    unfocusedTextColor = AweColors.OnSurface,
                    focusedBorderColor = AweColors.Accent,
                    unfocusedBorderColor = AweColors.SurfaceHigh,
                    cursorColor = AweColors.Accent,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.hud_custom_insert_title)) { Prefs.setHudCustomText(append(template, HudText.TITLE)) }
                Pill(stringResource(R.string.hud_custom_insert_time)) { Prefs.setHudCustomText(append(template, HudText.TIME)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (preview != null) stringResource(R.string.hud_custom_preview, preview)
            else stringResource(R.string.hud_custom_preview_empty),
            color = AweColors.Accent,
            fontSize = 13.sp,
        )
    }
}

private fun append(template: String, placeholder: String) =
    if (template.isBlank()) placeholder else "${template.trimEnd()} $placeholder"
