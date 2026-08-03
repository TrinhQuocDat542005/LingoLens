package com.quocdat.lingolens.camera

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

class CapturedImageRepository(private val context: Context) {
    fun createTempImageFile(): File {
        val outputDir = File(context.cacheDir, "captured_images").apply {
            if (!exists()) mkdirs()
        }

        cleanupExpiredImages(outputDir)

        // Tạo file ảnh mới với ID ngẫu nhiên không trùng lặp
        return File(outputDir, "img_${UUID.randomUUID()}.jpg")
    }

    fun getUri(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    fun delete(uri: Uri): Boolean {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: return false
        if (!name.startsWith("img_") || !name.endsWith(".jpg")) return false
        return File(File(context.cacheDir, "captured_images"), name).delete()
    }

    private fun cleanupExpiredImages(directory: File) {
        val cutoff = System.currentTimeMillis() - TEMP_IMAGE_MAX_AGE_MS
        directory.listFiles()
            ?.filter { it.isFile && it.lastModified() < cutoff }
            ?.forEach(File::delete)
    }

    private companion object {
        const val TEMP_IMAGE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
