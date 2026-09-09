# Overwatch

Overwatch is a check in app. You start it when you want someone to notice if you go quiet. It asks you to show you are okay. If you do not, it can raise the alarm.
## License

Overwatch is licensed under the GNU General Public License v3.0 (GPL-3.0).

Copyright © 2026 GrandSphere Studios See the LICENSE file for details.
## Features
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

## Known Issues
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
