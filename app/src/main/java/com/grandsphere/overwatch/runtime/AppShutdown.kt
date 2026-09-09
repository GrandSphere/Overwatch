package com.grandsphere.overwatch.runtime

import android.content.Context
import android.content.Intent
import com.grandsphere.overwatch.MainActivity
import com.grandsphere.overwatch.OverwatchApp

object AppShutdown {
    fun hardStop(context: Context, finishAffinity: Boolean) {
        val app = OverwatchApp.from(context)
        AlarmCapScheduler.cancel(context)
        ContinuousLocationScheduler.cancel(context)
        DeadlineScheduler.cancel(context)
        ClockAlarm.dismiss(context)
        app.dispatcher.halt()
        OverwatchService.stop(context)
        VerboseLog.d("Shutdown", "hardStop finishAffinity=$finishAffinity")
        app.engine.forceWadingHardQuit()
        if (!finishAffinity) return
        if (MainActivity.finishAffinityIfPresent()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(MainActivity.EXTRA_FINISH, true)
        }
        context.startActivity(intent)
    }
}
