package com.example.androidkiosk.ui.menu.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.androidkiosk.R
import com.example.androidkiosk.model.MenuItem
import com.example.androidkiosk.ui.theme.LocalBackgroundTheme
import com.example.androidkiosk.util.ImageUrlValidator
import java.util.Locale

/** A soft-surface menu item card showing an image, name, and price. */
@Composable
fun MenuItemCard(
    item: MenuItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val storageBucket = stringResource(R.string.google_storage_bucket)
    val theme = LocalBackgroundTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Spring-based press scale animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 800f
        ),
        label = "cardScale"
    )

    GlassCard(
        modifier = modifier
            .aspectRatio(0.75f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                enabled = item.available,
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    color = theme.accentColor
                ),
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.large,
        backgroundColor = theme.backgroundColor,
        borderWidth = 2.dp,
        borderColor = if (isFocused) theme.primaryTextColor else Color.Transparent,
        elevation = 3.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = ImageUrlValidator.sanitize(
                    item.imageUrl.ifEmpty { null },
                    storageBucket
                ) ?: R.drawable.menu_item_placeholder,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                    .weight(0.65f)
                    .padding(8.dp),
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.menu_item_placeholder)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.available) MaterialTheme.colorScheme.onSurface else theme.secondaryTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "₱${String.format(Locale.getDefault(), "%.2f", item.price)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.available) theme.accentColor else theme.secondaryTextColor
                )
                if (!item.available) {
                    Text(
                        text = "Unavailable",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = theme.secondaryTextColor
                    )
                }
            }
        }
    }
}
