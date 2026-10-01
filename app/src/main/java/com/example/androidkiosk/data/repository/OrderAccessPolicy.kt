package com.example.androidkiosk.data.repository

/** Unread lifecycle state never grants checkout. Missing records support legacy branches. */
fun effectiveOrderExpiry(entitlementExpiry: Long?, lifecycleStatus: String?, lifecycleLoaded: Boolean, billingBlocked: Boolean = false): Long? {
    if (!lifecycleLoaded || entitlementExpiry == null) return null
    if (billingBlocked) return 0L
    if (lifecycleStatus in setOf("closing", "deleting", "deleted", "unreadable")) return 0L
    return entitlementExpiry
}


fun lifecycleNoticeText(status: String?, deleteAt: Long?): String? {
    val message = when (status) {
        "warning_pending" -> "Inactive branch: the owner warning is awaiting delivery. Deletion is not scheduled yet."
        "inactivity_grace" -> "Inactive branch: ask the owner to download records or resume activity before deletion."
        "closing", "deleting", "deleted" -> "Branch closure: ordering is paused. Ask the owner to open Records and access."
        else -> return null
    }
    if (deleteAt == null) return message
    val deadline = java.time.Instant.ofEpochMilli(deleteAt).atZone(java.time.ZoneId.of("Asia/Manila"))
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", java.util.Locale.ENGLISH))
    return "$message Deadline: $deadline Philippine time."
}
