package com.grandsphere.overwatch.runtime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.grandsphere.overwatch.OverwatchApp

class AlarmCapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        VerboseLog.d("AlarmCap", "fired")
        OverwatchApp.from(context).engine.onAlarmMaxDuration()
    }
}

object AlarmCapScheduler {
    private const val REQ = 7002

    fun schedule(context: Context, minutes: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context)
        val trigger = SystemClock.elapsedRealtime() +
            minutes.coerceAtLeast(1) * 60_000L
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
            VerboseLog.d("AlarmCap", "schedule inexact minutes=$minutes")
            return
        }
        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        VerboseLog.ok("AlarmCap", "schedule exact minutes=$minutes")
    }

    fun cancel(context: Context) {
        VerboseLog.d("AlarmCap", "cancel")
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, AlarmCapReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
