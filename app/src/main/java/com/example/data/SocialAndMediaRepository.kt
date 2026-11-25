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
import com.example.util.PreparedVideoUpload
import com.example.util.VerificationAnalyzer
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    private val refreshedFeedPostsFlow = MutableSharedFlow<List<FeedPost>>(extraBufferCapacity = 4)
    private val refreshedStoriesFlow = MutableSharedFlow<List<StoryItem>>(extraBufferCapacity = 4)
    private val refreshedVideoFeedFlow = MutableSharedFlow<List<VideoPost>>(extraBufferCapacity = 4)

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
                ?.let { item -> if (item.id.isBlank()) item.copy(id = id) else item }
        }.onFailure { err ->
            Log.e("KindredStories", "Skipping malformed story document $id", err)
        }.getOrNull()
    }

    private fun DocumentSnapshot.toStoryHighlightSafe(): StoryHighlight? {
        return runCatching {
            toObject(StoryHighlight::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.let { hl -> if (hl.id.isBlank()) hl.copy(id = id) else hl }
        }.onFailure { err ->
            Log.e("KindredHighlights", "Skipping malformed highlight document $id", err)
        }.getOrNull()
    }

    private fun DocumentSnapshot.toFeedPostSafe(): FeedPost? {
        return runCatching {
            val direct = toObject(FeedPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (direct != null) {
                val resolvedId = if (direct.id.isNotBlank()) direct.id else id
                val resolvedUserId = if (direct.userId.isNotBlank()) direct.userId else (data?.get("authorId") as? String).orEmpty()
                direct.copy(id = resolvedId, userId = resolvedUserId)
            } else null
        }.recoverCatching {
            val d = data ?: return null
            val resolvedId = id
            val userId = (d["userId"] as? String) ?: (d["authorId"] as? String) ?: ""
            val authorName = (d["authorName"] as? String) ?: "Kindred Member"
            val authorPhoto = d["authorPhoto"] as? String
            val authorCountry = d["authorCountry"] as? String
            val authorCity = d["authorCity"] as? String
            val authorVerified = d["authorVerified"] as? Boolean ?: false
            val caption = (d["caption"] as? String) ?: ""
            val mediaType = (d["mediaType"] as? String) ?: "NONE"
            val mediaDataUrl = d["mediaDataUrl"] as? String
            val mediaUrl = d["mediaUrl"] as? String
            val rawMediaUrls = d["mediaUrls"] as? List<*>
            val mediaUrls = rawMediaUrls?.mapNotNull { it as? String } ?: emptyList()
            val thumbnailDataUrl = d["thumbnailDataUrl"] as? String
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
                authorVerified = authorVerified,
                caption = caption,
                mediaType = mediaType,
                mediaDataUrl = mediaDataUrl,
                mediaUrl = mediaUrl,
                mediaUrls = mediaUrls,
                thumbnailDataUrl = thumbnailDataUrl,
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
                val resolvedId = if (direct.id.isNotBlank()) direct.id else id
                val resolvedUserId = if (direct.userId.isNotBlank()) direct.userId else (data?.get("authorId") as? String).orEmpty()
                direct.copy(id = resolvedId, userId = resolvedUserId)
            } else null
        }.recoverCatching {
            val d = data ?: return null
            val resolvedId = id
            val userId = (d["userId"] as? String) ?: (d["authorId"] as? String) ?: ""
            val authorName = (d["authorName"] as? String) ?: "Kindred Member"
            val authorPhoto = d["authorPhoto"] as? String
            val authorCountry = d["authorCountry"] as? String
            val authorVerified = d["authorVerified"] as? Boolean ?: false
            val caption = (d["caption"] as? String) ?: ""
            val mediaDataUrl = d["mediaDataUrl"] as? String
            val thumbnailDataUrl = d["thumbnailDataUrl"] as? String
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
                authorVerified = authorVerified,
                caption = caption,
                mediaDataUrl = mediaDataUrl,
                thumbnailDataUrl = thumbnailDataUrl,
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

    private suspend fun resolveThumbnailForFirestoreOrCloud(
        rawThumbnail: String?,
        remotePath: String
    ): String? {
        val clean = rawThumbnail?.trim().orEmpty()
        if (clean.isBlank()) return null
        if (CloudMediaStorageService.isRemoteHttpUrl(clean)) return clean
        val uploaded = cloudStorage.ensureRemoteMediaUrl(
            rawDataUrlOrRemoteUrl = clean,
            remotePath = remotePath,
            defaultContentType = "image/jpeg"
        )
        return uploaded.getOrElse {
            VerificationAnalyzer.compactImageDataUrl(clean, maxDim = 480, maxRawBytes = 40_000)
        }
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
        return flow {
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
    }

    suspend fun uploadProfileVideo(
        caption: String,
        prepared: PreparedVideoUpload
    ): Result<String> {
        var docPath = "publicProfiles"
        return try {
            val uid = requireUserId()
            val videoRef = db.collection("publicProfiles").document(uid).collection("videos").document()
            docPath = videoRef.path
            withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS) {
                val videoUrl = cloudStorage.uploadFile(
                    file = prepared.localCachedFile,
                    remotePath = "videos/$uid/${videoRef.id}.mp4",
                    contentType = prepared.mimeType
                ).getOrThrow()

                val thumbUrl = resolveThumbnailForFirestoreOrCloud(
                    rawThumbnail = prepared.thumbnailDataUrl,
                    remotePath = "videos/$uid/${videoRef.id}_thumb.jpg"
                )

                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "caption" to caption.trim().take(300),
                    "mediaDataUrl" to videoUrl,
                    "mediaMimeType" to prepared.mimeType.take(80),
                    "mediaDurationSec" to prepared.durationSec.coerceAtLeast(1L),
                    "mediaByteSize" to prepared.sizeBytes.coerceAtLeast(0L),
                    "chunkCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (!thumbUrl.isNullOrBlank() && thumbUrl.length <= 180_000) {
                    payload["thumbnailDataUrl"] = thumbUrl
                }
                videoRef.set(payload).await()
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
        var docPath = "publicProfiles/$ownerUid/videos/$videoId"
        return try {
            requireUserId()
            val ref = db.collection("publicProfiles").document(ownerUid).collection("videos").document(videoId)
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
        return merge(liveFlow, refreshedStoriesFlow)
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
            val list = snap.documents.mapNotNull { it.toStoryItemSafe() }
                .filter { !it.isExpiredAt(now) }
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
        caption: String
    ): Result<String> {
        var docPath = "stories"
        return try {
            val uid = requireUserId()
            val storyRef = db.collection("stories").document()
            docPath = storyRef.path
            withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS) {
                val photoUrl = cloudStorage.ensureRemoteMediaUrl(
                    rawDataUrlOrRemoteUrl = photoDataUrl,
                    remotePath = "stories/$uid/${storyRef.id}.jpg",
                    defaultContentType = "image/jpeg"
                ).getOrThrow()

                val expiresMs = System.currentTimeMillis() + 86_400_000L
                val expiresTimestamp = Timestamp(java.util.Date(expiresMs))
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "authorName" to (authorProfile?.displayName ?: fallbackName ?: "Kindred Member").take(40),
                    "authorVerified" to (authorProfile?.isProfileVerified == true),
                    "mediaType" to "PHOTO",
                    "mediaDataUrl" to photoUrl,
                    "caption" to caption.trim().take(300),
                    "mediaDurationSec" to 0L,
                    "chunkCount" to 0L,
                    "viewerUids" to listOf(uid),
                    "expiresAt" to expiresTimestamp,
                    "expiresAtMs" to expiresMs,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                val authorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull()
                if (!authorPhoto.isNullOrBlank() && authorPhoto.length <= 180_000) {
                    payload["authorPhoto"] = authorPhoto
                }
                if (!authorProfile?.country.isNullOrBlank()) {
                    payload["authorCountry"] = authorProfile!!.country.take(80)
                }
                storyRef.set(payload).await()
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
        caption: String
    ): Result<String> {
        var docPath = "stories"
        return try {
            val uid = requireUserId()
            val storyRef = db.collection("stories").document()
            docPath = storyRef.path
            withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS) {
                val videoUrl = cloudStorage.uploadFile(
                    file = prepared.localCachedFile,
                    remotePath = "stories/$uid/${storyRef.id}.mp4",
                    contentType = prepared.mimeType
                ).getOrThrow()

                val thumbUrl = resolveThumbnailForFirestoreOrCloud(
                    rawThumbnail = prepared.thumbnailDataUrl,
                    remotePath = "stories/$uid/${storyRef.id}_thumb.jpg"
                )

                val expiresMs = System.currentTimeMillis() + 86_400_000L
                val expiresTimestamp = Timestamp(java.util.Date(expiresMs))
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "authorName" to (authorProfile?.displayName ?: fallbackName ?: "Kindred Member").take(40),
                    "authorVerified" to (authorProfile?.isProfileVerified == true),
                    "mediaType" to "VIDEO",
                    "mediaDataUrl" to videoUrl,
                    "caption" to caption.trim().take(300),
                    "mediaDurationSec" to prepared.durationSec.coerceAtLeast(1L),
                    "chunkCount" to 0L,
                    "viewerUids" to listOf(uid),
                    "expiresAt" to expiresTimestamp,
                    "expiresAtMs" to expiresMs,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                val authorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull()
                if (!authorPhoto.isNullOrBlank() && authorPhoto.length <= 180_000) {
                    payload["authorPhoto"] = authorPhoto
                }
                if (!authorProfile?.country.isNullOrBlank()) {
                    payload["authorCountry"] = authorProfile!!.country.take(80)
                }
                if (!thumbUrl.isNullOrBlank() && thumbUrl.length <= 180_000) {
                    payload["thumbnailDataUrl"] = thumbUrl
                }
                storyRef.set(payload).await()
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
        var docPath = "stories/$storyId"
        return try {
            requireUserId()
            val ref = db.collection("stories").document(storyId)
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

    // --- Story Highlights & Story Archive ---

    fun observeUserStoriesArchive(userId: String): Flow<List<StoryItem>> {
        if (userId.isBlank()) return flowOf(emptyList())
        return flow {
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
        storyItems: List<StoryItem>
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

                var resolvedCover = resolveThumbnailForFirestoreOrCloud(
                    rawThumbnail = coverUrl,
                    remotePath = "highlights/$uid/${ref.id}/cover.jpg"
                ).orEmpty()

                val limitedItems = storyItems.take(25)
                for ((idx, s) in limitedItems.withIndex()) {
                    val perItemMaxBytes = when {
                        remainingPayloadBudget > 220_000 -> 65_000
                        remainingPayloadBudget > 90_000 -> 35_000
                        else -> 20_000
                    }
                    val perItemMaxDim = if (perItemMaxBytes >= 60_000) 720 else 480

                    var safeMediaUrl = ""
                    var safeThumbUrl = ""
                    if (s.mediaType == "VIDEO") {
                        safeThumbUrl = resolveThumbnailForFirestoreOrCloud(
                            rawThumbnail = s.thumbnailDataUrl,
                            remotePath = "highlights/$uid/${ref.id}/item_${idx}_thumb.jpg"
                        ).orEmpty()
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
                                VerificationAnalyzer.compactImageDataUrl(
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
                                    VerificationAnalyzer.compactImageDataUrl(safeMediaUrl, maxDim = 420, maxRawBytes = 35_000).orEmpty()
                                }
                            } else ""
                        }
                    }

                    val approxSize = safeMediaUrl.length + safeThumbUrl.length + 256
                    if (remainingPayloadBudget - approxSize >= 0 || compactedItems.isEmpty()) {
                        remainingPayloadBudget = (remainingPayloadBudget - approxSize).coerceAtLeast(0)
                        val safeItemId = s.id.trim().ifBlank { "story_${idx}_${System.currentTimeMillis()}" }
                        compactedItems += mapOf(
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
                    }
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
                ref.set(payload).await()
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
        return merge(liveFlow, refreshedVideoFeedFlow)
    }

    suspend fun fetchLatestVideoFeed(viewerUid: String): Result<List<VideoPost>> {
        if (viewerUid.isBlank()) return Result.success(emptyList())
        return try {
            val snap = try {
                db.collection("videoPosts").get(Source.SERVER).await()
            } catch (_: Exception) {
                db.collection("videoPosts").get(Source.DEFAULT).await()
            }
            val list = snap.documents.mapNotNull { it.toVideoPostSafe() }
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
        prepared: PreparedVideoUpload
    ): Result<String> {
        var docPath = "videoPosts"
        return try {
            val uid = requireUserId()
            val postRef = db.collection("videoPosts").document()
            docPath = postRef.path
            withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS) {
                val videoUrl = cloudStorage.uploadFile(
                    file = prepared.localCachedFile,
                    remotePath = "reels/$uid/${postRef.id}.mp4",
                    contentType = prepared.mimeType
                ).getOrThrow()

                val thumbUrl = resolveThumbnailForFirestoreOrCloud(
                    rawThumbnail = prepared.thumbnailDataUrl,
                    remotePath = "reels/$uid/${postRef.id}_thumb.jpg"
                )

                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "authorName" to (authorProfile?.displayName ?: fallbackName ?: "Kindred Member").take(40),
                    "authorVerified" to (authorProfile?.isProfileVerified == true),
                    "caption" to caption.trim().take(300),
                    "mediaDataUrl" to videoUrl,
                    "mediaMimeType" to prepared.mimeType.take(80),
                    "mediaDurationSec" to prepared.durationSec.coerceAtLeast(1L),
                    "mediaByteSize" to prepared.sizeBytes.coerceAtLeast(0L),
                    "chunkCount" to 0L,
                    "likedByUids" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                val authorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull()
                if (!authorPhoto.isNullOrBlank() && authorPhoto.length <= 180_000) {
                    payload["authorPhoto"] = authorPhoto
                }
                if (!authorProfile?.country.isNullOrBlank()) {
                    payload["authorCountry"] = authorProfile!!.country.take(80)
                }
                if (!thumbUrl.isNullOrBlank() && thumbUrl.length <= 180_000) {
                    payload["thumbnailDataUrl"] = thumbUrl
                }
                postRef.set(payload).await()
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
        var docPath = "videoPosts/$postId"
        return try {
            requireUserId()
            val ref = db.collection("videoPosts").document(postId)
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
        return merge(liveFlow, refreshedFeedPostsFlow)
    }

    suspend fun fetchLatestFeedPosts(viewerUid: String): Result<List<FeedPost>> {
        if (viewerUid.isBlank()) return Result.success(emptyList())
        return try {
            val snap = try {
                db.collection("feedPosts").get(Source.SERVER).await()
            } catch (_: Exception) {
                db.collection("feedPosts").get(Source.DEFAULT).await()
            }
            val list = snap.documents.mapNotNull { it.toFeedPostSafe() }
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
        text: String
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
                "authorVerified" to (authorProfile?.isProfileVerified == true),
                "text" to cleanText,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val authorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull()
            if (!authorPhoto.isNullOrBlank() && authorPhoto.length <= 180_000) {
                payload["authorPhoto"] = authorPhoto
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
        preparedVideo: PreparedVideoUpload? = null
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
                    "authorVerified" to (authorProfile?.isProfileVerified == true),
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
                    val videoUrl = cloudStorage.uploadFile(
                        file = preparedVideo.localCachedFile,
                        remotePath = "feed_posts/$uid/$postId.mp4",
                        contentType = preparedVideo.mimeType
                    ).getOrThrow()

                    val thumbUrl = resolveThumbnailForFirestoreOrCloud(
                        rawThumbnail = preparedVideo.thumbnailDataUrl,
                        remotePath = "feed_posts/$uid/${postId}_thumb.jpg"
                    )

                    payload["mediaDataUrl"] = videoUrl
                    payload["mediaMimeType"] = preparedVideo.mimeType.take(80)
                    payload["mediaDurationSec"] = preparedVideo.durationSec.coerceAtLeast(1L)
                    payload["mediaByteSize"] = preparedVideo.sizeBytes.coerceAtLeast(0L)
                    payload["chunkCount"] = 0L
                    if (!thumbUrl.isNullOrBlank() && thumbUrl.length <= 180_000) {
                        payload["thumbnailDataUrl"] = thumbUrl
                    }
                } else if (rawPhotos.isNotEmpty()) {
                    val uploadedUrls = coroutineScope {
                        rawPhotos.mapIndexed { idx, rawPhoto ->
                            async(Dispatchers.IO) {
                                cloudStorage.ensureRemoteMediaUrl(
                                    rawDataUrlOrRemoteUrl = rawPhoto,
                                    remotePath = "feed_posts/$uid/${postId}_$idx.jpg",
                                    defaultContentType = "image/jpeg"
                                ).getOrThrow()
                            }
                        }.awaitAll()
                    }
                    payload["mediaDataUrl"] = uploadedUrls.first()
                    payload["mediaUrls"] = if (uploadedUrls.size > 1) uploadedUrls else emptyList<String>()
                    payload["chunkCount"] = 0L
                }
                postRef.set(payload).await()
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
        text: String
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
                "authorVerified" to (authorProfile?.isProfileVerified == true),
                "text" to cleanText,
                "likedByUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            val authorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull()
            if (!authorPhoto.isNullOrBlank() && authorPhoto.length <= 180_000) {
                payload["authorPhoto"] = authorPhoto
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
        var docPath = "feedPosts/$postId"
        return try {
            requireUserId()
            val ref = db.collection("feedPosts").document(postId)
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
                val verifs = db.collection("users").document(uid).collection("verifications").get().await()
                val vids = db.collection("publicProfiles").document(uid).collection("videos").get().await()
                val batch = db.batch()
                favs.documents.forEach { batch.delete(it.reference) }
                tokens.documents.forEach { batch.delete(it.reference) }
                verifs.documents.forEach { batch.delete(it.reference) }
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
