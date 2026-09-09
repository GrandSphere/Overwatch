package com.grandsphere.overwatch.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.domain.catalog.FeaturePermissions
import com.grandsphere.overwatch.domain.model.AppState
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = OverwatchApp.from(context)
        val pending = goAsync()
        app.scope.launch {
            try {
                when (intent.action) {
                    OverwatchNotifications.ACTION_PANIC -> {
                        VerboseLog.d("Notify", "ACTION_PANIC")
                        val idle = app.repository.defaultConfig()
                            ?: app.engine.configs.firstOrNull { it.isDefault() }
                            ?: app.engine.configs.firstOrNull()
                            ?: app.repository.list().firstOrNull { it.isDefault() }
                            ?: app.repository.list().firstOrNull()
                        app.engine.panic(idle)
                    }
                    OverwatchNotifications.ACTION_HIDE -> {
                        VerboseLog.d("Notify", "ACTION_HIDE")
                        app.engine.hideNotification()
                    }
                    OverwatchNotifications.ACTION_DISMISS -> {
                        VerboseLog.d("Notify", "ACTION_DISMISS")
                        app.engine.tapDismiss()
                    }
                    OverwatchNotifications.ACTION_ENABLE -> {
                        val id = intent.getLongExtra(OverwatchNotifications.EXTRA_CONFIG_ID, -1L)
                        VerboseLog.d("Notify", "ACTION_ENABLE id=$id")
                        if (id < 0L) return@launch
                        val configs = app.engine.configs.ifEmpty { app.repository.list() }
                        if (app.engine.configs.isEmpty()) app.engine.configs = configs
                        val config = configs.find { it.id == id } ?: return@launch
                        if (FeaturePermissions.needsLocation(config) &&
                            !LocationSender.isLocationEnabled(app)
                        ) {
                            VerboseLog.d("Notify", "location services off, opening settings")
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                        if (!app.engine.enable(config)) {
                            val running = app.engine.state.value as? AppState.Overwatch ?: return@launch
                            OverwatchNotifications.announceCurrentlyRunning(app, running)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
