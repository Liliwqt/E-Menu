package com.example.androidkiosk.data.repository

import com.example.androidkiosk.admin.AuthManager
import com.example.androidkiosk.domain.repository.OrderRepository
import com.example.androidkiosk.model.Order
import com.example.androidkiosk.model.OrderLogEntry
import com.example.androidkiosk.model.PaymentMethod
import com.example.androidkiosk.model.PaymentStatus
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase,
    private val authManager: AuthManager,
    private val branchPathProvider: BranchPathProvider
) : OrderRepository {

    override suspend fun submitOrder(order: Order): Result<Unit> {
        return try {
            validateOrder(order)

            check(authManager.authorizationState.value.isAuthorized) {
                "This kiosk UID is not registered"
            }

            requireNotNull(order.paymentMethod) { "Payment method is required" }
            requireNotNull(order.paymentStatus) { "Payment status is required" }

            val orderRef = database.getReference("${branchPathProvider.branchPath}/logs/${order.id}")
            if (orderRef.get().await().exists()) {
                Timber.i("Order %s was already submitted", order.orderNumber)
                return Result.success(Unit)
            }

            val userId = requireNotNull(authManager.userId) { "Authenticated user is unavailable" }
            val entry = OrderLogEntry.fromOrder(order, userId)
            val updates = mutableMapOf<String, Any>()

            updates["${branchPathProvider.branchPath}/logs/${order.id}"] = entry.toMap()

            // Re-read stock immediately before submission. Missing item records are legacy/untracked;
            // existing item records must contain the selected size and have enough stock.
            val inventory = FirebaseMenuMapper.parseInventory(
                database.getReference("${branchPathProvider.branchPath}/inventory").get().await().value
            )
            val quantitiesByStockPath = order.items.groupingBy { item ->
                val size = item.selectedSize.ifEmpty { DEFAULT_STOCK_SIZE }
                "${item.menuItem.categoryName}/${item.menuItem.id}/$size"
            }.fold(0) { total, item -> total + item.quantity }

            for ((stockKey, quantity) in quantitiesByStockPath) {
                val parts = stockKey.split('/', limit = 3)
                val itemKey = "${parts[0]}/${parts[1]}"
                val knownItemStock = inventory[itemKey] ?: continue
                val currentStock = requireNotNull(knownItemStock[parts[2]]) {
                    "Selected size is not tracked in inventory"
                }
                require(currentStock >= quantity) { "Insufficient inventory stock" }
                updates["${branchPathProvider.branchPath}/inventory/${parts[0]}/${parts[1]}/sizes/${parts[2]}/stock"] =
                    ServerValue.increment(-quantity.toLong())
            }

            // The log and all tracked stock decrements are one multi-location write.
            database.getReference().updateChildren(updates).await()

            Timber.i("Order %s submitted and inventory updated", order.orderNumber)
            Result.success(Unit)
        } catch (e: Exception) {
            if (e.message?.contains("permission denied", ignoreCase = true) == true) {
                authManager.reportAuthorizationDenied()
            }
            Timber.e(e, "Failed to submit order %s", order.orderNumber)
            Result.failure(e)
        }
    }

    private fun validateOrder(order: Order) {
        require(runCatching { UUID.fromString(order.id) }.isSuccess) { "Order ID must be a UUID" }
        require(order.orderNumber.matches(Regex("[A-F0-9]{8}"))) { "Invalid display order number" }
        require(order.orderNumber == order.id.take(8).uppercase()) { "Display order number does not match ID" }
        require(
            order.customerName.isNotBlank() &&
                order.customerName == order.customerName.trim() &&
                order.customerName.length <= 80
        ) {
            "Invalid customer name"
        }
        require(order.items.isNotEmpty()) { "Order must contain at least one item" }
        require(order.paymentMethod != null) { "Payment method is required" }
        require(order.paymentStatus != null) { "Payment status is required" }
        require(
            (order.paymentMethod == PaymentMethod.QR_CODE &&
                order.paymentStatus == PaymentStatus.CUSTOMER_REPORTED_PAID) ||
                (order.paymentMethod == PaymentMethod.COUNTER &&
                    order.paymentStatus == PaymentStatus.PAY_AT_COUNTER)
        ) { "Payment method and status do not match" }
        require(order.items.all { item ->
            item.menuItem.id.isNotBlank() &&
                item.menuItem.categoryName.isNotBlank() &&
                item.menuItem.id.isFirebasePathSegment() &&
                item.menuItem.categoryName.isFirebasePathSegment() &&
                item.menuItem.name.isNotBlank() &&
                item.quantity in 1..99 &&
                item.price.isFinite() && item.price >= 0.0 &&
                if (item.menuItem.sizes.isEmpty()) {
                    item.selectedSize.isEmpty()
                } else {
                    item.selectedSize in item.menuItem.sizes && item.selectedSize.isFirebasePathSegment()
                }
        }) { "Order contains an invalid line item" }

        require(order.items.all { item ->
            val expected = item.menuItem.price +
                (item.menuItem.sizes[item.selectedSize]?.priceModifier ?: 0.0)
            expected.isFinite() && expected >= 0.0 && abs(expected - item.price) < 0.005
        }) { "Order line price does not match menu pricing" }

        val calculatedTotal = order.items.sumOf { it.price * it.quantity }
        require(order.total.isFinite() && order.total >= 0.0 && abs(calculatedTotal - order.total) < 0.005) {
            "Order total does not match its line items"
        }
    }

    private fun String.isFirebasePathSegment(): Boolean =
        isNotBlank() && none { it == '.' || it == '#' || it == '$' || it == '[' || it == ']' || it == '/' }

    private companion object { const val DEFAULT_STOCK_SIZE = "Medium" }
}
