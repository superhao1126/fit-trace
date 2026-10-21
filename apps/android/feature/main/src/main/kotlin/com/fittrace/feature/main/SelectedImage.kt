package com.fittrace.feature.main

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream

internal fun readSelectedImage(context: Context, uri: Uri): Pair<ByteArray, String> {
    val mime = context.contentResolver.getType(uri).orEmpty()
    require(mime in setOf("image/jpeg", "image/png", "image/webp")) { "请选择 JPG、PNG 或 WebP 照片" }
    val bytes =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() <= 6 * 1024 * 1024) {
                val count = stream.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("无法读取照片，请重新选择")
    require(bytes.isNotEmpty() && bytes.size <= 6 * 1024 * 1024) { "照片需小于 6 MB" }
    return bytes to mime
}
