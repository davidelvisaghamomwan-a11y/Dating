package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import android.media.ExifInterface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared bitmap decoding, EXIF orientation, compression, and memory/disk caching helpers
 * for avatars, profile galleries, feed posts, stories, and video thumbnails.
 */
object MediaImageHelper {

    suspend fun compressUriToDataUrl(
        context: Context,
        uri: Uri,
        maxDim: Int = 768,
        maxRawBytes: Int = 125_000
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeAndOrientUri(context, uri) ?: return@withContext null
            compressBitmapToDataUrl(bitmap, maxDim = maxDim, maxRawBytes = maxRawBytes)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun compressUriForFeedPostDataUrl(
        context: Context,
        uri: Uri,
        maxDim: Int = 1280,
        maxRawBytes: Int = 340_000
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeAndOrientUri(context, uri) ?: return@withContext null
            compressBitmapToDataUrl(bitmap, maxDim = maxDim, maxRawBytes = maxRawBytes)
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun decodeAndOrientUri(context: Context, uri: Uri, targetMaxDim: Int = 1280): Bitmap? {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
        }
        val sampleSize = if (boundsOpts.outWidth > 0 && boundsOpts.outHeight > 0) {
            calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetMaxDim, targetMaxDim)
        } else {
            1
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val original = try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        } catch (_: Throwable) {
            null
        } ?: return null
        return applyExifOrientation(original, orientation)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Throwable) {
            bitmap
        }
    }

    suspend fun decodeUriToBitmap(context: Context, uri: Uri, maxDim: Int = 1080): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val original = decodeAndOrientUri(context, uri, targetMaxDim = maxDim) ?: return@withContext null
                resizeToMaxDim(original, maxDim = maxDim)
            } catch (_: Throwable) {
                null
            }
        }

    fun compressBitmapToDataUrl(
        bitmap: Bitmap,
        maxDim: Int = 768,
        maxRawBytes: Int = 125_000
    ): String {
        var currentMaxDim = maxDim
        var bytes: ByteArray = ByteArray(0)
        // Step down JPEG quality and, if still over maxRawBytes, downscale resolution cleanly.
        for (attempt in 0..3) {
            val resized = resizeToMaxDim(bitmap, maxDim = currentMaxDim)
            var quality = if (attempt == 0) 84 else 72
            do {
                val out = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bytes = out.toByteArray()
                quality -= 10
            } while (bytes.size > maxRawBytes && quality >= 36)
            if (bytes.size <= maxRawBytes) break
            currentMaxDim = (currentMaxDim * 0.7f).roundToInt().coerceAtLeast(180)
        }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$encoded"
    }

    private val memoryCacheMaxKb: Int =
        ((Runtime.getRuntime().maxMemory() / 1024L) / 8L).toInt().coerceIn(16 * 1024, 64 * 1024)

    private val decodedBitmapCache: LruCache<String, Bitmap> =
        object : LruCache<String, Bitmap>(memoryCacheMaxKb) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.allocationByteCount / 1024).coerceAtLeast(1)
        }

    @Volatile
    private var diskImageCacheDir: File? = null

    fun initImageCache(context: Context) {
        if (diskImageCacheDir == null) {
            val dir = File(context.applicationContext.cacheDir, "kindred_image_cache")
            if (!dir.exists()) dir.mkdirs()
            diskImageCacheDir = dir
        }
    }

    private fun cacheKeyForDataUrl(dataUrl: String): String =
        sha256Hex(dataUrl.toByteArray(Charsets.UTF_8))

    fun decodeBase64DataUrl(dataUrl: String?): ImageBitmap? {
        return decodeBitmapFromDataUrl(dataUrl)?.asImageBitmap()
    }

    fun decodeBitmapFromDataUrl(dataUrl: String?): Bitmap? {
        if (dataUrl.isNullOrBlank()) return null
        if (!dataUrl.startsWith("data:image", ignoreCase = true) &&
            (dataUrl.startsWith("http://", ignoreCase = true) || dataUrl.startsWith("https://", ignoreCase = true))
        ) {
            return null
        }
        return try {
            val key = cacheKeyForDataUrl(dataUrl)
            decodedBitmapCache.get(key)?.takeIf { !it.isRecycled }?.let { return it }

            val cacheFile = diskImageCacheDir?.let { File(it, "$key.bin") }
            if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0L) {
                val diskOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(cacheFile.absolutePath, diskOpts)
                val diskSample = if (diskOpts.outWidth > 0 && diskOpts.outHeight > 0) {
                    calculateInSampleSize(diskOpts.outWidth, diskOpts.outHeight, 1280, 1280)
                } else 1
                val diskBmp = BitmapFactory.decodeFile(
                    cacheFile.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = diskSample }
                )
                if (diskBmp != null) {
                    decodedBitmapCache.put(key, diskBmp)
                    return diskBmp
                }
            }

            val base64Part = dataUrl.substringAfter("base64,", dataUrl)
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            if (bytes.isEmpty()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                calculateInSampleSize(bounds.outWidth, bounds.outHeight, 1280, 1280)
            } else 1
            val decoded = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return null
            decodedBitmapCache.put(key, decoded)
            if (cacheFile != null && !cacheFile.exists()) {
                runCatching {
                    cacheFile.writeBytes(bytes)
                    trimDiskCacheIfNeeded(diskImageCacheDir)
                }
            }
            decoded
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Safely compacts an inline image data URL (or validates an http/https URL) so Firestore
     * document fields stay strictly within rules size limits without corrupting Base64 payloads.
     */
    fun compactImageDataUrl(
        rawUrl: String?,
        maxDim: Int = 512,
        maxRawBytes: Int = 55_000,
        maxDataUrlChars: Int = 180_000
    ): String? {
        return try {
            val clean = rawUrl?.trim().orEmpty()
            if (clean.isBlank()) return null
            if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
                return clean.take(2048)
            }
            if (!clean.startsWith("data:image", ignoreCase = true)) {
                return null
            }
            val maxDataUrlLen = minOf(maxDataUrlChars, (maxRawBytes * 4 / 3) + 64)
            val bmp = decodeBitmapFromDataUrl(clean) ?: return null
            if (clean.length <= maxDataUrlLen && bmp.width <= maxDim && bmp.height <= maxDim) {
                return clean
            }
            val compacted = compressBitmapToDataUrl(bmp, maxDim = maxDim, maxRawBytes = maxRawBytes)
            compacted.takeIf { it.length <= maxDataUrlChars }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun prefetchDataUrls(urls: List<String>) = withContext(Dispatchers.Default) {
        urls.asSequence()
            .filter { it.startsWith("data:image") }
            .distinct()
            .take(18)
            .forEach { url ->
                decodeBitmapFromDataUrl(url)
            }
    }

    private fun trimDiskCacheIfNeeded(dir: File?) {
        val cacheDir = dir ?: return
        val files = cacheDir.listFiles() ?: return
        if (files.size <= 160) return
        files.sortedBy { it.lastModified() }
            .take(files.size - 120)
            .forEach { runCatching { it.delete() } }
    }

    private fun resizeToMaxDim(bitmap: Bitmap, maxDim: Int): Bitmap {
        val scale = min(1f, maxDim.toFloat() / max(bitmap.width, bitmap.height).toFloat())
        val targetW = ((bitmap.width * scale).roundToInt()).let { if (it % 2 == 0) it else it - 1 }.coerceAtLeast(160)
        val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(160)
        return if (bitmap.width == targetW && bitmap.height == targetH) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
