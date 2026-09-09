package com.grandsphere.overwatch.runtime

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import com.grandsphere.overwatch.domain.model.AppState
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import com.grandsphere.overwatch.domain.model.Submode
import java.util.Calendar

/**
 * Best-effort handoff to the system Clock app. No AccessibilityService.
 * SET is public; dismiss is not reliable across OEMs.
 * Clock only accepts hour+minute; we round up to the next minute.
 */
object ClockAlarm {
    private const val LABEL = "Overwatch"

    @Volatile
    private var armed = false

    fun sync(context: Context, state: AppState) {
        when (state) {
            AppState.Wading -> dismiss(context)
            is AppState.Overwatch -> when (state.submode) {
                Submode.NotifyMode -> scheduleNotify(context, state.config, state.remainingMs)
                Submode.AlarmMode -> scheduleAlarmMode(context, state.config)
                Submode.DismissMode, Submode.GraceMode -> Unit
            }
        }
    }

    fun scheduleNotify(context: Context, config: OverwatchConfig, remainingMs: Long) {
        if (config.covert || "clock_alarm" !in config.notifyEffectIds) return
        setIn(context, remainingMs, LABEL)
    }

    fun scheduleAlarmMode(context: Context, config: OverwatchConfig) {
        if (config.covert || "clock_alarm" !in config.alarmEffectIds) return
        setIn(context, 60_000L, LABEL)
    }

    fun dismiss(context: Context) {
        if (!armed) return
        armed = false
        if (Build.VERSION.SDK_INT < 23) return
        val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
            putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_LABEL)
            putExtra(AlarmClock.EXTRA_MESSAGE, LABEL)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            runCatching { context.startActivity(intent) }
                .onSuccess { VerboseLog.ok("Clock", "dismiss") }
                .onFailure { VerboseLog.fail("Clock", "dismiss", it) }
        } else {
            VerboseLog.fail("Clock", "dismiss no activity")
        }
    }

    private fun setIn(context: Context, remainingMs: Long, message: String) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis() + remainingMs.coerceAtLeast(0L)
            if (get(Calendar.SECOND) > 0 || get(Calendar.MILLISECOND) > 0) {
                add(Calendar.MINUTE, 1)
            }
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, cal.get(Calendar.HOUR_OF_DAY))
            putExtra(AlarmClock.EXTRA_MINUTES, cal.get(Calendar.MINUTE))
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_VIBRATE, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            runCatching { context.startActivity(intent) }
                .onSuccess {
                    armed = true
                    VerboseLog.ok("Clock", "set ${cal.get(Calendar.HOUR_OF_DAY)}:${cal.get(Calendar.MINUTE)}")
                }
                .onFailure { VerboseLog.fail("Clock", "set", it) }
        } else {
            VerboseLog.fail("Clock", "set no activity")
        }
    }
}
