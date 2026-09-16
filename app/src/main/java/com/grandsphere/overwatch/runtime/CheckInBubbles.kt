package com.grandsphere.overwatch.runtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.R
import com.grandsphere.overwatch.domain.model.AppState
import com.grandsphere.overwatch.domain.model.Submode

/**
 * Collapsed check-in bubble for a running Overwatch. Never auto-expands.
 * Stays until the user trashes it, then respawns after min(60s, interval/3).
 */
object CheckInBubbles {
    const val ACTION_TRASHED = "com.grandsphere.overwatch.ACTION_BUBBLE_TRASHED"
    const val ID = 49
    const val CHANNEL = "overwatch_checkin_bubble"
    const val CHANNEL_COVERT = "overwatch_checkin_bubble_quiet"
    private const val SHORTCUT_ID = "overwatch-check-in"
    private const val PERSON_KEY = "overwatch-check-in-person"

    @Volatile
    private var posted = false

    @Volatile
    private var respawnAtElapsedMs: Long? = null

    fun areAllowed(nm: NotificationManager): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        if (!nm.areBubblesAllowed()) return false
        if (Build.VERSION.SDK_INT >= 31) {
            return nm.bubblePreference != NotificationManager.BUBBLE_PREFERENCE_NONE
        }
        return true
    }

    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 29) return
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                VerboseLog.fail("Bubble", "open settings", it)
            }
    }

    fun promptIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < 29) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!areAllowed(nm)) openSettings(context)
    }

    fun shouldShow(state: AppState): Boolean {
        val running = state as? AppState.Overwatch ?: return false
        if (running.secretAlarm || running.notificationHidden) return false
        if (running.submode == Submode.AlarmMode) return false
        return "bubble" in running.config.notifyEffectIds
    }

    fun sync(context: Context, state: AppState) {
        if (!shouldShow(state)) {
            cancel(context)
            return
        }
        val running = state as AppState.Overwatch
        if (posted) return
        val due = respawnAtElapsedMs
        if (due != null && SystemClock.elapsedRealtime() < due) return
        post(context, running)
    }

    fun onTrashed(context: Context) {
        VerboseLog.d("Bubble", "trashed")
        posted = false
        val state = OverwatchApp.from(context).engine.state.value
        if (!shouldShow(state)) {
            respawnAtElapsedMs = null
            return
        }
        val running = state as AppState.Overwatch
        val period = minOf(60_000L, running.intervalMs / 3L).coerceAtLeast(1_000L)
        respawnAtElapsedMs = SystemClock.elapsedRealtime() + period
        VerboseLog.d("Bubble", "respawn in ${period}ms")
    }

    fun cancel(context: Context) {
        posted = false
        respawnAtElapsedMs = null
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    private fun post(context: Context, running: AppState.Overwatch) {
        val covert = running.config.covert
        ensureChannel(context)
        publishShortcut(context, covert)
        val app = context.applicationContext
        val title = if (covert) "Timer" else "Overwatch"
        val text = if (covert) {
            "Active"
        } else {
            running.config.notifyNotificationBody.trim().ifEmpty { "Remember to check in" }
        }
        val open = PendingIntent.getActivity(
            app,
            20,
            Intent(app, CuePopupActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            pendingFlags(mutable = false),
        )
        val trash = PendingIntent.getBroadcast(
            app,
            21,
            Intent(app, NotificationActionReceiver::class.java).setAction(ACTION_TRASHED),
            pendingFlags(mutable = false),
        )
        val person = person(covert)
        val style = NotificationCompat.MessagingStyle(person)
            .setConversationTitle(title)
            .addMessage(text, System.currentTimeMillis(), person)
        val channel = if (covert) CHANNEL_COVERT else CHANNEL
        val builder = NotificationCompat.Builder(app, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId(SHORTCUT_ID)
            .setLocusId(LocusIdCompat(SHORTCUT_ID))
            .setContentIntent(open)
            .setDeleteIntent(trash)
            .setPriority(
                if (covert) {
                    NotificationCompat.PRIORITY_MIN
                } else {
                    running.config.notifyNotificationUrgency.toCompatPriority()
                },
            )
        if (Build.VERSION.SDK_INT >= 29) {
            val bubbleIntent = PendingIntent.getActivity(
                app,
                22,
                Intent(app, CuePopupActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                pendingFlags(mutable = true),
            )
            val bubble = NotificationCompat.BubbleMetadata.Builder(
                bubbleIntent,
                IconCompat.createWithResource(app, R.drawable.ic_launcher_foreground),
            )
                .setDesiredHeight(640)
                .setAutoExpandBubble(false)
                .setSuppressNotification(false)
                .setDeleteIntent(trash)
                .build()
            builder.setBubbleMetadata(bubble)
        }
        app.getSystemService(NotificationManager::class.java).notify(ID, builder.build())
        posted = true
        respawnAtElapsedMs = null
        VerboseLog.ok("Bubble", "posted")
    }

    private fun publishShortcut(context: Context, covert: Boolean) {
        val icon = IconCompat.createWithResource(context, R.drawable.ic_launcher_foreground)
        val intent = Intent(context, CuePopupActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
        val label = if (covert) "Timer" else "Check in"
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(label)
            .setLongLabel(if (covert) "Timer" else "Overwatch check in")
            .setIcon(icon)
            .setIntent(intent)
            .setPerson(person(covert))
            .setLongLived(true)
            .setLocusId(LocusIdCompat(SHORTCUT_ID))
            .setCategories(setOf("android.shortcut.conversation"))
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
            .onFailure { VerboseLog.fail("Bubble", "shortcut", it) }
    }

    private fun person(covert: Boolean): Person = Person.Builder()
        .setName(if (covert) "Timer" else "Overwatch")
        .setKey(PERSON_KEY)
        .setImportant(true)
        .build()

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Check-in bubble", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Floating check-in while an Overwatch is running"
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_COVERT,
                "Timer bubble",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Quiet floating timer"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun pendingFlags(mutable: Boolean): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        flags = if (Build.VERSION.SDK_INT >= 31) {
            flags or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        } else {
            flags
        }
        return flags
    }

    private fun com.grandsphere.overwatch.domain.model.NotificationUrgency.toCompatPriority(): Int = when (this) {
        com.grandsphere.overwatch.domain.model.NotificationUrgency.LOW -> NotificationCompat.PRIORITY_LOW
        com.grandsphere.overwatch.domain.model.NotificationUrgency.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
        com.grandsphere.overwatch.domain.model.NotificationUrgency.HIGH -> NotificationCompat.PRIORITY_HIGH
    }
}
