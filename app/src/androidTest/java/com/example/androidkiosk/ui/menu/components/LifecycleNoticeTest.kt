package com.example.androidkiosk.ui.menu.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.androidkiosk.data.repository.lifecycleNoticeText
import com.example.androidkiosk.ui.theme.AndroidDeviceTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class LifecycleNoticeTest {
    @get:Rule val composeRule = createComposeRule()
    @Test fun graceAndClosureNoticeRemainReadableAtDoubleTextSize() {
        val message = mutableStateOf(lifecycleNoticeText("inactivity_grace", 0L))
        composeRule.setContent {
            AndroidDeviceTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Surface(Modifier.fillMaxSize()) { LifecycleNotice(message.value) }
                }
            }
        }
        composeRule.onNodeWithText(message.value!!).assertIsDisplayed()
        capture("lifecycle-inactivity-2x.png")
        composeRule.runOnIdle { message.value = lifecycleNoticeText("closing", 0L) }
        composeRule.onNodeWithText(message.value!!).assertIsDisplayed()
        capture("lifecycle-closure-2x.png")
    }
    private fun capture(name: String) {
        composeRule.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "device-ux")
        directory.mkdirs()
        File(directory, name).outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
