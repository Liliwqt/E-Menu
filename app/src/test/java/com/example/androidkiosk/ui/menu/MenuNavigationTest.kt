package com.example.androidkiosk.ui.menu

import com.example.androidkiosk.model.CartItem
import com.example.androidkiosk.model.MenuItem
import com.example.androidkiosk.model.Order
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the overlay back stack. Every one of these cases was a real defect: Back finished the
 * Activity and destroyed the cart.
 */
class MenuNavigationTest {

    private val item = MenuItem(id = "i1", name = "Cappuccino", price = 110.0)
    private val largeItem = MenuItem(id = "i1", name = "Cappuccino", price = 135.0)
    private val order = Order(
        id = "o1",
        orderNumber = "O1",
        customerName = "Ana",
        items = listOf(cartItem()),
        total = 110.0
    )

    private fun cartItem(quantity: Int = 1) = CartItem(
        menuItem = item,
        price = item.price,
        quantity = quantity
    )

    private val emptyCart = emptyList<CartItem>()
    private val fullCart = listOf(cartItem(quantity = 2))

    // ── the bug this replaces ────────────────────────────────────────────────────────────────
    // Back finished the Activity from every overlay, so the cart died with the composition.
    @Test
    fun `back from an overlay pops instead of exiting`() {
        val state = MenuNavigation.push(MenuNavigation.MenuState(), MenuNavigation.Stage.Cart)

        assertEquals(MenuNavigation.BackAction.POP, MenuNavigation.backAction(state, fullCart))
    }

    @Test
    fun `back pops through the whole flow one stage at a time`() {
        var state = MenuNavigation.MenuState()
        state = MenuNavigation.push(state, MenuNavigation.Stage.ItemDetail(item))
        state = MenuNavigation.push(state, MenuNavigation.Stage.Cart)
        state = MenuNavigation.push(state, MenuNavigation.Stage.Checkout)
        state = MenuNavigation.push(state, MenuNavigation.Stage.PaymentMethod(order))
        state = MenuNavigation.push(state, MenuNavigation.Stage.QrPayment(order))

        val backToMenu = generateSequence(state) { MenuNavigation.pop(it) }.last()

        assertTrue(backToMenu.isAtMenu)
        // Four pops to leave five stages, and the last one is refused at the root.
        assertEquals(MenuNavigation.BackAction.EXIT, MenuNavigation.backAction(backToMenu, emptyCart))
    }

    @Test
    fun `pop refuses at the menu root`() {
        val atMenu = MenuNavigation.MenuState()

        assertNull(MenuNavigation.pop(atMenu))
        assertFalse(MenuNavigation.canPop(atMenu))
    }

    // ── exit guard ───────────────────────────────────────────────────────────────────────────
    @Test
    fun `back at the menu with an empty cart exits freely`() {
        // Staff rely on this. A guard here would lock them out of their own app.
        assertEquals(
            MenuNavigation.BackAction.EXIT,
            MenuNavigation.backAction(MenuNavigation.MenuState(), emptyCart)
        )
    }

    @Test
    fun `back at the menu with a full cart asks before discarding the order`() {
        assertEquals(
            MenuNavigation.BackAction.CONFIRM_EXIT,
            MenuNavigation.backAction(MenuNavigation.MenuState(), fullCart)
        )
    }

    @Test
    fun `an empty list of lines still counts as a full cart`() {
        // A line can exist at quantity 0 only transiently, but if it ever renders, the customer
        // still built something and must be asked.
        assertEquals(
            MenuNavigation.BackAction.CONFIRM_EXIT,
            MenuNavigation.backAction(MenuNavigation.MenuState(), listOf(cartItem(quantity = 0)))
        )
    }

    // ── confirmation ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `a confirmation refuses to be popped`() {
        val paying = MenuNavigation.push(
            MenuNavigation.push(MenuNavigation.MenuState(), MenuNavigation.Stage.Cart),
            MenuNavigation.Stage.QrPayment(order)
        )
        val confirmed = MenuNavigation.confirm(paying, order, MenuNavigation.Method.QR)

        assertNotNull(confirmed)
        assertNull(MenuNavigation.pop(confirmed!!))
        assertFalse(MenuNavigation.canPop(confirmed))
        // And Back is swallowed rather than exiting, so Done is the only way out.
        assertEquals(
            MenuNavigation.BackAction.CONSUME,
            MenuNavigation.backAction(confirmed, emptyCart)
        )
    }

    @Test
    fun `the confirmation keeps its payment stage underneath`() {
        val paying = MenuNavigation.push(
            MenuNavigation.push(MenuNavigation.MenuState(), MenuNavigation.Stage.Cart),
            MenuNavigation.Stage.CounterPayment(order)
        )
        val confirmed = MenuNavigation.confirm(paying, order, MenuNavigation.Method.COUNTER)!!

        assertEquals(MenuNavigation.Stage.CounterPayment(order), confirmed.top)
        assertTrue(confirmed.hasOverlay)
    }

    @Test
    fun `confirming is rejected when the matching payment stage is not showing`() {
        val atMenu = MenuNavigation.MenuState()

        assertNull(MenuNavigation.confirm(atMenu, order, MenuNavigation.Method.QR))
        assertNull(MenuNavigation.confirm(atMenu, order, MenuNavigation.Method.COUNTER))
    }

    @Test
    fun `confirming the wrong method is rejected`() {
        val paying = MenuNavigation.push(
            MenuNavigation.MenuState(), MenuNavigation.Stage.CounterPayment(order)
        )

        assertNull(MenuNavigation.confirm(paying, order, MenuNavigation.Method.QR))
    }

    @Test
    fun `dismissing a confirmation returns to the payment stage, not the menu`() {
        val paying = MenuNavigation.push(
            MenuNavigation.push(MenuNavigation.MenuState(), MenuNavigation.Stage.Cart),
            MenuNavigation.Stage.QrPayment(order)
        )
        val confirmed = MenuNavigation.confirm(paying, order, MenuNavigation.Method.QR)!!
        val afterDone = confirmed.copy(confirmation = null)

        assertEquals(MenuNavigation.Stage.QrPayment(order), afterDone.top)
    }

    // ── push hygiene ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `pushing the stage already on top is a no-op`() {
        val state = MenuNavigation.push(MenuNavigation.MenuState(), MenuNavigation.Stage.Cart)

        val again = MenuNavigation.push(state, MenuNavigation.Stage.Cart)

        assertEquals(1, again.stack.size)
    }

    @Test
    fun `pushing while a confirmation shows is ignored`() {
        val paying = MenuNavigation.push(MenuNavigation.MenuState(), MenuNavigation.Stage.QrPayment(order))
        val confirmed = MenuNavigation.confirm(paying, order, MenuNavigation.Method.QR)!!

        assertEquals(confirmed, MenuNavigation.push(confirmed, MenuNavigation.Stage.Cart))
    }

    @Test
    fun `differing sizes of one item are distinct stages`() {
        // Cart lines are keyed by id + size, so the same item at two sizes must not collapse.
        val small = MenuNavigation.Stage.ItemDetail(item)
        val large = MenuNavigation.Stage.ItemDetail(largeItem)

        val withSmall = MenuNavigation.push(MenuNavigation.MenuState(), small)
        val withBoth = MenuNavigation.push(withSmall, large)

        assertEquals(2, withBoth.stack.size)
        assertEquals(large, withBoth.top)
    }

    @Test
    fun `dismissAll clears the stack`() {
        val state = MenuNavigation.push(
            MenuNavigation.push(MenuNavigation.MenuState(), MenuNavigation.Stage.Cart),
            MenuNavigation.Stage.Checkout
        )

        val cleared = MenuNavigation.dismissAll()

        assertTrue(cleared.isAtMenu)
        assertFalse(cleared.hasOverlay)
        assertTrue(state.hasOverlay)
    }
}
