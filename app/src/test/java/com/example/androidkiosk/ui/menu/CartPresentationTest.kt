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
    fun `limit explanation distinguishes stock from the order ceiling`() {
        val fromStock = CartPresentation.limitExplanation(
            coffee,
            "Large",
            MenuQuantityRules.quantityLimit(mapOf("Large" to 4), "Large")
        )
        assertTrue(fromStock!!.contains("4 left in stock"))

        val fromCeiling = CartPresentation.limitExplanation(
            coffee,
            "Large",
            MenuQuantityRules.quantityLimit(mapOf("Large" to 150), "Large")
        )
        assertTrue(fromCeiling!!.contains("150") && fromCeiling.contains("99 per order"))

        val untracked = CartPresentation.limitExplanation(
            coffee,
            "Large",
            MenuQuantityRules.quantityLimit(null, "Large")
        )
        assertEquals("Max 99 per order", untracked)
    }

    @Test
    fun `unknown availability text is explicit`() {
        assertEquals("Availability checked when ordering", CartPresentation.UNKNOWN_AVAILABILITY)
    }
}
