package com.example.androidkiosk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** CompositionLocal for providing the background image URL from Firebase appSettings. */
val LocalBackgroundImageUrl = staticCompositionLocalOf<String?> { null }

/** CompositionLocal for the active background theme resolved from Firebase. */
val LocalBackgroundTheme = staticCompositionLocalOf<BackgroundTheme> { BackgroundTheme.Dark }

/** CompositionLocal for reduced-motion accessibility preference. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun AndroidDeviceTheme(
    backgroundImageUrl: String? = null,
    backgroundThemeName: String = "Dark",
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val bgTheme = BackgroundTheme.fromName(backgroundThemeName)

    // Build a color scheme that matches the resolved theme so that
    // MaterialTheme.colorScheme.background / onBackground / surface etc.
    // are coherent with the custom BackgroundTheme palette.
    val colorScheme = if (bgTheme is BackgroundTheme.Light) {
        lightColorScheme(
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
    } else {
        darkColorScheme(
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
    }

    CompositionLocalProvider(
        LocalBackgroundImageUrl provides backgroundImageUrl,
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
