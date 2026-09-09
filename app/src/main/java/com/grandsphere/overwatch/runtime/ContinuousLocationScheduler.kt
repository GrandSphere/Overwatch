package com.grandsphere.overwatch.runtime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.grandsphere.overwatch.OverwatchApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ContinuousLocationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        ContinuousLocationScheduler.onFired(context) {
            pending.finish()
        }
    }
}

object ContinuousLocationScheduler {
    private const val REQ = 7003
    private const val FETCH_WAKE_MS = 25_000L

    @Volatile
    private var session: Session? = null

    data class Session(
        val configId: Long,
        val sendSms: Boolean,
        val writeLog: Boolean,
        val minutes: Int,
    )

    fun start(context: Context, configId: Long, sendSms: Boolean, writeLog: Boolean) {
        val minutes = OverwatchApp.from(context).latestSettings.continuousLocationMinutes.coerceAtLeast(1)
        session = Session(configId, sendSms, writeLog, minutes)
        VerboseLog.ok("Location", "continuous schedule minutes=$minutes sms=$sendSms log=$writeLog")
        scheduleNext(context, minutes)
    }

    fun cancel(context: Context) {
        session = null
        VerboseLog.d("Location", "continuous cancel")
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }

    fun onFired(context: Context, done: () -> Unit = {}) {
        val current = session
        if (current == null) {
            done()
            return
        }
        VerboseLog.d("Location", "continuous due")
        scheduleNext(context, current.minutes)
        val app = OverwatchApp.from(context)
        val config = app.engine.configById(current.configId) ?: run {
            VerboseLog.fail("Location", "continuous no config")
            done()
            return
        }
        val appContext = context.applicationContext
        app.scope.launch(Dispatchers.IO) {
            val pm = appContext.getSystemService(PowerManager::class.java)
            val wake = runCatching {
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "overwatch:loc-tick").apply {
                    setReferenceCounted(false)
                    acquire(FETCH_WAKE_MS)
                }
            }.getOrNull()
            if (wake?.isHeld == true) VerboseLog.ok("Location", "tick wake lock")
            else VerboseLog.fail("Location", "tick wake lock")
            try {
                LocationTrail.ensureHot(appContext)
                val maxAge = LocationTrail.staleAfterMs(appContext)
                val fresh = LocationTrail.awaitFresh(appContext, maxAge)
                VerboseLog.d("Location", "continuous fetch fresh=$fresh maxAgeMs=$maxAge")
                if (session == null) {
                    VerboseLog.d("Location", "continuous skipped session ended")
                    return@launch
                }
                if (!fresh) {
                    // GPS did not deliver a new fix (device locked / hardware throttled).
                    // Do not send stale cached coordinates — log and wait for the next tick.
                    VerboseLog.fail("Location", "continuous skip: no new fix (locked/throttled)")
                    if (current.writeLog) {
                        EventLog.append(
                            appContext,
                            app.repository,
                            app.scope,
                            config,
                            "Location skipped: GPS not updating (device may be locked)",
                        )
                    }
                    return@launch
                }
                LocationSender.sendUpdate(
                    appContext,
                    app.repository,
                    config,
                    app.scope,
                    sendSms = current.sendSms,
                    writeLog = current.writeLog,
                )
            } catch (t: Throwable) {
                VerboseLog.fail("Location", "continuous fetch", t)
            } finally {
                if (wake?.isHeld == true) runCatching { wake.release() }
                done()
            }
        }
    }

    private fun scheduleNext(context: Context, minutes: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context)
        val trigger = SystemClock.elapsedRealtime() + minutes.coerceAtLeast(1) * 60_000L
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
            VerboseLog.d("Location", "continuous inexact minutes=$minutes")
            return
        }
        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        VerboseLog.ok("Location", "continuous next minutes=$minutes")
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, ContinuousLocationReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
