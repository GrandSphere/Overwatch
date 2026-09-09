package com.grandsphere.overwatch.domain.catalog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.grandsphere.overwatch.domain.model.OverwatchConfig

/**
 * Runtime (dangerous) permissions only. Normal / special permissions are not prompted here.
 */
object FeaturePermissions {
    fun notifyPermission(id: String): String? = when (id) {
        "notification", "live_notify" -> postNotificationsOrNull()
        else -> null
    }

    fun alarmPermissions(id: String): List<String> = when (id) {
        "sms" -> listOf(Manifest.permission.SEND_SMS)
        "call", "quiet_call" -> listOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
        )
        "location" -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        "record_video" -> listOf(Manifest.permission.CAMERA)
        "record_audio" -> listOf(Manifest.permission.RECORD_AUDIO)
        else -> emptyList()
    }

    fun alarmPermission(id: String): String? = alarmPermissions(id).firstOrNull()

    fun gracePermission(id: String): String? =
        if (id == "grace_notification") postNotificationsOrNull() else null

    fun needsLocation(config: OverwatchConfig): Boolean =
        "location" in config.alarmEffectIds ||
            config.safetyEffectIds.any { it == "location" && AlarmCatalog.isAllowedInSafety(it) }

    fun missingFor(context: Context, config: OverwatchConfig): Array<String> {
        val needed = linkedSetOf<String>()
        postNotificationsOrNull()?.let { needed += it }
        config.notifyEffectIds.mapNotNull { notifyPermission(it) }.forEach { needed += it }
        config.graceNotifyEffectIds.mapNotNull { gracePermission(it) }.forEach { needed += it }
        config.alarmEffectIds.flatMap { alarmPermissions(it) }.forEach { needed += it }
        config.safetyEffectIds
            .filter { AlarmCatalog.isAllowedInSafety(it) }
            .flatMap { alarmPermissions(it) }
            .forEach { needed += it }
        return needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    fun isGranted(context: Context, permission: String?): Boolean {
        if (permission == null) return true
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun missingOf(context: Context, permissions: List<String>): Array<String> =
        permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    private fun postNotificationsOrNull(): String? =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
}
