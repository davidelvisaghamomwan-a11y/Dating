package com.example.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object MediaDownloadHelper {

    suspend fun saveImageToDevice(
        context: Context,
        mediaDataUrl: String,
        fileNamePrefix: String = "kindred_photo"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base64 = mediaDataUrl.substringAfter("base64,", mediaDataUrl)
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            if (bytes.isEmpty()) throw IllegalArgumentException("Image data is empty.")
            val mimeType = when {
                mediaDataUrl.contains("image/png", ignoreCase = true) -> "image/png"
                mediaDataUrl.contains("image/webp", ignoreCase = true) -> "image/webp"
                else -> "image/jpeg"
            }
            val ext = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val fileName = "${fileNamePrefix}_${System.currentTimeMillis()}.$ext"
            saveBytesViaScopedMediaStore(
                context = context,
                bytes = bytes,
                fileName = fileName,
                mimeType = mimeType,
                isVideo = false
            )
        }
    }

    suspend fun saveVideoFileToDevice(
        context: Context,
        sourceFile: File,
        fileNamePrefix: String = "kindred_video"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!sourceFile.exists() || sourceFile.length() <= 0L) {
                throw IllegalArgumentException("Video file unavailable for download.")
            }
            val bytes = sourceFile.readBytes()
            val fileName = "${fileNamePrefix}_${System.currentTimeMillis()}.mp4"
            saveBytesViaScopedMediaStore(
                context = context,
                bytes = bytes,
                fileName = fileName,
                mimeType = "video/mp4",
                isVideo = true
            )
        }
    }

    private fun saveBytesViaScopedMediaStore(
        context: Context,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        isVideo: Boolean
    ): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val relativePath = if (isVideo) {
                "${Environment.DIRECTORY_MOVIES}/Kindred"
            } else {
                "${Environment.DIRECTORY_PICTURES}/Kindred"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val itemUri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Could not create MediaStore destination.")
            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    out.write(bytes)
                } ?: throw IllegalStateException("Could not open MediaStore output stream.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            } catch (e: Exception) {
                runCatching { resolver.delete(itemUri, null, null) }
                throw e
            }
            return if (isVideo) "Saved to Movies/Kindred/$fileName" else "Saved to Pictures/Kindred/$fileName"
        } else {
            val subDir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            val dir = context.getExternalFilesDir(subDir)
                ?: File(context.filesDir, "downloads").apply { mkdirs() }
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { it.write(bytes) }
            MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), arrayOf(mimeType), null)
            return "Saved to ${outFile.name}"
        }
    }
}
