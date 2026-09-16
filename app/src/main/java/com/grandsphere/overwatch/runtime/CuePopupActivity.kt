package com.grandsphere.overwatch.runtime

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.grandsphere.overwatch.MainActivitySettings
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.domain.model.AppState
import com.grandsphere.overwatch.domain.model.Submode
import com.grandsphere.overwatch.ui.running.RunningScreen
import com.grandsphere.overwatch.ui.theme.OverwatchTheme

class CuePopupActivity : FragmentActivity() {
    private val app get() = application as OverwatchApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        setContent {
            val state by app.engine.state.collectAsState()
            val running = state as? AppState.Overwatch
            LaunchedEffect(running?.submode, running == null) {
                if (running == null || running.submode == Submode.AlarmMode || running.secretAlarm) {
                    finish()
                }
            }
            val settings = app.latestSettings
            OverwatchTheme(
                light = settings.lightTheme,
                cardArgb = settings.darkCardArgb,
                actionArgb = settings.actionArgb,
            ) {
                if (running == null) {
                    Box(Modifier.fillMaxSize())
                    return@OverwatchTheme
                }
                RunningScreen(
                    state = running,
                    onDigit = { app.engine.submitPinDigit(it) },
                    onBackspace = { app.engine.pinBackspace() },
                    onClear = { app.engine.pinClear() },
                    onTap = { app.engine.tapDismiss() },
                    onFingerprint = { app.engine.fingerprintSuccess() },
                    onConfirmCancel = { app.engine.cancelConfirm() },
                    onCancelWithPin = { app.engine.cancelWithPin(it) },
                    onCancelWithTap = { app.engine.cancelWithTap() },
                    onCancelWithFingerprint = { app.engine.cancelWithFingerprint() },
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handled = app.hardware.onKeyDown(keyCode, event, MainActivitySettings.latest)
        return handled || super.onKeyDown(keyCode, event)
    }
}
