package com.example.service

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.model.BlockRecord
import com.example.model.DiscoveryCandidate
import com.example.model.MatchingCriteria
import com.example.model.PrivateUser
import com.example.model.PublicProfile
import com.example.model.SwipeRecord
import com.example.util.GeoHashUtils
import com.example.util.LocationAndGeoHelper
import com.example.util.ResolvedDeviceLocation
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

data class UserLocationAndPreferences(
    val userId: String,
    val privateUser: PrivateUser?,
    val publicProfile: PublicProfile?,
    val effectiveLatitude: Double?,
    val effectiveLongitude: Double?,
    val interestedIn: String,
    val minAge: Int,
    val maxAge: Int,
    val maxDistanceKm: Double,
    val verifiedOnly: Boolean,
    val myInterests: List<String>
)

data class MatchedFeedResult(
    val criteria: MatchingCriteria,
    val queriedGeoCells: List<String>,
    val candidates: List<DiscoveryCandidate>
)

/**
 * Firestore-backed matching logic service that loads the authenticated user's stored location
 * and dating preferences (/users/{uid} and /publicProfiles/{uid}), excludes swiped/blocked users,
 * queries discoverable candidates (/geoCells/{cellId}/profiles + /publicProfiles), and ranks
 * the resulting feed by geodesic distance, preference alignment, shared interests, and trust.
 */
class MatchingLogicService(
    private val db: FirebaseFirestore,
    private val geoService: GeoHashMatchingService = GeoHashMatchingService(db),
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before running matching.")
    }

    private fun handleStreamError(
        error: Throwable,
        op: OperationType,
        path: String,
        expectedUid: String
    ): Nothing {
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

    /**
     * Observes the signed-in user's stored location and matching preferences from Firestore
     * (/users/{userId} + /publicProfiles/{userId}).
     */
    fun observeUserLocationAndPreferences(userId: String): Flow<UserLocationAndPreferences> {
        val privateFlow = flow {
            val path = "users/$userId"
            emitAll(
                db.collection("users").document(userId)
                    .snapshots()
                    .map { snap ->
                        if (snap.exists()) {
                            snap.toObject(
                                PrivateUser::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        } else null
                    }
                    .catch { handleStreamError(it, OperationType.GET, path, userId) }
            )
        }

        val publicFlow = flow {
            val path = "publicProfiles/$userId"
            emitAll(
                db.collection("publicProfiles").document(userId)
                    .snapshots()
                    .map { snap ->
                        if (snap.exists()) {
                            snap.toObject(
                                PublicProfile::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        } else null
                    }
                    .catch { handleStreamError(it, OperationType.GET, path, userId) }
            )
        }

        return combine(privateFlow, publicFlow) { priv, pub ->
            resolveUserPreferences(userId = userId, privateUser = priv, myProfile = pub, liveLocation = null)
        }
    }

    /**
     * One-shot Firestore fetch that retrieves the current user's location and preferences,
     * queries nearby discoverable users from GeoHash cells and /publicProfiles, removes swiped
     * and blocked profiles, and returns a ranked list of [DiscoveryCandidate] items for the feed.
     */
    suspend fun fetchFilteredMatches(
        liveLocationOverride: ResolvedDeviceLocation? = null,
        extraProfiles: List<PublicProfile> = emptyList()
    ): Result<MatchedFeedResult> {
        var activePath = "users"
        return try {
            val uid = requireUserId()

            val (privateSnap, myPublicSnap, swipesSnap, iBlockedSnap, blockedMeSnap, publicSnap) = coroutineScope {
                val privDeferred = async {
                    db.collection("users").document(uid).get().await()
                }
                val pubDeferred = async {
                    db.collection("publicProfiles").document(uid).get().await()
                }
                val swipesDeferred = async {
                    db.collection("swipes").whereEqualTo("fromUserId", uid).get().await()
                }
                val iBlockedDeferred = async {
                    db.collection("blocks").whereEqualTo("blockerUid", uid).get().await()
                }
                val blockedMeDeferred = async {
                    db.collection("blocks").whereEqualTo("blockedUid", uid).get().await()
                }
                val discoverableDeferred = async {
                    db.collection("publicProfiles").whereEqualTo("discoverable", true).get().await()
                }
                LoadedFirestoreMatchingInputs(
                    privateSnap = privDeferred.await(),
                    myPublicSnap = pubDeferred.await(),
                    swipesSnap = swipesDeferred.await(),
                    iBlockedSnap = iBlockedDeferred.await(),
                    blockedMeSnap = blockedMeDeferred.await(),
                    discoverableSnap = discoverableDeferred.await()
                )
            }

            activePath = "users/$uid"
            val privateUser = if (privateSnap.exists()) {
                privateSnap.toObject(PrivateUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null

            val myProfile = if (myPublicSnap.exists()) {
                myPublicSnap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null

            val prefs = resolveUserPreferences(
                userId = uid,
                privateUser = privateUser,
                myProfile = myProfile,
                liveLocation = liveLocationOverride
            )

            val swipedIds = swipesSnap.toObjects(
                SwipeRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).map { it.toUserId }.toSet()

            val blockedByMe = iBlockedSnap.toObjects(
                BlockRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).map { it.blockedUid }
            val blockedMe = blockedMeSnap.toObjects(
                BlockRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).map { it.blockerUid }
            val blockedIds = (blockedByMe + blockedMe).toSet()

            val allDiscoverable = publicSnap.toObjects(
                PublicProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )

            val geoProfiles: List<PublicProfile>
            val queriedCells: List<String>
            val centerGeoHash: String

            if (prefs.effectiveLatitude != null && prefs.effectiveLongitude != null) {
                activePath = "geoCells"
                val proximityResult = geoService.findNearbyMatches(
                    latitude = prefs.effectiveLatitude,
                    longitude = prefs.effectiveLongitude,
                    maxDistanceKm = prefs.maxDistanceKm,
                    minAge = prefs.minAge,
                    maxAge = prefs.maxAge,
                    interestedIn = prefs.interestedIn,
                    verifiedOnly = prefs.verifiedOnly,
                    myInterests = prefs.myInterests,
                    excludedUserIds = swipedIds,
                    blockedUserIds = blockedIds
                ).getOrNull()

                geoProfiles = proximityResult?.candidates?.map { it.profile }.orEmpty()
                queriedCells = proximityResult?.queriedCells.orEmpty()
                centerGeoHash = proximityResult?.centerGeoHash
                    ?: GeoHashUtils.encode(prefs.effectiveLatitude, prefs.effectiveLongitude, 7)
            } else {
                geoProfiles = emptyList()
                queriedCells = emptyList()
                centerGeoHash = ""
            }

            val mergedProfiles = LinkedHashMap<String, PublicProfile>().apply {
                geoProfiles.forEach { put(it.userId, it) }
                allDiscoverable.forEach { if (!containsKey(it.userId)) put(it.userId, it) }
                extraProfiles.forEach { if (!containsKey(it.userId)) put(it.userId, it) }
            }.values.toList()

            val criteria = MatchingCriteria(
                currentUserId = uid,
                latitude = prefs.effectiveLatitude,
                longitude = prefs.effectiveLongitude,
                geoHash = centerGeoHash,
                maxDistanceKm = prefs.maxDistanceKm,
                minAge = prefs.minAge,
                maxAge = prefs.maxAge,
                interestedIn = prefs.interestedIn,
                verifiedOnly = prefs.verifiedOnly,
                myInterests = prefs.myInterests,
                excludedUserIds = swipedIds,
                blockedUserIds = blockedIds
            )

            val ranked = filterAndRankCandidates(mergedProfiles, criteria)
            Result.success(
                MatchedFeedResult(
                    criteria = criteria,
                    queriedGeoCells = queriedCells,
                    candidates = ranked
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, activePath)
            Result.failure(e)
        }
    }

    /**
     * Filters and ranks [rawProfiles] against the user's Firestore location & dating preferences.
     */
    fun filterAndRankCandidates(
        rawProfiles: List<PublicProfile>,
        criteria: MatchingCriteria
    ): List<DiscoveryCandidate> = Companion.filterAndRankCandidates(rawProfiles, criteria)

    private data class LoadedFirestoreMatchingInputs(
        val privateSnap: DocumentSnapshot,
        val myPublicSnap: DocumentSnapshot,
        val swipesSnap: com.google.firebase.firestore.QuerySnapshot,
        val iBlockedSnap: com.google.firebase.firestore.QuerySnapshot,
        val blockedMeSnap: com.google.firebase.firestore.QuerySnapshot,
        val discoverableSnap: com.google.firebase.firestore.QuerySnapshot
    )

    companion object {
        fun resolveUserPreferences(
            userId: String,
            privateUser: PrivateUser?,
            myProfile: PublicProfile?,
            liveLocation: ResolvedDeviceLocation? = null
        ): UserLocationAndPreferences {
            val effectiveLat = liveLocation?.exactLatitude
                ?: privateUser?.exactLatitude
                ?: myProfile?.coarseLatitude
            val effectiveLng = liveLocation?.exactLongitude
                ?: privateUser?.exactLongitude
                ?: myProfile?.coarseLongitude
            val minAge = (privateUser?.minAgePref ?: 18L).toInt().coerceIn(18, 99)
            val maxAge = (privateUser?.maxAgePref ?: 99L).toInt().coerceIn(minAge, 99)
            val maxDistanceKm = (privateUser?.maxDistanceKm ?: 100L).toDouble().coerceIn(5.0, 500.0)

            return UserLocationAndPreferences(
                userId = userId,
                privateUser = privateUser,
                publicProfile = myProfile,
                effectiveLatitude = effectiveLat,
                effectiveLongitude = effectiveLng,
                interestedIn = privateUser?.interestedIn ?: "Everyone",
                minAge = minAge,
                maxAge = maxAge,
                maxDistanceKm = maxDistanceKm,
                verifiedOnly = privateUser?.verifiedOnlyFilter ?: false,
                myInterests = myProfile?.interests.orEmpty()
            )
        }

        fun matchesGenderPreference(candidateGender: String, interestedIn: String): Boolean {
            return when (interestedIn.trim()) {
                "Women" -> candidateGender.equals("Woman", ignoreCase = true)
                "Men" -> candidateGender.equals("Man", ignoreCase = true)
                "Non-binary" -> candidateGender.equals("Non-binary", ignoreCase = true)
                else -> true
            }
        }

        fun filterByInterestTag(
            candidates: List<DiscoveryCandidate>,
            interestTag: String?
        ): List<DiscoveryCandidate> {
            val tag = interestTag?.trim().orEmpty()
            if (tag.isBlank() || tag.equals("All", ignoreCase = true)) {
                return candidates
            }
            return candidates.filter { candidate ->
                candidate.profile.interests.any { it.equals(tag, ignoreCase = true) }
            }
        }

        fun filterAndRankCandidates(
            rawProfiles: List<PublicProfile>,
            criteria: MatchingCriteria
        ): List<DiscoveryCandidate> {
            val myInterestByLower = criteria.myInterests
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .associateBy { it.lowercase() }

            val myLat = criteria.latitude
            val myLng = criteria.longitude
            val resolvedGeoHash = criteria.geoHash.ifBlank {
                if (myLat != null && myLng != null) GeoHashUtils.encode(myLat, myLng, 7) else ""
            }

            return rawProfiles
                .asSequence()
                .filter { it.userId.isNotBlank() && it.userId != criteria.currentUserId }
                .filter { it.discoverable }
                .filter { it.userId !in criteria.excludedUserIds }
                .filter { it.userId !in criteria.blockedUserIds }
                .filter { it.age in criteria.minAge..criteria.maxAge }
                .filter { matchesGenderPreference(it.gender, criteria.interestedIn) }
                .filter { !criteria.verifiedOnly || it.isProfileVerified }
                .map { candidate ->
                    val distKm = if (myLat != null && myLng != null) {
                        LocationAndGeoHelper.calculateDistanceKm(
                            myLat,
                            myLng,
                            candidate.coarseLatitude,
                            candidate.coarseLongitude
                        )
                    } else {
                        0.0
                    }

                    val candidateHash = candidate.geohash.ifBlank {
                        GeoHashUtils.encode(candidate.coarseLatitude, candidate.coarseLongitude, 7)
                    }
                    val sharedPrefix = if (resolvedGeoHash.isNotBlank()) {
                        GeoHashUtils.sharedPrefixLength(resolvedGeoHash, candidateHash)
                    } else {
                        0
                    }

                    val sharedInterests = candidate.interests
                        .map { it.trim() }
                        .filter { it.lowercase() in myInterestByLower }
                        .distinctBy { it.lowercase() }

                    val safeRadius = criteria.maxDistanceKm.coerceAtLeast(1.0)
                    val distanceRatio = (1.0 - (distKm / safeRadius)).coerceIn(0.0, 1.0)
                    val distancePoints = (distanceRatio * 50.0).roundToInt()

                    val interestPoints = if (myInterestByLower.isEmpty()) {
                        10
                    } else {
                        sharedInterests.size.coerceAtMost(5) * 5
                    }

                    val verifiedPoints = if (candidate.isProfileVerified) 15 else 0
                    val geoCellPoints = when {
                        sharedPrefix >= 5 -> 10
                        sharedPrefix == 4 -> 8
                        sharedPrefix == 3 -> 5
                        else -> 2
                    }

                    val totalScore = (distancePoints + interestPoints + verifiedPoints + geoCellPoints)
                        .coerceIn(1, 100)

                    val reasons = buildList {
                        if (myLat != null && myLng != null) {
                            when {
                                distKm < 5.0 -> add("Nearby (< 5 km)")
                                distKm <= 20.0 -> add("${distKm.roundToInt()} km away")
                            }
                        }
                        if (sharedInterests.isNotEmpty()) {
                            add("${sharedInterests.size} shared ${if (sharedInterests.size == 1) "interest" else "interests"}")
                        }
                        if (candidate.isProfileVerified) {
                            add("Verified profile")
                        }
                    }

                    DiscoveryCandidate(
                        profile = candidate.copy(geohash = candidateHash),
                        distanceKm = distKm,
                        sharedGeoPrefixLength = sharedPrefix,
                        proximityMatchScore = totalScore,
                        sharedInterests = sharedInterests,
                        matchReasons = reasons,
                        distanceScore = distancePoints,
                        interestScore = interestPoints,
                        trustScore = verifiedPoints
                    )
                }
                .filter { myLat == null || it.distanceKm <= criteria.maxDistanceKm }
                .sortedWith(
                    compareByDescending<DiscoveryCandidate> { it.proximityMatchScore }
                        .thenBy { it.distanceKm }
                )
                .toList()
        }
    }
}
