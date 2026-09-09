package com.grandsphere.overwatch.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.grandsphere.overwatch.domain.catalog.CancelCatalog
import com.grandsphere.overwatch.domain.catalog.DismissCatalog
import com.grandsphere.overwatch.domain.catalog.PanicModeCatalog
import com.grandsphere.overwatch.domain.engine.OverwatchEngine
import com.grandsphere.overwatch.domain.model.AppState
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import com.grandsphere.overwatch.domain.model.ShakeStrength
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Power / turnover / shake / crash detectors while an Overwatch is running.
 * Calls [OverwatchEngine.onSensorTrigger] (panic path remains immediate).
 */
class SensorTriggers(
    private val context: Context,
    private val engine: OverwatchEngine,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var registered = false
    private var screenReceiver: BroadcastReceiver? = null

    private var wantPower = false
    private var wantTurnover = false
    private var wantShake = false
    private var wantCrash = false
    private var powerTapsNeeded = 3
    private var shakeStrength = ShakeStrength.MEDIUM
    private var shakeCountNeeded = 3
    private var crashThresholdG = 8f
    private var crashStillnessMs = 50_000L
    private var turnoverHoldMs = 700L

    private var screenToggleTimes = mutableListOf<Long>()
    private var shakeHitTimes = mutableListOf<Long>()
    private var lastShakeAt = 0L
    private var faceDownSince = 0L
    private var crashCooldownUntil = 0L
    private var crashSpikeAt = 0L
    private var crashStillSince = 0L

    private var lastKey: String? = null

    fun sync(state: AppState) {
        val running = state as? AppState.Overwatch
        if (running == null) {
            if (lastKey != null) {
                stop()
                lastKey = null
            }
            return
        }
        val config = running.config
        val key = listOf(
            config.panicEffectIds.joinToString(),
            config.dismissEffectIds.joinToString(),
            config.cancelEffectIds.joinToString(),
            config.panicPowerTaps,
            config.dismissPowerTaps,
            config.cancelPowerTaps,
            config.shakeStrength.name,
            config.dismissShakeStrength.name,
            config.cancelShakeStrength.name,
            config.shakeCount,
            config.dismissShakeCount,
            config.cancelShakeCount,
            config.crashThresholdG,
            config.crashStillnessMs,
            config.turnoverHoldMs,
            config.dismissTurnoverHoldMs,
            config.cancelTurnoverHoldMs,
        ).joinToString("|")
        if (key == lastKey) return
        lastKey = key
        applyConfig(config)
    }

    private fun applyConfig(config: OverwatchConfig) {
        val panic = config.panicEffectIds
        val dismiss = config.dismissEffectIds
        val cancel = CancelCatalog.resolvedProofIds(config.cancelEffectIds, config.dismissEffectIds)
        wantPower = PanicModeCatalog.usesPower(panic) ||
            DismissCatalog.usesPowerButton(dismiss) ||
            "power_button" in cancel
        wantTurnover = PanicModeCatalog.usesTurnover(panic) ||
            DismissCatalog.usesTurnover(dismiss) ||
            "turnover" in cancel
        wantShake = PanicModeCatalog.usesShake(panic) ||
            DismissCatalog.usesShake(dismiss) ||
            "shake" in cancel
        wantCrash = PanicModeCatalog.usesCrash(panic)
        powerTapsNeeded = when {
            PanicModeCatalog.usesPower(panic) -> config.panicPowerTaps
            DismissCatalog.usesPowerButton(dismiss) -> config.dismissPowerTaps
            else -> config.cancelPowerTaps
        }.coerceIn(2, 10)
        shakeStrength = when {
            PanicModeCatalog.usesShake(panic) -> config.shakeStrength
            DismissCatalog.usesShake(dismiss) -> config.dismissShakeStrength
            else -> config.cancelShakeStrength
        }
        shakeCountNeeded = when {
            PanicModeCatalog.usesShake(panic) -> config.shakeCount
            DismissCatalog.usesShake(dismiss) -> config.dismissShakeCount
            else -> config.cancelShakeCount
        }.coerceIn(1, 10)
        crashThresholdG = config.crashThresholdG.coerceIn(1f, 16f)
        crashStillnessMs = config.crashStillnessMs.coerceAtLeast(1_000L)
        turnoverHoldMs = when {
            PanicModeCatalog.usesTurnover(panic) -> config.turnoverHoldMs
            DismissCatalog.usesTurnover(dismiss) -> config.dismissTurnoverHoldMs
            else -> config.cancelTurnoverHoldMs
        }.coerceAtLeast(100L)

        val needAccel = wantTurnover || wantShake || wantCrash
        if (needAccel) startAccel() else stopAccel()
        if (wantPower) startScreen() else stopScreen()
        if (!needAccel && !wantPower) {
            registered = false
        }
    }

    fun stop() {
        wantPower = false
        wantTurnover = false
        wantShake = false
        wantCrash = false
        stopAccel()
        stopScreen()
        screenToggleTimes.clear()
        shakeHitTimes.clear()
        faceDownSince = 0L
        crashSpikeAt = 0L
        crashStillSince = 0L
        registered = false
    }

    private fun startAccel() {
        if (accel == null || sensorManager == null) return
        if (registered) return
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        registered = true
        VerboseLog.ok("Sensor", "accel on")
    }

    private fun stopAccel() {
        if (!registered) return
        sensorManager?.unregisterListener(this)
        registered = false
        VerboseLog.d("Sensor", "accel off")
    }

    private fun startScreen() {
        if (screenReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                if (action != Intent.ACTION_SCREEN_ON && action != Intent.ACTION_SCREEN_OFF) return
                onScreenToggle()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(receiver, filter)
        screenReceiver = receiver
        VerboseLog.ok("Sensor", "screen receiver on taps=$powerTapsNeeded")
    }

    private fun stopScreen() {
        val receiver = screenReceiver ?: return
        runCatching { context.unregisterReceiver(receiver) }
        screenReceiver = null
        screenToggleTimes.clear()
        VerboseLog.d("Sensor", "screen receiver off")
    }

    private fun onScreenToggle() {
        if (!wantPower) return
        val now = SystemClock.elapsedRealtime()
        screenToggleTimes.removeAll { now - it > POWER_WINDOW_MS }
        screenToggleTimes += now
        if (screenToggleTimes.size >= powerTapsNeeded) {
            screenToggleTimes.clear()
            VerboseLog.ok("Sensor", "power button taps=$powerTapsNeeded")
            fire(PanicModeCatalog.POWER_BUTTON)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val mag = sqrt(x * x + y * y + z * z)
        val now = SystemClock.elapsedRealtime()
        val g = SensorManager.GRAVITY_EARTH

        if (wantShake) {
            val threshold = shakeThresholdG(shakeStrength) * g
            if (mag > threshold && now - lastShakeAt > SHAKE_REFRACTORY_MS) {
                lastShakeAt = now
                shakeHitTimes.removeAll { now - it > SHAKE_WINDOW_MS }
                shakeHitTimes += now
                if (shakeHitTimes.size >= shakeCountNeeded) {
                    shakeHitTimes.clear()
                    VerboseLog.ok("Sensor", "shake strength=$shakeStrength count=$shakeCountNeeded")
                    fire(PanicModeCatalog.SHAKE)
                }
            }
        }

        if (wantTurnover) {
            val faceDown = z < -TURNOVER_Z_G * g
            if (faceDown) {
                if (faceDownSince == 0L) faceDownSince = now
                else if (now - faceDownSince >= turnoverHoldMs) {
                    faceDownSince = Long.MAX_VALUE / 2
                    VerboseLog.ok("Sensor", "turnover")
                    fire(PanicModeCatalog.TURNOVER)
                }
            } else {
                faceDownSince = 0L
            }
        }

        if (wantCrash && now >= crashCooldownUntil) {
            val spikeThreshold = crashThresholdG * g
            if (crashSpikeAt == 0L) {
                if (mag > spikeThreshold) {
                    crashSpikeAt = now
                    crashStillSince = 0L
                    VerboseLog.d("Sensor", "crash spike mag=$mag thr=${crashThresholdG}g")
                }
            } else if (isNearlyStill(mag, g)) {
                if (crashStillSince == 0L) crashStillSince = now
                else if (now - crashStillSince >= crashStillnessMs) {
                    crashCooldownUntil = now + CRASH_COOLDOWN_MS
                    crashSpikeAt = 0L
                    crashStillSince = 0L
                    VerboseLog.ok(
                        "Sensor",
                        "crash detect thr=${crashThresholdG}g still=${crashStillnessMs}ms",
                    )
                    fire(PanicModeCatalog.CRASH_DETECT)
                }
            } else {
                crashStillSince = 0L
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun fire(id: String) {
        mainHandler.post { engine.onSensorTrigger(id) }
    }

    private fun isNearlyStill(mag: Float, g: Float): Boolean =
        abs(mag - g) < CRASH_STILL_DELTA_G * g

    private fun shakeThresholdG(strength: ShakeStrength): Float = when (strength) {
        ShakeStrength.LOW -> 1.8f
        ShakeStrength.MEDIUM -> 2.4f
        ShakeStrength.HIGH -> 3.2f
    }

    companion object {
        private const val POWER_WINDOW_MS = 1_500L
        private const val SHAKE_WINDOW_MS = 1_200L
        private const val SHAKE_REFRACTORY_MS = 180L
        private const val TURNOVER_HOLD_MS = 700L
        private const val TURNOVER_Z_G = 0.7f
        private const val CRASH_STILL_DELTA_G = 0.35f
        private const val CRASH_COOLDOWN_MS = 5_000L
    }
}
