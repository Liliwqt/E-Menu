package com.example.androidkiosk.domain.repository

import com.example.androidkiosk.model.Order
import com.example.androidkiosk.model.QrCheckoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Submits orders to Firebase and applies their inventory changes. */
interface OrderRepository {
    /** Null while billing loads; 0 when unavailable; the server expiry otherwise. */
    val subscriptionEndAt: StateFlow<Long?>
        get() = MutableStateFlow(Long.MAX_VALUE)
    /** Branch-specific retention/closure notice; never includes credentials. */
    val lifecycleNotice: StateFlow<String?>
        get() = MutableStateFlow(null)
    fun startSubscriptionObservation() {}
    fun stopSubscriptionObservation() {}
    /** Idempotently submit [order] to the provisioned branch logs path. */
    suspend fun submitOrder(order: Order): Result<Unit>
    suspend fun startQrCheckout(order: Order): Result<QrCheckoutSession> =
        Result.failure(UnsupportedOperationException("Verified QR Ph is unavailable"))
    suspend fun getQrCheckout(orderId: String): Result<QrCheckoutSession> =
        Result.failure(UnsupportedOperationException("Verified QR Ph is unavailable"))
    suspend fun cancelQrCheckout(orderId: String): Result<Unit> = Result.success(Unit)
}
