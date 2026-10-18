package com.grandsphere.overwatch.ui.import

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class OverwatchImportLine(
    val key: Int,
    val originalName: String,
    var selected: Boolean,
    var name: String,
    val needsRename: Boolean,
)

@Composable
fun ImportOverwatchesDialog(
    lines: List<OverwatchImportLine>,
    error: String?,
    onLineChange: (Int, OverwatchImportLine) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Overwatches") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (error != null) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                lines.forEach { line ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = line.selected,
                            onCheckedChange = {
                                onLineChange(line.key, line.copy(selected = it))
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            if (line.needsRename) {
                                OutlinedTextField(
                                    value = line.name,
                                    onValueChange = { onLineChange(line.key, line.copy(name = it)) },
                                    label = { Text("Name for ${line.originalName}") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(line.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
