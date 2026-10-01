package com.example.androidkiosk.ui.menu.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL

private const val MAX_QR_BYTES = 2 * 1024 * 1024

private suspend fun qrBytes(source: String): ByteArray = withContext(Dispatchers.IO) {
    if (source.startsWith("data:")) {
        val encoded = source.substringAfter(',', "")
        require(encoded.isNotEmpty()) { "QR image is empty" }
        Base64.decode(encoded, Base64.DEFAULT)
    } else {
        URL(source).openConnection().run {
            connectTimeout = 10_000
            readTimeout = 15_000
            getInputStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    require(output.size() + read <= MAX_QR_BYTES) { "QR image is too large" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        }
    }.also { require(it.isNotEmpty() && it.size <= MAX_QR_BYTES) { "Invalid QR image" } }
}

suspend fun saveQrImage(context: Context, source: String, orderNumber: String) {
    runCatching {
        val bytes = qrBytes(source)
        val name = "TouchOrders-$orderNumber-QR.png"
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TouchOrders")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
                context.contentResolver.openOutputStream(uri).use { requireNotNull(it).write(bytes) }
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } else {
                val directory = requireNotNull(context.getExternalFilesDir("Pictures/TouchOrders"))
                directory.mkdirs(); File(directory, name).writeBytes(bytes)
            }
        }
    }.onSuccess { Toast.makeText(context, "QR saved", Toast.LENGTH_SHORT).show() }
        .onFailure { Toast.makeText(context, "Could not save QR", Toast.LENGTH_SHORT).show() }
}

suspend fun shareQrImage(context: Context, source: String, orderNumber: String) {
    runCatching {
        val bytes = qrBytes(source)
        val directory = File(context.cacheDir, "shared_qr").apply { mkdirs() }
        val file = File(directory, "TouchOrders-$orderNumber-QR.png")
        withContext(Dispatchers.IO) { file.writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "QR Ph payment for order #$orderNumber")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share QR Ph code"))
    }.onFailure { Toast.makeText(context, "Could not share QR", Toast.LENGTH_SHORT).show() }
}
