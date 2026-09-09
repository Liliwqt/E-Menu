package com.example.androidkiosk.model

import com.google.firebase.database.ServerValue

/** Flattened representation of an order for Firebase logging at the provisioned branch path. */
data class OrderLogEntry(
    val orderId: String,
    val submittedByUid: String,
    val orderNumber: String,
    val customerName: String,
    val items: List<OrderLogItem>,
    val total: Double,
    val paymentMethod: String,
    val paymentStatus: String,
    val timestamp: Any,  // ServerValue.TIMESTAMP when writing
    /** Inventory is decremented atomically with this kiosk order, never by the web dashboard. */
    val inventoryProcessed: Boolean,
    val inventoryProcessedAt: Any,
    val orderSource: String
) {
    /** Converts to a plain Map for Firebase `setValue()`. */
    fun toMap(): Map<String, Any?> = mapOf(
        "orderId" to orderId,
        "submittedByUid" to submittedByUid,
        "orderNumber" to orderNumber,
        "customerName" to customerName,
        "items" to items.map { it.toMap() },
        "total" to total,
        "paymentMethod" to paymentMethod,
        "paymentStatus" to paymentStatus,
        "timestamp" to timestamp,
        "inventoryProcessed" to inventoryProcessed,
        "inventoryProcessedAt" to inventoryProcessedAt,
        "orderSource" to orderSource
    )

    companion object {
        fun fromOrder(order: Order, submittedByUid: String): OrderLogEntry = OrderLogEntry(
            orderId = order.id,
            submittedByUid = submittedByUid,
            orderNumber = order.orderNumber,
            customerName = order.customerName,
            items = order.items.map { OrderLogItem.fromCartItem(it) },
            total = order.total,
            paymentMethod = order.paymentMethod?.name ?: "UNKNOWN",
            paymentStatus = order.paymentStatus?.name ?: "UNKNOWN",
            timestamp = ServerValue.TIMESTAMP,
            inventoryProcessed = true,
            inventoryProcessedAt = ServerValue.TIMESTAMP,
            orderSource = ANDROID_KIOSK_SOURCE
        )

        private const val ANDROID_KIOSK_SOURCE = "android_kiosk"
    }
}

/** Single line-item in the order log — a flat DTO (no nested MenuItem). */
data class OrderLogItem(
    val name: String,
    /** Selected size label ("Medium", "Large"). Empty string if no size applies. */
    val size: String,
    val quantity: Int,
    val price: Double,
    val subtotal: Double
) {
    fun toMap(): Map<String, Any> = mapOf(
        "name" to name,
        "size" to size,
        "quantity" to quantity,
        "price" to price,
        "subtotal" to subtotal
    )

    companion object {
        fun fromCartItem(cartItem: CartItem): OrderLogItem = OrderLogItem(
            name = cartItem.menuItem.name,
            size = cartItem.selectedSize,
            quantity = cartItem.quantity,
            // Use the size-adjusted price stored in CartItem
            price = cartItem.price,
            subtotal = cartItem.price * cartItem.quantity
        )
    }
}
