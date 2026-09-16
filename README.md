# Overwatch

Overwatch is an Android check in app. You start it when you want someone to notice if you go quiet. It asks you to show you are okay. If you do not, it can raise the alarm.
## License

Overwatch is licensed under the GNU General Public License v3.0 (GPL-3.0).

Copyright © 2026 GrandSphere Studios See the LICENSE file for details.

## Description
You can configure different Overwatches depending on what you will be doing
and how you want to check in or alert people.

* Schedule: how often you need to check in.
* Notify: how the app reminds you to check in.
* Dismiss: how you show you are okay.
* Alarm: what the app does if you still do not check in, including calling people, 
        sending an SMS, and sending location.
* Panic: how you will manually trigger the alarm.
* Safety: what happens when you are safe again.
* Cancel: how you stop an Overwatch on purpose.
    
Please try every feature you plan to use before you rely on it.

## Potential Issues
Location:
* Android can close or block this app in the background without warning. 
Please be careful and do not assume it will always keep running.
* Location in Alarm Mode keeps the GPS radio working and uses more battery.
* Location updates on Android might be blocked when the device is locked. As a result, 
location data might only be usable when the device is unlocked.
* On some devices, turning on Background location in Settings can help location keep working while the screen       is locked. It is not required on every device.

SMS/Call:
* On some devices Android restricts call and SMS functionality and the app cannot request the permissions.\
    A potential workaround might be:\
    Settings → Apps → Overwatch → ⋮ → Allow restricted settings\
    Permissions → SMS → Allow

Sensors:
* All of these panic methods require a currently running Overwatch.
* Some devices already assign actions to sensors such as shake, power button and flipping.
* Please test these carefully and only use if you have verified that it works the way you expect it to.
* These can be very prone to false alarms.

## Features
You can use more than one option together.

Schedule:
* Single: check in once within a time you set.
* Repeat: check in every so often. You can stop after a number of times, or after an interval.
* By Time: check in before a clock time you set.
* Grace period: how long you can take to check in after it is due.
* Covert mode: hide app actions.

Notify:
* None: the app does not remind you to check in.
* Bubble: a small pop-up over other apps. Tap it to check in. It stays until you close it.
* Notification: a notification when it is time to check in. You can set the text.
* Sound: plays a sound you pick, for a time you set.
* Live notify: a countdown of time left on the running notification. You can show the Overwatch name.
* Set Alarm: sets an alarm in the Clock app for when the check in is due. Alarms are set on the closest minute.
* Vibrate: the phone vibrates for a time you set.
* Flashlight: turns the torch on. You can set how long, and whether it stays on, flickers, or uses SOS.

Dismiss:
* Tap: tap to show you are okay.
* PIN: enter your PIN to show you are okay. In Settings you can set a Fake PIN digit. If an entered PIN starts or ends with that digit, it raises the alarm.
* Fingerprint: use your fingerprint. Auto fingerprint asks on its own. Without auto, you tap first.
* Volume down (in-app): press volume down while Overwatch is open.
* Power Button: press the power button.
* Turnover: flip the phone.
* Shake: shake the phone.

Alarm:
* Log: writes what happened to the log in the app.
* SMS: sends an SMS to numbers you set.
* Call: calls numbers you set. Quiet call is best effort.
* Sound: plays a sound. You can let it keep playing until it is dismissed.
* Vibrate: the phone vibrates.
* Set Alarm: sets an alarm in the Clock app. Alarms are set on the closest minute.
* Location: can send recent locations, and can keep sending your current location. Location requires SMS or Log.
* Flashlight: turns the torch on. You can set how long, and whether it stays on, flickers, or uses SOS.
* Record video: keeps recording. Split into clips.
* Record audio: keeps recording. Split into clips.
* Notification: a notification that you need help. You can set the text.
* Quit: closes the app after the other alarm actions have started.

Panic:
* None: you do not add extra ways to trigger the alarm.
* Power Button: press the power button to raise the alarm.
* Turnover: flip the phone to raise the alarm.
* Shake: shake the phone to raise the alarm.
* Crash Detect: raises the alarm if the phone takes a hard jolt and then stays still.

Safety:
These run when you are safe again. They work like the Alarm options of the same name.
* None, Log, SMS, Call, Sound, Vibrate, Location, Flashlight, Notification, and Quit.

Cancel:
* PIN, Fingerprint, Volume down (in-app), Power Button, Turnover, and Shake: same idea as Dismiss, used to stop an Overwatch on purpose.
* Same as dismiss: use whatever you set for Dismiss.

Covert mode:
Hides app actions so the Overwatch is less obvious. Notifications use bland wording. Sound, vibration, flashlight, and Clock alarms are skipped.

Fail secretly:
This is in Settings. A wrong PIN looks as if it worked, but it secretly raises the alarm.

## Technical Information

    Language: Kotlin
    UI Framework: Jetpack Compose
    Architecture: MVVM
    Database: Room
    Minimum SDK: Android 8.0 (API 26)
    Target SDK: Android 15 (API 35)
    License: GNU GPL-3.0

This project was developed with assistance from AI coding tools. Review and test carefully before relying on the app.
## Getting Started
### Prerequisites

    Android Studio (e.g., Koala or later)
    JDK 17
    Kotlin 2.0.21
    Gradle 8.11.1
    Device/Emulator running Android 8.0 (API 26) or higher

### Build

    Clone the repository:

    git clone https://github.com/GrandSphere/Overwatch
    cd Overwatch

    Open the project in Android Studio and sync Gradle to download dependencies.

    Configure signing for release builds:
        Create a keystore.jks file with android studio.

    Build and run:
        Select a device or emulator (API 26+).
        Run via Android Studio

## Dependencies

Key dependencies (see app/build.gradle.kts):

    Jetpack Compose (UI)
    Room (database)
    DataStore (preferences)
    Google Play services Location
    AndroidX Biometric

## Contributing

Contributions are welcome. To contribute:

    Fork the repository.
    Create a feature branch (git checkout -b feature/your-feature).
    Commit changes (git commit -m "Add your feature").
    Push to the branch (git push origin feature/your-feature).
    Open a pull request.

Follow Kotlin Coding Conventions and ensure compliance with the GPL-3.0 license.
## Known Issues

Report issues at https://github.com/GrandSphere/Overwatch/issues.
