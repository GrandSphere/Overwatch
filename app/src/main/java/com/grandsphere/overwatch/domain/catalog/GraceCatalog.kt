package com.grandsphere.overwatch.domain.catalog

object GraceCatalog {
    const val NOTIFICATION_ID = "grace_notification"
    const val NOTIFICATION_LABEL = "Grace notification"

    val options: List<Pair<String, String>> = listOf(
        NOTIFICATION_ID to NOTIFICATION_LABEL,
    )

    fun labelOf(id: String): String =
        options.find { it.first == id }?.second ?: id
}
