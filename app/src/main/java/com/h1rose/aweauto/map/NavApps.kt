package com.h1rose.aweauto.map

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.h1rose.aweauto.R

/** 地図枠に出すアプリ */
data class NativeMapApp(
    val packageName: String,
    val label: String,
)

/**
 * 端末に入っているカーナビ・地図アプリを探す。
 *
 * 決め打ちの一覧ではなく「geo: やナビ開始のリンクを開けるアプリ」を集めるので、Google マップ・Waze・
 * Yahoo!カーナビ・NAVITIME などを個別に書かなくても出てくる。
 * Android Auto のナビ枠 (Car App Library の NAVIGATION) は動画アプリなども名乗れるので手がかりにしない。
 * ホーム画面から起動できないものは地図枠にも出せないので除く。位置を偽装するアプリも geo: を開けるが地図ではないので除く。
 */
object NavApps {
    /** よく使われるものを先頭に並べる (この順)。入っていないものは出さない */
    private val PREFERRED = listOf(
        "com.google.android.apps.maps",
        "jp.co.yahoo.android.apps.navi",
        "jp.co.yahoo.android.apps.map",
        "com.navitime.local.navitimedrive",
        "com.navitime.local.navitime",
        "jp.dmapnavi.navi02",
        "com.huawei.maps.app",
        "com.here.app.maps",
        "com.sygic.aura",
        "com.tomtom.gplay.navapp",
        "net.osmand.plus",
        "net.osmand",
        "app.organicmaps",
        "com.generalmagic.magicearth",
    )

    /**
     * Android Auto につないでいる間はスマホ側の画面を「車の画面で使ってください」で塞いでしまうアプリ。
     * 地図枠に起動しても中身が出ないので候補に出さない
     */
    private val LOCKED_WHILE_PROJECTING = setOf(
        "com.waze",
    )

    /** 以前の設定値 (アプリの種類の ID) */
    private val LEGACY_IDS = mapOf(
        "google" to "com.google.android.apps.maps",
        "yahoo" to "jp.co.yahoo.android.apps.map",
    )

    /** 地図・カーナビアプリのパッケージ (ナビ通知を読む対象。地図枠に出せないものも含む) */
    fun navigationCapable(context: Context): Set<String> {
        val pm = context.packageManager
        val packages = linkedSetOf<String>()
        listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")),
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=")),
        ).forEach { intent ->
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .forEach { packages += it.activityInfo.packageName }
        }
        PREFERRED.forEach { if (isInstalled(pm, it)) packages += it }
        packages -= context.packageName
        return packages.filterNot { mocksLocation(pm, it) }.toSet()
    }

    fun installed(context: Context): List<NativeMapApp> {
        val pm = context.packageManager
        val candidates = navigationCapable(context) - LOCKED_WHILE_PROJECTING

        return candidates
            .filter { pm.getLaunchIntentForPackage(it) != null }
            .map { NativeMapApp(it, label(pm, it)) }
            .sortedWith(compareBy({ PREFERRED.indexOf(it.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.label }))
    }

    /** 設定値からアプリを決める。入っていなければ見つかったうちの先頭 (なければ Google マップ) */
    fun resolve(context: Context, id: String?): NativeMapApp {
        val pm = context.packageManager
        val pkg = LEGACY_IDS[id] ?: id
        if (pkg != null && pkg !in LOCKED_WHILE_PROJECTING && isInstalled(pm, pkg)) return NativeMapApp(pkg, label(pm, pkg))
        return installed(context).firstOrNull() ?: NativeMapApp(PREFERRED.first(), context.getString(R.string.google_maps))
    }

    @Suppress("DEPRECATION")
    private fun mocksLocation(pm: PackageManager, pkg: String): Boolean =
        runCatching {
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
                .contains("android.permission.ACCESS_MOCK_LOCATION")
        }.getOrDefault(false)

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean =
        runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess

    private fun label(pm: PackageManager, pkg: String): String =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
}
