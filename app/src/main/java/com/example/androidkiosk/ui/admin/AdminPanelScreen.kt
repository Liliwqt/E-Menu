package com.example.androidkiosk.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** Full management panel hosted by the existing React application. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AdminPanelScreen(
    panelUrl: String,
    onReturnToKiosk: () -> Unit,
    onEnterKioskMode: (companyId: String, branchId: String) -> Unit = { _, _ -> Unit }
) {
    val webViewHolder = remember { WebViewBackHelper() }

    BackHandler {
        if (webViewHolder.goBack()) {
            // handled: navigated one step back inside the panel
        } else {
            onReturnToKiosk()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("E-Menu Admin Panel") },
                actions = {
                    TextButton(onClick = onReturnToKiosk) {
                        Text("Return to menu")
                    }
                }
            )
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            KioskWebView(
                url = panelUrl,
                injectBridge = true,
                onEnterKioskMode = onEnterKioskMode,
                onWebViewCreated = { webViewHolder.attach(it) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Lightweight holder so the composable can ask the active WebView to go back. */
class WebViewBackHelper {
    private var webView: android.webkit.WebView? = null

    fun attach(webView: android.webkit.WebView) {
        this.webView = webView
    }

    fun goBack(): Boolean {
        val current = webView ?: return false
        return if (current.canGoBack()) {
            current.goBack()
            true
        } else {
            false
        }
    }
}
