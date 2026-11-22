package com.example

import com.example.communication.LiveCallAudioEngine
import com.example.model.CallAudioChunk
import com.example.model.PublicProfile
import com.example.update.AppUpdateChecker
import com.example.util.GeoHashUtils
import com.example.util.LocationAndGeoHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

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
    fun allProfilePhotos_deduplicatesAndCapsAtMaxLimit() {
        val sampleList = (1..15).map { idx -> "data:image/jpeg;base64,PHOTO_$idx" }
        val profile = PublicProfile(
            photoDataUrl = "data:image/jpeg;base64,PHOTO_1",
            profilePhotos = listOf("data:image/jpeg;base64,PHOTO_1", " ") + sampleList
        )
        val result = profile.allProfilePhotos()
        assertEquals(com.example.model.KindredConstants.MAX_PROFILE_PHOTOS, result.size)
        assertEquals("data:image/jpeg;base64,PHOTO_1", result.first())
        assertEquals("data:image/jpeg;base64,PHOTO_12", result.last())
    }

    @Test
    fun pcm16RmsLevel_detectsSilenceVsVoiceSignal() {
        val silent = ByteArray(LiveCallAudioEngine.FRAME_BYTES)
        assertEquals(0f, LiveCallAudioEngine.computePcm16RmsLevel(silent), 0.0001f)

        val tone = ByteArray(LiveCallAudioEngine.FRAME_BYTES)
        var i = 0
        var sampleIdx = 0
        while (i + 1 < tone.size) {
            val sample = (sin(sampleIdx * 0.25) * 6000.0).toInt().toShort().toInt()
            tone[i] = (sample and 0xFF).toByte()
            tone[i + 1] = ((sample ushr 8) and 0xFF).toByte()
            i += 2
            sampleIdx++
        }
        val level = LiveCallAudioEngine.computePcm16RmsLevel(tone)
        assertTrue("Expected audible voice RMS level > 0.3, got $level", level > 0.3f)

        val chunk = CallAudioChunk(
            id = "alice_0",
            matchId = "alice_bob",
            callId = "call_1",
            senderUid = "alice",
            participantUids = listOf("alice", "bob"),
            seq = 0L,
            sampleRateHz = 8000L,
            audioBase64 = "AAAA"
        )
        assertEquals(8000L, chunk.sampleRateHz)
        assertEquals("call_1", chunk.callId)
    }

    @Test
    fun appUpdateChecker_parsesGitHubSlugsAndComparesBuilds() {
        assertEquals("octocat/kindred", AppUpdateChecker.parseGitHubRepoSlug("octocat/kindred"))
        assertEquals(
            "octocat/kindred",
            AppUpdateChecker.parseGitHubRepoSlug("https://github.com/octocat/kindred.git")
        )
        assertNull(AppUpdateChecker.parseGitHubRepoSlug("invalid"))

        assertTrue(
            AppUpdateChecker.isRemoteReleaseNewer(
                tagName = "build-12",
                title = "Kindred APK #12",
                localBuildNumber = 5,
                localVersionName = "1.0"
            )
        )
        assertFalse(
            AppUpdateChecker.isRemoteReleaseNewer(
                tagName = "build-5",
                title = "Kindred APK #5",
                localBuildNumber = 5,
                localVersionName = "1.0"
            )
        )
        assertTrue(
            AppUpdateChecker.isRemoteReleaseNewer(
                tagName = "v1.2.0",
                title = "v1.2.0",
                localBuildNumber = 1,
                localVersionName = "1.0"
            )
        )
    }
}
