package com.grandsphere.overwatch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.grandsphere.overwatch.domain.model.AppearanceDefaults

@Composable
fun OverwatchTheme(
    light: Boolean,
    cardArgb: Int = AppearanceDefaults.DARK_GROUP,
    actionArgb: Int = AppearanceDefaults.DARK_ACTION,
    content: @Composable () -> Unit,
) {
    val black = Color.Black
    val actionContainer = Color(actionArgb)
    val onAction = if (actionContainer.luminance() > 0.5f) Color(0xFF381E72) else Color(0xFFE8DEF8)
    val scheme = if (light) {
        lightColorScheme(
            primary = Color(0xFF1A1C1E),
            background = Color(0xFFF7F7F2),
            onBackground = Color(0xFF1A1C1E),
            surfaceVariant = Color(cardArgb),
            secondaryContainer = actionContainer,
            onSecondaryContainer = onAction,
        )
    } else {
        darkColorScheme(
            primary = Color(0xFFE8EAED),
            onPrimary = Color(0xFF000000),
            background = black,
            onBackground = Color(0xFFE8EAED),
            surface = black,
            onSurface = Color(0xFFE8EAED),
            surfaceVariant = Color(cardArgb),
            surfaceContainer = black,
            surfaceContainerLow = black,
            surfaceContainerLowest = black,
            surfaceContainerHigh = black,
            surfaceContainerHighest = black,
            secondaryContainer = actionContainer,
            onSecondaryContainer = onAction,
            error = Color(0xFFFFB4AB),
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
