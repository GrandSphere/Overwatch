package com.grandsphere.overwatch.ui.chrome

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun HoldHintDialog(text: String?, onDismiss: () -> Unit) {
    if (text == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.holdHintLabel(onHint: () -> Unit): Modifier = this
    .semantics {
        customActions = listOf(
            CustomAccessibilityAction("Hint") {
                onHint()
                true
            },
        )
    }
    .combinedClickable(onClick = { }, onLongClick = onHint)

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.holdHintOrClick(onClick: () -> Unit, onHint: () -> Unit): Modifier = this
    .semantics {
        customActions = listOf(
            CustomAccessibilityAction("Hint") {
                onHint()
                true
            },
        )
    }
    .combinedClickable(onClick = onClick, onLongClick = onHint)

fun Modifier.hintSemantics(onHint: () -> Unit): Modifier = this.semantics {
    customActions = listOf(
        CustomAccessibilityAction("Hint") {
            onHint()
            true
        },
    )
}

fun Modifier.watchHoldHint(onHint: () -> Unit): Modifier = this
    .hintSemantics(onHint)
    .pointerInput(onHint) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation()
            }
            if (up == null) onHint()
        }
    }

@Composable
fun HintText(
    text: String,
    hint: String,
    onHint: (String) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text,
        color = color,
        fontSize = fontSize,
        modifier = modifier.holdHintLabel { onHint(hint) },
    )
}

@Composable
fun HintFilterChip(
    selected: Boolean,
    label: String,
    hint: String,
    onHint: (String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                modifier = Modifier.watchHoldHint { onHint(hint) },
            )
        },
        modifier = modifier,
    )
}

@Composable
fun HintMenuItem(
    label: String,
    hint: String,
    onHint: (String) -> Unit,
    onClick: () -> Unit,
) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .holdHintOrClick(onClick = onClick, onHint = { onHint(hint) })
            .padding(horizontal = 12.dp, vertical = 12.dp),
        fontSize = 16.sp,
    )
}

@Composable
fun hintFieldLabel(
    text: String,
    hint: String,
    onHint: (String) -> Unit,
): @Composable () -> Unit = {
    Text(text, modifier = Modifier.watchHoldHint { onHint(hint) })
}
