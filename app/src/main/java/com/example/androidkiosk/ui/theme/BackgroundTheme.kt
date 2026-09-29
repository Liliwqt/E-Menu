package com.example.androidkiosk.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Defines the visual properties for each background theme. */
sealed class BackgroundTheme(
    /** Solid background color (ignored when [usesBackgroundImage] is true). */
    val backgroundColor: Color,
    /** Optional gradient background brush; null means use [backgroundColor] solid fill. */
    val backgroundBrush: Brush? = null,
    /** Primary text / icon color for maximum readability. */
    val primaryTextColor: Color,
    /** Dimmed text for subtitles, hints, and secondary information. */
    val secondaryTextColor: Color,
    /** Accent color for prices, highlights, and interactive elements. */
    val accentColor: Color,
    /** Glass card border tint (solid fallback). */
    val glassBorderColor: Color,
    /** Glass border gradient start color (accent tone). */
    val glassBorderGradientStart: Color,
    /** Glass border gradient end color (faded white). */
    val glassBorderGradientEnd: Color,
    /** Glass card gradient start color (top-left). */
    val glassGradientStart: Color,
    /** Glass card gradient end color (bottom-right). */
    val glassGradientEnd: Color,
    /** Semi-transparent overlay for surfaces (qty badges, panels). */
    val surfaceOverlayColor: Color,
    /** Whether this theme uses the Firebase/drawable background image instead of solid color. */
    val usesBackgroundImage: Boolean = false,
    /** Surface color for elevated elements (cards, dialogs). */
    val surfaceColor: Color,
    /** FAB / primary button container color. */
    val buttonContainerColor: Color,
    /** FAB / primary button content (icon/text) color. */
    val buttonContentColor: Color,
    /** Color for the category selection indicator. */
    val categorySelectedColor: Color,
    /** Color for unselected category items. */
    val categoryUnselectedColor: Color,

    // ── M3 Tonal Container Colors ────────────────────────────
    /** M3 primary container — used for tonal elevation on primary surfaces. */
    val primaryContainer: Color,
    /** M3 on-primary-container — content color on primary container. */
    val onPrimaryContainer: Color,
    /** M3 secondary container — used for secondary tonal surfaces. */
    val secondaryContainer: Color,
    /** M3 on-secondary-container — content color on secondary container. */
    val onSecondaryContainer: Color,
    /** M3 tertiary container — used for tertiary tonal surfaces. */
    val tertiaryContainer: Color,
    /** M3 on-tertiary-container — content color on tertiary container. */
    val onTertiaryContainer: Color,

    // ── M3 Surface Container Hierarchy ───────────────────────
    /** Lowest elevation surface container. */
    val surfaceContainerLowest: Color,
    /** Low elevation surface container. */
    val surfaceContainerLow: Color,
    /** Default surface container. */
    val surfaceContainer: Color,
    /** High elevation surface container. */
    val surfaceContainerHigh: Color,
    /** Highest elevation surface container. */
    val surfaceContainerHighest: Color,

    // ── M3 Outline Colors ────────────────────────────────────
    /** M3 outline — borders, dividers. */
    val outlineColor: Color,
    /** M3 outline variant — subtle borders. */
    val outlineVariantColor: Color,

    // ── M3 Error Container ───────────────────────────────────
    /** M3 error container background. */
    val errorContainer: Color,
    /** M3 on-error-container content color. */
    val onErrorContainer: Color
) {

    /** Fixed native palette. Saved branch theme preferences remain in Firebase. */
    data object SoftLight : BackgroundTheme(
        backgroundColor = Color(0xFFE2E7EB),
        primaryTextColor = Color(0xFF202428),
        secondaryTextColor = Color(0xFF505860),
        accentColor = Color(0xFF202428),
        glassBorderColor = Color(0xFF89949C),
        glassBorderGradientStart = Color(0xFF89949C),
        glassBorderGradientEnd = Color(0xFF89949C),
        glassGradientStart = Color.Transparent,
        glassGradientEnd = Color.Transparent,
        surfaceOverlayColor = Color(0xFFE4E9EC),
        surfaceColor = Color(0xFFF3F5F6),
        buttonContainerColor = Color(0xFF202428),
        buttonContentColor = Color.White,
        categorySelectedColor = Color(0xFF202428),
        categoryUnselectedColor = Color(0xFF505860),
        primaryContainer = Color(0xFFD8DEE2),
        onPrimaryContainer = Color(0xFF202428),
        secondaryContainer = Color(0xFFD8DEE2),
        onSecondaryContainer = Color(0xFF202428),
        tertiaryContainer = Color(0xFFD8DEE2),
        onTertiaryContainer = Color(0xFF202428),
        surfaceContainerLowest = Color(0xFFF9FAFB),
        surfaceContainerLow = Color(0xFFF3F5F6),
        surfaceContainer = Color(0xFFE2E7EB),
        surfaceContainerHigh = Color(0xFFE4E9EC),
        surfaceContainerHighest = Color(0xFFD8DEE2),
        outlineColor = Color(0xFF727D85),
        outlineVariantColor = Color(0xFF89949C),
        errorContainer = Color(0xFFF8E4E1),
        onErrorContainer = Color(0xFF842C25)
    )

    // ── Dark ─────────────────────────────────────────────────────────
    data object Dark : BackgroundTheme(
        backgroundColor = DarkBackground,
        primaryTextColor = DarkPrimaryText,
        secondaryTextColor = DarkSecondaryText,
        accentColor = DarkAccent,
        glassBorderColor = DarkGlassBorder,
        glassBorderGradientStart = DarkAccent.copy(alpha = 0.25f),
        glassBorderGradientEnd = DarkBorderGradientEnd.copy(alpha = 0.08f),
        glassGradientStart = Color.White.copy(alpha = 0.06f),
        glassGradientEnd = Color.White.copy(alpha = 0.02f),
        surfaceOverlayColor = DarkSurfaceOverlay,
        surfaceColor = DarkSurface,
        buttonContainerColor = Color(0xFF1A1F36),
        buttonContentColor = Color.White,
        categorySelectedColor = DarkAccent,
        categoryUnselectedColor = Color.White.copy(alpha = 0.7f),
        // M3 tonal
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnPrimaryContainer,
        secondaryContainer = DarkSecondaryContainer,
        onSecondaryContainer = DarkOnSecondaryContainer,
        tertiaryContainer = DarkTertiaryContainer,
        onTertiaryContainer = DarkOnTertiaryContainer,
        surfaceContainerLowest = DarkSurfaceContainerLowest,
        surfaceContainerLow = DarkSurfaceContainerLow,
        surfaceContainer = DarkSurfaceContainer,
        surfaceContainerHigh = DarkSurfaceContainerHigh,
        surfaceContainerHighest = DarkSurfaceContainerHighest,
        outlineColor = DarkOutline,
        outlineVariantColor = DarkOutlineVariant,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer
    )

    // ── Light ────────────────────────────────────────────────────────
    data object Light : BackgroundTheme(
        backgroundColor = LightBackground,
        primaryTextColor = LightPrimaryText,
        secondaryTextColor = LightSecondaryText,
        accentColor = LightAccent,
        glassBorderColor = LightGlassBorder,
        glassBorderGradientStart = LightAccent.copy(alpha = 0.20f),
        glassBorderGradientEnd = LightBorderGradientEnd.copy(alpha = 0.06f),
        glassGradientStart = Color.Black.copy(alpha = 0.04f),
        glassGradientEnd = Color.Black.copy(alpha = 0.01f),
        surfaceOverlayColor = LightSurfaceOverlay,
        usesBackgroundImage = true,
        surfaceColor = LightSurface,
        buttonContainerColor = Color(0xFF1A1A2E),
        buttonContentColor = Color.White,
        categorySelectedColor = LightAccent,
        categoryUnselectedColor = Color(0xFF1A1A2E).copy(alpha = 0.7f),
        // M3 tonal
        primaryContainer = LightPrimaryContainer,
        onPrimaryContainer = LightOnPrimaryContainer,
        secondaryContainer = LightSecondaryContainer,
        onSecondaryContainer = LightOnSecondaryContainer,
        tertiaryContainer = LightTertiaryContainer,
        onTertiaryContainer = LightOnTertiaryContainer,
        surfaceContainerLowest = LightSurfaceContainerLowest,
        surfaceContainerLow = LightSurfaceContainerLow,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerHigh = LightSurfaceContainerHigh,
        surfaceContainerHighest = LightSurfaceContainerHighest,
        outlineColor = LightOutline,
        outlineVariantColor = LightOutlineVariant,
        errorContainer = LightErrorContainer,
        onErrorContainer = LightOnErrorContainer
    )

    // ── Wooden ───────────────────────────────────────────────────────
    data object Wooden : BackgroundTheme(
        backgroundColor = WoodenBackground,
        primaryTextColor = WoodenPrimaryText,
        secondaryTextColor = WoodenSecondaryText,
        accentColor = WoodenAccent,
        glassBorderColor = WoodenGlassBorder,
        glassBorderGradientStart = WoodenAccent.copy(alpha = 0.25f),
        glassBorderGradientEnd = WoodenBorderGradientEnd.copy(alpha = 0.08f),
        glassGradientStart = Color(0xFFFFF8E1).copy(alpha = 0.06f),
        glassGradientEnd = Color(0xFFFFF8E1).copy(alpha = 0.02f),
        surfaceOverlayColor = WoodenSurfaceOverlay,
        surfaceColor = WoodenSurface,
        buttonContainerColor = Color(0xFF4E342E),
        buttonContentColor = Color(0xFFFFF8E1),
        categorySelectedColor = WoodenAccent,
        categoryUnselectedColor = Color(0xFFFFF8E1).copy(alpha = 0.7f),
        // M3 tonal
        primaryContainer = WoodenPrimaryContainer,
        onPrimaryContainer = WoodenOnPrimaryContainer,
        secondaryContainer = WoodenSecondaryContainer,
        onSecondaryContainer = WoodenOnSecondaryContainer,
        tertiaryContainer = WoodenTertiaryContainer,
        onTertiaryContainer = WoodenOnTertiaryContainer,
        surfaceContainerLowest = WoodenSurfaceContainerLowest,
        surfaceContainerLow = WoodenSurfaceContainerLow,
        surfaceContainer = WoodenSurfaceContainer,
        surfaceContainerHigh = WoodenSurfaceContainerHigh,
        surfaceContainerHighest = WoodenSurfaceContainerHighest,
        outlineColor = WoodenOutline,
        outlineVariantColor = WoodenOutlineVariant,
        errorContainer = WoodenErrorContainer,
        onErrorContainer = WoodenOnErrorContainer
    )

    // ── Ocean ────────────────────────────────────────────────────────
    data object Ocean : BackgroundTheme(
        backgroundColor = OceanBackground,
        primaryTextColor = OceanPrimaryText,
        secondaryTextColor = OceanSecondaryText,
        accentColor = OceanAccent,
        glassBorderColor = OceanGlassBorder,
        glassBorderGradientStart = OceanAccent.copy(alpha = 0.25f),
        glassBorderGradientEnd = OceanBorderGradientEnd.copy(alpha = 0.08f),
        glassGradientStart = Color.White.copy(alpha = 0.06f),
        glassGradientEnd = Color.White.copy(alpha = 0.02f),
        surfaceOverlayColor = OceanSurfaceOverlay,
        surfaceColor = OceanSurface,
        buttonContainerColor = Color(0xFF0277BD),
        buttonContentColor = Color.White,
        categorySelectedColor = OceanAccent,
        categoryUnselectedColor = Color.White.copy(alpha = 0.7f),
        // M3 tonal
        primaryContainer = OceanPrimaryContainer,
        onPrimaryContainer = OceanOnPrimaryContainer,
        secondaryContainer = OceanSecondaryContainer,
        onSecondaryContainer = OceanOnSecondaryContainer,
        tertiaryContainer = OceanTertiaryContainer,
        onTertiaryContainer = OceanOnTertiaryContainer,
        surfaceContainerLowest = OceanSurfaceContainerLowest,
        surfaceContainerLow = OceanSurfaceContainerLow,
        surfaceContainer = OceanSurfaceContainer,
        surfaceContainerHigh = OceanSurfaceContainerHigh,
        surfaceContainerHighest = OceanSurfaceContainerHighest,
        outlineColor = OceanOutline,
        outlineVariantColor = OceanOutlineVariant,
        errorContainer = OceanErrorContainer,
        onErrorContainer = OceanOnErrorContainer
    )

    // ── Sunset ───────────────────────────────────────────────────────
    data object Sunset : BackgroundTheme(
        backgroundColor = SunsetBackground,
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(SunsetBackground, SunsetBackgroundEnd)
        ),
        primaryTextColor = SunsetPrimaryText,
        secondaryTextColor = SunsetSecondaryText,
        accentColor = SunsetAccent,
        glassBorderColor = SunsetGlassBorder,
        glassBorderGradientStart = SunsetAccent.copy(alpha = 0.25f),
        glassBorderGradientEnd = SunsetBorderGradientEnd.copy(alpha = 0.08f),
        glassGradientStart = Color.White.copy(alpha = 0.06f),
        glassGradientEnd = Color.White.copy(alpha = 0.02f),
        surfaceOverlayColor = SunsetSurfaceOverlay,
        surfaceColor = SunsetSurface,
        buttonContainerColor = Color(0xFFD84315),
        buttonContentColor = Color.White,
        categorySelectedColor = SunsetAccent,
        categoryUnselectedColor = Color.White.copy(alpha = 0.7f),
        // M3 tonal
        primaryContainer = SunsetPrimaryContainer,
        onPrimaryContainer = SunsetOnPrimaryContainer,
        secondaryContainer = SunsetSecondaryContainer,
        onSecondaryContainer = SunsetOnSecondaryContainer,
        tertiaryContainer = SunsetTertiaryContainer,
        onTertiaryContainer = SunsetOnTertiaryContainer,
        surfaceContainerLowest = SunsetSurfaceContainerLowest,
        surfaceContainerLow = SunsetSurfaceContainerLow,
        surfaceContainer = SunsetSurfaceContainer,
        surfaceContainerHigh = SunsetSurfaceContainerHigh,
        surfaceContainerHighest = SunsetSurfaceContainerHighest,
        outlineColor = SunsetOutline,
        outlineVariantColor = SunsetOutlineVariant,
        errorContainer = SunsetErrorContainer,
        onErrorContainer = SunsetOnErrorContainer
    )

    companion object {
        /** Resolves a Firebase theme name string to a [BackgroundTheme]. */
        fun fromName(name: String?): BackgroundTheme = when (name?.lowercase()?.trim()) {
            "light" -> Light
            "wooden" -> Wooden
            "ocean" -> Ocean
            "sunset" -> Sunset
            else -> Dark // default
        }
    }
}
