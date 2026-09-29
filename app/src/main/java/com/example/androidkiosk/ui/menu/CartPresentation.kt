package com.example.androidkiosk.ui.menu

import com.example.androidkiosk.model.CartItem
import com.example.androidkiosk.model.MenuItem
import java.util.Locale

/**
 * Pure formatting and accessibility text for cart lines. Shared by the cart, checkout,
 * and item detail overlay so prices, totals, and labels never disagree between screens.
 */
object CartPresentation {

    /** Formats a peso amount, guarding against non-finite values. */
    fun formatPrice(amount: Double): String =
        "₱" + String.format(Locale.getDefault(), "%.2f", amount.takeIf(Double::isFinite) ?: 0.0)

    /** Line subtotal using the size-adjusted unit price held on the cart item. */
    fun lineSubtotal(cartItem: CartItem): Double = cartItem.price * cartItem.quantity

    /**
     * Line subtotal for the item detail overlay, where there is no [CartItem] yet — the unit
     * price is the size-adjusted effective price and the quantity is what the stepper holds.
     *
     * The overlay previously showed the unit price beside a quantity control, so a customer
     * setting quantity to 3 still read a single-item price.
     */
    fun lineSubtotal(effectivePrice: Double, quantity: Int): Double =
        effectivePrice * quantity.coerceAtLeast(0)

    /** Sum of every line subtotal. */
    fun cartTotal(cartItems: List<CartItem>): Double = cartItems.sumOf { it.price * it.quantity }

    /** Total number of individual items across every line. */
    fun cartItemCount(cartItems: List<CartItem>): Int = cartItems.sumOf { it.quantity }

    /** Selected size label, or null when no size applies. */
    fun sizeLabel(cartItem: CartItem): String? = cartItem.selectedSize.ifBlank { null }

    /** Display name that keeps different sizes visibly distinct (e.g. "Coffee · Large"). */
    fun displayName(cartItem: CartItem): String {
        val size = sizeLabel(cartItem)
        return if (size == null) cartItem.menuItem.name else "${cartItem.menuItem.name} · $size"
    }

    /** "Medium" or "" — used to build accessible control labels. */
    private fun sizeSuffix(selectedSize: String): String =
        if (selectedSize.isBlank()) "" else " ($selectedSize)"

    /** Accessible label for a decrease control, including item name and selected size. */
    fun decreaseContentDescription(item: MenuItem, selectedSize: String): String =
        "Decrease quantity of ${item.name}${sizeSuffix(selectedSize)}"

    /**
     * Accessible label for an increase control, including item name, selected size, and — when the
     * control is disabled at the limit — why it is disabled.
     */
    fun increaseContentDescription(
        item: MenuItem,
        selectedSize: String,
        atLimit: Boolean,
        limit: MenuQuantityRules.QuantityLimit
    ): String {
        val base = "Increase quantity of ${item.name}${sizeSuffix(selectedSize)}"
        if (!atLimit) return base
        return when {
            limit.maxQuantity <= 0 -> "$base, unavailable"
            limit.source == MenuQuantityRules.LimitSource.STOCK ->
                "$base, maximum reached, only ${limit.maxQuantity} in stock"
            else -> "$base, maximum reached, ${limit.maxQuantity} per order"
        }
    }

    /** Text shown when numeric inventory is unavailable for an item. */
    const val UNKNOWN_AVAILABILITY = "Availability checked when ordering"

    /**
     * Stock line for the item detail overlay.
     *
     * "1 available" read ambiguously against the price and the quantity stepper — it was
     * unclear whether it meant one in stock, one on hand for this size, or one allowed per
     * order. It is the count for the *selected size*, so it says so.
     */
    fun availabilityText(limit: MenuQuantityRules.QuantityLimit): String = when {
        limit.trackedStock == null -> UNKNOWN_AVAILABILITY
        limit.trackedStock > 0 -> "${limit.trackedStock} left in stock"
        else -> "Out of stock"
    }
}
