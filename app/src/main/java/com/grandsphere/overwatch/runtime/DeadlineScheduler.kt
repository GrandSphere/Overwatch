package com.grandsphere.overwatch.runtime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.grandsphere.overwatch.OverwatchApp

class DeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        VerboseLog.d("Deadline", "fired")
        OverwatchApp.from(context).engine.onDeadline()
        val app = OverwatchApp.from(context)
        app.engine.nextDeadlineElapsed()?.let { DeadlineScheduler.schedule(context, it) }
    }
}

object DeadlineScheduler {
    private const val REQ = 7001

    fun schedule(context: Context, elapsedRealtime: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context)
        val trigger = elapsedRealtime.coerceAtLeast(SystemClock.elapsedRealtime() + 500)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
            VerboseLog.d("Deadline", "schedule inexact trigger=$trigger")
            return
        }
        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        VerboseLog.ok("Deadline", "schedule exact trigger=$trigger")
    }

    fun cancel(context: Context) {
        VerboseLog.d("Deadline", "cancel")
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, DeadlineReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
