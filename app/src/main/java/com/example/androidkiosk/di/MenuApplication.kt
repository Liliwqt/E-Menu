package com.example.androidkiosk.di

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.pm.ApplicationInfo
import android.webkit.WebView
import com.example.androidkiosk.BuildConfig
import com.example.androidkiosk.admin.AuthManager
import com.example.androidkiosk.admin.KioskDeviceAdminReceiver
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MenuApplication : Application() {

    @Inject
    lateinit var authManager: AuthManager

    /** Application-scoped coroutine scope for startup tasks. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Keep remote WebView debugging disabled in release builds.
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        }
        runCatching {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        }.onFailure { error ->
            Timber.w(error, "Firebase persistence init failed")
        }

        // Establish the anonymous device identity before checking kiosk authorization.
        appScope.launch {
            authManager.ensureSignedIn()
        }

        // Log Device Owner status on startup
        logDeviceOwnerStatus()
    }

    private fun logDeviceOwnerStatus() {
        try {
            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val isDeviceOwner = dpm?.isDeviceOwnerApp(packageName) == true
            val componentName = KioskDeviceAdminReceiver.getComponentName(this)
            val isAdminActive = dpm?.isAdminActive(componentName) == true

            Timber.i(
                "Kiosk status — Device Owner: %s, Admin Active: %s, Package: %s",
                isDeviceOwner,
                isAdminActive,
                packageName
            )

            if (!isDeviceOwner) {
                Timber.w(
                    "Device Owner NOT provisioned. To enable kiosk mode, run:\n" +
                    "  adb shell dpm set-device-owner %s/.admin.KioskDeviceAdminReceiver",
                    packageName
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to check Device Owner status")
        }
    }
}
