package com.example.androidkiosk.data.repository

import com.example.androidkiosk.model.Order
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Carries the displayed cart prices so the server can reject a stale menu. */
internal fun orderRequestJson(order: Order, companyId: String, branchId: String): String =
    buildJsonObject {
        put("companyId", companyId)
        put("branchId", branchId)
        put("orderId", order.id)
        put("customerName", order.customerName)
        put("paymentMethod", requireNotNull(order.paymentMethod).name)
        put("expectedTotal", order.total)
        put("items", buildJsonArray {
            order.items.forEach { line ->
                add(buildJsonObject {
                    put("categoryId", line.menuItem.categoryName)
                    put("itemId", line.menuItem.id)
                    put("size", line.selectedSize)
                    put("quantity", line.quantity)
                    put("expectedUnitPrice", line.price)
                })
            }
        })
    }.toString()


/** QR checkout omits client payment status; only the provider can establish it. */
internal fun qrCheckoutRequestJson(order: Order, companyId: String, branchId: String): String =
    buildJsonObject {
        put("companyId", companyId)
        put("branchId", branchId)
        put("orderId", order.id)
        put("customerName", order.customerName)
        put("expectedTotal", order.total)
        put("items", buildJsonArray {
            order.items.forEach { line ->
                add(buildJsonObject {
                    put("categoryId", line.menuItem.categoryName)
                    put("itemId", line.menuItem.id)
                    put("size", line.selectedSize)
                    put("quantity", line.quantity)
                    put("expectedUnitPrice", line.price)
                })
            }
        })
    }.toString()
