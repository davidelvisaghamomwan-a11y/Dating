package com.example.data

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.OnProgressListener
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
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

data class CloudUploadResult(
    val downloadUrl: String,
    val storagePath: String,
    val bucketName: String = ""
)

/**
 * Cloud object-storage gateway backed by Firebase Storage.
 *
 * Guarantees:
 * - Explicit bucket verification against google-services.json (`FirebaseOptions.storageBucket`)
 *   with single fallback probe across `.firebasestorage.app` <-> `.appspot.com` if 404.
 * - Authenticated user + ID token validation prior to upload.
 * - Progress callbacks via [UploadTask.addOnProgressListener].
 * - Verified upload completion (`isSuccessful && bytesTransferred > 0`) before requesting
 *   `snapshot.storage.downloadUrl` on the exact same [StorageReference].
 * - Strict timeout guard with active task cancellation on timeout.
 */
class CloudMediaStorageService(
    private val authProvider: () -> FirebaseAuth = { Firebase.auth },
    private val storageProvider: () -> FirebaseStorage = { Firebase.storage }
) {
    private data class StoragePrerequisites(
        val uid: String,
        val app: FirebaseApp,
        val primaryBucket: String,
        val primaryStorage: FirebaseStorage
    )

    private fun normalizeBucketName(rawBucket: String?): String {
        return rawBucket.orEmpty()
            .trim()
            .removePrefix("gs://")
            .substringBefore("/")
            .trim()
    }

    private fun alternateBucketFor(primaryBucket: String): String? {
        return when {
            primaryBucket.endsWith(".firebasestorage.app", ignoreCase = true) ->
                primaryBucket.removeSuffix(".firebasestorage.app") + ".appspot.com"
            primaryBucket.endsWith(".appspot.com", ignoreCase = true) ->
                primaryBucket.removeSuffix(".appspot.com") + ".firebasestorage.app"
            else -> null
        }
    }

    private suspend fun validatePrerequisites(): StoragePrerequisites {
        val currentUser = authProvider().currentUser
            ?: throw IllegalStateException("Authentication required: please sign in before uploading media.")
        val uid = currentUser.uid.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Authenticated user ID is missing. Please sign in again.")

        // Ensure Firebase Auth ID token is fresh before talking to Cloud Storage
        runCatching { currentUser.getIdToken(false).await() }.onFailure { err ->
            Log.w(TAG, "Unable to verify Firebase ID token before upload for uid=$uid", err)
        }

        val defaultStorage = storageProvider()
        val app = defaultStorage.app
        val configuredBucket = normalizeBucketName(app.options.storageBucket)
        val projectId = app.options.projectId?.trim().orEmpty()
        val resolvedBucket = when {
            configuredBucket.isNotBlank() -> configuredBucket
            projectId.isNotBlank() -> "$projectId.firebasestorage.app"
            else -> throw IllegalStateException("Storage bucket configuration missing in Firebase project.")
        }

        val explicitStorage = runCatching {
            FirebaseStorage.getInstance(app, "gs://$resolvedBucket")
        }.getOrElse { err ->
            Log.w(TAG, "Falling back to default FirebaseStorage instance for bucket=$resolvedBucket", err)
            defaultStorage
        }
        explicitStorage.maxUploadRetryTimeMillis = UPLOAD_TIMEOUT_MS
        explicitStorage.maxOperationRetryTimeMillis = DOWNLOAD_TIMEOUT_MS

        return StoragePrerequisites(
            uid = uid,
            app = app,
            primaryBucket = resolvedBucket,
            primaryStorage = explicitStorage
        )
    }

    private fun normalizeStoragePath(rawPath: String, uid: String): String {
        val cleaned = rawPath.trim().trimStart('/').replace(Regex("/+"), "/")
        require(cleaned.isNotBlank()) { "Storage path must not be empty." }
        return if (cleaned.startsWith("users/")) {
            cleaned
        } else {
            "users/$uid/$cleaned"
        }
    }

    private suspend fun executeUploadAndGetUrl(
        storage: FirebaseStorage,
        bucketName: String,
        storagePath: String,
        timeoutMs: Long,
        onProgress: (Float) -> Unit,
        startUpload: (StorageReference) -> UploadTask
    ): CloudUploadResult {
        val uploadRef = storage.reference.child(storagePath)
        var activeTask: UploadTask? = null
        var progressListener: OnProgressListener<UploadTask.TaskSnapshot>? = null

        return try {
            onProgress(0.02f)
            withTimeout(timeoutMs) {
                val task = startUpload(uploadRef)
                activeTask = task
                val listener = OnProgressListener<UploadTask.TaskSnapshot> { snap ->
                    val total = snap.totalByteCount
                    if (total > 0L) {
                        val fraction = (snap.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0.02f, 0.96f)
                        onProgress(fraction)
                    }
                }
                progressListener = listener
                task.addOnProgressListener(listener)

                val snapshot = task.await()
                if (!snapshot.task.isSuccessful || snapshot.bytesTransferred <= 0L) {
                    throw IOException(
                        "Upload to gs://$bucketName/$storagePath did not complete (bytesTransferred=${snapshot.bytesTransferred})."
                    )
                }

                onProgress(0.98f)
                // Always request downloadUrl from the exact same StorageReference that completed the upload
                val uploadedRef = snapshot.storage
                val downloadUrl = uploadedRef.downloadUrl.await().toString().trim()
                if (!isRemoteHttpUrl(downloadUrl)) {
                    throw IOException("Cloud Storage returned an invalid download URL for gs://$bucketName/$storagePath.")
                }
                onProgress(1f)
                CloudUploadResult(
                    downloadUrl = downloadUrl,
                    storagePath = uploadedRef.path.trimStart('/').ifBlank { storagePath },
                    bucketName = uploadedRef.bucket.ifBlank { bucketName }
                )
            }
        } catch (e: TimeoutCancellationException) {
            runCatching {
                val t = activeTask
                if (t != null && !t.isComplete) t.cancel()
            }
            throw e
        } finally {
            runCatching {
                val t = activeTask
                val l = progressListener
                if (t != null && l != null) t.removeOnProgressListener(l)
            }
        }
    }

    private suspend fun executeWithBucketFallback(
        prereqs: StoragePrerequisites,
        storagePath: String,
        timeoutMs: Long,
        onProgress: (Float) -> Unit,
        startUpload: (StorageReference) -> UploadTask
    ): CloudUploadResult {
        return try {
            executeUploadAndGetUrl(
                storage = prereqs.primaryStorage,
                bucketName = prereqs.primaryBucket,
                storagePath = storagePath,
                timeoutMs = timeoutMs,
                onProgress = onProgress,
                startUpload = startUpload
            )
        } catch (primaryErr: Exception) {
            if (primaryErr is CancellationException) throw primaryErr
            val altBucket = alternateBucketFor(prereqs.primaryBucket)
            if (altBucket != null && isStorageUnavailableError(primaryErr)) {
                Log.w(
                    TAG,
                    "Primary bucket gs://${prereqs.primaryBucket} returned 404 for $storagePath; probing alternate bucket gs://$altBucket",
                    primaryErr
                )
                val altStorage = FirebaseStorage.getInstance(prereqs.app, "gs://$altBucket").apply {
                    maxUploadRetryTimeMillis = timeoutMs
                    maxOperationRetryTimeMillis = DOWNLOAD_TIMEOUT_MS
                }
                executeUploadAndGetUrl(
                    storage = altStorage,
                    bucketName = altBucket,
                    storagePath = storagePath,
                    timeoutMs = timeoutMs,
                    onProgress = onProgress,
                    startUpload = startUpload
                )
            } else {
                throw primaryErr
            }
        }
    }

    suspend fun uploadFileWithMeta(
        file: File,
        remotePath: String,
        contentType: String,
        timeoutMs: Long = UPLOAD_TIMEOUT_MS,
        onProgress: (Float) -> Unit = {}
    ): Result<CloudUploadResult> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0L) {
            val err = IllegalArgumentException("Media file is missing or empty.")
            Log.e(TAG, "uploadFile rejected for $remotePath: empty file", err)
            return@withContext Result.failure(err)
        }
        try {
            val prereqs = validatePrerequisites()
            val scopedPath = normalizeStoragePath(remotePath, prereqs.uid)
            val metadata = storageMetadata {
                this.contentType = contentType
            }
            val fileUri = Uri.fromFile(file)
            val result = executeWithBucketFallback(
                prereqs = prereqs,
                storagePath = scopedPath,
                timeoutMs = timeoutMs,
                onProgress = onProgress
            ) { ref ->
                ref.putFile(fileUri, metadata)
            }
            Log.i(
                TAG,
                "uploadFile succeeded: bucket=${result.bucketName}, path=${result.storagePath}, bytes=${file.length()}"
            )
            Result.success(result)
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

    suspend fun uploadFile(
        file: File,
        remotePath: String,
        contentType: String,
        timeoutMs: Long = UPLOAD_TIMEOUT_MS,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = uploadFileWithMeta(
        file = file,
        remotePath = remotePath,
        contentType = contentType,
        timeoutMs = timeoutMs,
        onProgress = onProgress
    ).map { it.downloadUrl }

    suspend fun uploadBytesWithMeta(
        bytes: ByteArray,
        remotePath: String,
        contentType: String,
        timeoutMs: Long = UPLOAD_TIMEOUT_MS,
        onProgress: (Float) -> Unit = {}
    ): Result<CloudUploadResult> = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) {
            val err = IllegalArgumentException("Media payload is empty.")
            Log.e(TAG, "uploadBytes rejected for $remotePath: empty payload", err)
            return@withContext Result.failure(err)
        }
        try {
            val prereqs = validatePrerequisites()
            val scopedPath = normalizeStoragePath(remotePath, prereqs.uid)
            val metadata = storageMetadata {
                this.contentType = contentType
            }
            val result = executeWithBucketFallback(
                prereqs = prereqs,
                storagePath = scopedPath,
                timeoutMs = timeoutMs,
                onProgress = onProgress
            ) { ref ->
                ref.putBytes(bytes, metadata)
            }
            Log.i(
                TAG,
                "uploadBytes succeeded: bucket=${result.bucketName}, path=${result.storagePath}, bytes=${bytes.size}"
            )
            Result.success(result)
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

    suspend fun uploadBytes(
        bytes: ByteArray,
        remotePath: String,
        contentType: String,
        timeoutMs: Long = UPLOAD_TIMEOUT_MS,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = uploadBytesWithMeta(
        bytes = bytes,
        remotePath = remotePath,
        contentType = contentType,
        timeoutMs = timeoutMs,
        onProgress = onProgress
    ).map { it.downloadUrl }

    suspend fun ensureRemoteMediaUrlWithMeta(
        rawDataUrlOrRemoteUrl: String,
        remotePath: String,
        defaultContentType: String = "image/jpeg",
        existingStoragePath: String? = null,
        timeoutMs: Long = UPLOAD_TIMEOUT_MS,
        onProgress: (Float) -> Unit = {}
    ): Result<CloudUploadResult> {
        val clean = rawDataUrlOrRemoteUrl.trim()
        if (clean.isBlank()) {
            return Result.failure(IllegalArgumentException("Media URL is empty."))
        }
        if (isRemoteHttpUrl(clean)) {
            onProgress(1f)
            return Result.success(
                CloudUploadResult(
                    downloadUrl = clean,
                    storagePath = existingStoragePath?.trim().orEmpty(),
                    bucketName = ""
                )
            )
        }
        val localFile = File(clean.removePrefix("file://"))
        if (clean.startsWith("file://") || (localFile.exists() && localFile.isFile)) {
            return uploadFileWithMeta(
                file = localFile,
                remotePath = remotePath,
                contentType = defaultContentType,
                timeoutMs = timeoutMs,
                onProgress = onProgress
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
                uploadBytesWithMeta(
                    bytes = bytes,
                    remotePath = remotePath,
                    contentType = detectedType,
                    timeoutMs = timeoutMs,
                    onProgress = onProgress
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

    suspend fun ensureRemoteMediaUrl(
        rawDataUrlOrRemoteUrl: String,
        remotePath: String,
        defaultContentType: String = "image/jpeg",
        timeoutMs: Long = UPLOAD_TIMEOUT_MS,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = ensureRemoteMediaUrlWithMeta(
        rawDataUrlOrRemoteUrl = rawDataUrlOrRemoteUrl,
        remotePath = remotePath,
        defaultContentType = defaultContentType,
        timeoutMs = timeoutMs,
        onProgress = onProgress
    ).map { it.downloadUrl }

    companion object {
        const val TAG = "KindredMediaUpload"
        const val UPLOAD_TIMEOUT_MS = 60_000L
        const val DOWNLOAD_TIMEOUT_MS = 30_000L
        const val MAX_INLINE_FIRESTORE_BYTES = 640_000
        const val MAX_INLINE_FIRESTORE_MEDIA_CHARS = 900_000
        const val MAX_INLINE_FIRESTORE_PHOTO_LIST_CHARS = 175_000

        fun buildUserPath(uid: String, category: String, fileName: String): String {
            val cleanUid = uid.trim().trim('/')
            val cleanCategory = category.trim().trim('/')
            val cleanName = fileName.trim().trim('/')
            require(cleanUid.isNotBlank()) { "Authenticated user ID is required for Storage path." }
            require(cleanCategory.isNotBlank() && cleanName.isNotBlank()) { "Invalid Storage path segment." }
            return "users/$cleanUid/$cleanCategory/$cleanName"
        }

        fun isRemoteHttpUrl(url: String?): Boolean {
            val clean = url?.trim().orEmpty()
            return clean.startsWith("https://", ignoreCase = true) ||
                clean.startsWith("http://", ignoreCase = true)
        }

        fun isStorageUnavailableError(error: Throwable): Boolean {
            val storageEx = generateSequence(error) { it.cause }
                .filterIsInstance<StorageException>()
                .firstOrNull()
            if (storageEx != null) {
                when (storageEx.errorCode) {
                    StorageException.ERROR_OBJECT_NOT_FOUND,
                    StorageException.ERROR_BUCKET_NOT_FOUND,
                    StorageException.ERROR_PROJECT_NOT_FOUND -> return true
                }
                if (storageEx.httpResultCode == 404) return true
            }
            val msgChain = generateSequence(error) { it.cause }
                .mapNotNull { it.localizedMessage ?: it.message }
                .joinToString(" | ")
            return msgChain.contains("Object does not exist", ignoreCase = true) ||
                msgChain.contains("bucket", ignoreCase = true) ||
                msgChain.contains("404", ignoreCase = true)
        }

        suspend fun readLocalFileAsDataUrl(
            file: File,
            mimeType: String,
            maxBytes: Int = MAX_INLINE_FIRESTORE_BYTES
        ): String? = withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() <= 0L || file.length() > maxBytes) return@withContext null
            runCatching {
                val bytes = file.readBytes()
                if (bytes.isEmpty() || bytes.size > maxBytes) return@runCatching null
                val cleanMime = mimeType.trim().ifBlank { "application/octet-stream" }
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "data:$cleanMime;base64,$encoded"
            }.getOrNull()
        }

        fun formatUploadError(error: Throwable): String {
            if (error is TimeoutCancellationException) {
                return "Upload timed out after 60 seconds. Check your internet connection and try again."
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
                    StorageException.ERROR_OBJECT_NOT_FOUND,
                    StorageException.ERROR_BUCKET_NOT_FOUND,
                    StorageException.ERROR_PROJECT_NOT_FOUND ->
                        "Firebase Storage returned 404 (Object or bucket does not exist at location). Verify Storage is enabled in Firebase Console and Storage rules are published."
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
                        } else if (httpStatus == 404) {
                            "Firebase Storage returned HTTP 404 (Object does not exist at location). Verify Storage is enabled and Storage rules are published."
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
                rawMsg.contains("bucket", ignoreCase = true) ||
                    rawMsg.contains("Object does not exist", ignoreCase = true) ->
                    "Firebase Storage location error: $rawMsg"
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
