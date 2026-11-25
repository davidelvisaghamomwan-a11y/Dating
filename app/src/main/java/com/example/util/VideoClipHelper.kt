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
import java.io.FileInputStream
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
    // Firestore doc cap is 1 MiB and chunkCount rules cap at 1000 chunks (~500 MB max).
    // 650 KB raw bytes yields ~866 KB base64, well within Firestore's 920 KB limit.
    const val CHUNK_RAW_BYTES = 650_000
    const val INLINE_MAX_RAW_BYTES = 650_000
    const val MAX_CHUNKS = 1000
    const val MAX_CLOUD_VIDEO_BYTES = 150L * 1024L * 1024L
    private const val MAX_THUMBNAIL_DATA_URL_LEN = 180_000

    suspend fun prepareVideoForUpload(
        context: Context,
        uri: Uri,
        cacheKey: String = "upload_${System.currentTimeMillis()}"
    ): Result<PreparedVideoUpload> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)?.takeIf { it.startsWith("video/") } ?: "video/mp4"

            // Step 1: Stream content:// URI directly to app cache file so Firebase Storage can
            // upload it directly as a binary File without Base64 memory bloat or Firestore limits.
            val dir = File(context.cacheDir, "chat_videos").apply { mkdirs() }
            val safeId = cacheKey.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "clip" }
            val cachedFile = File(dir, "video_$safeId.mp4")

            var totalBytes = 0L
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cachedFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        totalBytes += read
                        if (totalBytes > MAX_CLOUD_VIDEO_BYTES) {
                            throw IllegalArgumentException("Video exceeds the 150 MB upload limit.")
                        }
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            } ?: throw IllegalArgumentException("Unable to read selected video.")

            if (totalBytes <= 0L || !cachedFile.exists() || cachedFile.length() <= 0L) {
                cachedFile.delete()
                throw IllegalArgumentException("Selected video is empty.")
            }

            // Step 2: Safely extract duration and JPEG thumbnail from local cached file.
            var durationMs = 0L
            var thumbnailDataUrl: String? = null
            val retriever = MediaMetadataRetriever()
            try {
                runCatching { retriever.setDataSource(cachedFile.absolutePath) }
                    .onFailure {
                        retriever.setDataSource(context, uri)
                    }
                durationMs = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                }.getOrDefault(0L)
                val frame = runCatching {
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                }.getOrNull()
                if (frame != null) {
                    thumbnailDataUrl = encodeThumbnailDataUrl(frame)
                    if (!frame.isRecycled) frame.recycle()
                }
            } catch (_: Throwable) {
                // Non-fatal: still allow uploading valid video bytes
            } finally {
                runCatching { retriever.release() }
            }

            val durationSec = if (durationMs > 0L) {
                ((durationMs + 500L) / 1000L).coerceAtLeast(1L)
            } else {
                1L
            }

            PreparedVideoUpload(
                inlineDataUrl = null,
                chunkBase64List = emptyList(),
                thumbnailDataUrl = thumbnailDataUrl,
                mimeType = mimeType,
                durationSec = durationSec,
                sizeBytes = totalBytes,
                localCachedFile = cachedFile
            )
        }
    }

    fun streamFileIntoBase64Chunks(file: File, chunkSize: Int = CHUNK_RAW_BYTES): List<String> {
        val len = file.length()
        if (len <= 0L) return emptyList()
        val expected = ((len + chunkSize - 1L) / chunkSize).toInt().coerceAtLeast(1)
        val result = ArrayList<String>(expected)
        val buffer = ByteArray(chunkSize)
        FileInputStream(file).use { input ->
            while (true) {
                var filled = 0
                while (filled < chunkSize) {
                    val read = input.read(buffer, filled, chunkSize - filled)
                    if (read <= 0) break
                    filled += read
                }
                if (filled <= 0) break
                result += Base64.encodeToString(buffer, 0, filled, Base64.NO_WRAP)
            }
        }
        return result
    }

    fun splitBytesIntoBase64Chunks(bytes: ByteArray, chunkSize: Int = CHUNK_RAW_BYTES): List<String> {
        if (bytes.isEmpty()) return emptyList()
        val result = ArrayList<String>((bytes.size + chunkSize - 1) / chunkSize)
        var offset = 0
        while (offset < bytes.size) {
            val len = minOf(chunkSize, bytes.size - offset)
            result += Base64.encodeToString(bytes, offset, len, Base64.NO_WRAP)
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
        if (com.example.data.CloudMediaStorageService.isRemoteHttpUrl(raw)) return null
        return runCatching {
            val payload = raw.substringAfter("base64,", raw)
            Base64.decode(payload, Base64.DEFAULT)
        }.getOrNull()
    }

    fun cacheTargetFile(context: Context, cacheKey: String): File {
        val dir = File(context.cacheDir, "chat_videos").apply { mkdirs() }
        val safeId = cacheKey.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "clip" }
        return File(dir, "video_$safeId.mp4")
    }

    fun materializeVideoFile(
        context: Context,
        messageId: String,
        mediaDataUrl: String?
    ): File? {
        val raw = mediaDataUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (com.example.data.CloudMediaStorageService.isRemoteHttpUrl(raw)) {
            val cached = cacheTargetFile(context, messageId)
            return cached.takeIf { it.exists() && it.length() > 0L }
        }
        val bytes = decodeVideoDataUrl(raw) ?: return null
        return runCatching { writeBytesToCacheFile(context, messageId, bytes) }.getOrNull()
    }

    suspend fun downloadOrMaterializeVideoFile(
        context: Context,
        cacheKey: String,
        mediaDataUrl: String?
    ): File? = withContext(Dispatchers.IO) {
        val raw = mediaDataUrl?.trim()?.takeIf { it.isNotBlank() } ?: return@withContext null
        val target = cacheTargetFile(context, cacheKey)
        if (target.exists() && target.length() > 0L) return@withContext target
        if (com.example.data.CloudMediaStorageService.isRemoteHttpUrl(raw)) {
            return@withContext com.example.data.CloudMediaStorageService
                .downloadUrlToFile(raw, target)
                .getOrNull()
        }
        materializeVideoFile(context, cacheKey, raw)
    }

    suspend fun extractVideoThumbnail(
        context: Context,
        messageId: String,
        mediaDataUrl: String?
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = downloadOrMaterializeVideoFile(context, messageId, mediaDataUrl) ?: return@withContext null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun encodeThumbnailDataUrl(bitmap: Bitmap): String? {
        val qualities = intArrayOf(84, 70, 52)
        val maxDims = intArrayOf(720, 480, 360)
        for (maxDim in maxDims) {
            val w = bitmap.width
            val h = bitmap.height
            val scale = minOf(maxDim.toFloat() / w.coerceAtLeast(1), maxDim.toFloat() / h.coerceAtLeast(1), 1f)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (w * scale).toInt().coerceAtLeast(1),
                    (h * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            for (q in qualities) {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
                val encoded = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                if (encoded.length <= MAX_THUMBNAIL_DATA_URL_LEN) {
                    if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
                    return encoded
                }
            }
            if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
        }
        return null
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

    fun createCaptureStoryPhotoUri(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "story_media_capture").apply { mkdirs() }
        val file = File(dir, "story_photo_${System.currentTimeMillis()}.jpg")
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        return file to uri
    }

    fun createCaptureStoryVideoUri(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "story_media_capture").apply { mkdirs() }
        val file = File(dir, "story_video_${System.currentTimeMillis()}.mp4")
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        return file to uri
    }
}
