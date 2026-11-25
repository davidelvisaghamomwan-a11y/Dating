package com.example.data

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloud object-storage gateway backed by Firebase Storage.
 *
 * Replaces embedding large Base64 blobs / chunked subcollections inside Firestore documents
 * with real binary file / byte uploads guarded by timeouts and structured error reporting.
 */
class CloudMediaStorageService(
    private val authProvider: () -> FirebaseAuth = { Firebase.auth },
    private val storageProvider: () -> FirebaseStorage = { Firebase.storage }
) {
    private fun validatePrerequisites(): Pair<String, FirebaseStorage> {
        val uid = authProvider().currentUser?.uid
            ?: throw IllegalStateException("Authentication required: please sign in before uploading media.")
        val storage = storageProvider()
        val bucket = storage.app.options.storageBucket?.trim().orEmpty()
        if (bucket.isBlank()) {
            throw IllegalStateException("Storage bucket configuration missing in Firebase project.")
        }
        return uid to storage
    }

    suspend fun uploadFile(
        file: File,
        remotePath: String,
        contentType: String,
        timeoutMs: Long = UPLOAD_TIMEOUT_MS
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0L) {
            val err = IllegalArgumentException("Media file is missing or empty.")
            Log.e(TAG, "uploadFile rejected for $remotePath: empty file", err)
            return@withContext Result.failure(err)
        }
        try {
            val (_, storage) = validatePrerequisites()
            val ref = storage.reference.child(remotePath.trimStart('/'))
            val metadata = storageMetadata {
                this.contentType = contentType
            }
            val downloadUrl = withTimeout(timeoutMs) {
                ref.putFile(Uri.fromFile(file), metadata).await()
                ref.downloadUrl.await().toString()
            }
            if (downloadUrl.isBlank()) {
                throw IOException("Storage returned an empty download URL.")
            }
            Result.success(downloadUrl)
        } catch (e: TimeoutCancellationException) {
            val msg = formatUploadError(e)
            Log.e(TAG, "uploadFile timed out after ${timeoutMs}ms for path=$remotePath: $msg", e)
            Result.failure(IOException(msg, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = formatUploadError(e)
            Log.e(TAG, "uploadFile failed for path=$remotePath: $msg", e)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun uploadBytes(
        bytes: ByteArray,
        remotePath: String,
        contentType: String,
        timeoutMs: Long = UPLOAD_TIMEOUT_MS
    ): Result<String> = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) {
            val err = IllegalArgumentException("Media payload is empty.")
            Log.e(TAG, "uploadBytes rejected for $remotePath: empty payload", err)
            return@withContext Result.failure(err)
        }
        try {
            val (_, storage) = validatePrerequisites()
            val ref = storage.reference.child(remotePath.trimStart('/'))
            val metadata = storageMetadata {
                this.contentType = contentType
            }
            val downloadUrl = withTimeout(timeoutMs) {
                ref.putBytes(bytes, metadata).await()
                ref.downloadUrl.await().toString()
            }
            if (downloadUrl.isBlank()) {
                throw IOException("Storage returned an empty download URL.")
            }
            Result.success(downloadUrl)
        } catch (e: TimeoutCancellationException) {
            val msg = formatUploadError(e)
            Log.e(TAG, "uploadBytes timed out after ${timeoutMs}ms for path=$remotePath: $msg", e)
            Result.failure(IOException(msg, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = formatUploadError(e)
            Log.e(TAG, "uploadBytes failed for path=$remotePath: $msg", e)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun ensureRemoteMediaUrl(
        rawDataUrlOrRemoteUrl: String,
        remotePath: String,
        defaultContentType: String = "image/jpeg",
        timeoutMs: Long = UPLOAD_TIMEOUT_MS
    ): Result<String> {
        val clean = rawDataUrlOrRemoteUrl.trim()
        if (clean.isBlank()) {
            return Result.failure(IllegalArgumentException("Media URL is empty."))
        }
        if (isRemoteHttpUrl(clean)) {
            return Result.success(clean)
        }
        val localFile = File(clean.removePrefix("file://"))
        if (clean.startsWith("file://") || (localFile.exists() && localFile.isFile)) {
            return uploadFile(
                file = localFile,
                remotePath = remotePath,
                contentType = defaultContentType,
                timeoutMs = timeoutMs
            )
        }
        return withContext(Dispatchers.IO) {
            try {
                val detectedType = clean.substringAfter("data:", "").substringBefore(";").takeIf {
                    it.contains("/")
                } ?: defaultContentType
                val base64Part = clean.substringAfter("base64,", clean)
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                if (bytes.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Decoded media bytes were empty."))
                }
                uploadBytes(
                    bytes = bytes,
                    remotePath = remotePath,
                    contentType = detectedType,
                    timeoutMs = timeoutMs
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = formatUploadError(e)
                Log.e(TAG, "ensureRemoteMediaUrl decode/upload failed for path=$remotePath: $msg", e)
                Result.failure(IllegalStateException(msg, e))
            }
        }
    }

    companion object {
        const val TAG = "KindredMediaUpload"
        const val UPLOAD_TIMEOUT_MS = 45_000L
        const val DOWNLOAD_TIMEOUT_MS = 30_000L

        fun isRemoteHttpUrl(url: String?): Boolean {
            val clean = url?.trim().orEmpty()
            return clean.startsWith("https://", ignoreCase = true) ||
                clean.startsWith("http://", ignoreCase = true)
        }

        fun formatUploadError(error: Throwable): String {
            if (error is TimeoutCancellationException) {
                return "Upload timed out after 45 seconds. Check your internet connection and try again."
            }
            val storageEx = generateSequence(error) { it.cause }
                .filterIsInstance<StorageException>()
                .firstOrNull()
            if (storageEx != null) {
                return when (storageEx.errorCode) {
                    StorageException.ERROR_NOT_AUTHENTICATED ->
                        "Upload blocked: please sign in again to authenticate with cloud storage."
                    StorageException.ERROR_NOT_AUTHORIZED ->
                        "Storage permission denied (403). Check Firebase Storage security rules for your account."
                    StorageException.ERROR_BUCKET_NOT_FOUND,
                    StorageException.ERROR_PROJECT_NOT_FOUND ->
                        "Cloud storage bucket is not configured or cannot be found (404)."
                    StorageException.ERROR_QUOTA_EXCEEDED ->
                        "Cloud storage quota exceeded. Please try again later."
                    StorageException.ERROR_RETRY_LIMIT_EXCEEDED ->
                        "Upload failed due to unstable network or timeout. Please retry."
                    StorageException.ERROR_CANCELED ->
                        "Upload was cancelled."
                    else -> {
                        val httpStatus = storageEx.httpResultCode
                        if (httpStatus == 403) {
                            "Storage permission denied (HTTP 403)."
                        } else if (httpStatus == 401) {
                            "Authentication expired (HTTP 401). Please sign in again."
                        } else {
                            storageEx.localizedMessage ?: "Cloud storage upload failed (code ${storageEx.errorCode})."
                        }
                    }
                }
            }
            val rawMsg = error.localizedMessage?.trim().orEmpty()
            return when {
                rawMsg.contains("403") || rawMsg.contains("permission", ignoreCase = true) ->
                    "Storage permission denied: $rawMsg"
                rawMsg.contains("bucket", ignoreCase = true) ->
                    "Storage bucket configuration error: $rawMsg"
                rawMsg.contains("auth", ignoreCase = true) || rawMsg.contains("sign in", ignoreCase = true) ->
                    rawMsg
                error is IOException ->
                    rawMsg.ifBlank { "Network error during media upload. Check connectivity and retry." }
                else ->
                    rawMsg.ifBlank { "Media upload failed. Please try again." }
            }
        }

        suspend fun downloadBytesFromUrl(
            url: String,
            timeoutMs: Long = DOWNLOAD_TIMEOUT_MS
        ): Result<ByteArray> = withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val bytes = withTimeout(timeoutMs) {
                    connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15_000
                        readTimeout = 25_000
                        instanceFollowRedirects = true
                        doInput = true
                    }
                    val conn = connection!!
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        throw IOException("Media download failed (HTTP $code)")
                    }
                    conn.inputStream.use { input ->
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                        }
                        out.toByteArray()
                    }
                }
                if (bytes.isEmpty()) throw IOException("Downloaded media was empty.")
                Result.success(bytes)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "downloadBytesFromUrl timed out for $url", e)
                Result.failure(IOException("Media download timed out.", e))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "downloadBytesFromUrl failed for $url", e)
                Result.failure(e)
            } finally {
                runCatching { connection?.disconnect() }
            }
        }

        suspend fun downloadUrlToFile(
            url: String,
            targetFile: File,
            timeoutMs: Long = DOWNLOAD_TIMEOUT_MS
        ): Result<File> = withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                targetFile.parentFile?.mkdirs()
                val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                withTimeout(timeoutMs) {
                    connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15_000
                        readTimeout = 25_000
                        instanceFollowRedirects = true
                        doInput = true
                    }
                    val conn = connection!!
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        throw IOException("Media download failed (HTTP $code)")
                    }
                    conn.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buf = ByteArray(32 * 1024)
                            while (true) {
                                val read = input.read(buf)
                                if (read <= 0) break
                                output.write(buf, 0, read)
                            }
                            output.flush()
                        }
                    }
                    if (!tempFile.exists() || tempFile.length() <= 0L) {
                        tempFile.delete()
                        throw IOException("Downloaded video file was empty.")
                    }
                    if (targetFile.exists()) targetFile.delete()
                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                }
                Result.success(targetFile)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "downloadUrlToFile timed out for $url", e)
                Result.failure(IOException("Media download timed out.", e))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "downloadUrlToFile failed for $url", e)
                Result.failure(e)
            } finally {
                runCatching { connection?.disconnect() }
            }
        }
    }
}
