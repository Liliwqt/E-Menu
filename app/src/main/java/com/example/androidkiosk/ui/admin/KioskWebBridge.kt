package com.example.androidkiosk.ui.admin

import android.webkit.JavascriptInterface
import timber.log.Timber

/**
 * JavaScript bridge injected into the embedded E-Menu web app WebView.
 *
 * The web app calls these via `window.AndroidKiosk` (see the web helper
 * `src/lib/kioskBridge.js`). The web UI uses it to:
 *  - detect that it runs inside the kiosk WebView (`isEmbedded`),
 *  - read the device's anonymous Firebase UID (`getDeviceUid`) so the web app
 *    can register the device under the signed-in account WITHOUT the operator
 *    typing a UID manually,
 *  - ask the Android shell to leave the dashboard and enter the native
 *    locked kiosk menu mode, handing over the workspace's company/branch
 *    IDs so the native menu is provisioned without re-enrollment.
 */
class KioskWebBridge(
    private val onEnterKioskMode: (companyId: String, branchId: String) -> Unit,
    private val deviceUidProvider: () -> String = { "" }
) {
    @JavascriptInterface
    fun isEmbedded(): Boolean = true

    /** The device's anonymous Firebase Auth UID (used to register this kiosk). */
    @JavascriptInterface
    fun getDeviceUid(): String = deviceUidProvider()

    @JavascriptInterface
    fun enterKioskMode(companyId: String, branchId: String) {
        Timber.i("KioskWebBridge: web app requested kiosk mode (company=%s branch=%s)", companyId, branchId)
        onEnterKioskMode(companyId, branchId)
    }
}