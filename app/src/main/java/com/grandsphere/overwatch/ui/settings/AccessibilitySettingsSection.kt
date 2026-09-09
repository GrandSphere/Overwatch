package com.grandsphere.overwatch.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.grandsphere.overwatch.domain.model.AppSettings
import com.grandsphere.overwatch.runtime.OverwatchAccessibilityService

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccessibilitySettingsSection(
    settings: AppSettings,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var a11yOn by remember { mutableStateOf(OverwatchAccessibilityService.isEnabled(context)) }
    var a11yHint by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yOn = OverwatchAccessibilityService.isEnabled(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Hint") {
                        a11yHint = true
                        true
                    },
                )
            }
            .combinedClickable(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onLongClick = { a11yHint = true },
            )
            .padding(vertical = 8.dp),
    ) {
        Text("Accessibility mode", modifier = Modifier.weight(1f))
        Text(
            if (a11yOn) "On" else "Off",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
    if (a11yOn) {
        Spacer(Modifier.height(8.dp))
        Text("Accessibility", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        SettingsSwitchRow(
            label = "Prevent close on Overwatch",
            checked = settings.preventCloseOnOverwatch,
            onChecked = { on -> onSettings { it.copy(preventCloseOnOverwatch = on) } },
        )
        SettingsSwitchRow(
            label = "Hardware keys outside app",
            checked = settings.hardwareKeysOutsideApp,
            onChecked = { on -> onSettings { it.copy(hardwareKeysOutsideApp = on) } },
        )
        SettingsSwitchRow(
            label = "Turn location on",
            checked = settings.turnLocationOn,
            onChecked = { on -> onSettings { it.copy(turnLocationOn = on) } },
        )
    }
    if (a11yHint) {
        AlertDialog(
            onDismissRequest = { a11yHint = false },
            text = { Text("enables advanced options") },
            confirmButton = {
                TextButton(onClick = { a11yHint = false }) { Text("OK") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (onLongClick != null) {
                    customActions = listOf(
                        CustomAccessibilityAction("Hint") {
                            onLongClick()
                            true
                        },
                    )
                }
            },
    ) {
        Text(
            label,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = { },
                            onLongClick = onLongClick,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
        Switch(checked, onCheckedChange = onChecked)
    }
}
