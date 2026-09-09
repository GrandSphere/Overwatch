package com.grandsphere.overwatch

import android.app.Application
import android.content.Context
import com.grandsphere.overwatch.data.OverwatchRepository
import com.grandsphere.overwatch.data.SettingsStore
import com.grandsphere.overwatch.data.db.OverwatchDatabase
import com.grandsphere.overwatch.domain.engine.LoopCallbacks
import com.grandsphere.overwatch.domain.engine.OverwatchEngine
import com.grandsphere.overwatch.domain.hardware.InAppRestrictedHardware
import com.grandsphere.overwatch.domain.hardware.RestrictedHardware
import com.grandsphere.overwatch.domain.model.AppSettings
import com.grandsphere.overwatch.domain.model.AppState
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import com.grandsphere.overwatch.runtime.AlarmCapScheduler
import com.grandsphere.overwatch.runtime.AlarmDispatcher
import com.grandsphere.overwatch.runtime.AppShutdown
import com.grandsphere.overwatch.runtime.ClockAlarm
import com.grandsphere.overwatch.runtime.CuePlayer
import com.grandsphere.overwatch.runtime.DeadlineScheduler
import com.grandsphere.overwatch.runtime.EventLog
import com.grandsphere.overwatch.runtime.LocationTrail
import com.grandsphere.overwatch.runtime.OverwatchService
import com.grandsphere.overwatch.runtime.VerboseLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OverwatchApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    lateinit var database: OverwatchDatabase
        private set
    lateinit var repository: OverwatchRepository
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var cuePlayer: CuePlayer
        private set
    lateinit var dispatcher: AlarmDispatcher
        private set
    lateinit var engine: OverwatchEngine
        private set
    lateinit var hardware: RestrictedHardware
        private set

    @Volatile
    var latestSettings: AppSettings = AppSettings()

    fun hardStop(finishAffinity: Boolean = true) {
        AppShutdown.hardStop(this, finishAffinity)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        VerboseLog.install(this)
        database = OverwatchDatabase.create(this)
        repository = OverwatchRepository(this, database)
        settingsStore = SettingsStore(this)
        cuePlayer = CuePlayer(this)
        dispatcher = AlarmDispatcher(this, repository, cuePlayer, scope)
        hardware = InAppRestrictedHardware()
        engine = OverwatchEngine(object : LoopCallbacks {
            override fun onEnterOverwatch(config: OverwatchConfig) {
                OverwatchService.start(this@OverwatchApp)
                engine.nextDeadlineElapsed()?.let { DeadlineScheduler.schedule(this@OverwatchApp, it) }
                val running = engine.state.value as? AppState.Overwatch
                if (running != null && running.submode != com.grandsphere.overwatch.domain.model.Submode.AlarmMode) {
                    if ("location" in config.alarmEffectIds) {
                        OverwatchService.refreshLocationType(this@OverwatchApp, true)
                        LocationTrail.start(this@OverwatchApp)
                    }
                }
            }

            override fun onHaltAlarmEffects() {
                VerboseLog.d("Engine", "halt alarm effects")
                dispatcher.halt()
                ClockAlarm.dismiss(this@OverwatchApp)
            }

            override fun onHaltNotifyCues() {
                VerboseLog.d("Engine", "halt notify cues")
                cuePlayer.stop()
                ClockAlarm.dismiss(this@OverwatchApp)
            }

            override fun onTeardownOverwatch(keepService: Boolean) {
                VerboseLog.d("Engine", "teardown keepService=$keepService")
                DeadlineScheduler.cancel(this@OverwatchApp)
                if (!keepService) {
                    OverwatchService.stop(this@OverwatchApp)
                }
            }

            override fun onNotifyCue(config: OverwatchConfig, effectIds: List<String>, covert: Boolean) {
                cuePlayer.play(config, effectIds, covert)
            }

            override fun onAlarm(config: OverwatchConfig) {
                dispatcher.dispatchAlarm(config)
            }

            override fun onSafety(config: OverwatchConfig, resetCaps: Boolean) {
                VerboseLog.ok("Safety", "dispatch ${config.name} resetCaps=$resetCaps")
                dispatcher.dispatchSafety(config, resetCaps)
                if (AlarmDispatcher.needsDurationCap(
                        config.safetyEffectIds,
                        continuousLocation = config.safetyLocationContinuous,
                    )
                ) {
                    AlarmCapScheduler.schedule(this@OverwatchApp, engine.maxAlarmDurationMinutes)
                }
            }

            override fun onAlarmCapSchedule(minutes: Int) {
                AlarmCapScheduler.schedule(this@OverwatchApp, minutes)
            }

            override fun onAlarmCapCancel() {
                AlarmCapScheduler.cancel(this@OverwatchApp)
            }

            override fun onHardQuit() {
                AppShutdown.hardStop(this@OverwatchApp, finishAffinity = true)
            }

            override fun onUserLog(config: OverwatchConfig, message: String) {
                EventLog.append(this@OverwatchApp, repository, scope, config, message)
            }
        })
        hardware.bind(engine)
        scope.launch {
            settingsStore.ensureDefaults()
            settingsStore.settings.collect { settings ->
                latestSettings = settings
                val wasEnabled = VerboseLog.enabled
                if (settings.verboseLogging && !wasEnabled) {
                    VerboseLog.enabled = true
                    VerboseLog.logProcessStart()
                    VerboseLog.ok("Process", "verbose logging enabled")
                    VerboseLog.d(
                        "Log",
                        "alwaysLogEvents=${settings.alwaysLogEvents} pinSet=${settings.pinHash.isNotBlank()}",
                    )
                } else if (!settings.verboseLogging && wasEnabled) {
                    VerboseLog.d("Process", "verbose logging disabled")
                    VerboseLog.enabled = false
                } else {
                    VerboseLog.enabled = settings.verboseLogging
                }
                engine.settingsPinHash = settings.pinHash
                engine.duressDigit = settings.duressDigit.trim().firstOrNull()
                engine.duressPrefix = settings.duressPrefix
                engine.panicOnTwoWrongPins = settings.panicOnTwoWrongPins
                engine.failSecretly = settings.failSecretly
                engine.maxAlarmDurationMinutes = settings.maxAlarmDurationMinutes
                com.grandsphere.overwatch.widget.refreshOverwatchWidgets(this@OverwatchApp)
            }
        }
        scope.launch(Dispatchers.IO) {
            repository.seedIfEmpty()
            engine.configs = repository.list()
        }
        scope.launch {
            repository.configs.collect { engine.configs = it }
        }
        scope.launch {
            var lastKey: String? = null
            engine.state.collect { state ->
                val key = when (state) {
                    AppState.Wading -> "wading"
                    is AppState.Overwatch ->
                        "${state.submode}-${state.deadlineElapsedMs}-${state.graceDeadlineElapsedMs}"
                }
                if (key == lastKey) return@collect
                lastKey = key
                when (state) {
                    AppState.Wading -> VerboseLog.d("Engine", "state Idle (wading)")
                    is AppState.Overwatch -> VerboseLog.d(
                        "Engine",
                        "state ${state.submode} ${state.config.name} remainingMs=${state.remainingMs} " +
                            "panic=${state.startedByPanic} secret=${state.secretAlarm} " +
                            "alarmThisRun=${state.enteredAlarmThisRun}",
                    )
                }
                ClockAlarm.sync(this@OverwatchApp, state)
                when (state) {
                    AppState.Wading -> Unit
                    is AppState.Overwatch -> {
                        val next = engine.nextDeadlineElapsed()
                        if (next == null) DeadlineScheduler.cancel(this@OverwatchApp)
                        else DeadlineScheduler.schedule(this@OverwatchApp, next)
                    }
                }
            }
        }
    }

    companion object {
        private lateinit var instance: OverwatchApp
        fun from(context: Context): OverwatchApp = context.applicationContext as OverwatchApp
    }
}
