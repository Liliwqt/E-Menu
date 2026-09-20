package com.example.androidkiosk.admin

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Logs admin unlock attempts for audit purposes. */
@Singleton
class UnlockAttemptLogger @Inject constructor() {

    /** Log an unlock attempt. */
    fun logAttempt(method: UnlockMethod, success: Boolean) {
        val statusStr = if (success) "SUCCESS" else "FAILED"

        Timber.i(
            "Unlock attempt [%s] via %s",
            statusStr,
            method.name
        )
    }
}

/** How the admin PIN dialog was triggered. */
enum class UnlockMethod {
    /** Visible Admin button on the menu. */
    ADMIN_BUTTON,

    /** Secret corner tap (5 taps in top-right corner). */
    CORNER_TAP
}
