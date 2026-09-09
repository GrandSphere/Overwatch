package com.grandsphere.overwatch.runtime

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.data.OverwatchRepository
import com.grandsphere.overwatch.domain.model.AppSettings
import com.grandsphere.overwatch.domain.model.AppState
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object LocationSender {
    private const val TAG = "LocationSender"

    fun send(
        context: Context,
        repository: OverwatchRepository,
        config: OverwatchConfig,
        scope: CoroutineScope,
        settings: AppSettings,
        sendSms: Boolean,
        writeLog: Boolean,
        includeAlarmMessage: Boolean,
        includeRecentTrail: Boolean = false,
        alarmBodyOverride: String? = null,
        includeTriggerDiagnostics: Boolean = true,
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                if (AccessibilityFeatures.ADVERTISED &&
                    !isLocationEnabled(context) &&
                    settings.turnLocationOn &&
                    OverwatchAccessibilityService.isEnabled(context)
                ) {
                    withContext(Dispatchers.Main) {
                        OverwatchAccessibilityService.requestEnableLocation()
                    }
                    delay(2_500)
                }
                val maxAge = LocationTrail.staleAfterMs(context)
                if (!LocationTrail.isFresh(maxAge)) {
                    LocationTrail.poke(context)
                    var waited = 0
                    while (waited < 8_000 && !LocationTrail.isFresh(maxAge)) {
                        delay(250)
                        waited += 250
                    }
                    VerboseLog.d("Location", "waited ${waited}ms fresh=${LocationTrail.isFresh(maxAge)}")
                }
                val trail = if (includeRecentTrail) LocationTrail.snapshot() else emptyList()
                val loc = LocationTrail.latest()?.takeIf {
                    System.currentTimeMillis() - it.atEpochMs <= maxAge
                }
                if (loc != null) {
                    VerboseLog.ok(
                        "Location",
                        "fresh ${loc.latitude},${loc.longitude} age=${System.currentTimeMillis() - loc.atEpochMs}ms provider=${loc.provider}",
                    )
                } else {
                    VerboseLog.fail(
                        "Location",
                        "empty or stale trail=${trail.size} maxAgeMs=$maxAge latest=${LocationTrail.latest()?.provider}",
                    )
                }
                val packed = if (includeRecentTrail) {
                    val withTa = if (loc != null) {
                        trail + loc
                    } else {
                        trail
                    }
                    LocationTrail.packForSms(withTa, settings.recentLocationPoints)
                } else if (loc != null) {
                    listOf(loc)
                } else {
                    emptyList()
                }
                if (packed.isNotEmpty()) {
                    val last = packed.last()
                    if (LocationTrail.matchesLastSent(last.latitude, last.longitude)) {
                        VerboseLog.d("Location", "location matches last")
                        if (writeLog) {
                            EventLog.append(
                                context,
                                repository,
                                scope,
                                config,
                                "location matches last",
                            )
                        }
                        return@launch
                    }
                }
                val links = LocationTrail.formatTrail(packed)
                val running = OverwatchApp.from(context).engine.state.value as? AppState.Overwatch
                val smsBody = if (includeAlarmMessage || links.isNotEmpty()) {
                    AlarmPayload.fromRunning(
                        running = running,
                        config = config,
                        includeMessage = includeAlarmMessage,
                        includeDiagnostics = includeTriggerDiagnostics && config.sendTriggerMode,
                        locationLines = links,
                        messageOverride = alarmBodyOverride,
                    )
                } else {
                    ""
                }
                val logBody = if (includeAlarmMessage || links.isNotEmpty()) {
                    AlarmPayload.fromRunning(
                        running = running,
                        config = config,
                        includeMessage = includeAlarmMessage,
                        includeDiagnostics = includeTriggerDiagnostics,
                        locationLines = links,
                        messageOverride = alarmBodyOverride,
                    )
                } else {
                    ""
                }
                VerboseLog.d(
                    "Location",
                    "payload sms=$sendSms log=$writeLog links=${packed.size} " +
                        "smsEmpty=${smsBody.isEmpty()} logEmpty=${logBody.isEmpty()}",
                )
                if (writeLog) {
                    if (logBody.isNotEmpty()) {
                        EventLog.append(context, repository, scope, config, logBody)
                    } else {
                        EventLog.append(
                            context,
                            repository,
                            scope,
                            config,
                            "Location skipped: no fresh location",
                        )
                    }
                }
                if (sendSms && smsBody.isNotEmpty()) sendSms(context, config, smsBody)
                if (packed.isNotEmpty() && (sendSms || writeLog) &&
                    (smsBody.isNotEmpty() || logBody.isNotEmpty())
                ) {
                    val last = packed.last()
                    LocationTrail.rememberSent(last.latitude, last.longitude)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Send location failed", t)
                VerboseLog.fail("Location", "send", t)
            }
        }
    }

    fun sendUpdate(
        context: Context,
        repository: OverwatchRepository,
        config: OverwatchConfig,
        scope: CoroutineScope,
        sendSms: Boolean,
        writeLog: Boolean,
    ) {
        val maxAge = LocationTrail.staleAfterMs(context)
        val loc = LocationTrail.latest()
        val age = loc?.let { System.currentTimeMillis() - it.atEpochMs }
        if (loc == null || age == null || age > maxAge) {
            VerboseLog.fail("Location", "continuous skip empty/stale age=$age maxAgeMs=$maxAge provider=${loc?.provider}")
            if (writeLog) {
                EventLog.append(
                    context,
                    repository,
                    scope,
                    config,
                    "Location skipped: no fresh location",
                )
            }
            return
        }
        if (LocationTrail.matchesLastSent(loc.latitude, loc.longitude)) {
            VerboseLog.d("Location", "location matches last")
            if (writeLog) {
                EventLog.append(
                    context,
                    repository,
                    scope,
                    config,
                    "location matches last",
                )
            }
            return
        }
        val line = LocationTrail.formatPointLine(loc)
        val body = "Overwatch location update:\n$line"
        VerboseLog.ok("Location", "continuous store/check/send ${loc.latitude},${loc.longitude} age=${age}ms provider=${loc.provider}")
        if (writeLog) {
            EventLog.append(context, repository, scope, config, body)
        }
        if (sendSms) sendSms(context, config, body)
        if (sendSms || writeLog) {
            LocationTrail.rememberSent(loc.latitude, loc.longitude)
        }
    }

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun obtainFix(context: Context): Location? {
        val stored = LocationTrail.latest()
        if (stored != null && System.currentTimeMillis() - stored.atEpochMs < 120_000L) {
            return Location("overwatch").apply {
                latitude = stored.latitude
                longitude = stored.longitude
                time = stored.atEpochMs
            }
        }
        val pm = context.getSystemService(PowerManager::class.java)
        val wake = runCatching {
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "overwatch:loc").apply {
                setReferenceCounted(false)
                acquire(15_000L)
            }
        }.getOrNull()
        return try {
            obtainFixInner(context)
        } finally {
            if (wake?.isHeld == true) runCatching { wake.release() }
        }
    }

    private fun obtainFixInner(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            VerboseLog.fail("Location", "no ACCESS_FINE_LOCATION")
            return null
        }
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val last = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            if (!lm.isProviderEnabled(provider) && provider != LocationManager.PASSIVE_PROVIDER) null
            else runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 120_000L) return last

        val providers = buildList {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }
        if (providers.isEmpty()) return last
        val latch = CountDownLatch(1)
        var result: Location? = last
        val thread = HandlerThread("ow-loc").apply { start() }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                result = location
                latch.countDown()
            }
        }
        Handler(thread.looper).post {
            var started = false
            for (provider in providers) {
                runCatching {
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, thread.looper)
                    started = true
                }
            }
            if (!started) latch.countDown()
        }
        latch.await(8, TimeUnit.SECONDS)
        runCatching { lm.removeUpdates(listener) }
        thread.quitSafely()
        return result
    }

    private fun sendSms(
        context: Context,
        config: OverwatchConfig,
        body: String,
    ) {
        val numbers = config.smsContacts()
        if (numbers.isEmpty()) {
            VerboseLog.fail("Location", "SMS skipped no contacts")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            VerboseLog.fail("Location", "SMS skipped no SEND_SMS permission")
            return
        }
        val sms = context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        for (n in numbers) {
            if (!OutboundCaps.tryConsumeSms(context) {
                    VerboseLog.fail("Location", "SMS capped")
                }
            ) {
                break
            }
            runCatching {
                val parts = sms.divideMessage(body)
                if (parts.size <= 1) sms.sendTextMessage(n, null, body, null, null)
                else sms.sendMultipartTextMessage(n, null, parts, null, null)
            }.onSuccess {
                VerboseLog.ok("Location", "SMS contact ${numbers.indexOf(n) + 1}/${numbers.size}")
            }.onFailure {
                VerboseLog.fail("Location", "SMS send", it)
            }
        }
    }
}
