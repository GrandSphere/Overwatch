package com.grandsphere.overwatch.data

import com.grandsphere.overwatch.domain.model.OverwatchConfig
import org.json.JSONObject

object OverwatchExchange {
    const val FORMAT_OVERWATCHES = "overwatch-overwatches"
    const val FORMAT_LOG = "overwatch-log"
    const val FORMAT_VERBOSE_LOG = "overwatch-verbose-log"

    const val FILE_OVERWATCHES = "overwatch-overwatches.json"

    fun detectFormat(json: String): String? {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching { JSONObject(trimmed).optString("format") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun overwatchesJson(configs: List<OverwatchConfig>): String =
        ImportDatabaseValidator.configsExchangeJson(configs)

    fun parseOverwatches(json: String): List<OverwatchConfig> =
        ImportDatabaseValidator.parseConfigsExchange(json).map { it.toDomain() }

    fun logJson(text: String): String = JSONObject().apply {
        put("format", FORMAT_LOG)
        put("version", 1)
        put("text", text)
    }.toString()

    fun verboseLogJson(text: String): String = JSONObject().apply {
        put("format", FORMAT_VERBOSE_LOG)
        put("version", 1)
        put("text", text)
    }.toString()

    fun parseLogText(json: String): String {
        val obj = JSONObject(json.trim())
        if (obj.optString("format") != FORMAT_LOG) error("Not a log export")
        return obj.getString("text")
    }

    fun parseVerboseLogText(json: String): String {
        val obj = JSONObject(json.trim())
        if (obj.optString("format") != FORMAT_VERBOSE_LOG) error("Not a verbose log export")
        return obj.getString("text")
    }

    fun uniqueTempName(taken: Set<String>, prefix: String = "temp"): String {
        var i = 1
        while (i < 10_000) {
            val candidate = "$prefix$i"
            if (candidate !in taken) return candidate
            i++
        }
        return "${prefix}${System.currentTimeMillis()}"
    }
}
