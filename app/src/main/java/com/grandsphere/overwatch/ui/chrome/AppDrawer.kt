package com.grandsphere.overwatch.ui.chrome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppDrawer(
    selectedHome: Boolean,
    selectedAbout: Boolean,
    selectedHelp: Boolean,
    selectedSettings: Boolean,
    onHome: () -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    onQuit: () -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Overwatch",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(16.dp),
            )
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text("Home") },
                selected = selectedHome,
                onClick = onHome,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                label = { Text("About") },
                selected = selectedAbout,
                onClick = onAbout,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null) },
                label = { Text("Help") },
                selected = selectedHelp,
                onClick = onHelp,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Settings") },
                selected = selectedSettings,
                onClick = onSettings,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                label = { Text("Quit") },
                selected = false,
                onClick = onQuit,
            )
        }
    }
}
