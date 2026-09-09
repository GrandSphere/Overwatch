package com.grandsphere.overwatch.ui.chrome

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit = onBackspace,
) {
    val rows = listOf("123", "456", "789", " 0<")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { c ->
                    if (c == ' ') {
                        Spacer(Modifier.size(72.dp))
                    } else if (c == '<') {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .semantics { contentDescription = "Backspace" }
                                .combinedClickable(
                                    onClick = onBackspace,
                                    onLongClick = onClear,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("⌫", fontSize = 20.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onDigit(c) },
                            modifier = Modifier
                                .size(72.dp)
                                .semantics { contentDescription = "Digit $c" },
                        ) {
                            Text(c.toString(), fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }
}
