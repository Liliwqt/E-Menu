package com.example.androidkiosk.ui.admin

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import timber.log.Timber

/**
 * Shared, hardened WebView hosting for the embedded E-Menu web app.
 *
 * Used by both:
 *  - [AdminPanelScreen] (PIN‑unlocked dashboard), and
 *  - the new first‑run shell (registration → setup → dashboard).
 *
 * It applies the strict navigation policy, disables downloads/file access,
 * and injects the [DeviceWebBridge] so the web app can request menu mode.
 *
 * @param url initial URL to load.
 * @param injectBridge when true, exposes `window.AndroidKiosk` to page scripts.
 * @param onEnterMenuMode invoked when the web app asks to enter menu mode,
 *   receiving the workspace company/branch IDs handed over by the web app.
 * @param onWebViewCreated optional callback giving the hosting screen a handle
 *   to the active WebView (used for back-navigation).
 */
@Composable
fun DeviceWebView(
    url: String,
    modifier: Modifier = Modifier,
    injectBridge: Boolean = true,
    onEnterMenuMode: (companyId: String, branchId: String) -> Unit = { _, _ -> },
    onWebViewCreated: (WebView) -> Unit = {}
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // A load that exceeds this duration without onPageFinished is almost certainly
    // stuck (e.g. old WebView provider renderer crash), so surface a retry instead
    // of leaving an endless spinner on screen.
    val loadTimeoutMs = remember { 15_000L }
    val loadTimeoutHandle = remember { mutableStateOf<android.os.Handler?>(null) }
    val retryCount = remember { mutableStateOf(0) }

    fun resetTimeout() {
        loadTimeoutHandle.value?.removeCallbacksAndMessages(null)
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (isLoading) {
                // Auto-retry once in case the renderer was transiently killed
                // (old WebView providers do this on first load), then surface a
                // manual Retry button so the device isn't stuck indefinitely.
                if (retryCount.value == 0) {
                    retryCount.value = 1
                    Timber.w("WebView load stalled — auto-retrying once")
                    webView?.reload()
                    resetTimeout()
                } else {
                    errorMessage = "The page is taking too long to load. Tap Retry to try again."
                }
            }
        }, loadTimeoutMs)
        loadTimeoutHandle.value = handler
    }

    fun retry() {
        errorMessage = null
        isLoading = true
        retryCount.value = 0
        loadUrlSafely(webView, url)
        resetTimeout()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).also { created ->
                    webView = created
                    onWebViewCreated(created)
                    configurePanelWebView(
                        webView = created,
                        panelUrl = url,
                        onLoadingChanged = { isLoading = it },
                        onError = { errorMessage = it }
                    )
                    if (injectBridge) {
                        created.addJavascriptInterface(
                            DeviceWebBridge(
                                onEnterMenuMode = onEnterMenuMode,
                                deviceUidProvider = {
                                    // Read the current anonymous Firebase UID at
                                    // call time so it's fresh after sign-in.
                                    try {
                                        com.google.firebase.auth.FirebaseAuth
                                            .getInstance().currentUser?.uid.orEmpty()
                                    } catch (e: Exception) {
                                        ""
                                    }
                                }
                            ),
                            "AndroidKiosk"
                        )
                    }
                    created.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: android.view.View) {
                            loadUrlSafely(created, url)
                            resetTimeout()
                        }
                        override fun onViewDetachedFromWindow(v: android.view.View) {}
                    })
                    loadUrlSafely(created, url)
                    resetTimeout()
                }
            },
            onRelease = { view ->
                // The composable is leaving composition: safe to destroy now.
                loadTimeoutHandle.value?.removeCallbacksAndMessages(null)
                view.stopLoading()
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                view.destroy()
            }
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        errorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Button(onClick = ::retry) {
                    Text("Retry")
                }
            }
        }
    }

    // NOTE: WebView destroy must happen via AndroidView's onRelease (below),
    // NOT a DisposableEffect keyed on `webView`. Keying on the changing state
    // re-runs the effect whenever the view reference updates, destroying the
    // still-needed WebView and causing "Application attempted to call on a
    // destroyed WebView" on the next load.
}

/** Loads [target] once the WebView is attached & ready. */
private fun loadUrlSafely(view: WebView?, target: String) {
    if (view == null) return
    if (view.isAttachedToWindow) {
        runCatching { view.loadUrl(target) }
            .onFailure { Timber.e(it, "loadUrl failed while attached") }
    } else {
        view.post {
            if (view.isAttachedToWindow) {
                runCatching { view.loadUrl(target) }
                    .onFailure { Timber.e(it, "loadUrl failed after attach") }
            } else {
                Timber.w("WebView not attached yet; will retry via onAttachedToWindow")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configurePanelWebView(
    webView: WebView,
    panelUrl: String,
    onLoadingChanged: (Boolean) -> Unit,
    onError: (String?) -> Unit
) {
    // Firebase Auth keeps the session in a cookie on the authDomain
    // (device-streaming-ded679cd.firebaseapp.com) while the app is served from
    // the panel host (touch-menu-web.online). From the WebView's perspective
    // that auth cookie is third-party, so blocking it here would make
    // onAuthStateChanged never settle and leave the app stuck on its spinner.
    // We only allow the panel + auth hosts, and downloads/file access stay off.
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    // NOTE: no LAYER_TYPE_SOFTWARE here. The old WebView 121 renderer crash is
    // solved by using the updated Google WebView (151+); software rendering can
    // stall page navigation on modern WebViews, so we keep HW acceleration.
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        // IndexedDB is required by Firebase Auth persistence in some WebViews.
        databaseEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        setGeolocationEnabled(false)
        // Reduce renderer memory pressure on low-RAM devices (helps old WebViews
        // avoid OOM renderer crashes).
        setOffscreenPreRaster(false)
    }
    webView.setDownloadListener(DownloadListener { _, _, _, _, _ ->
        // Downloads are intentionally disabled in the public-tablet panel.
    })
    webView.webChromeClient = WebChromeClient()
    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val allowed = AdminPanelNavigationPolicy.isAllowed(request.url.toString(), panelUrl)
            if (!allowed) onError("Blocked navigation to an untrusted address.")
            return !allowed
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            onLoadingChanged(true)
            onError(null)
        }

        override fun onPageFinished(view: WebView, url: String) {
            onLoadingChanged(false)
            Timber.i("DeviceWebView: page finished loading — %s", url)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                onLoadingChanged(false)
                onError("Unable to load the panel. Check the connection and try again.")
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            if (request.isForMainFrame) {
                onLoadingChanged(false)
                onError("The panel returned an error. Please try again later.")
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            onLoadingChanged(false)
            onError("The panel certificate could not be verified.")
        }
    }
}