package com.example.androidkiosk.ui.admin

import java.net.URI

/** Restricts top-level WebView navigation to the panel and Firebase services it requires. */
object AdminPanelNavigationPolicy {
    fun isAllowed(url: String, panelUrl: String): Boolean {
        val target = runCatching { URI(url) }.getOrNull() ?: return false
        val panel = runCatching { URI(panelUrl) }.getOrNull() ?: return false
        if (!target.scheme.equals("https", ignoreCase = true)) return false

        val host = target.host?.lowercase() ?: return false
        val panelHost = panel.host?.lowercase() ?: return false
        return host == panelHost ||
            host in PANEL_AUTH_REDIRECT_HOSTS ||
            host in GOOGLE_FIREBASE_HOSTS
    }

    // Firebase email/password may return through the hosting project's auth handler.
    // Keep this explicit: arbitrary Firebase Hosting apps are not trusted destinations.
    private val PANEL_AUTH_REDIRECT_HOSTS = setOf(
        "device-streaming-ded679cd.web.app",
        "device-streaming-ded679cd.firebaseapp.com"
    )

    private val GOOGLE_FIREBASE_HOSTS = setOf(
        "identitytoolkit.googleapis.com",
        "securetoken.googleapis.com",
        "firebasestorage.googleapis.com",
        "storage.googleapis.com",
        "www.googleapis.com"
    )
}
