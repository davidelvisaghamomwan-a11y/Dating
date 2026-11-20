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
    val mediaGifId: String? = null,
    val mediaDurationSec: Long = 0L,
    val readByUids: List<String> = emptyList(),
    val readAt: Timestamp? = null,
    val disappearingSeconds: Long = 0L,
    val viewOnce: Boolean = false,
    val openedByRecipient: Boolean = false,
    val expiresAtMs: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun isReadBy(uid: String): Boolean = uid.isNotBlank() && uid in readByUids

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
}
