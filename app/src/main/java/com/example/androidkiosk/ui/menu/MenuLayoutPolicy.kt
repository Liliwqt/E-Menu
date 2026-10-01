package com.example.androidkiosk.ui.menu

/** Keep phone ordering one tap away while retaining the tablet layout chooser. */
object MenuLayoutPolicy {
    fun isTablet(smallestWidthDp: Int): Boolean = smallestWidthDp >= 600

    fun initialMode(smallestWidthDp: Int): UIMode? =
        if (isTablet(smallestWidthDp)) null else UIMode.PORTRAIT

    /** The tablet chooser keeps its existing wide layout; registration stays portrait. */
    fun useLandscape(isTablet: Boolean, isAuthorized: Boolean, mode: UIMode?): Boolean =
        isTablet && isAuthorized && mode != UIMode.PORTRAIT

    fun portraitColumns(screenWidthDp: Int, fontScale: Float): Int =
        if (screenWidthDp < 360 || fontScale >= 1.5f) 1 else 2
}
