package com.grandsphere.overwatch.ui.chrome

import com.grandsphere.overwatch.domain.catalog.CancelCatalog
import com.grandsphere.overwatch.domain.catalog.PanicModeCatalog
import com.grandsphere.overwatch.domain.model.CrashSensitivity
import com.grandsphere.overwatch.domain.model.HardwareKeyOption
import com.grandsphere.overwatch.domain.model.NotificationUrgency
import com.grandsphere.overwatch.domain.model.PanicActivation
import com.grandsphere.overwatch.domain.model.ShakeStrength

object HintCopy {
    fun notify(id: String): String = when (id) {
        "none" -> "No reminder to check in."
        "bubble" -> "A small pop-up over other apps. Tap it to check in."
        "notification" -> "A notification when it is time to check in."
        "sound" -> "Plays a sound you pick."
        "live_notify" -> "A countdown of time left on the running notification."
        "clock_alarm" -> "Sets an alarm in the Clock app."
        "vibrate" -> "The phone vibrates."
        "flashlight" -> "Turns the torch on."
        else -> ""
    }

    fun dismiss(id: String): String = when (id) {
        "tap" -> "Tap."
        "pin" -> "Enter your PIN."
        "fingerprint", "auto_fingerprint" -> "Use your fingerprint."
        "volume_down" -> "Press volume down while Overwatch is open."
        "power_button" -> "Press the power button."
        "turnover" -> "Flip the phone."
        "shake" -> "Shake the phone."
        else -> ""
    }

    fun cancel(id: String): String = when (id) {
        CancelCatalog.SAME_AS_DISMISS -> "Use whatever you set for Dismiss."
        else -> dismiss(id)
    }

    fun alarm(id: String): String = when (id) {
        "none" -> "Nothing extra when you are safe."
        "log" -> "Writes what happened to the log in the app."
        "sms" -> "Sends an SMS to numbers you set."
        "call", "quiet_call" -> "Calls numbers you set."
        "siren" -> "Plays a sound."
        "vibrate" -> "The phone vibrates."
        "clock_alarm" -> "Sets an alarm in the Clock app."
        "location" -> "Can send location. Requires SMS or Log."
        "flashlight" -> "Turns the torch on."
        "record_video" -> "Keeps recording. Split into clips."
        "record_audio" -> "Keeps recording. Split into clips."
        "notification" -> "A notification that you need help."
        "quit" -> "Closes the app after the other alarm actions have started."
        else -> ""
    }

    fun panic(id: String): String = when (id) {
        PanicModeCatalog.NONE -> "No extra ways to trigger the alarm."
        PanicModeCatalog.POWER_BUTTON -> "Press the power button to raise the alarm."
        PanicModeCatalog.TURNOVER -> "Flip the phone to raise the alarm."
        PanicModeCatalog.SHAKE -> "Shake the phone to raise the alarm."
        PanicModeCatalog.CRASH_DETECT ->
            "Raises the alarm if the phone takes a hard jolt and then stays still."
        else -> ""
    }

    const val UNTIL_DISMISSED = "Keeps going until it is stopped."
    const val DURATION = "How long it should last."
    const val FLICKER = "Turns the torch on and off."
    const val FLICKER_ON = "How long the torch stays on."
    const val FLICKER_OFF = "How long the torch stays off."
    const val SOS = "Flashes SOS."
    const val SHOW_ACTIVE_OVERWATCH = "Show the Overwatch name on the countdown."
    const val AUTO_FINGERPRINT = "Continually show fingerprint popup."
    const val QUIET_CALL = "Tries to call without ringing this phone."
    const val INCLUDE_PANIC_INFO = "Adds how the alarm started to the SMS."
    const val SMS_MESSAGE = "Text sent in the SMS."
    const val NOTIFICATION_MESSAGE = "Text shown on the notification."
    const val URGENCY = "How strongly the notification alerts."
    const val HOLD_DURATION = "How long the phone must stay flipped."
    const val SHAKE_STRENGTH = "How hard you need to shake."
    const val SHAKE_COUNT = "How many shakes are needed."
    const val POWER_TAPS = "How many power presses are needed."
    const val CRASH_G = "How hard a jolt must be."
    const val CRASH_IGNORE = "How long to ignore bouncing after the jolt."
    const val CRASH_STILLNESS = "How long the phone must stay still after that."
    const val SCHEDULE_SINGLE = "Check in once within a time you set."
    const val SCHEDULE_REPEAT = "Check in every so often."
    const val SCHEDULE_BY_TIME = "Check in before a clock time you set."
    const val SCHEDULE_INTERVAL = "Stop after this much time."
    const val SCHEDULE_COUNT = "Stop after this many check ins."
    const val CHECK_IN_EVERY = "Time between check ins."
    const val NUMBER_OF_TIMES = "How many check ins."
    const val CHECK_IN_BEFORE = "Check in before this clock time."
    const val CRASH_HIGH = "Easiest to trip."
    const val CRASH_MEDIUM = "A medium jolt."
    const val CRASH_LOW = "Hardest to trip."
    const val SHAKE_LOW = "A light shake."
    const val SHAKE_MEDIUM = "A medium shake."
    const val SHAKE_HIGH = "A hard shake."
    const val URGENCY_LOW = "A weaker alert."
    const val URGENCY_DEFAULT = "A normal alert."
    const val URGENCY_HIGH = "A stronger alert."
    const val SET_PIN = "Set the PIN. 4 to 8 digits."

    const val PANIC_ACTIVATION = "How you start Panic from the app bar."
    const val PANIC_HARDWARE = "Power or volume keys while Overwatch is open."
    const val SETTINGS_PIN = "PIN used to check in and to cancel."
    const val ENDS_STARTS = "Which end of the PIN the fake digit uses."
    const val PANIC_TWO_WRONG = "Raise the alarm after two wrong PINs."
    const val SECRET_PHRASE = "The button shown when a secret alarm is running."
    const val MAX_DURATION = "The max duration for a single alarm."
    const val MAX_SMS = "The max SMS for a single alarm."
    const val MAX_CALLS = "The max calls for a single alarm."
    const val CLIP_LENGTH = "Clip length before a new one is started."
    const val CAMERA_BACK = "Record with the back camera."
    const val CAMERA_FRONT = "Record with the front camera."
    const val CAMERA_BOTH = "Record with both cameras."
    const val RECENT_SAMPLE = "How often a location is stored for Recent."
    const val RECENT_POINTS = "How many recent location points are sent."
    const val CONTINUOUS_SMS = "How often current location is sent."
    const val LIGHT_THEME = "Light or dark colours."
    const val FONT_SIZE = "Text size in the app."
    const val NOTIFY_TOASTS = "Short banners when an Overwatch starts."
    const val VERBOSE_LOG = "Writes extra detail to the verbose log."
    const val ALWAYS_LOG = "Write log lines even without Log selected."
    const val PANIC_SINGLE = "Tap once."
    const val PANIC_DOUBLE = "Tap twice."
    const val PANIC_LONG = "Press and hold."
    const val HW_OFF = "No hardware panic key."
    const val HW_VOL_UP_X2 = "Double press volume up while Overwatch is open."
    const val HW_VOL_UP = "Press volume up while Overwatch is open."
    const val FONT_SMALLER = "Smaller text."
    const val FONT_DEFAULT = "Default text size."
    const val FONT_LARGER = "Larger text."
    const val PERM_NOTIFICATIONS = "Allow notifications."
    const val PERM_SMS = "Allow sending SMS."
    const val PERM_CALLS = "Allow placing calls."
    const val PERM_PHONE_STATE = "Needed for call handling."
    const val PERM_CONTACTS = "Allow picking numbers from contacts."
    const val PERM_LOCATION = "Allow location."
    const val PERM_CAMERA = "Allow video recording."
    const val PERM_MIC = "Allow audio recording."
    const val PERM_BATTERY = "Helps keep Overwatch running."
    const val PERM_EXACT = "Needed for on-time check ins."
    const val PERM_DND = "Allow alerts during Do Not Disturb."
    const val PERM_BUBBLES = "Allow the Bubble pop-up."
    const val PERM_FSI = "Allow full-screen alerts."
    const val PERM_LIVE = "Allow the Live notify countdown."
    const val PERM_BG_LOCATION = "Location while the screen is locked. Not required on every device."
    const val EXPORT_OW = "Save Overwatches to a file."
    const val SHARE_OW = "Share the Overwatches file."
    const val IMPORT_OW = "Load Overwatches from a file."
    const val CLEAR_OW = "Delete all Overwatches."
    const val EXPORT_SETTINGS = "Save settings to a file."
    const val SHARE_SETTINGS = "Share the settings file."
    const val IMPORT_SETTINGS = "Load settings from a file."
    const val EXPORT_LOG = "Save the log to a file."
    const val SHARE_LOG = "Share the log."
    const val EXPORT_VERBOSE = "Save the verbose log to a file."
    const val SHARE_VERBOSE = "Share the verbose log."

    fun panicActivation(act: PanicActivation): String = when (act) {
        PanicActivation.SINGLE_TAP -> PANIC_SINGLE
        PanicActivation.DOUBLE_TAP -> PANIC_DOUBLE
        PanicActivation.LONG_PRESS -> PANIC_LONG
    }

    fun hardwareKey(opt: HardwareKeyOption): String = when (opt) {
        HardwareKeyOption.NONE -> HW_OFF
        HardwareKeyOption.VOLUME_UP_DOUBLE -> HW_VOL_UP_X2
        HardwareKeyOption.VOLUME_UP -> HW_VOL_UP
        else -> HW_OFF
    }

    fun permissionHint(label: String): String = when (label) {
        "Notifications" -> PERM_NOTIFICATIONS
        "SMS" -> PERM_SMS
        "Phone calls" -> PERM_CALLS
        "Phone state" -> PERM_PHONE_STATE
        "Contacts" -> PERM_CONTACTS
        "Precise location" -> PERM_LOCATION
        "Camera" -> PERM_CAMERA
        "Microphone" -> PERM_MIC
        "Battery optimisation" -> PERM_BATTERY
        "Exact alarms" -> PERM_EXACT
        "Do Not Disturb" -> PERM_DND
        "Bubbles" -> PERM_BUBBLES
        "Full-screen intents" -> PERM_FSI
        "Live Updates" -> PERM_LIVE
        "Background location (not critical)" -> PERM_BG_LOCATION
        else -> ""
    }

    fun urgency(option: NotificationUrgency): String = when (option) {
        NotificationUrgency.LOW -> URGENCY_LOW
        NotificationUrgency.DEFAULT -> URGENCY_DEFAULT
        NotificationUrgency.HIGH -> URGENCY_HIGH
    }

    fun shakeStrength(option: ShakeStrength): String = when (option) {
        ShakeStrength.LOW -> SHAKE_LOW
        ShakeStrength.MEDIUM -> SHAKE_MEDIUM
        ShakeStrength.HIGH -> SHAKE_HIGH
    }

    fun crashSensitivity(option: CrashSensitivity): String = when (option) {
        CrashSensitivity.HIGH -> CRASH_HIGH
        CrashSensitivity.MEDIUM -> CRASH_MEDIUM
        CrashSensitivity.LOW -> CRASH_LOW
    }
}
