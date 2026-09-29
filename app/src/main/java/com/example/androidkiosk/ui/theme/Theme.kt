package com.example.androidkiosk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** CompositionLocal for providing the background image URL from Firebase appSettings. */
val LocalBackgroundImageUrl = staticCompositionLocalOf<String?> { null }

/** CompositionLocal for the active background theme resolved from Firebase. */
val LocalBackgroundTheme = staticCompositionLocalOf<BackgroundTheme> { BackgroundTheme.SoftLight }

/** CompositionLocal for reduced-motion accessibility preference. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun AndroidDeviceTheme(
    backgroundImageUrl: String? = null,
    backgroundThemeName: String = "Dark",
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val bgTheme = BackgroundTheme.SoftLight

    // The native screen always uses a light palette; the embedded portal owns its CSS.
    val colorScheme = lightColorScheme(
            primary = bgTheme.accentColor,
            onPrimary = Color.White,
            primaryContainer = bgTheme.primaryContainer,
            onPrimaryContainer = bgTheme.onPrimaryContainer,
            secondary = bgTheme.accentColor,
            secondaryContainer = bgTheme.secondaryContainer,
            onSecondaryContainer = bgTheme.onSecondaryContainer,
            tertiary = bgTheme.accentColor,
            tertiaryContainer = bgTheme.tertiaryContainer,
            onTertiaryContainer = bgTheme.onTertiaryContainer,
            background = bgTheme.backgroundColor,
            onBackground = bgTheme.primaryTextColor,
            surface = bgTheme.surfaceColor,
            onSurface = bgTheme.primaryTextColor,
            surfaceVariant = bgTheme.surfaceOverlayColor,
            onSurfaceVariant = bgTheme.secondaryTextColor,
            surfaceContainerLowest = bgTheme.surfaceContainerLowest,
            surfaceContainerLow = bgTheme.surfaceContainerLow,
            surfaceContainer = bgTheme.surfaceContainer,
            surfaceContainerHigh = bgTheme.surfaceContainerHigh,
            surfaceContainerHighest = bgTheme.surfaceContainerHighest,
            outline = bgTheme.outlineColor,
            outlineVariant = bgTheme.outlineVariantColor,
            errorContainer = bgTheme.errorContainer,
            onErrorContainer = bgTheme.onErrorContainer
    )

    CompositionLocalProvider(
        LocalBackgroundImageUrl provides null,
        LocalBackgroundTheme provides bgTheme,
        LocalReducedMotion provides reducedMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
