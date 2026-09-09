package com.grandsphere.overwatch.data

import com.grandsphere.overwatch.domain.model.AppSettings
import com.grandsphere.overwatch.domain.model.HardwareKeyOption
import com.grandsphere.overwatch.domain.model.PanicActivation
import com.grandsphere.overwatch.domain.model.RecordCameraMode
import org.json.JSONObject

object SettingsExport {
    const val FORMAT = "overwatch-settings"
    const val VERSION = 1
    const val FILE_NAME = "overwatch-settings.json"

    fun toJson(settings: AppSettings): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("panicActivation", settings.panicActivation.name)
        put("panicHardwareKey", settings.panicHardwareKey.name)
        if (settings.idlePanicConfigId == null) put("idlePanicConfigId", JSONObject.NULL)
        else put("idlePanicConfigId", settings.idlePanicConfigId)
        put("pinHash", settings.pinHash)
        put("duressDigit", settings.duressDigit)
        put("duressPrefix", settings.duressPrefix)
        put("preventCloseOnOverwatch", settings.preventCloseOnOverwatch)
        put("hardwareKeysOutsideApp", settings.hardwareKeysOutsideApp)
        put("turnLocationOn", settings.turnLocationOn)
        put("notifyToasts", settings.notifyToasts)
        put("videoClipSeconds", settings.videoClipSeconds)
        put("audioClipSeconds", settings.audioClipSeconds)
        put("recordCameraMode", settings.recordCameraMode.name)
        put("darkCardArgb", settings.darkCardArgb)
        put("widgetArgb", settings.widgetArgb)
        put("actionArgb", settings.actionArgb)
        put("fontScale", settings.fontScale.toDouble())
        put("recentLocationMinutes", settings.recentLocationMinutes)
        put("recentLocationPoints", settings.recentLocationPoints)
        put("continuousLocationMinutes", settings.continuousLocationMinutes)
        put("panicOnTwoWrongPins", settings.panicOnTwoWrongPins)
        put("failSecretly", settings.failSecretly)
        put("failSecretPhrase", settings.failSecretPhrase)
        put("maxAlarmDurationMinutes", settings.maxAlarmDurationMinutes)
        put("maxAlarmSms", settings.maxAlarmSms)
        put("maxAlarmCalls", settings.maxAlarmCalls)
        put("verboseLogging", settings.verboseLogging)
        put("alwaysLogEvents", settings.alwaysLogEvents)
    }.toString()

    fun merge(current: AppSettings, json: String, knownConfigIds: Set<Long>): AppSettings {
        val obj = JSONObject(json)
        if (obj.optString("format") != FORMAT) error("Not a settings export")
        return current.copy(
            panicActivation = obj.enum("panicActivation", current.panicActivation),
            panicHardwareKey = obj.enum("panicHardwareKey", current.panicHardwareKey),
            idlePanicConfigId = obj.mergeIdlePanic(current.idlePanicConfigId, knownConfigIds),
            pinHash = obj.string("pinHash", current.pinHash).ifBlank { current.pinHash },
            duressDigit = obj.string("duressDigit", current.duressDigit),
            duressPrefix = obj.bool("duressPrefix", current.duressPrefix),
            preventCloseOnOverwatch = obj.bool("preventCloseOnOverwatch", current.preventCloseOnOverwatch),
            hardwareKeysOutsideApp = obj.bool("hardwareKeysOutsideApp", current.hardwareKeysOutsideApp),
            turnLocationOn = obj.bool("turnLocationOn", current.turnLocationOn),
            notifyToasts = obj.bool("notifyToasts", current.notifyToasts),
            videoClipSeconds = obj.int("videoClipSeconds", current.videoClipSeconds),
            audioClipSeconds = obj.int("audioClipSeconds", current.audioClipSeconds),
            recordCameraMode = obj.mergeCameraMode(current.recordCameraMode),
            darkCardArgb = obj.int("darkCardArgb", current.darkCardArgb),
            widgetArgb = obj.int("widgetArgb", current.widgetArgb),
            actionArgb = obj.int("actionArgb", current.actionArgb),
            fontScale = obj.float("fontScale", current.fontScale),
            recentLocationMinutes = obj.int("recentLocationMinutes", current.recentLocationMinutes),
            recentLocationPoints = obj.int("recentLocationPoints", current.recentLocationPoints).coerceIn(1, 20),
            continuousLocationMinutes = obj.int("continuousLocationMinutes", current.continuousLocationMinutes),
            panicOnTwoWrongPins = obj.bool("panicOnTwoWrongPins", current.panicOnTwoWrongPins),
            failSecretly = obj.bool("failSecretly", current.failSecretly),
            failSecretPhrase = obj.string("failSecretPhrase", current.failSecretPhrase).ifBlank { current.failSecretPhrase },
            maxAlarmDurationMinutes = obj.int("maxAlarmDurationMinutes", current.maxAlarmDurationMinutes),
            maxAlarmSms = obj.int("maxAlarmSms", current.maxAlarmSms),
            maxAlarmCalls = obj.int("maxAlarmCalls", current.maxAlarmCalls),
            verboseLogging = obj.bool("verboseLogging", current.verboseLogging),
            alwaysLogEvents = obj.bool("alwaysLogEvents", current.alwaysLogEvents),
        )
    }

    private fun JSONObject.mergeCameraMode(current: RecordCameraMode): RecordCameraMode {
        if (has("recordCameraMode") && !isNull("recordCameraMode")) {
            return runCatching { RecordCameraMode.valueOf(getString("recordCameraMode")) }
                .getOrDefault(current)
        }
        if (has("recordCameraFront") && !isNull("recordCameraFront")) {
            return if (runCatching { getBoolean("recordCameraFront") }.getOrDefault(false)) {
                RecordCameraMode.FRONT
            } else {
                RecordCameraMode.BACK
            }
        }
        return current
    }

    private inline fun <reified T : Enum<T>> JSONObject.enum(key: String, current: T): T {
        if (!has(key) || isNull(key)) return current
        return runCatching { enumValueOf<T>(getString(key)) }.getOrDefault(current)
    }

    private fun JSONObject.string(key: String, current: String): String {
        if (!has(key) || isNull(key)) return current
        return runCatching { getString(key) }.getOrDefault(current)
    }

    private fun JSONObject.bool(key: String, current: Boolean): Boolean {
        if (!has(key) || isNull(key)) return current
        return runCatching { getBoolean(key) }.getOrDefault(current)
    }

    private fun JSONObject.int(key: String, current: Int): Int {
        if (!has(key) || isNull(key)) return current
        return runCatching { getInt(key) }.getOrDefault(current)
    }

    private fun JSONObject.float(key: String, current: Float): Float {
        if (!has(key) || isNull(key)) return current
        return runCatching { getDouble(key).toFloat() }.getOrDefault(current)
    }

    private fun JSONObject.mergeIdlePanic(current: Long?, knownConfigIds: Set<Long>): Long? {
        if (!has("idlePanicConfigId")) return current
        if (isNull("idlePanicConfigId")) return null
        val id = runCatching { getLong("idlePanicConfigId") }.getOrNull() ?: return current
        return id.takeIf { it in knownConfigIds }
    }
}
