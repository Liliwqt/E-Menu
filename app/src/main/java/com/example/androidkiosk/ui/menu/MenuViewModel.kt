package com.example.androidkiosk.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidkiosk.admin.AuthManager
import com.example.androidkiosk.admin.DeviceAuthorizationState
import com.example.androidkiosk.domain.repository.AppSettingsRepository
import com.example.androidkiosk.domain.repository.MenuRepository
import com.example.androidkiosk.domain.repository.OrderRepository
import com.example.androidkiosk.model.AppSettings
import com.example.androidkiosk.model.CartItem
import com.example.androidkiosk.model.CategoryWithItems
import com.example.androidkiosk.model.MenuItem
import com.example.androidkiosk.model.Order
import com.example.androidkiosk.model.PaymentMethod
import com.example.androidkiosk.model.PaymentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class OrderSubmissionState(
    val orderId: String? = null,
    val isSubmitting: Boolean = false,
    val isComplete: Boolean = false,
    val errorMessage: String? = null,
    val qrImage: String? = null,
    val paymentStatus: String? = null,
    val expiresAt: Long = 0L
)

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
    private val orderRepository: OrderRepository,
    appSettingsRepository: AppSettingsRepository,
    private val authManager: AuthManager
) : ViewModel() {

    val authorizationState: StateFlow<DeviceAuthorizationState> = authManager.authorizationState
    val subscriptionEndAt: StateFlow<Long?> = orderRepository.subscriptionEndAt
    val lifecycleNotice: StateFlow<String?> = orderRepository.lifecycleNotice

    val categories: StateFlow<List<CategoryWithItems>> = menuRepository
        .observeCategories()
        .catch { e ->
            Timber.e(e, "Error observing categories")
            _errorMessage.value = "Unable to load menu. Please try again."
            _isLoading.value = false
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bestSellers: StateFlow<List<MenuItem>> = menuRepository
        .observeBestSellers()
        .catch { e -> Timber.e(e, "Error observing best sellers") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appSettings: StateFlow<AppSettings> = appSettingsRepository
        .observeAppSettings()
        .catch { e -> Timber.e(e, "Error observing app settings") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val inventoryStock: StateFlow<Map<String, Map<String, Int>>> = menuRepository
        .observeInventoryStock()
        .catch { e -> Timber.e(e, "Error observing inventory stock") }
        // Cart validation also reads this state when no composable is collecting it.
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _submissionState = MutableStateFlow(OrderSubmissionState())
    private var qrPollingJob: Job? = null
    val submissionState: StateFlow<OrderSubmissionState> = _submissionState.asStateFlow()

    init {
        observeMenuData()
        viewModelScope.launch {
            authorizationState.collect { state ->
                if (state.isAuthorized) orderRepository.startSubscriptionObservation()
                else orderRepository.stopSubscriptionObservation()
            }
        }
    }

    private fun observeMenuData() {
        viewModelScope.launch {
            categories.collect { cats ->
                if (cats.isNotEmpty()) {
                    _isLoading.value = false
                    _errorMessage.value = null
                }
            }
        }
    }

    fun retryLoading() {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            runCatching { menuRepository.refresh() }
                .onFailure { error ->
                    Timber.e(error, "Unable to refresh menu")
                    _isLoading.value = false
                    _errorMessage.value = "Unable to load menu. Please try again."
                }
        }
    }

    fun retryAuthorization() {
        viewModelScope.launch { authManager.refreshAuthorization() }
    }

    /** Current per-size limit for an item, so the UI can disable its increase control at the real cap. */
    fun quantityLimit(item: MenuItem, selectedSize: String): MenuQuantityRules.QuantityLimit {
        val effectiveSize = MenuQuantityRules.resolveSize(item, selectedSize)
        val stockSize = effectiveSize.ifEmpty { MenuQuantityRules.DEFAULT_STOCK_SIZE }
        val itemStock = inventoryStock.value["${item.categoryName}/${item.id}"]
        return MenuQuantityRules.quantityLimit(itemStock, stockSize)
    }

    fun addToCartWithQuantity(item: MenuItem, quantity: Int, selectedSize: String = "") {
        if (quantity <= 0) return
        val sizeKey = "${item.categoryName}/${item.id}"
        val effectiveSize = MenuQuantityRules.resolveSize(item, selectedSize)
        val stockSize = effectiveSize.ifEmpty { MenuQuantityRules.DEFAULT_STOCK_SIZE }
        val itemStock = inventoryStock.value[sizeKey]
        val limit = MenuQuantityRules.quantityLimit(itemStock, stockSize).maxQuantity
        val effectivePrice = MenuQuantityRules.effectivePrice(item, effectiveSize)
        _cartItems.update { currentCart ->
            val existingIndex = currentCart.indexOfFirst {
                it.menuItem.id == item.id && it.selectedSize == effectiveSize
            }
            if (existingIndex >= 0) {
                val existing = currentCart[existingIndex]
                if (limit <= 0) return@update currentCart
                val newQty = (existing.quantity + quantity.coerceAtMost(limit)).coerceAtMost(limit)
                currentCart.toMutableList().apply {
                    this[existingIndex] = existing.copy(quantity = newQty)
                }
            } else {
                if (limit <= 0) return@update currentCart
                currentCart + CartItem(
                    menuItem = item,
                    price = effectivePrice,
                    quantity = quantity.coerceAtMost(limit),
                    selectedSize = effectiveSize
                )
            }
        }
    }

    fun removeFromCart(cartItem: CartItem) {
        _cartItems.update { currentCart ->
            currentCart.filter {
                !(it.menuItem.id == cartItem.menuItem.id && it.selectedSize == cartItem.selectedSize)
            }
        }
    }

    fun updateQuantity(cartItem: CartItem, newQuantity: Int) {
        if (newQuantity <= 0) {
            removeFromCart(cartItem)
            return
        }
        val sizeKey = "${cartItem.menuItem.categoryName}/${cartItem.menuItem.id}"
        val stockSize = cartItem.selectedSize.ifEmpty { MenuQuantityRules.DEFAULT_STOCK_SIZE }
        val itemStock = inventoryStock.value[sizeKey]
        val limit = MenuQuantityRules.quantityLimit(itemStock, stockSize).maxQuantity
        val clampedQuantity = newQuantity.coerceAtMost(limit)
        _cartItems.update { currentCart ->
            val index = currentCart.indexOfFirst {
                it.menuItem.id == cartItem.menuItem.id && it.selectedSize == cartItem.selectedSize
            }
            if (index >= 0) {
                if (clampedQuantity <= 0) {
                    return@update currentCart.filterIndexed { itemIndex, _ -> itemIndex != index }
                }
                currentCart.toMutableList().apply {
                    this[index] = this[index].copy(quantity = clampedQuantity)
                }
            } else {
                currentCart
            }
        }
    }

    private fun clearCart() {
        _cartItems.update { emptyList() }
    }

    fun confirmOrder(customerName: String): Order {
        val items = _cartItems.value
        require(items.isNotEmpty()) { "Cannot submit an empty cart" }
        val normalizedCustomerName = customerName.trim()
        require(normalizedCustomerName.isNotEmpty()) { "Customer name is required" }
        require(normalizedCustomerName.length <= MAX_CUSTOMER_NAME_LENGTH) {
            "Customer name must be $MAX_CUSTOMER_NAME_LENGTH characters or fewer"
        }
        val total = items.sumOf { it.price * it.quantity }
        val orderId = UUID.randomUUID().toString()
        val orderNumber = orderId.take(8).uppercase()
        val order = Order(
            id = orderId,
            orderNumber = orderNumber,
            customerName = normalizedCustomerName,
            items = items,
            total = total
        )
        _submissionState.value = OrderSubmissionState(orderId = order.id)
        return order
    }

    fun submitOrder(order: Order, method: PaymentMethod, status: PaymentStatus) {
        if (method != PaymentMethod.COUNTER || status != PaymentStatus.PAY_AT_COUNTER) {
            _submissionState.value = OrderSubmissionState(
                orderId = order.id,
                errorMessage = "QR Ph orders must use the verified payment flow."
            )
            return
        }
        if (!authorizationState.value.isAuthorized) {
            _submissionState.value = OrderSubmissionState(
                orderId = order.id,
                errorMessage = "This device is not registered. Ask an administrator to authorize its UID."
            )
            return
        }
        if ((subscriptionEndAt.value ?: 0L) <= System.currentTimeMillis()) {
            _submissionState.value = OrderSubmissionState(
                orderId = order.id,
                errorMessage = "Branch access is paused by expiry, cancellation or closure. Ask the owner to check Records and access."
            )
            return
        }
        if (_submissionState.value.isSubmitting ||
            (_submissionState.value.isComplete && _submissionState.value.orderId == order.id)
        ) return

        if (!hasEnoughStock(order)) {
            _submissionState.value = OrderSubmissionState(
                orderId = order.id,
                errorMessage = "Some items no longer have enough stock. Return to the cart and update the order."
            )
            return
        }

        val submittedOrder = order.copy(paymentMethod = method, paymentStatus = status)
        _submissionState.value = OrderSubmissionState(
            orderId = submittedOrder.id,
            isSubmitting = true
        )
        viewModelScope.launch {
            orderRepository.submitOrder(submittedOrder)
                .onSuccess {
                    clearCart()
                    _submissionState.value = OrderSubmissionState(
                        orderId = submittedOrder.id,
                        isComplete = true
                    )
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to submit order %s", submittedOrder.orderNumber)
                    _submissionState.value = OrderSubmissionState(
                        orderId = submittedOrder.id,
                        errorMessage = when {
                            error.message?.contains("plan expired", ignoreCase = true) == true -> error.message
                            error.message?.contains("price changed", ignoreCase = true) == true ->
                                "An item price changed. Return to the menu and review the cart."
                            error.message?.contains("unavailable", ignoreCase = true) == true ->
                                "An item is no longer available. Return to the menu and review the cart."
                            error.message?.contains("insufficient inventory", ignoreCase = true) == true ->
                                "Some items no longer have enough stock. Return to the cart and update the order."
                            else -> "Unable to submit the order. Check the connection and try again."
                        }
                    )
                }
        }
    }

    fun startQrPayment(order: Order) {
        if (_submissionState.value.isSubmitting || _submissionState.value.isComplete) return
        if (!authorizationState.value.isAuthorized) {
            _submissionState.value = OrderSubmissionState(orderId = order.id, errorMessage = "This device is not registered.")
            return
        }
        if ((subscriptionEndAt.value ?: 0L) <= System.currentTimeMillis()) {
            _submissionState.value = OrderSubmissionState(orderId = order.id, errorMessage = "Branch access is paused by expiry, cancellation or closure. Ask the owner to check Records and access.")
            return
        }
        qrPollingJob?.cancel()
        _submissionState.value = OrderSubmissionState(orderId = order.id, isSubmitting = true, paymentStatus = "creating")
        qrPollingJob = viewModelScope.launch {
            orderRepository.startQrCheckout(order)
                .onSuccess { checkout ->
                    applyQrCheckout(order, checkout)
                    while (_submissionState.value.paymentStatus in setOf("creating", "awaiting_payment")) {
                        delay(QR_POLL_INTERVAL_MS)
                        val refresh = if (_submissionState.value.paymentStatus == "creating") {
                            // Repeating create with the same order/attempt is provider-idempotent
                            // and recovers a process interruption before the QR was stored.
                            orderRepository.startQrCheckout(order)
                        } else {
                            orderRepository.getQrCheckout(order.id)
                        }
                        refresh.onSuccess { applyQrCheckout(order, it) }
                            .onFailure { error ->
                                Timber.w(error, "Unable to refresh QR payment %s", order.orderNumber)
                            }
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Unable to start QR payment %s", order.orderNumber)
                    _submissionState.value = OrderSubmissionState(
                        orderId = order.id,
                        errorMessage = error.message ?: "Verified QR Ph is unavailable. Use pay at counter."
                    )
                }
        }
    }

    private fun applyQrCheckout(order: Order, checkout: com.example.androidkiosk.model.QrCheckoutSession) {
        when {
            checkout.isPaid -> {
                clearCart()
                _submissionState.value = OrderSubmissionState(
                    orderId = order.id, isComplete = true, paymentStatus = "paid"
                )
            }
            checkout.isPending -> {
                _submissionState.value = OrderSubmissionState(
                    orderId = order.id, qrImage = checkout.qrImage, paymentStatus = checkout.status,
                    expiresAt = checkout.expiresAt
                )
            }
            else -> _submissionState.value = OrderSubmissionState(
                orderId = order.id, paymentStatus = checkout.status,
                errorMessage = when (checkout.status) {
                    "expired" -> "The QR code expired. No order was placed and reserved stock was released."
                    "cancelled" -> "QR payment was cancelled. No order was placed."
                    else -> "Payment was not completed. No order was placed; use pay at counter or try again."
                }
            )
        }
    }

    fun cancelQrPayment(orderId: String) {
        qrPollingJob?.cancel()
        val shouldCancel = _submissionState.value.paymentStatus in setOf("creating", "awaiting_payment")
        if (shouldCancel) viewModelScope.launch { orderRepository.cancelQrCheckout(orderId) }
        _submissionState.value = OrderSubmissionState()
    }

    fun resetOrderFlow() {
        qrPollingJob?.cancel()
        _submissionState.value = OrderSubmissionState()
    }

    private fun hasEnoughStock(order: Order): Boolean = order.items.all { cartItem ->
        val itemKey = "${cartItem.menuItem.categoryName}/${cartItem.menuItem.id}"
        val knownStock = inventoryStock.value[itemKey] ?: return@all true
        val sizeKey = cartItem.selectedSize.ifEmpty { MenuQuantityRules.DEFAULT_STOCK_SIZE }
        (knownStock[sizeKey] ?: 0) >= cartItem.quantity
    }

    private companion object {
        const val MAX_CUSTOMER_NAME_LENGTH = 80
        const val QR_POLL_INTERVAL_MS = 2_000L
    }
}
