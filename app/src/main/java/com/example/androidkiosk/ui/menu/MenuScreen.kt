@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.androidkiosk.ui.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import coil3.compose.AsyncImage
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.LocalActivity
import com.example.androidkiosk.R
import com.example.androidkiosk.admin.PinManager
import com.example.androidkiosk.admin.DeviceRegistrationStatus
import com.example.androidkiosk.admin.UnlockMethod
import com.example.androidkiosk.ui.theme.LocalBackgroundImageUrl
import com.example.androidkiosk.ui.theme.LocalBackgroundTheme
import com.example.androidkiosk.util.ImageUrlValidator
import com.example.androidkiosk.model.CartItem
import com.example.androidkiosk.model.CategoryWithItems
import com.example.androidkiosk.model.MenuItem
import com.example.androidkiosk.model.Order
import com.example.androidkiosk.model.PaymentMethod
import com.example.androidkiosk.model.PaymentStatus
import com.example.androidkiosk.ui.menu.components.AdminPinDialog
import com.example.androidkiosk.ui.menu.components.CategoryPageContent
import com.example.androidkiosk.ui.menu.components.CounterPaymentOverlay
import com.example.androidkiosk.ui.menu.components.ErrorScreen
import com.example.androidkiosk.ui.menu.components.GlassCard
import com.example.androidkiosk.ui.menu.components.ItemDetailOverlay
import com.example.androidkiosk.ui.menu.components.LoadingScreen
import com.example.androidkiosk.ui.menu.components.DeviceRegistrationScreen
import com.example.androidkiosk.ui.menu.components.MenuItemCard
import com.example.androidkiosk.ui.menu.components.MenuModeSelectionScreen
import com.example.androidkiosk.ui.menu.components.PaymentMethodOverlay
import com.example.androidkiosk.ui.menu.components.QRPaymentOverlay
import com.example.androidkiosk.ui.menu.components.SideCategoryPanel
import com.example.androidkiosk.ui.menu.components.categoryIcon
import androidx.compose.material3.VerticalDivider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Enum to track UI mode: current vertical design, horizontal slide, or portrait design */
enum class UIMode {
    CURRENT,
    NEW_HORIZONTAL,
    PORTRAIT
}

@Composable
fun MenuScreen(
    viewModel: MenuViewModel,
    showPinDialog: Boolean = false,
    isAdminUnlocked: Boolean = false,
    unlockMethod: UnlockMethod = UnlockMethod.ADMIN_BUTTON,
    pinManager: PinManager? = null,
    onPinDialogDismiss: () -> Unit = {},
    onUnlockSuccess: (UnlockMethod) -> Unit = {},
    onRelockRequest: () -> Unit = {},
    onPinDialogRequest: (UnlockMethod) -> Unit = {},
    onPinFailed: (UnlockMethod) -> Unit = {}
) {
    val storageBucket = stringResource(R.string.google_storage_bucket)
    val bestSellers by viewModel.bestSellers.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val cartItems by viewModel.cartItems.collectAsState()
    val inventoryStock by viewModel.inventoryStock.collectAsState()
    val submissionState by viewModel.submissionState.collectAsState()
    val authorizationState by viewModel.authorizationState.collectAsState()
    val subscriptionEndAt by viewModel.subscriptionEndAt.collectAsState()
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(subscriptionEndAt) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }
    val isAuthorized = authorizationState.isAuthorized
    val orderingActive = isAuthorized && (subscriptionEndAt ?: 0L) > currentTime

    // Track UI mode — null means show the mode selection screen
    var selectedUIMode by remember { mutableStateOf<UIMode?>(null) }

    var selectedItem by remember { mutableStateOf<MenuItem?>(null) }
    var showCart by remember { mutableStateOf(false) }
    var showCheckout by remember { mutableStateOf(false) }
    var showPaymentMethod by remember { mutableStateOf(false) }
    var showQRPayment by remember { mutableStateOf(false) }
    var showCounterPayment by remember { mutableStateOf(false) }
    var currentOrder by remember { mutableStateOf<Order?>(null) }

    LaunchedEffect(orderingActive) {
        if (!orderingActive) {
            selectedItem = null
            showCart = false
            showCheckout = false
            showPaymentMethod = false
            showQRPayment = false
            showCounterPayment = false
        }
    }

    // Secret corner tap state: 5 taps in top-right corner within 3 seconds
    var cornerTapTimestamps by remember { mutableStateOf(listOf<Long>()) }

    // Programmatically control screen orientation based on selected UI mode.
    // The native menu is the ONLY surface that wants landscape; the two WebView surfaces
    // (setup/registration and the PIN-unlocked admin panel) are portrait portals, which is
    // also the manifest default (android:screenOrientation="portrait").
    val activity = LocalActivity.current
    DisposableEffect(selectedUIMode) {
        val orientation = if (selectedUIMode == UIMode.PORTRAIT)
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        activity?.requestedOrientation = orientation
        onDispose {
            // Leaving the native menu lands on a WebView surface, so hand the window back to
            // portrait. This used to force LANDSCAPE, which made the admin panel open sideways
            // when unlocked and left the orientation landscape on the way back out.
            // NOTE: this also runs when selectedUIMode *changes*; the new effect body below
            // then applies the new mode, so the final value is still correct.
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val blurAmount by animateFloatAsState(
        targetValue = if (selectedItem != null || showCart || showCheckout || showPaymentMethod || showQRPayment || showCounterPayment || showPinDialog) 10f else 0f,
        animationSpec = tween(150),
        label = "blur"
    )

    Box(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                // Detect taps in the top-right corner (100x100 dp area)
                val cornerSize = 100.dp.toPx()
                if (offset.x > size.width - cornerSize && offset.y < cornerSize) {
                    val now = System.currentTimeMillis()
                    val recentTaps = (cornerTapTimestamps + now)
                        .filter { it > now - 3000 } // Keep taps within last 3 seconds
                    if (recentTaps.size >= 5) {
                        cornerTapTimestamps = emptyList()
                        onPinDialogRequest(UnlockMethod.CORNER_TAP)
                    } else {
                        cornerTapTimestamps = recentTaps
                    }
                }
            }
        }
    ) {
        val bgTheme = LocalBackgroundTheme.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurAmount > 0f) Modifier.blur(blurAmount.dp) else Modifier)
        ) {
            // ── Dynamic background: theme-aware ─────────────────
            if (bgTheme.usesBackgroundImage) {
                // Light theme: use Firebase background image or default drawable
                val backgroundImageUrl = LocalBackgroundImageUrl.current
                val validatedBackgroundUrl = ImageUrlValidator.sanitize(backgroundImageUrl, storageBucket)
                if (!validatedBackgroundUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = validatedBackgroundUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        fallback = painterResource(id = R.drawable.menu_background),
                        error = painterResource(id = R.drawable.menu_background)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.menu_background),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                // Non-image themes: solid color or gradient background
                val bgBrush = bgTheme.backgroundBrush
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (bgBrush != null) Modifier.background(bgBrush)
                            else Modifier.background(bgTheme.backgroundColor)
                        )
                )
            }
            Scaffold(
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    if (orderingActive && selectedUIMode != null && !isLoading && errorMessage == null) {
                        CartSummaryBar(
                            cartItems = cartItems,
                            onViewCart = { showCart = true }
                        )
                    }
                }
            ) { paddingValues ->
                when {
                    !isAuthorized -> DeviceRegistrationScreen(
                        uid = authorizationState.uid,
                        message = when (authorizationState.status) {
                            DeviceRegistrationStatus.ERROR -> authorizationState.errorMessage
                            DeviceRegistrationStatus.AUTHENTICATING -> "Checking device registration…"
                            else -> null
                        },
                        onRetry = viewModel::retryAuthorization
                    )
                    isLoading -> LoadingScreen()
                    errorMessage != null -> {
                        ErrorScreen(
                            message = errorMessage ?: "Unknown error",
                            onRetry = { viewModel.retryLoading() }
                        )
                    }
                    else -> {
                        val uiMode = selectedUIMode
                        if (uiMode == null) {
                            // Show mode selection dialog
                            MenuModeSelectionScreen(
                                onModeSelected = { mode -> selectedUIMode = mode },
                                modifier = Modifier.padding(paddingValues)
                            )
                        } else {
                            // Show selected menu layout
                            Box(modifier = Modifier.padding(paddingValues)) {
                                when (uiMode) {
                                    UIMode.CURRENT -> {
                                        MenuContent(
                                            modifier = Modifier.fillMaxSize(),
                                            bestSellers = bestSellers,
                                            categories = categories,
                                            onItemClick = { item -> selectedItem = item }
                                        )
                                    }
                                    UIMode.NEW_HORIZONTAL -> {
                                        NewHorizontalMenuContent(
                                            modifier = Modifier.fillMaxSize(),
                                            bestSellers = bestSellers,
                                            categories = categories,
                                            onItemClick = { item -> selectedItem = item }
                                        )
                                    }
                                    UIMode.PORTRAIT -> {
                                        PortraitMenuContent(
                                            modifier = Modifier.fillMaxSize(),
                                            bestSellers = bestSellers,
                                            categories = categories,
                                            onItemClick = { item -> selectedItem = item }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (isAuthorized && !orderingActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(16.dp)
            ) {
                Text(
                    if (subscriptionEndAt == null) "Checking branch subscription…"
                    else "Branch plan expired. Ordering is paused until the owner renews.",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        // ── Overlays ───────────────────────────────────────────
        if (orderingActive) selectedItem?.let { item ->
            ItemDetailOverlay(
                item = item,
                stockBySize = inventoryStock["${item.categoryName}/${item.id}"],
                onDismiss = { selectedItem = null },
                onAddToCart = { menuItem, quantity, size ->
                    viewModel.addToCartWithQuantity(menuItem, quantity, size)
                }
            )
        }

        if (orderingActive && showCart) {
            CartOverlay(
                viewModel = viewModel,
                onDismiss = { showCart = false },
                onProceedToCheckout = { showCart = false; showCheckout = true }
            )
        }

        if (orderingActive && showCheckout) {
            CheckoutOverlay(
                viewModel = viewModel,
                onDismiss = { showCheckout = false },
                onOrderConfirmed = { order ->
                    currentOrder = order
                    showCheckout = false
                    showPaymentMethod = true
                }
            )
        }

        if (isAuthorized && showPaymentMethod && currentOrder != null) {
            PaymentMethodOverlay(
                order = currentOrder!!,
                onDismiss = { showPaymentMethod = false },
                onMethodSelected = { paymentMethod ->
                    showPaymentMethod = false
                    when (paymentMethod) {
                        PaymentMethod.QR_CODE -> {
                            showQRPayment = true
                        }
                        PaymentMethod.COUNTER -> {
                            showCounterPayment = true
                        }
                    }
                }
            )
        }

        if (isAuthorized && showQRPayment && currentOrder != null) {
            QRPaymentOverlay(
                order = currentOrder!!,
                isSubmitting = submissionState.isSubmitting,
                isComplete = submissionState.isComplete,
                errorMessage = submissionState.errorMessage,
                onPaid = {
                    currentOrder?.let {
                        viewModel.submitOrder(
                            it,
                            PaymentMethod.QR_CODE,
                            PaymentStatus.CUSTOMER_REPORTED_PAID
                        )
                    }
                },
                onDismiss = {
                    showQRPayment = false
                    showPaymentMethod = false
                    currentOrder = null
                    viewModel.resetOrderFlow()
                }
            )
        }

        if (isAuthorized && showCounterPayment && currentOrder != null) {
            CounterPaymentOverlay(
                order = currentOrder!!,
                isSubmitting = submissionState.isSubmitting,
                isComplete = submissionState.isComplete,
                errorMessage = submissionState.errorMessage,
                onSubmit = {
                    currentOrder?.let {
                        viewModel.submitOrder(
                            it,
                            PaymentMethod.COUNTER,
                            PaymentStatus.PAY_AT_COUNTER
                        )
                    }
                },
                onDismiss = {
                    showCounterPayment = false
                    showPaymentMethod = false
                    currentOrder = null
                    viewModel.resetOrderFlow()
                }
            )
        }

        // Admin PIN Dialog (shown when admin button is pressed or secret corner is tapped 5 times)
        if (showPinDialog && pinManager != null) {
            AdminPinDialog(
                pinManager = pinManager,
                onUnlockSuccess = { onUnlockSuccess(unlockMethod) },
                onDismiss = { onPinDialogDismiss() },
                onPinFailed = { onPinFailed(unlockMethod) }
            )
        }

        // Visible admin entry — opens the PIN dialog. The five-corner tap still works as a backup.
        val anyOverlayOpen = selectedItem != null || showCart || showCheckout || showPaymentMethod ||
            showQRPayment || showCounterPayment || showPinDialog
        if (isAuthorized && selectedUIMode != null && !isAdminUnlocked && !anyOverlayOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { onPinDialogRequest(UnlockMethod.ADMIN_BUTTON) },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = bgTheme.buttonContainerColor,
                        contentColor = bgTheme.buttonContentColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Admin settings"
                    )
                }
            }
        }

        // Exit control while the admin panel is open.
        if (isAdminUnlocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Button(
                    onClick = { onRelockRequest() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exit Admin")
                }
            }
        }
    }
}

@Composable
private fun NewHorizontalMenuContent(
    modifier: Modifier = Modifier,
    bestSellers: List<MenuItem>,
    categories: List<CategoryWithItems>,
    onItemClick: (MenuItem) -> Unit
) {
    val theme = LocalBackgroundTheme.current

    // Build the unified list: Best Sellers first, then remaining categories
    val allSections = remember(bestSellers, categories) {
        val otherCategories = categories.filter {
            it.categoryName.lowercase() != "best sellers"
        }
        buildList {
            if (bestSellers.isNotEmpty()) {
                add(CategoryWithItems("Best Sellers", bestSellers))
            }
            addAll(otherCategories)
        }
    }

    if (allSections.isEmpty()) return

    // Number of item rows to display per category column
    val itemsPerRow = 3
    val itemWidth = 170.dp
    val itemHeight = 220.dp
    val itemSpacing = 12.dp

    // Single continuous LazyRow spanning all categories
    LazyRow(
        modifier = modifier
            .fillMaxSize()
            .background(theme.surfaceOverlayColor.copy(alpha = 0.3f)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 12.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        allSections.forEachIndexed { sectionIndex, section ->
            // Each category section as a single LazyRow item
            item(key = "section_${section.categoryName}") {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp)
                ) {
                    // Category header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = categoryIcon(section.categoryName),
                            contentDescription = null,
                            tint = theme.accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = section.categoryName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = theme.primaryTextColor
                        )
                    }

                    // Items laid out as rows of `itemsPerRow`
                    // Chunk items into rows and lay them out vertically
                    val chunkedItems = remember(section.items) {
                        section.items.chunked(itemsPerRow)
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(itemSpacing),
                        modifier = Modifier.weight(1f)
                    ) {
                        chunkedItems.forEach { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(itemSpacing)
                            ) {
                                rowItems.forEach { item ->
                                MenuItemCard(
                                    item = item,
                                    modifier = Modifier
                                        .width(itemWidth)
                                        .height(itemHeight),
                                    onClick = { onItemClick(item) }
                                )
                                }
                            }
                        }
                    }
                }
            }

            // Vertical divider between categories
            if (sectionIndex < allSections.size - 1) {
                item(key = "divider_$sectionIndex") {
                    VerticalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 8.dp),
                        thickness = 1.dp,
                        color = theme.outlineVariantColor
                    )
                }
            }
        }
    }
}

// ── Portrait Menu Content ──────────────────────────────────────────
/**
 * Portrait-oriented menu layout with a **horizontal top category panel**
 * and a vertically scrollable list of all categories and items below it.
 *
 * Layout structure:
 * ┌─────────────────────────────┐
 * │  [☆ Best] [☕ Bev] [🍕 ...]  │  ← horizontal scrollable top panel
 * ├─────────────────────────────┤  ← divider
 * │  ★ Best Sellers             │
 * │  ┌───┐ ┌───┐ ┌───┐         │
 * │  │   │ │   │ │   │         │  ← LazyColumn with grid-like rows
 * │  └───┘ └───┘ └───┘         │
 * │  ────────────────────       │  ← section divider
 * │  ☕ Beverages               │
 * │  ┌───┐ ┌───┐ ┌───┐         │
 * │  ...                       │
 * └─────────────────────────────┘
 */
@Composable
private fun PortraitMenuContent(
    modifier: Modifier = Modifier,
    bestSellers: List<MenuItem>,
    categories: List<CategoryWithItems>,
    onItemClick: (MenuItem) -> Unit
) {
    val theme = LocalBackgroundTheme.current

    // Build unified category list: Best Sellers first, then the rest
    val allCategories = remember(bestSellers, categories) {
        val otherCategories = categories.filter {
            it.categoryName.lowercase() != "best sellers"
        }
        buildList {
            if (bestSellers.isNotEmpty()) {
                add(CategoryWithItems("Best Sellers", bestSellers))
            }
            addAll(otherCategories)
        }
    }

    if (allCategories.isEmpty()) return

    val scope = rememberCoroutineScope()
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Keep track of which category is selected (derived from scroll position)
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    // Calculate section start indices in the flat LazyColumn
    // Pattern per section: header (1) + item rows (ceil(items / 2)) + divider (1, except last)
    val itemsPerRow = 2
    val sectionStartIndices = remember(allCategories) {
        var runningIndex = 0
        allCategories.mapIndexed { index, cat ->
            val start = runningIndex
            val rowCount = (cat.items.size + itemsPerRow - 1) / itemsPerRow
            runningIndex += 1 + rowCount // header + rows
            if (index < allCategories.size - 1) runningIndex += 1 // divider
            start
        }
    }

    // Update selected category based on scroll position
    LaunchedEffect(lazyListState, sectionStartIndices) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }.collect { firstVisible ->
            val newIndex = sectionStartIndices.indexOfLast { it <= firstVisible }
            if (newIndex >= 0 && newIndex != selectedCategoryIndex) {
                selectedCategoryIndex = newIndex
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.surfaceOverlayColor.copy(alpha = 0.3f))
    ) {
        // ── Top horizontal category panel ──────────────────────────
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surfaceOverlayColor.copy(alpha = 0.15f))
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
        ) {
            items(allCategories.size) { index ->
                val isSelected = index == selectedCategoryIndex
                val bgColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isSelected)
                        theme.categorySelectedColor.copy(alpha = 0.2f)
                    else
                        Color.Transparent,
                    animationSpec = tween(200),
                    label = "portraitCatBg$index"
                )
                val iconTint by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isSelected)
                        theme.categorySelectedColor
                    else
                        theme.categoryUnselectedColor,
                    animationSpec = tween(200),
                    label = "portraitCatTint$index"
                )

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .clickable {
                            selectedCategoryIndex = index
                            scope.launch {
                                lazyListState.animateScrollToItem(sectionStartIndices[index])
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = categoryIcon(allCategories[index].categoryName),
                        contentDescription = allCategories[index].categoryName,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = allCategories[index].categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = iconTint,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        // ── Divider between category panel and content ─────────────
        HorizontalDivider(
            thickness = 1.dp,
            color = theme.outlineVariantColor
        )

        // ── Vertically scrollable content ──────────────────────────
        val itemSpacing = 16.dp

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 12.dp,
                vertical = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            allCategories.forEachIndexed { catIndex, category ->
                // ── Category header: icon on top, label centered below ──
                item(key = "header_$catIndex") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (catIndex == 0) 0.dp else 8.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = categoryIcon(category.categoryName),
                            contentDescription = null,
                            tint = theme.accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = category.categoryName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = theme.primaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // ── Menu item cards in rows of `itemsPerRow` ────────────
                val chunkedItems = category.items.chunked(itemsPerRow)
                items(
                    count = chunkedItems.size,
                    key = { rowIndex -> "items_${catIndex}_row_$rowIndex" }
                ) { rowIndex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = itemSpacing),
                        horizontalArrangement = Arrangement.spacedBy(itemSpacing)
                    ) {
                        chunkedItems[rowIndex].forEach { item ->
                            MenuItemCard(
                                item = item,
                                modifier = Modifier.weight(1f),
                                onClick = { onItemClick(item) }
                            )
                        }
                        // Fill remaining space if row is not full
                        val remaining = itemsPerRow - chunkedItems[rowIndex].size
                        repeat(remaining) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // ── Section divider (except after last category) ────────
                if (catIndex < allCategories.size - 1) {
                    item(key = "divider_$catIndex") {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = 1.dp,
                            color = theme.outlineVariantColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuContent(
    modifier: Modifier = Modifier,
    bestSellers: List<MenuItem>,
    categories: List<CategoryWithItems>,
    onItemClick: (MenuItem) -> Unit
) {
    // Build unified category list: Best Sellers first, then the rest
    val allCategories = remember(bestSellers, categories) {
        val otherCategories = categories.filter {
            it.categoryName.lowercase() != "best sellers"
        }
        buildList {
            if (bestSellers.isNotEmpty()) {
                add(CategoryWithItems("Best Sellers", bestSellers))
            }
            addAll(otherCategories)
        }
    }

    if (allCategories.isEmpty()) return

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0) { allCategories.size }
    var isPanelExpanded by remember { mutableStateOf(false) }

    // Derive selected index from the pager's settled page
    val selectedCategoryIndex by remember {
        derivedStateOf { pagerState.currentPage }
    }

    Row(modifier = modifier.fillMaxSize()) {
        // ── Left: collapsible side panel ──────────────────────────
        SideCategoryPanel(
            categories = allCategories.map { it.categoryName },
            selectedIndex = selectedCategoryIndex,
            isExpanded = isPanelExpanded,
            onToggleExpand = { isPanelExpanded = !isPanelExpanded },
            onCategoryClick = { index ->
                isPanelExpanded = false
                scope.launch { pagerState.animateScrollToPage(index) }
            }
        )

        // ── Right: horizontal-paging category sections ───────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            beyondViewportPageCount = 1,
            key = { allCategories[it].categoryName }
        ) { pageIndex ->
            CategoryPageContent(
                category = allCategories[pageIndex],
                onItemClick = onItemClick
            )
        }
    }
}

@Composable
private fun CartSummaryBar(
    cartItems: List<CartItem>,
    onViewCart: () -> Unit
) {
    val theme = LocalBackgroundTheme.current
    val hasItems = cartItems.isNotEmpty()
    val itemCount = CartPresentation.cartItemCount(cartItems)
    val total = CartPresentation.cartTotal(cartItems)

    Button(
        onClick = onViewCart,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(60.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (hasItems) theme.buttonContainerColor else theme.surfaceOverlayColor,
            contentColor = if (hasItems) theme.buttonContentColor else theme.primaryTextColor
        )
    ) {
        Icon(
            imageVector = Icons.Default.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        if (hasItems) {
            Text(
                text = "View cart · ${if (itemCount == 1) "1 item" else "$itemCount items"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = CartPresentation.formatPrice(total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        } else {
            Text(
                text = "View cart",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CartOverlay(
    viewModel: MenuViewModel,
    onDismiss: () -> Unit,
    onProceedToCheckout: () -> Unit
) {
    val cartItems by viewModel.cartItems.collectAsState()
    val inventoryStock by viewModel.inventoryStock.collectAsState()
    var isVisible by remember { mutableStateOf(false) }
    var isCheckoutTransition by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { isVisible = true }

    fun animatedDismiss() {
        scope.launch {
            isVisible = false
            delay(250) // Matches max exit tween (250ms)
            onDismiss()
        }
    }

    fun proceedToCheckout() {
        scope.launch {
            isCheckoutTransition = true
            isVisible = false
            onProceedToCheckout()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(if (isCheckoutTransition) 0 else 200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { animatedDismiss() }
                    )
            )
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(200)) + scaleIn(
                initialScale = 0.1f,
                transformOrigin = TransformOrigin(1f, 1f),
                animationSpec = tween(250)
            ),
            exit = fadeOut(tween(if (isCheckoutTransition) 0 else 250)) + scaleOut(
                targetScale = 0.1f,
                transformOrigin = TransformOrigin(1f, 1f),
                animationSpec = tween(if (isCheckoutTransition) 0 else 250)
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val cartConfig = LocalConfiguration.current
                val isCartPortrait = cartConfig.orientation == Configuration.ORIENTATION_PORTRAIT
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth(if (isCartPortrait) 0.92f else 0.5f)
                        .fillMaxHeight(if (isCartPortrait) 0.85f else 0.9f)
                        .clickable(enabled = false) { },
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = 6.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Your Cart",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            IconButton(onClick = { animatedDismiss() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                        val overlayTheme = LocalBackgroundTheme.current
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = overlayTheme.outlineVariantColor
                        )
                        if (cartItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Your cart is empty", color = overlayTheme.secondaryTextColor)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(cartItems) { item ->
                                    CartListItem(
                                        cartItem = item,
                                        quantityLimit = MenuQuantityRules.quantityLimit(
                                            itemStock = inventoryStock["${item.menuItem.categoryName}/${item.menuItem.id}"],
                                            sizeKey = item.selectedSize.ifEmpty { MenuQuantityRules.DEFAULT_STOCK_SIZE }
                                        ),
                                        onRemove = { viewModel.removeFromCart(it) },
                                        onUpdateQuantity = { cartItem, qty ->
                                            viewModel.updateQuantity(cartItem, qty)
                                        }
                                    )
                                }
                            }
                        }
                        val total = cartItems.sumOf { it.price * it.quantity }

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = overlayTheme.outlineVariantColor
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(overlayTheme.surfaceOverlayColor)
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Amount", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "₱${String.format(Locale.getDefault(), "%.2f", total)}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = overlayTheme.accentColor
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { proceedToCheckout() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                 shape = MaterialTheme.shapes.large,
                                enabled = cartItems.isNotEmpty()
                            ) {
                                Text("PROCEED TO CHECKOUT", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartListItem(
    cartItem: CartItem,
    quantityLimit: MenuQuantityRules.QuantityLimit,
    onRemove: (CartItem) -> Unit,
    onUpdateQuantity: (CartItem, Int) -> Unit
) {
    val storageBucket = stringResource(R.string.google_storage_bucket)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalBackgroundTheme.current.surfaceOverlayColor, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val itemTheme = LocalBackgroundTheme.current
        AsyncImage(
            model = ImageUrlValidator.sanitize(cartItem.menuItem.imageUrl, storageBucket)
                ?: R.drawable.menu_item_placeholder,
            contentDescription = null,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.menu_item_placeholder)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cartItem.menuItem.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    CartPresentation.sizeLabel(cartItem)?.let { size ->
                        Text(
                            text = size,
                            style = MaterialTheme.typography.bodySmall,
                            color = itemTheme.secondaryTextColor
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = CartPresentation.formatPrice(CartPresentation.lineSubtotal(cartItem)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = itemTheme.accentColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${CartPresentation.formatPrice(cartItem.price)} each",
                    style = MaterialTheme.typography.bodySmall,
                    color = itemTheme.secondaryTextColor
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val atLimit = cartItem.quantity >= quantityLimit.maxQuantity
                    IconButton(
                        onClick = { onUpdateQuantity(cartItem, cartItem.quantity - 1) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = CartPresentation.decreaseContentDescription(
                                cartItem.menuItem,
                                cartItem.selectedSize
                            ),
                            tint = itemTheme.accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Text(
                        text = "${cartItem.quantity}",
                        modifier = Modifier.width(28.dp),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = { onUpdateQuantity(cartItem, cartItem.quantity + 1) },
                        enabled = !atLimit,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = CartPresentation.increaseContentDescription(
                                item = cartItem.menuItem,
                                selectedSize = cartItem.selectedSize,
                                atLimit = atLimit,
                                limit = quantityLimit
                            ),
                            tint = itemTheme.accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = { onRemove(cartItem) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove ${cartItem.menuItem.name}",
                            tint = Color.Red.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckoutOverlay(
    viewModel: MenuViewModel,
    onDismiss: () -> Unit,
    onOrderConfirmed: (Order) -> Unit
) {
    val cartItems by viewModel.cartItems.collectAsState()
    var customerName by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )

        val checkoutConfig = LocalConfiguration.current
        val isCheckoutPortrait = checkoutConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        GlassCard(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(if (isCheckoutPortrait) 0.92f else 0.5f)
                .fillMaxHeight(if (isCheckoutPortrait) 0.85f else 0.9f)
                .clickable(enabled = false) { },
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Checkout",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                val checkoutTheme = LocalBackgroundTheme.current
                HorizontalDivider(
                    thickness = 1.dp,
                    color = checkoutTheme.outlineVariantColor
                )

                // ─── Customer Name Input ─────────────────────────────
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("Customer Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = checkoutTheme.accentColor,
                        focusedLabelColor = checkoutTheme.accentColor
                    )
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cartItems) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(checkoutTheme.surfaceOverlayColor, RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = CartPresentation.displayName(item),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${item.quantity} × ${CartPresentation.formatPrice(item.price)} each",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = checkoutTheme.secondaryTextColor
                                )
                            }
                            Text(
                                text = CartPresentation.formatPrice(CartPresentation.lineSubtotal(item)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = checkoutTheme.accentColor
                            )
                        }
                    }
                }
                val total = cartItems.sumOf { it.price * it.quantity }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(checkoutTheme.surfaceOverlayColor)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(checkoutTheme.surfaceOverlayColor)
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Total Amount",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "₱${String.format(Locale.getDefault(), "%.2f", total)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = checkoutTheme.accentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val order = viewModel.confirmOrder(customerName)
                            onOrderConfirmed(order)
                        },
                        enabled = customerName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("CONFIRM ORDER", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
