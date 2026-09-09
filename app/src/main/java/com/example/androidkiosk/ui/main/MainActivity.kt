package com.example.androidkiosk.ui.main

import android.os.Bundle
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androidkiosk.admin.KioskManager
import com.example.androidkiosk.admin.KioskReleaseGate
import com.example.androidkiosk.admin.PinManager
import com.example.androidkiosk.admin.UnlockAttemptLogger
import com.example.androidkiosk.admin.UnlockMethod
import com.example.androidkiosk.BuildConfig
import com.example.androidkiosk.data.repository.BranchPathProvider
import com.example.androidkiosk.ui.admin.AdminPanelScreen
import com.example.androidkiosk.ui.admin.KioskWebView
import com.example.androidkiosk.ui.menu.MenuScreen
import com.example.androidkiosk.ui.menu.MenuViewModel
import com.example.androidkiosk.ui.menu.components.KioskProvisioningRequiredScreen
import com.example.androidkiosk.ui.theme.AndroidKioskTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import javax.inject.Inject

/** Which full-screen surface the kiosk app is currently showing. */
private enum class KioskSurface {
    /** Embedded web app (registration → setup → dashboard) for unprovisioned devices. */
    SETUP_WEB,

    /** Native locked kiosk menu. */
    KIOSK_MENU,

    /** PIN-unlocked dashboard (embedded web app, admin mode). */
    ADMIN_WEB
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var kioskManager: KioskManager
    @Inject lateinit var pinManager: PinManager
    @Inject lateinit var unlockAttemptLogger: UnlockAttemptLogger
    @Inject lateinit var branchPathProvider: BranchPathProvider

    /** Whether the admin PIN dialog should be shown. */
    private val showPinDialog = MutableStateFlow(false)

    /** Whether the device is currently unlocked by admin. */
    private val isAdminUnlocked = MutableStateFlow(false)
    private val unlockMethod = MutableStateFlow(UnlockMethod.VOLUME_BUTTON)

    /**
     * Current surface: setup web by default, native kiosk menu if already provisioned.
     * NOTE: this cannot read [branchPathProvider] in a field initializer — Hilt injects
     * the field in onCreate() (after construction), so a field initializer would hit an
     * uninitialized lateinit var and crash the app on launch. Initialized in onCreate().
     */
    private val surface = MutableStateFlow(KioskSurface.SETUP_WEB)

    // Volume Up long-press detection via Handler
    private val handler = Handler(Looper.getMainLooper())
    private var volumeUpLongPressRunnable: Runnable? = null
    private var isVolumeUpPressed = false

    companion object {
        /** Duration (ms) that Volume Up must be held to trigger PIN dialog. */
        private const val VOLUME_LONG_PRESS_DURATION_MS = 3000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hilt has now injected all @Inject fields. Decide the initial surface:
        // an already-provisioned device boots straight into the locked kiosk menu;
        // a fresh device starts in the embedded web app (registration → setup).
        surface.value = if (branchPathProvider.isConfigured) {
            KioskSurface.KIOSK_MENU
        } else {
            KioskSurface.SETUP_WEB
        }
        Timber.i(
            "Surface decision — isConfigured=%s company=%s branch=%s → %s",
            branchPathProvider.isConfigured,
            branchPathProvider.companyId,
            branchPathProvider.branchId,
            surface.value.name
        )

        // Window configuration
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // NOTE: FLAG_SECURE was intentionally removed so the app can be
        // screenshotted/recorded during development and support debugging.
        // (Re-enable for a hardened production kiosk if screen privacy is required.)

        // Block back in kiosk mode. The admin panel WebView consumes Back first
        // (via its BackHandler) so we only act when back reaches us.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (surface.value == KioskSurface.ADMIN_WEB) {
                    returnToKiosk()
                } else {
                    Timber.d("Back gesture blocked — kiosk mode active")
                }
            }
        })

        // Enable kiosk mode (Device Owner) unless we are on the first-run setup web
        // (the setup web must stay interactive for registration/setup before locking).
        if (surface.value != KioskSurface.SETUP_WEB) {
            kioskManager.enableKioskMode(this)
        }
        kioskManager.applyUserRestrictions()

        Timber.i(
            "MainActivity created — Device Owner: %s",
            kioskManager.isDeviceOwner
        )

        setContent {
            val viewModel: MenuViewModel = hiltViewModel()
            val appSettings by viewModel.appSettings.collectAsState()
            val showPin by showPinDialog.collectAsState()
            val adminUnlocked by isAdminUnlocked.collectAsState()
            val currentUnlockMethod by unlockMethod.collectAsState()
            val kioskStatus by kioskManager.enforcementStatus.collectAsState()
            val currentSurface by surface.collectAsState()
            val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val releaseKioskBlocked = KioskReleaseGate.isOrderingBlocked(
                isDebuggable = isDebuggable,
                isAdminUnlocked = adminUnlocked,
                status = kioskStatus
            )

            // Determine if reduced motion accessibility setting is enabled
            val reducedMotion = getReducedMotionPreference()

            AndroidKioskTheme(
                backgroundImageUrl = appSettings.backgroundImage,
                backgroundThemeName = appSettings.backgroundTheme,
                reducedMotion = reducedMotion
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentSurface) {
                        KioskSurface.SETUP_WEB -> KioskWebView(
                            url = BuildConfig.ADMIN_PANEL_URL,
                            injectBridge = true,
                            onEnterKioskMode = { companyId, branchId -> enterKioskFromWeb(companyId, branchId) }
                        )
                        KioskSurface.ADMIN_WEB -> AdminPanelScreen(
                            panelUrl = BuildConfig.ADMIN_PANEL_URL,
                            onReturnToKiosk = ::returnToKiosk,
                            onEnterKioskMode = { companyId, branchId -> enterKioskFromWeb(companyId, branchId) }
                        )
                        else -> {
                            if (releaseKioskBlocked) {
                                KioskProvisioningRequiredScreen(kioskStatus.name)
                            } else MenuScreen(
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
                                    kioskManager.disableKioskMode(this@MainActivity)
                                    kioskManager.removeUserRestrictions()
                                    kioskManager.clearHomeApp()
                                    surface.value = KioskSurface.ADMIN_WEB
                                    Timber.i("Admin unlocked device via %s", method.name)
                                },
                                onRelockRequest = {
                                    returnToKiosk()
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

    override fun onResume() {
        super.onResume()
        // Re-enforce kiosk mode if it was somehow exited (defense-in-depth).
        if (!isAdminUnlocked.value && surface.value != KioskSurface.SETUP_WEB) {
            hideSystemBars()
            // Re-enter lock task if not currently in it
            if (kioskManager.isDeviceOwner) {
                try {
                    kioskManager.enableKioskMode(this)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to re-enforce kiosk mode on resume")
                }
            }
        }
    }

    // ─── Volume Up Long-Press Detection ─────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && !isVolumeUpPressed) {
            isVolumeUpPressed = true

            // Schedule the long-press trigger
            volumeUpLongPressRunnable = Runnable {
                Timber.i("Volume Up long press detected — showing PIN dialog")
                unlockMethod.value = UnlockMethod.VOLUME_BUTTON
                showPinDialog.value = true
            }
            handler.postDelayed(volumeUpLongPressRunnable!!, VOLUME_LONG_PRESS_DURATION_MS)

            return true // Consume the event — prevent system volume popup
        }

        // Also consume Volume Down to prevent system volume changes in kiosk mode
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && !isAdminUnlocked.value) {
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            isVolumeUpPressed = false

            // Cancel the long-press runnable if released too early
            volumeUpLongPressRunnable?.let { handler.removeCallbacks(it) }
            volumeUpLongPressRunnable = null

            return true // Consume the event
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && !isAdminUnlocked.value) {
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    // ─── System Bars ────────────────────────────────────────────────────

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** Called from the embedded web app (setup shell or admin panel) to lock into the kiosk menu. */
    private fun enterKioskFromWeb(companyId: String, branchId: String) {
        // Provision the native menu to the branch the web workspace selected.
        if (companyId.isNotBlank() && branchId.isNotBlank()) {
            runCatching { branchPathProvider.configure(companyId, branchId) }
                .onFailure { Timber.w(it, "Failed to provision branch path from web kiosk request") }
        }
        surface.value = KioskSurface.KIOSK_MENU
        isAdminUnlocked.value = false
        kioskManager.enableKioskMode(this)
        kioskManager.applyUserRestrictions()
        hideSystemBars()
        Timber.i("Entered kiosk menu from web (company=%s branch=%s)", companyId, branchId)
    }

    /** Return from the admin panel to the native kiosk menu and re-lock the device. */
    private fun returnToKiosk() {
        surface.value = KioskSurface.KIOSK_MENU
        isAdminUnlocked.value = false
        kioskManager.relockKioskMode(this)
        kioskManager.applyUserRestrictions()
        hideSystemBars()
        Timber.i("Admin panel closed and device re-locked")
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

    override fun onDestroy() {
        super.onDestroy()
        // Clean up handler callbacks
        volumeUpLongPressRunnable?.let { handler.removeCallbacks(it) }
    }
}
