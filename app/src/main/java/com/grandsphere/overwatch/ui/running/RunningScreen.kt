package com.grandsphere.overwatch.ui.running

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.grandsphere.overwatch.domain.catalog.AlarmCatalog
import com.grandsphere.overwatch.domain.catalog.CancelCatalog
import com.grandsphere.overwatch.domain.catalog.DismissCatalog
import com.grandsphere.overwatch.domain.model.AppState
import com.grandsphere.overwatch.domain.model.Submode
import com.grandsphere.overwatch.runtime.OverwatchNotifications
import com.grandsphere.overwatch.runtime.VerboseLog
import com.grandsphere.overwatch.ui.chrome.FingerprintAuth
import com.grandsphere.overwatch.ui.chrome.PinPad
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun RunningScreen(
    state: AppState.Overwatch,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onTap: () -> Unit,
    onFingerprint: () -> Unit,
    onConfirmCancel: () -> Unit,
    onCancelWithPin: (String) -> Boolean,
    onCancelWithTap: () -> Boolean,
    onCancelWithFingerprint: () -> Boolean,
    onCancelAborted: () -> Unit = {},
    cancelLabel: String = "Cancel",
) {
    val usesPin = "pin" in state.config.dismissEffectIds
    val usesTap = "tap" in state.config.dismissEffectIds
    val usesFingerprint = DismissCatalog.usesFingerprint(state.config.dismissEffectIds)
    val usesAutoFingerprint = DismissCatalog.usesAutoFingerprint(state.config.dismissEffectIds)
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val alerting = state.submode == Submode.AlarmMode
    val secret = state.secretAlarm
    val covert = state.config.covert
    var cancelProof by remember { mutableStateOf(false) }
    var cancelPin by remember { mutableStateOf("") }
    var promptBusy by remember { mutableStateOf(false) }
    var resumeEpoch by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        var stopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> stopped = true
                Lifecycle.Event.ON_START -> {
                    if (stopped) {
                        stopped = false
                        resumeEpoch++
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun promptFingerprint(
        title: String = "Fingerprint",
        onSuccess: () -> Unit,
        onDismiss: () -> Unit = {},
    ) {
        if (activity == null || promptBusy) return
        promptBusy = true
        FingerprintAuth.prompt(
            activity,
            title = title,
            onSuccess = {
                promptBusy = false
                onSuccess()
            },
            onDismiss = {
                promptBusy = false
                onDismiss()
            },
        )
    }

    fun abortCancelProof() {
        cancelProof = false
        cancelPin = ""
        VerboseLog.d("UI", "cancel aborted")
    }

    val cancelUsesAutoFingerprint = CancelCatalog.usesAutoFingerprint(
        state.config.cancelEffectIds,
        state.config.dismissEffectIds,
    )
    val autoFpPeriodMs = minOf(60_000L, (state.config.intervalMs / 3L).coerceAtLeast(1_000L))

    LaunchedEffect(
        state.submode,
        usesAutoFingerprint,
        cancelUsesAutoFingerprint,
        cancelProof,
        covert,
        alerting,
        resumeEpoch,
        autoFpPeriodMs,
    ) {
        if (covert || alerting) return@LaunchedEffect
        val dismissWindow = state.submode == Submode.NotifyMode ||
            state.submode == Submode.DismissMode ||
            state.submode == Submode.GraceMode
        val wantDismissAuto = usesAutoFingerprint && !cancelProof && dismissWindow
        val wantCancelAuto = cancelProof && cancelUsesAutoFingerprint
        if (!wantDismissAuto && !wantCancelAuto) return@LaunchedEffect

        // Immediate prompt on enter / fresh focus; then throttle.
        while (isActive) {
            if (!promptBusy) {
                if (wantCancelAuto) {
                    promptFingerprint(
                        title = "Cancel Overwatch",
                        onSuccess = {
                            if (onCancelWithFingerprint()) {
                                cancelProof = false
                                cancelPin = ""
                            }
                        },
                    )
                } else {
                    promptFingerprint(onSuccess = onFingerprint)
                }
            }
            delay(autoFpPeriodMs)
        }
    }

    val cancelUsesPin = CancelCatalog.usesPin(state.config.cancelEffectIds, state.config.dismissEffectIds)
    val cancelUsesTap = CancelCatalog.usesTap(state.config.cancelEffectIds, state.config.dismissEffectIds)
    val cancelUsesFingerprint = CancelCatalog.usesFingerprint(
        state.config.cancelEffectIds,
        state.config.dismissEffectIds,
    )
    val cancelUsesVolumeDown = CancelCatalog.usesVolumeDown(
        state.config.cancelEffectIds,
        state.config.dismissEffectIds,
    )

    val showPin = if (cancelProof) cancelUsesPin else !secret && !alerting && usesPin
    val showTap = if (cancelProof) cancelUsesTap else !secret && !alerting && usesTap
    val showFingerprint = if (cancelProof) cancelUsesFingerprint else !secret && !alerting && usesFingerprint
    val pinValue = if (cancelProof) cancelPin else state.pinBuffer

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val heading = when {
            secret -> null
            cancelProof -> "Cancel?"
            alerting -> null
            else -> "Are you okay"
        }
        if (heading != null) {
            Text(heading, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(16.dp))
        if (alerting) {
            if (!covert && !secret) {
                Text(
                    "Alerting...",
                    fontSize = 42.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                AlarmCatalog.chipIds(state.config.alarmEffectIds).forEach { id ->
                    Text(
                        AlarmCatalog.labelOf(id),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                        fontSize = 15.sp,
                    )
                }
            }
        } else if (!secret) {
            Text(
                OverwatchNotifications.formatMmSs(state.remainingMs),
                fontSize = 42.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics {
                    contentDescription = remainingDescription(state.remainingMs)
                },
            )
        }
        if (state.errorFlag && !covert && !secret) {
            Text(state.errorMessage ?: "Error Mode", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        if (showPin) {
            Text("•".repeat(pinValue.length).ifEmpty { "PIN" }, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            PinPad(
                onDigit = { d ->
                    if (cancelProof) {
                        val (next, done) = com.grandsphere.overwatch.OverwatchApp.from(context)
                            .engine.cancelPinDigit(d, cancelPin)
                        cancelPin = next
                        if (done) {
                            cancelProof = false
                            cancelPin = ""
                        }
                    } else {
                        onDigit(d)
                    }
                },
                onBackspace = {
                    if (cancelProof) cancelPin = cancelPin.dropLast(1) else onBackspace()
                },
                onClear = {
                    if (cancelProof) cancelPin = "" else onClear()
                },
            )
        }
        if (showTap) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (cancelProof) {
                        if (onCancelWithTap()) cancelProof = false
                    } else {
                        onTap()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("I'm OK") }
        }
        if (showFingerprint) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (cancelProof) {
                        promptFingerprint(
                            title = "Cancel Overwatch",
                            onSuccess = {
                                if (onCancelWithFingerprint()) {
                                    cancelProof = false
                                    cancelPin = ""
                                }
                            },
                        )
                    } else {
                        promptFingerprint(onSuccess = onFingerprint)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Fingerprint") }
        }
        if (cancelProof && cancelUsesVolumeDown) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Volume down (in-app)",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            )
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = {
                if (state.config.cancelEffectIds.isEmpty()) {
                    VerboseLog.d("UI", "cancel confirm (no cancel effects)")
                    onConfirmCancel()
                } else if (cancelProof) {
                    abortCancelProof()
                    onCancelAborted()
                } else {
                    VerboseLog.d("UI", "cancel proof start")
                    cancelProof = true
                    cancelPin = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (cancelProof) "Back" else cancelLabel)
        }
    }
}

private fun remainingDescription(ms: Long): String {
    val total = kotlin.math.abs(ms) / 1000
    val m = total / 60
    val s = total % 60
    return if (ms < 0) "Overdue $m minutes $s seconds" else "$m minutes $s seconds remaining"
}
