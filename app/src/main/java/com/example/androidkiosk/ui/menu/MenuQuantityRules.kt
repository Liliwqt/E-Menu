package com.example.androidkiosk.ui.menu

import com.example.androidkiosk.model.MenuItem

/**
 * Pure quantity, size, and price rules shared by the cart, the item detail overlay,
 * and [MenuViewModel].
 *
 * Keeping these rules in one place means the UI limit shown to the customer and the
 * limit enforced at submission always agree. Nothing here touches Firebase, so it is
 * covered by fast JVM unit tests.
 */
object MenuQuantityRules {

    /** Hard ceiling on a single line, regardless of stock. */
    const val MAX_ITEM_QUANTITY = 99

    /** Inventory size used for items that have no sizes of their own. */
    const val DEFAULT_STOCK_SIZE = "Medium"

    /** Which rule is currently the binding limit for a size. */
    enum class LimitSource {
        /** A known, lower stock count for the chosen size. */
        STOCK,

        /** Untracked inventory, or stock above the per-order ceiling. */
        MAX_ORDER_QUANTITY
    }

    /**
     * The effective limit for one size selection.
     *
     * @param maxQuantity maximum selectable quantity (0 means the size is unavailable).
     * @param source whether the limit comes from tracked stock or the per-order ceiling.
     * @param trackedStock known numeric stock for the size, or null when inventory is untracked.
     */
    data class QuantityLimit(
        val maxQuantity: Int,
        val source: LimitSource,
        val trackedStock: Int?
    ) {
        val isAvailable: Boolean get() = maxQuantity > 0
        val isTracked: Boolean get() = trackedStock != null
    }

    /** Resolves the size actually used for an item, matching the cart's existing behaviour. */
    fun resolveSize(item: MenuItem, requestedSize: String): String {
        if (item.sizes.isEmpty()) return ""
        if (requestedSize in item.sizes) return requestedSize
        return if (DEFAULT_STOCK_SIZE in item.sizes) DEFAULT_STOCK_SIZE else item.sizes.keys.first()
    }

    /** Size-adjusted effective price (base price + size modifier), clamped to a finite, non-negative value. */
    fun effectivePrice(item: MenuItem, selectedSize: String): Double {
        val adjusted = item.price + (item.sizes[selectedSize]?.priceModifier ?: 0.0)
        return adjusted.takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0
    }

    /**
     * @param itemStock the per-size stock map for an item, or null when the item has no
     *   inventory record at all (legacy/untracked, available up to the per-order ceiling).
     * @param sizeKey the resolved size, defaulting to [DEFAULT_STOCK_SIZE] when the item has no sizes.
     */
    fun quantityLimit(itemStock: Map<String, Int>?, sizeKey: String): QuantityLimit {
        if (itemStock == null) {
            return QuantityLimit(MAX_ITEM_QUANTITY, LimitSource.MAX_ORDER_QUANTITY, null)
        }
        val stock = itemStock[sizeKey] ?: 0
        return if (stock > MAX_ITEM_QUANTITY) {
            QuantityLimit(MAX_ITEM_QUANTITY, LimitSource.MAX_ORDER_QUANTITY, stock)
        } else {
            QuantityLimit(stock, LimitSource.STOCK, stock)
        }
    }
}
