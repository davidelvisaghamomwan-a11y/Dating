package com.example.service

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.model.DiscoveryCandidate
import com.example.model.PublicProfile
import com.example.util.GeoHashUtils
import com.example.util.LocationAndGeoHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

data class GeoHashSpatialSnapshot(
    val centerGeoHash: String,
    val queryPrecision: Int,
    val queriedCells: List<String>,
    val profiles: List<PublicProfile>
)

data class GeoProximitySearchResult(
    val centerGeoHash: String,
    val queriedCells: List<String>,
    val candidates: List<DiscoveryCandidate>
)

/**
 * Firestore-backed spatial proximity matching service using GeoHash cell partitioning
 * (/geoCells/{cellId}/profiles/{userId}) and Haversine refinement.
 */
class GeoHashMatchingService(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before querying GeoHash cells.")
    }

    /**
     * Opens real-time Firestore snapshot listeners across the 9 GeoHash cells (center + 8 neighbors)
     * covering [latitude], [longitude] at the precision selected for [maxDistanceKm].
     */
    fun observeSpatialNeighborhood(
        userId: String,
        latitude: Double,
        longitude: Double,
        maxDistanceKm: Double
    ): Flow<GeoHashSpatialSnapshot> {
        val centerGeoHash = GeoHashUtils.encode(latitude, longitude, precision = 7)
        val precision = GeoHashUtils.precisionForRadiusKm(maxDistanceKm)
        val centerCell = centerGeoHash.take(precision)
        val cells = GeoHashUtils.neighborhood(centerCell)

        if (cells.isEmpty()) {
            return flowOf(
                GeoHashSpatialSnapshot(
                    centerGeoHash = centerGeoHash,
                    queryPrecision = precision,
                    queriedCells = emptyList(),
                    profiles = emptyList()
                )
            )
        }

        val cellFlows = cells.map { cellId ->
            val path = "geoCells/$cellId/profiles"
            flow {
                emitAll(
                    db.collection("geoCells")
                        .document(cellId)
                        .collection("profiles")
                        .whereEqualTo("discoverable", true)
                        .snapshots()
                        .map { snap ->
                            snap.toObjects(
                                PublicProfile::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        }
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            val exceptionToLog = firestoreError ?: (error as? Exception)
                            if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                                handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                            }
                            throw error
                        }
                )
            }
        }

        return combine(cellFlows) { cellLists ->
            val deduplicated = LinkedHashMap<String, PublicProfile>()
            for (list in cellLists) {
                for (profile in list) {
                    if (profile.userId.isNotBlank()) {
                        deduplicated[profile.userId] = profile
                    }
                }
            }
            GeoHashSpatialSnapshot(
                centerGeoHash = centerGeoHash,
                queryPrecision = precision,
                queriedCells = cells,
                profiles = deduplicated.values.toList()
            )
        }
    }

    /**
     * One-shot GeoHash proximity search against Firestore /geoCells/{cellId}/profiles.
     */
    suspend fun findNearbyMatches(
        latitude: Double,
        longitude: Double,
        maxDistanceKm: Double,
        minAge: Int,
        maxAge: Int,
        interestedIn: String,
        verifiedOnly: Boolean,
        myInterests: List<String>,
        excludedUserIds: Set<String>,
        blockedUserIds: Set<String> = emptySet()
    ): Result<GeoProximitySearchResult> {
        val centerGeoHash = GeoHashUtils.encode(latitude, longitude, precision = 7)
        val precision = GeoHashUtils.precisionForRadiusKm(maxDistanceKm)
        val cells = GeoHashUtils.neighborhood(centerGeoHash.take(precision))
        var activePath = "geoCells"

        return try {
            val uid = requireUserId()
            val snapshots = coroutineScope {
                cells.map { cellId ->
                    async {
                        val path = "geoCells/$cellId/profiles"
                        activePath = path
                        db.collection("geoCells")
                            .document(cellId)
                            .collection("profiles")
                            .whereEqualTo("discoverable", true)
                            .get()
                            .await()
                            .toObjects(
                                PublicProfile::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                    }
                }.awaitAll()
            }

            val deduplicated = LinkedHashMap<String, PublicProfile>()
            snapshots.flatten().forEach { profile ->
                if (profile.userId.isNotBlank()) {
                    deduplicated[profile.userId] = profile
                }
            }

            val candidates = rankAndFilterCandidates(
                rawProfiles = deduplicated.values.toList(),
                currentUserId = uid,
                myLat = latitude,
                myLng = longitude,
                myGeoHash = centerGeoHash,
                maxDistanceKm = maxDistanceKm,
                minAge = minAge,
                maxAge = maxAge,
                interestedIn = interestedIn,
                verifiedOnly = verifiedOnly,
                myInterests = myInterests,
                excludedUserIds = excludedUserIds,
                blockedUserIds = blockedUserIds
            )

            Result.success(
                GeoProximitySearchResult(
                    centerGeoHash = centerGeoHash,
                    queriedCells = cells,
                    candidates = candidates
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
     * Appends atomic GeoCell index writes/deletes onto [batch] for both precision-3 and precision-4
     * GeoHash buckets so wide and local proximity queries remain up to date.
     */
    fun stageGeoCellIndexMutations(
        batch: WriteBatch,
        userId: String,
        geohash: String,
        discoverable: Boolean,
        previousGeoHash3: String?,
        previousGeoHash4: String?,
        baseProfileFields: Map<String, Any>,
        createMissingCells: Boolean = true
    ) {
        val cell3 = geohash.take(3)
        val cell4 = geohash.take(4)

        if (!previousGeoHash3.isNullOrBlank() && (previousGeoHash3 != cell3 || !discoverable)) {
            batch.delete(db.collection("geoCells").document(previousGeoHash3).collection("profiles").document(userId))
        }
        if (!previousGeoHash4.isNullOrBlank() && (previousGeoHash4 != cell4 || !discoverable)) {
            batch.delete(db.collection("geoCells").document(previousGeoHash4).collection("profiles").document(userId))
        }

        if (discoverable && cell3.length == 3 && cell4.length == 4) {
            if (createMissingCells) {
                val payload3 = baseProfileFields.toMutableMap().apply {
                    put("geohash", geohash)
                    put("cellPrecision", 3L)
                }
                val payload4 = baseProfileFields.toMutableMap().apply {
                    put("geohash", geohash)
                    put("cellPrecision", 4L)
                }
                batch.set(db.collection("geoCells").document(cell3).collection("profiles").document(userId), payload3)
                batch.set(db.collection("geoCells").document(cell4).collection("profiles").document(userId), payload4)
            }
        }
    }

    fun rankAndFilterCandidates(
        rawProfiles: List<PublicProfile>,
        currentUserId: String,
        myLat: Double?,
        myLng: Double?,
        myGeoHash: String,
        maxDistanceKm: Double,
        minAge: Int,
        maxAge: Int,
        interestedIn: String,
        verifiedOnly: Boolean,
        myInterests: List<String>,
        excludedUserIds: Set<String>,
        blockedUserIds: Set<String> = emptySet()
    ): List<DiscoveryCandidate> = Companion.rankAndFilterCandidates(
        rawProfiles = rawProfiles,
        currentUserId = currentUserId,
        myLat = myLat,
        myLng = myLng,
        myGeoHash = myGeoHash,
        maxDistanceKm = maxDistanceKm,
        minAge = minAge,
        maxAge = maxAge,
        interestedIn = interestedIn,
        verifiedOnly = verifiedOnly,
        myInterests = myInterests,
        excludedUserIds = excludedUserIds,
        blockedUserIds = blockedUserIds
    )

    companion object {
        fun rankAndFilterCandidates(
            rawProfiles: List<PublicProfile>,
            currentUserId: String,
            myLat: Double?,
            myLng: Double?,
            myGeoHash: String,
            maxDistanceKm: Double,
            minAge: Int,
            maxAge: Int,
            interestedIn: String,
            verifiedOnly: Boolean,
            myInterests: List<String>,
            excludedUserIds: Set<String>,
            blockedUserIds: Set<String> = emptySet()
        ): List<DiscoveryCandidate> {
            val myInterestSet = myInterests.map { it.lowercase() }.toSet()

            return rawProfiles
                .asSequence()
                .filter { it.userId != currentUserId }
                .filter { it.userId !in excludedUserIds }
                .filter { it.userId !in blockedUserIds }
                .filter { it.age in minAge..maxAge }
                .filter { candidate ->
                    when (interestedIn) {
                        "Women" -> candidate.gender == "Woman"
                        "Men" -> candidate.gender == "Man"
                        "Non-binary" -> candidate.gender == "Non-binary"
                        else -> true
                    }
                }
                .filter { !verifiedOnly || it.isProfileVerified }
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
                    val sharedPrefix = if (myGeoHash.isNotBlank()) {
                        GeoHashUtils.sharedPrefixLength(myGeoHash, candidateHash)
                    } else {
                        0
                    }
                    val proximityScore = computeProximityMatchScore(
                        distanceKm = distKm,
                        maxDistanceKm = maxDistanceKm,
                        sharedGeoPrefix = sharedPrefix,
                        candidateInterests = candidate.interests,
                        myInterestSet = myInterestSet,
                        isVerified = candidate.isProfileVerified
                    )
                    DiscoveryCandidate(
                        profile = candidate.copy(geohash = candidateHash),
                        distanceKm = distKm,
                        sharedGeoPrefixLength = sharedPrefix,
                        proximityMatchScore = proximityScore
                    )
                }
                .filter { myLat == null || it.distanceKm <= maxDistanceKm }
                .sortedWith(
                    compareByDescending<DiscoveryCandidate> { it.proximityMatchScore }
                        .thenBy { it.distanceKm }
                )
                .toList()
        }

        private fun computeProximityMatchScore(
            distanceKm: Double,
            maxDistanceKm: Double,
            sharedGeoPrefix: Int,
            candidateInterests: List<String>,
            myInterestSet: Set<String>,
            isVerified: Boolean
        ): Int {
            val safeRadius = maxDistanceKm.coerceAtLeast(1.0)
            val distanceRatio = (1.0 - (distanceKm / safeRadius)).coerceIn(0.0, 1.0)
            val distancePoints = (distanceRatio * 50.0).roundToInt()

            val interestPoints = if (myInterestSet.isEmpty()) {
                10
            } else {
                val sharedCount = candidateInterests.count { it.lowercase() in myInterestSet }
                (sharedCount.coerceAtMost(5) * 5)
            }

            val verifiedPoints = if (isVerified) 15 else 0
            val geoCellPoints = when {
                sharedGeoPrefix >= 5 -> 10
                sharedGeoPrefix == 4 -> 8
                sharedGeoPrefix == 3 -> 5
                else -> 2
            }

            return (distancePoints + interestPoints + verifiedPoints + geoCellPoints).coerceIn(1, 100)
        }
    }
}
