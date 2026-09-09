package com.example.androidkiosk.domain.repository

import com.example.androidkiosk.model.Order

/** Submits orders to Firebase and applies their inventory changes. */
interface OrderRepository {
    /** Idempotently submit [order] to the provisioned branch logs path. */
    suspend fun submitOrder(order: Order): Result<Unit>
}
