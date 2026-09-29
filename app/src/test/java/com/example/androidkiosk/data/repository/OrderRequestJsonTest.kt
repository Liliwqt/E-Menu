package com.example.androidkiosk.data.repository

import com.example.androidkiosk.model.CartItem
import com.example.androidkiosk.model.MenuItem
import com.example.androidkiosk.model.Order
import com.example.androidkiosk.model.PaymentMethod
import com.example.androidkiosk.model.PaymentStatus
import com.example.androidkiosk.model.SizeOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderRequestJsonTest {
    @Test
    fun retryPayloadKeepsTheOrderIdAndDisplayedSizePrice() {
        val item = MenuItem(
            id = "coffee", categoryName = "Drinks", name = "Coffee", price = 100.0,
            sizes = mapOf("Medium" to SizeOption(10.0))
        )
        val order = Order(
            id = "123e4567-e89b-42d3-a456-426614174001",
            orderNumber = "123E4567",
            customerName = "Guest",
            items = listOf(CartItem(item, 110.0, 2, "Medium")),
            total = 220.0,
            paymentMethod = PaymentMethod.COUNTER,
            paymentStatus = PaymentStatus.PAY_AT_COUNTER
        )
        val first = orderRequestJson(order, "company-cafe", "branch-main")
        val retry = orderRequestJson(order, "company-cafe", "branch-main")
        assertEquals(first, retry)
        val json = Json.parseToJsonElement(first).jsonObject
        assertEquals(order.id, json["orderId"]?.jsonPrimitive?.content)
        assertEquals("company-cafe", json["companyId"]?.jsonPrimitive?.content)
        assertEquals("branch-main", json["branchId"]?.jsonPrimitive?.content)
        assertEquals("COUNTER", json["paymentMethod"]?.jsonPrimitive?.content)
        assertNull(json["paymentStatus"])
        val line = json["items"]!!.jsonArray.single().jsonObject
        assertEquals("Drinks", line["categoryId"]?.jsonPrimitive?.content)
        assertEquals("coffee", line["itemId"]?.jsonPrimitive?.content)
        assertEquals("Medium", line["size"]?.jsonPrimitive?.content)
        assertEquals("2", line["quantity"]?.jsonPrimitive?.content)
        assertEquals("110.0", line["expectedUnitPrice"]?.jsonPrimitive?.content)
    }
}
