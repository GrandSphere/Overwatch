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
