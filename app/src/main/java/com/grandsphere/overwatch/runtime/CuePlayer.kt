package com.grandsphere.overwatch.runtime

import android.app.NotificationManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.grandsphere.overwatch.R
import com.grandsphere.overwatch.domain.model.NotificationUrgency
import com.grandsphere.overwatch.domain.model.OverwatchConfig

class CuePlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var preview: Ringtone? = null
    private var torchOn = false
    private val sosHandler = Handler(Looper.getMainLooper())
    private var sosStep = 0
    private var sosRunning = false
    private var flickerRunning = false
    private var flickerOnMs = 2_000L
    private var flickerOffMs = 2_000L
    private val soundStopHandler = Handler(Looper.getMainLooper())
    private val vibrateStopHandler = Handler(Looper.getMainLooper())
    private val flashlightStopHandler = Handler(Looper.getMainLooper())
    private val sosPatternMs = intArrayOf(
        180, 180, 180, 180, 180, 360,
        540, 180, 540, 180, 540, 360,
        180, 180, 180, 180, 180, 720,
    )

    fun play(config: OverwatchConfig, effectIds: List<String>, covert: Boolean) {
        val ids = if (covert) {
            effectIds.filter { it == "none" || it == "notification" }
                .ifEmpty { listOf("notification") }
        } else {
            effectIds
        }
        VerboseLog.d("Cue", "play covert=$covert ids=$ids")
        for (id in ids) {
            when (id) {
                "sound" -> if (!covert) {
                    playTimed(config.notifySoundUri, config.notifySoundDurationMs)
                }
                "vibrate" -> if (!covert) vibrate(config.notifyVibrateDurationMs)
                "flashlight" -> if (!covert) {
                    playFlashlight(
                        mode = config.notifyFlashlightMode,
                        durationMs = config.notifyFlashlightDurationMs,
                        flickerOnMs = config.notifyFlickerOnMs,
                        flickerOffMs = config.notifyFlickerOffMs,
                    )
                }
                "notification" -> postCheckIn(config, covert)
                "popup", "none", "live_notify" -> VerboseLog.ok("Cue", id)
            }
        }
    }

    fun stop() {
        stopPreview()
        stopSos()
        stopFlicker()
        stopSound()
        stopVibrate()
        flashlightStopHandler.removeCallbacksAndMessages(null)
        flashlight(false, log = false)
    }

    /** Stops siren/sound only. Torch, SOS, and recording keep running. */
    fun pauseSiren() {
        stopSound()
    }

    fun playSiren(
        config: OverwatchConfig? = null,
        soundUri: String? = null,
        durationMs: Long = OverwatchConfig.SOUND_UNTIL_DISMISSED,
    ) {
        val loop = durationMs == OverwatchConfig.SOUND_UNTIL_DISMISSED || durationMs > 5_000L
        VerboseLog.d("Cue", "siren durationMs=$durationMs loop=$loop")
        playTimed(soundUri ?: config?.alarmSoundUri.orEmpty(), durationMs)
    }

    fun flashlightOn() {
        playFlashlight(
            mode = OverwatchConfig.FLASHLIGHT_STEADY,
            durationMs = OverwatchConfig.SOUND_UNTIL_DISMISSED,
            flickerOnMs = 2_000L,
            flickerOffMs = 2_000L,
        )
    }

    fun playFlashlight(
        mode: String,
        durationMs: Long,
        flickerOnMs: Long = 2_000L,
        flickerOffMs: Long = 2_000L,
    ) {
        flashlightStopHandler.removeCallbacksAndMessages(null)
        when (OverwatchConfig.normalizeFlashlightMode(mode)) {
            OverwatchConfig.FLASHLIGHT_FLICKER -> playFlicker(flickerOnMs, flickerOffMs)
            OverwatchConfig.FLASHLIGHT_SOS -> playSos()
            else -> {
                stopSos()
                stopFlicker()
                flashlight(true, log = true)
            }
        }
        if (durationMs != OverwatchConfig.SOUND_UNTIL_DISMISSED && durationMs > 0L) {
            flashlightStopHandler.postDelayed({
                stopSos()
                stopFlicker()
                flashlight(false, log = false)
            }, durationMs)
        }
    }

    fun playSos() {
        stopFlicker()
        stopSos()
        sosRunning = true
        sosStep = 0
        VerboseLog.ok("Cue", "sos start")
        tickSos()
    }

    fun playFlicker(onMs: Long = 2_000L, offMs: Long = 2_000L) {
        stopSos()
        stopFlicker()
        flickerRunning = true
        flickerOnMs = onMs.coerceAtLeast(0L)
        flickerOffMs = offMs.coerceAtLeast(0L)
        VerboseLog.ok("Cue", "flicker start onMs=$flickerOnMs offMs=$flickerOffMs")
        tickFlicker(on = true)
    }

    fun previewUri(uriString: String) {
        stopPreview()
        val uri = uriString.toUriOrNull() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        preview = RingtoneManager.getRingtone(context, uri)?.also { it.play() }
    }

    fun stopPreview() {
        preview?.runCatching { stop() }
        preview = null
    }

    fun vibrate(durationMs: Long = 600L) {
        stopVibrate()
        val result = runCatching {
            val vib = vibrator() ?: return@runCatching
            val pattern = longArrayOf(0, 500, 200)
            if (durationMs == OverwatchConfig.SOUND_UNTIL_DISMISSED) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                val ms = durationMs.coerceAtLeast(1L)
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
                vibrateStopHandler.postDelayed({ stopVibrate() }, ms)
            }
        }
        if (result.isSuccess) VerboseLog.ok("Cue", "vibrate durationMs=$durationMs")
        else VerboseLog.fail("Cue", "vibrate", result.exceptionOrNull())
    }

    private fun postCheckIn(config: OverwatchConfig, covert: Boolean) {
        OverwatchNotifications.ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = if (covert) OverwatchNotifications.CHANNEL_COVERT else OverwatchNotifications.CHANNEL_STATUS
        val title = if (covert) "Timer" else "Check In"
        val text = if (covert) {
            "Active"
        } else {
            config.notifyNotificationBody.trim().ifEmpty { "Remember to check in" }
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(config.notifyNotificationUrgency.toCompatPriority())
            .build()
        nm.notify(CHECK_IN_NOTIFY_ID, notification)
        VerboseLog.ok("Cue", "notification Check In covert=$covert")
    }

    private fun NotificationUrgency.toCompatPriority(): Int = when (this) {
        NotificationUrgency.LOW -> NotificationCompat.PRIORITY_LOW
        NotificationUrgency.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
        NotificationUrgency.HIGH -> NotificationCompat.PRIORITY_HIGH
    }

    private fun playTimed(uriString: String, durationMs: Long) {
        val loop = durationMs == OverwatchConfig.SOUND_UNTIL_DISMISSED || durationMs > 5_000L
        playSound(uriString, loop = loop)
        soundStopHandler.removeCallbacksAndMessages(null)
        if (durationMs != OverwatchConfig.SOUND_UNTIL_DISMISSED && durationMs > 0L) {
            soundStopHandler.postDelayed({ stopSound() }, durationMs)
        }
    }

    private fun playSound(uriString: String, loop: Boolean) {
        stopSound()
        val custom = uriString.toUriOrNull()
        val uri = custom
            ?: RingtoneManager.getDefaultUri(if (loop) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (uri == null) {
            VerboseLog.fail("Cue", "sound no uri custom=${uriString.isNotBlank()}")
            return
        }
        val result = runCatching {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                isLooping = loop
                prepare()
                start()
            }
        }
        if (result.isSuccess) {
            VerboseLog.ok("Cue", "sound loop=$loop custom=${custom != null}")
        } else {
            mediaPlayer = null
            VerboseLog.fail("Cue", "sound loop=$loop custom=${custom != null}", result.exceptionOrNull())
        }
    }

    private fun stopSound() {
        soundStopHandler.removeCallbacksAndMessages(null)
        mediaPlayer?.runCatching { stop(); release() }
        mediaPlayer = null
    }

    private fun stopVibrate() {
        vibrateStopHandler.removeCallbacksAndMessages(null)
        runCatching { vibrator()?.cancel() }
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun flashlight(on: Boolean, log: Boolean = true) {
        val result = runCatching {
            val camera = context.getSystemService(CameraManager::class.java)
            val id = camera.cameraIdList.firstOrNull { camId ->
                camera.getCameraCharacteristics(camId)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: run {
                if (log) VerboseLog.fail("Cue", "flashlight no torch camera")
                return
            }
            camera.setTorchMode(id, on)
            torchOn = on
            id
        }
        if (!log) return
        if (result.isSuccess) VerboseLog.ok("Cue", "flashlight on=$on cam=${result.getOrNull()}")
        else VerboseLog.fail("Cue", "flashlight on=$on", result.exceptionOrNull())
    }

    private fun stopSos() {
        sosRunning = false
        sosHandler.removeCallbacksAndMessages(null)
        flashlight(false, log = false)
    }

    private fun stopFlicker() {
        flickerRunning = false
        sosHandler.removeCallbacksAndMessages(null)
        flashlight(false, log = false)
    }

    private fun tickSos() {
        if (!sosRunning) return
        val on = sosStep % 2 == 0
        flashlight(on, log = false)
        val delay = sosPatternMs[sosStep % sosPatternMs.size].toLong()
        sosStep += 1
        sosHandler.postDelayed({ tickSos() }, delay)
    }

    private fun tickFlicker(on: Boolean) {
        if (!flickerRunning) return
        flashlight(on, log = false)
        val delay = if (on) flickerOnMs else flickerOffMs
        sosHandler.postDelayed({ tickFlicker(!on) }, delay.coerceAtLeast(1L))
    }

    private fun String.toUriOrNull(): Uri? =
        if (isBlank()) null else runCatching { Uri.parse(this) }.getOrNull()

    companion object {
        private const val CHECK_IN_NOTIFY_ID = 48
    }
}
