package com.grandsphere.overwatch.runtime



import com.grandsphere.overwatch.domain.model.AppState

import com.grandsphere.overwatch.domain.model.OverwatchConfig

import java.text.SimpleDateFormat

import java.util.Date

import java.util.Locale



object AlarmPayload {

    fun formatTime(epochMs: Long): String {

        if (epochMs <= 0L) return ""

        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))

    }



    fun formatTimeHm(epochMs: Long): String {

        if (epochMs <= 0L) return ""

        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))

    }



    fun userMessage(config: OverwatchConfig, override: String? = null): String {

        val fromOverride = override?.trim().orEmpty()

        if (fromOverride.isNotEmpty()) return fromOverride

        val fromConfig = config.alarmSmsBody.trim()

        if (fromConfig.isNotEmpty()) return fromConfig

        return "I need help"

    }



    /**

     * @param includeDiagnostics SMS uses [OverwatchConfig.sendTriggerMode]; log always true for alarm payloads.

     */

    fun build(

        config: OverwatchConfig,

        includeMessage: Boolean,

        includeDiagnostics: Boolean,

        enabledAtEpochMs: Long,

        alarmAtEpochMs: Long,

        triggerLabel: String,

        lastCheckInAtEpochMs: Long = 0L,

        locationLines: String = "",

        messageOverride: String? = null,

    ): String = buildString {

        if (includeMessage) {

            append(userMessage(config, messageOverride))

        }

        if (includeDiagnostics) {

            val enableLine = listOfNotNull(

                formatTimeHm(enabledAtEpochMs).ifBlank { null },

                config.name,

            ).joinToString(" ")

            if (enableLine.isNotBlank()) {

                if (isNotEmpty()) append('\n')

                append(enableLine)

            }

            if (lastCheckInAtEpochMs > 0L) {

                val checkInLine = listOfNotNull(

                    formatTimeHm(lastCheckInAtEpochMs).ifBlank { null },

                    "Last check-in",

                ).joinToString(" ")

                if (checkInLine.isNotBlank()) {

                    if (isNotEmpty()) append('\n')

                    append(checkInLine)

                }

            }

            val trigger = triggerLabel.ifBlank { "No check-in" }

            val alarmLine = listOfNotNull(

                formatTimeHm(alarmAtEpochMs).ifBlank { null },

                trigger,

            ).joinToString(" ")

            if (alarmLine.isNotBlank()) {

                if (isNotEmpty()) append('\n')

                append(alarmLine)

            }

        }

        if (locationLines.isNotBlank()) {

            if (isNotEmpty()) append('\n')

            append(locationLines)

        }

    }



    fun fromRunning(

        running: AppState.Overwatch?,

        config: OverwatchConfig,

        includeMessage: Boolean,

        includeDiagnostics: Boolean,

        locationLines: String = "",

        messageOverride: String? = null,

    ): String = build(

        config = config,

        includeMessage = includeMessage,

        includeDiagnostics = includeDiagnostics,

        enabledAtEpochMs = running?.enabledAtEpochMs ?: 0L,

        alarmAtEpochMs = running?.alarmAtEpochMs ?: System.currentTimeMillis(),

        triggerLabel = running?.alarmTriggerLabel.orEmpty().ifBlank { "No check-in" },

        lastCheckInAtEpochMs = running?.lastCheckInAtEpochMs ?: 0L,

        locationLines = locationLines,

        messageOverride = messageOverride,

    )

}


