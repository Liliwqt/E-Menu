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

    @Test
    fun `support email needs a user gesture in the trusted main frame`() {
        val url = "mailto:touch.support1@gmail.com?subject=Cancel&body=Branch%3A%20one%0ATwo"
        assertTrue(AdminPanelNavigationPolicy.isSupportEmailRequest(url, "$panelUrl/subscription/one", panelUrl, true, true))
        assertFalse(AdminPanelNavigationPolicy.isSupportEmailRequest(url, panelUrl, panelUrl, true, false))
        assertFalse(AdminPanelNavigationPolicy.isSupportEmailRequest(url, panelUrl, panelUrl, false, true))
        assertFalse(AdminPanelNavigationPolicy.isSupportEmailRequest(url, "https://untrusted.web.app", panelUrl, true, true))
        assertFalse(AdminPanelNavigationPolicy.isSupportEmailRequest(url, null, panelUrl, true, true))
    }

    @Test
    fun `support email cannot add recipients or inject subject headers`() {
        for (url in listOf("mailto:other@example.com", "mailto:touch.support1@gmail.com,other@example.com",
            "mailto:touch.support1@gmail.com?bcc=other@example.com", "mailto:touch.support1@gmail.com?subject=Cancel%0Abcc%3Aother@example.com",
            "intent://touch.support1@gmail.com", "mailto:touch.support1@gmail.com#fragment")) {
            assertFalse(url, AdminPanelNavigationPolicy.isSupportEmailRequest(url, panelUrl, panelUrl, true, true))
        }
        assertFalse(AdminPanelNavigationPolicy.isAllowed("mailto:touch.support1@gmail.com", panelUrl))
    }
}
