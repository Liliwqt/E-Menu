package com.example.androidkiosk.ui.menu.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.androidkiosk.admin.PinManager
import com.example.androidkiosk.ui.animation.MotionTokens
import com.example.androidkiosk.ui.theme.LocalBackgroundTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Admin PIN entry dialog for kiosk unlock. */
@Composable
fun AdminPinDialog(
    pinManager: PinManager,
    onUnlockSuccess: () -> Unit,
    onDismiss: () -> Unit,
    onPinFailed: () -> Unit = {}
) {
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLockedOut by remember { mutableStateOf(pinManager.isLockedOut()) }
    var lockoutRemainingSeconds by remember { mutableIntStateOf(0) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isVisible by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Spring-based shake animation offset
    val shakeOffset = remember { Animatable(0f) }

    // Entry animation
    LaunchedEffect(Unit) {
        isVisible = true
        delay(200)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // Auto-dismiss after 30 seconds of inactivity
    LaunchedEffect(lastInteractionTime) {
        delay(30_000L)
        if (System.currentTimeMillis() - lastInteractionTime >= 30_000L) {
            isVisible = false
            delay(200)
            onDismiss()
        }
    }

    // Lockout countdown timer
    LaunchedEffect(isLockedOut) {
        while (pinManager.isLockedOut()) {
            lockoutRemainingSeconds =
                ((pinManager.remainingLockoutMs() + 999L) / 1000L).toInt()
            isLockedOut = true
            delay(1000L)
        }
        isLockedOut = false
        lockoutRemainingSeconds = 0
    }

    fun onInteraction() {
        lastInteractionTime = System.currentTimeMillis()
    }

    fun submitPin() {
        onInteraction()

        if (pinInput.isBlank()) {
            errorMessage = "Enter your PIN"
            return
        }

        if (pinManager.isLockedOut()) {
            isLockedOut = true
            errorMessage = "Too many attempts. Please wait."
            return
        }

        val isValid = pinManager.validatePin(pinInput)

        if (isValid) {
            errorMessage = null
            scope.launch {
                isVisible = false
                delay(150)
                onUnlockSuccess()
            }
        } else {
            onPinFailed()
            errorMessage = if (pinManager.isLockedOut()) {
                isLockedOut = true
                "Too many attempts. Locked for 60s."
            } else {
                val remaining = PinManager.MAX_ATTEMPTS - pinManager.failedAttempts
                "Wrong PIN. $remaining attempt${if (remaining != 1) "s" else ""} left."
            }
            pinInput = ""

            // Spring-based shake animation (physics-based)
            scope.launch {
                shakeOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.3f,
                        stiffness = 2000f
                    ),
                    initialVelocity = 3000f
                )
            }
        }
    }

    fun animatedDismiss() {
        scope.launch {
            isVisible = false
            delay(200)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Dim background
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(MotionTokens.DurationMedium1)),
            exit = fadeOut(tween(MotionTokens.DurationMedium1))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { animatedDismiss() }
                    )
            )
        }

        // Dialog card with M3 entrance animation
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EasingEmphasizedDecelerate)) +
                    scaleIn(
                        initialScale = 0.85f,
                        animationSpec = tween(MotionTokens.DurationMedium2, easing = MotionTokens.EasingEmphasizedDecelerate)
                    ) +
                    slideInVertically(
                        initialOffsetY = { it / 10 },
                        animationSpec = tween(MotionTokens.DurationMedium2, easing = MotionTokens.EasingEmphasizedDecelerate)
                    ),
            exit = fadeOut(tween(MotionTokens.DurationShort2, easing = MotionTokens.EasingEmphasizedAccelerate)) +
                    scaleOut(
                        targetScale = 0.85f,
                        animationSpec = tween(MotionTokens.DurationShort2, easing = MotionTokens.EasingEmphasizedAccelerate)
                    )
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                    .clickable(enabled = false) { }
            ) {
                GlassCard(
                    modifier = Modifier
                        .width(380.dp)
                        .clickable(enabled = false) { },
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = 0.dp,
                    showFocusOutline = false
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Lock icon with M3 tonal background
                        val pinTheme = LocalBackgroundTheme.current
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    pinTheme.primaryContainer,
                                    MaterialTheme.shapes.extraLarge
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = pinTheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Admin Authentication",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Enter your PIN to unlock the device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = pinTheme.secondaryTextColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Lockout warning with M3 errorContainer
                        if (isLockedOut) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        pinTheme.errorContainer,
                                        MaterialTheme.shapes.small
                                    )
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = pinTheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Locked out. Retry in ${lockoutRemainingSeconds}s",
                                    color = pinTheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // PIN input field with M3 shape tokens
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { newValue ->
                                onInteraction()
                                if (newValue.all { it.isDigit() } && newValue.length <= 8) {
                                    pinInput = newValue
                                    errorMessage = null
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            label = { Text("PIN") },
                            placeholder = { Text("Enter PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submitPin() }
                            ),
                            singleLine = true,
                            isError = errorMessage != null,
                            enabled = !isLockedOut,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = pinTheme.outlineVariantColor,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = pinTheme.secondaryTextColor,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = pinTheme.primaryTextColor,
                                unfocusedTextColor = pinTheme.primaryTextColor,
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                disabledBorderColor = pinTheme.outlineVariantColor.copy(alpha = 0.3f),
                                disabledTextColor = pinTheme.secondaryTextColor
                            ),
                            shape = MaterialTheme.shapes.medium
                        )

                        // Error message
                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Buttons with M3 styling
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { animatedDismiss() },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = pinTheme.secondaryTextColor
                                )
                            ) {
                                Text("Cancel", style = MaterialTheme.typography.labelLarge)
                            }

                            Button(
                                onClick = { submitPin() },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                enabled = !isLockedOut && pinInput.isNotBlank()
                            ) {
                                Text(
                                    "Unlock",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
