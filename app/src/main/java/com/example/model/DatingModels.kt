package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class PrivateUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String? = null,
    val interestedIn: String = "Everyone",
    val minAgePref: Long = 20L,
    val maxAgePref: Long = 45L,
    val maxDistanceKm: Long = 50L,
    val verifiedOnlyFilter: Boolean = false,
    val exactLatitude: Double? = null,
    val exactLongitude: Double? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val age: Long = 25L,
    val gender: String = "Woman",
    val bio: String = "",
    val cityLabel: String = "San Francisco",
    val coarseLatitude: Double = 37.77,
    val coarseLongitude: Double = -122.42,
    val geohash: String = "",
    val geohash3: String = "",
    val geohash4: String = "",
    val interests: List<String> = emptyList(),
    val photoDataUrl: String? = null,
    val profilePhotos: List<String> = emptyList(),
    val avatarSeed: String = "kindred",
    val discoverable: Boolean = true,
    val verificationStatus: String = "UNVERIFIED",
    val verificationId: String? = null,
    val verificationScore: Long = 30L,
    val verificationHash: String? = null,
    val verifiedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isProfileVerified: Boolean
        get() = verificationStatus == "VERIFIED"

    fun allProfilePhotos(): List<String> =
        (listOfNotNull(photoDataUrl) + profilePhotos)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)
}

@Keep
@IgnoreExtraProperties
data class VerificationRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val challengeCode: String = "",
    val challengePrompt: String = "",
    val faceCount: Long = 0L,
    val faceConfidence: Double = 0.0,
    val eyesDistancePx: Double = 0.0,
    val photoSha256: String = "",
    val locationVerified: Boolean = false,
    val googleVerified: Boolean = true,
    val comparedPhotoCount: Long = 0L,
    val photoSimilarityScore: Double = 0.0,
    val livenessPassed: Boolean = false,
    val faceMatchPassed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SwipeRecord(
    @DocumentId val id: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val direction: String = "PASS",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MatchRecord(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val createdBy: String = "",
    val lastMessageText: String? = null,
    val lastMessageSenderId: String? = null,
    val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val matchId: String = "",
    val senderId: String = "",
    val participantUids: List<String> = emptyList(),
    val text: String = "",
    val messageType: String = "TEXT",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserPresence(
    @DocumentId val id: String = "",
    val userId: String = "",
    val online: Boolean = false,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BlockRecord(
    @DocumentId val id: String = "",
    val blockerUid: String = "",
    val blockedUid: String = "",
    val reason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FcmTokenRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val token: String = "",
    val platform: String = "android",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class BlockedUserItem(
    val blockedUid: String,
    val displayName: String,
    val reason: String? = null,
    val isDemo: Boolean = false
)

data class DiscoveryCandidate(
    val profile: PublicProfile,
    val distanceKm: Double,
    val sharedGeoPrefixLength: Int = 0,
    val proximityMatchScore: Int = 0,
    val sharedInterests: List<String> = emptyList(),
    val matchReasons: List<String> = emptyList(),
    val distanceScore: Int = 0,
    val interestScore: Int = 0,
    val trustScore: Int = 0
)

data class MatchingCriteria(
    val currentUserId: String,
    val latitude: Double?,
    val longitude: Double?,
    val geoHash: String = "",
    val maxDistanceKm: Double = 50.0,
    val minAge: Int = 18,
    val maxAge: Int = 99,
    val interestedIn: String = "Everyone",
    val verifiedOnly: Boolean = false,
    val myInterests: List<String> = emptyList(),
    val excludedUserIds: Set<String> = emptySet(),
    val blockedUserIds: Set<String> = emptySet()
)

data class MatchThreadItem(
    val match: MatchRecord,
    val partnerId: String,
    val partnerProfile: PublicProfile?,
    val distanceKm: Double?
)

data class VerificationChallenge(
    val code: String,
    val prompt: String,
    val hint: String
)

data class ProfilePhotoComparison(
    val photoIndex: Int,
    val photoDataUrl: String,
    val faceDetected: Boolean,
    val similarityScore: Float,
    val geometryScore: Float,
    val structureScore: Float,
    val toneScore: Float,
    val matched: Boolean,
    val reason: String
)

data class LivenessAnalysisResult(
    val passed: Boolean,
    val livenessPassed: Boolean = false,
    val faceMatchPassed: Boolean = false,
    val faceCount: Int,
    val confidence: Float,
    val eyesDistancePx: Float,
    val photoSha256: String,
    val comparedPhotoCount: Int = 0,
    val bestSimilarityScore: Float = 0f,
    val averageSimilarityScore: Float = 0f,
    val perPhotoComparisons: List<ProfilePhotoComparison> = emptyList(),
    val capturedSelfiePreviewDataUrl: String? = null,
    val summary: String
)

object KindredConstants {
    val INTEREST_OPTIONS = listOf(
        "Coffee", "Hiking", "Architecture", "Live Music",
        "Cooking", "Photography", "Travel", "Art Museums",
        "Running", "Vinyl Records", "Cinema", "Bookstores",
        "Cycling", "Cooking Class", "Design", "Botany"
    )

    val GENDER_OPTIONS = listOf("Woman", "Man", "Non-binary")
    val INTERESTED_IN_OPTIONS = listOf("Everyone", "Women", "Men", "Non-binary")

    val VERIFICATION_CHALLENGES = listOf(
        VerificationChallenge(
            code = "SMILE_CENTER",
            prompt = "Look directly into the front camera with a natural smile",
            hint = "Keep your face centered inside the ring with even lighting."
        ),
        VerificationChallenge(
            code = "EYES_LEVEL",
            prompt = "Hold your phone at eye level and face straight forward",
            hint = "Remove sunglasses or hats so both eyes are clearly visible."
        ),
        VerificationChallenge(
            code = "SLIGHT_TILT",
            prompt = "Frame your face clearly and hold steady for liveness capture",
            hint = "Ensure only your face is in the frame during capture."
        )
    )
}
