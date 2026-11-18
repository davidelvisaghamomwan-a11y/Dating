package com.example

import com.example.data.SampleProfilesProvider
import com.example.model.PublicProfile
import com.example.service.GeoHashMatchingService
import com.example.util.GeoHashUtils
import com.example.util.LocationAndGeoHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun coarseRounding_preservesApproxOneKmPrecision() {
        val rounded = LocationAndGeoHelper.roundToCoarse(37.774929)
        assertEquals(37.77, rounded, 0.0001)
    }

    @Test
    fun haversineDistance_sfToOaklandIsReasonable() {
        val distance = LocationAndGeoHelper.haversineDistanceKm(
            37.7749, -122.4194,
            37.8044, -122.2712
        )
        assertTrue("Expected ~13 km, got $distance", distance in 10.0..18.0)
    }

    @Test
    fun formatDistanceLabel_formatsSubKilometerAndKilometers() {
        assertEquals("< 1 km away", LocationAndGeoHelper.formatDistanceLabel(0.4))
        assertEquals("3.5 km away", LocationAndGeoHelper.formatDistanceLabel(3.5))
        assertEquals("24 km away", LocationAndGeoHelper.formatDistanceLabel(24.3))
    }

    @Test
    fun geohashEncodeAndDecodeBounds_roundTripsSanFrancisco() {
        val lat = 37.7749
        val lng = -122.4194
        val hash = GeoHashUtils.encode(lat, lng, precision = 7)
        assertEquals(7, hash.length)
        assertTrue("Expected SF geohash starting with 9q8yy, got $hash", hash.startsWith("9q8yy"))

        val bounds = GeoHashUtils.decodeBounds(hash)
        assertTrue(lat in bounds.minLat..bounds.maxLat)
        assertTrue(lng in bounds.minLng..bounds.maxLng)
    }

    @Test
    fun geohashNeighborhood_returnsNineUniqueCellsContainingCenter() {
        val centerCell = GeoHashUtils.encode(37.77, -122.42, precision = 4)
        val neighbors = GeoHashUtils.neighborhood(centerCell)
        assertEquals(9, neighbors.size)
        assertEquals(centerCell, neighbors.first())
        assertEquals(9, neighbors.toSet().size)
    }

    @Test
    fun geohashPrecisionForRadius_switchesAt45Km() {
        assertEquals(4, GeoHashUtils.precisionForRadiusKm(15.0))
        assertEquals(4, GeoHashUtils.precisionForRadiusKm(45.0))
        assertEquals(3, GeoHashUtils.precisionForRadiusKm(100.0))
    }

    @Test
    fun sharedPrefixLength_countsMatchingLeadingChars() {
        assertEquals(5, GeoHashUtils.sharedPrefixLength("9q8yyk8", "9q8yy9z"))
        assertEquals(0, GeoHashUtils.sharedPrefixLength("9q8yyk8", "xn76urx"))
    }

    @Test
    fun allProfilePhotos_deduplicatesAndCapsAtFour() {
        val profile = PublicProfile(
            photoDataUrl = "data:image/jpeg;base64,AAA",
            profilePhotos = listOf(
                "data:image/jpeg;base64,AAA",
                "data:image/jpeg;base64,BBB",
                " ",
                "data:image/jpeg;base64,CCC",
                "data:image/jpeg;base64,DDD",
                "data:image/jpeg;base64,EEE"
            )
        )
        val result = profile.allProfilePhotos()
        assertEquals(4, result.size)
        assertEquals(
            listOf(
                "data:image/jpeg;base64,AAA",
                "data:image/jpeg;base64,BBB",
                "data:image/jpeg;base64,CCC",
                "data:image/jpeg;base64,DDD"
            ),
            result
        )
    }

    @Test
    fun sampleProfiles_hasTenWomenSevenMenSixNonBinaryWithinGeoRadius() {
        val anchorLat = 40.7128
        val anchorLng = -74.0060
        val profiles = SampleProfilesProvider.generateProfiles(anchorLat, anchorLng)

        assertEquals(23, profiles.size)
        assertEquals(23, profiles.map { it.userId }.toSet().size)
        assertEquals(10, profiles.count { it.gender == "Woman" })
        assertEquals(7, profiles.count { it.gender == "Man" })
        assertEquals(6, profiles.count { it.gender == "Non-binary" })

        profiles.forEach { profile ->
            assertTrue(SampleProfilesProvider.isDemoProfileId(profile.userId))
            assertEquals(7, profile.geohash.length)
            assertEquals(profile.geohash.take(3), profile.geohash3)
            assertEquals(profile.geohash.take(4), profile.geohash4)
            val dist = LocationAndGeoHelper.haversineDistanceKm(
                anchorLat,
                anchorLng,
                profile.coarseLatitude,
                profile.coarseLongitude
            )
            assertTrue("Expected distance <= 30 km, got $dist for ${profile.displayName}", dist <= 30.0)
        }
    }
}
