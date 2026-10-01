package com.example.androidkiosk.ui.menu.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.androidkiosk.R
import com.example.androidkiosk.model.MenuItem
import com.example.androidkiosk.ui.animation.MotionTokens
import com.example.androidkiosk.ui.menu.CartPresentation
import com.example.androidkiosk.ui.menu.MenuQuantityRules
import com.example.androidkiosk.ui.theme.LocalBackgroundTheme
import com.example.androidkiosk.util.ImageUrlValidator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Item detail overlay with M3 components and enhanced animations. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemDetailOverlay(
    item: MenuItem,
    stockBySize: Map<String, Int>?,
    initialSelectedSize: String = "",
    onDismiss: () -> Unit,
    onAddToCart: (MenuItem, Int, String) -> Unit
) {
    val storageBucket = stringResource(R.string.google_storage_bucket)
    var isVisible by remember { mutableStateOf(false) }
    var isAddingToCart by remember { mutableStateOf(false) }
    var quantity by remember { mutableIntStateOf(1) }
    var selectedSize by remember(item.id, item.sizes, initialSelectedSize) {
        mutableStateOf(MenuQuantityRules.resolveSize(item, initialSelectedSize))
    }
    val scope = rememberCoroutineScope()
    val stockKey = selectedSize.ifEmpty { MenuQuantityRules.DEFAULT_STOCK_SIZE }
    val sizeLimit = MenuQuantityRules.quantityLimit(stockBySize, stockKey)
    val maxQuantity = sizeLimit.maxQuantity
    val effectivePrice = MenuQuantityRules.effectivePrice(item, selectedSize)
    val detailTheme = LocalBackgroundTheme.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    // Shrink the hero image when vertical space is scarce so the footer stays reachable.
    val imageHeight = when {
        configuration.screenHeightDp < 600 -> 96.dp
        isPortrait -> 140.dp
        else -> 120.dp
    }

    LaunchedEffect(maxQuantity) {
        quantity = quantity.coerceIn(1, maxQuantity.coerceAtLeast(1))
    }

    LaunchedEffect(Unit) { isVisible = true }

    fun animatedDismiss() {
        scope.launch {
            isVisible = false
            delay(150)
            onDismiss()
        }
    }

    fun animatedAddToCart() {
        scope.launch {
            isAddingToCart = true
            isVisible = false
            delay(250)
            onAddToCart(item, quantity, selectedSize)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Scrim
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EasingStandard)),
            exit = fadeOut(tween(if (isAddingToCart) MotionTokens.DurationMedium1 else MotionTokens.DurationShort2))
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

        // Content card with container transform-style animation
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(
                tween(MotionTokens.DurationMedium1, easing = MotionTokens.EasingEmphasizedDecelerate)
            ) + scaleIn(
                initialScale = 0.85f,
                animationSpec = tween(MotionTokens.DurationMedium2, easing = MotionTokens.EasingEmphasizedDecelerate)
            ) + slideInVertically(
                initialOffsetY = { it / 12 },
                animationSpec = tween(MotionTokens.DurationMedium2, easing = MotionTokens.EasingEmphasizedDecelerate)
            ),
            exit = if (isAddingToCart) {
                fadeOut(tween(MotionTokens.DurationMedium1)) + scaleOut(
                    targetScale = 0.1f,
                    transformOrigin = TransformOrigin(1f, 1f),
                    animationSpec = tween(MotionTokens.DurationMedium1, easing = MotionTokens.EasingEmphasizedAccelerate)
                )
            } else {
                fadeOut(tween(MotionTokens.DurationShort2)) + scaleOut(
                    targetScale = 0.85f,
                    animationSpec = tween(MotionTokens.DurationShort2, easing = MotionTokens.EasingEmphasizedAccelerate)
                ) + slideOutVertically(
                    targetOffsetY = { it / 12 },
                    animationSpec = tween(MotionTokens.DurationShort2, easing = MotionTokens.EasingEmphasizedAccelerate)
                )
            }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth(if (isPortrait) 0.92f else 0.6f)
                        .widthIn(max = 560.dp)
                        .fillMaxHeight(if (isPortrait) 0.72f else 0.92f)
                        .clickable(enabled = false) { },
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = 0.dp,
                    showFocusOutline = false
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // ── Image section (shrinks on constrained screens) ──
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(imageHeight)
                        ) {
                            AsyncImage(
                                model = ImageUrlValidator.sanitize(
                                    item.imageUrl.ifEmpty { null },
                                    storageBucket
                                ) ?: R.drawable.menu_item_placeholder,
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = painterResource(R.drawable.menu_item_placeholder)
                            )

                            // Close button — M3 FilledTonalIconButton
                            FilledTonalIconButton(
                                onClick = { animatedDismiss() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.5f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close"
                                )
                            }
                        }

                        // ── Scrollable details ──
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = CartPresentation.formatPrice(effectivePrice),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = detailTheme.accentColor
                            )
                            // Running line total. The overlay used to show only the unit price
                            // while a quantity stepper sat in the footer, so setting the quantity
                            // to 3 still read as a single item. Shown only once the quantity
                            // differs from one, keeping the common case uncluttered, and styled
                            // distinctly from the unit price so the two are never read as
                            // competing totals.
                            if (quantity > 1) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${CartPresentation.formatPrice(CartPresentation.lineSubtotal(effectivePrice, quantity))} total",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = detailTheme.secondaryTextColor
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            // M3 HorizontalDivider
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = detailTheme.outlineVariantColor
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            if (item.sizes.isNotEmpty()) {
                                Text(
                                    text = "Choose a size",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = detailTheme.primaryTextColor
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item.sizes.forEach { (sizeName, option) ->
                                        val sizeAvailable = MenuQuantityRules
                                            .quantityLimit(stockBySize, sizeName).isAvailable
                                        val isSelected = selectedSize == sizeName
                                        // The chip no longer paints a container, so the raised
                                        // card itself is what changes: the selected size is
                                        // pressed IN (shadows flipped, no offset) while an
                                        // unselected one stays raised. Colour alone would not
                                        // survive a greyscale or high-contrast reading.
                                        GlassCard(
                                            shape = MaterialTheme.shapes.medium,
                                            elevation = if (isSelected) 0.dp else 2.dp,
                                            isPressed = isSelected
                                        ) {
                                            FilterChip(
                                                border = null,
                                                selected = isSelected,
                                                onClick = {
                                                    selectedSize = sizeName
                                                    quantity = 1
                                                },
                                                enabled = sizeAvailable,
                                                leadingIcon = if (isSelected) {
                                                    { Icon(Icons.Default.Check, contentDescription = null,
                                                        modifier = Modifier.size(18.dp)) }
                                                } else null,
                                                label = {
                                                    val label = when {
                                                        !sizeAvailable -> "$sizeName — Sold out"
                                                        option.priceModifier == 0.0 -> sizeName
                                                        else -> "$sizeName +₱${
                                                            String.format(Locale.getDefault(), "%.2f", option.priceModifier)
                                                        }"
                                                    }
                                                    Text(
                                                        text = label,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    // Transparent so the neumorphic card behind the
                                                    // chip is what actually shows. FilterChip paints
                                                    // its own container by default, which put a flat
                                                    // grey panel inside the raised shadow and
                                                    // flattened the effect. Selection is carried by
                                                    // the label colour, weight and the card's own
                                                    // pressed state instead.
                                                    containerColor = Color.Transparent,
                                                    selectedContainerColor = Color.Transparent,
                                                    disabledContainerColor = Color.Transparent,
                                                    selectedLabelColor = detailTheme.accentColor,
                                                    labelColor = detailTheme.primaryTextColor,
                                                    disabledLabelColor = detailTheme.secondaryTextColor.copy(alpha = 0.6f)
                                                )
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            val trackedStock = sizeLimit.trackedStock
                            val availabilityText = CartPresentation.availabilityText(sizeLimit)
                            val isOutOfStock = trackedStock != null && trackedStock <= 0
                            Text(
                                text = availabilityText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOutOfStock) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    detailTheme.secondaryTextColor
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = detailTheme.outlineVariantColor
                        )

                        // ── Pinned quantity + Add to Cart footer ──
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(detailTheme.surfaceOverlayColor)
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GlassCard(shape = androidx.compose.foundation.shape.CircleShape, elevation = 2.dp) {
                                        FilledTonalIconButton(
                                            onClick = { if (quantity > 1) quantity-- },
                                            enabled = item.available && quantity > 1,
                                            modifier = Modifier.size(48.dp),
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = Color.Transparent,
                                                contentColor = detailTheme.primaryTextColor,
                                                disabledContentColor = detailTheme.secondaryTextColor
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = CartPresentation.decreaseContentDescription(
                                                    item,
                                                    selectedSize
                                                )
                                            )
                                        }
                                    }
                                    Text(
                                        text = "$quantity",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = detailTheme.primaryTextColor,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(32.dp)
                                    )
                                    GlassCard(shape = androidx.compose.foundation.shape.CircleShape, elevation = 2.dp) {
                                        FilledTonalIconButton(
                                            onClick = { if (quantity < maxQuantity) quantity++ },
                                            enabled = item.available && quantity < maxQuantity,
                                            modifier = Modifier.size(48.dp),
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = Color.Transparent,
                                                contentColor = detailTheme.primaryTextColor,
                                                disabledContentColor = detailTheme.secondaryTextColor
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = CartPresentation.increaseContentDescription(
                                                    item = item,
                                                    selectedSize = selectedSize,
                                                    atLimit = quantity >= maxQuantity,
                                                    limit = sizeLimit
                                                )
                                            )
                                        }
                                    }
                                }

                                GlassCard(shape = MaterialTheme.shapes.large, backgroundColor = detailTheme.accentColor, elevation = 2.dp) {
                                    Button(
                                        onClick = { animatedAddToCart() },
                                        modifier = Modifier.height(52.dp),
                                        shape = MaterialTheme.shapes.large,
                                        enabled = item.available && maxQuantity > 0,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ShoppingCart,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Add to Cart",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }


                        }
                    }
                }
            }
        }
    }
}
