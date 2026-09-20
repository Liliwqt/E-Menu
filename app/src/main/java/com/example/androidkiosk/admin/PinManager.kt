package com.example.androidkiosk.admin

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Validates the kiosk admin PIN and persists brute-force throttling state. */
@Singleton
@SuppressLint("ApplySharedPref", "UseKtx")
class PinManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    val failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    private val lockoutUntil: Long
        get() = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)

    internal var clock: () -> Long = System::currentTimeMillis

    @Synchronized
    fun validatePin(input: String): Boolean {
        if (isLockedOut()) {
            Timber.w("PIN validation rejected — lockout active")
            return false
        }

        val isValid = MessageDigest.isEqual(
            sha256(input),
            DEFAULT_PIN_HASH
        )
        if (isValid) {
            clearThrottle()
            Timber.i("PIN validated successfully")
            return true
        }

        val attempts = failedAttempts + 1
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts)
        if (attempts >= MAX_ATTEMPTS) {
            editor.putLong(KEY_LOCKOUT_UNTIL, clock() + LOCKOUT_DURATION_MS)
            Timber.w("PIN lockout activated")
        } else {
            Timber.w("Invalid PIN attempt")
        }
        // Security state must reach disk before a kiosk process can be terminated.
        editor.commit()
        return false
    }

    @Synchronized
    fun isLockedOut(): Boolean {
        if (lockoutUntil == 0L) return false
        if (clock() < lockoutUntil) return true
        clearThrottle()
        return false
    }

    fun remainingLockoutMs(): Long {
        if (!isLockedOut()) return 0L
        return (lockoutUntil - clock()).coerceAtLeast(0L)
    }

    private fun clearThrottle() {
        // A successful PIN or expired lockout must also survive an immediate process exit.
        prefs.edit()
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_UNTIL)
            .commit()
    }

    private fun sha256(input: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))

    companion object {
        /** SHA-256 hash of the default maintenance PIN. */
        private val DEFAULT_PIN_HASH = byteArrayOf(
            0x03, 0xac.toByte(), 0x67, 0x42, 0x16, 0xf3.toByte(), 0xe1.toByte(), 0x5c,
            0x76, 0x1e, 0xe1.toByte(), 0xa5.toByte(), 0xe2.toByte(), 0x55, 0xf0.toByte(), 0x67,
            0x95.toByte(), 0x36, 0x23, 0xc8.toByte(), 0xb3.toByte(), 0x88.toByte(), 0xb4.toByte(), 0x45,
            0x9e.toByte(), 0x13, 0xf9.toByte(), 0x78, 0xd7.toByte(), 0xc8.toByte(), 0x46, 0xf4.toByte()
        )

        const val MAX_ATTEMPTS = 3
        const val LOCKOUT_DURATION_MS = 60_000L

        private const val PREFS_FILE = "device_security_state"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
    }
}
