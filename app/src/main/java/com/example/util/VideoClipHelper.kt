package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class EncodedShortVideo(
    val dataUrl: String,
    val durationSec: Long,
    val sizeBytes: Int
)

object VideoClipHelper {
    const val MAX_VIDEO_DURATION_MS = 20_000L
    const val MAX_VIDEO_DURATION_SEC = 20L
    const val MAX_VIDEO_RAW_BYTES = 660_000
    const val MAX_VIDEO_DATA_URL_CHARS = 920_000

    fun isDurationAllowed(durationMs: Long): Boolean {
        return durationMs in 1L..MAX_VIDEO_DURATION_MS
    }

    suspend fun prepareVideoForChat(
        context: Context,
        uri: Uri
    ): Result<EncodedShortVideo> = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            val durationMs = try {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            } finally {
                runCatching { retriever.release() }
            }

            if (durationMs <= 0L) {
                throw IllegalArgumentException("Could not read video duration. Try another short clip.")
            }
            if (durationMs > MAX_VIDEO_DURATION_MS) {
                val secs = (durationMs + 500L) / 1000L
                throw IllegalArgumentException(
                    "Video is ${secs}s. Short videos in chat can be up to $MAX_VIDEO_DURATION_SEC seconds."
                )
            }

            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16_384)
                var total = 0
                while (true) {
                    val read = stream.read(chunk)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_VIDEO_RAW_BYTES) {
                        throw IllegalArgumentException(
                            "Video file exceeds 660 KB limit for live chat sync. Record a lower-resolution clip (up to 20s) or trim it first."
                        )
                    }
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: throw IllegalArgumentException("Unable to read selected video.")

            if (bytes.isEmpty()) {
                throw IllegalArgumentException("Selected video is empty.")
            }

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val dataUrl = "data:video/mp4;base64,$base64"
            if (dataUrl.length > MAX_VIDEO_DATA_URL_CHARS) {
                throw IllegalArgumentException("Video exceeds sync limit. Please choose a shorter or smaller clip.")
            }

            val durationSec = ((durationMs + 500L) / 1000L).coerceIn(1L, MAX_VIDEO_DURATION_SEC)
            EncodedShortVideo(
                dataUrl = dataUrl,
                durationSec = durationSec,
                sizeBytes = bytes.size
            )
        }
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
        return runCatching {
            val dir = File(context.cacheDir, "chat_videos").apply { mkdirs() }
            val safeId = messageId.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "clip" }
            val target = File(dir, "video_$safeId.mp4")
            if (!target.exists() || target.length() != bytes.size.toLong()) {
                FileOutputStream(target).use { it.write(bytes) }
            }
            target
        }.getOrNull()
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

    fun formatDurationLabel(durationSec: Long): String {
        val safe = durationSec.coerceIn(1L, MAX_VIDEO_DURATION_SEC)
        val mins = safe / 60L
        val secs = safe % 60L
        return "%d:%02d".format(mins, secs)
    }

    fun createCaptureVideoUri(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "chat_video_capture").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.mp4")
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        return file to uri
    }
}
