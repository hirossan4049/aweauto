package com.h1rose.aweauto.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import com.h1rose.aweauto.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.h1rose.aweauto.ui.AweNav
import com.h1rose.aweauto.ui.onCarScreenVisibilityChanged

class AweCarAppService : CarAppService() {
    // サイドロード前提の個人用アプリなので接続元の検証はしない
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = SurfaceScreen(carContext)
    }
}

/**
 * 画面の中身はすべて Surface 側に描くので、テンプレートは最低限のボタンだけ。
 * NavigationTemplate はボタンが最低 1 個必須なので、邪魔にならないようアイコンだけの「戻る」を置く
 * (画面に触れると表示され、数秒で自動的に隠れる)。
 */
private class SurfaceScreen(carContext: CarContext) : Screen(carContext) {
    init {
        SurfaceRenderer(carContext, lifecycle)
        // 車側の割り込み (バックカメラなど) で画面が隠れると ON_STOP、戻ると ON_START が来る
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> onCarScreenVisibilityChanged(false)
                    Lifecycle.Event.ON_START -> onCarScreenVisibilityChanged(true)
                    else -> Unit
                }
            }
        )
    }

    override fun onGetTemplate(): Template {
        val icon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_back)).build()
        val back = Action.Builder()
            .setIcon(icon)
            .setOnClickListener { AweNav.back() }
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(back).build())
            .build()
    }
}
