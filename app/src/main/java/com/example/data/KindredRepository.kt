package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.BlockRecord
import com.example.model.CallAudioChunk
import com.example.model.CallSession
import com.example.model.CallVideoFrame
import com.example.model.ChatMessage
import com.example.model.KindredConstants
import com.example.model.LivenessAnalysisResult
import com.example.model.MatchRecord
import com.example.model.PrivateUser
import com.example.model.PublicProfile
import com.example.model.SwipeRecord
import com.example.model.TypingStatus
import com.example.model.UserPresence
import com.example.model.VerificationChallenge
import com.example.model.VerificationRecord
import com.example.model.VisibilityLevel
import com.example.notifications.KindredNotificationHelper
import com.example.service.GeoHashMatchingService
import com.example.util.GeoHashUtils
import com.example.util.LocationAndGeoHelper
import com.example.util.PreparedVideoUpload
import com.example.util.VideoClipHelper
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import kotlin.math.abs

class KindredRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val geoService: GeoHashMatchingService = GeoHashMatchingService(db, auth),
    private val socialRepo: SocialAndMediaRepository = SocialAndMediaRepository(db, auth),
    private val cloudStorage: CloudMediaStorageService = CloudMediaStorageService(authProvider = { auth })
) {
    val socialRepository: SocialAndMediaRepository
        get() = socialRepo

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private suspend fun uploadProfilePhotosToCloud(uid: String, photos: List<String>): List<String> {
        val cleanedInput = photos
            .map { it.trim() }
            .filter { it.isNotBlank() && (CloudMediaStorageService.isRemoteHttpUrl(it) || it.length <= 920_000) }
            .distinct()
            .take(KindredConstants.MAX_PROFILE_PHOTOS)

        return cleanedInput.mapIndexed { index, photo ->
            if (CloudMediaStorageService.isRemoteHttpUrl(photo)) {
                photo.take(2048)
            } else {
                cloudStorage.ensureRemoteMediaUrl(
                    rawDataUrlOrRemoteUrl = photo,
                    remotePath = "profile_photos/$uid/photo_${index}_${abs(photo.hashCode())}.jpg",
                    defaultContentType = "image/jpeg"
                ).getOrThrow()
            }
        }.filter { it.isNotBlank() && (CloudMediaStorageService.isRemoteHttpUrl(it) || it.length <= 180_000) }
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
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

    fun observePrivateUser(userId: String): Flow<PrivateUser?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(PrivateUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { handleStreamError(it, OperationType.GET, path, userId) }
        )
    }

    fun observeMyPublicProfile(userId: String): Flow<PublicProfile?> = flow {
        val path = "publicProfiles/$userId"
        emitAll(
            db.collection("publicProfiles").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { handleStreamError(it, OperationType.GET, path, userId) }
        )
    }

    fun observeDiscoverableProfiles(userId: String): Flow<List<PublicProfile>> = flow {
        val path = "publicProfiles"
        emitAll(
            db.collection("publicProfiles")
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { handleStreamError(it, OperationType.LIST, path, userId) }
        )
    }

    fun observeMyOutgoingSwipes(userId: String): Flow<List<SwipeRecord>> = flow {
        val path = "swipes"
        emitAll(
            db.collection("swipes")
                .whereEqualTo("fromUserId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SwipeRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { handleStreamError(it, OperationType.LIST, path, userId) }
        )
    }

    fun observeMyMatches(userId: String): Flow<List<MatchRecord>> = flow {
        val path = "matches"
        emitAll(
            db.collection("matches")
                .whereArrayContains("participantUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(MatchRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { handleStreamError(it, OperationType.LIST, path, userId) }
        )
    }

    fun observeMessages(matchId: String, userId: String): Flow<List<ChatMessage>> = flow {
        val path = "matches/$matchId/messages"
        emitAll(
            db.collection("matches").document(matchId).collection("messages")
                .whereArrayContains("participantUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(Long.MAX_VALUE, 0) }
                }
                .catch { handleStreamError(it, OperationType.LIST, path, userId) }
        )
    }

    fun observePartnerTyping(matchId: String, partnerUserId: String, viewerUserId: String): Flow<TypingStatus?> {
        if (matchId.isBlank() || partnerUserId.isBlank() || viewerUserId.isBlank()) {
            return flowOf(null)
        }
        val path = "matches/$matchId/typing/$partnerUserId"
        return flow {
            emitAll(
                db.collection("matches").document(matchId).collection("typing").document(partnerUserId)
                    .snapshots()
                    .map { snap ->
                        if (snap.exists()) {
                            snap.toObject(TypingStatus::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        } else null
                    }
                    .catch { handleStreamError(it, OperationType.GET, path, viewerUserId) }
            )
        }
    }

    fun observeMatchCalls(matchId: String, viewerUserId: String): Flow<List<CallSession>> {
        if (matchId.isBlank() || viewerUserId.isBlank()) {
            return flowOf(emptyList())
        }
        val path = "matches/$matchId/calls"
        return flow {
            emitAll(
                db.collection("matches").document(matchId).collection("calls")
                    .whereArrayContains("participantUids", viewerUserId)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, path, viewerUserId) }
            )
        }
    }

    fun observeCallAudioChunks(
        matchId: String,
        callId: String,
        viewerUserId: String
    ): Flow<List<CallAudioChunk>> {
        if (matchId.isBlank() || callId.isBlank() || viewerUserId.isBlank()) {
            return flowOf(emptyList())
        }
        val path = "matches/$matchId/calls/$callId/audioChunks"
        return flow {
            emitAll(
                db.collection("matches")
                    .document(matchId)
                    .collection("calls")
                    .document(callId)
                    .collection("audioChunks")
                    .whereArrayContains("participantUids", viewerUserId)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(CallAudioChunk::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedBy { it.seq }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, path, viewerUserId) }
            )
        }
    }

    fun observeCallVideoFrames(
        matchId: String,
        callId: String,
        viewerUserId: String
    ): Flow<List<CallVideoFrame>> {
        if (matchId.isBlank() || callId.isBlank() || viewerUserId.isBlank()) {
            return flowOf(emptyList())
        }
        val path = "matches/$matchId/calls/$callId/videoFrames"
        return flow {
            emitAll(
                db.collection("matches")
                    .document(matchId)
                    .collection("calls")
                    .document(callId)
                    .collection("videoFrames")
                    .whereArrayContains("participantUids", viewerUserId)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(CallVideoFrame::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.seq }
                    }
                    .catch { handleStreamError(it, OperationType.LIST, path, viewerUserId) }
            )
        }
    }

    fun observeVerifications(userId: String): Flow<List<VerificationRecord>> = flow {
        val path = "users/$userId/verifications"
        emitAll(
            db.collection("users").document(userId).collection("verifications")
                .snapshots()
                .map { snap ->
                    snap.toObjects(VerificationRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { handleStreamError(it, OperationType.LIST, path, userId) }
        )
    }

    fun observeUserPresence(targetUserId: String, viewerUserId: String): Flow<UserPresence?> {
        if (targetUserId.isBlank() || viewerUserId.isBlank()) {
            return flowOf(null)
        }
        val path = "presence/$targetUserId"
        return flow {
            emitAll(
                db.collection("presence").document(targetUserId)
                    .snapshots()
                    .map { snap ->
                        if (snap.exists()) {
                            snap.toObject(UserPresence::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        } else null
                    }
                    .catch { handleStreamError(it, OperationType.GET, path, viewerUserId) }
            )
        }
    }

    suspend fun updatePresence(online: Boolean, hideOnlineStatus: Boolean? = null): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val docRef = db.collection("presence").document(uid)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "online" to online,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (hideOnlineStatus != null) {
                    updates["hideOnlineStatus"] = hideOnlineStatus
                }
                docRef.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "online" to online,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (hideOnlineStatus != null) {
                    payload["hideOnlineStatus"] = hideOnlineStatus
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePrivacyAndHideOnline(
        hideOnlineStatus: Boolean,
        photosVisibility: String,
        videosVisibility: String,
        infoVisibility: String
    ): Result<Unit> {
        val allowedVisibility = setOf(
            VisibilityLevel.EVERYONE,
            VisibilityLevel.FOLLOWERS_ONLY,
            VisibilityLevel.MATCHES_AND_FRIENDS,
            VisibilityLevel.FRIENDS_ONLY
        )
        val safePhotosVis = if (photosVisibility in allowedVisibility) photosVisibility else VisibilityLevel.EVERYONE
        val safeVideosVis = if (videosVisibility in allowedVisibility) videosVisibility else VisibilityLevel.EVERYONE
        val safeInfoVis = if (infoVisibility in allowedVisibility) infoVisibility else VisibilityLevel.EVERYONE

        var docPath = "publicProfiles"
        return try {
            val uid = requireUserId()
            val privateRef = db.collection("users").document(uid)
            val publicRef = db.collection("publicProfiles").document(uid)
            val batch = db.batch()
            if (privateRef.get().await().exists()) {
                batch.update(
                    privateRef,
                    mapOf(
                        "hideOnlineStatus" to hideOnlineStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val pubSnap = publicRef.get().await()
            if (pubSnap.exists()) {
                docPath = publicRef.path
                val existingPublic = pubSnap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                val privUpdates = mapOf(
                    "hideOnlineStatus" to hideOnlineStatus,
                    "photosVisibility" to safePhotosVis,
                    "videosVisibility" to safeVideosVis,
                    "infoVisibility" to safeInfoVis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.update(publicRef, privUpdates)
                if (existingPublic != null) {
                    val c3 = existingPublic.geohash3
                    val c4 = existingPublic.geohash4
                    if (c3.length == 3) {
                        val ref3 = db.collection("geoCells").document(c3).collection("profiles").document(uid)
                        if (ref3.get().await().exists()) batch.update(ref3, privUpdates)
                    }
                    if (c4.length == 4) {
                        val ref4 = db.collection("geoCells").document(c4).collection("profiles").document(uid)
                        if (ref4.get().await().exists()) batch.update(ref4, privUpdates)
                    }
                }
            }
            batch.commit().await()
            updatePresence(online = !hideOnlineStatus, hideOnlineStatus = hideOnlineStatus)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveFcmToken(token: String): Result<Unit> {
        val cleanToken = token.trim()
        if (cleanToken.length !in 10..512) {
            return Result.failure(IllegalArgumentException("Invalid FCM registration token."))
        }
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val tokenId = KindredNotificationHelper.tokenDocumentId(cleanToken)
        val docRef = db.collection("users").document(uid).collection("fcmTokens").document(tokenId)
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "token" to cleanToken,
                    "platform" to "android",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteFcmToken(token: String): Result<Unit> {
        val cleanToken = token.trim()
        if (cleanToken.length !in 10..512) {
            return Result.success(Unit)
        }
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val tokenId = KindredNotificationHelper.tokenDocumentId(cleanToken)
        val docRef = db.collection("users").document(uid).collection("fcmTokens").document(tokenId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeUsersIBlocked(userId: String): Flow<List<BlockRecord>> = flow {
        val path = "blocks"
        emitAll(
            db.collection("blocks")
                .whereEqualTo("blockerUid", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(BlockRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { handleStreamError(it, OperationType.LIST, path, userId) }
        )
    }

    fun observeUsersWhoBlockedMe(userId: String): Flow<List<BlockRecord>> = flow {
        val path = "blocks"
        emitAll(
            db.collection("blocks")
                .whereEqualTo("blockedUid", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(BlockRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { handleStreamError(it, OperationType.LIST, path, userId) }
        )
    }

    suspend fun saveProfileAndPreferences(
        email: String?,
        displayName: String,
        age: Int,
        gender: String,
        bio: String,
        cityLabel: String,
        country: String = "United States",
        latitude: Double,
        longitude: Double,
        interests: List<String>,
        photoDataUrl: String?,
        profilePhotos: List<String> = emptyList(),
        discoverable: Boolean,
        interestedIn: String,
        minAgePref: Int,
        maxAgePref: Int,
        maxDistanceKm: Int,
        verifiedOnlyFilter: Boolean,
        dateOfBirth: String = "",
        profileCompleted: Boolean = true
    ): Result<Unit> {
        val cleanName = displayName.trim().take(40)
        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Display name must be at least 2 characters."))
        }
        val safeAge = age.coerceIn(18, 99)
        val safeMinAge = minAgePref.coerceIn(18, 99)
        val safeMaxAge = maxAgePref.coerceIn(safeMinAge, 99)
        val safeDistance = maxDistanceKm.coerceIn(5, 500)
        val cleanBio = bio.trim().take(400)
        val cleanCity = cityLabel.trim().ifBlank { "Nearby" }.take(80)
        val cleanCountry = country.trim().take(80)
        val cleanInterests = interests.map { it.trim().take(40) }.filter { it.isNotBlank() }.distinct().take(8)
        val candidatePhotos = (profilePhotos + listOfNotNull(photoDataUrl))
            .map { it.trim() }
            .filter { it.isNotBlank() && (CloudMediaStorageService.isRemoteHttpUrl(it) || it.length <= 920_000) }
            .distinct()
            .take(KindredConstants.MAX_PROFILE_PHOTOS)
        val coarseLat = LocationAndGeoHelper.roundToCoarse(latitude.coerceIn(-90.0, 90.0))
        val coarseLng = LocationAndGeoHelper.roundToCoarse(longitude.coerceIn(-180.0, 180.0))
        val geohash = GeoHashUtils.encode(coarseLat, coarseLng, precision = 7)
        val geohash3 = geohash.take(3)
        val geohash4 = geohash.take(4)

        var currentPath = "users"
        return try {
            val uid = requireUserId()
            withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS) {
                val cleanPhotos = uploadProfilePhotosToCloud(uid, candidatePhotos)
                val primaryPhoto = cleanPhotos.firstOrNull()
                val privateRef = db.collection("users").document(uid)
                val publicRef = db.collection("publicProfiles").document(uid)

                val privateExists = privateRef.get().await().exists()
                val publicSnap = publicRef.get().await()
                val publicExists = publicSnap.exists()
                val existingPublic = if (publicExists) {
                    publicSnap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null

                val batch = db.batch()
                currentPath = privateRef.path

            if (privateExists) {
                val updatePrivate = mutableMapOf<String, Any>(
                    "interestedIn" to interestedIn,
                    "minAgePref" to safeMinAge.toLong(),
                    "maxAgePref" to safeMaxAge.toLong(),
                    "maxDistanceKm" to safeDistance.toLong(),
                    "verifiedOnlyFilter" to verifiedOnlyFilter,
                    "exactLatitude" to latitude.coerceIn(-90.0, 90.0),
                    "exactLongitude" to longitude.coerceIn(-180.0, 180.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!email.isNullOrBlank()) {
                    updatePrivate["email"] = email.take(200)
                }
                batch.update(privateRef, updatePrivate)
            } else {
                val createPrivate = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "interestedIn" to interestedIn,
                    "minAgePref" to safeMinAge.toLong(),
                    "maxAgePref" to safeMaxAge.toLong(),
                    "maxDistanceKm" to safeDistance.toLong(),
                    "verifiedOnlyFilter" to verifiedOnlyFilter,
                    "exactLatitude" to latitude.coerceIn(-90.0, 90.0),
                    "exactLongitude" to longitude.coerceIn(-180.0, 180.0),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!email.isNullOrBlank()) {
                    createPrivate["email"] = email.take(200)
                }
                batch.set(privateRef, createPrivate)
            }

            val photosChanged = existingPublic != null && existingPublic.allProfilePhotos() != cleanPhotos
            val keepVerifiedStatus = existingPublic != null && !photosChanged && existingPublic.isProfileVerified

            val cleanDob = dateOfBirth.trim().take(16)
            val resolvedDob = if (cleanDob.isNotBlank()) cleanDob else existingPublic?.dateOfBirth.orEmpty()

            currentPath = publicRef.path
            if (publicExists) {
                val updatePublic = mutableMapOf<String, Any>(
                    "displayName" to cleanName,
                    "age" to safeAge.toLong(),
                    "gender" to gender,
                    "bio" to cleanBio,
                    "cityLabel" to cleanCity,
                    "country" to cleanCountry,
                    "coarseLatitude" to coarseLat,
                    "coarseLongitude" to coarseLng,
                    "geohash" to geohash,
                    "geohash3" to geohash3,
                    "geohash4" to geohash4,
                    "interests" to cleanInterests,
                    "profilePhotos" to cleanPhotos,
                    "avatarSeed" to uid.take(40),
                    "discoverable" to discoverable,
                    "profileCompleted" to profileCompleted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (resolvedDob.isNotBlank()) {
                    updatePublic["dateOfBirth"] = resolvedDob
                }
                if (primaryPhoto != null) {
                    updatePublic["photoDataUrl"] = primaryPhoto
                } else if (existingPublic?.photoDataUrl != null) {
                    updatePublic["photoDataUrl"] = FieldValue.delete()
                }
                if (photosChanged && existingPublic?.isProfileVerified == true) {
                    updatePublic["verificationStatus"] = "UNVERIFIED"
                    updatePublic["verificationScore"] = 30L
                }
                batch.update(publicRef, updatePublic)
            } else {
                val createPublic = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "age" to safeAge.toLong(),
                    "gender" to gender,
                    "bio" to cleanBio,
                    "cityLabel" to cleanCity,
                    "country" to cleanCountry,
                    "coarseLatitude" to coarseLat,
                    "coarseLongitude" to coarseLng,
                    "geohash" to geohash,
                    "geohash3" to geohash3,
                    "geohash4" to geohash4,
                    "interests" to cleanInterests,
                    "profilePhotos" to cleanPhotos,
                    "avatarSeed" to uid.take(40),
                    "discoverable" to discoverable,
                    "profileCompleted" to profileCompleted,
                    "verificationStatus" to "UNVERIFIED",
                    "verificationScore" to 30L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (resolvedDob.isNotBlank()) {
                    createPublic["dateOfBirth"] = resolvedDob
                }
                if (primaryPhoto != null) {
                    createPublic["photoDataUrl"] = primaryPhoto
                }
                batch.set(publicRef, createPublic)
            }

            // Sync /geoCells/{cellId}/profiles/{uid} spatial partition atomically
            val geoProfilePayload = buildGeoCellPayload(
                uid = uid,
                displayName = cleanName,
                age = safeAge.toLong(),
                gender = gender,
                bio = cleanBio,
                cityLabel = cleanCity,
                country = cleanCountry,
                coarseLat = coarseLat,
                coarseLng = coarseLng,
                interests = cleanInterests,
                photoDataUrl = primaryPhoto,
                profilePhotos = cleanPhotos,
                discoverable = discoverable,
                dateOfBirth = resolvedDob,
                profileCompleted = profileCompleted,
                existingPublic = existingPublic,
                forceUnverified = photosChanged && existingPublic?.isProfileVerified == true
            )
            geoService.stageGeoCellIndexMutations(
                batch = batch,
                userId = uid,
                geohash = geohash,
                discoverable = discoverable,
                previousGeoHash3 = existingPublic?.geohash3,
                previousGeoHash4 = existingPublic?.geohash4,
                baseProfileFields = geoProfilePayload,
                createMissingCells = true
            )

                batch.commit().await()
                Result.success(Unit)
            }
        } catch (e: TimeoutCancellationException) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "saveProfileAndPreferences timed out for $currentPath", e)
            Result.failure(IOException(msg, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "saveProfileAndPreferences failed for $currentPath: $msg", e)
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    /**
     * Focused update for the Edit Profile flow (bio, interests, and profile photos).
     * Atomically updates /publicProfiles/{uid} and /geoCells/{cellId}/profiles/{uid},
     * resetting verification status if a verified member modifies their photos.
     * Falls back to creating a baseline profile if the user has not saved a profile yet.
     */
    suspend fun updateProfileContent(
        email: String?,
        fallbackDisplayName: String?,
        bio: String,
        interests: List<String>,
        profilePhotos: List<String>,
        cityLabel: String? = null,
        country: String? = null,
        displayName: String? = null,
        gender: String? = null,
        dateOfBirth: String? = null,
        computedAge: Int? = null
    ): Result<Unit> {
        val cleanBio = bio.trim().take(400)
        val cleanInterests = interests
            .map { it.trim().take(40) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
        val candidatePhotos = profilePhotos
            .map { it.trim() }
            .filter { it.isNotBlank() && (CloudMediaStorageService.isRemoteHttpUrl(it) || it.length <= 920_000) }
            .distinct()
            .take(KindredConstants.MAX_PROFILE_PHOTOS)

        var docPath = "publicProfiles"
        return try {
            val uid = requireUserId()
            val publicRef = db.collection("publicProfiles").document(uid)
            docPath = publicRef.path
            val publicSnap = publicRef.get().await()
            val existingPublic = if (publicSnap.exists()) {
                publicSnap.toObject(
                    PublicProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }

            if (existingPublic == null) {
                val safeFallbackName = displayName?.trim()?.take(40)?.takeIf { it.length >= 2 }
                    ?: fallbackDisplayName?.trim()?.take(40)?.takeIf { it.length >= 2 }
                    ?: "Kindred Member"
                val hasExplicitAge = computedAge != null && computedAge in 18..99
                return saveProfileAndPreferences(
                    email = email,
                    displayName = safeFallbackName,
                    age = computedAge?.coerceIn(18, 99) ?: 18,
                    gender = gender?.takeIf { it in KindredConstants.GENDER_OPTIONS } ?: "Woman",
                    bio = cleanBio,
                    cityLabel = cityLabel?.trim()?.take(80).orEmpty().ifBlank { "San Francisco" },
                    country = country?.trim()?.take(80).orEmpty().ifBlank { "United States" },
                    latitude = 37.7749,
                    longitude = -122.4194,
                    interests = cleanInterests,
                    photoDataUrl = candidatePhotos.firstOrNull(),
                    profilePhotos = candidatePhotos,
                    discoverable = true,
                    interestedIn = "Everyone",
                    minAgePref = 18,
                    maxAgePref = 45,
                    maxDistanceKm = 100,
                    verifiedOnlyFilter = false,
                    dateOfBirth = dateOfBirth.orEmpty(),
                    profileCompleted = hasExplicitAge && !dateOfBirth.isNullOrBlank()
                )
            }

            withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS) {
                val cleanPhotos = uploadProfilePhotosToCloud(uid, candidatePhotos)
                val primaryPhoto = cleanPhotos.firstOrNull()

            val nextName = displayName?.trim()?.take(40)?.takeIf { it.length >= 2 } ?: existingPublic.displayName
            val nextGender = gender?.takeIf { it in KindredConstants.GENDER_OPTIONS } ?: existingPublic.gender
            val nextAge = computedAge?.coerceIn(18, 99)?.toLong() ?: existingPublic.age
            val nextDob = dateOfBirth?.trim()?.take(16)?.takeIf { it.isNotBlank() } ?: existingPublic.dateOfBirth
            val nextCity = cityLabel?.trim()?.take(80)?.ifBlank { existingPublic.cityLabel } ?: existingPublic.cityLabel
            val nextCountry = country?.trim()?.take(80) ?: existingPublic.country
            val photosChanged = existingPublic.allProfilePhotos() != cleanPhotos
            val resetVerification = photosChanged && existingPublic.isProfileVerified

            val updatePublic = mutableMapOf<String, Any>(
                "displayName" to nextName,
                "age" to nextAge,
                "gender" to nextGender,
                "bio" to cleanBio,
                "cityLabel" to nextCity,
                "country" to nextCountry,
                "interests" to cleanInterests,
                "profilePhotos" to cleanPhotos,
                "profileCompleted" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (nextDob.isNotBlank()) {
                updatePublic["dateOfBirth"] = nextDob
            }
            if (primaryPhoto != null) {
                updatePublic["photoDataUrl"] = primaryPhoto
            } else if (existingPublic.photoDataUrl != null) {
                updatePublic["photoDataUrl"] = FieldValue.delete()
            }
            if (resetVerification) {
                updatePublic["verificationStatus"] = "UNVERIFIED"
                updatePublic["verificationScore"] = 30L
            }

            val geohash = existingPublic.geohash.ifBlank {
                GeoHashUtils.encode(
                    existingPublic.coarseLatitude,
                    existingPublic.coarseLongitude,
                    precision = 7
                )
            }

            val batch = db.batch()
            batch.update(publicRef, updatePublic)

            val geoProfilePayload = buildGeoCellPayload(
                uid = uid,
                displayName = nextName,
                age = nextAge,
                gender = nextGender,
                bio = cleanBio,
                cityLabel = nextCity,
                country = nextCountry,
                coarseLat = existingPublic.coarseLatitude,
                coarseLng = existingPublic.coarseLongitude,
                interests = cleanInterests,
                photoDataUrl = primaryPhoto,
                profilePhotos = cleanPhotos,
                discoverable = existingPublic.discoverable,
                dateOfBirth = nextDob,
                profileCompleted = true,
                existingPublic = existingPublic,
                forceUnverified = resetVerification
            )
            geoService.stageGeoCellIndexMutations(
                batch = batch,
                userId = uid,
                geohash = geohash,
                discoverable = existingPublic.discoverable,
                previousGeoHash3 = existingPublic.geohash3,
                previousGeoHash4 = existingPublic.geohash4,
                baseProfileFields = geoProfilePayload,
                createMissingCells = true
            )

                batch.commit().await()
                Result.success(Unit)
            }
        } catch (e: TimeoutCancellationException) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "updateProfileContent timed out for $docPath", e)
            Result.failure(IOException(msg, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "updateProfileContent failed for $docPath: $msg", e)
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun updateDiscoveryFilters(
        interestedIn: String,
        minAgePref: Int,
        maxAgePref: Int,
        maxDistanceKm: Int,
        verifiedOnlyFilter: Boolean
    ): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            val safeMin = minAgePref.coerceIn(18, 99)
            val safeMax = maxAgePref.coerceIn(safeMin, 99)
            val safeDist = maxDistanceKm.coerceIn(5, 500)
            ref.update(
                mapOf(
                    "interestedIn" to interestedIn,
                    "minAgePref" to safeMin.toLong(),
                    "maxAgePref" to safeMax.toLong(),
                    "maxDistanceKm" to safeDist.toLong(),
                    "verifiedOnlyFilter" to verifiedOnlyFilter,
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

    /**
     * Persists dating preferences (interestedIn, age range, radius, verified-only) in /users/{uid}
     * (creating the private user document if needed) and optionally updates gender & discoverable
     * visibility atomically across /publicProfiles/{uid} and /geoCells/{cellId}/profiles/{uid}.
     */
    suspend fun saveSettingsPreferences(
        email: String?,
        interestedIn: String,
        minAgePref: Int,
        maxAgePref: Int,
        maxDistanceKm: Int,
        verifiedOnlyFilter: Boolean,
        myGender: String? = null,
        discoverable: Boolean? = null
    ): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val privateRef = db.collection("users").document(uid)
            val publicRef = db.collection("publicProfiles").document(uid)
            docPath = privateRef.path

            val safeMin = minAgePref.coerceIn(18, 99)
            val safeMax = maxAgePref.coerceIn(safeMin, 99)
            val safeDist = maxDistanceKm.coerceIn(5, 500)

            val privateExists = privateRef.get().await().exists()
            val publicSnap = publicRef.get().await()
            val existingPublic = if (publicSnap.exists()) {
                publicSnap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }

            val batch = db.batch()
            if (privateExists) {
                val updatePrivate = mutableMapOf<String, Any>(
                    "interestedIn" to interestedIn,
                    "minAgePref" to safeMin.toLong(),
                    "maxAgePref" to safeMax.toLong(),
                    "maxDistanceKm" to safeDist.toLong(),
                    "verifiedOnlyFilter" to verifiedOnlyFilter,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!email.isNullOrBlank()) {
                    updatePrivate["email"] = email.take(200)
                }
                batch.update(privateRef, updatePrivate)
            } else {
                val createPrivate = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "interestedIn" to interestedIn,
                    "minAgePref" to safeMin.toLong(),
                    "maxAgePref" to safeMax.toLong(),
                    "maxDistanceKm" to safeDist.toLong(),
                    "verifiedOnlyFilter" to verifiedOnlyFilter,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!email.isNullOrBlank()) {
                    createPrivate["email"] = email.take(200)
                }
                batch.set(privateRef, createPrivate)
            }

            if (existingPublic != null && (myGender != null || discoverable != null)) {
                val nextGender = myGender ?: existingPublic.gender
                val nextDiscoverable = discoverable ?: existingPublic.discoverable
                if (nextGender != existingPublic.gender || nextDiscoverable != existingPublic.discoverable) {
                    docPath = publicRef.path
                    batch.update(
                        publicRef,
                        mapOf(
                            "gender" to nextGender,
                            "discoverable" to nextDiscoverable,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    val geohash = existingPublic.geohash.ifBlank {
                        GeoHashUtils.encode(
                            existingPublic.coarseLatitude,
                            existingPublic.coarseLongitude,
                            precision = 7
                        )
                    }
                    val geoProfilePayload = buildGeoCellPayload(
                        uid = uid,
                        displayName = existingPublic.displayName,
                        age = existingPublic.age,
                        gender = nextGender,
                        bio = existingPublic.bio,
                        cityLabel = existingPublic.cityLabel,
                        country = existingPublic.country,
                        coarseLat = existingPublic.coarseLatitude,
                        coarseLng = existingPublic.coarseLongitude,
                        interests = existingPublic.interests,
                        photoDataUrl = existingPublic.photoDataUrl,
                        profilePhotos = existingPublic.profilePhotos,
                        discoverable = nextDiscoverable,
                        existingPublic = existingPublic,
                        forceUnverified = false
                    )
                    geoService.stageGeoCellIndexMutations(
                        batch = batch,
                        userId = uid,
                        geohash = geohash,
                        discoverable = nextDiscoverable,
                        previousGeoHash3 = existingPublic.geohash3,
                        previousGeoHash4 = existingPublic.geohash4,
                        baseProfileFields = geoProfilePayload,
                        createMissingCells = true
                    )
                }
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateDiscoverable(discoverable: Boolean): Result<Unit> {
        var docPath = "publicProfiles"
        return try {
            val uid = requireUserId()
            val publicRef = db.collection("publicProfiles").document(uid)
            docPath = publicRef.path
            val publicSnap = publicRef.get().await()
            val existingPublic = if (publicSnap.exists()) {
                publicSnap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            } ?: return Result.success(Unit)

            if (existingPublic.discoverable == discoverable) return Result.success(Unit)
            val batch = db.batch()
            batch.update(
                publicRef,
                mapOf(
                    "discoverable" to discoverable,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val geohash = existingPublic.geohash.ifBlank {
                GeoHashUtils.encode(existingPublic.coarseLatitude, existingPublic.coarseLongitude, precision = 7)
            }
            val geoProfilePayload = buildGeoCellPayload(
                uid = uid,
                displayName = existingPublic.displayName,
                age = existingPublic.age,
                gender = existingPublic.gender,
                bio = existingPublic.bio,
                cityLabel = existingPublic.cityLabel,
                country = existingPublic.country,
                coarseLat = existingPublic.coarseLatitude,
                coarseLng = existingPublic.coarseLongitude,
                interests = existingPublic.interests,
                photoDataUrl = existingPublic.photoDataUrl,
                profilePhotos = existingPublic.profilePhotos,
                discoverable = discoverable,
                existingPublic = existingPublic,
                forceUnverified = false
            )
            geoService.stageGeoCellIndexMutations(
                batch = batch,
                userId = uid,
                geohash = geohash,
                discoverable = discoverable,
                previousGeoHash3 = existingPublic.geohash3,
                previousGeoHash4 = existingPublic.geohash4,
                baseProfileFields = geoProfilePayload,
                createMissingCells = true
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateLocationCoordinates(
        latitude: Double,
        longitude: Double,
        cityLabel: String,
        countryName: String = "",
        email: String? = null
    ): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val privateRef = db.collection("users").document(uid)
            val publicRef = db.collection("publicProfiles").document(uid)
            val privateExists = privateRef.get().await().exists()
            val publicSnap = publicRef.get().await()
            val publicExists = publicSnap.exists()
            val existingPublic = if (publicExists) {
                publicSnap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null

            val cleanCity = cityLabel.trim().ifBlank { "Nearby" }.take(80)
            val cleanCountry = countryName.trim().take(80).ifBlank { existingPublic?.country.orEmpty() }
            val safeLat = latitude.coerceIn(-90.0, 90.0)
            val safeLng = longitude.coerceIn(-180.0, 180.0)
            val coarseLat = LocationAndGeoHelper.roundToCoarse(safeLat)
            val coarseLng = LocationAndGeoHelper.roundToCoarse(safeLng)
            val geohash = GeoHashUtils.encode(coarseLat, coarseLng, precision = 7)
            val geohash3 = geohash.take(3)
            val geohash4 = geohash.take(4)

            val batch = db.batch()
            docPath = privateRef.path
            if (privateExists) {
                batch.update(
                    privateRef,
                    mapOf(
                        "exactLatitude" to safeLat,
                        "exactLongitude" to safeLng,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                val createPrivate = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "interestedIn" to "Everyone",
                    "minAgePref" to 20L,
                    "maxAgePref" to 45L,
                    "maxDistanceKm" to 100L,
                    "verifiedOnlyFilter" to false,
                    "exactLatitude" to safeLat,
                    "exactLongitude" to safeLng,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!email.isNullOrBlank()) {
                    createPrivate["email"] = email.take(200)
                }
                batch.set(privateRef, createPrivate)
            }
            if (publicExists && existingPublic != null) {
                docPath = publicRef.path
                batch.update(
                    publicRef,
                    mapOf(
                        "cityLabel" to cleanCity,
                        "country" to cleanCountry,
                        "coarseLatitude" to coarseLat,
                        "coarseLongitude" to coarseLng,
                        "geohash" to geohash,
                        "geohash3" to geohash3,
                        "geohash4" to geohash4,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val geoProfilePayload = buildGeoCellPayload(
                    uid = uid,
                    displayName = existingPublic.displayName,
                    age = existingPublic.age,
                    gender = existingPublic.gender,
                    bio = existingPublic.bio,
                    cityLabel = cleanCity,
                    country = cleanCountry,
                    coarseLat = coarseLat,
                    coarseLng = coarseLng,
                    interests = existingPublic.interests,
                    photoDataUrl = existingPublic.photoDataUrl,
                    profilePhotos = existingPublic.profilePhotos,
                    discoverable = existingPublic.discoverable,
                    existingPublic = existingPublic,
                    forceUnverified = false
                )
                geoService.stageGeoCellIndexMutations(
                    batch = batch,
                    userId = uid,
                    geohash = geohash,
                    discoverable = existingPublic.discoverable,
                    previousGeoHash3 = existingPublic.geohash3,
                    previousGeoHash4 = existingPublic.geohash4,
                    baseProfileFields = geoProfilePayload,
                    createMissingCells = true
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordSwipe(targetUserId: String, direction: String): Result<String?> {
        var docPath = "swipes"
        return try {
            val uid = requireUserId()
            require(targetUserId.isNotBlank() && targetUserId != uid) {
                "Invalid target user for swipe."
            }
            require(direction == "LIKE" || direction == "PASS") {
                "Invalid swipe direction."
            }

            val swipeId = "${uid}_${targetUserId}"
            val swipeRef = db.collection("swipes").document(swipeId)
            docPath = swipeRef.path

            val blockedByMe = db.collection("blocks")
                .whereEqualTo("blockerUid", uid)
                .get()
                .await()
                .documents
                .any { it.getString("blockedUid") == targetUserId }
            val blockedMe = db.collection("blocks")
                .whereEqualTo("blockedUid", uid)
                .get()
                .await()
                .documents
                .any { it.getString("blockerUid") == targetUserId }
            if (blockedByMe || blockedMe) {
                return Result.failure(IllegalStateException("This profile cannot be matched because a block is active."))
            }

            // /swipes/{swipeId} has `allow update: if false;` in firestore.rules, so check if already recorded
            val alreadySwipedDoc = db.collection("swipes")
                .whereEqualTo("fromUserId", uid)
                .get()
                .await()
                .documents
                .firstOrNull { it.getString("toUserId") == targetUserId }

            if (alreadySwipedDoc != null) {
                val recordedDirection = alreadySwipedDoc.getString("direction")
                if (direction == "LIKE" && recordedDirection == "LIKE") {
                    val incomingLikes = db.collection("swipes")
                        .whereEqualTo("toUserId", uid)
                        .get()
                        .await()
                    val mutualLikeExists = incomingLikes.documents.any { doc ->
                        doc.getString("fromUserId") == targetUserId &&
                            doc.getString("direction") == "LIKE"
                    }
                    if (mutualLikeExists) {
                        val sortedParticipants = listOf(uid, targetUserId).sorted()
                        val matchId = "${sortedParticipants[0]}_${sortedParticipants[1]}"
                        val matchRef = db.collection("matches").document(matchId)
                        val existingMatch = db.collection("matches")
                            .whereArrayContains("participantUids", uid)
                            .get()
                            .await()
                            .documents
                            .any { it.id == matchId }
                        if (!existingMatch) {
                            docPath = matchRef.path
                            matchRef.set(
                                mapOf(
                                    "participantUids" to sortedParticipants,
                                    "createdBy" to uid,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                        return Result.success(matchId)
                    }
                }
                return Result.success(null)
            }

            val swipePayload = mapOf(
                "fromUserId" to uid,
                "toUserId" to targetUserId,
                "direction" to direction,
                "createdAt" to FieldValue.serverTimestamp()
            )

            if (direction == "LIKE") {
                val incomingLikes = db.collection("swipes")
                    .whereEqualTo("toUserId", uid)
                    .get()
                    .await()
                val mutualLikeExists = incomingLikes.documents.any { doc ->
                    doc.getString("fromUserId") == targetUserId &&
                        doc.getString("direction") == "LIKE"
                }

                if (mutualLikeExists) {
                    val sortedParticipants = listOf(uid, targetUserId).sorted()
                    val matchId = "${sortedParticipants[0]}_${sortedParticipants[1]}"
                    val matchRef = db.collection("matches").document(matchId)
                    val existingMatch = db.collection("matches")
                        .whereArrayContains("participantUids", uid)
                        .get()
                        .await()
                        .documents
                        .any { it.id == matchId }

                    if (existingMatch) {
                        swipeRef.set(swipePayload).await()
                        return Result.success(matchId)
                    }

                    docPath = matchRef.path
                    val batch = db.batch()
                    batch.set(swipeRef, swipePayload)
                    batch.set(
                        matchRef,
                        mapOf(
                            "participantUids" to sortedParticipants,
                            "createdBy" to uid,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.commit().await()
                    return Result.success(matchId)
                }
            }

            swipeRef.set(swipePayload).await()
            Result.success(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Opens an existing match/direct conversation with [targetUserId] or creates a new DIRECT
     * conversation document in /matches/{minUid_maxUid}.
     */
    suspend fun createOrOpenDirectMatch(targetUserId: String): Result<String> {
        var docPath = "matches"
        return try {
            val uid = requireUserId()
            require(targetUserId.isNotBlank() && targetUserId != uid) {
                "Invalid target profile."
            }

            val sortedParticipants = listOf(uid, targetUserId).sorted()
            val matchId = "${sortedParticipants[0]}_${sortedParticipants[1]}"

            val existingMatch = db.collection("matches")
                .whereArrayContains("participantUids", uid)
                .get()
                .await()
                .documents
                .any { it.id == matchId }
            if (existingMatch) {
                return Result.success(matchId)
            }

            val blockedByMe = db.collection("blocks")
                .whereEqualTo("blockerUid", uid)
                .get()
                .await()
                .documents
                .any { it.getString("blockedUid") == targetUserId }
            val blockedMe = db.collection("blocks")
                .whereEqualTo("blockedUid", uid)
                .get()
                .await()
                .documents
                .any { it.getString("blockerUid") == targetUserId }
            if (blockedByMe || blockedMe) {
                return Result.failure(IllegalStateException("Messaging is unavailable because a block is active."))
            }

            val myPublicSnap = db.collection("publicProfiles").document(uid).get().await()
            if (!myPublicSnap.exists()) {
                return Result.failure(
                    IllegalStateException("Save your profile in the Profile tab before messaging members directly.")
                )
            }
            val targetPublicSnap = db.collection("publicProfiles").document(targetUserId).get().await()
            if (!targetPublicSnap.exists()) {
                return Result.failure(
                    IllegalStateException("Recipient profile is not available yet.")
                )
            }

            val matchRef = db.collection("matches").document(matchId)
            if (matchRef.get().await().exists()) {
                return Result.success(matchId)
            }
            docPath = matchRef.path
            matchRef.set(
                mapOf(
                    "participantUids" to sortedParticipants,
                    "createdBy" to uid,
                    "conversationType" to "DIRECT",
                    "lastMessageReadByUids" to listOf(uid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(matchId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSwipe(targetUserId: String): Result<Unit> {
        var docPath = "swipes"
        return try {
            val uid = requireUserId()
            require(targetUserId.isNotBlank() && targetUserId != uid) {
                "Invalid target user to undo swipe."
            }
            val swipeId = "${uid}_${targetUserId}"
            val swipeRef = db.collection("swipes").document(swipeId)
            docPath = swipeRef.path
            swipeRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        matchId: String,
        participantUids: List<String>,
        text: String,
        messageType: String = "TEXT",
        mediaDataUrl: String? = null,
        mediaGifId: String? = null,
        mediaDurationSec: Long = 0L,
        disappearingSeconds: Long = 0L,
        viewOnce: Boolean = false,
        preparedVideo: PreparedVideoUpload? = null,
        replyToMessageId: String? = null,
        replyToSenderId: String? = null,
        replyToText: String? = null,
        replyToType: String? = null
    ): Result<Unit> {
        val allowedType = when (messageType) {
            "ICEBREAKER", "PHOTO", "VIDEO", "VOICE", "GIF", "CALL" -> messageType
            else -> "TEXT"
        }
        val cleanText = text.trim().take(1000)
        val cleanMedia = (preparedVideo?.inlineDataUrl ?: mediaDataUrl)?.trim()?.takeIf { it.isNotBlank() }
        val cleanGif = mediaGifId?.trim()?.take(300)?.takeIf { it.isNotBlank() }
        if (cleanText.isEmpty() && allowedType !in setOf("PHOTO", "VIDEO", "VOICE", "GIF")) {
            return Result.failure(IllegalArgumentException("Message must be between 1 and 1000 characters."))
        }
        if (preparedVideo == null && cleanMedia != null && !cleanMedia.startsWith("file://") && cleanMedia.length > 920_000) {
            return Result.failure(IllegalArgumentException("Photo or voice attachment exceeds upload limit."))
        }
        val effectiveDurationSec = preparedVideo?.durationSec ?: mediaDurationSec.coerceAtLeast(0L)
        val safeDisappearSec = disappearingSeconds.coerceIn(0L, 604_800L)
        val expiresAtMs = if (!viewOnce && safeDisappearSec > 0L) {
            System.currentTimeMillis() + safeDisappearSec * 1000L
        } else {
            null
        }
        val previewText = when {
            viewOnce && allowedType == "PHOTO" -> "⏱️ View-once photo"
            viewOnce && allowedType == "VOICE" -> "⏱️ View-once voice note"
            allowedType == "PHOTO" -> cleanText.ifBlank { "📷 Photo" }
            allowedType == "VIDEO" -> cleanText.ifBlank {
                if (effectiveDurationSec > 0L) "🎥 Video (${VideoClipHelper.formatDurationLabel(effectiveDurationSec)})" else "🎥 Video"
            }
            allowedType == "VOICE" -> "🎙️ Voice note (${effectiveDurationSec.coerceAtLeast(1L)}s)"
            allowedType == "GIF" -> cleanText.ifBlank { "🎞️ Sent a GIF" }
            else -> cleanText
        }.take(300)

        var docPath = "matches/$matchId/messages"
        return try {
            val uid = requireUserId()
            val sortedParticipants = participantUids.distinct().sorted()
            require(sortedParticipants.size == 2 && uid in sortedParticipants) {
                "You are not a participant in this conversation."
            }
            val matchRef = db.collection("matches").document(matchId)
            val msgRef = matchRef.collection("messages").document()
            docPath = msgRef.path

            withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS) {
                val payload = mutableMapOf<String, Any>(
                    "matchId" to matchId,
                    "senderId" to uid,
                    "participantUids" to sortedParticipants,
                    "text" to cleanText,
                    "messageType" to allowedType,
                    "readByUids" to listOf(uid),
                    "disappearingSeconds" to safeDisappearSec,
                    "viewOnce" to viewOnce,
                    "openedByRecipient" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (preparedVideo != null) {
                    val videoUrl = cloudStorage.uploadFile(
                        file = preparedVideo.localCachedFile,
                        remotePath = "chat_media/$matchId/${msgRef.id}.mp4",
                        contentType = preparedVideo.mimeType
                    ).getOrThrow()
                    payload["mediaDataUrl"] = videoUrl

                    val rawThumb = preparedVideo.thumbnailDataUrl?.trim().orEmpty()
                    if (rawThumb.isNotBlank()) {
                        val thumbUrl = if (CloudMediaStorageService.isRemoteHttpUrl(rawThumb)) {
                            rawThumb.take(2048)
                        } else {
                            cloudStorage.ensureRemoteMediaUrl(
                                rawDataUrlOrRemoteUrl = rawThumb,
                                remotePath = "chat_media/$matchId/${msgRef.id}_thumb.jpg",
                                defaultContentType = "image/jpeg"
                            ).getOrElse {
                                rawThumb.takeIf { it.length <= 180_000 }.orEmpty()
                            }
                        }
                        if (thumbUrl.isNotBlank() && thumbUrl.length <= 180_000) {
                            payload["thumbnailDataUrl"] = thumbUrl
                        }
                    }
                    payload["mediaMimeType"] = preparedVideo.mimeType.take(80)
                    payload["mediaChunkCount"] = 0L
                    payload["mediaByteSize"] = preparedVideo.sizeBytes.coerceAtLeast(0L)
                } else if (cleanMedia != null) {
                    val resolvedMedia = when (allowedType) {
                        "VOICE" -> cloudStorage.ensureRemoteMediaUrl(
                            rawDataUrlOrRemoteUrl = cleanMedia,
                            remotePath = "chat_media/$matchId/${msgRef.id}.m4a",
                            defaultContentType = "audio/mp4"
                        ).getOrThrow()
                        "PHOTO" -> cloudStorage.ensureRemoteMediaUrl(
                            rawDataUrlOrRemoteUrl = cleanMedia,
                            remotePath = "chat_media/$matchId/${msgRef.id}.jpg",
                            defaultContentType = "image/jpeg"
                        ).getOrThrow()
                        else -> cleanMedia
                    }
                    payload["mediaDataUrl"] = resolvedMedia
                }
                if (cleanGif != null) payload["mediaGifId"] = cleanGif
                if (effectiveDurationSec > 0L) payload["mediaDurationSec"] = effectiveDurationSec
                if (expiresAtMs != null) payload["expiresAtMs"] = expiresAtMs
                if (!replyToMessageId.isNullOrBlank()) {
                    payload["replyToMessageId"] = replyToMessageId.take(128)
                    if (!replyToSenderId.isNullOrBlank()) {
                        payload["replyToSenderId"] = replyToSenderId.take(128)
                    }
                    if (!replyToText.isNullOrBlank()) {
                        payload["replyToText"] = replyToText.take(300)
                    }
                    if (!replyToType.isNullOrBlank()) {
                        payload["replyToType"] = replyToType.take(24)
                    }
                }

                val batch = db.batch()
                batch.set(msgRef, payload)
                batch.update(
                    matchRef,
                    mapOf(
                        "lastMessageText" to previewText,
                        "lastMessageSenderId" to uid,
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "lastMessageReadByUids" to listOf(uid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
                Result.success(Unit)
            }
        } catch (e: TimeoutCancellationException) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "sendMessage timed out for $docPath", e)
            Result.failure(IOException(msg, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = CloudMediaStorageService.formatUploadError(e)
            Log.e(CloudMediaStorageService.TAG, "sendMessage failed for $docPath: $msg", e)
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(IllegalStateException(msg, e))
        } finally {
            if (cleanMedia != null && cleanMedia.startsWith("file://")) {
                runCatching {
                    val f = File(cleanMedia.removePrefix("file://"))
                    if (f.name.startsWith("kindred_voice_") && f.exists()) {
                        f.delete()
                    }
                }
            }
        }
    }

    suspend fun markMessagesAsRead(matchId: String, messageIds: List<String>): Result<Unit> {
        if (matchId.isBlank() || messageIds.isEmpty()) return Result.success(Unit)
        var docPath = "matches/$matchId/messages"
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            messageIds.distinct().take(40).forEach { msgId ->
                val ref = db.collection("matches").document(matchId).collection("messages").document(msgId)
                docPath = ref.path
                batch.update(
                    ref,
                    mapOf(
                        "readByUids" to FieldValue.arrayUnion(uid),
                        "readAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val matchRef = db.collection("matches").document(matchId)
            batch.update(
                matchRef,
                mapOf(
                    "lastMessageReadByUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markViewOnceOpened(
        matchId: String,
        messageId: String,
        lingerSeconds: Long = 10L
    ): Result<Unit> {
        var docPath = "matches/$matchId/messages/$messageId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("matches").document(matchId).collection("messages").document(messageId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "readByUids" to FieldValue.arrayUnion(uid),
                    "readAt" to FieldValue.serverTimestamp(),
                    "openedByRecipient" to true,
                    "expiresAtMs" to (System.currentTimeMillis() + lingerSeconds.coerceIn(5L, 60L) * 1000L)
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

    suspend fun deleteMessage(matchId: String, messageId: String): Result<Unit> {
        var docPath = "matches/$matchId/messages/$messageId"
        return try {
            requireUserId()
            val ref = db.collection("matches").document(matchId).collection("messages").document(messageId)
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

    suspend fun deleteMessageForMe(matchId: String, messageId: String): Result<Unit> {
        var docPath = "matches/$matchId/messages/$messageId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("matches").document(matchId).collection("messages").document(messageId)
            docPath = ref.path
            ref.update("deletedForUids", FieldValue.arrayUnion(uid)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun unsendMessage(
        matchId: String,
        messageId: String,
        isLastMessage: Boolean = false
    ): Result<Unit> {
        var docPath = "matches/$matchId/messages/$messageId"
        return try {
            requireUserId()
            val matchRef = db.collection("matches").document(matchId)
            val msgRef = matchRef.collection("messages").document(messageId)
            docPath = msgRef.path
            val batch = db.batch()
            batch.update(
                msgRef,
                mapOf(
                    "unsent" to true,
                    "unsentAt" to FieldValue.serverTimestamp(),
                    "text" to "Message unsent",
                    "mediaDataUrl" to FieldValue.delete(),
                    "thumbnailDataUrl" to FieldValue.delete(),
                    "mediaGifId" to FieldValue.delete()
                )
            )
            if (isLastMessage) {
                batch.update(
                    matchRef,
                    mapOf(
                        "lastMessageText" to "Message unsent",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun searchMessagesAcrossMatches(
        userId: String,
        matchIds: List<String>,
        query: String
    ): List<Pair<String, ChatMessage>> {
        val q = query.trim()
        if (q.isBlank() || userId.isBlank()) return emptyList()
        val results = mutableListOf<Pair<String, ChatMessage>>()
        for (matchId in matchIds.take(25)) {
            runCatching {
                val snap = db.collection("matches").document(matchId).collection("messages")
                    .whereArrayContains("participantUids", userId)
                    .get()
                    .await()
                val msgs = snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                msgs.filter { msg ->
                    !msg.unsent &&
                        !msg.isDeletedFor(userId) &&
                        !msg.isExpiredAt() &&
                        msg.text.contains(q, ignoreCase = true)
                }.forEach { msg ->
                    results += matchId to msg
                }
            }
        }
        return results.sortedByDescending { it.second.createdAt ?: Timestamp(0, 0) }
    }

    suspend fun updateMatchDisappearingTimer(matchId: String, ttlSeconds: Long): Result<Unit> {
        val allowedTtl = when (ttlSeconds) {
            30L, 300L, 86400L -> ttlSeconds
            else -> 0L
        }
        var docPath = "matches/$matchId"
        return try {
            requireUserId()
            val ref = db.collection("matches").document(matchId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "disappearingTtlSeconds" to allowedTtl,
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

    suspend fun setTypingStatus(matchId: String, isTyping: Boolean): Result<Unit> {
        if (matchId.isBlank()) return Result.success(Unit)
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val docRef = db.collection("matches").document(matchId).collection("typing").document(uid)
        return try {
            docRef.set(
                mapOf(
                    "matchId" to matchId,
                    "userId" to uid,
                    "isTyping" to isTyping,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun startCall(
        matchId: String,
        calleeUid: String,
        participantUids: List<String>,
        callType: String
    ): Result<String> {
        val safeCallType = if (callType == "VOICE") "VOICE" else "VIDEO"
        var docPath = "matches/$matchId/calls"
        return try {
            val uid = requireUserId()
            val sortedParticipants = participantUids.distinct().sorted()
            require(sortedParticipants.size == 2 && uid in sortedParticipants && calleeUid in sortedParticipants && uid != calleeUid) {
                "Invalid call participants."
            }
            val callRef = db.collection("matches").document(matchId).collection("calls").document()
            docPath = callRef.path
            callRef.set(
                mapOf(
                    "matchId" to matchId,
                    "callerUid" to uid,
                    "calleeUid" to calleeUid,
                    "participantUids" to sortedParticipants,
                    "callType" to safeCallType,
                    "status" to "RINGING",
                    "callerMuted" to false,
                    "calleeMuted" to false,
                    "callerCameraOn" to (safeCallType == "VIDEO"),
                    "calleeCameraOn" to (safeCallType == "VIDEO"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(callRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCallSession(
        matchId: String,
        callId: String,
        status: String? = null,
        callerMuted: Boolean? = null,
        calleeMuted: Boolean? = null,
        callerCameraOn: Boolean? = null,
        calleeCameraOn: Boolean? = null
    ): Result<Unit> {
        var docPath = "matches/$matchId/calls/$callId"
        return try {
            requireUserId()
            val callRef = db.collection("matches").document(matchId).collection("calls").document(callId)
            docPath = callRef.path
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (status in setOf("RINGING", "ACTIVE", "DECLINED", "ENDED") && status != null) {
                updates["status"] = status
                if (status == "ENDED" || status == "DECLINED") {
                    updates["endedAtMs"] = System.currentTimeMillis()
                }
            }
            if (callerMuted != null) updates["callerMuted"] = callerMuted
            if (calleeMuted != null) updates["calleeMuted"] = calleeMuted
            if (callerCameraOn != null) updates["callerCameraOn"] = callerCameraOn
            if (calleeCameraOn != null) updates["calleeCameraOn"] = calleeCameraOn
            callRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendCallAudioChunk(
        matchId: String,
        callId: String,
        participantUids: List<String>,
        seq: Long,
        sampleRateHz: Int,
        audioBase64: String
    ): Result<Unit> {
        if (matchId.isBlank() || callId.isBlank() || audioBase64.isBlank() || participantUids.size != 2) {
            return Result.success(Unit)
        }
        var docPath = "matches/$matchId/calls/$callId/audioChunks"
        return try {
            val uid = requireUserId()
            val sortedParticipants = participantUids.distinct().sorted()
            if (sortedParticipants.size != 2 || uid !in sortedParticipants) return Result.success(Unit)
            val chunkId = "${uid}_$seq"
            val chunkRef = db.collection("matches")
                .document(matchId)
                .collection("calls")
                .document(callId)
                .collection("audioChunks")
                .document(chunkId)
            docPath = chunkRef.path
            chunkRef.set(
                mapOf(
                    "matchId" to matchId,
                    "callId" to callId,
                    "senderUid" to uid,
                    "participantUids" to sortedParticipants,
                    "seq" to seq,
                    "sampleRateHz" to sampleRateHz.toLong(),
                    "audioBase64" to audioBase64.take(40_000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendCallVideoFrame(
        matchId: String,
        callId: String,
        participantUids: List<String>,
        seq: Long,
        frameBase64: String
    ): Result<Unit> {
        if (matchId.isBlank() || callId.isBlank() || frameBase64.isBlank() || participantUids.size != 2) {
            return Result.success(Unit)
        }
        var docPath = "matches/$matchId/calls/$callId/videoFrames"
        return try {
            val uid = requireUserId()
            val sortedParticipants = participantUids.distinct().sorted()
            if (sortedParticipants.size != 2 || uid !in sortedParticipants) return Result.success(Unit)
            val frameRef = db.collection("matches")
                .document(matchId)
                .collection("calls")
                .document(callId)
                .collection("videoFrames")
                .document("${uid}_latest")
            docPath = frameRef.path
            frameRef.set(
                mapOf(
                    "matchId" to matchId,
                    "callId" to callId,
                    "senderUid" to uid,
                    "participantUids" to sortedParticipants,
                    "seq" to seq,
                    "frameBase64" to frameBase64.take(160_000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun endCallAndLogMessage(
        matchId: String,
        callId: String,
        participantUids: List<String>,
        callType: String,
        durationSeconds: Long,
        declined: Boolean
    ): Result<Unit> {
        val status = if (declined) "DECLINED" else "ENDED"
        val updateResult = updateCallSession(matchId = matchId, callId = callId, status = status)
        if (updateResult.isFailure) return updateResult
        val label = when {
            declined -> if (callType == "VIDEO") "Missed video call" else "Missed voice call"
            durationSeconds > 0 -> {
                val mins = durationSeconds / 60L
                val secs = durationSeconds % 60L
                val fmt = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                if (callType == "VIDEO") "Video call • $fmt" else "Voice call • $fmt"
            }
            else -> if (callType == "VIDEO") "Video call ended" else "Voice call ended"
        }
        return sendMessage(
            matchId = matchId,
            participantUids = participantUids,
            text = label,
            messageType = "CALL",
            mediaDurationSec = durationSeconds.coerceAtLeast(0L)
        )
    }

    suspend fun unmatch(matchId: String): Result<Unit> {
        var docPath = "matches/$matchId"
        return try {
            requireUserId()
            val ref = db.collection("matches").document(matchId)
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

    suspend fun blockUser(targetUserId: String, reason: String? = null): Result<Unit> {
        var docPath = "blocks"
        return try {
            val uid = requireUserId()
            require(targetUserId.isNotBlank() && targetUserId != uid) {
                "Invalid target user to block."
            }

            val blockId = "${uid}_${targetUserId}"
            val blockRef = db.collection("blocks").document(blockId)
            docPath = blockRef.path

            val alreadyBlocked = db.collection("blocks")
                .whereEqualTo("blockerUid", uid)
                .get()
                .await()
                .documents
                .any { it.id == blockId }

            val sortedParticipants = listOf(uid, targetUserId).sorted()
            val matchId = "${sortedParticipants[0]}_${sortedParticipants[1]}"
            val existingMatch = db.collection("matches")
                .whereArrayContains("participantUids", uid)
                .get()
                .await()
                .documents
                .any { it.id == matchId }

            if (alreadyBlocked && !existingMatch) {
                return Result.success(Unit)
            }

            val batch = db.batch()
            if (!alreadyBlocked) {
                val payload = mutableMapOf<String, Any>(
                    "blockerUid" to uid,
                    "blockedUid" to targetUserId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                val cleanReason = reason?.trim()?.take(200)
                if (!cleanReason.isNullOrBlank()) {
                    payload["reason"] = cleanReason
                }
                batch.set(blockRef, payload)
            }
            if (existingMatch) {
                batch.delete(db.collection("matches").document(matchId))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun unblockUser(targetUserId: String): Result<Unit> {
        var docPath = "blocks"
        return try {
            val uid = requireUserId()
            require(targetUserId.isNotBlank() && targetUserId != uid) {
                "Invalid user to unblock."
            }
            val blockId = "${uid}_${targetUserId}"
            val blockRef = db.collection("blocks").document(blockId)
            docPath = blockRef.path
            blockRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitReport(
        reportedUid: String,
        reason: String,
        details: String = "",
        sourceSurface: String = "PROFILE",
        matchId: String? = null
    ): Result<Unit> {
        var docPath = "reports"
        return try {
            val uid = requireUserId()
            require(reportedUid.isNotBlank() && reportedUid != uid) {
                "Invalid profile to report."
            }
            val cleanReason = reason.trim().take(100)
            require(cleanReason.length >= 2) { "Please choose a reason for reporting." }
            val safeSurface = when (sourceSurface.uppercase()) {
                "DISCOVER", "HOME_FEED", "SEARCH", "CHAT", "PROFILE", "VIDEO_FEED", "STORY", "FEED_POST" -> sourceSurface.uppercase()
                else -> "PROFILE"
            }
            val reportRef = db.collection("reports").document()
            docPath = reportRef.path
            val payload = mutableMapOf<String, Any>(
                "reporterUid" to uid,
                "reportedUid" to reportedUid,
                "reason" to cleanReason,
                "sourceSurface" to safeSurface,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val cleanDetails = details.trim().take(500)
            if (cleanDetails.isNotBlank()) {
                payload["details"] = cleanDetails
            }
            if (!matchId.isNullOrBlank()) {
                payload["matchId"] = matchId
            }
            reportRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitVerification(
        challenge: VerificationChallenge,
        analysis: LivenessAnalysisResult,
        locationVerified: Boolean,
        googleVerified: Boolean
    ): Result<Long> {
        if (!analysis.passed || !analysis.livenessPassed || !analysis.faceMatchPassed) {
            return Result.failure(IllegalArgumentException(analysis.summary))
        }
        if (analysis.comparedPhotoCount < 1 || analysis.bestSimilarityScore < 0.65f) {
            return Result.failure(
                IllegalArgumentException("Selfie must match your uploaded profile photos before verifying.")
            )
        }
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val publicRef = db.collection("publicProfiles").document(uid)
            val publicSnap = publicRef.get().await()
            if (!publicSnap.exists()) {
                return Result.failure(
                    IllegalStateException("Save your profile in the Profile tab before completing verification.")
                )
            }
            val existingPublic = publicSnap.toObject(
                PublicProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )

            val verRef = db.collection("users").document(uid).collection("verifications").document()
            val verId = verRef.id
            docPath = verRef.path

            val score = (
                70 +
                    (if (locationVerified) 12 else 0) +
                    (if (googleVerified) 8 else 0) +
                    (if (analysis.confidence >= 0.75f) 5 else 0) +
                    (if (analysis.bestSimilarityScore >= 0.80f) 5 else 0)
                ).coerceIn(70, 100).toLong()

            val batch = db.batch()
            batch.set(
                verRef,
                mapOf(
                    "userId" to uid,
                    "challengeCode" to challenge.code,
                    "challengePrompt" to challenge.prompt,
                    "faceCount" to analysis.faceCount.toLong(),
                    "faceConfidence" to analysis.confidence.toDouble(),
                    "eyesDistancePx" to analysis.eyesDistancePx.toDouble(),
                    "photoSha256" to analysis.photoSha256,
                    "locationVerified" to locationVerified,
                    "googleVerified" to googleVerified,
                    "comparedPhotoCount" to analysis.comparedPhotoCount.toLong(),
                    "photoSimilarityScore" to analysis.bestSimilarityScore.toDouble().coerceIn(0.65, 1.0),
                    "livenessPassed" to true,
                    "faceMatchPassed" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            val verificationUpdate = mapOf(
                "verificationStatus" to "VERIFIED",
                "verificationId" to verId,
                "verificationScore" to score,
                "verificationHash" to analysis.photoSha256,
                "verifiedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.update(publicRef, verificationUpdate)

            // Propagate verified status to active GeoCells
            if (existingPublic != null && existingPublic.discoverable) {
                val c3 = existingPublic.geohash3
                val c4 = existingPublic.geohash4
                if (c3.length == 3) {
                    val ref3 = db.collection("geoCells").document(c3).collection("profiles").document(uid)
                    if (ref3.get().await().exists()) {
                        batch.update(ref3, verificationUpdate)
                    }
                }
                if (c4.length == 4) {
                    val ref4 = db.collection("geoCells").document(c4).collection("profiles").document(uid)
                    if (ref4.get().await().exists()) {
                        batch.update(ref4, verificationUpdate)
                    }
                }
            }

            batch.commit().await()
            Result.success(score)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private fun buildGeoCellPayload(
        uid: String,
        displayName: String,
        age: Long,
        gender: String,
        bio: String,
        cityLabel: String,
        country: String,
        coarseLat: Double,
        coarseLng: Double,
        interests: List<String>,
        photoDataUrl: String?,
        profilePhotos: List<String>,
        discoverable: Boolean,
        dateOfBirth: String = "",
        profileCompleted: Boolean = true,
        existingPublic: PublicProfile?,
        forceUnverified: Boolean
    ): Map<String, Any> {
        val effectiveVerified = !forceUnverified &&
            existingPublic?.verificationStatus == "VERIFIED" &&
            !existingPublic.verificationId.isNullOrBlank() &&
            !existingPublic.verificationHash.isNullOrBlank() &&
            existingPublic.verifiedAt != null

        val map = mutableMapOf<String, Any>(
            "userId" to uid,
            "displayName" to displayName,
            "age" to age,
            "gender" to gender,
            "bio" to bio,
            "cityLabel" to cityLabel,
            "country" to country,
            "coarseLatitude" to coarseLat,
            "coarseLongitude" to coarseLng,
            "interests" to interests,
            "profilePhotos" to profilePhotos,
            "avatarSeed" to uid.take(40),
            "discoverable" to discoverable,
            "profileCompleted" to profileCompleted,
            "hideOnlineStatus" to (existingPublic?.hideOnlineStatus ?: false),
            "photosVisibility" to (existingPublic?.photosVisibility ?: VisibilityLevel.EVERYONE),
            "videosVisibility" to (existingPublic?.videosVisibility ?: VisibilityLevel.EVERYONE),
            "infoVisibility" to (existingPublic?.infoVisibility ?: VisibilityLevel.EVERYONE),
            "verificationStatus" to if (effectiveVerified) "VERIFIED" else "UNVERIFIED",
            "verificationScore" to if (effectiveVerified) (existingPublic?.verificationScore ?: 70L) else 30L,
            "createdAt" to (existingPublic?.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (dateOfBirth.isNotBlank()) {
            map["dateOfBirth"] = dateOfBirth
        }
        if (!photoDataUrl.isNullOrBlank() && photoDataUrl.length <= 180_000) {
            map["photoDataUrl"] = photoDataUrl
        }
        if (effectiveVerified && existingPublic != null) {
            map["verificationId"] = existingPublic.verificationId!!
            map["verificationHash"] = existingPublic.verificationHash!!
            map["verifiedAt"] = existingPublic.verifiedAt!!
        }
        return map
    }
}
