package com.example.androidkiosk.ui.menu.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.androidkiosk.ui.theme.LocalBackgroundTheme

/** Neumorphic inset colours: a dark inner edge top-left, a light one bottom-right. */
private val InsetDark = Color(0xFFAFB9C0)
private val InsetLight = Color.White

/**
 * Draws the inner-shadow pass for [shape], clipped to it so nothing spills outside.
 *
 * Drawn as four per-edge linear gradients — top, left, bottom, right — rather than as one
 * corner-to-corner diagonal ramp. A single diagonal ramp only darkens the CORNER: its alpha
 * falls off as you travel along the top edge, so the top and left edges fade out towards the
 * opposite end and the shape reads as a diagonal wash across its middle. That is a gradient
 * laid over a shape, not a shadow cast by its rim.
 *
 * A real inset is a band of roughly constant width hugging the perimeter, so each edge needs
 * its own gradient running at full strength along that edge and fading over `band` pixels.
 * The two lights then wrap the whole shape — dark down the top and left, light up the bottom
 * and right — and the corners agree, because each pair shares its strength.
 *
 * The earlier offset-box attempt (fill the shape, then lay a surface-coloured copy offset to
 * leave a crescent) cannot produce both edges at once: the second pass's opaque surface copy is
 * offset the OPPOSITE way, so it slides back across the first pass's crescent and erases it.
 * Two opaque passes in sequence always cancel — measured on device, the chip interior came out
 * exactly the card colour, i.e. no shadow at all. Gradients avoid that because the "empty" end
 * is transparent and lets the surface below show through instead of covering it.
 */
@Composable
private fun NeumorphicInset(
    shape: Shape,
    modifier: Modifier = Modifier,
    strength: Float = 1f,
    band: Float = 0.30f,
) {
    Box(
        modifier
            .clip(shape)
            // drawBehind gives access to the measured size, which the gradients need in order
            // to span the shape; a Brush built in composition could not know it.
            .drawBehind {
                val w = size.width
                val h = size.height
                // Band width as a fraction of the SHORTER side, so a wide chip gets a
                // proportionally tighter rim than a square one instead of a huge wash across
                // most of its area.
                val b = (minOf(w, h) * band).coerceAtLeast(1f)
                val dark = InsetDark.copy(alpha = 0.40f * strength)
                val light = InsetLight.copy(alpha = 0.95f * strength)

                // Dark rim: top edge, fading downwards.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(dark, Color.Transparent),
                        startY = 0f,
                        endY = b,
                    )
                )
                // Dark rim: left edge, fading rightwards.
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(dark, Color.Transparent),
                        startX = 0f,
                        endX = b,
                    )
                )
                // Light rim: bottom edge, fading upwards.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, light),
                        startY = h - b,
                        endY = h,
                    )
                )
                // Light rim: right edge, fading leftwards.
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, light),
                        startX = w - b,
                        endX = w,
                    )
                )
            }
    )
}


/** Shared raised native surface; outlines are reserved for focus and explicit states. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    backgroundColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    borderBrush: Brush? = null,
    borderWidth: Dp = 1.dp,
    contentColor: Color = Color.Unspecified,
    elevation: Dp = 0.dp,
    useTonalSurface: Boolean = false,
    isPressed: Boolean = false,
    content: @Composable () -> Unit
) {
    val theme = LocalBackgroundTheme.current
    val resolvedContentColor = if (contentColor != Color.Unspecified) contentColor else theme.primaryTextColor

    var isFocused by remember { mutableStateOf(false) }
    // Resting cards use paired shadows; keyboard focus retains a strong outline.
    val borderModifier = when {
        isFocused -> Modifier.border(2.dp, theme.primaryTextColor, shape)
        borderColor != Color.Unspecified -> Modifier.border(borderWidth, borderColor, shape)
        borderBrush != null -> Modifier.border(borderWidth, borderBrush, shape)
        else -> Modifier
    }
    val surface = when {
        backgroundColor != Color.Unspecified -> backgroundColor
        useTonalSurface -> theme.backgroundColor
        else -> theme.backgroundColor
    }
    CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
        Box(modifier = Modifier.onFocusChanged { isFocused = it.isFocused }.then(modifier)) {
            if (elevation > 0.dp) {
                // Opposing soft shadows make the surface visibly raised. These are SIBLINGS of
                // the surface below, so they spill outside the card — that is what makes the
                // raised state read as raised.
                Box(
                    Modifier.matchParentSize()
                        .offset(x = (-5).dp, y = (-5).dp)
                        .blur(10.dp, BlurredEdgeTreatment.Unbounded)
                        .background(Color.White, shape)
                )
                Box(
                    Modifier.matchParentSize()
                        .offset(x = 6.dp, y = 6.dp)
                        .blur(12.dp, BlurredEdgeTreatment.Unbounded)
                        .background(Color(0xFFAFB9C0), shape)
                )
            }
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(surface)
                    .then(borderModifier)
            ) {
                // ── Pressed state: the inner shadow ──
                // This must sit BETWEEN the surface and the content, in that order. Painting
                // it after the surface hides the shadow under an opaque box, and painting it
                // after the content draws opaque crescents over the label and erases the text.
                // Both were real failures; only this ordering works.
                if (isPressed) {
                    NeumorphicInset(shape = shape, modifier = Modifier.matchParentSize())
                }
                content()
            }
        }
    }

}
