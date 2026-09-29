package com.example.androidkiosk.ui.menu

import com.example.androidkiosk.model.CartItem
import com.example.androidkiosk.model.MenuItem
import com.example.androidkiosk.model.Order

/**
 * Pure model of the ordering flow's overlay stack.
 *
 * The menu used to track six independent booleans (`selectedItem`, `showCart`, `showCheckout`,
 * `showPaymentMethod`, `showQRPayment`, `showCounterPayment`) plus a separate `currentOrder`.
 * That has no back stack at all: the system Back button had nowhere to pop to, so it finished
 * the Activity from every one of those overlays and destroyed the customer's in-memory cart.
 * Nothing here knows about Compose, so the whole flow is provable without a device.
 *
 * Each stage is a data class rather than a bare token because each needs the data that only
 * exists at that point in the flow — the item being viewed, the order being paid. This also
 * removes the impossible states the booleans allowed, such as the QR overlay being visible with
 * a null order.
 */
object MenuNavigation {

    /** One step of the flow. The menu itself is an empty stack. */
    sealed interface Stage {

        /** Item detail for a specific menu item. */
        data class ItemDetail(val item: MenuItem) : Stage

        /** The cart panel. */
        data object Cart : Stage

        /** Checkout: customer name, still editing. */
        data object Checkout : Stage

        /** Payment method choice, for an order that exists but is not yet submitted. */
        data class PaymentMethod(val order: Order) : Stage

        /** GCash: order is created but not yet submitted. */
        data class QrPayment(val order: Order) : Stage

        /** Pay at counter: order is created but not yet submitted. */
        data class CounterPayment(val order: Order) : Stage
    }

    /**
     * A completed payment confirmation, shown *instead of* [Stage.QrPayment] /
     * [Stage.CounterPayment] rather than pushed on top of them.
     *
     * It is separate from [Stage] because it has different navigation rules: system Back must
     * not dismiss it, so it is not something [pop] may ever remove.
     */
    data class Confirmation(val order: Order, val method: Method)

    /** Which payment the confirmation belongs to. */
    enum class Method { QR, COUNTER }

    /**
     * The whole flow state: an overlay stack plus an optional confirmation.
     *
     * A confirmation implies its underlying payment stage, so [from] rebuilds that stage when
     * constructing the state. Keeping them separate lets the confirmation refuse to pop without
     * the payment stage underneath also becoming un-back-able.
     */
    data class MenuState(
        val stack: List<Stage> = emptyList(),
        val confirmation: Confirmation? = null
    ) {
        /** The stage that is currently on screen, ignoring any confirmation. */
        val top: Stage? get() = stack.lastOrNull()

        /** True when nothing is open — the plain menu is showing. */
        val isAtMenu: Boolean get() = stack.isEmpty() && confirmation == null

        /** True when any overlay, including the confirmation, is visible. */
        val hasOverlay: Boolean get() = stack.isNotEmpty() || confirmation != null
    }

    /**
     * Pushes a stage onto the stack.
     *
     * A stage that is already on top is not pushed twice, so a double tap on "View cart"
     * cannot open two carts.
     */
    fun push(state: MenuState, stage: Stage): MenuState {
        if (state.confirmation != null) return state
        if (state.top == stage) return state
        return state.copy(stack = state.stack + stage)
    }

    /**
     * Removes the top stage, or null when there is nothing to pop.
     *
     * A visible confirmation never pops — the customer must press "Done / Next customer" so the
     * next customer starts from a known state.
     */
    fun pop(state: MenuState): MenuState? {
        if (state.confirmation != null) return null
        if (state.stack.isEmpty()) return null
        return state.copy(stack = state.stack.dropLast(1))
    }

    /** True when [pop] would succeed. */
    fun canPop(state: MenuState): Boolean =
        state.confirmation == null && state.stack.isNotEmpty()

    /** Clears every stage, returning to the plain menu. */
    fun dismissAll(): MenuState = MenuState()

    /**
     * Raises the payment confirmation for a payment stage that is already on top.
     *
     * Returns null when [state] is not showing that payment, which is a programming error
     * rather than something the customer can cause.
     */
    fun confirm(state: MenuState, order: Order, method: Method): MenuState? {
        val expected: Stage = when (method) {
            Method.QR -> Stage.QrPayment(order)
            Method.COUNTER -> Stage.CounterPayment(order)
        }
        if (state.top != expected) return null
        return state.copy(confirmation = Confirmation(order, method))
    }

    /**
     * What Back should do, given the current state and whether the cart has contents.
     *
     * The menu previously let Back finish the Activity unconditionally, silently discarding a
     * cart the customer had spent minutes building. Loss aversion says protect that work — but
     * this app also runs on staff phones, so the app must never be trapped. An empty cart exits
     * freely and a non-empty one asks first.
     */
    enum class BackAction {
        /** Close the top overlay and return to the one beneath it. */
        POP,

        /** A confirmation is showing: swallow Back so the customer must press Done. */
        CONSUME,

        /** At the plain menu with a non-empty cart: ask before discarding the order. */
        CONFIRM_EXIT,

        /** At the plain menu with an empty cart: let the host finish the Activity. */
        EXIT
    }

    /**
     * Decides what Back does. Pure, so the guard is provable without a device.
     *
     * [cartItems] is passed in rather than read from a ViewModel to keep this testable and free
     * of Compose.
     */
    fun backAction(state: MenuState, cartItems: List<CartItem>): BackAction = when {
        state.confirmation != null -> BackAction.CONSUME
        state.stack.isNotEmpty() -> BackAction.POP
        cartItems.isEmpty() -> BackAction.EXIT
        else -> BackAction.CONFIRM_EXIT
    }
}
