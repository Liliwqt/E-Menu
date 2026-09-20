package com.example.androidkiosk.ui.admin

import android.webkit.JavascriptInterface
import timber.log.Timber

/**
 * JavaScript bridge injected into the embedded E-Menu web app WebView.
 *
 * The web app calls these via `window.AndroidKiosk` (see the web helper
 * `src/lib/deviceBridge.js`). The web UI uses it to:
 *  - detect that it runs inside the embedded WebView (`isEmbedded`),
 *  - read the device's anonymous Firebase UID (`getDeviceUid`) so the web app
 *    can register the device under the signed-in account WITHOUT the operator
 *    typing a UID manually,
 *  - ask the Android shell to leave the dashboard and enter the native
 *    menu mode, handing over the workspace's company/branch
 *    IDs so the native menu is provisioned without re-enrollment.
 */
class DeviceWebBridge(
    private val onEnterMenuMode: (companyId: String, branchId: String) -> Unit,
    private val deviceUidProvider: () -> String = { "" }
) {
    @JavascriptInterface
    fun isEmbedded(): Boolean = true

    /** The device's anonymous Firebase Auth UID (used to register this device). */
    @JavascriptInterface
    fun getDeviceUid(): String = deviceUidProvider()

    // NOTE: the method name `enterKioskMode` is part of the JavaScript contract
    // (`window.AndroidKiosk.enterKioskMode`) and must not be renamed without
    // updating and redeploying the web app in lockstep.
    @JavascriptInterface
    fun enterKioskMode(companyId: String, branchId: String) {
        Timber.i("DeviceWebBridge: web app requested menu mode (company=%s branch=%s)", companyId, branchId)
        onEnterMenuMode(companyId, branchId)
    }
}