package com.grandsphere.overwatch.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandsphere.overwatch.domain.model.OverwatchConfig

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IdleHomeScreen(
    configs: List<OverwatchConfig>,
    onOpen: (Long) -> Unit,
    onShare: (OverwatchConfig) -> Unit,
    onDuplicate: (OverwatchConfig) -> Unit,
    onDelete: (OverwatchConfig) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<OverwatchConfig?>(null) }
    var blockedDefault by remember { mutableStateOf(false) }
    var menuConfig by remember { mutableStateOf<OverwatchConfig?>(null) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(configs, key = { it.id }) { config ->
            Box(Modifier.fillMaxWidth()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onOpen(config.id) },
                            onLongClick = { menuConfig = config },
                        ),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(config.name, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                config.scheduleLabel(),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                        if (config.covert) {
                            Text(
                                "Covert",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
                DropdownMenu(
                    expanded = menuConfig?.id == config.id,
                    onDismissRequest = { menuConfig = null },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            menuConfig = null
                            onShare(config)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = {
                            menuConfig = null
                            if (config.isDefault()) blockedDefault = true
                            else onDuplicate(config)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuConfig = null
                            if (config.isDefault()) blockedDefault = true
                            else pendingDelete = config
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${target.name}?") },
            text = { Text("This Overwatch will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Back") }
            },
        )
    }
    if (blockedDefault) {
        AlertDialog(
            onDismissRequest = { blockedDefault = false },
            title = { Text("Default") },
            text = { Text("Default is used for Idle Panic and cannot be duplicated or deleted.") },
            confirmButton = {
                TextButton(onClick = { blockedDefault = false }) { Text("OK") }
            },
        )
    }
}
