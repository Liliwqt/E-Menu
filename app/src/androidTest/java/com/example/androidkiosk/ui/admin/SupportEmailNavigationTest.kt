package com.example.androidkiosk.ui.admin

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupportEmailNavigationTest {
    private val panel = "https://touch-menu-web.online"
    private val email = "mailto:touch.support1@gmail.com?subject=Cancel&body=Branch%3A%20fixture"

    private class EmailContext(base: Context, private val available: Boolean) : ContextWrapper(base) {
        var launched: Intent? = null
        override fun startActivity(intent: Intent) {
            if (!available) throw ActivityNotFoundException()
            launched = intent // Capture only: never open an email app or send mail.
        }
    }

    private fun checkNavigation(available: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = EmailContext(instrumentation.targetContext, available)
            val view = object : WebView(context) {
                override fun getUrl(): String = "$panel/subscription/fixture"
            }
            var error: String? = null
            configurePanelWebView(view, panel, {}, { error = it })
            val request = object : WebResourceRequest {
                override fun getUrl(): Uri = Uri.parse(email)
                override fun isForMainFrame() = true
                override fun isRedirect() = false
                override fun hasGesture() = true
                override fun getMethod() = "GET"
                override fun getRequestHeaders(): Map<String, String> = emptyMap()
            }
            assertTrue(view.webViewClient.shouldOverrideUrlLoading(view, request))
            assertNull(error)
            assertEquals("$panel/subscription/fixture", view.url)
            if (available) {
                assertEquals(Intent.ACTION_SENDTO, context.launched?.action)
                assertEquals(email, context.launched?.dataString)
            } else {
                assertNull(context.launched)
            }
            view.destroy()
        }
    }

    @Test fun supportLinkOpensEmailIntentAndKeepsPortal() = checkNavigation(true)
    @Test fun missingEmailAppKeepsPortalAndDoesNotCrash() = checkNavigation(false)
}
