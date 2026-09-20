package com.example.androidkiosk.ui.menu

import com.example.androidkiosk.model.MenuItem
import com.example.androidkiosk.model.SizeOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuQuantityRulesTest {

    private val item = MenuItem(
        id = "coffee",
        categoryName = "Drinks",
        name = "Coffee",
        price = 100.0,
        sizes = linkedMapOf(
            "Medium" to SizeOption(),
            "Large" to SizeOption(25.0)
        )
    )

    @Test
    fun `size modifier is included in effective price`() {
        assertEquals(100.0, MenuQuantityRules.effectivePrice(item, "Medium"), 0.0)
        assertEquals(125.0, MenuQuantityRules.effectivePrice(item, "Large"), 0.0)
    }

    @Test
    fun `unknown or negative pricing is clamped to zero`() {
        val negative = item.copy(price = -50.0, sizes = linkedMapOf("Large" to SizeOption(-5.0)))
        assertEquals(0.0, MenuQuantityRules.effectivePrice(negative, "Large"), 0.0)
    }

    @Test
    fun `resolve size keeps requested size, defaults to medium, then first key`() {
        assertEquals("Large", MenuQuantityRules.resolveSize(item, "Large"))
        assertEquals("Medium", MenuQuantityRules.resolveSize(item, "Nope"))
        val noMedium = item.copy(sizes = linkedMapOf("Small" to SizeOption(), "Large" to SizeOption()))
        assertEquals("Small", MenuQuantityRules.resolveSize(noMedium, "Nope"))
        assertEquals("", MenuQuantityRules.resolveSize(item.copy(sizes = emptyMap()), "Large"))
    }

    @Test
    fun `untracked inventory allows up to the per-order ceiling`() {
        val limit = MenuQuantityRules.quantityLimit(null, "Medium")
        assertEquals(MenuQuantityRules.MAX_ITEM_QUANTITY, limit.maxQuantity)
        assertEquals(MenuQuantityRules.LimitSource.MAX_ORDER_QUANTITY, limit.source)
        assertNull(limit.trackedStock)
        assertFalse(limit.isTracked)
        assertTrue(limit.isAvailable)
    }

    @Test
    fun `known size missing from inventory is unavailable`() {
        val limit = MenuQuantityRules.quantityLimit(mapOf("Medium" to 3), "Large")
        assertEquals(0, limit.maxQuantity)
        assertEquals(MenuQuantityRules.LimitSource.STOCK, limit.source)
        assertFalse(limit.isAvailable)
    }

    @Test
    fun `known stock below the ceiling limits quantity from stock`() {
        val limit = MenuQuantityRules.quantityLimit(mapOf("Medium" to 3), "Medium")
        assertEquals(3, limit.maxQuantity)
        assertEquals(MenuQuantityRules.LimitSource.STOCK, limit.source)
        assertEquals(3, limit.trackedStock)
    }

    @Test
    fun `stock above the ceiling is capped by the per-order quantity`() {
        val limit = MenuQuantityRules.quantityLimit(mapOf("Medium" to 150), "Medium")
        assertEquals(MenuQuantityRules.MAX_ITEM_QUANTITY, limit.maxQuantity)
        assertEquals(MenuQuantityRules.LimitSource.MAX_ORDER_QUANTITY, limit.source)
        assertEquals(150, limit.trackedStock)
    }

    @Test
    fun `stock exactly at the ceiling still reports a stock source`() {
        val limit = MenuQuantityRules.quantityLimit(mapOf("Medium" to MenuQuantityRules.MAX_ITEM_QUANTITY), "Medium")
        assertEquals(MenuQuantityRules.MAX_ITEM_QUANTITY, limit.maxQuantity)
        assertEquals(MenuQuantityRules.LimitSource.STOCK, limit.source)
    }
}
