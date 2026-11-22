package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class PreparedVideoUpload(
    val inlineDataUrl: String?,
    val chunkBase64List: List<String>,
    val thumbnailDataUrl: String?,
    val mimeType: String,
    val durationSec: Long,
    val sizeBytes: Long,
    val localCachedFile: File
) {
    val chunkCount: Int get() = chunkBase64List.size
}

object VideoClipHelper {
    // Firestore doc cap is 1 MiB. Keeping each raw chunk at 500 KB (~667 KB Base64)
    // enables videos of unlimited file size and duration across ordered chunk docs.
    const val CHUNK_RAW_BYTES = 500_000
    const val INLINE_MAX_RAW_BYTES = 500_000

    suspend fun prepareVideoForUpload(
        context: Context,
        uri: Uri,
        cacheKey: String = "upload_${System.currentTimeMillis()}"
    ): Result<PreparedVideoUpload> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)?.takeIf { it.startsWith("video/") } ?: "video/mp4"

            val retriever = MediaMetadataRetriever()
            val durationMs: Long
            val thumbnailDataUrl: String?
            try {
                retriever.setDataSource(context, uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                thumbnailDataUrl = frame?.let { encodeThumbnailDataUrl(it) }
            } finally {
                runCatching { retriever.release() }
            }

            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Unable to read selected video.")
            if (bytes.isEmpty()) {
                throw IllegalArgumentException("Selected video is empty.")
            }

            val cachedFile = writeBytesToCacheFile(context, cacheKey, bytes)
            val durationSec = ((durationMs + 500L) / 1000L).coerceAtLeast(1L)

            if (bytes.size <= INLINE_MAX_RAW_BYTES) {
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                PreparedVideoUpload(
                    inlineDataUrl = "data:$mimeType;base64,$base64",
                    chunkBase64List = emptyList(),
                    thumbnailDataUrl = thumbnailDataUrl,
                    mimeType = mimeType,
                    durationSec = durationSec,
                    sizeBytes = bytes.size.toLong(),
                    localCachedFile = cachedFile
                )
            } else {
                val chunks = splitBytesIntoBase64Chunks(bytes, CHUNK_RAW_BYTES)
                PreparedVideoUpload(
                    inlineDataUrl = null,
                    chunkBase64List = chunks,
                    thumbnailDataUrl = thumbnailDataUrl,
                    mimeType = mimeType,
                    durationSec = durationSec,
                    sizeBytes = bytes.size.toLong(),
                    localCachedFile = cachedFile
                )
            }
        }
    }

    fun splitBytesIntoBase64Chunks(bytes: ByteArray, chunkSize: Int = CHUNK_RAW_BYTES): List<String> {
        if (bytes.isEmpty()) return emptyList()
        val result = ArrayList<String>((bytes.size + chunkSize - 1) / chunkSize)
        var offset = 0
        while (offset < bytes.size) {
            val len = minOf(chunkSize, bytes.size - offset)
            val slice = bytes.copyOfRange(offset, offset + len)
            result += Base64.encodeToString(slice, Base64.NO_WRAP)
            offset += len
        }
        return result
    }

    fun reassembleChunksToFile(
        context: Context,
        cacheKey: String,
        orderedBase64Chunks: List<String>
    ): File? {
        if (orderedBase64Chunks.isEmpty()) return null
        return runCatching {
            val dir = File(context.cacheDir, "chat_videos").apply { mkdirs() }
            val safeId = cacheKey.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "clip" }
            val target = File(dir, "video_$safeId.mp4")
            FileOutputStream(target).use { out ->
                orderedBase64Chunks.forEach { chunk ->
                    val decoded = Base64.decode(chunk.substringAfter("base64,", chunk), Base64.DEFAULT)
                    out.write(decoded)
                }
            }
            target
        }.getOrNull()
    }

    fun writeBytesToCacheFile(context: Context, cacheKey: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, "chat_videos").apply { mkdirs() }
        val safeId = cacheKey.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "clip" }
        val target = File(dir, "video_$safeId.mp4")
        if (!target.exists() || target.length() != bytes.size.toLong()) {
            FileOutputStream(target).use { it.write(bytes) }
        }
        return target
    }

    fun decodeVideoDataUrl(mediaDataUrl: String?): ByteArray? {
        val raw = mediaDataUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val payload = raw.substringAfter("base64,", raw)
            Base64.decode(payload, Base64.DEFAULT)
        }.getOrNull()
    }

    fun materializeVideoFile(
        context: Context,
        messageId: String,
        mediaDataUrl: String?
    ): File? {
        val bytes = decodeVideoDataUrl(mediaDataUrl) ?: return null
        return runCatching { writeBytesToCacheFile(context, messageId, bytes) }.getOrNull()
    }

    suspend fun extractVideoThumbnail(
        context: Context,
        messageId: String,
        mediaDataUrl: String?
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = materializeVideoFile(context, messageId, mediaDataUrl) ?: return@withContext null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun encodeThumbnailDataUrl(bitmap: Bitmap): String {
        val maxDim = 360
        val w = bitmap.width
        val h = bitmap.height
        val scale = minOf(maxDim.toFloat() / w.coerceAtLeast(1), maxDim.toFloat() / h.coerceAtLeast(1), 1f)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
        val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$encoded"
    }

    fun formatDurationLabel(durationSec: Long): String {
        val safe = durationSec.coerceAtLeast(0L)
        val hrs = safe / 3600L
        val mins = (safe % 3600L) / 60L
        val secs = safe % 60L
        return if (hrs > 0L) "%d:%02d:%02d".format(hrs, mins, secs) else "%d:%02d".format(mins, secs)
    }

    fun createCaptureVideoUri(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "chat_video_capture").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.mp4")
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        return file to uri
    }
}
