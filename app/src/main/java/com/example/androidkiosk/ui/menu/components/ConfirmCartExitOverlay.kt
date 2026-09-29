package com.example.androidkiosk.ui.menu.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.androidkiosk.model.CartItem
import com.example.androidkiosk.ui.animation.MotionTokens
import com.example.androidkiosk.ui.menu.CartPresentation
import com.example.androidkiosk.ui.theme.LocalBackgroundTheme

/**
 * Asks before Back discards a cart the customer has already built.
 *
 * The menu used to let Back finish the Activity unconditionally, so a customer who pressed it
 * while browsing lost an order they had spent minutes assembling with no warning at all. Loss
 * aversion says protect that work — but this app also runs on staff phones and shared tablets,
 * so it must never be a trap: [onLeave] is always one tap away and the scrim does nothing.
 *
 * Deliberately hand-rolled, like every other overlay in this app. There is no `AlertDialog` or
 * `Dialog` anywhere in the project, and introducing M3's would break the glass visual language
 * and the shared `GlassCard` neumorphism.
 */
@Composable
fun ConfirmCartExitOverlay(
    cartItems: List<CartItem>,
    onStay: () -> Unit,
    onLeave: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val theme = LocalBackgroundTheme.current
    val itemCount = CartPresentation.cartItemCount(cartItems)
    val total = CartPresentation.cartTotal(cartItems)

    // Modal: Back must not dismiss this silently either, since dismissing IS the destructive
    // action being confirmed.
    BackHandler(enabled = true) { onStay() }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(MotionTokens.DurationMedium1)),
        exit = fadeOut(tween(MotionTokens.DurationMedium1))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .requiredHeightIn(max = 520.dp)
                    .padding(horizontal = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                // No exterior drop shadow: on a dark scrim it reads as a glow rather than
                // elevation. Matches the item detail, cart, and payment overlays.
                elevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Keep your order?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = theme.primaryTextColor,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "You have $itemCount ${if (itemCount == 1) "item" else "items"} " +
                            "worth ${CartPresentation.formatPrice(total)} in your cart.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.secondaryTextColor,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onLeave,
                            modifier = Modifier
                                .weight(1f)
                                .requiredHeightIn(min = 52.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text("Leave anyway", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onStay,
                            modifier = Modifier
                                .weight(1f)
                                .requiredHeightIn(min = 52.dp),
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.buttonContainerColor,
                                contentColor = theme.buttonContentColor
                            )
                        ) {
                            Text("Stay", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
