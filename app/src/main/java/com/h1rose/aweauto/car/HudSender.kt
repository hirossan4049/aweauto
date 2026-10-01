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
import com.h1rose.aweauto.data.Prefs
import com.h1rose.aweauto.hud.Hud
import com.h1rose.aweauto.hud.HudState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

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
            combine(Hud.state, Prefs.hud) { state, on -> state.takeIf { on } }
                .distinctUntilChanged()
                .collect { send(it) }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        scope.cancel()
        end()
        nav.clearNavigationManagerCallback()
    }

    private fun send(state: HudState?) {
        if (state == null) {
            stoppedByCar = false
            end()
            return
        }
        if (stoppedByCar) return
        runCatching {
            if (!navigating) {
                nav.navigationStarted()
                navigating = true
                Log.i(TAG, "navigation started (${state.packageName})")
            }
            nav.updateTrip(trip(state))
        }.onFailure {
            Log.w(TAG, "updateTrip failed", it)
        }
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
            .addDestination(Destination.Builder().setName("目的地").build(), tripEstimate)
            .build()
    }

    /** 車の表示に合わせて単位を選ぶ (1 km 未満は m、10 km 未満は小数 1 桁の km) */
    private fun distance(meters: Double): Distance = when {
        meters < 1000 -> Distance.create(meters, Distance.UNIT_METERS)
        meters < 10_000 -> Distance.create(meters / 1000, Distance.UNIT_KILOMETERS_P1)
        else -> Distance.create(meters / 1000, Distance.UNIT_KILOMETERS)
    }
}
