package com.example.model

import androidx.annotation.Keep
import com.example.util.CountryFlagHelper
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
    val hideOnlineStatus: Boolean = false,
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
    val dateOfBirth: String = "",
    val gender: String = "Woman",
    val bio: String = "",
    val cityLabel: String = "San Francisco",
    val country: String = "United States",
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
    val profileCompleted: Boolean = true,
    val suspended: Boolean = false,
    val hideOnlineStatus: Boolean = false,
    val photosVisibility: String = VisibilityLevel.EVERYONE,
    val videosVisibility: String = VisibilityLevel.EVERYONE,
    val infoVisibility: String = VisibilityLevel.EVERYONE,
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
            .take(KindredConstants.MAX_PROFILE_PHOTOS)

    fun flagEmoji(): String = CountryFlagHelper.flagEmojiForCountry(country)

    fun formattedNameAgeWithFlag(): String =
        CountryFlagHelper.nameAgeWithFlag(displayName, age, country)

    fun formattedLocation(): String {
        val cleanCity = cityLabel.trim()
        val cleanCountry = country.trim()
        return when {
            cleanCity.isNotBlank() && cleanCountry.isNotBlank() &&
                !cleanCity.contains(cleanCountry, ignoreCase = true) -> "$cleanCity, $cleanCountry"
            cleanCity.isNotBlank() -> cleanCity
            cleanCountry.isNotBlank() -> cleanCountry
            else -> "Nearby"
        }
    }
}

object VisibilityLevel {
    const val EVERYONE = "EVERYONE"
    const val MATCHES_AND_FRIENDS = "MATCHES_AND_FRIENDS"
    const val FRIENDS_ONLY = "FRIENDS_ONLY"

    fun labelFor(level: String): String = when (level) {
        FRIENDS_ONLY -> "Friends Only"
        MATCHES_AND_FRIENDS -> "Matches & Friends"
        else -> "Everyone"
    }
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
    val conversationType: String = "MATCH",
    val lastMessageText: String? = null,
    val lastMessageSenderId: String? = null,
    val lastMessageAt: Timestamp? = null,
    val lastMessageReadByUids: List<String> = emptyList(),
    val disappearingTtlSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isUnreadFor(uid: String): Boolean {
        if (uid.isBlank()) return false
        val sender = lastMessageSenderId
        if (lastMessageText.isNullOrBlank() || sender.isNullOrBlank() || sender == uid) return false
        return uid !in lastMessageReadByUids
    }
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val matchId: String = "",
    val senderId: String = "",
    val participantUids: List<String> = emptyList(),
    val text: String = "",
    val messageType: String = "TEXT",
    val mediaDataUrl: String? = null,
    val thumbnailDataUrl: String? = null,
    val mediaMimeType: String? = null,
    val mediaChunkCount: Long = 0L,
    val mediaByteSize: Long = 0L,
    val mediaGifId: String? = null,
    val mediaDurationSec: Long = 0L,
    val replyToMessageId: String? = null,
    val replyToSenderId: String? = null,
    val replyToText: String? = null,
    val replyToType: String? = null,
    val deletedForUids: List<String> = emptyList(),
    val unsent: Boolean = false,
    val unsentAt: Timestamp? = null,
    val readByUids: List<String> = emptyList(),
    val readAt: Timestamp? = null,
    val disappearingSeconds: Long = 0L,
    val viewOnce: Boolean = false,
    val openedByRecipient: Boolean = false,
    val expiresAtMs: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun isReadBy(uid: String): Boolean = uid.isNotBlank() && uid in readByUids

    fun isDeletedFor(uid: String): Boolean = uid.isNotBlank() && uid in deletedForUids

    fun isExpiredAt(nowMs: Long = System.currentTimeMillis()): Boolean {
        val exp = expiresAtMs
        if (exp != null && exp > 0L && nowMs >= exp) return true
        if (disappearingSeconds > 0L && createdAt != null) {
            val computedExpiry = createdAt.toDate().time + disappearingSeconds * 1000L
            if (nowMs >= computedExpiry) return true
        }
        return false
    }

    fun remainingSecondsAt(nowMs: Long = System.currentTimeMillis()): Long? {
        val targetMs = when {
            expiresAtMs != null && expiresAtMs > 0L -> expiresAtMs
            disappearingSeconds > 0L && createdAt != null ->
                createdAt.toDate().time + disappearingSeconds * 1000L
            else -> null
        } ?: return null
        return ((targetMs - nowMs).coerceAtLeast(0L) + 999L) / 1000L
    }
}

@Keep
@IgnoreExtraProperties
data class TypingStatus(
    @DocumentId val id: String = "",
    val matchId: String = "",
    val userId: String = "",
    val isTyping: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val matchId: String = "",
    val callerUid: String = "",
    val calleeUid: String = "",
    val participantUids: List<String> = emptyList(),
    val callType: String = "VIDEO",
    val status: String = "RINGING",
    val callerMuted: Boolean = false,
    val calleeMuted: Boolean = false,
    val callerCameraOn: Boolean = true,
    val calleeCameraOn: Boolean = true,
    val endedAtMs: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallAudioChunk(
    @DocumentId val id: String = "",
    val matchId: String = "",
    val callId: String = "",
    val senderUid: String = "",
    val participantUids: List<String> = emptyList(),
    val seq: Long = 0L,
    val sampleRateHz: Long = 8000L,
    val audioBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallVideoFrame(
    @DocumentId val id: String = "",
    val matchId: String = "",
    val callId: String = "",
    val senderUid: String = "",
    val participantUids: List<String> = emptyList(),
    val seq: Long = 0L,
    val frameBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MediaChunkRecord(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val participantUids: List<String> = emptyList(),
    val seq: Long = 0L,
    val dataBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ProfileVideoItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val caption: String = "",
    val mediaDataUrl: String? = null,
    val thumbnailDataUrl: String? = null,
    val mediaMimeType: String? = null,
    val mediaDurationSec: Long = 0L,
    val mediaByteSize: Long = 0L,
    val chunkCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FriendRequestRecord(
    @DocumentId val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val status: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FriendshipRecord(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ProfileViewRecord(
    @DocumentId val id: String = "",
    val viewerUid: String = "",
    val targetUid: String = "",
    val viewerName: String = "",
    val viewerPhoto: String? = null,
    @ServerTimestamp val viewedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ProfileLikeRecord(
    @DocumentId val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FavoriteRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val targetUid: String = "",
    val displayName: String = "",
    val age: Long = 25L,
    val cityLabel: String = "",
    val country: String = "",
    val photoDataUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorPhoto: String? = null,
    val authorCountry: String? = null,
    val mediaType: String = "PHOTO",
    val mediaDataUrl: String? = null,
    val thumbnailDataUrl: String? = null,
    val caption: String? = null,
    val mediaDurationSec: Long = 0L,
    val chunkCount: Long = 0L,
    val viewerUids: List<String> = emptyList(),
    val expiresAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun isExpiredAt(nowMs: Long = System.currentTimeMillis()): Boolean =
        expiresAtMs in 1 until nowMs
}

@Keep
@IgnoreExtraProperties
data class VideoPost(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorPhoto: String? = null,
    val authorCountry: String? = null,
    val caption: String = "",
    val mediaDataUrl: String? = null,
    val thumbnailDataUrl: String? = null,
    val mediaMimeType: String? = null,
    val mediaDurationSec: Long = 0L,
    val mediaByteSize: Long = 0L,
    val chunkCount: Long = 0L,
    val likedByUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FeedPost(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorPhoto: String? = null,
    val authorCountry: String? = null,
    val authorCity: String? = null,
    val caption: String = "",
    val mediaType: String = "NONE",
    val mediaDataUrl: String? = null,
    val thumbnailDataUrl: String? = null,
    val mediaMimeType: String? = null,
    val mediaDurationSec: Long = 0L,
    val mediaByteSize: Long = 0L,
    val chunkCount: Long = 0L,
    val likedByUids: List<String> = emptyList(),
    val commentCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FeedComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorPhoto: String? = null,
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class IcebreakerSuggestion(
    val prompt: String,
    val tone: String,
    val reason: String
)

@Keep
@IgnoreExtraProperties
data class UserPresence(
    @DocumentId val id: String = "",
    val userId: String = "",
    val online: Boolean = false,
    val hideOnlineStatus: Boolean = false,
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

@Keep
@IgnoreExtraProperties
data class ReportRecord(
    @DocumentId val id: String = "",
    val reporterUid: String = "",
    val reportedUid: String = "",
    val reason: String = "",
    val details: String? = null,
    val sourceSurface: String = "PROFILE",
    val matchId: String? = null,
    val status: String = "OPEN",
    val resolvedBy: String? = null,
    val resolvedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class BlockedUserItem(
    val blockedUid: String,
    val displayName: String,
    val reason: String? = null
)

data class ActiveMemberPresenceItem(
    val profile: PublicProfile,
    val presence: UserPresence,
    val isOnline: Boolean,
    val isRecentlyLeft: Boolean,
    val statusLabel: String
)

data class SuggestedFriendItem(
    val profile: PublicProfile,
    val sharedInterests: List<String>,
    val sameCity: Boolean,
    val sameCountry: Boolean,
    val distanceKm: Double,
    val score: Int
)

enum class FriendStatus {
    NONE,
    OUTGOING_PENDING,
    INCOMING_PENDING,
    FRIENDS
}

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
    const val MAX_PROFILE_PHOTOS = 12
    const val ADMIN_EMAIL = "davidelvisaghamomwan@gmail.com"

    val INTEREST_OPTIONS = listOf(
        "Coffee", "Hiking", "Architecture", "Live Music",
        "Cooking", "Photography", "Travel", "Art Museums",
        "Running", "Vinyl Records", "Cinema", "Bookstores",
        "Cycling", "Cooking Class", "Design", "Botany"
    )

    val GENDER_OPTIONS = listOf("Woman", "Man", "Non-binary")
    val INTERESTED_IN_OPTIONS = listOf("Everyone", "Women", "Men", "Non-binary")

    val COUNTRY_CITIES: LinkedHashMap<String, List<String>> = linkedMapOf(
        "United States" to listOf("San Francisco", "New York", "Los Angeles", "Chicago", "Austin", "Seattle", "Miami"),
        "United Kingdom" to listOf("London", "Manchester", "Edinburgh", "Bristol", "Birmingham", "Leeds"),
        "Canada" to listOf("Toronto", "Vancouver", "Montreal", "Calgary", "Ottawa", "Halifax"),
        "Nigeria" to listOf("Lagos", "Abuja", "Port Harcourt", "Ibadan", "Enugu", "Kano"),
        "France" to listOf("Paris", "Lyon", "Marseille", "Bordeaux", "Nice", "Toulouse"),
        "Germany" to listOf("Berlin", "Munich", "Hamburg", "Frankfurt", "Cologne", "Stuttgart"),
        "Spain" to listOf("Madrid", "Barcelona", "Valencia", "Seville", "Bilbao", "Malaga"),
        "Italy" to listOf("Rome", "Milan", "Florence", "Naples", "Turin", "Bologna"),
        "Netherlands" to listOf("Amsterdam", "Rotterdam", "Utrecht", "The Hague", "Eindhoven"),
        "Australia" to listOf("Sydney", "Melbourne", "Brisbane", "Perth", "Adelaide"),
        "Japan" to listOf("Tokyo", "Osaka", "Kyoto", "Yokohama", "Fukuoka", "Sapporo"),
        "South Korea" to listOf("Seoul", "Busan", "Incheon", "Daegu", "Jeju"),
        "India" to listOf("Mumbai", "Bengaluru", "New Delhi", "Hyderabad", "Pune", "Chennai"),
        "Brazil" to listOf("São Paulo", "Rio de Janeiro", "Belo Horizonte", "Curitiba", "Florianópolis"),
        "Kenya" to listOf("Nairobi", "Mombasa", "Kisumu", "Nakuru", "Eldoret"),
        "South Africa" to listOf("Cape Town", "Johannesburg", "Durban", "Pretoria", "Stellenbosch"),
        "Ghana" to listOf("Accra", "Kumasi", "Tamale", "Takoradi", "Cape Coast"),
        "United Arab Emirates" to listOf("Dubai", "Abu Dhabi", "Sharjah", "Ajman"),
        "Singapore" to listOf("Central Singapore", "Marina Bay", "Orchard", "Tampines", "Jurong"),
        "Mexico" to listOf("Mexico City", "Guadalajara", "Monterrey", "Playa del Carmen", "Oaxaca")
    )

    val COUNTRY_OPTIONS: List<String> = COUNTRY_CITIES.keys.toList()

    fun citiesForCountry(country: String): List<String> {
        val exact = COUNTRY_CITIES[country.trim()]
        if (exact != null) return exact
        return COUNTRY_CITIES.entries
            .firstOrNull { it.key.equals(country.trim(), ignoreCase = true) }
            ?.value
            ?: emptyList()
    }

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

    val REPORT_REASONS = listOf(
        "Harassment or abusive behavior",
        "Spam, scam, or fake profile",
        "Inappropriate photos or explicit content",
        "Underage or misleading age",
        "Safety concern outside Kindred"
    )
}
