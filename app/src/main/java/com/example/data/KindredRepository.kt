package com.example.data

import android.content.Context
import com.example.R
import com.example.model.BlockRecord
import com.example.model.ChatMessage
import com.example.model.LivenessAnalysisResult
import com.example.model.MatchRecord
import com.example.model.PrivateUser
import com.example.model.PublicProfile
import com.example.model.SwipeRecord
import com.example.model.UserPresence
import com.example.model.VerificationChallenge
import com.example.model.VerificationRecord
import com.example.notifications.KindredNotificationHelper
import com.example.service.GeoHashMatchingService
import com.example.util.GeoHashUtils
import com.example.util.LocationAndGeoHelper
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class KindredRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val geoService: GeoHashMatchingService = GeoHashMatchingService(db, auth)
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

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

    suspend fun updatePresence(online: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val docRef = db.collection("presence").document(uid)
        return try {
            val payload = mapOf(
                "userId" to uid,
                "online" to online,
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
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
        verifiedOnlyFilter: Boolean
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
        val cleanInterests = interests.map { it.trim().take(40) }.filter { it.isNotBlank() }.distinct().take(8)
        val cleanPhotos = (profilePhotos + listOfNotNull(photoDataUrl))
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length <= 180_000 }
            .distinct()
            .take(4)
        val primaryPhoto = cleanPhotos.firstOrNull()
        val coarseLat = LocationAndGeoHelper.roundToCoarse(latitude.coerceIn(-90.0, 90.0))
        val coarseLng = LocationAndGeoHelper.roundToCoarse(longitude.coerceIn(-180.0, 180.0))
        val geohash = GeoHashUtils.encode(coarseLat, coarseLng, precision = 7)
        val geohash3 = geohash.take(3)
        val geohash4 = geohash.take(4)

        var currentPath = "users"
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

            currentPath = publicRef.path
            if (publicExists) {
                val updatePublic = mutableMapOf<String, Any>(
                    "displayName" to cleanName,
                    "age" to safeAge.toLong(),
                    "gender" to gender,
                    "bio" to cleanBio,
                    "cityLabel" to cleanCity,
                    "coarseLatitude" to coarseLat,
                    "coarseLongitude" to coarseLng,
                    "geohash" to geohash,
                    "geohash3" to geohash3,
                    "geohash4" to geohash4,
                    "interests" to cleanInterests,
                    "profilePhotos" to cleanPhotos,
                    "avatarSeed" to uid.take(40),
                    "discoverable" to discoverable,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
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
                    "coarseLatitude" to coarseLat,
                    "coarseLongitude" to coarseLng,
                    "geohash" to geohash,
                    "geohash3" to geohash3,
                    "geohash4" to geohash4,
                    "interests" to cleanInterests,
                    "profilePhotos" to cleanPhotos,
                    "avatarSeed" to uid.take(40),
                    "discoverable" to discoverable,
                    "verificationStatus" to "UNVERIFIED",
                    "verificationScore" to 30L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
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
                coarseLat = coarseLat,
                coarseLng = coarseLng,
                interests = cleanInterests,
                photoDataUrl = primaryPhoto,
                profilePhotos = cleanPhotos,
                discoverable = discoverable,
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
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
        profilePhotos: List<String>
    ): Result<Unit> {
        val cleanBio = bio.trim().take(400)
        val cleanInterests = interests
            .map { it.trim().take(40) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
        val cleanPhotos = profilePhotos
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length <= 180_000 }
            .distinct()
            .take(4)
        val primaryPhoto = cleanPhotos.firstOrNull()

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
                val safeFallbackName = fallbackDisplayName
                    ?.trim()
                    ?.take(40)
                    ?.takeIf { it.length >= 2 }
                    ?: "Kindred Member"
                return saveProfileAndPreferences(
                    email = email,
                    displayName = safeFallbackName,
                    age = 26,
                    gender = "Woman",
                    bio = cleanBio,
                    cityLabel = "San Francisco, CA",
                    latitude = 37.7749,
                    longitude = -122.4194,
                    interests = cleanInterests,
                    photoDataUrl = primaryPhoto,
                    profilePhotos = cleanPhotos,
                    discoverable = true,
                    interestedIn = "Everyone",
                    minAgePref = 20,
                    maxAgePref = 45,
                    maxDistanceKm = 100,
                    verifiedOnlyFilter = false
                )
            }

            val photosChanged = existingPublic.allProfilePhotos() != cleanPhotos
            val resetVerification = photosChanged && existingPublic.isProfileVerified

            val updatePublic = mutableMapOf<String, Any>(
                "bio" to cleanBio,
                "interests" to cleanInterests,
                "profilePhotos" to cleanPhotos,
                "updatedAt" to FieldValue.serverTimestamp()
            )
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
                displayName = existingPublic.displayName,
                age = existingPublic.age,
                gender = existingPublic.gender,
                bio = cleanBio,
                cityLabel = existingPublic.cityLabel,
                coarseLat = existingPublic.coarseLatitude,
                coarseLng = existingPublic.coarseLongitude,
                interests = cleanInterests,
                photoDataUrl = primaryPhoto,
                profilePhotos = cleanPhotos,
                discoverable = existingPublic.discoverable,
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
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

    suspend fun updateLocationCoordinates(
        latitude: Double,
        longitude: Double,
        cityLabel: String
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
            val coarseLat = LocationAndGeoHelper.roundToCoarse(latitude.coerceIn(-90.0, 90.0))
            val coarseLng = LocationAndGeoHelper.roundToCoarse(longitude.coerceIn(-180.0, 180.0))
            val geohash = GeoHashUtils.encode(coarseLat, coarseLng, precision = 7)
            val geohash3 = geohash.take(3)
            val geohash4 = geohash.take(4)

            val batch = db.batch()
            if (privateExists) {
                docPath = privateRef.path
                batch.update(
                    privateRef,
                    mapOf(
                        "exactLatitude" to latitude.coerceIn(-90.0, 90.0),
                        "exactLongitude" to longitude.coerceIn(-180.0, 180.0),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            if (publicExists && existingPublic != null) {
                docPath = publicRef.path
                batch.update(
                    publicRef,
                    mapOf(
                        "cityLabel" to cleanCity,
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
            if (privateExists || publicExists) {
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

    suspend fun sendMessage(
        matchId: String,
        participantUids: List<String>,
        text: String,
        messageType: String = "TEXT"
    ): Result<Unit> {
        val cleanText = text.trim()
        if (cleanText.isEmpty() || cleanText.length > 1000) {
            return Result.failure(IllegalArgumentException("Message must be between 1 and 1000 characters."))
        }
        var docPath = "matches/$matchId/messages"
        return try {
            val uid = requireUserId()
            require(participantUids.size == 2 && uid in participantUids) {
                "You are not a participant in this match."
            }
            val matchRef = db.collection("matches").document(matchId)
            val msgRef = matchRef.collection("messages").document()
            docPath = msgRef.path

            val batch = db.batch()
            batch.set(
                msgRef,
                mapOf(
                    "matchId" to matchId,
                    "senderId" to uid,
                    "participantUids" to participantUids,
                    "text" to cleanText,
                    "messageType" to if (messageType == "ICEBREAKER") "ICEBREAKER" else "TEXT",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                matchRef,
                mapOf(
                    "lastMessageText" to cleanText.take(300),
                    "lastMessageSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
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
        coarseLat: Double,
        coarseLng: Double,
        interests: List<String>,
        photoDataUrl: String?,
        profilePhotos: List<String>,
        discoverable: Boolean,
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
            "coarseLatitude" to coarseLat,
            "coarseLongitude" to coarseLng,
            "interests" to interests,
            "profilePhotos" to profilePhotos,
            "avatarSeed" to uid.take(40),
            "discoverable" to discoverable,
            "verificationStatus" to if (effectiveVerified) "VERIFIED" else "UNVERIFIED",
            "verificationScore" to if (effectiveVerified) (existingPublic?.verificationScore ?: 70L) else 30L,
            "createdAt" to (existingPublic?.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
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
