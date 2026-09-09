package com.grandsphere.overwatch.runtime

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.R
import com.grandsphere.overwatch.data.OverwatchRepository
import com.grandsphere.overwatch.domain.catalog.AlarmCatalog
import com.grandsphere.overwatch.domain.model.AppState
import com.grandsphere.overwatch.domain.model.NotificationUrgency
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmDispatcher(
    private val context: Context,
    private val repository: OverwatchRepository,
    private val cuePlayer: CuePlayer,
    private val scope: CoroutineScope,
) {
    data class DispatchContext(
        val ids: Set<String>,
        val smsBody: String,
        val notificationBody: String,
        val smsNumbers: List<String>,
        val callNumbers: List<String>,
        val locationRecent: Boolean,
        val locationContinuous: Boolean,
        val soundUri: String,
        val soundDurationMs: Long,
        val vibrateDurationMs: Long,
        val flashlightDurationMs: Long,
        val flashlightMode: String,
        val flickerOnMs: Long = 2_000L,
        val flickerOffMs: Long = 2_000L,
        val logLabel: String,
        val resetCaps: Boolean,
        /** When true, Covert gates siren/flashlight and quiet-call. Safety passes false. */
        val applyCovertGates: Boolean,
        /** Alarm payloads include trigger diagnostics; Safety does not. */
        val includeTriggerDiagnostics: Boolean,
        val notificationUrgency: NotificationUrgency = NotificationUrgency.DEFAULT,
    )

    private val callFailover = CallFailover(context, repository, scope)
    private val clipRecorder = ClipRecorder(context, scope)
    private var pendingQuitJob: Job? = null

    fun dispatchAlarm(config: OverwatchConfig) {
        val ids = filterCovert(config.alarmEffectIds.toMutableSet(), config.covert)
        if (config.covert) {
            VerboseLog.d("Alarm", "covert filter raw=${config.alarmEffectIds} kept=$ids")
        }
        VerboseLog.d("Alarm", "dispatch ${config.name} ids=$ids")
        dispatch(
            config,
            DispatchContext(
                ids = ids,
                smsBody = config.alarmSmsBody,
                notificationBody = config.alarmNotificationBody,
                smsNumbers = config.smsContacts(),
                callNumbers = config.callContacts(),
                locationRecent = config.locationRecent,
                locationContinuous = config.locationContinuous,
                soundUri = config.alarmSoundUri,
                soundDurationMs = config.alarmSoundDurationMs,
                vibrateDurationMs = config.alarmVibrateDurationMs,
                flashlightDurationMs = config.alarmFlashlightDurationMs,
                flashlightMode = config.alarmFlashlightMode,
                flickerOnMs = config.alarmFlickerOnMs,
                flickerOffMs = config.alarmFlickerOffMs,
                logLabel = "Alarm",
                resetCaps = true,
                applyCovertGates = true,
                includeTriggerDiagnostics = true,
                notificationUrgency = config.alarmNotificationUrgency,
            ),
        )
    }

    fun dispatchSafety(config: OverwatchConfig, resetCaps: Boolean) {
        // Safety is overt "I am safe" — do not strip effects for Covert.
        val ids = config.safetyEffectIds
            .filter { it != "none" && AlarmCatalog.isAllowedInSafety(it) }
            .toSet()
        if (ids.isEmpty()) {
            VerboseLog.d("Safety", "dispatch skipped ${config.name}: none/empty")
            return
        }
        VerboseLog.d("Safety", "dispatch ${config.name} ids=$ids resetCaps=$resetCaps")
        dispatch(
            config,
            DispatchContext(
                ids = ids,
                smsBody = config.safetySmsBody,
                notificationBody = config.safetyNotificationBody,
                smsNumbers = config.safetySmsContacts(),
                callNumbers = config.safetyCallContacts(),
                locationRecent = false,
                locationContinuous = false,
                soundUri = config.safetySoundUri,
                soundDurationMs = config.safetySoundDurationMs.coerceAtLeast(1_000L),
                vibrateDurationMs = config.safetyVibrateDurationMs.coerceAtLeast(1_000L),
                flashlightDurationMs = config.safetyFlashlightDurationMs.coerceAtLeast(1_000L),
                flashlightMode = config.safetyFlashlightMode,
                flickerOnMs = config.safetyFlickerOnMs,
                flickerOffMs = config.safetyFlickerOffMs,
                logLabel = "Safety",
                resetCaps = resetCaps,
                applyCovertGates = false,
                includeTriggerDiagnostics = false,
                notificationUrgency = config.safetyNotificationUrgency,
            ),
        )
    }

    private fun filterCovert(ids: MutableSet<String>, covert: Boolean): MutableSet<String> {
        if (!covert) return ids
        ids.remove("siren")
        ids.remove("clock_alarm")
        ids.remove("flashlight")
        ids.remove("vibrate")
        ids.remove("sos_flashlight")
        ids.remove("flicker_flashlight")
        if ("call" in ids) ids += "quiet_call"
        return ids
    }

    private fun dispatch(config: OverwatchConfig, ctx: DispatchContext) {
        try {
            dispatchInner(config, ctx)
        } catch (t: Throwable) {
            VerboseLog.fail(ctx.logLabel, "dispatch", t)
        }
    }

    private fun dispatchInner(config: OverwatchConfig, ctx: DispatchContext) {
        if (ctx.resetCaps) OutboundCaps.reset()
        val ids = ctx.ids
        val covertGate = ctx.applyCovertGates && config.covert
        VerboseLog.d(ctx.logLabel, "effects ${ids.joinToString()}")
        if ("location" !in ids) {
            if ("sms" in ids) {
                sendSms(config, ctx)
            } else if (ctx.includeTriggerDiagnostics && EventLog.shouldWrite(context, config)) {
                writeAlarmLog(config, ctx)
            }
        }
        if ("call" in ids || "quiet_call" in ids) {
            callFailover.start(
                config.name,
                ctx.callNumbers,
                quiet = "quiet_call" in ids || covertGate,
            )
        }
        if ("siren" in ids && !covertGate) {
            cuePlayer.playSiren(config, soundUri = ctx.soundUri, durationMs = ctx.soundDurationMs)
        }
        if ("vibrate" in ids && !covertGate) {
            cuePlayer.vibrate(ctx.vibrateDurationMs)
        }
        if ("flashlight" in ids && !covertGate) {
            cuePlayer.playFlashlight(
                mode = ctx.flashlightMode,
                durationMs = ctx.flashlightDurationMs,
                flickerOnMs = ctx.flickerOnMs,
                flickerOffMs = ctx.flickerOffMs,
            )
        }
        val video = "record_video" in ids
        val audio = "record_audio" in ids
        if (video || audio) {
            val settings = OverwatchApp.from(context).latestSettings
            val clipMs = when {
                video -> settings.videoClipSeconds.coerceIn(3, 120) * 1000L
                else -> settings.audioClipSeconds.coerceIn(3, 120) * 1000L
            }
            clipRecorder.start(
                video = video,
                audio = audio,
                mode = settings.recordCameraMode,
                clipMs = clipMs,
            )
            VerboseLog.ok(ctx.logLabel, "record video=$video audio=$audio mode=${settings.recordCameraMode} clipMs=$clipMs")
            OverwatchService.refreshRecordingTypes(context, camera = video, mic = audio)
        }
        if ("notification" in ids) postCustomNotification(config, ctx)
        if ("location" in ids && ("sms" in ids || EventLog.shouldWrite(context, config))) {
            OverwatchService.refreshLocationType(context, true)
            LocationTrail.ensureHot(context)
            val settings = OverwatchApp.from(context).latestSettings
            val sendSms = "sms" in ids
            val writeLog = EventLog.shouldWrite(context, config)
            LocationSender.send(
                context,
                repository,
                config,
                scope,
                settings,
                sendSms = sendSms,
                writeLog = writeLog,
                includeAlarmMessage = sendSms || writeLog,
                includeRecentTrail = ctx.locationRecent,
                alarmBodyOverride = ctx.smsBody,
                includeTriggerDiagnostics = ctx.includeTriggerDiagnostics,
            )
            if (ctx.locationContinuous && (sendSms || writeLog)) {
                VerboseLog.d(ctx.logLabel, "location continuous")
                startContinuous(config, sendSms = sendSms, writeLog = writeLog)
            }
        } else if ("location" in ids) {
            VerboseLog.fail(ctx.logLabel, "location skipped needs sms or log")
        }
        if (!ctx.locationContinuous) LocationTrail.stop()
        // Quit only when explicitly selected — after other actions have been started.
        if ("quit" in ids) {
            pendingQuitJob?.cancel()
            pendingQuitJob = scope.launch {
                delay(QUIT_DEFER_MS)
                VerboseLog.d(ctx.logLabel, "quit deferred")
                AppShutdown.hardStop(context, finishAffinity = true)
            }
        }
    }

    fun halt() {
        VerboseLog.d("Alarm", "halt")
        pendingQuitJob?.cancel()
        pendingQuitJob = null
        ContinuousLocationScheduler.cancel(context)
        LocationTrail.stop()
        LocationTrail.clear()
        OverwatchService.refreshLocationType(context, false)
        callFailover.stop()
        clipRecorder.stop()
        cuePlayer.stop()
    }

    fun resumeSirenIfNeeded(config: OverwatchConfig) {
        if (config.covert || "siren" !in config.alarmEffectIds) return
        cuePlayer.playSiren(config, durationMs = config.alarmSoundDurationMs)
    }

    private fun startContinuous(config: OverwatchConfig, sendSms: Boolean, writeLog: Boolean) {
        ContinuousLocationScheduler.start(
            context,
            configId = config.id,
            sendSms = sendSms,
            writeLog = writeLog,
        )
    }

    private fun runningState(): AppState.Overwatch? =
        OverwatchApp.from(context).engine.state.value as? AppState.Overwatch

    private fun sendSms(config: OverwatchConfig, ctx: DispatchContext) {
        val numbers = ctx.smsNumbers
        val sms = context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        val running = runningState()
        val smsBody = AlarmPayload.fromRunning(
            running = running,
            config = config,
            includeMessage = true,
            includeDiagnostics = ctx.includeTriggerDiagnostics && config.sendTriggerMode,
            messageOverride = ctx.smsBody,
        )
        val logBody = AlarmPayload.fromRunning(
            running = running,
            config = config,
            includeMessage = true,
            includeDiagnostics = ctx.includeTriggerDiagnostics,
            messageOverride = ctx.smsBody,
        )
        EventLog.append(context, repository, scope, config, logBody)
        if (numbers.isEmpty()) {
            VerboseLog.fail("SMS", "skipped no contacts")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            VerboseLog.fail("SMS", "skipped no SEND_SMS permission")
            return
        }
        for (n in numbers) {
            if (!OutboundCaps.tryConsumeSms(context) {
                    VerboseLog.fail("SMS", "capped")
                }
            ) {
                break
            }
            val sent = runCatching {
                val parts = sms.divideMessage(smsBody)
                if (parts.size <= 1) sms.sendTextMessage(n, null, smsBody, null, null)
                else sms.sendMultipartTextMessage(n, null, parts, null, null)
            }
            if (sent.isSuccess) VerboseLog.ok("SMS", "sent contact ${numbers.indexOf(n) + 1}/${numbers.size}")
            else VerboseLog.fail("SMS", "send", sent.exceptionOrNull())
        }
    }

    private fun writeAlarmLog(config: OverwatchConfig, ctx: DispatchContext) {
        val body = AlarmPayload.fromRunning(
            running = runningState(),
            config = config,
            includeMessage = true,
            includeDiagnostics = true,
            messageOverride = ctx.smsBody,
        )
        EventLog.append(context, repository, scope, config, body)
    }

    private fun postCustomNotification(config: OverwatchConfig, ctx: DispatchContext) {
        OverwatchNotifications.ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val body = ctx.notificationBody.trim().ifEmpty {
            if (ctx.logLabel == "Safety") {
                "I am safe"
            } else {
                "I need help"
            }
        }
        val notification = NotificationCompat.Builder(context, OverwatchNotifications.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(config.name)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(ctx.notificationUrgency.toCompatPriority())
            .build()
        nm.notify(CUSTOM_NOTIFY_ID, notification)
        VerboseLog.ok(ctx.logLabel, "notification posted")
    }

    private fun NotificationUrgency.toCompatPriority(): Int = when (this) {
        NotificationUrgency.LOW -> NotificationCompat.PRIORITY_LOW
        NotificationUrgency.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
        NotificationUrgency.HIGH -> NotificationCompat.PRIORITY_HIGH
    }

    companion object {
        private const val QUIT_DEFER_MS = 2_500L
        private const val CUSTOM_NOTIFY_ID = 47

        /** Lasting Alarm/Safety effects that should be bounded by max duration. */
        fun needsDurationCap(ids: Collection<String>, continuousLocation: Boolean = false): Boolean {
            val set = ids.toSet()
            return "siren" in set ||
                "vibrate" in set ||
                "flashlight" in set ||
                "record_video" in set ||
                "record_audio" in set ||
                "call" in set ||
                "quiet_call" in set ||
                (continuousLocation && "location" in set)
        }
    }
}
