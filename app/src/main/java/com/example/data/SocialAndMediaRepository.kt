package com.example.data

import android.content.Context
import com.example.model.FavoriteRecord
import com.example.model.FeedComment
import com.example.model.FeedPost
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
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Modular repository handling Friendships, Profile Views, Likes, Favorites,
 * Profile Videos, 24h Stories, Video Feed, Chunked High-Res Video Sync,
 * All Presence Listing, Admin Moderation, and Full Account Deletion.
 */
class SocialAndMediaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
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
    ) {
        if (chunks.isEmpty()) return
        // Firestore limits RPC write batch payload to ~10 MiB; write <= 8 chunks per batch (~5.3 MiB)
        var index = 0
        while (index < chunks.size) {
            val end = minOf(index + 8, chunks.size)
            val batch = db.batch()
            for (seq in index until end) {
                val chunkRef = parentCollection.document(seq.toString())
                val map = mutableMapOf<String, Any>(
                    "ownerUid" to ownerUid,
                    "seq" to seq.toLong(),
                    "dataBase64" to chunks[seq],
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (participantUids != null && participantUids.size == 2) {
                    map["participantUids"] = participantUids.sorted()
                }
                batch.set(chunkRef, map)
            }
            batch.commit().await()
            index = end
        }
    }

    suspend fun resolveVideoToFile(
        context: Context,
        cacheKey: String,
        inlineDataUrl: String?,
        chunkCount: Long,
        chunksCollection: CollectionReference
    ): Result<File> = runCatching {
        if (!inlineDataUrl.isNullOrBlank()) {
            return@runCatching VideoClipHelper.materializeVideoFile(context, cacheKey, inlineDataUrl)
                ?: throw IllegalStateException("Could not decode inline video data.")
        }
        val existingCache = File(
            File(context.cacheDir, "chat_videos"),
            "video_${cacheKey.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "clip" }}.mp4"
        )
        if (existingCache.exists() && existingCache.length() > 0L) {
            return@runCatching existingCache
        }
        if (chunkCount <= 0L) {
            throw IllegalStateException("Video has no media chunks.")
        }
        val snap = chunksCollection.get().await()
        val ordered = snap.toObjects(MediaChunkRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.seq }
            .map { it.dataBase64 }
            .filter { it.isNotBlank() }
        VideoClipHelper.reassembleChunksToFile(context, cacheKey, ordered)
            ?: throw IllegalStateException("Failed to assemble video chunks from Firestore.")
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
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "caption" to caption.trim().take(300),
                "mediaMimeType" to prepared.mimeType.take(80),
                "mediaDurationSec" to prepared.durationSec.coerceAtLeast(0L),
                "mediaByteSize" to prepared.sizeBytes.coerceAtLeast(0L),
                "chunkCount" to prepared.chunkCount.toLong(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (!prepared.inlineDataUrl.isNullOrBlank()) {
                payload["mediaDataUrl"] = prepared.inlineDataUrl
            }
            if (!prepared.thumbnailDataUrl.isNullOrBlank() && prepared.thumbnailDataUrl.length <= 180_000) {
                payload["thumbnailDataUrl"] = prepared.thumbnailDataUrl
            }
            if (prepared.chunkBase64List.isNotEmpty()) {
                uploadChunksToSubcollection(
                    parentCollection = videoRef.collection("mediaChunks"),
                    ownerUid = uid,
                    chunks = prepared.chunkBase64List
                )
            }
            videoRef.set(payload).await()
            Result.success(videoRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
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
        return flow {
            emitAll(
                db.collection("stories")
                    .snapshots()
                    .map { snap ->
                        val now = System.currentTimeMillis()
                        snap.toObjects(StoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .filter { !it.isExpiredAt(now) }
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "stories", viewerUid) }
            )
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
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "authorName" to (authorProfile?.displayName ?: fallbackName ?: "Kindred Member").take(40),
                "mediaType" to "PHOTO",
                "mediaDataUrl" to photoDataUrl,
                "caption" to caption.trim().take(300),
                "mediaDurationSec" to 0L,
                "chunkCount" to 0L,
                "viewerUids" to listOf(uid),
                "expiresAtMs" to (System.currentTimeMillis() + 86_400_000L),
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
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
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "authorName" to (authorProfile?.displayName ?: fallbackName ?: "Kindred Member").take(40),
                "mediaType" to "VIDEO",
                "caption" to caption.trim().take(300),
                "mediaDurationSec" to prepared.durationSec.coerceAtLeast(1L),
                "chunkCount" to prepared.chunkCount.toLong(),
                "viewerUids" to listOf(uid),
                "expiresAtMs" to (System.currentTimeMillis() + 86_400_000L),
                "createdAt" to FieldValue.serverTimestamp()
            )
            val authorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull()
            if (!authorPhoto.isNullOrBlank() && authorPhoto.length <= 180_000) {
                payload["authorPhoto"] = authorPhoto
            }
            if (!authorProfile?.country.isNullOrBlank()) {
                payload["authorCountry"] = authorProfile!!.country.take(80)
            }
            if (!prepared.inlineDataUrl.isNullOrBlank()) {
                payload["mediaDataUrl"] = prepared.inlineDataUrl
            }
            if (!prepared.thumbnailDataUrl.isNullOrBlank() && prepared.thumbnailDataUrl.length <= 180_000) {
                payload["thumbnailDataUrl"] = prepared.thumbnailDataUrl
            }
            if (prepared.chunkBase64List.isNotEmpty()) {
                uploadChunksToSubcollection(
                    parentCollection = storyRef.collection("mediaChunks"),
                    ownerUid = uid,
                    chunks = prepared.chunkBase64List
                )
            }
            storyRef.set(payload).await()
            Result.success(storyRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
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
                        snap.toObjects(StoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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
                        snap.toObjects(StoryHighlight::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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
        return try {
            val uid = requireUserId()
            val ref = db.collection("highlights").document()
            docPath = ref.path
            val itemsData = storyItems.take(50).map { s ->
                mapOf(
                    "id" to s.id,
                    "userId" to s.userId,
                    "authorName" to s.authorName,
                    "mediaType" to s.mediaType,
                    "mediaDataUrl" to (s.mediaDataUrl ?: ""),
                    "thumbnailDataUrl" to (s.thumbnailDataUrl ?: ""),
                    "caption" to (s.caption ?: ""),
                    "mediaDurationSec" to s.mediaDurationSec,
                    "expiresAtMs" to s.expiresAtMs
                )
            }
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "title" to title.trim().ifBlank { "Highlight" }.take(40),
                "coverUrl" to coverUrl.trim().take(180_000),
                "storyItems" to itemsData,
                "createdAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
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
        return flow {
            emitAll(
                db.collection("videoPosts")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(VideoPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "videoPosts", viewerUid) }
            )
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
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "authorName" to (authorProfile?.displayName ?: fallbackName ?: "Kindred Member").take(40),
                "caption" to caption.trim().take(300),
                "mediaMimeType" to prepared.mimeType.take(80),
                "mediaDurationSec" to prepared.durationSec.coerceAtLeast(1L),
                "mediaByteSize" to prepared.sizeBytes.coerceAtLeast(0L),
                "chunkCount" to prepared.chunkCount.toLong(),
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
            if (!prepared.inlineDataUrl.isNullOrBlank()) {
                payload["mediaDataUrl"] = prepared.inlineDataUrl
            }
            if (!prepared.thumbnailDataUrl.isNullOrBlank() && prepared.thumbnailDataUrl.length <= 180_000) {
                payload["thumbnailDataUrl"] = prepared.thumbnailDataUrl
            }
            if (prepared.chunkBase64List.isNotEmpty()) {
                uploadChunksToSubcollection(
                    parentCollection = postRef.collection("mediaChunks"),
                    ownerUid = uid,
                    chunks = prepared.chunkBase64List
                )
            }
            postRef.set(payload).await()
            Result.success(postRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
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
        return flow {
            emitAll(
                db.collection("feedPosts")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(FeedPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, "feedPosts", viewerUid) }
            )
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

    suspend fun publishFeedPost(
        authorProfile: PublicProfile?,
        fallbackName: String?,
        caption: String,
        photoDataUrl: String? = null,
        photoUrls: List<String> = emptyList(),
        preparedVideo: PreparedVideoUpload? = null
    ): Result<String> {
        val cleanCaption = caption.trim().take(1000)
        val validPhotos = buildList {
            photoUrls.forEach { url ->
                val clean = url.trim()
                if (clean.isNotBlank() && clean.length <= 180_000) add(clean)
            }
            val single = photoDataUrl?.trim().orEmpty()
            if (single.isNotBlank() && single.length <= 920_000) add(single)
        }.distinct().take(6)

        if (cleanCaption.isBlank() && validPhotos.isEmpty() && preparedVideo == null) {
            return Result.failure(IllegalArgumentException("Add text, photos, or a video to share a post."))
        }
        var docPath = "feedPosts"
        return try {
            val uid = requireUserId()
            val postRef = db.collection("feedPosts").document()
            docPath = postRef.path
            val mediaType = when {
                preparedVideo != null -> "VIDEO"
                validPhotos.isNotEmpty() -> "PHOTO"
                else -> "TEXT"
            }
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
                payload["mediaMimeType"] = preparedVideo.mimeType.take(80)
                payload["mediaDurationSec"] = preparedVideo.durationSec.coerceAtLeast(1L)
                payload["mediaByteSize"] = preparedVideo.sizeBytes.coerceAtLeast(0L)
                payload["chunkCount"] = preparedVideo.chunkCount.toLong()
                if (!preparedVideo.inlineDataUrl.isNullOrBlank()) {
                    payload["mediaDataUrl"] = preparedVideo.inlineDataUrl
                }
                if (!preparedVideo.thumbnailDataUrl.isNullOrBlank() && preparedVideo.thumbnailDataUrl.length <= 180_000) {
                    payload["thumbnailDataUrl"] = preparedVideo.thumbnailDataUrl
                }
                if (preparedVideo.chunkBase64List.isNotEmpty()) {
                    uploadChunksToSubcollection(
                        parentCollection = postRef.collection("mediaChunks"),
                        ownerUid = uid,
                        chunks = preparedVideo.chunkBase64List
                    )
                }
            } else if (validPhotos.isNotEmpty()) {
                payload["mediaDataUrl"] = validPhotos.first()
                payload["mediaUrls"] = validPhotos.filter { it.length <= 180_000 }
                payload["chunkCount"] = 0L
            }
            postRef.set(payload).await()
            Result.success(postRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
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
            val batch = db.batch()
            batch.set(commentRef, payload)
            batch.update(postRef, "commentCount", FieldValue.increment(1L))
            batch.commit().await()
            Result.success(commentRef.id)
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
            val batch = db.batch()
            batch.delete(commentRef)
            batch.update(postRef, "commentCount", FieldValue.increment(-1L))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
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
                val swipes = db.collection("swipes").whereEqualTo("fromUserId", uid).get().await()
                val batch = db.batch()
                stories.documents.forEach { batch.delete(it.reference) }
                videoPosts.documents.forEach { batch.delete(it.reference) }
                feedPosts.documents.forEach { batch.delete(it.reference) }
                swipes.documents.forEach { batch.delete(it.reference) }
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
