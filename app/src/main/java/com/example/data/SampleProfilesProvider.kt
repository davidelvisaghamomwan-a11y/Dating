package com.example.data

import com.example.model.PublicProfile
import com.example.util.GeoHashUtils
import com.example.util.LocationAndGeoHelper
import com.google.firebase.Timestamp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Provides a balanced catalog of 23 local sample profiles (10 Women, 7 Men, 6 Non-binary)
 * anchored around the active user's coordinates so GeoHash proximity, distance radius,
 * gender, age, and verified-only filters work immediately out of the box without altering
 * zero-trust Firestore security rules.
 */
object SampleProfilesProvider {

    const val DEMO_PREFIX = "demo_"

    private data class ProfileSeed(
        val id: String,
        val displayName: String,
        val age: Long,
        val gender: String,
        val bio: String,
        val interests: List<String>,
        val verified: Boolean,
        val verificationScore: Long,
        val distanceKmOffset: Double,
        val bearingDeg: Double,
        val neighborhoodSuffix: String,
        val avatarSeed: String
    )

    fun isDemoProfileId(userId: String): Boolean = userId.startsWith(DEMO_PREFIX)

    private val seeds: List<ProfileSeed> = listOf(
        // --- 10 Women ---
        ProfileSeed(
            id = "${DEMO_PREFIX}woman_01",
            displayName = "Maya",
            age = 27,
            gender = "Woman",
            bio = "Architectural photographer hunting down morning espresso and quiet sunlit courtyards.",
            interests = listOf("Architecture", "Photography", "Coffee", "Art Museums"),
            verified = true,
            verificationScore = 96,
            distanceKmOffset = 1.6,
            bearingDeg = 25.0,
            neighborhoodSuffix = "Arts District",
            avatarSeed = "maya-rose"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}woman_02",
            displayName = "Elena",
            age = 29,
            gender = "Woman",
            bio = "Trail runner by dawn, homemade pasta experimenter by night. Always down for an unplanned bookstore detour.",
            interests = listOf("Running", "Cooking", "Bookstores", "Hiking"),
            verified = true,
            verificationScore = 94,
            distanceKmOffset = 2.9,
            bearingDeg = 72.0,
            neighborhoodSuffix = "Hillside",
            avatarSeed = "elena-gold"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}woman_03",
            displayName = "Chloe",
            age = 24,
            gender = "Woman",
            bio = "Collecting obscure 70s funk records and tending way too many balcony ferns.",
            interests = listOf("Vinyl Records", "Botany", "Live Music", "Cinema"),
            verified = true,
            verificationScore = 91,
            distanceKmOffset = 4.3,
            bearingDeg = 118.0,
            neighborhoodSuffix = "Midtown",
            avatarSeed = "chloe-mint"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}woman_04",
            displayName = "Priya",
            age = 31,
            gender = "Woman",
            bio = "Industrial designer who sketches in cafes and plans trips entirely around regional bakeries.",
            interests = listOf("Design", "Travel", "Coffee", "Cooking Class"),
            verified = true,
            verificationScore = 98,
            distanceKmOffset = 5.8,
            bearingDeg = 160.0,
            neighborhoodSuffix = "Harbor View",
            avatarSeed = "priya-plum"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}woman_05",
            displayName = "Sofia",
            age = 26,
            gender = "Woman",
            bio = "Weekend coastal cyclist looking for a partner in crime for farmers market tacos and indie films.",
            interests = listOf("Cycling", "Cinema", "Cooking", "Travel"),
            verified = false,
            verificationScore = 45,
            distanceKmOffset = 7.4,
            bearingDeg = 205.0,
            neighborhoodSuffix = "Bayfront",
            avatarSeed = "sofia-coral"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}woman_06",
            displayName = "Hannah",
            age = 33,
            gender = "Woman",
            bio = "Curator of small gallery shows. Tell me the last museum exhibit that actually surprised you.",
            interests = listOf("Art Museums", "Bookstores", "Architecture", "Design"),
            verified = true,
            verificationScore = 92,
            distanceKmOffset = 9.1,
            bearingDeg = 248.0,
            neighborhoodSuffix = "Old Town",
            avatarSeed = "hannah-amber"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}woman_07",
            displayName = "Naomi",
            age = 28,
            gender = "Woman",
            bio = "Software engineer off the clock: salsa nights, pour-over rituals, and long ridge hikes.",
            interests = listOf("Hiking", "Coffee", "Live Music", "Running"),
            verified = true,
            verificationScore = 95,
            distanceKmOffset = 11.5,
            bearingDeg = 290.0,
            neighborhoodSuffix = "Parkside",
            avatarSeed = "naomi-teal"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}woman_08",
            displayName = "Zara",
            age = 25,
            gender = "Woman",
            bio = "Analog film shooter documenting neon diners and late-night jazz sets.",
            interests = listOf("Photography", "Live Music", "Cinema", "Vinyl Records"),
            verified = false,
            verificationScore = 40,
            distanceKmOffset = 14.2,
            bearingDeg = 325.0,
            neighborhoodSuffix = "West End",
            avatarSeed = "zara-sunset"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}woman_09",
            displayName = "Clara",
            age = 35,
            gender = "Woman",
            bio = "Botanical illustrator happiest near greenhouses, sourdough ovens, and calm Sunday mornings.",
            interests = listOf("Botany", "Cooking", "Art Museums", "Coffee"),
            verified = true,
            verificationScore = 89,
            distanceKmOffset = 18.0,
            bearingDeg = 42.0,
            neighborhoodSuffix = "Garden Heights",
            avatarSeed = "clara-botanic"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}woman_10",
            displayName = "Nora",
            age = 30,
            gender = "Woman",
            bio = "Always training for the next alpine trek, but never saying no to a spicy ramen spot.",
            interests = listOf("Hiking", "Travel", "Running", "Cooking Class"),
            verified = true,
            verificationScore = 93,
            distanceKmOffset = 22.5,
            bearingDeg = 140.0,
            neighborhoodSuffix = "North Ridge",
            avatarSeed = "nora-alpine"
        ),

        // --- 7 Men ---
        ProfileSeed(
            id = "${DEMO_PREFIX}man_01",
            displayName = "Julian",
            age = 29,
            gender = "Man",
            bio = "Furniture maker who spends Saturdays hunting vintage vinyl and testing espresso dial-ins.",
            interests = listOf("Design", "Vinyl Records", "Coffee", "Architecture"),
            verified = true,
            verificationScore = 95,
            distanceKmOffset = 2.1,
            bearingDeg = 15.0,
            neighborhoodSuffix = "Design Row",
            avatarSeed = "julian-cedar"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}man_02",
            displayName = "Marcus",
            age = 32,
            gender = "Man",
            bio = "Documentary editor & marathoner. Bonus points if you know a hidden trail with ocean views.",
            interests = listOf("Cinema", "Running", "Hiking", "Photography"),
            verified = true,
            verificationScore = 92,
            distanceKmOffset = 4.9,
            bearingDeg = 95.0,
            neighborhoodSuffix = "Marina Edge",
            avatarSeed = "marcus-slate"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}man_03",
            displayName = "Liam",
            age = 26,
            gender = "Man",
            bio = "Gravel cyclist and amateur Thai cook. Let's trade playlists and cook something spicy.",
            interests = listOf("Cycling", "Cooking", "Live Music", "Travel"),
            verified = false,
            verificationScore = 48,
            distanceKmOffset = 6.7,
            bearingDeg = 182.0,
            neighborhoodSuffix = "South Slope",
            avatarSeed = "liam-indigo"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}man_04",
            displayName = "Mateo",
            age = 34,
            gender = "Man",
            bio = "Urban planner obsessed with walkable neighborhoods, street tacos, and indie bookstores.",
            interests = listOf("Architecture", "Bookstores", "Coffee", "Cycling"),
            verified = true,
            verificationScore = 97,
            distanceKmOffset = 10.4,
            bearingDeg = 228.0,
            neighborhoodSuffix = "Civic Quarter",
            avatarSeed = "mateo-terracotta"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}man_05",
            displayName = "Devon",
            age = 27,
            gender = "Man",
            bio = "Acoustic guitarist with a soft spot for late-night ramen and Sunday museum strolls.",
            interests = listOf("Live Music", "Art Museums", "Cooking Class", "Cinema"),
            verified = true,
            verificationScore = 88,
            distanceKmOffset = 13.8,
            bearingDeg = 276.0,
            neighborhoodSuffix = "Echo Valley",
            avatarSeed = "devon-emerald"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}man_06",
            displayName = "Kenji",
            age = 30,
            gender = "Man",
            bio = "Terrarium builder and Leica fan. Looking for someone curious, kind, and up for road trips.",
            interests = listOf("Botany", "Photography", "Travel", "Design"),
            verified = true,
            verificationScore = 94,
            distanceKmOffset = 16.5,
            bearingDeg = 312.0,
            neighborhoodSuffix = "Oakwood",
            avatarSeed = "kenji-moss"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}man_07",
            displayName = "Arthur",
            age = 37,
            gender = "Man",
            bio = "Rare-book restorer who makes killer cardamom cold brew and enjoys rainy cinema matinees.",
            interests = listOf("Bookstores", "Cinema", "Coffee", "Vinyl Records"),
            verified = false,
            verificationScore = 42,
            distanceKmOffset = 21.0,
            bearingDeg = 58.0,
            neighborhoodSuffix = "Ferry Commons",
            avatarSeed = "arthur-velvet"
        ),

        // --- 6 Non-binary ---
        ProfileSeed(
            id = "${DEMO_PREFIX}nb_01",
            displayName = "River",
            age = 26,
            gender = "Non-binary",
            bio = "Ceramicist and plant parent. Always down for gallery openings and late-night diner fries.",
            interests = listOf("Design", "Botany", "Art Museums", "Coffee"),
            verified = true,
            verificationScore = 95,
            distanceKmOffset = 2.4,
            bearingDeg = 50.0,
            neighborhoodSuffix = "Studio Row",
            avatarSeed = "river-aurora"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}nb_02",
            displayName = "Sage",
            age = 28,
            gender = "Non-binary",
            bio = "Sound designer mixing field recordings with ambient synths. Let's catch a rooftop gig.",
            interests = listOf("Live Music", "Vinyl Records", "Cinema", "Travel"),
            verified = true,
            verificationScore = 90,
            distanceKmOffset = 5.3,
            bearingDeg = 132.0,
            neighborhoodSuffix = "Lantern Walk",
            avatarSeed = "sage-jade"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}nb_03",
            displayName = "Rowan",
            age = 31,
            gender = "Non-binary",
            bio = "Bikepacking enthusiast and community garden volunteer looking for warm, intentional energy.",
            interests = listOf("Cycling", "Botany", "Hiking", "Cooking"),
            verified = true,
            verificationScore = 93,
            distanceKmOffset = 8.6,
            bearingDeg = 214.0,
            neighborhoodSuffix = "Greenbelt",
            avatarSeed = "rowan-ochre"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}nb_04",
            displayName = "Kai",
            age = 24,
            gender = "Non-binary",
            bio = "Zine publisher, espresso nerd, and unapologetic sci-fi bookworm.",
            interests = listOf("Bookstores", "Coffee", "Design", "Photography"),
            verified = false,
            verificationScore = 46,
            distanceKmOffset = 12.1,
            bearingDeg = 265.0,
            neighborhoodSuffix = "Mission Loop",
            avatarSeed = "kai-cobalt"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}nb_05",
            displayName = "Quinn",
            age = 29,
            gender = "Non-binary",
            bio = "Pastry chef off duty. Swipe right if you appreciate laminated dough and weird indie comedies.",
            interests = listOf("Cooking Class", "Cooking", "Cinema", "Art Museums"),
            verified = true,
            verificationScore = 96,
            distanceKmOffset = 15.7,
            bearingDeg = 340.0,
            neighborhoodSuffix = "Creekside",
            avatarSeed = "quinn-rosewood"
        ),
        ProfileSeed(
            id = "${DEMO_PREFIX}nb_06",
            displayName = "Avery",
            age = 33,
            gender = "Non-binary",
            bio = "Architectural lighting designer chasing golden hour trails and quiet neighborhood wine bars.",
            interests = listOf("Architecture", "Hiking", "Photography", "Travel"),
            verified = true,
            verificationScore = 91,
            distanceKmOffset = 19.4,
            bearingDeg = 105.0,
            neighborhoodSuffix = "Summit Terrace",
            avatarSeed = "avery-dusk"
        )
    )

    /**
     * Generates all 23 fake profiles positioned around ([anchorLatitude], [anchorLongitude])
     * with valid GeoHash cells and coarse coordinates.
     */
    fun generateProfiles(
        anchorLatitude: Double = 37.7749,
        anchorLongitude: Double = -122.4194
    ): List<PublicProfile> {
        val safeLat = anchorLatitude.coerceIn(-85.0, 85.0)
        val safeLng = anchorLongitude.coerceIn(-180.0, 180.0)
        val cosLat = cos(Math.toRadians(safeLat)).coerceAtLeast(0.2)

        return seeds.map { seed ->
            val bearingRad = Math.toRadians(seed.bearingDeg)
            val deltaLat = (seed.distanceKmOffset * cos(bearingRad)) / 111.0
            val deltaLng = (seed.distanceKmOffset * sin(bearingRad)) / (111.0 * cosLat)

            val coarseLat = LocationAndGeoHelper.roundToCoarse((safeLat + deltaLat).coerceIn(-89.9, 89.9))
            val coarseLng = LocationAndGeoHelper.roundToCoarse(((safeLng + deltaLng + 540.0) % 360.0) - 180.0)
            val geohash = GeoHashUtils.encode(coarseLat, coarseLng, precision = 7)

            PublicProfile(
                id = seed.id,
                userId = seed.id,
                displayName = seed.displayName,
                age = seed.age,
                gender = seed.gender,
                bio = seed.bio,
                cityLabel = seed.neighborhoodSuffix,
                country = "United States",
                coarseLatitude = coarseLat,
                coarseLongitude = coarseLng,
                geohash = geohash,
                geohash3 = geohash.take(3),
                geohash4 = geohash.take(4),
                interests = seed.interests,
                photoDataUrl = null,
                profilePhotos = emptyList(),
                avatarSeed = seed.avatarSeed,
                discoverable = true,
                verificationStatus = if (seed.verified) "VERIFIED" else "UNVERIFIED",
                verificationScore = seed.verificationScore,
                verifiedAt = if (seed.verified) Timestamp.now() else null
            )
        }
    }
}
