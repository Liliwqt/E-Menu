package com.example.androidkiosk.ui.menu.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.example.androidkiosk.model.MenuItem
import com.example.androidkiosk.model.Order
import com.example.androidkiosk.model.SizeOption
import com.example.androidkiosk.ui.theme.AndroidKioskTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Captures device screenshots of the reworked kiosk screens using isolated fixture data.
 *
 * This is a native rendering check only: it never signs in, never touches Firebase, and never
 * submits an order. Screenshots land in the app's external files directory and are pulled with
 * `adb pull` for the handover record.
 */
class KioskUxScreenshotsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: File by lazy {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "kiosk-ux"
        )
        dir.mkdirs()
        dir
    }

    private val order = Order(
        id = "8fb46cb1-52a7-4e77-9030-b58be89b742f",
        orderNumber = "8FB46CB1",
        customerName = "Guest",
        items = emptyList(),
        total = 350.0
    )

    private val sizedItem = MenuItem(
        id = "coffee",
        categoryName = "Drinks",
        name = "Coffee",
        price = 100.0,
        sizes = linkedMapOf(
            "Medium" to SizeOption(),
            "Large" to SizeOption(25.0)
        )
    )

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        File(outputDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun captureItemDetailWithSizesAndStock() {
        composeRule.setContent {
            AndroidKioskTheme(backgroundThemeName = "Dark") {
                ItemDetailOverlay(
                    item = sizedItem,
                    stockBySize = mapOf("Medium" to 2, "Large" to 0),
                    onDismiss = {},
                    onAddToCart = { _, _, _ -> }
                )
            }
        }
        capture("item-detail-sizes")
    }

    @Test
    fun captureItemDetailWithUnknownInventory() {
        val untracked = MenuItem(
            id = "tea",
            categoryName = "Drinks",
            name = "Signature Iced Caramel Macchiato With Extra Cream",
            price = 150.0
        )
        composeRule.setContent {
            AndroidKioskTheme(backgroundThemeName = "Dark") {
                ItemDetailOverlay(
                    item = untracked,
                    stockBySize = null,
                    onDismiss = {},
                    onAddToCart = { _, _, _ -> }
                )
            }
        }
        capture("item-detail-unknown-inventory")
    }

    @Test
    fun captureQrPaymentConfirmation() {
        composeRule.setContent {
            AndroidKioskTheme(backgroundThemeName = "Dark") {
                QRPaymentOverlay(
                    order = order,
                    isSubmitting = false,
                    isComplete = true,
                    onPaid = {},
                    onDismiss = {}
                )
            }
        }
        capture("qr-confirmation")
    }

    @Test
    fun captureCounterPaymentConfirmation() {
        composeRule.setContent {
            AndroidKioskTheme(backgroundThemeName = "Dark") {
                CounterPaymentOverlay(
                    order = order,
                    isSubmitting = false,
                    isComplete = true,
                    onSubmit = {},
                    onDismiss = {}
                )
            }
        }
        capture("counter-confirmation")
    }
}
