package com.example.androidkiosk.ui.menu.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.androidkiosk.ui.animation.FadeInAnimatedItem
import com.example.androidkiosk.ui.animation.ShimmerMenuSkeleton
import com.example.androidkiosk.ui.theme.LocalBackgroundTheme

@Composable
fun KioskAuthorizationScreen(
    uid: String?,
    message: String?,
    onRetry: () -> Unit
) {
    val theme = LocalBackgroundTheme.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)
        ) {
            Text(
                text = "Kiosk registration required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = theme.primaryTextColor,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message ?: "Ask a Firebase administrator to authorize this kiosk for its assigned branch.",
                color = theme.secondaryTextColor,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            SelectionContainer {
                Text(
                    text = uid ?: "Waiting for anonymous authentication…",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.primaryTextColor,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(onClick = onRetry) { Text("Check registration") }
        }
    }
}

/** Loading screen with shimmer skeleton effect. */
@Composable
fun LoadingScreen() {
    ShimmerMenuSkeleton(modifier = Modifier.fillMaxSize())
}

/** Error screen with retry button. */
@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    val theme = LocalBackgroundTheme.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        FadeInAnimatedItem {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "Something went wrong",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.primaryTextColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = theme.secondaryTextColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = theme.secondaryTextColor
                )
                Spacer(modifier = Modifier.height(24.dp))
                FilledTonalButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = theme.accentColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "Retry",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
