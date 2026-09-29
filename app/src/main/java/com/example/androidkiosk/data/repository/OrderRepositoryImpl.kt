package com.example.androidkiosk.data.repository

import com.example.androidkiosk.BuildConfig
import com.example.androidkiosk.admin.AuthManager
import com.example.androidkiosk.domain.repository.OrderRepository
import com.example.androidkiosk.model.Order
import com.example.androidkiosk.model.PaymentMethod
import com.example.androidkiosk.model.PaymentStatus
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase,
    private val authManager: AuthManager,
    private val branchPathProvider: BranchPathProvider
) : OrderRepository {

    private val _subscriptionEndAt = MutableStateFlow<Long?>(null)
    override val subscriptionEndAt: StateFlow<Long?> = _subscriptionEndAt.asStateFlow()
    private var entitlementRef: DatabaseReference? = null
    private var entitlementListener: ValueEventListener? = null
    private var observationGeneration = 0

    override fun startSubscriptionObservation() {
        if (!branchPathProvider.isConfigured || entitlementListener != null) return
        val reference = database.getReference(
            "billingEntitlements/${branchPathProvider.companyId}/${branchPathProvider.branchId}"
        )
        val generation = ++observationGeneration
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (generation != observationGeneration) return
                _subscriptionEndAt.value = if (
                    snapshot.child("subscriptionStatus").getValue(String::class.java) in listOf("trialing", "active")
                ) snapshot.child("periodEndAt").getValue(Long::class.java) ?: 0L else 0L
            }
            override fun onCancelled(error: DatabaseError) {
                if (generation != observationGeneration) return
                _subscriptionEndAt.value = 0L
                Timber.e(error.toException(), "Unable to read branch subscription")
            }
        }
        entitlementRef = reference
        entitlementListener = listener
        _subscriptionEndAt.value = null
        reference.addValueEventListener(listener)
    }

    override fun stopSubscriptionObservation() {
        observationGeneration++
        entitlementListener?.let { listener -> entitlementRef?.removeEventListener(listener) }
        entitlementListener = null
        entitlementRef = null
        _subscriptionEndAt.value = null
    }

    override suspend fun submitOrder(order: Order): Result<Unit> {
        return try {
            validateOrder(order)
            check(authManager.authorizationState.value.isAuthorized) {
                "This device is not registered"
            }
            val token = authManager.idToken()
            val body = orderRequestJson(order, branchPathProvider.companyId, branchPathProvider.branchId)
            val response = withContext(Dispatchers.IO) {
                val connection = (URL(BuildConfig.ORDER_API_URL.trimEnd('/') + "/api/orders")
                    .openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 25000
                    connection.doOutput = true
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val status = connection.responseCode
                    val responseText = (if (status in 200..299) connection.inputStream
                    else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (status !in 200..299) {
                        val detail = runCatching {
                            Json.parseToJsonElement(responseText).jsonObject["detail"]
                                ?.jsonPrimitive?.content
                        }.getOrNull()
                        error(detail ?: "Order service unavailable ($status). Retry with the same order.")
                    }
                    responseText
                } finally {
                    connection.disconnect()
                }
            }
            val result = Json.parseToJsonElement(response).jsonObject
            check(result["orderId"]?.jsonPrimitive?.content == order.id) {
                "Order confirmation did not match the submitted order"
            }
            check(result["orderNumber"]?.jsonPrimitive?.content == order.orderNumber) {
                "Order confirmation number did not match"
            }
            Timber.i("Order %s submitted through trusted checkout", order.orderNumber)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (error.message?.contains("Device registration is inactive") == true) {
                authManager.reportAuthorizationDenied()
            }
            Timber.e(error, "Failed to submit order %s", order.orderNumber)
            Result.failure(error)
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
