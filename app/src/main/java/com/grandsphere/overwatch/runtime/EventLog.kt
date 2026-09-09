package com.grandsphere.overwatch.runtime

import android.content.Context
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.data.OverwatchRepository
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object EventLog {
    fun shouldWrite(context: Context, config: OverwatchConfig): Boolean {
        val settings = OverwatchApp.from(context).latestSettings
        return settings.alwaysLogEvents || "log" in config.alarmEffectIds
    }

    fun append(
        context: Context,
        repository: OverwatchRepository,
        scope: CoroutineScope,
        config: OverwatchConfig,
        message: String,
    ) {
        if (!shouldWrite(context, config)) return
        scope.launch(Dispatchers.IO) {
            repository.appendLog(config.name, message)
        }
    }
}
