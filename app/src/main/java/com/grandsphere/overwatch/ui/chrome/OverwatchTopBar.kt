package com.grandsphere.overwatch.ui.chrome

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandsphere.overwatch.domain.model.PanicActivation

/**
 * Panic stays far right. Status-bar inset is applied by [com.grandsphere.overwatch.ui.OverwatchRoot]
 * so these controls are never drawn under the system clock.
 */
@Composable
fun OverwatchTopBar(
    title: String,
    showMenu: Boolean,
    onMenu: () -> Unit,
    panicActivation: PanicActivation,
    onPanic: () -> Unit,
    navigation: @Composable () -> Unit = {},
) {
    var lastTap by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val talkBack = remember(context) {
        val am = context.getSystemService(AccessibilityManager::class.java)
        am.isEnabled && am.isTouchExplorationEnabled
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMenu) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        }
        navigation()
        Text(
            title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        if (talkBack) {
            TextButton(onClick = onPanic) { Text("Panic") }
        } else {
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Panic"
                    }
                    .pointerInput(panicActivation) {
                        detectTapGestures(
                            onLongPress = {
                                if (panicActivation == PanicActivation.LONG_PRESS) onPanic()
                            },
                            onTap = {
                                when (panicActivation) {
                                    PanicActivation.SINGLE_TAP -> onPanic()
                                    PanicActivation.DOUBLE_TAP -> {
                                        val now = System.currentTimeMillis()
                                        if (now - lastTap < 450) {
                                            lastTap = 0L
                                            onPanic()
                                        } else {
                                            lastTap = now
                                        }
                                    }
                                    PanicActivation.LONG_PRESS -> Unit
                                }
                            },
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("Panic", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
            }
        }
    }
}
