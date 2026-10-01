package com.example.androidkiosk.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.PropertyName
import kotlinx.serialization.Serializable

/** A single size option for a drink item. */
@Serializable
data class SizeOption(val priceModifier: Double = 0.0)

data class MenuItem(
    val id: String = "",
    /** Category this item belongs to. Populated from the entity, not from Firebase directly. */
    @get:Exclude val categoryName: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val imageUrl: String = "",
    val available: Boolean = true,
    @get:PropertyName("isBestSeller")
    var isBestSeller: Boolean = false,
    /** Size options keyed by size name ("Medium", "Large"). Populated from the entity. */
    @get:Exclude val sizes: Map<String, SizeOption> = emptyMap()
)

data class CategoryWithItems(
    val categoryName: String,
    val items: List<MenuItem>
)

data class CartItem(
    val menuItem: MenuItem,
    /** Size-adjusted effective price (base price + priceModifier for the selected size). */
    val price: Double,
    var quantity: Int,
    /** Selected size label ("Medium", "Large"). Empty string means no size applies. */
    val selectedSize: String = ""
)

enum class PaymentMethod {
    QR_CODE,
    COUNTER
}

enum class PaymentStatus {
    CUSTOMER_REPORTED_PAID,
    PAY_AT_COUNTER
}

data class QrCheckoutSession(
    val orderId: String,
    val orderNumber: String,
    val total: Double,
    val status: String,
    val expiresAt: Long,
    val qrImage: String? = null
) {
    val isPaid: Boolean get() = status == "paid"
    val isPending: Boolean get() = status == "creating" || status == "awaiting_payment"
}

data class Order(
    val id: String,
    val orderNumber: String,
    val customerName: String = "",
    val items: List<CartItem>,
    val total: Double,
    val paymentMethod: PaymentMethod? = null,
    val paymentStatus: PaymentStatus? = null
)
