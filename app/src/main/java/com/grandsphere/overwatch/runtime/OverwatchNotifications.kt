package com.grandsphere.overwatch.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.grandsphere.overwatch.MainActivity
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.R
import com.grandsphere.overwatch.domain.model.AppState
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import com.grandsphere.overwatch.domain.model.Submode

object OverwatchNotifications {
    const val CHANNEL = "overwatch_loop"
    const val CHANNEL_COVERT = "overwatch_timer"
    const val CHANNEL_STATUS = "overwatch_status"
    const val ID = 42
    const val BUSY_ID = 45
    const val ACTION_PANIC = "com.grandsphere.overwatch.ACTION_PANIC"
    const val ACTION_HIDE = "com.grandsphere.overwatch.ACTION_HIDE"
    const val ACTION_DISMISS = "com.grandsphere.overwatch.ACTION_DISMISS"
    const val ACTION_ENABLE = "com.grandsphere.overwatch.ACTION_ENABLE"
    const val EXTRA_CONFIG_ID = "config_id"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.fgs_channel), NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_COVERT,
                context.getString(R.string.fgs_covert_channel),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.status_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun announceCurrentlyRunning(context: Context, running: AppState.Overwatch) {
        if (running.config.covert || running.secretAlarm || running.notificationHidden) return
        ensureChannels(context)
        val text = "Currently in ${running.config.name}"
        val open = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Overwatch")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(BUSY_ID, notification)
        if (OverwatchApp.from(context).latestSettings.notifyToasts) {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }

    fun build(context: Context, state: AppState.Overwatch): Notification {
        val opaque = state.config.covert || state.secretAlarm || state.notificationHidden
        val channel = if (opaque) CHANNEL_COVERT else CHANNEL
        val liveCountdown = !opaque &&
            "live_notify" in state.config.notifyEffectIds &&
            state.submode != Submode.AlarmMode
        val title = when {
            opaque -> "Timer"
            liveCountdown && state.config.liveNotifyShowName -> state.config.name
            else -> "Overwatch"
        }
        val text = when {
            opaque -> "Active"
            state.submode == Submode.AlarmMode -> "Alerting..."
            liveCountdown -> formatMmSs(state.remainingMs)
            else -> "Running"
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val panic = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_PANIC),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val hide = PendingIntent.getBroadcast(
            context,
            4,
            Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (!opaque) {
            builder.addAction(0, "Panic", panic)
            builder.addAction(0, "Hide", hide)
        }
        return builder.build()
    }

    fun placeholder(context: Context, config: OverwatchConfig): Notification {
        ensureChannels(context)
        val channel = if (config.covert) CHANNEL_COVERT else CHANNEL
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (config.covert) "Timer" else "Overwatch")
            .setContentText(if (config.covert) "Active" else "Running")
            .setOngoing(true)
            .build()
    }

    fun formatMmSs(ms: Long): String {
        val sign = if (ms < 0) "-" else ""
        val total = kotlin.math.abs(ms) / 1000
        val m = total / 60
        val s = total % 60
        return "%s%d:%02d".format(sign, m, s)
    }
}
