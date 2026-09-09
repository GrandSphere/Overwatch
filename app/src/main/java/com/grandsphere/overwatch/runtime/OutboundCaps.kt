package com.grandsphere.overwatch.runtime

import android.content.Context
import com.grandsphere.overwatch.OverwatchApp

/** Tracks outbound SMS/call counts for the current Alarm (and optional Safety) run. */
object OutboundCaps {
    @Volatile
    private var smsSent = 0

    @Volatile
    private var callsMade = 0

    @Volatile
    private var smsCappedLogged = false

    @Volatile
    private var callsCappedLogged = false

    fun reset() {
        synchronized(this) {
            smsSent = 0
            callsMade = 0
            smsCappedLogged = false
            callsCappedLogged = false
        }
    }

    fun tryConsumeSms(context: Context, onCapped: (() -> Unit)? = null): Boolean {
        val max = OverwatchApp.from(context).latestSettings.maxAlarmSms.coerceAtLeast(0)
        synchronized(this) {
            if (smsSent >= max) {
                if (!smsCappedLogged) {
                    smsCappedLogged = true
                    onCapped?.invoke()
                }
                return false
            }
            smsSent += 1
            return true
        }
    }

    fun tryConsumeCall(context: Context, onCapped: (() -> Unit)? = null): Boolean {
        val max = OverwatchApp.from(context).latestSettings.maxAlarmCalls.coerceAtLeast(0)
        synchronized(this) {
            if (callsMade >= max) {
                if (!callsCappedLogged) {
                    callsCappedLogged = true
                    onCapped?.invoke()
                }
                return false
            }
            callsMade += 1
            return true
        }
    }
}
