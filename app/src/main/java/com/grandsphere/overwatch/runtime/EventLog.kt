package com.grandsphere.overwatch.runtime

import android.content.Context
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.data.OverwatchRepository
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object EventLog {
    /**
     * [effectIds] is the dispatch list (Alarm or Safety). When omitted, lifecycle lines
     * use Alarm Log (or always-log).
     */
    fun shouldWrite(
        context: Context,
        config: OverwatchConfig,
        effectIds: Collection<String>? = null,
    ): Boolean {
        val settings = OverwatchApp.from(context).latestSettings
        if (settings.alwaysLogEvents) return true
        return "log" in (effectIds ?: config.alarmEffectIds)
    }

    fun append(
        context: Context,
        repository: OverwatchRepository,
        scope: CoroutineScope,
        config: OverwatchConfig,
        message: String,
        effectIds: Collection<String>? = null,
    ) {
        if (!shouldWrite(context, config, effectIds)) return
        scope.launch(Dispatchers.IO) {
            repository.appendLog(config.name, message)
        }
    }
}
