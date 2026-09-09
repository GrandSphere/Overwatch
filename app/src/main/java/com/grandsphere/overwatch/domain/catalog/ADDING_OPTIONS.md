# Adding options to Overwatch modes

Room stores string **ids**, never Kotlin enums. The editor `+` menus read `Catalog.all`. Runtime looks up those ids in CuePlayer, AlarmDispatcher, or the engine.

## Notify Mode (cues)

File: `NotifyCatalog.kt`

1. Create an object implementing `NotifyEffect` (`id`, `label`, optional `conflictsWith`).
2. Append it to `NotifyCatalog.all`.
3. If it needs a runtime permission, add it in `FeaturePermissions.notifyPermission`.
4. If it should do something at cue time, handle the id in `CuePlayer.play` (and Covert filtering there). Popup also starts `CuePopupActivity` from `OverwatchApp`.
5. Clock alarm is scheduled from `ClockAlarm` when the id is `clock_alarm`.

## Dismiss Mode (proof)

File: `DismissCatalog.kt`

1. Create an object implementing `DismissEffect`.
2. Append it to `DismissCatalog.all`.
3. Wire success in `OverwatchEngine` (`tapDismiss`, `onVolumeDown`, `fingerprintSuccess`, PIN via `proofOk`) and the running UI (`RunningScreen`).
4. Hardware keys stay in-app unless a future AccessibilityService feeds `RestrictedHardware`.

## Grace Mode

File: `GraceCatalog.kt`

Grace length is on the Overwatch row. Proof reuses Dismiss Mode methods. `Grace notification` is reserved; do not fire a cue yet. To add a real grace cue later, register it here and call it from `enterGrace`.

## Alarm Mode (actions)

File: `AlarmCatalog.kt`

1. Create an object implementing `AlarmEffect`.
2. Append it to `AlarmCatalog.all`.
3. Add covert/conflict rules in `AlarmCatalog.conflicts` if needed.
4. If it needs a runtime permission, add it in `FeaturePermissions.alarmPermissions` (asked when the user taps `+`, and again on Enable/Panic if the saved config already has it).
5. Handle the id in `AlarmDispatcher.dispatch`. SMS uses `alarmSmsBody` and all contacts. Calls use `CallFailover` (next number if the first fails). `location` is a stub in `LocationSender` — do not request GPS until that is implemented.

## After adding an option

Rebuild. Existing saved Overwatches keep their old id lists; they pick up the new choice only when the user adds it in the editor.
