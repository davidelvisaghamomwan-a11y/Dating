package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.FavoriteRecord
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.FollowRecord
import com.example.model.FriendRequestRecord
import com.example.model.FriendshipRecord
import com.example.model.KindredConstants
import com.example.model.MediaChunkRecord
import com.example.model.ModerationNoticeRecord
import com.example.model.ProfileLikeRecord
import com.example.model.ProfileVideoItem
import com.example.model.ProfileViewRecord
import com.example.model.PublicProfile
import com.example.model.ReportRecord
import com.example.model.StoryHighlight
import com.example.model.StoryItem
import com.example.model.SwipeRecord
import com.example.model.UserPresence
import com.example.model.VideoPost
import com.example.util.MediaImageHelper
import com.example.util.PreparedVideoUpload
import com.example.util.VideoClipHelper
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException

/**
 * Modular repository handling Friendships, Profile Views, Likes, Favorites,
 * Profile Videos, 24h Stories, Video Feed, Cloud Storage Media Sync,
 * All Presence Listing, Admin Moderation, and Full Account Deletion.
 */
class SocialAndMediaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val cloudStorage: CloudMediaStorageService = CloudMediaStorageService(authProvider = { auth })
) {
    private val refreshedFeedPostsFlow = MutableSharedFlow<List<FeedPost>>(replay = 1, extraBufferCapacity = 16)
    private val refreshedStoriesFlow = MutableSharedFlow<List<StoryItem>>(replay = 1, extraBufferCapacity = 16)
    private val refreshedVideoFeedFlow = MutableSharedFlow<List<VideoPost>>(replay = 1, extraBufferCapacity = 16)

    private val deletedFeedPostIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val deletedVideoPostIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val deletedProfileVideoIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val deletedStoryIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    private fun DocumentSnapshot.extractOwnedStoragePaths(): List<String> = buildList {
        getString("mediaStoragePath")?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        getString("thumbnailStoragePath")?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        (get("mediaStoragePaths") as? List<*>)?.forEach { item ->
            (item as? String)?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }.distinct()

    private suspend fun deleteSubcollectionDocsBestEffort(
        collection: CollectionReference,
        timeoutMs: Long = 2_500L
    ) {
        runCatching {
            withTimeoutOrNull(timeoutMs) {
                val snap = runCatching { collection.get(Source.SERVER).await() }
                    .getOrElse { collection.get(Source.DEFAULT).await() }
                if (!snap.isEmpty) {
                    snap.documents.chunked(400).forEach { chunk ->
                        val batch = db.batch()
                        chunk.forEach { batch.delete(it.reference) }
                        batch.commit().await()
                    }
                }
            }
        }
    }

    private suspend fun deleteStoragePathsBestEffort(paths: List<String>) {
        if (paths.isEmpty()) return
        runCatching {
            withTimeoutOrNull(4_000L) {
                coroutineScope {
                    paths.map { path ->
                        async(Dispatchers.IO) { cloudStorage.deleteFileIfOwned(path) }
                    }.awaitAll()
                }
            }
        }
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    private fun handleStreamError(error: Throwable, op: OperationType, path: String, expectedUid: String): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    // --- Chunked Video Upload & Reassembly ---

    suspend fun uploadChunksToSubcollection(
        parentCollection: CollectionReference,
        ownerUid: String,
        participantUids: List<String>? = null,
        chunks: List<String>
    ) = coroutineScope {
        if (chunks.isEmpty()) return@coroutineScope
        // Parallelize writes across concurrent batches for rapid multi-chunk video upload
        val batchSize = 6
        val batches = chunks.chunked(batchSize)
        batches.mapIndexed { batchIdx, chunkSubList ->
            async(Dispatchers.IO) {
                val batch = db.batch()
                val startSeq = batchIdx * batchSize
                chunkSubList.forEachIndexed { offset, chunkData ->
                    val seq = startSeq + offset
                    val chunkRef = parentCollection.document(seq.toString())
                    val map = mutableMapOf<String, Any>(
                        "ownerUid" to ownerUid,
                        "seq" to seq.toLong(),
                        "dataBase64" to chunkData,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    if (participantUids != null && participantUids.size == 2) {
                        map["participantUids"] = participantUids.sorted()
                    }
                    batch.set(chunkRef, map)
                }
                batch.commit().await()
            }
        }.awaitAll()
    }

    suspend fun resolveVideoToFile(
        context: Context,
        cacheKey: String,
        inlineDataUrl: String?,
        chunkCount: Long,
        chunksCollection: CollectionReference
    ): Result<File> = runCatching {
        val existingCache = VideoClipHelper.cacheTargetFile(context, cacheKey)
        if (existingCache.exists() && existingCache.length() > 0L) {
            return@runCatching existingCache
        }
        val rawUrl = inlineDataUrl?.trim().orEmpty()
        if (CloudMediaStorageService.isRemoteHttpUrl(rawUrl)) {
            return@runCatching CloudMediaStorageService.downloadUrlToFile(rawUrl, existingCache).getOrThrow()
        }
        if (rawUrl.isNotBlank()) {
            return@runCatching VideoClipHelper.materializeVideoFile(context, cacheKey, rawUrl)
                ?: throw IllegalStateException("Could not decode inline video data.")
        }
        if (chunkCount <= 0L) {
            throw IllegalStateException("Video source is unavailable.")
        }
        val snap = chunksCollection.get().await()
        val ordered = snap.toObjects(MediaChunkRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.seq }
            .map { it.dataBase64 }
            .filter { it.isNotBlank() }
        VideoClipHelper.reassembleChunksToFile(context, cacheKey, ordered)
            ?: throw IllegalStateException("Failed to assemble video chunks from Firestore.")
    }

    private fun DocumentSnapshot.toStoryItemSafe(): StoryItem? {
        return runCatching {
            toObject(StoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.copy(id = id)
        }.onFailure { err ->
            Log.e("KindredStories", "Skipping malformed story document $id", err)
        }.getOrNull()
    }

    private fun DocumentSnapshot.toStoryHighlightSafe(): StoryHighlight? {
        return runCatching {
            toObject(StoryHighlight::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.copy(id = id)
        }.onFailure { err ->
            Log.e("KindredHighlights", "Skipping malformed highlight document $id", err)
        }.getOrNull()
    }

    private fun DocumentSnapshot.toFeedPostSafe(): FeedPost? {
        return runCatching {
            val direct = toObject(FeedPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (direct != null) {
                val resolvedUserId = if (direct.userId.isNotBlank()) direct.userId else (data?.get("authorId") as? String).orEmpty()
                direct.copy(id = id, postId = id, userId = resolvedUserId)
            } else null
        }.recoverCatching {
            val d = data ?: return null
                val resolvedId = id
            val userId = (d["userId"] as? String) ?: (d["authorId"] as? String) ?: ""
            val authorName = (d["authorName"] as? String) ?: "Kindred Member"
            val authorPhoto = d["authorPhoto"] as? String
            val authorCountry = d["authorCountry"] as? String
            val authorCity = d["authorCity"] as? String
            val caption = (d["caption"] as? String) ?: ""
            val mediaType = (d["mediaType"] as? String) ?: "NONE"
            val mediaDataUrl = d["mediaDataUrl"] as? String
            val mediaStoragePath = d["mediaStoragePath"] as? String
            val mediaUrl = d["mediaUrl"] as? String
            val rawMediaUrls = d["mediaUrls"] as? List<*>
            val mediaUrls = rawMediaUrls?.mapNotNull { it as? String } ?: emptyList()
            val rawMediaStoragePaths = d["mediaStoragePaths"] as? List<*>
            val mediaStoragePaths = rawMediaStoragePaths?.mapNotNull { it as? String } ?: emptyList()
            val thumbnailDataUrl = d["thumbnailDataUrl"] as? String
            val thumbnailStoragePath = d["thumbnailStoragePath"] as? String
            val mediaMimeType = d["mediaMimeType"] as? String
            val mediaDurationSec = (d["mediaDurationSec"] as? Number)?.toLong() ?: 0L
            val mediaByteSize = (d["mediaByteSize"] as? Number)?.toLong() ?: 0L
            val chunkCount = (d["chunkCount"] as? Number)?.toLong() ?: 0L
            val rawLiked = d["likedByUids"] as? List<*>
            val likedByUids = rawLiked?.mapNotNull { it as? String } ?: emptyList()
            val rawSaved = d["savedByUids"] as? List<*>
            val savedByUids = rawSaved?.mapNotNull { it as? String } ?: emptyList()
            val commentCount = (d["commentCount"] as? Number)?.toLong()
                ?: (d["commentsCount"] as? Number)?.toLong() ?: 0L
            val createdAt = d["createdAt"] as? Timestamp
            FeedPost(
                id = resolvedId,
                postId = resolvedId,
                userId = userId,
                authorId = userId,
                authorName = authorName,
                authorPhoto = authorPhoto,
                authorCountry = authorCountry,
                authorCity = authorCity,
                caption = caption,
                mediaType = mediaType,
                mediaDataUrl = mediaDataUrl,
                mediaStoragePath = mediaStoragePath,
                mediaUrl = mediaUrl,
                mediaUrls = mediaUrls,
                mediaStoragePaths = mediaStoragePaths,
                thumbnailDataUrl = thumbnailDataUrl,
                thumbnailStoragePath = thumbnailStoragePath,
                mediaMimeType = mediaMimeType,
                mediaDurationSec = mediaDurationSec,
                mediaByteSize = mediaByteSize,
                chunkCount = chunkCount,
                likedByUids = likedByUids,
                savedByUids = savedByUids,
                commentCount = commentCount,
                createdAt = createdAt
            )
        }.onFailure { err ->
            Log.e("KindredFeedPosts", "Skipping malformed feed post document $id", err)
        }.getOrNull()
    }

    private fun DocumentSnapshot.toVideoPostSafe(): VideoPost? {
        return runCatching {
            val direct = toObject(VideoPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (direct != null) {
                val resolvedUserId = if (direct.userId.isNotBlank()) direct.userId else (data?.get("authorId") as? String).orEmpty()
                direct.copy(id = id, userId = resolvedUserId)
            } else null
        }.recoverCatching {
            val d = data ?: return null
            val resolvedId = id
            val userId = (d["userId"] as? String) ?: (d["authorId"] as? String) ?: ""
            val authorName = (d["authorName"] as? String) ?: "Kindred Member"
            val authorPhoto = d["authorPhoto"] as? String
            val authorCountry = d["authorCountry"] as? String
            val caption = (d["caption"] as? String) ?: ""
            val mediaDataUrl = d["mediaDataUrl"] as? String
            val mediaStoragePath = d["mediaStoragePath"] as? String
            val thumbnailDataUrl = d["thumbnailDataUrl"] as? String
            val thumbnailStoragePath = d["thumbnailStoragePath"] as? String
            val mediaMimeType = d["mediaMimeType"] as? String
            val mediaDurationSec = (d["mediaDurationSec"] as? Number)?.toLong() ?: 0L
            val mediaByteSize = (d["mediaByteSize"] as? Number)?.toLong() ?: 0L
            val chunkCount = (d["chunkCount"] as? Number)?.toLong() ?: 0L
            val rawLiked = d["likedByUids"] as? List<*>
            val likedByUids = rawLiked?.mapNotNull { it as? String } ?: emptyList()
            val createdAt = d["createdAt"] as? Timestamp
            VideoPost(
                id = resolvedId,
                userId = userId,
                authorName = authorName,
                authorPhoto = authorPhoto,
                authorCountry = authorCountry,
                caption = caption,
                mediaDataUrl = mediaDataUrl,
                mediaStoragePath = mediaStoragePath,
                thumbnailDataUrl = thumbnailDataUrl,
                thumbnailStoragePath = thumbnailStoragePath,
                mediaMimeType = mediaMimeType,
                mediaDurationSec = mediaDurationSec,
                mediaByteSize = mediaByteSize,
                chunkCount = chunkCount,
                likedByUids = likedByUids,
                createdAt = createdAt
            )
        }.onFailure { err ->
            Log.e("KindredVideoPosts", "Skipping malformed video post document $id", err)
        }.getOrNull()
    }

    private data class ResolvedImageUpload(
        val url: String,
        val storagePath: String? = null
    )

    private data class ResolvedVideoUpload(
        val inlineMediaUrl: String? = null,
        val storagePath: String? = null,
        val chunks: List<String> = emptyList()
    )

    private suspend fun resolveImageForFirestoreOrCloud(
        rawImage: String,
        remotePath: String,
        maxDim: Int = 1080,
        maxRawBytes: Int = 580_000,
        maxDataUrlChars: Int = CloudMediaStorageService.MAX_INLINE_FIRESTORE_MEDIA_CHARS,
        onProgress: (Float) -> Unit = {}
    ): ResolvedImageUpload {
        val clean = rawImage.trim()
        if (CloudMediaStorageService.isRemoteHttpUrl(clean)) {
            onProgress(1f)
            return ResolvedImageUpload(url = clean.take(2048), storagePath = null)
        }
        val uploaded = cloudStorage.ensureRemoteMediaUrlWithMeta(
            rawDataUrlOrRemoteUrl = clean,
            remotePath = remotePath,
            defaultContentType = "image/jpeg",
            onProgress = onProgress
        )
        return uploaded.fold(
            onSuccess = { meta ->
                if (!CloudMediaStorageService.isRemoteHttpUrl(meta.downloadUrl)) {
                    throw IllegalStateException("Cloud Storage returned an invalid download URL.")
                }
                ResolvedImageUpload(
                    url = meta.downloadUrl.take(2048),
                    storagePath = meta.storagePath.takeIf { it.startsWith("users/") }
                )
            },
            onFailure = { err ->
                if (CloudMediaStorageService.isStorageUnavailableError(err)) {
                    Log.w(
                        CloudMediaStorageService.TAG,
                        "Storage unavailable for $remotePath; falling back to inline Firestore image",
                        err
                    )
                    val compacted = MediaImageHelper.compactImageDataUrl(
                        rawUrl = clean,
                        maxDim = maxDim,
                        maxRawBytes = maxRawBytes,
                        maxDataUrlChars = maxDataUrlChars
                    ) ?: throw IllegalStateException("Photo exceeds Firestore fallback limit or could not be encoded.", err)
                    ResolvedImageUpload(url = compacted, storagePath = null)
                } else {
                    throw err
                }
            }
        )
    }

    private suspend fun resolveVideoForFirestoreOrCloud(
        prepared: PreparedVideoUpload,
        remotePath: String,
        onProgress: (Float) -> Unit = {}
    ): ResolvedVideoUpload {
        val uploaded = cloudStorage.uploadFileWithMeta(
            file = prepared.localCachedFile,
            remotePath = remotePath,
            contentType = prepared.mimeType,
            onProgress = onProgress
        )
        return uploaded.fold(
            onSuccess = { meta ->
                ResolvedVideoUpload(
                    inlineMediaUrl = meta.downloadUrl,
                    storagePath = meta.storagePath.takeIf { it.startsWith("users/") }
                )
            },
            onFailure = { err ->
                if (!CloudMediaStorageService.isStorageUnavailableError(err)) {
                    throw err
                }
                Log.w(
                    CloudMediaStorageService.TAG,
                    "Storage unavailable for $remotePath; falling back to Firestore video storage",
                    err
                )
                val inline = CloudMediaStorageService.readLocalFileAsDataUrl(
                    file = prepared.localCachedFile,
                    mimeType = prepared.mimeType
                )?.takeIf { it.length <= CloudMediaStorageService.MAX_INLINE_FIRESTORE_MEDIA_CHARS }
                if (inline != null) {
                    ResolvedVideoUpload(inlineMediaUrl = inline)
                } else {
                    val chunks = withContext(Dispatchers.IO) {
                        VideoClipHelper.streamFileIntoBase64Chunks(prepared.localCachedFile)
                    }
                    if (chunks.isEmpty() || chunks.size > VideoClipHelper.MAX_CHUNKS) {
                        throw IllegalStateException(
                            "Video is too large for inline Firestore fallback (${chunks.size} chunks). Configure a Firebase Storage bucket to upload large videos.",
                            err
                        )
                    }
                    ResolvedVideoUpload(chunks = chunks)
                }
            }
        )
    }

    private suspend fun resolveThumbnailForFirestoreOrCloud(
        rawThumbnail: String?,
        remotePath: String
    ): ResolvedImageUpload? {
        val clean = rawThumbnail?.trim().orEmpty()
        if (clean.isBlank()) return null
        if (CloudMediaStorageService.isRemoteHttpUrl(clean)) {
            return ResolvedImageUpload(url = clean.take(2048), storagePath = null)
        }
        val uploaded = cloudStorage.ensureRemoteMediaUrlWithMeta(
            rawDataUrlOrRemoteUrl = clean,
            remotePath = remotePath,
            defaultContentType = "image/jpeg"
        )
        return uploaded.fold(
            onSuccess = { meta ->
                ResolvedImageUpload(
                    url = meta.downloadUrl.take(2048),
                    storagePath = meta.storagePath.takeIf { it.startsWith("users/") }
                )
            },
            onFailure = { err ->
                if (!CloudMediaStorageService.isStorageUnavailableError(err)) {
                    throw err
                }
                Log.w(
                    CloudMediaStorageService.TAG,
                    "Storage unavailable for thumbnail $remotePath; falling back to compact inline thumbnail",
                    err
                )
                MediaImageHelper.compactImageDataUrl(clean, maxDim = 480, maxRawBytes = 40_000)
                    ?.takeIf { it.isNotBlank() && it.length <= 180_000 }
                    ?.let { ResolvedImageUpload(url = it, storagePath = null) }
            }
        )
    }

    // --- Live Presence Listing for Active Users Screen ---

    fun observeAllPresence(viewerUid: String): Flow<List<UserPresence>> {
        if (viewerUid.isBlank()) return flowOf(emptyList())
        return flow {
            emitAll(
                db.collection("presence")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(UserPresence::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "presence", viewerUid) }
            )
        }
    }

    // --- Social Graph: Follows / Followers ---

    fun observeAllFollows(viewerUid: String): Flow<List<FollowRecord>> {
        if (viewerUid.isBlank()) return flowOf(emptyList())
        return flow {
            emitAll(
                db.collection("follows")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(FollowRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "follows", viewerUid) }
            )
        }
    }

    suspend fun followUser(
        targetUid: String,
        myProfile: PublicProfile?,
        fallbackName: String?
    ): Result<Unit> {
        var docPath = "follows"
        return try {
            val uid = requireUserId()
            require(targetUid.isNotBlank() && targetUid != uid) { "Invalid profile to follow." }
            val followRef = db.collection("follows").document("${uid}_$targetUid")
            docPath = followRef.path
            if (followRef.get().await().exists()) {
                return Result.success(Unit)
            }
            val payload = mutableMapOf<String, Any>(
                "followerUid" to uid,
                "followingUid" to targetUid,
                "followerName" to (myProfile?.displayName ?: fallbackName ?: "Kindred Member").take(40),
                "createdAt" to FieldValue.serverTimestamp()
            )
            val photo = myProfile?.allProfilePhotos()?.firstOrNull()
            if (!photo.isNullOrBlank() && photo.length <= 180_000) {
                payload["followerPhoto"] = photo
            }
            followRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun unfollowUser(targetUid: String): Result<Unit> {
        var docPath = "follows"
        return try {
            val uid = requireUserId()
            val followRef = db.collection("follows").document("${uid}_$targetUid")
            docPath = followRef.path
            if (followRef.get().await().exists()) {
                followRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun removeFollower(followerUid: String): Result<Unit> {
        var docPath = "follows"
        return try {
            val uid = requireUserId()
            val followRef = db.collection("follows").document("${followerUid}_$uid")
            docPath = followRef.path
            if (followRef.get().await().exists()) {
                followRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Friend Requests & Friendships ---

    fun observeFriendRequestsForUser(userId: String): Flow<List<FriendRequestRecord>> {
        if (userId.isBlank()) return flowOf(emptyList())
        return flow {
            val incomingFlow = db.collection("friendRequests")
                .whereEqualTo("toUid", userId)
                .snapshots()
                .map { it.toObjects(FriendRequestRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            val outgoingFlow = db.collection("friendRequests")
                .whereEqualTo("fromUid", userId)
                .snapshots()
                .map { it.toObjects(FriendRequestRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            emitAll(
                kotlinx.coroutines.flow.combine(incomingFlow, outgoingFlow) { inc, out ->
                    (inc + out).distinctBy { it.id }
                }.catch { handleStreamError(it, OperationType.LIST, "friendRequests", userId) }
            )
        }
    }

    fun observeAllFriendships(viewerUid: String): Flow<List<FriendshipRecord>> {
        if (viewerUid.isBlank()) return flowOf(emptyList())
        return flow {
            emitAll(
                db.collection("friendships")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(FriendshipRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "friendships", viewerUid) }
            )
        }
    }

    suspend fun sendFriendRequest(targetUid: String): Result<Unit> {
        var docPath = "friendRequests"
        return try {
            val uid = requireUserId()
            require(targetUid.isNotBlank() && targetUid != uid) { "Invalid friend target." }
            val sorted = listOf(uid, targetUid).sorted()
            val friendId = "${sorted[0]}_${sorted[1]}"
            val friendRef = db.collection("friendships").document(friendId)
            if (friendRef.get().await().exists()) {
                return Result.success(Unit)
            }

            // If target user already sent us a pending friend request, accept it cleanly.
            val reverseRef = db.collection("friendRequests").document("${targetUid}_$uid")
            val reverseSnap = reverseRef.get().await()
            if (reverseSnap.exists() && reverseSnap.getString("status") == "PENDING") {
                val batch = db.batch()
                batch.update(
                    reverseRef,
                    mapOf(
                        "status" to "ACCEPTED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    friendRef,
                    mapOf(
                        "participantUids" to sorted,
                        "createdBy" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
                return Result.success(Unit)
            }

            val reqId = "${uid}_$targetUid"
            val reqRef = db.collection("friendRequests").document(reqId)
            docPath = reqRef.path
            val snap = reqRef.get().await()
            if (snap.exists()) {
                reqRef.update(
                    mapOf(
                        "status" to "PENDING",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                reqRef.set(
                    mapOf(
                        "fromUid" to uid,
                        "toUid" to targetUid,
                        "status" to "PENDING",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun cancelFriendRequest(targetUid: String): Result<Unit> {
        var docPath = "friendRequests"
        return try {
            val uid = requireUserId()
            val reqRef = db.collection("friendRequests").document("${uid}_$targetUid")
            docPath = reqRef.path
            if (reqRef.get().await().exists()) {
                reqRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequest(fromUid: String): Result<Unit> {
        var docPath = "friendRequests"
        return try {
            val uid = requireUserId()
            val sorted = listOf(uid, fromUid).sorted()
            val friendId = "${sorted[0]}_${sorted[1]}"
            val reqRef = db.collection("friendRequests").document("${fromUid}_$uid")
            val friendRef = db.collection("friendships").document(friendId)
            docPath = friendRef.path
            val reqExists = reqRef.get().await().exists()
            val friendExists = friendRef.get().await().exists()
            val batch = db.batch()
            if (reqExists) {
                batch.update(
                    reqRef,
                    mapOf(
                        "status" to "ACCEPTED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            if (!friendExists) {
                batch.set(
                    friendRef,
                    mapOf(
                        "participantUids" to sorted,
                        "createdBy" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            if (reqExists || !friendExists) {
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun rejectFriendRequest(fromUid: String): Result<Unit> {
        var docPath = "friendRequests"
        return try {
            val uid = requireUserId()
            val reqRef = db.collection("friendRequests").document("${fromUid}_$uid")
            docPath = reqRef.path
            if (reqRef.get().await().exists()) {
                reqRef.update(
                    mapOf(
                        "status" to "REJECTED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun removeFriend(targetUid: String): Result<Unit> {
        var docPath = "friendships"
        return try {
            val uid = requireUserId()
            val sorted = listOf(uid, targetUid).sorted()
            val friendRef = db.collection("friendships").document("${sorted[0]}_${sorted[1]}")
            docPath = friendRef.path
            val reqOut = db.collection("friendRequests").document("${uid}_$targetUid")
            val reqIn = db.collection("friendRequests").document("${targetUid}_$uid")
            val friendExists = friendRef.get().await().exists()
            val outExists = reqOut.get().await().exists()
            val inExists = reqIn.get().await().exists()
            val batch = db.batch()
            if (friendExists) batch.delete(friendRef)
            if (outExists) batch.delete(reqOut)
            if (inExists) batch.delete(reqIn)
            if (friendExists || outExists || inExists) {
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Profile Views, Profile Likes, Who Likes Me & Favorites ---

    fun observeIncomingProfileViews(userId: String): Flow<List<ProfileViewRecord>> {
        if (userId.isBlank()) return flowOf(emptyList())
        return flow {
            emitAll(
                db.collection("profileViews")
                    .whereEqualTo("targetUid", userId)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(ProfileViewRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.viewedAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "profileViews", userId) }
            )
        }
    }

    suspend fun recordProfileView(
        targetUid: String,
        viewerName: String,
        viewerPhoto: String?
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            if (targetUid.isBlank() || targetUid == uid) return Result.success(Unit)
            val ref = db.collection("profileViews").document("${uid}_$targetUid")
            val payload = mutableMapOf<String, Any>(
                "viewerUid" to uid,
                "targetUid" to targetUid,
                "viewerName" to viewerName.trim().ifBlank { "Kindred Member" }.take(40),
                "viewedAt" to FieldValue.serverTimestamp()
            )
            if (!viewerPhoto.isNullOrBlank() && viewerPhoto.length <= 180_000) {
                payload["viewerPhoto"] = viewerPhoto
            }
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.success(Unit)
        }
    }

    fun observeIncomingSwipes(userId: String): Flow<List<SwipeRecord>> {
        if (userId.isBlank()) return flowOf(emptyList())
        return flow {
            emitAll(
                db.collection("swipes")
                    .whereEqualTo("toUserId", userId)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(SwipeRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "swipes", userId) }
            )
        }
    }

    fun observeProfileLikes(userId: String): Flow<List<ProfileLikeRecord>> {
        if (userId.isBlank()) return flowOf(emptyList())
        return flow {
            val outgoing = db.collection("profileLikes")
                .whereEqualTo("fromUid", userId)
                .snapshots()
                .map { it.toObjects(ProfileLikeRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            val incoming = db.collection("profileLikes")
                .whereEqualTo("toUid", userId)
                .snapshots()
                .map { it.toObjects(ProfileLikeRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            emitAll(
                kotlinx.coroutines.flow.combine(outgoing, incoming) { out, inc ->
                    (out + inc).distinctBy { it.id }
                }.catch { handleStreamError(it, OperationType.LIST, "profileLikes", userId) }
            )
        }
    }

    suspend fun toggleProfileLike(targetUid: String): Result<Boolean> {
        var docPath = "profileLikes"
        return try {
            val uid = requireUserId()
            require(targetUid.isNotBlank() && targetUid != uid) { "Invalid profile target." }
            val ref = db.collection("profileLikes").document("${uid}_$targetUid")
            docPath = ref.path
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.delete().await()
                Result.success(false)
            } else {
                ref.set(
                    mapOf(
                        "fromUid" to uid,
                        "toUid" to targetUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeFavorites(userId: String): Flow<List<FavoriteRecord>> {
        if (userId.isBlank()) return flowOf(emptyList())
        return flow {
            emitAll(
                db.collection("users").document(userId).collection("favorites")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(FavoriteRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "users/$userId/favorites", userId) }
            )
        }
    }

    suspend fun toggleFavorite(profile: PublicProfile): Result<Boolean> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("favorites").document(profile.userId)
            docPath = ref.path
            if (ref.get().await().exists()) {
                ref.delete().await()
                Result.success(false)
            } else {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "targetUid" to profile.userId,
                    "displayName" to profile.displayName.take(40),
                    "age" to profile.age,
                    "cityLabel" to profile.cityLabel.take(80),
                    "country" to profile.country.take(80),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                val photo = profile.allProfilePhotos().firstOrNull()
                if (!photo.isNullOrBlank() && photo.length <= 180_000) {
                    payload["photoDataUrl"] = photo
                }
                ref.set(payload).await()
                Result.success(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    // --- Profile Showcase Videos ---

    fun observeUserVideos(targetUserId: String, viewerUid: String): Flow<List<ProfileVideoItem>> {
        if (targetUserId.isBlank() || viewerUid.isBlank()) return flowOf(emptyList())
        val path = "publicProfiles/$targetUserId/videos"
        val liveFlow = flow {
            emitAll(
                db.collection("publicProfiles").document(targetUserId).collection("videos")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(ProfileVideoItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, path, viewerUid) }
            )
        }
        return combine(liveFlow, deletedProfileVideoIdsFlow) { videos, deletedIds ->
            if (deletedIds.isEmpty()) videos else videos.filter { it.id !in deletedIds }
        }
    }

    suspend fun uploadProfileVideo(
        caption: String,
        prepared: PreparedVideoUpload,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        var docPath = "publicProfiles"
        return try {
            val uid = requireUserId()
            val videoRef = db.collection("publicProfiles").document(uid).collection("videos").document()
            docPath = videoRef.path
            withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS) {
                val resolvedVideo = resolveVideoForFirestoreOrCloud(
                    prepared = prepared,
                    remotePath = CloudMediaStorageService.buildUserPath(uid, "profile_videos", "${videoRef.id}.mp4"),
                    onProgress = { onProgress(it * 0.88f) }
                )
                if (resolvedVideo.chunks.isNotEmpty()) {
                    uploadChunksToSubcollection(
                        parentCollection = profileVideoChunksRef(uid, videoRef.id),
                        ownerUid = uid,
                        chunks = resolvedVideo.chunks
                    )
                }

                val thumbRes = resolveThumbnailForFirestoreOrCloud(
                    rawThumbnail = prepared.thumbnailDataUrl,
                    remotePath = CloudMediaStorageService.buildUserPath(uid, "profile_videos", "${videoRef.id}_thumb.jpg")
                )
                onProgress(0.96f)

                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "caption" to caption.trim().take(300),
                    "mediaMimeType" to prepared.mimeType.take(80),
                    "mediaDurationSec" to prepared.durationSec.coerceAtLeast(1L),
                    "mediaByteSize" to prepared.sizeBytes.coerceAtLeast(0L),
                    "chunkCount" to resolvedVideo.chunks.size.toLong(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (!resolvedVideo.inlineMediaUrl.isNullOrBlank()) {
                    payload["mediaDataUrl"] = resolvedVideo.inlineMediaUrl
                }
                resolvedVideo.storagePath?.let { payload["mediaStoragePath"] = it }
                if (thumbRes != null && thumbRes.url.isNotBlank() && thumbRes.url.length <= 180_000) {
                    payload["thumbnailDataUrl"] = thumbRes.url
                    thumbRes.storagePath?.let { payload["thumbnailStoragePath"] = it }
                }
                videoRef.set(payload).await()
                onProgress(1f)
                Result.success(videoRef.id)
            }
        } catch (e: TimeoutCancellationException) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "uploadProfileVideo timed out for $docPath", e)
            Result.failure(IOException(msg, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "uploadProfileVideo failed for $docPath: $msg", e)
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun deleteProfileVideo(ownerUid: String, videoId: String): Result<Unit> {
        val cleanOwnerUid = ownerUid.trim()
        val cleanVideoId = videoId.trim()
        if (cleanOwnerUid.isBlank() || cleanVideoId.isBlank()) {
            return Result.failure(IllegalArgumentException("Video ID is missing."))
        }
        var docPath = "publicProfiles/$cleanOwnerUid/videos/$cleanVideoId"
        deletedProfileVideoIdsFlow.update { it + cleanVideoId }
        return try {
            requireUserId()
            val ref = db.collection("publicProfiles").document(cleanOwnerUid).collection("videos").document(cleanVideoId)
            docPath = ref.path
            val storagePaths = runCatching {
                withTimeoutOrNull(1_500L) { ref.get(Source.DEFAULT).await().extractOwnedStoragePaths() }
            }.getOrNull().orEmpty()
            ref.delete().await()
            deleteSubcollectionDocsBestEffort(profileVideoChunksRef(cleanOwnerUid, cleanVideoId))
            deleteStoragePathsBestEffort(storagePaths)
            Result.success(Unit)
        } catch (e: CancellationException) {
            deletedProfileVideoIdsFlow.update { it - cleanVideoId }
            throw e
        } catch (e: Exception) {
            deletedProfileVideoIdsFlow.update { it - cleanVideoId }
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- 24-Hour Stories ---

    fun observeActiveStories(viewerUid: String): Flow<List<StoryItem>> {
        if (viewerUid.isBlank()) return flowOf(emptyList())
        val liveFlow = flow {
            emitAll(
                db.collection("stories")
                    .snapshots()
                    .map { snap ->
                        val now = System.currentTimeMillis()
                        snap.documents.mapNotNull { it.toStoryItemSafe() }
                            .filter { !it.isExpiredAt(now) }
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "stories", viewerUid) }
            )
        }
        return combine(
            liveFlow,
            refreshedStoriesFlow.onStart { emit(emptyList()) },
            deletedStoryIdsFlow
        ) { live, refreshed, deletedIds ->
            val now = System.currentTimeMillis()
            val base = if (refreshed.isNotEmpty()) {
                val newestRefreshedSeconds = refreshed.maxOfOrNull { it.createdAt?.seconds ?: 0L } ?: 0L
                val refreshedMap = refreshed.associateBy { it.id }.toMutableMap()
                for (item in live) {
                    if (item.id.isNotBlank() && !refreshedMap.containsKey(item.id)) {
                        val itemSeconds = item.createdAt?.seconds ?: 0L
                        if (itemSeconds >= newestRefreshedSeconds) {
                            refreshedMap[item.id] = item
                        }
                    }
                }
                refreshedMap.values.filter { !it.isExpiredAt(now) }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            } else {
                live
            }
            if (deletedIds.isEmpty()) base else base.filter { it.id !in deletedIds }
        }
    }

    suspend fun fetchLatestActiveStories(viewerUid: String): Result<List<StoryItem>> {
        if (viewerUid.isBlank()) return Result.success(emptyList())
        return try {
            val snap = try {
                db.collection("stories").get(Source.SERVER).await()
            } catch (_: Exception) {
                db.collection("stories").get(Source.DEFAULT).await()
            }
            val now = System.currentTimeMillis()
            val deleted = deletedStoryIdsFlow.value
            val allStories = snap.documents.mapNotNull { it.toStoryItemSafe() }
            val expiredOwnedIds = allStories
                .filter { it.isExpiredAt(now) && it.userId == viewerUid && it.id.isNotBlank() && it.id !in deleted }
                .map { it.id }
            if (expiredOwnedIds.isNotEmpty()) {
                deletedStoryIdsFlow.update { it + expiredOwnedIds }
                allStories
                    .filter { it.id in expiredOwnedIds }
                    .forEach { expiredStory ->
                        runCatching {
                            deleteExpiredStoryDocInternal(expiredStory)
                        }
                    }
            }
            val list = allStories
                .filter { !it.isExpiredAt(now) && it.id !in deleted && it.id !in expiredOwnedIds }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            refreshedStoriesFlow.tryEmit(list)
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "stories")
            Result.failure(e)
        }
    }

    suspend fun postPhotoStory(
        authorProfile: PublicProfile?,
        fallbackName: String?,
        photoDataUrl: String,
        caption: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        var docPath = "stories"
        return try {
            val uid = requireUserId()
            val storyRef = db.collection("stories").document()
            docPath = storyRef.path
            withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS) {
                val resolvedPhoto = resolveImageForFirestoreOrCloud(
                    rawImage = photoDataUrl,
                    remotePath = CloudMediaStorageService.buildUserPath(uid, "stories", "${storyRef.id}.jpg"),
                    maxDataUrlChars = CloudMediaStorageService.MAX_INLINE_FIRESTORE_MEDIA_CHARS,
                    onProgress = { onProgress(it * 0.92f) }
                )

                val expiresMs = System.currentTimeMillis() + 86_400_000L
                val expiresTimestamp = Timestamp(java.util.Date(expiresMs))
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "authorName" to (authorProfile?.displayName ?: fallbackName ?: "Kindred Member").take(40),
                    "mediaType" to "PHOTO",
                    "mediaDataUrl" to resolvedPhoto.url,
                    "caption" to caption.trim().take(300),
                    "mediaDurationSec" to 0L,
                    "chunkCount" to 0L,
                    "viewerUids" to listOf(uid),
                    "expiresAt" to expiresTimestamp,
                    "expiresAtMs" to expiresMs,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                resolvedPhoto.storagePath?.let { payload["mediaStoragePath"] = it }
                val authorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull()
                if (!authorPhoto.isNullOrBlank() && authorPhoto.length <= 180_000) {
                    payload["authorPhoto"] = authorPhoto
                }
                if (!authorProfile?.country.isNullOrBlank()) {
                    payload["authorCountry"] = authorProfile!!.country.take(80)
                }
                storyRef.set(payload).await()
                onProgress(1f)
                Result.success(storyRef.id)
            }
        } catch (e: TimeoutCancellationException) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "postPhotoStory timed out for $docPath", e)
            Result.failure(IOException(msg, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "postPhotoStory failed for $docPath: $msg", e)
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun postVideoStory(
        authorProfile: PublicProfile?,
        fallbackName: String?,
        prepared: PreparedVideoUpload,
        caption: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        var docPath = "stories"
        return try {
            val uid = requireUserId()
            val storyRef = db.collection("stories").document()
            docPath = storyRef.path
            withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS) {
                val resolvedVideo = resolveVideoForFirestoreOrCloud(
                    prepared = prepared,
                    remotePath = CloudMediaStorageService.buildUserPath(uid, "stories", "${storyRef.id}.mp4"),
                    onProgress = { onProgress(it * 0.85f) }
                )

                val thumbRes = resolveThumbnailForFirestoreOrCloud(
                    rawThumbnail = prepared.thumbnailDataUrl,
                    remotePath = CloudMediaStorageService.buildUserPath(uid, "stories", "${storyRef.id}_thumb.jpg")
                )
                onProgress(0.94f)

                if (resolvedVideo.chunks.isNotEmpty()) {
                    uploadChunksToSubcollection(
                        parentCollection = storyChunksRef(storyRef.id),
                        ownerUid = uid,
                        chunks = resolvedVideo.chunks
                    )
                }

                val expiresMs = System.currentTimeMillis() + 86_400_000L
                val expiresTimestamp = Timestamp(java.util.Date(expiresMs))
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "authorName" to (authorProfile?.displayName ?: fallbackName ?: "Kindred Member").take(40),
                    "mediaType" to "VIDEO",
                    "caption" to caption.trim().take(300),
                    "mediaDurationSec" to prepared.durationSec.coerceAtLeast(1L),
                    "chunkCount" to resolvedVideo.chunks.size.toLong(),
                    "viewerUids" to listOf(uid),
                    "expiresAt" to expiresTimestamp,
                    "expiresAtMs" to expiresMs,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                resolvedVideo.inlineMediaUrl?.let { payload["mediaDataUrl"] = it }
                resolvedVideo.storagePath?.let { payload["mediaStoragePath"] = it }
                val authorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull()
                if (!authorPhoto.isNullOrBlank() && authorPhoto.length <= 180_000) {
                    payload["authorPhoto"] = authorPhoto
                }
                if (!authorProfile?.country.isNullOrBlank()) {
                    payload["authorCountry"] = authorProfile!!.country.take(80)
                }
                if (thumbRes != null && thumbRes.url.isNotBlank() && thumbRes.url.length <= 180_000) {
                    payload["thumbnailDataUrl"] = thumbRes.url
                    thumbRes.storagePath?.let { payload["thumbnailStoragePath"] = it }
                }
                storyRef.set(payload).await()
                onProgress(1f)
                Result.success(storyRef.id)
            }
        } catch (e: TimeoutCancellationException) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "postVideoStory timed out for $docPath", e)
            Result.failure(IOException(msg, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "postVideoStory failed for $docPath: $msg", e)
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun markStoryViewed(storyId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            db.collection("stories").document(storyId)
                .update("viewerUids", FieldValue.arrayUnion(uid))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun deleteStory(storyId: String): Result<Unit> {
        val cleanStoryId = storyId.trim()
        if (cleanStoryId.isBlank()) {
            return Result.failure(IllegalArgumentException("Story ID is missing."))
        }
        var docPath = "stories/$cleanStoryId"
        deletedStoryIdsFlow.update { it + cleanStoryId }
        return try {
            requireUserId()
            val ref = db.collection("stories").document(cleanStoryId)
            docPath = ref.path
            val storagePaths = runCatching {
                withTimeoutOrNull(1_500L) { ref.get(Source.DEFAULT).await().extractOwnedStoragePaths() }
            }.getOrNull().orEmpty()
            ref.delete().await()
            deleteSubcollectionDocsBestEffort(storyChunksRef(cleanStoryId))
            deleteStoragePathsBestEffort(storagePaths)
            Result.success(Unit)
        } catch (e: CancellationException) {
            deletedStoryIdsFlow.update { it - cleanStoryId }
            throw e
        } catch (e: Exception) {
            deletedStoryIdsFlow.update { it - cleanStoryId }
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun deleteExpiredStoryDocInternal(story: StoryItem) {
        val cleanStoryId = story.id.trim()
        if (cleanStoryId.isBlank()) return
        val ref = db.collection("stories").document(cleanStoryId)
        val storagePaths = buildList {
            story.mediaStoragePath?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            story.thumbnailStoragePath?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.distinct()
        ref.delete().await()
        deleteSubcollectionDocsBestEffort(storyChunksRef(cleanStoryId))
        deleteStoragePathsBestEffort(storagePaths)
    }

    /**
     * Automatically deletes stories from Firestore (and their mediaChunks + Cloud Storage assets)
     * once they have exceeded the 24-hour TTL (StoryItem.STORY_TTL_MS).
     * Non-admin users clean up their own expired stories; admins sweep all users' expired stories.
     */
    suspend fun purgeExpiredStories(
        viewerUid: String,
        includeAllUsers: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): Result<Int> {
        if (viewerUid.isBlank()) return Result.success(0)
        return try {
            val query = if (includeAllUsers) {
                db.collection("stories")
            } else {
                db.collection("stories").whereEqualTo("userId", viewerUid)
            }
            val snap = try {
                query.get(Source.SERVER).await()
            } catch (_: Exception) {
                query.get(Source.DEFAULT).await()
            }
            val expiredStories = snap.documents
                .mapNotNull { it.toStoryItemSafe() }
                .filter { story ->
                    story.id.isNotBlank() &&
                        story.isExpiredAt(nowMs) &&
                        (includeAllUsers || story.userId == viewerUid)
                }
            if (expiredStories.isEmpty()) {
                return Result.success(0)
            }
            deletedStoryIdsFlow.update { it + expiredStories.map { s -> s.id } }
            var removedCount = 0
            for (story in expiredStories) {
                try {
                    deleteExpiredStoryDocInternal(story)
                    removedCount += 1
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("SocialMediaRepo", "Failed to auto-purge expired story ${story.id}", e)
                }
            }
            Result.success(removedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "stories")
            Result.failure(e)
        }
    }

    // --- Story Highlights & Story Archive ---

    fun observeUserStoriesArchive(userId: String): Flow<List<StoryItem>> {
        if (userId.isBlank()) return flowOf(emptyList())
        val liveFlow = flow {
            emitAll(
                db.collection("stories")
                    .whereEqualTo("userId", userId)
                    .snapshots()
                    .map { snap ->
                        snap.documents.mapNotNull { it.toStoryItemSafe() }
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "stories", userId) }
            )
        }
        return combine(liveFlow, deletedStoryIdsFlow) { stories, deletedIds ->
            if (deletedIds.isEmpty()) stories else stories.filter { it.id !in deletedIds }
        }
    }

    fun observeUserHighlights(userId: String): Flow<List<StoryHighlight>> {
        if (userId.isBlank()) return flowOf(emptyList())
        return flow {
            emitAll(
                db.collection("highlights")
                    .whereEqualTo("userId", userId)
                    .snapshots()
                    .map { snap ->
                        snap.documents.mapNotNull { it.toStoryHighlightSafe() }
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "highlights", userId) }
            )
        }
    }

    suspend fun createHighlight(
        title: String,
        coverUrl: String,
        storyItems: List<StoryItem>,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        var docPath = "highlights"
        if (storyItems.isEmpty()) {
            return Result.failure(IllegalArgumentException("Select at least 1 story for this highlight"))
        }
        return try {
            val uid = requireUserId()
            val ref = db.collection("highlights").document()
            docPath = ref.path

            withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS) {
                val safeTitle = title.trim().ifBlank { "Highlight" }.take(40)
                val compactedItems = mutableListOf<Map<String, Any>>()
                var remainingPayloadBudget = 580_000

                val resolvedCoverMeta = resolveThumbnailForFirestoreOrCloud(
                    rawThumbnail = coverUrl,
                    remotePath = CloudMediaStorageService.buildUserPath(uid, "highlights", "${ref.id}_cover.jpg")
                )
                var resolvedCover = resolvedCoverMeta?.url.orEmpty()
                var resolvedCoverPath = resolvedCoverMeta?.storagePath
                onProgress(0.25f)

                val limitedItems = storyItems.take(25)
                for ((idx, s) in limitedItems.withIndex()) {
                    val perItemMaxBytes = when {
                        remainingPayloadBudget > 220_000 -> 65_000
                        remainingPayloadBudget > 90_000 -> 35_000
                        else -> 20_000
                    }
                    val perItemMaxDim = if (perItemMaxBytes >= 60_000) 720 else 480

                    var safeMediaUrl = ""
                    var safeMediaPath = s.mediaStoragePath?.trim().orEmpty()
                    var safeThumbUrl = ""
                    var safeThumbPath = s.thumbnailStoragePath?.trim().orEmpty()
                    if (s.mediaType == "VIDEO") {
                        val thumbMeta = resolveThumbnailForFirestoreOrCloud(
                            rawThumbnail = s.thumbnailDataUrl,
                            remotePath = CloudMediaStorageService.buildUserPath(uid, "highlights", "${ref.id}_item_${idx}_thumb.jpg")
                        )
                        safeThumbUrl = thumbMeta?.url.orEmpty()
                        if (thumbMeta?.storagePath != null) safeThumbPath = thumbMeta.storagePath
                        val rawVideo = s.mediaDataUrl?.trim().orEmpty()
                        if (CloudMediaStorageService.isRemoteHttpUrl(rawVideo)) {
                            safeMediaUrl = rawVideo.take(2048)
                        } else if (rawVideo.isNotBlank() && rawVideo.length <= 120_000 && rawVideo.length <= remainingPayloadBudget) {
                            safeMediaUrl = rawVideo
                        }
                    } else {
                        val rawPhoto = (s.mediaDataUrl ?: s.thumbnailDataUrl)?.trim().orEmpty()
                        safeMediaUrl = if (CloudMediaStorageService.isRemoteHttpUrl(rawPhoto)) {
                            rawPhoto.take(2048)
                        } else {
                            withContext(Dispatchers.Default) {
                                MediaImageHelper.compactImageDataUrl(
                                    rawUrl = rawPhoto,
                                    maxDim = perItemMaxDim,
                                    maxRawBytes = perItemMaxBytes
                                ).orEmpty()
                            }
                        }
                    }

                    if (resolvedCover.isBlank()) {
                        resolvedCover = safeThumbUrl.ifBlank {
                            if (CloudMediaStorageService.isRemoteHttpUrl(safeMediaUrl)) {
                                safeMediaUrl.take(2048)
                            } else if (safeMediaUrl.startsWith("data:image")) {
                                withContext(Dispatchers.Default) {
                                    MediaImageHelper.compactImageDataUrl(safeMediaUrl, maxDim = 420, maxRawBytes = 35_000).orEmpty()
                                }
                            } else ""
                        }
                        if (resolvedCoverPath == null && safeThumbPath.startsWith("users/")) {
                            resolvedCoverPath = safeThumbPath
                        }
                    }

                    val approxSize = safeMediaUrl.length + safeThumbUrl.length + 256
                    if (remainingPayloadBudget - approxSize >= 0 || compactedItems.isEmpty()) {
                        remainingPayloadBudget = (remainingPayloadBudget - approxSize).coerceAtLeast(0)
                        val safeItemId = s.id.trim().ifBlank { "story_${idx}_${System.currentTimeMillis()}" }
                        val itemMap = mutableMapOf<String, Any>(
                            "id" to safeItemId,
                            "userId" to s.userId.ifBlank { uid },
                            "authorName" to s.authorName.trim().ifBlank { "Kindred Member" }.take(40),
                            "mediaType" to (if (s.mediaType == "VIDEO") "VIDEO" else "PHOTO"),
                            "mediaDataUrl" to safeMediaUrl,
                            "thumbnailDataUrl" to safeThumbUrl,
                            "caption" to (s.caption ?: "").trim().take(300),
                            "mediaDurationSec" to s.mediaDurationSec.coerceAtLeast(0L),
                            "expiresAtMs" to s.expiresAtMs.coerceAtLeast(0L)
                        )
                        if (safeMediaPath.startsWith("users/")) itemMap["mediaStoragePath"] = safeMediaPath
                        if (safeThumbPath.startsWith("users/")) itemMap["thumbnailStoragePath"] = safeThumbPath
                        compactedItems += itemMap
                    }
                    onProgress(0.25f + (0.65f * ((idx + 1f) / limitedItems.size.coerceAtLeast(1))))
                }

                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "title" to safeTitle,
                    "storyItems" to compactedItems,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                val safeCover = resolvedCover.take(180_000)
                if (safeCover.isNotBlank()) {
                    payload["coverUrl"] = safeCover
                }
                resolvedCoverPath?.takeIf { it.startsWith("users/") }?.let {
                    payload["coverStoragePath"] = it
                }
                ref.set(payload).await()
                onProgress(1f)
                Result.success(ref.id)
            }
        } catch (e: TimeoutCancellationException) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e("KindredHighlights", "createHighlight timed out for $docPath", e)
            Result.failure(IOException(msg, e))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e("KindredHighlights", "createHighlight failed for $docPath", t)
            if (t is Exception) handleFirestoreError(t, OperationType.CREATE, docPath)
            Result.failure(IllegalStateException(t.localizedMessage ?: "Failed to create highlight.", t))
        }
    }

    suspend fun deleteHighlight(highlightId: String): Result<Unit> {
        var docPath = "highlights/$highlightId"
        return try {
            requireUserId()
            val ref = db.collection("highlights").document(highlightId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Video Feed Posts ---

    fun observeVideoFeed(viewerUid: String): Flow<List<VideoPost>> {
        if (viewerUid.isBlank()) return flowOf(emptyList())
        val liveFlow = flow {
            emitAll(
                db.collection("videoPosts")
                    .snapshots()
                    .map { snap ->
                        snap.documents.mapNotNull { it.toVideoPostSafe() }
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "videoPosts", viewerUid) }
            )
        }
        return combine(
            liveFlow,
            refreshedVideoFeedFlow.onStart { emit(emptyList()) },
            deletedVideoPostIdsFlow
        ) { live, refreshed, deletedIds ->
            val base = if (refreshed.isNotEmpty()) {
                val newestRefreshedSeconds = refreshed.maxOfOrNull { it.createdAt?.seconds ?: 0L } ?: 0L
                val refreshedMap = refreshed.associateBy { it.id }.toMutableMap()
                for (item in live) {
                    if (item.id.isNotBlank() && !refreshedMap.containsKey(item.id)) {
                        val itemSeconds = item.createdAt?.seconds ?: 0L
                        if (itemSeconds >= newestRefreshedSeconds) {
                            refreshedMap[item.id] = item
                        }
                    }
                }
                refreshedMap.values.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            } else {
                live
            }
            if (deletedIds.isEmpty()) base else base.filter { it.id !in deletedIds }
        }
    }

    suspend fun fetchLatestVideoFeed(viewerUid: String): Result<List<VideoPost>> {
        if (viewerUid.isBlank()) return Result.success(emptyList())
        return try {
            val snap = try {
                db.collection("videoPosts").get(Source.SERVER).await()
            } catch (_: Exception) {
                db.collection("videoPosts").get(Source.DEFAULT).await()
            }
            val deleted = deletedVideoPostIdsFlow.value
            val list = snap.documents.mapNotNull { it.toVideoPostSafe() }
                .filter { it.id !in deleted }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            refreshedVideoFeedFlow.tryEmit(list)
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "videoPosts")
            Result.failure(e)
        }
    }

    suspend fun publishVideoPost(
        authorProfile: PublicProfile?,
        fallbackName: String?,
        caption: String,
        prepared: PreparedVideoUpload,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        var docPath = "videoPosts"
        return try {
            val uid = requireUserId()
            val postRef = db.collection("videoPosts").document()
            docPath = postRef.path
            withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS) {
                val resolvedVideo = resolveVideoForFirestoreOrCloud(
                    prepared = prepared,
                    remotePath = CloudMediaStorageService.buildUserPath(uid, "reels", "${postRef.id}.mp4"),
                    onProgress = { onProgress(it * 0.88f) }
                )
                if (resolvedVideo.chunks.isNotEmpty()) {
                    uploadChunksToSubcollection(
                        parentCollection = videoPostChunksRef(postRef.id),
                        ownerUid = uid,
                        chunks = resolvedVideo.chunks
                    )
                }

                val thumbRes = resolveThumbnailForFirestoreOrCloud(
                    rawThumbnail = prepared.thumbnailDataUrl,
                    remotePath = CloudMediaStorageService.buildUserPath(uid, "reels", "${postRef.id}_thumb.jpg")
                )
                onProgress(0.95f)

                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "authorName" to (authorProfile?.displayName ?: fallbackName ?: "Kindred Member").take(40),
                    "caption" to caption.trim().take(300),
                    "mediaMimeType" to prepared.mimeType.take(80),
                    "mediaDurationSec" to prepared.durationSec.coerceAtLeast(1L),
                    "mediaByteSize" to prepared.sizeBytes.coerceAtLeast(0L),
                    "chunkCount" to resolvedVideo.chunks.size.toLong(),
                    "likedByUids" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (!resolvedVideo.inlineMediaUrl.isNullOrBlank()) {
                    payload["mediaDataUrl"] = resolvedVideo.inlineMediaUrl
                }
                resolvedVideo.storagePath?.let { payload["mediaStoragePath"] = it }
                val authorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull()
                if (!authorPhoto.isNullOrBlank() && authorPhoto.length <= 180_000) {
                    payload["authorPhoto"] = authorPhoto
                }
                if (!authorProfile?.country.isNullOrBlank()) {
                    payload["authorCountry"] = authorProfile!!.country.take(80)
                }
                if (thumbRes != null && thumbRes.url.isNotBlank() && thumbRes.url.length <= 180_000) {
                    payload["thumbnailDataUrl"] = thumbRes.url
                    thumbRes.storagePath?.let { payload["thumbnailStoragePath"] = it }
                }
                postRef.set(payload).await()
                onProgress(1f)
                Result.success(postRef.id)
            }
        } catch (e: TimeoutCancellationException) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "publishVideoPost timed out for $docPath", e)
            Result.failure(IOException(msg, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "publishVideoPost failed for $docPath: $msg", e)
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun toggleVideoPostLike(postId: String, currentlyLiked: Boolean): Result<Unit> {
        var docPath = "videoPosts/$postId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("videoPosts").document(postId)
            docPath = ref.path
            val op = if (currentlyLiked) FieldValue.arrayRemove(uid) else FieldValue.arrayUnion(uid)
            ref.update("likedByUids", op).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteVideoPost(postId: String): Result<Unit> {
        val cleanPostId = postId.trim()
        if (cleanPostId.isBlank()) {
            return Result.failure(IllegalArgumentException("Reel ID is missing."))
        }
        var docPath = "videoPosts/$cleanPostId"
        deletedVideoPostIdsFlow.update { it + cleanPostId }
        return try {
            requireUserId()
            val ref = db.collection("videoPosts").document(cleanPostId)
            docPath = ref.path
            val storagePaths = runCatching {
                withTimeoutOrNull(1_500L) { ref.get(Source.DEFAULT).await().extractOwnedStoragePaths() }
            }.getOrNull().orEmpty()
            // Remove comments before deleting parent doc so Firestore rules verifying parent owner succeed.
            deleteSubcollectionDocsBestEffort(ref.collection("comments"))
            ref.delete().await()
            deleteSubcollectionDocsBestEffort(videoPostChunksRef(cleanPostId))
            deleteStoragePathsBestEffort(storagePaths)
            Result.success(Unit)
        } catch (e: CancellationException) {
            deletedVideoPostIdsFlow.update { it - cleanPostId }
            throw e
        } catch (e: Exception) {
            deletedVideoPostIdsFlow.update { it - cleanPostId }
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Social Timeline Feed Posts & Comments ---

    fun observeFeedPosts(viewerUid: String): Flow<List<FeedPost>> {
        if (viewerUid.isBlank()) return flowOf(emptyList())
        val liveFlow = flow {
            emitAll(
                db.collection("feedPosts")
                    .snapshots()
                    .map { snap ->
                        snap.documents.mapNotNull { it.toFeedPostSafe() }
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "feedPosts", viewerUid) }
            )
        }
        return combine(
            liveFlow,
            refreshedFeedPostsFlow.onStart { emit(emptyList()) },
            deletedFeedPostIdsFlow
        ) { live, refreshed, deletedIds ->
            val base = if (refreshed.isNotEmpty()) {
                val newestRefreshedSeconds = refreshed.maxOfOrNull { it.createdAt?.seconds ?: 0L } ?: 0L
                val refreshedMap = refreshed.associateBy { it.id.ifBlank { it.postId } }.toMutableMap()
                for (item in live) {
                    val resolvedId = item.id.ifBlank { item.postId }
                    if (resolvedId.isNotBlank() && !refreshedMap.containsKey(resolvedId)) {
                        val itemSeconds = item.createdAt?.seconds ?: 0L
                        if (itemSeconds >= newestRefreshedSeconds) {
                            refreshedMap[resolvedId] = item
                        }
                    }
                }
                refreshedMap.values.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            } else {
                live
            }
            if (deletedIds.isEmpty()) base else base.filter {
                val resolvedId = it.id.ifBlank { it.postId }
                resolvedId !in deletedIds
            }
        }
    }

    suspend fun fetchLatestFeedPosts(viewerUid: String): Result<List<FeedPost>> {
        if (viewerUid.isBlank()) return Result.success(emptyList())
        return try {
            val snap = try {
                db.collection("feedPosts").get(Source.SERVER).await()
            } catch (_: Exception) {
                db.collection("feedPosts").get(Source.DEFAULT).await()
            }
            val deleted = deletedFeedPostIdsFlow.value
            val list = snap.documents.mapNotNull { it.toFeedPostSafe() }
                .filter {
                    val resolvedId = it.id.ifBlank { it.postId }
                    resolvedId !in deleted
                }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            refreshedFeedPostsFlow.tryEmit(list)
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "feedPosts")
            Result.failure(e)
        }
    }

    fun observePostComments(postId: String, viewerUid: String): Flow<List<FeedComment>> {
        if (postId.isBlank() || viewerUid.isBlank()) return flowOf(emptyList())
        val path = "feedPosts/$postId/comments"
        return flow {
            emitAll(
                db.collection("feedPosts")
                    .document(postId)
                    .collection("comments")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(FeedComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, path, viewerUid) }
            )
        }
    }

    fun observeVideoPostComments(postId: String, viewerUid: String): Flow<List<FeedComment>> {
        if (postId.isBlank() || viewerUid.isBlank()) return flowOf(emptyList())
        val path = "videoPosts/$postId/comments"
        return flow {
            emitAll(
                db.collection("videoPosts")
                    .document(postId)
                    .collection("comments")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(FeedComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, path, viewerUid) }
            )
        }
    }

    suspend fun addVideoPostComment(
        postId: String,
        authorProfile: PublicProfile?,
        fallbackName: String?,
        text: String,
        replyToComment: FeedComment? = null
    ): Result<String> {
        val cleanText = text.trim().take(500)
        if (cleanText.isBlank()) {
            return Result.failure(IllegalArgumentException("Comment cannot be empty."))
        }
        var docPath = "videoPosts/$postId/comments"
        return try {
            val uid = requireUserId()
            val commentRef = db.collection("videoPosts").document(postId).collection("comments").document()
            docPath = commentRef.path
            val payload = mutableMapOf<String, Any>(
                "postId" to postId,
                "userId" to uid,
                "authorName" to (authorProfile?.displayName ?: fallbackName ?: "Kindred Member").take(40),
                "text" to cleanText,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val authorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull()
            if (!authorPhoto.isNullOrBlank() && authorPhoto.length <= 180_000) {
                payload["authorPhoto"] = authorPhoto
            }
            val parentCommentId = replyToComment?.id?.trim().orEmpty()
            if (parentCommentId.isNotBlank()) {
                payload["replyToCommentId"] = parentCommentId
                payload["replyToAuthorName"] = replyToComment?.authorName?.trim()?.ifBlank { "Member" }?.take(40) ?: "Member"
                val snippet = replyToComment?.text?.trim()?.take(120).orEmpty()
                if (snippet.isNotBlank()) {
                    payload["replyToText"] = snippet
                }
            }
            commentRef.set(payload).await()
            Result.success(commentRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteVideoPostComment(postId: String, commentId: String): Result<Unit> {
        var docPath = "videoPosts/$postId/comments/$commentId"
        return try {
            requireUserId()
            val commentRef = db.collection("videoPosts").document(postId).collection("comments").document(commentId)
            docPath = commentRef.path
            commentRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun publishFeedPost(
        authorProfile: PublicProfile?,
        fallbackName: String?,
        caption: String,
        photoDataUrl: String? = null,
        photoUrls: List<String> = emptyList(),
        preparedVideo: PreparedVideoUpload? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        val cleanCaption = caption.trim().take(1000)
        val rawPhotos = buildList {
            val single = photoDataUrl?.trim().orEmpty()
            if (single.isNotBlank()) add(single)
            photoUrls.forEach { url ->
                val clean = url.trim()
                if (clean.isNotBlank()) add(clean)
            }
        }.distinct().take(6)

        if (cleanCaption.isBlank() && rawPhotos.isEmpty() && preparedVideo == null) {
            return Result.failure(IllegalArgumentException("Add text, photos, or a video to share a post."))
        }
        var docPath = "feedPosts"
        return try {
            val uid = requireUserId()
            val postRef = db.collection("feedPosts").document()
            val postId = postRef.id
            docPath = postRef.path

            val mediaType = when {
                preparedVideo != null -> "VIDEO"
                rawPhotos.isNotEmpty() -> "PHOTO"
                else -> "TEXT"
            }

            withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "authorName" to (authorProfile?.displayName ?: fallbackName ?: "Kindred Member").take(40),
                    "mediaType" to mediaType,
                    "caption" to cleanCaption,
                    "likedByUids" to emptyList<String>(),
                    "savedByUids" to emptyList<String>(),
                    "commentCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                val authorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull()
                if (!authorPhoto.isNullOrBlank() && authorPhoto.length <= 180_000) {
                    payload["authorPhoto"] = authorPhoto
                }
                if (!authorProfile?.country.isNullOrBlank()) {
                    payload["authorCountry"] = authorProfile!!.country.take(80)
                }
                if (!authorProfile?.cityLabel.isNullOrBlank()) {
                    payload["authorCity"] = authorProfile!!.cityLabel.take(80)
                }
                if (preparedVideo != null) {
                    val resolvedVideo = resolveVideoForFirestoreOrCloud(
                        prepared = preparedVideo,
                        remotePath = CloudMediaStorageService.buildUserPath(uid, "feed_posts", "$postId.mp4"),
                        onProgress = { onProgress(it * 0.88f) }
                    )
                    if (resolvedVideo.chunks.isNotEmpty()) {
                        uploadChunksToSubcollection(
                            parentCollection = feedPostChunksRef(postId),
                            ownerUid = uid,
                            chunks = resolvedVideo.chunks
                        )
                    }

                    val thumbRes = resolveThumbnailForFirestoreOrCloud(
                        rawThumbnail = preparedVideo.thumbnailDataUrl,
                        remotePath = CloudMediaStorageService.buildUserPath(uid, "feed_posts", "${postId}_thumb.jpg")
                    )
                    onProgress(0.95f)

                    if (!resolvedVideo.inlineMediaUrl.isNullOrBlank()) {
                        payload["mediaDataUrl"] = resolvedVideo.inlineMediaUrl
                    }
                    resolvedVideo.storagePath?.let { payload["mediaStoragePath"] = it }
                    payload["mediaMimeType"] = preparedVideo.mimeType.take(80)
                    payload["mediaDurationSec"] = preparedVideo.durationSec.coerceAtLeast(1L)
                    payload["mediaByteSize"] = preparedVideo.sizeBytes.coerceAtLeast(0L)
                    payload["chunkCount"] = resolvedVideo.chunks.size.toLong()
                    if (thumbRes != null && thumbRes.url.isNotBlank() && thumbRes.url.length <= 180_000) {
                        payload["thumbnailDataUrl"] = thumbRes.url
                        thumbRes.storagePath?.let { payload["thumbnailStoragePath"] = it }
                    }
                } else if (rawPhotos.isNotEmpty()) {
                    val perPhotoMaxRawBytes = if (rawPhotos.size == 1) {
                        580_000
                    } else {
                        (480_000 / rawPhotos.size).coerceIn(55_000, 118_000)
                    }
                    val perPhotoMaxChars = if (rawPhotos.size == 1) {
                        CloudMediaStorageService.MAX_INLINE_FIRESTORE_MEDIA_CHARS
                    } else {
                        CloudMediaStorageService.MAX_INLINE_FIRESTORE_PHOTO_LIST_CHARS
                    }
                    val totalPhotos = rawPhotos.size.coerceAtLeast(1).toFloat()
                    val perPhotoProgress = FloatArray(rawPhotos.size)
                    val uploadedItems = coroutineScope {
                        rawPhotos.mapIndexed { idx, rawPhoto ->
                            async(Dispatchers.IO) {
                                resolveImageForFirestoreOrCloud(
                                    rawImage = rawPhoto,
                                    remotePath = CloudMediaStorageService.buildUserPath(uid, "feed_posts", "${postId}_$idx.jpg"),
                                    maxDim = if (rawPhotos.size == 1) 1280 else 860,
                                    maxRawBytes = perPhotoMaxRawBytes,
                                    maxDataUrlChars = perPhotoMaxChars,
                                    onProgress = { fraction ->
                                        perPhotoProgress[idx] = fraction
                                        onProgress((perPhotoProgress.sum() / totalPhotos * 0.94f).coerceIn(0f, 0.94f))
                                    }
                                )
                            }
                        }.awaitAll()
                    }
                    val uploadedUrls = uploadedItems.map { it.url }
                    val uploadedPaths = uploadedItems.map { it.storagePath.orEmpty() }
                    if (uploadedUrls.size == 1) {
                        payload["mediaDataUrl"] = uploadedUrls.first()
                        payload["mediaUrls"] = emptyList<String>()
                        uploadedItems.first().storagePath?.let { payload["mediaStoragePath"] = it }
                    } else {
                        payload["mediaUrls"] = uploadedUrls
                        if (uploadedPaths.any { it.startsWith("users/") }) {
                            payload["mediaStoragePaths"] = uploadedPaths
                        }
                        uploadedItems.firstOrNull()?.storagePath?.let { payload["mediaStoragePath"] = it }
                    }
                    payload["chunkCount"] = 0L
                }
                postRef.set(payload).await()
                onProgress(1f)
                Result.success(postId)
            }
        } catch (e: TimeoutCancellationException) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "publishFeedPost timed out for $docPath", e)
            Result.failure(IOException(msg, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "publishFeedPost failed for $docPath: $msg", e)
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun toggleFeedPostLike(postId: String, currentlyLiked: Boolean): Result<Unit> {
        var docPath = "feedPosts/$postId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("feedPosts").document(postId)
            docPath = ref.path
            val op = if (currentlyLiked) FieldValue.arrayRemove(uid) else FieldValue.arrayUnion(uid)
            ref.update("likedByUids", op).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleFeedPostSave(postId: String, currentlySaved: Boolean): Result<Unit> {
        var docPath = "feedPosts/$postId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("feedPosts").document(postId)
            docPath = ref.path
            val op = if (currentlySaved) FieldValue.arrayRemove(uid) else FieldValue.arrayUnion(uid)
            ref.update("savedByUids", op).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addFeedPostComment(
        postId: String,
        authorProfile: PublicProfile?,
        fallbackName: String?,
        text: String,
        replyToComment: FeedComment? = null
    ): Result<String> {
        val cleanText = text.trim().take(500)
        if (cleanText.isBlank()) {
            return Result.failure(IllegalArgumentException("Comment cannot be empty."))
        }
        var docPath = "feedPosts/$postId/comments"
        return try {
            val uid = requireUserId()
            val postRef = db.collection("feedPosts").document(postId)
            val commentRef = postRef.collection("comments").document()
            val commentId = commentRef.id

            docPath = commentRef.path
            val payload = mutableMapOf<String, Any>(
                "postId" to postId,
                "userId" to uid,
                "authorName" to (authorProfile?.displayName ?: fallbackName ?: "Kindred Member").take(40),
                "text" to cleanText,
                "likedByUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            val authorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull()
            if (!authorPhoto.isNullOrBlank() && authorPhoto.length <= 180_000) {
                payload["authorPhoto"] = authorPhoto
            }
            val parentCommentId = replyToComment?.id?.trim().orEmpty()
            if (parentCommentId.isNotBlank()) {
                payload["replyToCommentId"] = parentCommentId
                payload["replyToAuthorName"] = replyToComment?.authorName?.trim()?.ifBlank { "Member" }?.take(40) ?: "Member"
                val snippet = replyToComment?.text?.trim()?.take(120).orEmpty()
                if (snippet.isNotBlank()) {
                    payload["replyToText"] = snippet
                }
            }
            val batch = db.batch()
            batch.set(commentRef, payload)
            batch.update(postRef, "commentCount", FieldValue.increment(1L))
            batch.commit().await()
            Result.success(commentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteFeedPostComment(postId: String, commentId: String): Result<Unit> {
        var docPath = "feedPosts/$postId/comments/$commentId"
        return try {
            requireUserId()
            val postRef = db.collection("feedPosts").document(postId)
            val commentRef = postRef.collection("comments").document(commentId)
            docPath = commentRef.path
            val postSnap = runCatching { postRef.get().await() }.getOrNull()
            val currentCount = postSnap?.getLong("commentCount") ?: 0L
            val batch = db.batch()
            batch.delete(commentRef)
            if (currentCount > 0L) {
                batch.update(postRef, "commentCount", FieldValue.increment(-1L))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleCommentLike(postId: String, commentId: String, currentlyLiked: Boolean): Result<Unit> {
        var docPath = "feedPosts/$postId/comments/$commentId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("feedPosts").document(postId).collection("comments").document(commentId)
            docPath = ref.path
            val op = if (currentlyLiked) FieldValue.arrayRemove(uid) else FieldValue.arrayUnion(uid)
            ref.update("likedByUids", op).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteFeedPost(postId: String): Result<Unit> {
        val cleanPostId = postId.trim()
        if (cleanPostId.isBlank()) {
            return Result.failure(IllegalArgumentException("Post ID is missing."))
        }
        var docPath = "feedPosts/$cleanPostId"
        deletedFeedPostIdsFlow.update { it + cleanPostId }
        return try {
            requireUserId()
            val ref = db.collection("feedPosts").document(cleanPostId)
            docPath = ref.path
            val storagePaths = runCatching {
                withTimeoutOrNull(1_500L) { ref.get(Source.DEFAULT).await().extractOwnedStoragePaths() }
            }.getOrNull().orEmpty()
            // Remove comments before deleting parent doc so Firestore rules verifying parent owner succeed.
            deleteSubcollectionDocsBestEffort(ref.collection("comments"))
            ref.delete().await()
            deleteSubcollectionDocsBestEffort(feedPostChunksRef(cleanPostId))
            deleteStoragePathsBestEffort(storagePaths)
            Result.success(Unit)
        } catch (e: CancellationException) {
            deletedFeedPostIdsFlow.update { it - cleanPostId }
            throw e
        } catch (e: Exception) {
            deletedFeedPostIdsFlow.update { it - cleanPostId }
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Admin Moderation ---

    suspend fun checkIsAdmin(email: String?): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        if (email != null && email.equals(KindredConstants.ADMIN_EMAIL, ignoreCase = true)) {
            return true
        }
        return runCatching {
            db.collection("admins").document(uid).get().await().exists()
        }.getOrDefault(false)
    }

    fun observeAdminReports(viewerUid: String, isAdmin: Boolean): Flow<List<ReportRecord>> {
        if (!isAdmin || viewerUid.isBlank()) return flowOf(emptyList())
        return flow {
            emitAll(
                db.collection("reports")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(ReportRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "reports", viewerUid) }
            )
        }
    }

    fun observeAllProfilesForAdmin(viewerUid: String, isAdmin: Boolean): Flow<List<PublicProfile>> {
        if (!isAdmin || viewerUid.isBlank()) return flowOf(emptyList())
        return flow {
            emitAll(
                db.collection("publicProfiles")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "publicProfiles", viewerUid) }
            )
        }
    }

    suspend fun updateReportStatus(reportId: String, status: String): Result<Unit> {
        var docPath = "reports/$reportId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("reports").document(reportId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "status" to status,
                    "resolvedBy" to uid,
                    "resolvedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setUserSuspendedByAdmin(targetUserId: String, suspended: Boolean): Result<Unit> {
        var docPath = "publicProfiles/$targetUserId"
        return try {
            requireUserId()
            val ref = db.collection("publicProfiles").document(targetUserId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "suspended" to suspended,
                    "discoverable" to !suspended,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observeModerationNotices(userId: String): Flow<List<ModerationNoticeRecord>> {
        if (userId.isBlank()) return flowOf(emptyList())
        val path = "users/$userId/moderationNotices"
        return flow {
            emitAll(
                db.collection("users")
                    .document(userId)
                    .collection("moderationNotices")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(
                            ModerationNoticeRecord::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, path, userId) }
            )
        }
    }

    suspend fun markModerationNoticeRead(userId: String, noticeId: String): Result<Unit> {
        val cleanUid = userId.trim()
        val cleanNoticeId = noticeId.trim()
        if (cleanUid.isBlank() || cleanNoticeId.isBlank()) {
            return Result.success(Unit)
        }
        var docPath = "users/$cleanUid/moderationNotices/$cleanNoticeId"
        return try {
            val uid = requireUserId()
            if (uid != cleanUid) {
                return Result.failure(SecurityException("Cannot mark another user's notice read."))
            }
            val ref = db.collection("users").document(cleanUid).collection("moderationNotices").document(cleanNoticeId)
            docPath = ref.path
            ref.update("read", true).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markAllModerationNoticesRead(userId: String, noticeIds: List<String>): Result<Unit> {
        val cleanUid = userId.trim()
        val ids = noticeIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanUid.isBlank() || ids.isEmpty()) return Result.success(Unit)
        var docPath = "users/$cleanUid/moderationNotices"
        return try {
            val uid = requireUserId()
            if (uid != cleanUid) {
                return Result.failure(SecurityException("Cannot update another user's notices."))
            }
            ids.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { noticeId ->
                    val ref = db.collection("users").document(cleanUid).collection("moderationNotices").document(noticeId)
                    batch.update(ref, "read", true)
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun sendModerationNotice(
        targetUid: String,
        moderatorUid: String,
        contentType: String,
        contentId: String,
        contentPreview: String,
        reason: String,
        details: String
    ) {
        if (targetUid.isBlank() || contentId.isBlank()) return
        val cleanReason = reason.trim().ifBlank { "Community guideline violation" }.let {
            if (it.length < 2) "Guideline violation" else it.take(120)
        }
        val cleanDetails = details.trim().take(500)
        val cleanPreview = contentPreview.trim().take(200)

        val noticeRef = db.collection("users")
            .document(targetUid)
            .collection("moderationNotices")
            .document()

        val payload = mutableMapOf<String, Any>(
            "targetUid" to targetUid,
            "moderatorUid" to moderatorUid,
            "contentType" to contentType,
            "contentId" to contentId,
            "reason" to cleanReason,
            "read" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (cleanPreview.isNotBlank()) {
            payload["contentPreview"] = cleanPreview
        }
        if (cleanDetails.isNotBlank()) {
            payload["details"] = cleanDetails
        }
        noticeRef.set(payload).await()
    }

    suspend fun moderatorDeleteFeedPost(
        post: FeedPost,
        reason: String,
        details: String = "",
        relatedReportId: String? = null
    ): Result<Unit> {
        val cleanPostId = post.id.ifBlank { post.postId }.trim()
        if (cleanPostId.isBlank()) {
            return Result.failure(IllegalArgumentException("Post ID is missing."))
        }
        val cleanReason = reason.trim()
        if (cleanReason.length < 2) {
            return Result.failure(IllegalArgumentException("Please provide a guideline violation reason."))
        }
        var docPath = "feedPosts/$cleanPostId"
        deletedFeedPostIdsFlow.update { it + cleanPostId }
        return try {
            val modUid = requireUserId()
            if (!checkIsAdmin(auth.currentUser?.email)) {
                deletedFeedPostIdsFlow.update { it - cleanPostId }
                return Result.failure(SecurityException("Moderator privileges are required."))
            }
            val ref = db.collection("feedPosts").document(cleanPostId)
            docPath = ref.path
            val snap = runCatching { withTimeoutOrNull(2_000L) { ref.get(Source.DEFAULT).await() } }.getOrNull()
            val targetUid = post.userId.ifBlank { post.authorId }.ifBlank { snap?.getString("userId").orEmpty() }.trim()
            val preview = post.caption.ifBlank { snap?.getString("caption").orEmpty() }.trim()

            deleteSubcollectionDocsBestEffort(ref.collection("comments"))
            ref.delete().await()
            deleteSubcollectionDocsBestEffort(feedPostChunksRef(cleanPostId))

            if (targetUid.isNotBlank()) {
                docPath = "users/$targetUid/moderationNotices"
                sendModerationNotice(
                    targetUid = targetUid,
                    moderatorUid = modUid,
                    contentType = "POST",
                    contentId = cleanPostId,
                    contentPreview = preview,
                    reason = cleanReason,
                    details = details
                )
            }
            if (!relatedReportId.isNullOrBlank()) {
                runCatching { updateReportStatus(relatedReportId, "RESOLVED") }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            deletedFeedPostIdsFlow.update { it - cleanPostId }
            throw e
        } catch (e: Exception) {
            deletedFeedPostIdsFlow.update { it - cleanPostId }
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun moderatorDeleteVideoPost(
        reel: VideoPost,
        reason: String,
        details: String = "",
        relatedReportId: String? = null
    ): Result<Unit> {
        val cleanPostId = reel.id.trim()
        if (cleanPostId.isBlank()) {
            return Result.failure(IllegalArgumentException("Reel ID is missing."))
        }
        val cleanReason = reason.trim()
        if (cleanReason.length < 2) {
            return Result.failure(IllegalArgumentException("Please provide a guideline violation reason."))
        }
        var docPath = "videoPosts/$cleanPostId"
        deletedVideoPostIdsFlow.update { it + cleanPostId }
        return try {
            val modUid = requireUserId()
            if (!checkIsAdmin(auth.currentUser?.email)) {
                deletedVideoPostIdsFlow.update { it - cleanPostId }
                return Result.failure(SecurityException("Moderator privileges are required."))
            }
            val ref = db.collection("videoPosts").document(cleanPostId)
            docPath = ref.path
            val snap = runCatching { withTimeoutOrNull(2_000L) { ref.get(Source.DEFAULT).await() } }.getOrNull()
            val targetUid = reel.userId.ifBlank { snap?.getString("userId").orEmpty() }.trim()
            val preview = reel.caption.ifBlank { snap?.getString("caption").orEmpty() }.trim()

            deleteSubcollectionDocsBestEffort(ref.collection("comments"))
            ref.delete().await()
            deleteSubcollectionDocsBestEffort(videoPostChunksRef(cleanPostId))

            if (targetUid.isNotBlank()) {
                docPath = "users/$targetUid/moderationNotices"
                sendModerationNotice(
                    targetUid = targetUid,
                    moderatorUid = modUid,
                    contentType = "REEL",
                    contentId = cleanPostId,
                    contentPreview = preview,
                    reason = cleanReason,
                    details = details
                )
            }
            if (!relatedReportId.isNullOrBlank()) {
                runCatching { updateReportStatus(relatedReportId, "RESOLVED") }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            deletedVideoPostIdsFlow.update { it - cleanPostId }
            throw e
        } catch (e: Exception) {
            deletedVideoPostIdsFlow.update { it - cleanPostId }
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun moderatorDeleteStory(
        story: StoryItem,
        reason: String,
        details: String = ""
    ): Result<Unit> {
        val cleanStoryId = story.id.trim()
        if (cleanStoryId.isBlank()) {
            return Result.failure(IllegalArgumentException("Story ID is missing."))
        }
        val cleanReason = reason.trim()
        if (cleanReason.length < 2) {
            return Result.failure(IllegalArgumentException("Please provide a guideline violation reason."))
        }
        var docPath = "stories/$cleanStoryId"
        deletedStoryIdsFlow.update { it + cleanStoryId }
        return try {
            val modUid = requireUserId()
            if (!checkIsAdmin(auth.currentUser?.email)) {
                deletedStoryIdsFlow.update { it - cleanStoryId }
                return Result.failure(SecurityException("Moderator privileges are required."))
            }
            val ref = db.collection("stories").document(cleanStoryId)
            docPath = ref.path
            val snap = runCatching { withTimeoutOrNull(2_000L) { ref.get(Source.DEFAULT).await() } }.getOrNull()
            val targetUid = story.userId.ifBlank { snap?.getString("userId").orEmpty() }.trim()
            val preview = story.caption.orEmpty().ifBlank { snap?.getString("caption").orEmpty() }.trim()

            ref.delete().await()
            deleteSubcollectionDocsBestEffort(storyChunksRef(cleanStoryId))

            if (targetUid.isNotBlank()) {
                docPath = "users/$targetUid/moderationNotices"
                sendModerationNotice(
                    targetUid = targetUid,
                    moderatorUid = modUid,
                    contentType = "STORY",
                    contentId = cleanStoryId,
                    contentPreview = preview,
                    reason = cleanReason,
                    details = details
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            deletedStoryIdsFlow.update { it - cleanStoryId }
            throw e
        } catch (e: Exception) {
            deletedStoryIdsFlow.update { it - cleanStoryId }
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Delete Account & Personal Data ---

    suspend fun deleteAccountAndData(): Result<Unit> {
        var docPath = "users"
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(IllegalStateException("No signed-in account found."))
            val uid = currentUser.uid

            // Delete subcollections & public docs owned by user
            runCatching {
                val favs = db.collection("users").document(uid).collection("favorites").get().await()
                val tokens = db.collection("users").document(uid).collection("fcmTokens").get().await()
                val vids = db.collection("publicProfiles").document(uid).collection("videos").get().await()
                val batch = db.batch()
                favs.documents.forEach { batch.delete(it.reference) }
                tokens.documents.forEach { batch.delete(it.reference) }
                vids.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }

            runCatching {
                val stories = db.collection("stories").whereEqualTo("userId", uid).get().await()
                val videoPosts = db.collection("videoPosts").whereEqualTo("userId", uid).get().await()
                val feedPosts = db.collection("feedPosts").whereEqualTo("userId", uid).get().await()
                val followingDocs = db.collection("follows").whereEqualTo("followerUid", uid).get().await()
                val followerDocs = db.collection("follows").whereEqualTo("followingUid", uid).get().await()
                val batch = db.batch()
                stories.documents.forEach { batch.delete(it.reference) }
                videoPosts.documents.forEach { batch.delete(it.reference) }
                feedPosts.documents.forEach { batch.delete(it.reference) }
                followingDocs.documents.forEach { batch.delete(it.reference) }
                followerDocs.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }

            // Clean up GeoCells before removing publicProfile
            runCatching {
                val pubSnap = db.collection("publicProfiles").document(uid).get().await()
                if (pubSnap.exists()) {
                    val g3 = pubSnap.getString("geohash3").orEmpty()
                    val g4 = pubSnap.getString("geohash4").orEmpty()
                    if (g3.length == 3) {
                        db.collection("geoCells").document(g3).collection("profiles").document(uid).delete().await()
                    }
                    if (g4.length == 4) {
                        db.collection("geoCells").document(g4).collection("profiles").document(uid).delete().await()
                    }
                }
            }

            val rootBatch = db.batch()
            docPath = "users/$uid"
            rootBatch.delete(db.collection("users").document(uid))
            rootBatch.delete(db.collection("publicProfiles").document(uid))
            rootBatch.delete(db.collection("presence").document(uid))
            rootBatch.commit().await()

            currentUser.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // Convenience reference accessors for chunk resolution in UI
    fun messageMediaChunksRef(matchId: String, messageId: String): CollectionReference =
        db.collection("matches").document(matchId).collection("messages").document(messageId).collection("mediaChunks")

    fun profileVideoChunksRef(userId: String, videoId: String): CollectionReference =
        db.collection("publicProfiles").document(userId).collection("videos").document(videoId).collection("mediaChunks")

    fun storyChunksRef(storyId: String): CollectionReference =
        db.collection("stories").document(storyId).collection("mediaChunks")

    fun videoPostChunksRef(postId: String): CollectionReference =
        db.collection("videoPosts").document(postId).collection("mediaChunks")

    fun feedPostChunksRef(postId: String): CollectionReference =
        db.collection("feedPosts").document(postId).collection("mediaChunks")
}
