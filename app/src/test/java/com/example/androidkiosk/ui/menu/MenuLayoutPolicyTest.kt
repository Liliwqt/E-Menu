package com.example.androidkiosk.ui.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuLayoutPolicyTest {
    @Test fun phonesEnterPortraitAndTabletsChoose() {
        assertFalse(MenuLayoutPolicy.isTablet(599))
        assertEquals(UIMode.PORTRAIT, MenuLayoutPolicy.initialMode(599))
        assertTrue(MenuLayoutPolicy.isTablet(600))
        assertNull(MenuLayoutPolicy.initialMode(600))
    }

    @Test fun tabletChooserIsWideButRegistrationAndPhoneStayPortrait() {
        assertTrue(MenuLayoutPolicy.useLandscape(true, true, null))
        assertTrue(MenuLayoutPolicy.useLandscape(true, true, UIMode.CURRENT))
        assertTrue(MenuLayoutPolicy.useLandscape(true, true, UIMode.NEW_HORIZONTAL))
        assertFalse(MenuLayoutPolicy.useLandscape(true, true, UIMode.PORTRAIT))
        assertFalse(MenuLayoutPolicy.useLandscape(true, false, null))
        assertFalse(MenuLayoutPolicy.useLandscape(false, true, UIMode.CURRENT))
    }

    @Test fun narrowOrLargeTextUsesReadableSingleColumn() {
        assertEquals(1, MenuLayoutPolicy.portraitColumns(320, 1f))
        assertEquals(2, MenuLayoutPolicy.portraitColumns(390, 1f))
        assertEquals(1, MenuLayoutPolicy.portraitColumns(390, 2f))
    }
}
