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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PayMongoSignupNavigationTest {
    private val panel = "https://touch-menu-web.online"
    private val signup = "https://dashboard.paymongo.com/signup?email=merchant%40example.test&invitation_code=lr_fixture"
    private class BrowserContext(base: Context, private val available: Boolean) : ContextWrapper(base) {
        var launched: Intent? = null
        override fun startActivity(intent: Intent) {
            if (!available) throw ActivityNotFoundException()
            launched = intent // Capture only; never contact PayMongo.
        }
    }
    private fun check(available: Boolean, gesture: Boolean = true) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = BrowserContext(instrumentation.targetContext, available)
            val view = object : WebView(context) {
                override fun getUrl() = "$panel/payments/branch-fixture"
            }
            var error: String? = null
            configurePanelWebView(view, panel, {}, { error = it })
            val request = object : WebResourceRequest {
                override fun getUrl() = Uri.parse(signup)
                override fun isForMainFrame() = true
                override fun isRedirect() = false
                override fun hasGesture() = gesture
                override fun getMethod() = "GET"
                override fun getRequestHeaders(): Map<String, String> = emptyMap()
            }
            assertTrue(view.webViewClient.shouldOverrideUrlLoading(view, request))
            assertEquals("$panel/payments/branch-fixture", view.url)
            if (available && gesture) {
                assertEquals(Intent.ACTION_VIEW, context.launched?.action)
                assertEquals(signup, context.launched?.dataString)
                assertNull(error)
            } else assertNull(context.launched)
            if (!gesture) assertNotNull(error) else assertNull(error)
            view.destroy()
        }
    }
    @Test fun signupOpensBrowserAndPreservesPortal() = check(true)
    @Test fun noBrowserPreservesPortal() = check(false)
    @Test fun backgroundSignupNavigationIsBlocked() = check(true, false)
}
