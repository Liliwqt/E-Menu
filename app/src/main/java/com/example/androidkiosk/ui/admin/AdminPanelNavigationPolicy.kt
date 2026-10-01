package com.example.androidkiosk.ui.admin

import java.net.URI

/** Restricts top-level WebView navigation to the panel and Firebase services it requires. */
object AdminPanelNavigationPolicy {
    fun isSupportEmailRequest(url: String, sourceUrl: String?, panelUrl: String,
                              isMainFrame: Boolean, hasGesture: Boolean): Boolean {
        if (!isMainFrame || !hasGesture || sourceUrl == null || url.length > 8192) return false
        val source = runCatching { URI(sourceUrl) }.getOrNull() ?: return false
        val panel = runCatching { URI(panelUrl) }.getOrNull() ?: return false
        if (!source.scheme.equals("https", true) || !panel.scheme.equals("https", true)
            || source.host == null || !source.host.equals(panel.host, true)) return false
        val target = runCatching { URI(url) }.getOrNull() ?: return false
        if (!target.scheme.equals("mailto", true) || target.rawFragment != null) return false
        val parts = target.rawSchemeSpecificPart.split('?', limit = 2)
        if (!parts[0].equals("touch.support1@gmail.com", true)) return false
        return parts.size == 1 || parts[1].split('&').all { field ->
            val pair = field.split('=', limit = 2)
            pair.size == 2 && pair[0] in setOf("subject", "body") &&
                (pair[0] != "subject" || runCatching {
                    val value = java.net.URLDecoder.decode(pair[1], "UTF-8")
                    '\r' !in value && '\n' !in value
                }.getOrDefault(false))
        }
    }

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
