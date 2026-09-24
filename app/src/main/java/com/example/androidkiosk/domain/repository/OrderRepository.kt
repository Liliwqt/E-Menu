package com.example.androidkiosk.domain.repository

import com.example.androidkiosk.model.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Submits orders to Firebase and applies their inventory changes. */
interface OrderRepository {
    /** Null while billing loads; 0 when unavailable; the server expiry otherwise. */
    val subscriptionEndAt: StateFlow<Long?>
        get() = MutableStateFlow(Long.MAX_VALUE)
    fun startSubscriptionObservation() {}
    fun stopSubscriptionObservation() {}
    /** Idempotently submit [order] to the provisioned branch logs path. */
    suspend fun submitOrder(order: Order): Result<Unit>
}
