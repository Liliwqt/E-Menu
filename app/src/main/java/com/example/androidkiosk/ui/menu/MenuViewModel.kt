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
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class OrderSubmissionState(
    val orderId: String? = null,
    val isSubmitting: Boolean = false,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
    private val orderRepository: OrderRepository,
    appSettingsRepository: AppSettingsRepository,
    private val authManager: AuthManager
) : ViewModel() {

    val authorizationState: StateFlow<DeviceAuthorizationState> = authManager.authorizationState

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
    val submissionState: StateFlow<OrderSubmissionState> = _submissionState.asStateFlow()

    init {
        observeMenuData()
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
        if (!authorizationState.value.isAuthorized) {
            _submissionState.value = OrderSubmissionState(
                orderId = order.id,
                errorMessage = "This device is not registered. Ask an administrator to authorize its UID."
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
                        errorMessage = "Unable to submit the order. Check the connection and try again."
                    )
                }
        }
    }

    fun resetOrderFlow() {
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
    }
}
