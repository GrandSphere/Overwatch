package com.grandsphere.overwatch.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.grandsphere.overwatch.MainActivity
import com.grandsphere.overwatch.R
import com.grandsphere.overwatch.domain.model.AppState
import com.grandsphere.overwatch.domain.model.Submode

/**
 * Grace countdown shade item only. Notify countdown lives on the FGS notification
 * when [live_notify] is selected (see OverwatchNotifications).
 */
object LiveCountdownNotifications {
    const val CHANNEL = "overwatch_live"
    const val CHANNEL_QUIET = "overwatch_live_quiet"
    const val LIVE_ID = 43
    const val GRACE_ID = 44
    private const val PROGRESS_MAX = 10_000
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Live countdown", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_QUIET, "Live countdown (quiet)", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    fun sync(context: Context, state: AppState) {
        val nm = context.getSystemService(NotificationManager::class.java)
        ensureChannel(context)
        nm.cancel(LIVE_ID)
        if (state !is AppState.Overwatch || state.config.covert || state.notificationHidden) {
            nm.cancel(GRACE_ID)
            return
        }
        val grace = "grace_notification" in state.config.graceNotifyEffectIds &&
            state.submode == Submode.GraceMode
        if (grace) {
            val graceLeft = (state.graceDeadlineElapsedMs ?: 0L) - android.os.SystemClock.elapsedRealtime()
            val graceTotal = state.config.graceDurationMs.coerceAtLeast(1L)
            val secs = (graceLeft / 1000).coerceAtLeast(0)
            nm.notify(
                GRACE_ID,
                build(
                    context,
                    title = "Grace",
                    text = "you have $secs seconds left to respond",
                    chip = "${secs}s",
                    progress = (PROGRESS_MAX * graceLeft.coerceAtLeast(0) / graceTotal).toInt()
                        .coerceIn(0, PROGRESS_MAX),
                ),
            )
        } else {
            nm.cancel(GRACE_ID)
        }
    }

    fun cancelAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(LIVE_ID)
        nm.cancel(GRACE_ID)
    }

    private fun build(
        context: Context,
        title: String,
        text: String,
        chip: String,
        progress: Int,
    ): Notification {
        val open = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val channel = channelId(context)
        if (Build.VERSION.SDK_INT >= 36) {
            val style = Notification.ProgressStyle()
                .setStyledByProgress(true)
                .setProgress(progress)
                .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_launcher_foreground))
                .addProgressSegment(Notification.ProgressStyle.Segment(PROGRESS_MAX))
            val builder = Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .setStyle(style)
                .setShortCriticalText(chip)
            requestPromotedOngoing(builder)
            return builder.build()
        }
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setProgress(PROGRESS_MAX, progress, false)
            .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
            .build()
    }

    private fun channelId(context: Context): String {
        val toasts = runCatching { com.grandsphere.overwatch.OverwatchApp.from(context).latestSettings.notifyToasts }
            .getOrDefault(true)
        return if (toasts) CHANNEL else CHANNEL_QUIET
    }

    private fun requestPromotedOngoing(builder: Notification.Builder) {
        val invoked = runCatching {
            builder.javaClass
                .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
            true
        }.getOrDefault(false)
        if (!invoked) {
            builder.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
        }
    }
}
