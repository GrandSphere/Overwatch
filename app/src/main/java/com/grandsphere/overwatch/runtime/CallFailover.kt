package com.grandsphere.overwatch.runtime

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.grandsphere.overwatch.data.OverwatchRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Queues outbound calls: after a call is no longer in progress, dial the next contact.
 * Watches [TelephonyManager] RINGING/OFFHOOK → IDLE. Background/lock-screen: full-screen
 * intent to [CallLaunchActivity].
 */
class CallFailover(
    private val context: Context,
    private val repository: OverwatchRepository,
    private val scope: CoroutineScope,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var numbers: List<String> = emptyList()
    private var index = 0
    private var quiet = false
    private var configName = ""
    /** True after RINGING or OFFHOOK for the current dial attempt. */
    private var sawInProgress = false
    private var advancing = false
    private var listening = false
    private var neverLeftIdleFallback: Runnable? = null
    private var settleThenNext: Runnable? = null
    private var telephonyCallback: TelephonyCallback? = null

    @Suppress("DEPRECATION")
    private var phoneListener: PhoneStateListener? = null

    fun start(configName: String, numbers: List<String>, quiet: Boolean) {
        stopListening()
        this.configName = configName
        this.numbers = numbers.filter { it.isNotEmpty() }
        this.index = 0
        this.quiet = quiet
        if (this.numbers.isEmpty()) {
            VerboseLog.fail("Call", "no contacts")
            return
        }
        VerboseLog.d("Call", "start count=${this.numbers.size} quiet=$quiet")
        if (!canWatchState()) {
            VerboseLog.fail("Call", "no READ_PHONE_STATE; cannot cascade contacts")
        }
        if (canWatchState()) startListening()
        dialCurrent()
    }

    fun stop() {
        stopListening()
        numbers = emptyList()
        index = 0
        context.getSystemService(NotificationManager::class.java).cancel(CALL_NOTIFY_ID)
    }

    private fun canWatchState(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    private fun dialCurrent() {
        val number = numbers.getOrNull(index) ?: return
        if (!OutboundCaps.tryConsumeCall(context) {
                VerboseLog.fail("Call", "capped")
            }
        ) {
            stopListening()
            return
        }
        sawInProgress = false
        advancing = false
        clearTimers()
        if (quiet) quietCallAudio()
        val launch = CallLaunchActivity.intent(context, number)
        val launched = runCatching { context.startActivity(launch) }
        if (launched.isSuccess) VerboseLog.ok("Call", "dial ${index + 1}/${numbers.size} quiet=$quiet")
        else VerboseLog.fail("Call", "dial ${index + 1}/${numbers.size}", launched.exceptionOrNull())
        postFullScreenCall(number)
        if (canWatchState()) {
            // Only if dial never leaves IDLE (failed to start) — not a connect timer.
            val wait = Runnable {
                if (!sawInProgress) {
                    VerboseLog.d("Call", "dial never left IDLE; trying next")
                    tryNext()
                }
            }
            neverLeftIdleFallback = wait
            handler.postDelayed(wait, NEVER_LEFT_IDLE_MS)
        } else {
            // Cannot observe state: only one dial attempt.
            stopListening()
        }
    }

    private fun postFullScreenCall(number: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CALL_CHANNEL, "Overwatch call", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
        val pi = PendingIntent.getActivity(
            context,
            CALL_NOTIFY_ID,
            CallLaunchActivity.intent(context, number),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CALL_CHANNEL)
            .setSmallIcon(com.grandsphere.overwatch.R.drawable.ic_launcher_foreground)
            .setContentTitle("Overwatch")
            .setContentText("Calling")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(CALL_NOTIFY_ID, notification)
    }

    private fun tryNext() {
        if (advancing) return
        advancing = true
        clearTimers()
        context.getSystemService(NotificationManager::class.java).cancel(CALL_NOTIFY_ID)
        index += 1
        if (index < numbers.size) {
            VerboseLog.d("Call", "trying next contact (${index + 1}/${numbers.size})")
            val settle = Runnable {
                advancing = false
                dialCurrent()
            }
            settleThenNext = settle
            handler.postDelayed(settle, SETTLE_BEFORE_NEXT_MS)
        } else {
            advancing = false
            stopListening()
        }
    }

    private fun onCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK,
            -> {
                if (!sawInProgress) {
                    sawInProgress = true
                    VerboseLog.ok(
                        "Call",
                        if (state == TelephonyManager.CALL_STATE_RINGING) {
                            "ringing ${index + 1}/${numbers.size}"
                        } else {
                            "offhook ${index + 1}/${numbers.size}"
                        },
                    )
                }
                neverLeftIdleFallback?.let { handler.removeCallbacks(it) }
                neverLeftIdleFallback = null
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    context.getSystemService(NotificationManager::class.java).cancel(CALL_NOTIFY_ID)
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (sawInProgress) {
                    VerboseLog.d("Call", "idle after in-progress; cascade")
                    tryNext()
                }
            }
        }
    }

    private fun startListening() {
        if (listening) return
        listening = true
        val tm = context.getSystemService(TelephonyManager::class.java)
        if (Build.VERSION.SDK_INT >= 31) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    onCallState(state)
                }
            }
            telephonyCallback = cb
            tm.registerTelephonyCallback(context.mainExecutor, cb)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    onCallState(state)
                }
            }
            phoneListener = listener
            @Suppress("DEPRECATION")
            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun clearTimers() {
        neverLeftIdleFallback?.let { handler.removeCallbacks(it) }
        neverLeftIdleFallback = null
        settleThenNext?.let { handler.removeCallbacks(it) }
        settleThenNext = null
    }

    private fun stopListening() {
        clearTimers()
        if (!listening) return
        listening = false
        val tm = context.getSystemService(TelephonyManager::class.java)
        if (Build.VERSION.SDK_INT >= 31) {
            telephonyCallback?.let { tm.unregisterTelephonyCallback(it) }
            telephonyCallback = null
        } else {
            @Suppress("DEPRECATION")
            phoneListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
            phoneListener = null
        }
    }

    private fun quietCallAudio() {
        val am = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            runCatching { am.ringerMode = AudioManager.RINGER_MODE_SILENT }
        }
        am.mode = AudioManager.MODE_IN_CALL
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = false
        listOf(
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_VOICE_CALL,
        ).forEach { stream ->
            val min = if (Build.VERSION.SDK_INT >= 28) am.getStreamMinVolume(stream) else 0
            runCatching { am.setStreamVolume(stream, min, 0) }
        }
    }

    companion object {
        private const val CALL_CHANNEL = "overwatch_call"
        private const val CALL_NOTIFY_ID = 45
        private const val NEVER_LEFT_IDLE_MS = 20_000L
        private const val SETTLE_BEFORE_NEXT_MS = 1_200L
    }
}
