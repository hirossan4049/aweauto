package com.h1rose.aweauto.car

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Distance
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Destination
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.h1rose.aweauto.R
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.hud.Hud
import com.h1rose.aweauto.hud.HudState
import com.h1rose.aweauto.hud.HudText
import com.h1rose.aweauto.hud.NowPlaying
import com.h1rose.aweauto.ui.AweNav
import com.h1rose.aweauto.ui.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

private const val TAG = "AweHud"

/**
 * 地図枠のアプリ (Google マップなど) のナビ案内を、車の HUD・メーター表示に送る。
 *
 * 地図枠のアプリは車とつながっていないので、そのままでは HUD に何も出ない。
 * Android Auto のナビアプリである aweauto が代わりに NavigationManager で案内を送る。
 * 何をどう出すかは車次第 (DHU は次の案内だけを出す)。
 */
class HudSender(private val carContext: CarContext, lifecycle: Lifecycle) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val nav by lazy { carContext.getCarService(NavigationManager::class.java) }
    private var navigating = false

    /** 車から「ナビをやめて」と言われたら、いったん送るのをやめる。次のナビが始まるまで再開しない */
    private var stoppedByCar = false
    private var lastOutput: HudOutput? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        nav.setNavigationManagerCallback(object : NavigationManagerCallback {
            override fun onStopNavigation() {
                Log.i(TAG, "car asked to stop navigation")
                stoppedByCar = true
                navigating = false
            }
        })
        scope.launch {
            // 地図アプリの道案内が最優先。案内が無いときは、設定した好きな文字を出す
            combine(Hud.state, Prefs.hud, customText()) { state, guidanceOn, custom ->
                state?.takeIf { guidanceOn }?.let(HudOutput::Guidance) ?: custom?.let(HudOutput::Custom)
            }
                .distinctUntilChanged()
                .collect { send(it) }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        scope.cancel()
        end()
        nav.clearNavigationManagerCallback()
    }

    /** 好きな文字を置き換えたもの。OFF や空なら null。{time} は 1 分ごと、{title} は再生ページを見ている間だけ入る */
    private fun customText(): Flow<String?> {
        val title = combine(NowPlaying.title, AweNav.backStack) { t, stack -> t.takeIf { stack.lastOrNull() is Route.Web } }
        return combine(Prefs.hudCustom, Prefs.hudCustomText, title, minutes()) { on, template, t, now ->
            if (on) HudText.format(template, t, now) else null
        }
    }

    /** 今の時刻を、分が変わるたびに流す */
    private fun minutes(): Flow<LocalTime> = flow {
        while (true) {
            val now = LocalTime.now()
            emit(now.truncatedTo(ChronoUnit.MINUTES))
            delay(((60 - now.second) * 1000L - now.nano / 1_000_000).coerceAtLeast(1L))
        }
    }

    private sealed interface HudOutput {
        data class Guidance(val state: HudState) : HudOutput
        data class Custom(val text: String) : HudOutput
    }

    private fun send(output: HudOutput?) {
        if (output == null) {
            stoppedByCar = false
            lastOutput = null
            end()
            return
        }
        // 好きな文字を出している間に止められたら、地図枠のアプリで新しくナビが始まるまで待つ
        // ({time} が変わるたびに再開すると、車側で始めた別のナビと取り合いになる)
        if (stoppedByCar && !(output is HudOutput.Guidance && lastOutput is HudOutput.Custom)) {
            lastOutput = output
            return
        }
        stoppedByCar = false
        lastOutput = output
        runCatching {
            if (!navigating) {
                nav.navigationStarted()
                navigating = true
                Log.i(TAG, "navigation started (${(output as? HudOutput.Guidance)?.state?.packageName ?: "custom text"})")
            }
            nav.updateTrip(
                when (output) {
                    is HudOutput.Guidance -> trip(output.state)
                    is HudOutput.Custom -> customTrip(output.text)
                },
            )
        }.onFailure {
            Log.w(TAG, "updateTrip failed", it)
        }
    }

    /**
     * 好きな文字だけの案内。車によって出す項目が違うので、案内文・道路名・今の道路・目的地の名前の全部に入れる。
     * 案内には距離が必須なので 0 m を付ける (車によっては「0 m」も出る)。方向を誤解させないよう矢印は「不明」にする
     */
    private fun customTrip(text: String): Trip {
        val now = ZonedDateTime.now()
        val step = Step.Builder(text)
            .setRoad(text)
            .setManeuver(Maneuver.Builder(Maneuver.TYPE_UNKNOWN).build())
            .build()
        val estimate = TravelEstimate.Builder(distance(0.0), now).build()
        return Trip.Builder()
            .addStep(step, estimate)
            .addDestination(Destination.Builder().setName(text).build(), estimate)
            .setCurrentRoad(text)
            .build()
    }

    private fun end() {
        if (!navigating) return
        navigating = false
        runCatching { nav.navigationEnded() }.onFailure { Log.w(TAG, "navigationEnded failed", it) }
        Log.i(TAG, "navigation ended")
    }

    private fun trip(state: HudState): Trip {
        val g = state.guidance
        val now = ZonedDateTime.now()
        val arrival = g.arrival?.let { t ->
            now.with(t).let { if (it.isBefore(now.minusMinutes(1))) it.plusDays(1) else it }
        } ?: g.remainingSeconds?.let { now.plusSeconds(it) } ?: now

        val maneuver = Maneuver.Builder(g.maneuverType).apply {
            state.arrow?.let { icon ->
                runCatching { IconCompat.createFromIcon(carContext, icon) }.getOrNull()
                    ?.let { setIcon(CarIcon.Builder(it).build()) }
            }
            // ロータリーの出入口を指定する種類は出口番号が必須
            if (g.maneuverType == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW) setRoundaboutExitNumber(g.roundaboutExit ?: 1)
        }.build()
        val step = Step.Builder(g.cue.ifEmpty { " " }).setManeuver(maneuver).build()
        val stepEstimate = TravelEstimate.Builder(distance(g.stepMeters ?: 0.0), arrival).build()

        val tripEstimate = TravelEstimate.Builder(distance(g.remainingMeters ?: 0.0), arrival).apply {
            g.remainingSeconds?.let { setRemainingTimeSeconds(it) }
        }.build()
        return Trip.Builder()
            .addStep(step, stepEstimate)
            .addDestination(Destination.Builder().setName(carContext.getString(R.string.hud_destination)).build(), tripEstimate)
            .build()
    }

    /** 車の表示に合わせて単位を選ぶ (1 km 未満は m、10 km 未満は小数 1 桁の km) */
    private fun distance(meters: Double): Distance = when {
        meters < 1000 -> Distance.create(meters, Distance.UNIT_METERS)
        meters < 10_000 -> Distance.create(meters / 1000, Distance.UNIT_KILOMETERS_P1)
        else -> Distance.create(meters / 1000, Distance.UNIT_KILOMETERS)
    }
}
