package com.example.androidkiosk.ui.menu.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.androidkiosk.model.MenuItem
import com.example.androidkiosk.model.Order
import com.example.androidkiosk.model.SizeOption
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CheckoutOverlaysTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val order = Order(
        id = "8fb46cb1-52a7-4e77-9030-b58be89b742f",
        orderNumber = "8FB46CB1",
        customerName = "Guest",
        items = emptyList(),
        total = 125.0
    )

    @Test
    fun qrRendersBundledMerchantCodeAndPaidActionIsSingleFire() {
        var reports = 0
        composeRule.setContent {
            MaterialTheme {
                QRPaymentOverlay(
                    order = order,
                    isSubmitting = false,
                    isComplete = false,
                    onPaid = { reports++ },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Merchant GCash QR code").assertExists()
        composeRule.onNodeWithText("I'VE PAID").performClick().assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(1, reports) }
    }

    @Test
    fun counterSubmissionIsSingleFire() {
        var submissions = 0
        composeRule.setContent {
            MaterialTheme {
                CounterPaymentOverlay(
                    order = order,
                    isSubmitting = false,
                    isComplete = false,
                    onSubmit = { submissions++ },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("SUBMIT ORDER").performClick().assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(1, submissions) }
    }

    @Test
    fun itemDetailSelectsSizeAndDisablesOutOfStockSelection() {
        val item = MenuItem(
            id = "coffee",
            categoryName = "Drinks",
            name = "Coffee",
            price = 100.0,
            sizes = linkedMapOf(
                "Medium" to SizeOption(),
                "Large" to SizeOption(25.0)
            )
        )
        var selectedSize: String? = null
        composeRule.setContent {
            MaterialTheme {
                ItemDetailOverlay(
                    item = item,
                    stockBySize = mapOf("Medium" to 0, "Large" to 1),
                    onDismiss = {},
                    onAddToCart = { _, _, size -> selectedSize = size }
                )
            }
        }

        composeRule.onNodeWithText("Out of stock").assertExists()
        composeRule.onNodeWithText("Add to Cart").assertIsNotEnabled()
        composeRule.onNodeWithText("Large +₱25.00").performClick()
        composeRule.onNodeWithText("₱125.00").assertExists()
        composeRule.onNodeWithText("Add to Cart").performClick()
        composeRule.runOnIdle { assertEquals("Large", selectedSize) }
    }

    @Test
    fun firebaseFailureShowsRetryAction() {
        composeRule.setContent {
            MaterialTheme {
                QRPaymentOverlay(
                    order = order,
                    isSubmitting = false,
                    isComplete = false,
                    errorMessage = "Check the connection and try again.",
                    onPaid = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Unable to submit order").assertExists()
        composeRule.onNodeWithText("TRY AGAIN").assertExists()
    }

    @Test
    fun registrationScreenDisplaysAnonymousUidAndRetries() {
        var retries = 0
        composeRule.setContent {
            MaterialTheme {
                KioskAuthorizationScreen(
                    uid = "new-anonymous-kiosk-uid",
                    message = null,
                    onRetry = { retries++ }
                )
            }
        }

        composeRule.onNodeWithText("new-anonymous-kiosk-uid").assertExists()
        composeRule.onNodeWithText("Check registration").performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun registrationScreenUsesDeviceWording() {
        composeRule.setContent {
            MaterialTheme {
                KioskAuthorizationScreen(
                    uid = "uid",
                    message = null,
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithText("Device registration required").assertExists()
    }

    // ── Order confirmation ──────────────────────────────────────────────

    @Test
    fun qrSuccessWaitsForDoneInsteadOfAutoDismissing() {
        var dismissals = 0
        composeRule.setContent {
            MaterialTheme {
                QRPaymentOverlay(
                    order = order,
                    isSubmitting = false,
                    isComplete = true,
                    onPaid = {},
                    onDismiss = { dismissals++ }
                )
            }
        }

        composeRule.onNodeWithText("Order Submitted").assertExists()
        composeRule.onNodeWithText("Done / Next customer").assertExists()

        // The previous build auto-dismissed success after 1500 ms; wait past that to prove it no longer does.
        Thread.sleep(2200)
        composeRule.waitForIdle()

        assertEquals(0, dismissals)
        composeRule.onNodeWithText("Done / Next customer").assertExists()
    }

    @Test
    fun counterSuccessWaitsForDoneInsteadOfAutoDismissing() {
        var dismissals = 0
        composeRule.setContent {
            MaterialTheme {
                CounterPaymentOverlay(
                    order = order,
                    isSubmitting = false,
                    isComplete = true,
                    onSubmit = {},
                    onDismiss = { dismissals++ }
                )
            }
        }

        composeRule.onNodeWithText("Done / Next customer").assertExists()

        Thread.sleep(2200)
        composeRule.waitForIdle()

        assertEquals(0, dismissals)
        composeRule.onNodeWithText("Done / Next customer").assertExists()
    }

    @Test
    fun doneDismissesQrConfirmationOnce() {
        var dismissals = 0
        composeRule.setContent {
            MaterialTheme {
                QRPaymentOverlay(
                    order = order,
                    isSubmitting = false,
                    isComplete = true,
                    onPaid = {},
                    onDismiss = { dismissals++ }
                )
            }
        }

        composeRule.onNodeWithText("Done / Next customer").performClick()
        composeRule.waitUntil(timeoutMillis = 3000) { dismissals == 1 }
        assertEquals(1, dismissals)
    }

    @Test
    fun doneDismissesCounterConfirmationOnce() {
        var dismissals = 0
        composeRule.setContent {
            MaterialTheme {
                CounterPaymentOverlay(
                    order = order,
                    isSubmitting = false,
                    isComplete = true,
                    onSubmit = {},
                    onDismiss = { dismissals++ }
                )
            }
        }

        composeRule.onNodeWithText("Done / Next customer").performClick()
        composeRule.waitUntil(timeoutMillis = 3000) { dismissals == 1 }
        assertEquals(1, dismissals)
    }

    @Test
    fun qrSuccessConfirmationKeepsPaymentVerificationCopy() {
        composeRule.setContent {
            MaterialTheme {
                QRPaymentOverlay(
                    order = order,
                    isSubmitting = false,
                    isComplete = true,
                    onPaid = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText(
            "Your payment was reported. Staff will verify your payment report before serving your order."
        ).assertExists()
    }

    // ── Responsive item detail ──────────────────────────────────────────

    @Test
    fun itemDetailShowsUnknownInventoryMessageWhenStockIsUnknown() {
        val untracked = MenuItem(
            id = "tea",
            categoryName = "Drinks",
            name = "Tea",
            price = 50.0
        )
        composeRule.setContent {
            MaterialTheme {
                ItemDetailOverlay(
                    item = untracked,
                    stockBySize = null,
                    onDismiss = {},
                    onAddToCart = { _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Availability checked when ordering").assertExists()
        composeRule.onNodeWithText("Max 99 per order").assertExists()
    }

    @Test
    fun itemDetailMarksUnavailableSizesAndLabelsQuantityControls() {
        val item = MenuItem(
            id = "coffee",
            categoryName = "Drinks",
            name = "Coffee",
            price = 100.0,
            sizes = linkedMapOf(
                "Medium" to SizeOption(),
                "Large" to SizeOption(25.0)
            )
        )
        composeRule.setContent {
            MaterialTheme {
                ItemDetailOverlay(
                    item = item,
                    stockBySize = mapOf("Medium" to 0, "Large" to 2),
                    onDismiss = {},
                    onAddToCart = { _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Medium — Sold out").assertExists()

        // The default (sold-out) size explains that increase is unavailable.
        composeRule.onNodeWithContentDescription("Increase quantity of Coffee (Medium), unavailable")
            .assertExists()

        // Select the available size so the limit explanation reflects tracked stock.
        composeRule.onNodeWithText("Large +₱25.00").performClick()
        composeRule.onNodeWithContentDescription("Decrease quantity of Coffee (Large)", substring = true)
            .assertExists()
        composeRule.onNodeWithContentDescription("Increase quantity of Coffee (Large)", substring = true)
            .assertExists()
        composeRule.onNodeWithText("Limit: 2 left in stock").assertExists()
    }

    @Test
    fun itemDetailKeepsPrimaryActionReachableWithManySizesAndLargeFonts() {
        val item = MenuItem(
            id = "combo",
            categoryName = "Drinks",
            name = "Signature Iced Caramel Macchiato With Extra Cream",
            price = 100.0,
            sizes = linkedMapOf(
                "Small" to SizeOption(),
                "Medium" to SizeOption(),
                "Large" to SizeOption(25.0),
                "Extra Large" to SizeOption(40.0),
                "Family" to SizeOption(80.0),
                "Party" to SizeOption(150.0)
            )
        )
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)
                ) {
                    Box(Modifier.requiredSize(360.dp, 520.dp)) {
                        ItemDetailOverlay(
                            item = item,
                            stockBySize = null,
                            onDismiss = {},
                            onAddToCart = { _, _, _ -> }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Add to Cart").assertIsDisplayed()
    }
}
