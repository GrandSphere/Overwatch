package com.grandsphere.overwatch.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.grandsphere.overwatch.domain.model.AppSettings
import com.grandsphere.overwatch.domain.model.AppearanceDefaults
import com.grandsphere.overwatch.domain.model.HardwareKeyOption
import com.grandsphere.overwatch.domain.model.PanicActivation
import com.grandsphere.overwatch.domain.model.RecordCameraMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("overwatch_settings")

class SettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsStore.data.map { it.toSettings() }

    suspend fun ensureDefaults() {
        // Intentionally empty: do not invent a default PIN.
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[PANIC_ACTIVATION] = next.panicActivation.name
            prefs[PANIC_HW] = next.panicHardwareKey.name
            if (next.idlePanicConfigId == null) prefs.remove(IDLE_PANIC_ID)
            else prefs[IDLE_PANIC_ID] = next.idlePanicConfigId
            prefs[LIGHT] = next.lightTheme
            prefs[BATTERY_PROMPTED] = next.batteryPrompted
            prefs[PIN_HASH] = next.pinHash.ifBlank { prefs[PIN_HASH].orEmpty() }
            prefs[DURESS] = next.duressDigit
            prefs[DURESS_PREFIX] = next.duressPrefix
            prefs[PREVENT_CLOSE] = next.preventCloseOnOverwatch
            prefs[HW_OUTSIDE] = next.hardwareKeysOutsideApp
            prefs[TURN_LOCATION] = next.turnLocationOn
            prefs[NOTIFY_TOASTS] = next.notifyToasts
            prefs[VIDEO_CLIP] = next.videoClipSeconds
            prefs[AUDIO_CLIP] = next.audioClipSeconds
            prefs[CAMERA_MODE] = next.recordCameraMode.name
            prefs.remove(CAMERA_FRONT)
            prefs[DARK_CARD] = next.darkCardArgb
            prefs[WIDGET_ARGB] = next.widgetArgb
            prefs[ACTION_ARGB] = next.actionArgb
            prefs[FONT_SCALE] = next.fontScale
            prefs[RECENT_LOC] = next.recentLocationMinutes
            prefs[RECENT_POINTS] = next.recentLocationPoints.coerceIn(1, 20)
            prefs[CONT_LOC] = next.continuousLocationMinutes
            prefs[PANIC_TWO_PIN] = next.panicOnTwoWrongPins
            prefs[FAIL_SECRET] = next.failSecretly
            prefs[FAIL_SECRET_PHRASE] = next.failSecretPhrase
            prefs[MAX_ALARM_DUR] = next.maxAlarmDurationMinutes
            prefs[MAX_ALARM_SMS] = next.maxAlarmSms
            prefs[MAX_ALARM_CALLS] = next.maxAlarmCalls
            prefs[VERBOSE_LOG] = next.verboseLogging
            prefs[ALWAYS_LOG] = next.alwaysLogEvents
        }
    }

    companion object {
        private val PANIC_ACTIVATION = stringPreferencesKey("panic_activation")
        private val PANIC_HW = stringPreferencesKey("panic_hardware")
        private val IDLE_PANIC_ID = longPreferencesKey("idle_panic_id")
        private val LIGHT = booleanPreferencesKey("light_theme")
        private val BATTERY_PROMPTED = booleanPreferencesKey("battery_prompted")
        private val PIN_HASH = stringPreferencesKey("pin_hash")
        private val DURESS = stringPreferencesKey("duress_digit")
        private val DURESS_PREFIX = booleanPreferencesKey("duress_prefix")
        private val PREVENT_CLOSE = booleanPreferencesKey("prevent_close")
        private val HW_OUTSIDE = booleanPreferencesKey("hw_keys_outside")
        private val TURN_LOCATION = booleanPreferencesKey("turn_location_on")
        private val NOTIFY_TOASTS = booleanPreferencesKey("notify_toasts")
        private val VIDEO_CLIP = intPreferencesKey("video_clip_seconds")
        private val AUDIO_CLIP = intPreferencesKey("audio_clip_seconds")
        private val CAMERA_MODE = stringPreferencesKey("record_camera_mode")
        private val CAMERA_FRONT = booleanPreferencesKey("record_camera_front")
        private val DARK_CARD = intPreferencesKey("dark_card_argb")
        private val WIDGET_ARGB = intPreferencesKey("widget_argb")
        private val ACTION_ARGB = intPreferencesKey("action_argb")
        private val FONT_SCALE = floatPreferencesKey("font_scale")
        private val RECENT_LOC = intPreferencesKey("recent_location_minutes")
        private val RECENT_POINTS = intPreferencesKey("recent_location_points")
        private val CONT_LOC = intPreferencesKey("continuous_location_minutes")
        private val PANIC_TWO_PIN = booleanPreferencesKey("panic_on_two_wrong_pins")
        private val FAIL_SECRET = booleanPreferencesKey("fail_secretly")
        private val FAIL_SECRET_PHRASE = stringPreferencesKey("fail_secret_phrase")
        private val MAX_ALARM_DUR = intPreferencesKey("max_alarm_duration_minutes")
        private val MAX_ALARM_SMS = intPreferencesKey("max_alarm_sms")
        private val MAX_ALARM_CALLS = intPreferencesKey("max_alarm_calls")
        private val VERBOSE_LOG = booleanPreferencesKey("verbose_logging")
        private val ALWAYS_LOG = booleanPreferencesKey("always_log_events")
    }
}

private fun Preferences.toSettings() = AppSettings(
    panicActivation = this[stringPreferencesKey("panic_activation")]?.let { PanicActivation.valueOf(it) }
        ?: PanicActivation.DOUBLE_TAP,
    panicHardwareKey = this[stringPreferencesKey("panic_hardware")]?.let { HardwareKeyOption.valueOf(it) }
        ?: HardwareKeyOption.NONE,
    idlePanicConfigId = this[longPreferencesKey("idle_panic_id")],
    lightTheme = this[booleanPreferencesKey("light_theme")] ?: false,
    batteryPrompted = this[booleanPreferencesKey("battery_prompted")] ?: false,
    pinHash = this[stringPreferencesKey("pin_hash")].orEmpty(),
    duressDigit = this[stringPreferencesKey("duress_digit")].orEmpty(),
    duressPrefix = this[booleanPreferencesKey("duress_prefix")] ?: false,
    preventCloseOnOverwatch = this[booleanPreferencesKey("prevent_close")] ?: false,
    hardwareKeysOutsideApp = this[booleanPreferencesKey("hw_keys_outside")] ?: false,
    turnLocationOn = this[booleanPreferencesKey("turn_location_on")] ?: false,
    notifyToasts = this[booleanPreferencesKey("notify_toasts")] ?: true,
    videoClipSeconds = this[intPreferencesKey("video_clip_seconds")] ?: 20,
    audioClipSeconds = this[intPreferencesKey("audio_clip_seconds")] ?: 20,
    recordCameraMode = resolveCameraMode(this),
    darkCardArgb = this[intPreferencesKey("dark_card_argb")] ?: AppearanceDefaults.DARK_GROUP,
    widgetArgb = this[intPreferencesKey("widget_argb")] ?: AppearanceDefaults.DARK_WIDGET,
    actionArgb = this[intPreferencesKey("action_argb")] ?: AppearanceDefaults.DARK_ACTION,
    fontScale = this[floatPreferencesKey("font_scale")] ?: 1f,
    recentLocationMinutes = this[intPreferencesKey("recent_location_minutes")] ?: 1,
    recentLocationPoints = (this[intPreferencesKey("recent_location_points")] ?: 3).coerceIn(1, 20),
    continuousLocationMinutes = this[intPreferencesKey("continuous_location_minutes")] ?: 1,
    panicOnTwoWrongPins = this[booleanPreferencesKey("panic_on_two_wrong_pins")] ?: false,
    failSecretly = this[booleanPreferencesKey("fail_secretly")] ?: false,
    failSecretPhrase = this[stringPreferencesKey("fail_secret_phrase")]?.ifBlank { "Okay" } ?: "Okay",
    maxAlarmDurationMinutes = this[intPreferencesKey("max_alarm_duration_minutes")] ?: 60,
    maxAlarmSms = this[intPreferencesKey("max_alarm_sms")] ?: 20,
    maxAlarmCalls = this[intPreferencesKey("max_alarm_calls")] ?: 10,
    verboseLogging = this[booleanPreferencesKey("verbose_logging")] ?: false,
    alwaysLogEvents = this[booleanPreferencesKey("always_log_events")] ?: false,
)

private fun resolveCameraMode(prefs: Preferences): RecordCameraMode {
    val mode = prefs[stringPreferencesKey("record_camera_mode")]
    if (mode != null) {
        return runCatching { RecordCameraMode.valueOf(mode) }.getOrDefault(RecordCameraMode.FRONT)
    }
    val legacy = prefs[booleanPreferencesKey("record_camera_front")]
    return when (legacy) {
        true -> RecordCameraMode.FRONT
        false -> RecordCameraMode.BACK
        null -> RecordCameraMode.FRONT
    }
}
