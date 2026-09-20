package com.example.androidkiosk.ui.menu

import com.example.androidkiosk.admin.AuthManager
import com.example.androidkiosk.admin.DeviceAuthorizationState
import com.example.androidkiosk.admin.DeviceRegistrationStatus
import com.example.androidkiosk.domain.repository.AppSettingsRepository
import com.example.androidkiosk.domain.repository.MenuRepository
import com.example.androidkiosk.domain.repository.OrderRepository
import com.example.androidkiosk.model.AppSettings
import com.example.androidkiosk.model.CategoryWithItems
import com.example.androidkiosk.model.MenuItem
import com.example.androidkiosk.model.Order
import com.example.androidkiosk.model.PaymentMethod
import com.example.androidkiosk.model.PaymentStatus
import com.example.androidkiosk.model.SizeOption
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MenuViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var menuRepository: MenuRepository
    private lateinit var orderRepository: RecordingOrderRepository
    private lateinit var settingsRepository: AppSettingsRepository
    private lateinit var authManager: AuthManager
    private lateinit var authorizationState: kotlinx.coroutines.flow.MutableStateFlow<DeviceAuthorizationState>

    private val sizedItem = MenuItem(
        id = "coffee",
        categoryName = "Drinks",
        name = "Coffee",
        price = 100.0,
        sizes = linkedMapOf(
            "Medium" to SizeOption(),
            "Large" to SizeOption(25.0)
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        menuRepository = mockk(relaxed = true)
        orderRepository = RecordingOrderRepository()
        settingsRepository = mockk(relaxed = true)
        authManager = mockk(relaxed = true)
        authorizationState = kotlinx.coroutines.flow.MutableStateFlow(
            DeviceAuthorizationState("registered-test-uid", DeviceRegistrationStatus.AUTHORIZED)
        )
        every { authManager.authorizationState } returns authorizationState
        every { menuRepository.observeCategories() } returns flowOf(
            listOf(CategoryWithItems("Drinks", listOf(sizedItem)))
        )
        every { menuRepository.observeBestSellers() } returns flowOf(listOf(sizedItem))
        every { menuRepository.observeInventoryStock() } returns flowOf(
            mapOf("Drinks/coffee" to mapOf("Medium" to 2, "Large" to 1))
        )
        every { settingsRepository.observeAppSettings() } returns flowOf(AppSettings())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sizes create separate cart lines and apply modifiers`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.addToCartWithQuantity(sizedItem, 1, "Medium")
        viewModel.addToCartWithQuantity(sizedItem, 1, "Large")

        assertEquals(2, viewModel.cartItems.value.size)
        assertEquals(100.0, viewModel.cartItems.value.first { it.selectedSize == "Medium" }.price, 0.0)
        assertEquals(125.0, viewModel.cartItems.value.first { it.selectedSize == "Large" }.price, 0.0)
    }

    @Test
    fun `known inventory caps quantity and missing known size is unavailable`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val itemWithMissingSize = sizedItem.copy(
            sizes = sizedItem.sizes + ("Small" to SizeOption(-10.0))
        )

        viewModel.addToCartWithQuantity(sizedItem, 10, "Medium")
        viewModel.addToCartWithQuantity(itemWithMissingSize, 1, "Small")

        assertEquals(2, viewModel.cartItems.value.single().quantity)
    }

    @Test
    fun `missing inventory item remains legacy untracked`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val legacyItem = sizedItem.copy(id = "legacy")

        viewModel.addToCartWithQuantity(legacyItem, 4, "Medium")

        assertEquals(4, viewModel.cartItems.value.single().quantity)
    }

    @Test
    fun `confirmed order uses full stable id and size adjusted total`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addToCartWithQuantity(sizedItem, 1, "Large")

        val first = viewModel.confirmOrder(" Guest ")

        assertEquals(36, first.id.length)
        assertEquals(8, first.orderNumber.length)
        assertEquals("Guest", first.customerName)
        assertEquals(125.0, first.total, 0.0)
        assertNotEquals(first.id, first.orderNumber)
    }

    @Test
    fun `successful submission clears cart and records explicit status`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addToCartWithQuantity(sizedItem, 1, "Medium")
        val order = viewModel.confirmOrder("Guest")

        viewModel.submitOrder(
            order,
            PaymentMethod.QR_CODE,
            PaymentStatus.CUSTOMER_REPORTED_PAID
        )
        advanceUntilIdle()

        assertTrue(viewModel.cartItems.value.isEmpty())
        assertTrue(viewModel.submissionState.value.isComplete)
        assertFalse(viewModel.submissionState.value.isSubmitting)
        assertEquals(1, orderRepository.submittedOrders.size)
        assertEquals(order.id, orderRepository.submittedOrders.single().id)
        assertEquals(PaymentMethod.QR_CODE, orderRepository.submittedOrders.single().paymentMethod)
        assertEquals(
            PaymentStatus.CUSTOMER_REPORTED_PAID,
            orderRepository.submittedOrders.single().paymentStatus
        )
    }

    @Test
    fun `double tap submits an order once`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addToCartWithQuantity(sizedItem, 1, "Medium")
        val order = viewModel.confirmOrder("Guest")

        repeat(2) {
            viewModel.submitOrder(order, PaymentMethod.COUNTER, PaymentStatus.PAY_AT_COUNTER)
        }
        advanceUntilIdle()

        assertEquals(1, orderRepository.submittedOrders.size)
    }

    @Test
    fun `failed submission preserves cart and retry reuses stable id`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addToCartWithQuantity(sizedItem, 1, "Medium")
        val order = viewModel.confirmOrder("Guest")
        orderRepository.nextResult = Result.failure(IllegalStateException("offline"))

        viewModel.submitOrder(order, PaymentMethod.COUNTER, PaymentStatus.PAY_AT_COUNTER)
        advanceUntilIdle()

        assertFalse(viewModel.cartItems.value.isEmpty())
        assertTrue(viewModel.submissionState.value.errorMessage != null)

        orderRepository.nextResult = Result.success(Unit)
        viewModel.submitOrder(order, PaymentMethod.COUNTER, PaymentStatus.PAY_AT_COUNTER)
        advanceUntilIdle()

        assertTrue(viewModel.cartItems.value.isEmpty())
        assertTrue(viewModel.submissionState.value.isComplete)
        assertEquals(listOf(order.id, order.id), orderRepository.submittedOrders.map { it.id })
    }

    @Test
    fun `authorization revocation preserves cart and blocks submission`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addToCartWithQuantity(sizedItem, 1, "Medium")
        val order = viewModel.confirmOrder("Guest")
        authorizationState.value = DeviceAuthorizationState(
            uid = "registered-test-uid",
            status = DeviceRegistrationStatus.PENDING_REGISTRATION
        )

        viewModel.submitOrder(order, PaymentMethod.COUNTER, PaymentStatus.PAY_AT_COUNTER)
        advanceUntilIdle()

        assertTrue(viewModel.cartItems.value.isNotEmpty())
        assertTrue(viewModel.submissionState.value.errorMessage?.contains("not registered") == true)
        assertTrue(orderRepository.submittedOrders.isEmpty())
    }

    @Test
    fun `decrementing to zero removes the cart line`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addToCartWithQuantity(sizedItem, 2, "Medium")
        val line = viewModel.cartItems.value.single()

        viewModel.updateQuantity(line, 0)

        assertTrue(viewModel.cartItems.value.isEmpty())
    }

    @Test
    fun `increase beyond tracked stock is clamped`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addToCartWithQuantity(sizedItem, 1, "Medium")
        val line = viewModel.cartItems.value.single()

        viewModel.updateQuantity(line, 99)

        assertEquals(2, viewModel.cartItems.value.single().quantity)
    }

    @Test
    fun `quantity limit reports its source for the ui`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val stockLimit = viewModel.quantityLimit(sizedItem, "Medium")
        assertEquals(MenuQuantityRules.LimitSource.STOCK, stockLimit.source)
        assertEquals(2, stockLimit.maxQuantity)

        val untrackedLimit = viewModel.quantityLimit(sizedItem.copy(id = "legacy"), "Medium")
        assertEquals(MenuQuantityRules.LimitSource.MAX_ORDER_QUANTITY, untrackedLimit.source)
        assertEquals(99, untrackedLimit.maxQuantity)
    }

    @Test
    fun `failed qr submission preserves cart and retry reuses stable id`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addToCartWithQuantity(sizedItem, 1, "Large")
        val order = viewModel.confirmOrder("Guest")
        orderRepository.nextResult = Result.failure(IllegalStateException("offline"))

        viewModel.submitOrder(order, PaymentMethod.QR_CODE, PaymentStatus.CUSTOMER_REPORTED_PAID)
        advanceUntilIdle()

        assertFalse(viewModel.cartItems.value.isEmpty())
        assertTrue(viewModel.submissionState.value.errorMessage != null)

        orderRepository.nextResult = Result.success(Unit)
        viewModel.submitOrder(order, PaymentMethod.QR_CODE, PaymentStatus.CUSTOMER_REPORTED_PAID)
        advanceUntilIdle()

        assertTrue(viewModel.submissionState.value.isComplete)
        assertEquals(listOf(order.id, order.id), orderRepository.submittedOrders.map { it.id })
        assertEquals(
            listOf(PaymentMethod.QR_CODE, PaymentMethod.QR_CODE),
            orderRepository.submittedOrders.map { it.paymentMethod }
        )
    }

    private fun createViewModel() = MenuViewModel(
        menuRepository,
        orderRepository,
        settingsRepository,
        authManager
    )

    private class RecordingOrderRepository : OrderRepository {
        val submittedOrders = mutableListOf<Order>()
        var nextResult: Result<Unit> = Result.success(Unit)

        override suspend fun submitOrder(order: Order): Result<Unit> {
            submittedOrders += order
            return nextResult
        }
    }
}
