package com.example.androidkiosk.ui.main

import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androidkiosk.admin.PinManager
import com.example.androidkiosk.admin.UnlockAttemptLogger
import com.example.androidkiosk.admin.UnlockMethod
import com.example.androidkiosk.BuildConfig
import com.example.androidkiosk.data.repository.BranchPathProvider
import com.example.androidkiosk.ui.admin.AdminPanelScreen
import com.example.androidkiosk.ui.admin.DeviceWebView
import com.example.androidkiosk.ui.menu.MenuScreen
import com.example.androidkiosk.ui.menu.MenuViewModel
import com.example.androidkiosk.ui.theme.AndroidDeviceTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import javax.inject.Inject

/** Which full-screen surface the app is currently showing. */
private enum class AppSurface {
    /** Embedded web app (registration → setup → dashboard) when the branch is not provisioned. */
    SETUP_WEB,

    /** Native menu. */
    MENU,

    /** PIN-unlocked dashboard (embedded web app, admin mode). */
    ADMIN_WEB
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var pinManager: PinManager
    @Inject lateinit var unlockAttemptLogger: UnlockAttemptLogger
    @Inject lateinit var branchPathProvider: BranchPathProvider

    /** Whether the admin PIN dialog should be shown. */
    private val showPinDialog = MutableStateFlow(false)

    /** Whether the admin panel is currently open (unlocked by PIN). */
    private val isAdminUnlocked = MutableStateFlow(false)
    private val unlockMethod = MutableStateFlow(UnlockMethod.ADMIN_BUTTON)

    /**
     * Current surface: setup web by default, native menu if already provisioned.
     * NOTE: this cannot read [branchPathProvider] in a field initializer — Hilt injects
     * the field in onCreate() (after construction), so a field initializer would hit an
     * uninitialized lateinit var and crash the app on launch. Initialized in onCreate().
     */
    private val surface = MutableStateFlow(AppSurface.SETUP_WEB)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hilt has now injected all @Inject fields. Decide the initial surface:
        // an already-provisioned device boots straight into the native menu;
        // a fresh device starts in the embedded web app (registration → setup).
        surface.value = if (branchPathProvider.isConfigured) {
            AppSurface.MENU
        } else {
            AppSurface.SETUP_WEB
        }
        Timber.i(
            "Surface decision — isConfigured=%s company=%s branch=%s → %s",
            branchPathProvider.isConfigured,
            branchPathProvider.companyId,
            branchPathProvider.branchId,
            surface.value.name
        )

        // Window configuration — display cutout only. Deliberately NOT a locked
        // kiosk-style lockdown: this app ships to staff phones and shared tablets, so the status
        // bar, keyguard, volume keys, Back, and screen timeout stay under the
        // device owner's control.
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        enableEdgeToEdge()
        // NOTE: FLAG_SECURE was intentionally removed so the app can be
        // screenshotted/recorded during development and support debugging.

        Timber.i("MainActivity created — surface=%s", surface.value.name)

        setContent {
            val viewModel: MenuViewModel = hiltViewModel()
            val appSettings by viewModel.appSettings.collectAsState()
            val showPin by showPinDialog.collectAsState()
            val adminUnlocked by isAdminUnlocked.collectAsState()
            val currentUnlockMethod by unlockMethod.collectAsState()
            val currentSurface by surface.collectAsState()

            // Determine if reduced motion accessibility setting is enabled
            val reducedMotion = getReducedMotionPreference()

            AndroidDeviceTheme(
                backgroundImageUrl = appSettings.backgroundImage,
                backgroundThemeName = appSettings.backgroundTheme,
                reducedMotion = reducedMotion
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentSurface) {
                        AppSurface.SETUP_WEB -> DeviceWebView(
                            url = BuildConfig.ADMIN_PANEL_URL,
                            injectBridge = true,
                            onEnterMenuMode = { companyId, branchId -> enterMenuFromWeb(companyId, branchId) }
                        )
                        AppSurface.ADMIN_WEB -> AdminPanelScreen(
                            panelUrl = BuildConfig.ADMIN_PANEL_URL,
                            onReturnToMenu = ::returnToMenu,
                            onEnterMenuMode = { companyId, branchId -> enterMenuFromWeb(companyId, branchId) }
                        )
                        else -> {
                            MenuScreen(
                                viewModel = viewModel,
                                showPinDialog = showPin,
                                isAdminUnlocked = adminUnlocked,
                                unlockMethod = currentUnlockMethod,
                                pinManager = pinManager,
                                onPinDialogDismiss = {
                                    showPinDialog.value = false
                                },
                                onUnlockSuccess = { method ->
                                    unlockAttemptLogger.logAttempt(method, success = true)
                                    showPinDialog.value = false
                                    isAdminUnlocked.value = true
                                    surface.value = AppSurface.ADMIN_WEB
                                    Timber.i("Admin unlocked device via %s", method.name)
                                },
                                onRelockRequest = {
                                    returnToMenu()
                                },
                                onPinDialogRequest = { method ->
                                    unlockMethod.value = method
                                    showPinDialog.value = true
                                },
                                onPinFailed = { method ->
                                    unlockAttemptLogger.logAttempt(method, success = false)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /** Called from the embedded web app (setup shell or admin panel) to switch to the native menu. */
    private fun enterMenuFromWeb(companyId: String, branchId: String) {
        // Provision the native menu to the branch the web workspace selected.
        if (companyId.isNotBlank() && branchId.isNotBlank()) {
            runCatching { branchPathProvider.configure(companyId, branchId) }
                .onFailure { Timber.w(it, "Failed to provision branch path from web request") }
        }
        surface.value = AppSurface.MENU
        isAdminUnlocked.value = false
        Timber.i("Entered native menu from web (company=%s branch=%s)", companyId, branchId)
    }

    /** Return from the admin panel to the native menu. */
    private fun returnToMenu() {
        surface.value = AppSurface.MENU
        isAdminUnlocked.value = false
        Timber.i("Admin panel closed")
    }

    // ─── Accessibility: Reduced Motion Preference ───────────────────────

    /** Returns whether the user has enabled reduced motion accessibility setting. */
    private fun getReducedMotionPreference(): Boolean {
        return try {
            val value = Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE
            )
            value == 0.0f
        } catch (e: Settings.SettingNotFoundException) {
            Timber.w(e, "Could not read ANIMATOR_DURATION_SCALE")
            false
        } catch (e: SecurityException) {
            Timber.w(e, "Permission denied to read ANIMATOR_DURATION_SCALE")
            false
        }
    }
}
