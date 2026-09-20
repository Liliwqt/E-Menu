package com.example.androidkiosk.admin

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinManagerInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearState() {
        context.getSharedPreferences("device_security_state", 0).edit().clear().commit()
    }

    @After
    fun tearDown() = clearState()

    @Test
    fun lockoutSurvivesManagerRecreationAndExpiresAfterSixtySeconds() {
        var now = 10_000L
        val firstManager = PinManager(context)
        firstManager.clock = { now }
        repeat(PinManager.MAX_ATTEMPTS) { assertFalse(firstManager.validatePin("0000")) }
        assertTrue(firstManager.isLockedOut())
        assertEquals(PinManager.LOCKOUT_DURATION_MS, firstManager.remainingLockoutMs())

        val recreatedManager = PinManager(context)
        recreatedManager.clock = { now }
        assertTrue(recreatedManager.isLockedOut())

        now += PinManager.LOCKOUT_DURATION_MS
        assertTrue(recreatedManager.validatePin("1234"))
        assertFalse(recreatedManager.isLockedOut())
    }

    @Test
    fun successfulAttemptClearsPersistedFailures() {
        val manager = PinManager(context)
        assertFalse(manager.validatePin("0000"))
        assertEquals(1, manager.failedAttempts)

        assertTrue(manager.validatePin("1234"))

        assertEquals(0, PinManager(context).failedAttempts)
    }
}
