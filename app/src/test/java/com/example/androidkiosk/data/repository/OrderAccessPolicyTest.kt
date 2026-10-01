package com.example.androidkiosk.data.repository
import org.junit.Assert.*
import org.junit.Test
class OrderAccessPolicyTest {
    @Test fun unreadLifecycleNeverGrantsAccess() {
        assertNull(effectiveOrderExpiry(999L, null, false))
        assertNull(effectiveOrderExpiry(null, "active", true))
    }
    @Test fun closureAndFailedReadsBlockCheckout() {
        for (state in listOf("closing", "deleting", "deleted", "unreadable")) assertEquals(0L, effectiveOrderExpiry(999L, state, true))
    }
    @Test fun recoveryDoesNotExtendEntitlement() {
        assertEquals(999L, effectiveOrderExpiry(999L, "active", true))
        assertEquals(0L, effectiveOrderExpiry(0L, "active", true))
        assertEquals(999L, effectiveOrderExpiry(999L, null, true))
        assertEquals(999L, effectiveOrderExpiry(999L, "inactivity_grace", true))
    }
    @Test fun billingMarkerBlocksStaleEntitlement() {
        assertEquals(0L, effectiveOrderExpiry(999L, "active", true, true))
    }
    @Test fun warningDatesAreExplicitAndMissingLegacyStateIsSilent() {
        assertNull(lifecycleNoticeText(null, null))
        assertTrue(lifecycleNoticeText("warning_pending", null)!!.contains("not scheduled"))
        val message = lifecycleNoticeText("inactivity_grace", 0L)!!
        assertTrue(message.contains("Jan 1, 1970 8:00 AM Philippine time"))
        assertTrue(lifecycleNoticeText("closing", 0L)!!.contains("ordering is paused"))
    }
}
