package com.h1rose.aweauto.hud

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.h1rose.aweauto.map.NavApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AweHud"

/** 今のナビ案内。ナビ中でなければ null */
data class HudState(
    val guidance: NavGuidance,
    /** 通知の大きいアイコン (地図アプリが描いた曲がり方の矢印) */
    val arrow: Icon?,
    /** 案内元のアプリ */
    val packageName: String,
    /** 案内元の通知 */
    val key: String,
)

object Hud {
    private val _state = MutableStateFlow<HudState?>(null)
    val state: StateFlow<HudState?> = _state.asStateFlow()

    /** 通知へのアクセスが許可されていて、リスナーがつながっているか */
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    internal fun update(state: HudState?) {
        if (_state.value != state) _state.value = state
    }

    internal fun setListening(on: Boolean) {
        _listening.value = on
        if (!on) _state.value = null
    }

    fun isAccessGranted(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        val me = ComponentName(context, NavNotificationListener::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}

/**
 * 地図アプリのナビ通知を読んで、[Hud.state] に流す。
 * 車の HUD・メーターへの送信は車の画面側 (HudSender) が受け持つ。
 *
 * 対象は次のどちらか。
 * - カテゴリが navigation の通知 (Google マップなど)
 * - 地図・カーナビアプリ (geo: を開けるアプリ) が出し続けている通知のうち、距離が読み取れるもの
 *   (カテゴリを付けない Yahoo!カーナビ・NAVITIME など)
 * 文字が独自レイアウトの中にしかないアプリは、レイアウトを組み立てて中の文字を読む。
 */
class NavNotificationListener : NotificationListenerService() {
    /** 地図・カーナビアプリのパッケージ。つながるたびに数え直す */
    private var navPackages: Set<String> = emptySet()

    override fun onListenerConnected() {
        navPackages = NavApps.navigationCapable(this)
        Hud.setListening(true)
        latest()?.let { (sbn, guidance) -> publish(sbn, guidance) }
    }

    override fun onListenerDisconnected() {
        Hud.setListening(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        read(sbn)?.let { publish(sbn, it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (Hud.state.value?.key != sbn.key) return
        // 別の地図アプリがまだナビ中ならそちらに切り替える
        val other = latest(exceptKey = sbn.key)
        if (other != null) publish(other.first, other.second) else Hud.update(null)
    }

    private fun latest(exceptKey: String? = null): Pair<StatusBarNotification, NavGuidance>? =
        activeNotifications.orEmpty()
            .filter { it.key != exceptKey }
            .sortedByDescending { it.postTime }
            .firstNotNullOfOrNull { sbn -> read(sbn)?.let { sbn to it } }

    private fun publish(sbn: StatusBarNotification, guidance: NavGuidance) {
        Hud.update(HudState(guidance, sbn.notification.getLargeIcon(), sbn.packageName, sbn.key))
    }

    private fun read(sbn: StatusBarNotification): NavGuidance? {
        if (sbn.packageName == packageName) return null
        val n = sbn.notification
        val isNavCategory = n.category == Notification.CATEGORY_NAVIGATION
        if (!isNavCategory && !(sbn.isOngoing && sbn.packageName in navPackages)) return null
        val guidance = NavGuidanceParser.parse(texts(n)) ?: return null
        // カテゴリの無い通知は「GPS 測位中」のような常駐表示もあるので、距離が読めたものだけ案内とみなす
        if (!isNavCategory && guidance.stepMeters == null) return null
        return guidance
    }

    /** 通知に出ている文字を表示順に集める */
    private fun texts(n: Notification): List<String> {
        val e = n.extras
        val fromExtras = buildList {
            e.getCharSequence(Notification.EXTRA_TITLE)?.let { add(it.toString()) }
            (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))
                ?.let { add(it.toString()) }
            e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it.toString()) }
            e.getCharSequence(Notification.EXTRA_SUB_TEXT)?.let { add(it.toString()) }
        }
        if (fromExtras.any { it.isNotBlank() }) return fromExtras
        @Suppress("DEPRECATION")
        val views = n.bigContentView ?: n.contentView ?: return emptyList()
        return runCatching {
            val root = views.apply(this, FrameLayout(this))
            buildList { collectText(root, this) }
        }.onFailure { Log.d(TAG, "could not inflate custom notification", it) }.getOrDefault(emptyList())
    }

    private fun collectText(view: View, out: MutableList<String>) {
        if (view.visibility != View.VISIBLE) return
        when (view) {
            is TextView -> view.text?.toString()?.let(out::add)
            is ViewGroup -> for (i in 0 until view.childCount) collectText(view.getChildAt(i), out)
        }
    }
}
