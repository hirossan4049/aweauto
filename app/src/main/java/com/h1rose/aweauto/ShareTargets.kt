package com.h1rose.aweauto

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * 共有メニューの一番上の列 (ダイレクトシェア) に「車の画面」を出す。
 * Android 10 以降は共有ショートカット、9 以前は androidx.sharetarget の ChooserTargetServiceCompat が使われる。
 */
object ShareTargets {
    const val CATEGORY = "com.h1rose.aweauto.category.CAR_SCREEN"

    fun publish(context: Context) {
        val shortcut = ShortcutInfoCompat.Builder(context, "car_screen")
            .setShortLabel(context.getString(R.string.share_target_label))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            // 共有から起動されたときは ShareActivity に EXTRA_SHORTCUT_ID 付きで届く。ここの Intent は使われない
            .setIntent(Intent(Intent.ACTION_VIEW).setClass(context, MainActivity::class.java))
            .setCategories(setOf(CATEGORY))
            .setLongLived(true)
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
    }
}
