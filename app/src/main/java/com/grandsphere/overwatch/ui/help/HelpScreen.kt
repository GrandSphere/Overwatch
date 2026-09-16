package com.grandsphere.overwatch.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandsphere.overwatch.ui.chrome.CollapsibleSection

@Composable
fun HelpScreen() {
    val body = MaterialTheme.colorScheme.onBackground
    val muted = body.copy(alpha = 0.7f)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Overwatch", fontSize = 24.sp, color = body)
        HorizontalDivider()
        Text(
            "Overwatch is a check in app. You start it when you want someone to notice if you go quiet. It asks you to show you are okay. If you do not, it can raise the alarm.",
            fontSize = 16.sp,
            color = body,
        )
        Text(
            "You can configure different Overwatches depending on what you will be doing and how you want to check in or alert people.",
            fontSize = 16.sp,
            color = body,
        )
        Text(
            "For each Overwatch you configure the following required modes:",
            fontSize = 16.sp,
            color = body,
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HelpBullet("Schedule: how often you need to check in.", muted)
            HelpBullet("Notify: how the app reminds you to check in.", muted)
            HelpBullet("Dismiss: how you show you are okay.", muted)
            HelpBullet("Alarm: what the app does if you still do not check in.", muted)
            HelpBullet("Panic: how you will manually trigger the alarm.", muted)
            HelpBullet("Safety: what happens when you are safe.", muted)
            HelpBullet("Cancel: how you stop an Overwatch on purpose.", muted)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Please try every feature you plan to use before you rely on it.",
            fontSize = 16.sp,
            color = body,
        )
        CollapsibleSection(
            title = "Features",
            hint = "What each option does",
        ) {
            Text("You can use more than one option together.", fontSize = 16.sp, color = muted)
            Text("Schedule:", fontSize = 16.sp, color = body)
            HelpBullet("Single: check in once within a time you set.", muted)
            HelpBullet("Repeat: check in every so often. You can stop after a number of times, or after an interval.", muted)
            HelpBullet("By Time: check in before a clock time you set.", muted)
            HelpBullet("Grace period: how long you can take to check in after it is due.", muted)
            HelpBullet("Covert mode: hide app actions.", muted)
            Spacer(Modifier.height(4.dp))
            Text("Notify:", fontSize = 16.sp, color = body)
            HelpBullet("None: the app does not remind you to check in.", muted)
            HelpBullet("Bubble: a small pop-up over other apps. Tap it to check in. It stays until you close it.", muted)
            HelpBullet("Notification: a notification when it is time to check in. You can set the text.", muted)
            HelpBullet("Sound: plays a sound you pick, for a time you set.", muted)
            HelpBullet("Live notify: a countdown of time left on the running notification. You can show the Overwatch name.", muted)
            HelpBullet("Set Alarm: sets an alarm in the Clock app for when the check in is due. Alarms are set on the closest minute.", muted)
            HelpBullet("Vibrate: the phone vibrates for a time you set.", muted)
            HelpBullet("Flashlight: turns the torch on. You can set how long, and whether it stays on, flickers, or uses SOS.", muted)
            Spacer(Modifier.height(4.dp))
            Text("Dismiss:", fontSize = 16.sp, color = body)
            HelpBullet("Tap: tap to show you are okay.", muted)
            HelpBullet("PIN: enter your PIN to show you are okay. In Settings you can set a Fake PIN digit. If an entered PIN starts or ends with that digit, it raises the alarm.", muted)
            HelpBullet("Fingerprint: use your fingerprint. Auto fingerprint asks on its own. Without auto, you tap first.", muted)
            HelpBullet("Volume down (in-app): press volume down while Overwatch is open.", muted)
            HelpBullet("Power Button: press the power button.", muted)
            HelpBullet("Turnover: flip the phone.", muted)
            HelpBullet("Shake: shake the phone.", muted)
            Spacer(Modifier.height(4.dp))
            Text("Alarm:", fontSize = 16.sp, color = body)
            HelpBullet("Log: writes what happened to the log in the app.", muted)
            HelpBullet("SMS: sends an SMS to numbers you set.", muted)
            HelpBullet("Call: calls numbers you set. Quiet call is best effort.", muted)
            HelpBullet("Sound: plays a sound. You can let it keep playing until it is dismissed.", muted)
            HelpBullet("Vibrate: the phone vibrates.", muted)
            HelpBullet("Set Alarm: sets an alarm in the Clock app. Alarms are set on the closest minute.", muted)
            HelpBullet("Location: can send recent locations, and can keep sending your current location. Location requires SMS or Log.", muted)
            HelpBullet("Flashlight: turns the torch on. You can set how long, and whether it stays on, flickers, or uses SOS.", muted)
            HelpBullet("Record video: keeps recording. Split into clips.", muted)
            HelpBullet("Record audio: keeps recording. Split into clips.", muted)
            HelpBullet("Notification: a notification that you need help. You can set the text.", muted)
            HelpBullet("Quit: closes the app after the other alarm actions have started.", muted)
            Spacer(Modifier.height(4.dp))
            Text("Panic:", fontSize = 16.sp, color = body)
            HelpBullet("None: you do not add extra ways to trigger the alarm.", muted)
            HelpBullet("Power Button: press the power button to raise the alarm.", muted)
            HelpBullet("Turnover: flip the phone to raise the alarm.", muted)
            HelpBullet("Shake: shake the phone to raise the alarm.", muted)
            HelpBullet("Crash Detect: raises the alarm if the phone takes a hard jolt and then stays still.", muted)
            Spacer(Modifier.height(4.dp))
            Text("Safety:", fontSize = 16.sp, color = body)
            Text(
                "These run when you are safe again. They work like the Alarm options of the same name.",
                fontSize = 16.sp,
                color = muted,
            )
            HelpBullet("None, Log, SMS, Call, Sound, Vibrate, Location, Flashlight, Notification, and Quit.", muted)
            Spacer(Modifier.height(4.dp))
            Text("Cancel:", fontSize = 16.sp, color = body)
            HelpBullet("PIN, Fingerprint, Volume down (in-app), Power Button, Turnover, and Shake: same idea as Dismiss, used to stop an Overwatch on purpose.", muted)
            HelpBullet("Same as dismiss: use whatever you set for Dismiss.", muted)
            Spacer(Modifier.height(4.dp))
            Text("Covert mode:", fontSize = 16.sp, color = body)
            Text(
                "Hides app actions so the Overwatch is less obvious. Notifications use bland wording. Sound, vibration, flashlight, and Clock alarms are skipped.",
                fontSize = 16.sp,
                color = muted,
            )
            Spacer(Modifier.height(4.dp))
            Text("Fail secretly:", fontSize = 16.sp, color = body)
            Text(
                "This is in Settings. A wrong PIN looks as if it worked, but it secretly raises the alarm.",
                fontSize = 16.sp,
                color = muted,
            )
        }
        CollapsibleSection(
            title = "Troubleshooting",
            hint = "Background, location, and SMS/call permission issues",
        ) {
            Text("Location:", fontSize = 16.sp, color = body)
            Text(
                "Android can close or block this app in the background without warning. Please be careful and do not assume it will always keep running.",
                fontSize = 16.sp,
                color = muted,
            )
            Text(
                "Location in Alarm keeps the GPS radio working and uses more battery.",
                fontSize = 16.sp,
                color = muted,
            )
            Text(
                "Location updates on Android might be blocked when the device is locked. As a result, location data might only be usable when the device is unlocked.",
                fontSize = 16.sp,
                color = muted,
            )
            Text(
                "On some devices, turning on Background location in Settings can help location keep working while the screen is locked. It is not required on every device.",
                fontSize = 16.sp,
                color = muted,
            )
            Spacer(Modifier.height(4.dp))
            Text("SMS/Call:", fontSize = 16.sp, color = body)
            Text(
                "On some devices Android restricts call and SMS functionality and the app cannot request the permissions.",
                fontSize = 16.sp,
                color = muted,
            )
            Text(
                "A potential workaround might be:",
                fontSize = 16.sp,
                color = muted,
            )
            Text(
                "Settings → Apps → Overwatch → ⋮ → Allow restricted settings",
                fontSize = 16.sp,
                color = muted,
            )
            Text(
                "Permissions → SMS → Allow",
                fontSize = 16.sp,
                color = muted,
            )
            Spacer(Modifier.height(4.dp))
            Text("Sensors:", fontSize = 16.sp, color = body)
            Text(
                "All of these panic methods require a currently running Overwatch.",
                fontSize = 16.sp,
                color = muted,
            )
            Text(
                "Some devices already assign actions to sensors such as shake, power button and flipping.",
                fontSize = 16.sp,
                color = muted,
            )
            Text(
                "Please test these carefully and only use if you have verified that it works the way you expect it to.",
                fontSize = 16.sp,
                color = muted,
            )
            Text(
                "These can be very prone to false alarms.",
                fontSize = 16.sp,
                color = muted,
            )
        }
    }
}

@Composable
private fun HelpBullet(text: String, color: Color) {
    Text("• $text", fontSize = 16.sp, color = color)
}
