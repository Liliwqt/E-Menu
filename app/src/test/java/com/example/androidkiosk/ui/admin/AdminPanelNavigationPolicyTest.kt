package com.example.androidkiosk.ui.admin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminPanelNavigationPolicyTest {
    private val panelUrl = "https://touch-menu-web.online"

    @Test
    fun `allows panel and Firebase endpoints over HTTPS`() {
        assertTrue(AdminPanelNavigationPolicy.isAllowed(panelUrl, panelUrl))
        assertTrue(AdminPanelNavigationPolicy.isAllowed("https://device-streaming-ded679cd.web.app", panelUrl))
        assertTrue(AdminPanelNavigationPolicy.isAllowed("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword", panelUrl))
        assertTrue(AdminPanelNavigationPolicy.isAllowed("https://firebasestorage.googleapis.com/v0/b/menu/o/image", panelUrl))
    }

    @Test
    fun `blocks cleartext files and arbitrary hosts`() {
        assertFalse(AdminPanelNavigationPolicy.isAllowed("http://touch-menu-web.online", panelUrl))
        assertFalse(AdminPanelNavigationPolicy.isAllowed("file:///android_asset/index.html", panelUrl))
        assertFalse(AdminPanelNavigationPolicy.isAllowed("https://example.com", panelUrl))
        assertFalse(AdminPanelNavigationPolicy.isAllowed("https://untrusted.web.app", panelUrl))
    }
}
