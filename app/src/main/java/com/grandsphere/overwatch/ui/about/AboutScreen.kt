package com.grandsphere.overwatch.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.grandsphere.overwatch.BuildConfig

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val icon = remember {
        context.packageManager.getApplicationIcon(context.packageName).toBitmap().asImageBitmap()
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(
            bitmap = icon,
            contentDescription = "Overwatch",
            modifier = Modifier.size(88.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Overwatch", fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Grand Sphere Studios",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Version: ${BuildConfig.VERSION_NAME}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text("License:", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            AboutLink(
                title = "GNU General Public License 3",
                subtitle = "(GPLv3)",
                url = "https://github.com/GrandSphere/Overwatch/blob/master/LICENSE",
            )
            Spacer(Modifier.height(16.dp))
            Text("Github:", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            AboutLink(
                title = "Github Repository",
                url = "https://github.com/GrandSphere/Overwatch",
            )
            AboutLink(
                title = "Latest Release",
                url = "https://github.com/GrandSphere/Overwatch/releases/latest",
            )
            Spacer(Modifier.height(16.dp))
            Text("Donate:", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            AboutLink(title = "Ko-fi", url = "https://ko-fi.com/grandspherestudios")
            AboutLink(title = "Liberapay", url = "https://liberapay.com/GrandSphere")
        }
    }
}

@Composable
private fun AboutLink(title: String, url: String, subtitle: String? = null) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.primary)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.primary)
            }
        }
        Icon(
            Icons.Default.OpenInNew,
            contentDescription = "Open link",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}
