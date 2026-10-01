package com.example.androidkiosk.ui.menu.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Keeps branch warnings visible in the native flow without blocking record access. */
@Composable
fun LifecycleNotice(message: String?) {
    if (message == null) return
    Text(message, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 80.dp, bottom = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite })
}
