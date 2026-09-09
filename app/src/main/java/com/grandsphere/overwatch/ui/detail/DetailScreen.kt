package com.grandsphere.overwatch.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandsphere.overwatch.domain.catalog.AlarmCatalog
import com.grandsphere.overwatch.domain.catalog.CancelCatalog
import com.grandsphere.overwatch.domain.catalog.DismissCatalog
import com.grandsphere.overwatch.domain.catalog.NotifyCatalog
import com.grandsphere.overwatch.domain.catalog.PanicModeCatalog
import com.grandsphere.overwatch.domain.model.OverwatchConfig

@Composable
fun DetailScreen(
    config: OverwatchConfig,
    onEnable: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(config.name, fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Line("Schedule", config.scheduleLabel())
            Line("Notify Mode", notifyLabel(config))
            Line("Dismiss Mode", dismissLabel(config))
            Line(
                "Alarm Mode",
                effectsLabel(
                    config.alarmEffectIds,
                    locationRecent = config.locationRecent,
                    locationContinuous = config.locationContinuous,
                    flashlightMode = config.alarmFlashlightMode,
                    includeLocationFlags = true,
                ),
            )
            Line("Panic Mode", panicLabel(config))
            Line("Safety Mode", safetyLabel(config))
            Line("Cancel Mode", cancelLabel(config))
            Line("Covert", if (config.covert) "on" else "off")
            ContactPreviewSections(config)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onEnable) { Text("Enable") }
            OutlinedButton(onClick = onEdit) { Text("Edit") }
        }
    }
}

private fun plainLabel(id: String, catalogLabel: String): String = when (id) {
    "volume_down" -> "Volume down"
    "fingerprint", "auto_fingerprint" -> "Fingerprint"
    else -> catalogLabel
}

private fun notifyLabel(config: OverwatchConfig): String {
    if (config.notifyEffectIds.isEmpty()) return "—"
    return config.notifyEffectIds.joinToString { id ->
        if (id == "flashlight") {
            flashlightLabel(config.notifyFlashlightMode)
        } else {
            plainLabel(id, NotifyCatalog.labelOf(id))
        }
    }
}

private fun dismissLabel(config: OverwatchConfig): String {
    if (config.dismissEffectIds.isEmpty()) return "—"
    return DismissCatalog.chipIds(config.dismissEffectIds).joinToString { id ->
        plainLabel(id, DismissCatalog.labelOf(id))
    }
}

private fun cancelLabel(config: OverwatchConfig): String {
    if (config.cancelEffectIds.isEmpty()) return "—"
    return CancelCatalog.chipIds(config.cancelEffectIds).joinToString { id ->
        plainLabel(id, CancelCatalog.labelOf(id))
    }
}

private fun panicLabel(config: OverwatchConfig): String {
    val ids = PanicModeCatalog.sanitize(config.panicEffectIds)
    if (ids.isEmpty() || ids == listOf(PanicModeCatalog.NONE)) {
        return PanicModeCatalog.labelOf(PanicModeCatalog.NONE)
    }
    return ids.joinToString { PanicModeCatalog.labelOf(it) }
}

private fun flashlightLabel(mode: String): String = when (OverwatchConfig.normalizeFlashlightMode(mode)) {
    OverwatchConfig.FLASHLIGHT_FLICKER -> "Flashlight (Flicker)"
    OverwatchConfig.FLASHLIGHT_SOS -> "Flashlight (SOS)"
    else -> "Flashlight"
}

private fun locationLabel(recent: Boolean, continuous: Boolean): String {
    val flags = buildList {
        if (recent) add("Recent")
        if (continuous) add("Continuous")
    }
    return if (flags.isEmpty()) "Location" else "Location (${flags.joinToString(", ")})"
}

private fun effectsLabel(
    ids: List<String>,
    locationRecent: Boolean,
    locationContinuous: Boolean,
    flashlightMode: String,
    includeLocationFlags: Boolean = true,
): String {
    val parts = AlarmCatalog.chipIds(ids).map { id ->
        when {
            id == "location" && includeLocationFlags ->
                locationLabel(locationRecent, locationContinuous)
            id == "flashlight" -> flashlightLabel(flashlightMode)
            else -> plainLabel(id, AlarmCatalog.labelOf(id))
        }
    }
    return parts.joinToString()
}

private fun safetyLabel(config: OverwatchConfig): String {
    val ids = config.safetyEffectIds.filter { AlarmCatalog.isAllowedInSafety(it) }
    if (ids.isEmpty() || ids == listOf("none")) {
        return if (config.safetyOnCancel) "— · Trigger on Cancel" else "—"
    }
    val effects = effectsLabel(
        ids,
        locationRecent = false,
        locationContinuous = false,
        flashlightMode = config.safetyFlashlightMode,
        includeLocationFlags = false,
    )
    return if (config.safetyOnCancel) "$effects · Trigger on Cancel" else effects
}

@Composable
private fun Line(label: String, value: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
        Text(value.ifBlank { "—" }, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
    }
}

@Composable
private fun ContactPreviewSections(config: OverwatchConfig) {
    val callNumbers = (config.callContacts() + config.safetyCallContacts()).distinct()
    val smsNumbers = (config.smsContacts() + config.safetySmsContacts()).distinct()
    if (callNumbers.isNotEmpty()) {
        ContactSection(title = "Call", numbers = callNumbers)
    }
    if (smsNumbers.isNotEmpty()) {
        ContactSection(title = "SMS", numbers = smsNumbers)
    }
}

@Composable
private fun ContactSection(
    title: String,
    numbers: List<String>,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
        numbers.forEach { number ->
            Text(
                number,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
            )
        }
    }
}
