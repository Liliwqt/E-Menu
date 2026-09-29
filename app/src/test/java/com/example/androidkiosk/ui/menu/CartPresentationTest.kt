package com.example.androidkiosk.ui.menu

import com.example.androidkiosk.model.CartItem
import com.example.androidkiosk.model.MenuItem
import com.example.androidkiosk.model.SizeOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartPresentationTest {

    private val coffee = MenuItem(
        id = "coffee",
        categoryName = "Drinks",
        name = "Coffee",
        price = 100.0,
        sizes = linkedMapOf(
            "Medium" to SizeOption(),
            "Large" to SizeOption(25.0)
        )
    )

    private val largeLine = CartItem(menuItem = coffee, price = 125.0, quantity = 2, selectedSize = "Large")
    private val mediumLine = CartItem(menuItem = coffee, price = 100.0, quantity = 1, selectedSize = "Medium")

    @Test
    fun `line subtotal uses the size-adjusted unit price`() {
        assertEquals(250.0, CartPresentation.lineSubtotal(largeLine), 0.0)
        assertEquals(100.0, CartPresentation.lineSubtotal(mediumLine), 0.0)
    }

    @Test
    fun `cart total sums every size-adjusted line`() {
        assertEquals(350.0, CartPresentation.cartTotal(listOf(largeLine, mediumLine)), 0.0)
    }

    @Test
    fun `item count sums quantities`() {
        assertEquals(3, CartPresentation.cartItemCount(listOf(largeLine, mediumLine)))
        assertEquals(0, CartPresentation.cartItemCount(emptyList()))
    }

    @Test
    fun `different sizes produce distinct labels`() {
        assertEquals("Large", CartPresentation.sizeLabel(largeLine))
        assertEquals("Medium", CartPresentation.sizeLabel(mediumLine))
        assertEquals("Coffee · Large", CartPresentation.displayName(largeLine))
        assertEquals("Coffee · Medium", CartPresentation.displayName(mediumLine))
        assertTrue(CartPresentation.displayName(largeLine) != CartPresentation.displayName(mediumLine))
    }

    @Test
    fun `size label is null when no size applies`() {
        val noSize = CartItem(menuItem = coffee.copy(sizes = emptyMap()), price = 100.0, quantity = 1, selectedSize = "")
        assertNull(CartPresentation.sizeLabel(noSize))
        assertEquals("Coffee", CartPresentation.displayName(noSize))
    }

    @Test
    fun `quantity control labels include item name and selected size`() {
        assertEquals(
            "Decrease quantity of Coffee (Large)",
            CartPresentation.decreaseContentDescription(coffee, "Large")
        )
        assertEquals(
            "Increase quantity of Coffee (Large)",
            CartPresentation.increaseContentDescription(
                item = coffee,
                selectedSize = "Large",
                atLimit = false,
                limit = MenuQuantityRules.quantityLimit(mapOf("Large" to 5), "Large")
            )
        )
        assertEquals(
            "Decrease quantity of Coffee",
            CartPresentation.decreaseContentDescription(coffee, "")
        )
    }

    @Test
    fun `increase label explains a stock limit`() {
        val label = CartPresentation.increaseContentDescription(
            item = coffee,
            selectedSize = "Large",
            atLimit = true,
            limit = MenuQuantityRules.quantityLimit(mapOf("Large" to 2), "Large")
        )
        assertTrue(label.contains("only 2 in stock"))
    }

    @Test
    fun `increase label explains the per-order limit`() {
        val label = CartPresentation.increaseContentDescription(
            item = coffee,
            selectedSize = "Large",
            atLimit = true,
            limit = MenuQuantityRules.quantityLimit(mapOf("Large" to 150), "Large")
        )
        assertTrue(label.contains("99 per order"))
    }

    @Test
    fun `availability text reports tracked stock for the selected size`() {
        val fromStock = CartPresentation.availabilityText(
            MenuQuantityRules.quantityLimit(mapOf("Large" to 4), "Large")
        )
        assertEquals("4 left in stock", fromStock)

        // A well-stocked item reports its real stock, not the 99-per-order ceiling: the
        // ceiling is a submission guard, not something a customer needs to reason about.
        val fromCeiling = CartPresentation.availabilityText(
            MenuQuantityRules.quantityLimit(mapOf("Large" to 150), "Large")
        )
        assertEquals("150 left in stock", fromCeiling)
        assertEquals(99, MenuQuantityRules.quantityLimit(mapOf("Large" to 150), "Large").maxQuantity)

        val outOfStock = CartPresentation.availabilityText(
            MenuQuantityRules.quantityLimit(mapOf("Large" to 0), "Large")
        )
        assertEquals("Out of stock", outOfStock)
    }

    @Test
    fun `availability text is explicit when inventory is untracked`() {
        assertEquals(
            CartPresentation.UNKNOWN_AVAILABILITY,
            CartPresentation.availabilityText(
                MenuQuantityRules.quantityLimit(null, "Large")
            )
        )
        assertEquals("Availability checked when ordering", CartPresentation.UNKNOWN_AVAILABILITY)
    }

    @Test
    fun `overlay line subtotal multiplies the size-adjusted unit price by the stepper value`() {
        // The overlay shows effectivePrice (base + size modifier) with a quantity stepper, and
        // used to display only the unit price — so 3 items still read as one.
        assertEquals(375.0, CartPresentation.lineSubtotal(125.0, 3), 0.0)
        assertEquals(125.0, CartPresentation.lineSubtotal(125.0, 1), 0.0)
    }

    @Test
    fun `overlay line subtotal never goes negative`() {
        assertEquals(0.0, CartPresentation.lineSubtotal(125.0, 0), 0.0)
        assertEquals(0.0, CartPresentation.lineSubtotal(125.0, -4), 0.0)
    }

    @Test
    fun `availability text names the count and says it is stock`() {
        // "1 available" was ambiguous next to the price and the stepper.
        assertEquals(
            "1 left in stock",
            CartPresentation.availabilityText(MenuQuantityRules.quantityLimit(mapOf("Medium" to 1), "Medium"))
        )
        assertEquals(
            "7 left in stock",
            CartPresentation.availabilityText(MenuQuantityRules.quantityLimit(mapOf("Large" to 7), "Large"))
        )
    }

    @Test
    fun `availability text covers unknown and sold out`() {
        assertEquals(
            CartPresentation.UNKNOWN_AVAILABILITY,
            CartPresentation.availabilityText(MenuQuantityRules.quantityLimit(null, "Medium"))
        )
        assertEquals(
            "Out of stock",
            CartPresentation.availabilityText(MenuQuantityRules.quantityLimit(mapOf("Medium" to 0), "Medium"))
        )
    }
}
