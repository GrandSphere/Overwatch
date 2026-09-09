package com.grandsphere.overwatch.runtime

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.domain.model.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OverwatchService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var ticker: Job? = null
    private var sensorTriggers: SensorTriggers? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        VerboseLog.ok("FGS", "onCreate")
        OverwatchNotifications.ensureChannels(this)
        LiveCountdownNotifications.ensureChannel(this)
        val app = OverwatchApp.from(this)
        sensorTriggers = SensorTriggers(this, app.engine)
        val state = app.engine.state.value
        sensorTriggers?.sync(state)
        val config = (state as? AppState.Overwatch)?.config
        val notification = if (state is AppState.Overwatch) {
            OverwatchNotifications.build(this, state)
        } else {
            OverwatchNotifications.placeholder(
                this,
                config ?: com.grandsphere.overwatch.data.db.SeedConfigs.driving,
            )
        }
        startAsForeground(notification)
        ticker = scope.launch {
            while (isActive) {
                try {
                    app.engine.tick()
                    val current = app.engine.state.value
                    sensorTriggers?.sync(current)
                    if (current is AppState.Overwatch) {
                        val nm = getSystemService(android.app.NotificationManager::class.java)
                        nm.notify(OverwatchNotifications.ID, OverwatchNotifications.build(this@OverwatchService, current))
                        LiveCountdownNotifications.sync(this@OverwatchService, current)
                    } else {
                        LiveCountdownNotifications.cancelAll(this@OverwatchService)
                        // Stay up until OverwatchService.stop() — Safety Mode may hold FGS after leave.
                    }
                } catch (t: Throwable) {
                    VerboseLog.fail("FGS", "tick", t)
                }
                delay(250)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var refresh = false
        if (intent != null) {
            if (intent.hasExtra(EXTRA_CAM)) {
                extraCamera = intent.getBooleanExtra(EXTRA_CAM, false)
                refresh = true
            }
            if (intent.hasExtra(EXTRA_MIC)) {
                extraMic = intent.getBooleanExtra(EXTRA_MIC, false)
                refresh = true
            }
            if (intent.hasExtra(EXTRA_LOC)) {
                extraLocation = intent.getBooleanExtra(EXTRA_LOC, false)
                refresh = true
            }
        }
        if (refresh) {
            VerboseLog.d("FGS", "onStartCommand cam=$extraCamera mic=$extraMic loc=$extraLocation")
            val app = OverwatchApp.from(this)
            val state = app.engine.state.value
            val notification = if (state is AppState.Overwatch) {
                OverwatchNotifications.build(this, state)
            } else {
                OverwatchNotifications.placeholder(
                    this,
                    (state as? AppState.Overwatch)?.config
                        ?: com.grandsphere.overwatch.data.db.SeedConfigs.driving,
                )
            }
            startAsForeground(notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        VerboseLog.ok("FGS", "onDestroy")
        sensorTriggers?.stop()
        sensorTriggers = null
        ticker?.cancel()
        job.cancel()
        LiveCountdownNotifications.cancelAll(this)
        extraCamera = false
        extraMic = false
        extraLocation = false
        running = false
        super.onDestroy()
    }

    private var extraCamera = false
    private var extraMic = false
    private var extraLocation = false

    private fun startAsForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (extraCamera) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (extraMic) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (extraLocation) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (tryStartForeground(notification, types, "types=$types loc=$extraLocation")) return
            if (extraLocation) {
                val locTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                if (locTypes != types &&
                    tryStartForeground(notification, locTypes, "retry specialUse|location")
                ) {
                    return
                }
                if (tryStartForeground(
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                        "retry location only",
                    )
                ) {
                    return
                }
                VerboseLog.fail("FGS", "location type required, not falling back to specialUse only")
            } else if (types != ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE &&
                tryStartForeground(notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, "fallback specialUse")
            ) {
                return
            }
            tryStartForegroundLegacy(notification)
        } else if (Build.VERSION.SDK_INT >= 29 && extraLocation) {
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (tryStartForeground(notification, types, "location loc=true")) return
            VerboseLog.fail("FGS", "location type failed, not dropping location FGS")
            tryStartForegroundLegacy(notification)
        } else {
            tryStartForegroundLegacy(notification)
        }
    }

    private fun tryStartForeground(
        notification: android.app.Notification,
        types: Int,
        label: String,
    ): Boolean {
        val result = runCatching {
            ServiceCompat.startForeground(
                this,
                OverwatchNotifications.ID,
                notification,
                types,
            )
        }
        return if (result.isSuccess) {
            VerboseLog.ok("FGS", "startForeground $label")
            true
        } else {
            VerboseLog.fail("FGS", "startForeground $label", result.exceptionOrNull())
            false
        }
    }

    private fun tryStartForegroundLegacy(notification: android.app.Notification) {
        val result = runCatching {
            startForeground(OverwatchNotifications.ID, notification)
        }
        if (result.isSuccess) VerboseLog.ok("FGS", "startForeground no-types")
        else VerboseLog.fail("FGS", "startForeground no-types", result.exceptionOrNull())
    }

    companion object {
        private const val EXTRA_CAM = "cam"
        private const val EXTRA_MIC = "mic"
        private const val EXTRA_LOC = "loc"

        @Volatile
        private var running = false

        fun start(context: Context) {
            val intent = Intent(context, OverwatchService::class.java)
            ContextCompatStart.start(context, intent)
        }

        fun stop(context: Context) {
            VerboseLog.d("FGS", "stop")
            context.stopService(Intent(context, OverwatchService::class.java))
        }

        fun refreshRecordingTypes(context: Context, camera: Boolean, mic: Boolean) {
            VerboseLog.d("FGS", "refreshRecordingTypes cam=$camera mic=$mic")
            val intent = Intent(context, OverwatchService::class.java)
                .putExtra(EXTRA_CAM, camera)
                .putExtra(EXTRA_MIC, mic)
            ContextCompatStart.start(context, intent)
        }

        fun refreshLocationType(context: Context, location: Boolean) {
            if (!location && !running) return
            VerboseLog.d("FGS", "refreshLocationType loc=$location")
            val intent = Intent(context, OverwatchService::class.java)
                .putExtra(EXTRA_LOC, location)
            ContextCompatStart.start(context, intent)
        }
    }
}

private object ContextCompatStart {
    fun start(context: Context, intent: Intent) {
        val result = runCatching {
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
        if (result.isSuccess) VerboseLog.ok("FGS", "startForegroundService")
        else VerboseLog.fail("FGS", "startForegroundService", result.exceptionOrNull())
    }
}
