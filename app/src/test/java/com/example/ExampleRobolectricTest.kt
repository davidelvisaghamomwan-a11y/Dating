package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import com.example.model.BlockedUserItem
import com.example.model.ChatMessage
import com.example.model.DiscoveryCandidate
import com.example.model.KindredConstants
import com.example.model.LivenessAnalysisResult
import com.example.model.MatchRecord
import com.example.model.MatchThreadItem
import com.example.model.ProfilePhotoComparison
import com.example.model.PublicProfile
import com.example.model.UiState
import com.example.model.UserPresence
import com.example.ai.AiIcebreakerService
import com.example.ai.IcebreakerAiUiState
import com.example.model.CallSession
import com.example.model.TypingStatus
import com.example.notifications.KindredNotificationHelper
import com.example.service.GeoHashMatchingService
import com.example.ui.components.ProfileSearchContent
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.EditProfileScreen
import com.example.ui.screens.MatchesAndChatScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VerificationScreen
import com.example.ui.theme.KindredTheme
import com.example.util.FaceComparisonEngine
import com.example.util.FaceDescriptor
import com.example.util.FacePoint
import com.example.util.LocationAndGeoHelper
import com.example.util.PresenceUtils
import com.example.util.ResolvedDeviceLocation
import com.google.firebase.Timestamp
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleThread = MatchThreadItem(
        match = MatchRecord(
            id = "alice_1_bob_2",
            participantUids = listOf("alice_1", "bob_2"),
            createdBy = "alice_1"
        ),
        partnerId = "bob_2",
        partnerProfile = PublicProfile(
            userId = "bob_2",
            displayName = "Elena",
            age = 28,
            cityLabel = "San Francisco",
            interests = listOf("Coffee", "Hiking"),
            verificationStatus = "VERIFIED",
            verificationScore = 95L
        ),
        distanceKm = 3.2
    )

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Kindred", appName)
    }

    @Test
    fun `chat screen displays messages and sends new message`() {
        var sentMatchId = ""
        var sentText = ""
        var sentType = ""

        val messages = listOf(
            ChatMessage(
                id = "m1",
                matchId = sampleThread.match.id,
                senderId = "bob_2",
                participantUids = listOf("alice_1", "bob_2"),
                text = "Hey! Ready for that coffee walk?",
                messageType = "TEXT",
                createdAt = Timestamp.now()
            ),
            ChatMessage(
                id = "m2",
                matchId = sampleThread.match.id,
                senderId = "alice_1",
                participantUids = listOf("alice_1", "bob_2"),
                text = "Always ready for coffee!",
                messageType = "TEXT",
                createdAt = Timestamp.now()
            )
        )

        composeTestRule.setContent {
            KindredTheme {
                ChatScreen(
                    currentUserId = "alice_1",
                    thread = sampleThread,
                    messagesState = UiState.Success(messages),
                    showBackButton = true,
                    onBack = {},
                    onSendMessage = { matchId, _, text, type ->
                        sentMatchId = matchId
                        sentText = text
                        sentType = type
                    },
                    onUnmatch = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Elena, 28").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hey! Ready for that coffee walk?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Always ready for coffee!").assertIsDisplayed()

        composeTestRule.onNodeWithTag("chat_message_input").performTextInput("  See you at 10am!  ")
        composeTestRule.onNodeWithTag("send_message_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("alice_1_bob_2", sentMatchId)
        assertEquals("See you at 10am!", sentText)
        assertEquals("TEXT", sentType)
    }

    @Test
    fun `chat screen empty state sends icebreaker`() {
        var sentType = ""

        composeTestRule.setContent {
            KindredTheme {
                ChatScreen(
                    currentUserId = "alice_1",
                    thread = sampleThread,
                    messagesState = UiState.Success(emptyList()),
                    showBackButton = false,
                    onBack = {},
                    onSendMessage = { _, _, _, type ->
                        sentType = type
                    },
                    onUnmatch = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("icebreaker_chip_0").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("ICEBREAKER", sentType)
    }

    @Test
    fun `chat screen error state retries`() {
        var retried = false

        composeTestRule.setContent {
            KindredTheme {
                ChatScreen(
                    currentUserId = "alice_1",
                    thread = sampleThread,
                    messagesState = UiState.Error("Offline"),
                    showBackButton = false,
                    onBack = {},
                    onSendMessage = { _, _, _, _ -> },
                    onUnmatch = {},
                    onRetry = { retried = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_retry_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(retried)
    }

    @Test
    fun `face comparison engine scores matching face high and altered face low`() {
        val faceA = renderSyntheticFaceBitmap(
            skinColor = Color.rgb(225, 185, 160),
            eyeOffsetY = 92f,
            mouthY = 162f,
            mouthHalfWidth = 28f,
            mustache = false
        )
        val descA = FaceDescriptor(
            bounds = RectF(50f, 45f, 190f, 205f),
            leftEye = FacePoint(92f, 92f),
            rightEye = FacePoint(148f, 92f),
            noseBase = FacePoint(120f, 126f),
            mouthLeft = FacePoint(92f, 162f),
            mouthRight = FacePoint(148f, 162f),
            mouthBottom = FacePoint(120f, 172f),
            leftCheek = FacePoint(78f, 130f),
            rightCheek = FacePoint(162f, 130f)
        )

        // Same person slightly brighter
        val samePerson = renderSyntheticFaceBitmap(
            skinColor = Color.rgb(232, 190, 165),
            eyeOffsetY = 92f,
            mouthY = 162f,
            mouthHalfWidth = 28f,
            mustache = false
        )
        val selfMatch = FaceComparisonEngine.compareFaceBitmapsAndDescriptors(
            selfieBitmap = faceA,
            selfieFace = descA,
            profileBitmap = samePerson,
            profileFace = descA
        )
        assertTrue(
            "Expected matching face >= 0.85, got ${selfMatch.overallSimilarity}",
            selfMatch.overallSimilarity >= 0.85f
        )

        // Distinct person with very different proportions, skin tone, and facial hair contrast
        val otherPerson = renderSyntheticFaceBitmap(
            skinColor = Color.rgb(110, 74, 58),
            eyeOffsetY = 82f,
            mouthY = 186f,
            mouthHalfWidth = 14f,
            mustache = true
        )
        val descB = FaceDescriptor(
            bounds = RectF(40f, 35f, 200f, 225f),
            leftEye = FacePoint(92f, 82f),
            rightEye = FacePoint(148f, 82f),
            noseBase = FacePoint(120f, 146f),
            mouthLeft = FacePoint(106f, 186f),
            mouthRight = FacePoint(134f, 186f),
            mouthBottom = FacePoint(120f, 202f),
            leftCheek = FacePoint(64f, 125f),
            rightCheek = FacePoint(176f, 125f)
        )
        val mismatch = FaceComparisonEngine.compareFaceBitmapsAndDescriptors(
            selfieBitmap = faceA,
            selfieFace = descA,
            profileBitmap = otherPerson,
            profileFace = descB
        )
        assertTrue(
            "Expected distinct face < 0.60, got ${mismatch.overallSimilarity}",
            mismatch.overallSimilarity < 0.60f
        )
        assertFalse(mismatch.overallSimilarity >= FaceComparisonEngine.MIN_BEST_PHOTO_SIMILARITY)
    }

    @Test
    fun `verification screen blocks selfie check when user has no profile photos`() {
        var navigatedToProfile = false
        val noPhotoProfile = PublicProfile(
            userId = "u1",
            displayName = "Alex",
            age = 27,
            photoDataUrl = null,
            profilePhotos = emptyList()
        )

        composeTestRule.setContent {
            KindredTheme {
                VerificationScreen(
                    myProfile = noPhotoProfile,
                    isEmailVerified = true,
                    verifications = emptyList(),
                    activeChallenge = KindredConstants.VERIFICATION_CHALLENGES.first(),
                    isBusy = false,
                    onCycleChallenge = {},
                    onSubmitVerification = { _, _, _ -> },
                    onNavigateToProfile = { navigatedToProfile = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("upload_photos_cta_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(navigatedToProfile)
    }

    @Test
    fun `verification screen renders per-photo comparison breakdown for catfish detection`() {
        val profileWithPhotos = PublicProfile(
            userId = "u1",
            displayName = "Alex",
            age = 27,
            photoDataUrl = "data:image/jpeg;base64,AAA",
            profilePhotos = listOf("data:image/jpeg;base64,AAA", "data:image/jpeg;base64,BBB")
        )
        val rejectedAnalysis = LivenessAnalysisResult(
            passed = false,
            livenessPassed = true,
            faceMatchPassed = false,
            faceCount = 1,
            confidence = 0.92f,
            eyesDistancePx = 54f,
            photoSha256 = "abcdef1234567890abcdef",
            comparedPhotoCount = 2,
            bestSimilarityScore = 0.72f,
            averageSimilarityScore = 0.54f,
            perPhotoComparisons = listOf(
                ProfilePhotoComparison(
                    photoIndex = 0,
                    photoDataUrl = "data:image/jpeg;base64,AAA",
                    faceDetected = true,
                    similarityScore = 0.72f,
                    geometryScore = 0.78f,
                    structureScore = 0.69f,
                    toneScore = 0.66f,
                    matched = true,
                    reason = "Strong facial match (72%)"
                ),
                ProfilePhotoComparison(
                    photoIndex = 1,
                    photoDataUrl = "data:image/jpeg;base64,BBB",
                    faceDetected = true,
                    similarityScore = 0.36f,
                    geometryScore = 0.38f,
                    structureScore = 0.34f,
                    toneScore = 0.37f,
                    matched = false,
                    reason = "Face mismatch (36%) — does not match live selfie"
                )
            ),
            summary = "Potential mismatch detected: one profile photo only scored 36% similarity against your live selfie."
        )

        composeTestRule.setContent {
            KindredTheme {
                VerificationScreen(
                    myProfile = profileWithPhotos,
                    isEmailVerified = true,
                    verifications = emptyList(),
                    activeChallenge = KindredConstants.VERIFICATION_CHALLENGES.first(),
                    isBusy = false,
                    onCycleChallenge = {},
                    onSubmitVerification = { _, _, _ -> },
                    onNavigateToProfile = {},
                    initialAnalysis = rejectedAnalysis
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("verification_comparison_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Selfie vs. Profile Photo Match").assertIsDisplayed()
        composeTestRule.onNodeWithText("Profile Photo #1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Profile Photo #2").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("fix_mismatched_photos_button").performScrollTo().assertIsDisplayed()
    }

    private fun renderSyntheticFaceBitmap(
        skinColor: Int,
        eyeOffsetY: Float,
        mouthY: Float,
        mouthHalfWidth: Float,
        mustache: Boolean
    ): Bitmap {
        val w = 240
        val h = 240
        val pixels = IntArray(w * h) { Color.rgb(240, 240, 244) }
        val eyeColor = Color.rgb(32, 26, 22)
        val noseColor = Color.rgb(120, 80, 70)
        val hairColor = Color.rgb(20, 18, 18)
        val mouthColor = Color.rgb(170, 70, 80)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val dxFace = (x - 120f) / 70f
                val dyFace = (y - 125f) / 80f
                if (dxFace * dxFace + dyFace * dyFace <= 1f) {
                    pixels[y * w + x] = skinColor
                }
                val distLeftEye = hypot(x - 92f, y - eyeOffsetY)
                val distRightEye = hypot(x - 148f, y - eyeOffsetY)
                if (distLeftEye <= 8f || distRightEye <= 8f) {
                    pixels[y * w + x] = eyeColor
                }
                if (x in 118..122 && y.toFloat() in (eyeOffsetY + 12f)..(mouthY - 22f)) {
                    pixels[y * w + x] = noseColor
                }
                if (mustache && x in 88..152 && y.toFloat() in (mouthY - 14f)..(mouthY - 4f)) {
                    pixels[y * w + x] = hairColor
                }
                if (x.toFloat() in (120f - mouthHalfWidth)..(120f + mouthHalfWidth) &&
                    y.toFloat() in (mouthY - 3f)..(mouthY + 3f)
                ) {
                    pixels[y * w + x] = mouthColor
                }
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    @Test
    fun `discover tinder swipe deck swipes right to like and left to pass`() {
        val recordedSwipes = mutableListOf<Pair<String, String>>()
        val myProfile = PublicProfile(userId = "me", displayName = "Alex", age = 27)
        val candidates = listOf(
            DiscoveryCandidate(
                profile = PublicProfile(userId = "demo_woman_01", displayName = "Maya", age = 26, gender = "Woman"),
                distanceKm = 2.0,
                proximityMatchScore = 90
            ),
            DiscoveryCandidate(
                profile = PublicProfile(userId = "demo_man_01", displayName = "Julian", age = 29, gender = "Man"),
                distanceKm = 4.0,
                proximityMatchScore = 84
            )
        )

        composeTestRule.setContent {
            var activeCandidates by remember { mutableStateOf(candidates) }
            KindredTheme {
                DiscoverScreen(
                    myProfile = myProfile,
                    privateUser = null,
                    geoHashMeta = null,
                    candidates = activeCandidates,
                    onSwipe = { candidate, direction ->
                        recordedSwipes += candidate.profile.userId to direction
                        activeCandidates = activeCandidates.filterNot { it.profile.userId == candidate.profile.userId }
                    },
                    onRefreshLocation = {},
                    onLocationError = {},
                    onUpdateFilters = { _, _, _, _, _ -> },
                    onNavigateToProfile = {},
                    onNavigateToVerify = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("swipe_deck_top_card").performTouchInput {
            swipeRight()
        }
        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.waitForIdle()
        assertEquals(listOf("demo_woman_01" to "LIKE"), recordedSwipes)

        composeTestRule.onNodeWithTag("swipe_deck_top_card").performTouchInput {
            swipeLeft()
        }
        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.waitForIdle()
        assertEquals(listOf("demo_woman_01" to "LIKE", "demo_man_01" to "PASS"), recordedSwipes)
    }

    @Test
    fun `edit profile screen updates bio interests country city and photos and saves changes`() {
        var savedBio = ""
        var savedInterests = emptyList<String>()
        var savedPhotos = emptyList<String>()
        var savedCity = ""
        var savedCountry = ""

        val initialProfile = PublicProfile(
            userId = "me",
            displayName = "Alex",
            age = 27,
            bio = "Original bio",
            cityLabel = "San Francisco",
            country = "United States",
            interests = listOf("Coffee", "Hiking"),
            photoDataUrl = "data:image/jpeg;base64,AAA",
            profilePhotos = listOf("data:image/jpeg;base64,AAA", "data:image/jpeg;base64,BBB"),
            verificationStatus = "VERIFIED"
        )

        composeTestRule.setContent {
            KindredTheme {
                EditProfileScreen(
                    myProfile = initialProfile,
                    defaultDisplayName = "Alex",
                    isBusy = false,
                    onBack = {},
                    onSaveChanges = { bio, interests, photos, city, country ->
                        savedBio = bio
                        savedInterests = interests
                        savedPhotos = photos
                        savedCity = city
                        savedCountry = country
                    },
                    onStatusMessage = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Remove the second photo (slot 1) -> should show verified badge reset warning
        composeTestRule.onNodeWithTag("edit_remove_photo_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(
            "Updating your profile photos will reset your Verified badge until you complete a quick selfie check."
        ).assertIsDisplayed()

        // Update country and city
        composeTestRule.onNodeWithTag("edit_country_input").performScrollTo()
            .performTextReplacement("Canada")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_city_chip_Vancouver").performScrollTo().performClick()

        // Update bio
        composeTestRule.onNodeWithTag("edit_bio_input").performScrollTo()
            .performTextReplacement("Sunday pour-over fan and coastal hiker.")

        // Deselect Hiking and select Photography
        composeTestRule.onNodeWithTag("edit_interest_chip_Hiking").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("edit_interest_chip_Photography").performScrollTo().performClick()

        // Save changes
        composeTestRule.onNodeWithTag("edit_save_profile_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("Sunday pour-over fan and coastal hiker.", savedBio)
        assertEquals(listOf("Coffee", "Photography"), savedInterests)
        assertEquals(listOf("data:image/jpeg;base64,AAA"), savedPhotos)
        assertEquals("Canada", savedCountry)
        assertEquals("Vancouver", savedCity)
    }

    @Test
    fun `profile screen edit button navigates to edit profile`() {
        var openedEdit = false
        val profile = PublicProfile(userId = "me", displayName = "Alex", age = 27)

        composeTestRule.setContent {
            KindredTheme {
                ProfileScreen(
                    currentUserId = "me",
                    userEmail = "alex@example.com",
                    defaultDisplayName = "Alex",
                    myProfile = profile,
                    privateUser = null,
                    isBusy = false,
                    onSaveProfile = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    onEditProfile = { openedEdit = true },
                    onStatusMessage = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_edit_profile_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(openedEdit)
    }

    @Test
    fun `presence utils resolves online stale and last seen states accurately`() {
        val now = 1_750_000_000_000L

        assertEquals("Offline", PresenceUtils.resolvePresenceDisplay(null, now).label)

        val freshOnline = UserPresence(
            userId = "bob_2",
            online = true,
            lastSeenAt = Timestamp(Date(now - 30_000L))
        )
        val onlineState = PresenceUtils.resolvePresenceDisplay(freshOnline, now)
        assertTrue(onlineState.isOnline)
        assertEquals("Online", onlineState.label)

        // Heartbeat older than 2 minutes must fall back to "Last seen ..." even if online == true
        val staleHeartbeat = UserPresence(
            userId = "bob_2",
            online = true,
            lastSeenAt = Timestamp(Date(now - 5 * 60_000L))
        )
        val staleState = PresenceUtils.resolvePresenceDisplay(staleHeartbeat, now)
        assertFalse(staleState.isOnline)
        assertEquals("Last seen 5m ago", staleState.label)

        val offlineHours = UserPresence(
            userId = "bob_2",
            online = false,
            lastSeenAt = Timestamp(Date(now - 3 * 60 * 60_000L))
        )
        assertEquals("Last seen 3h ago", PresenceUtils.resolvePresenceDisplay(offlineHours, now).label)
    }

    @Test
    fun `chat screen displays online and last seen presence indicators`() {
        val now = System.currentTimeMillis()
        val initialPresence = UserPresence(
            userId = "bob_2",
            online = true,
            lastSeenAt = Timestamp(Date(now - 10_000L))
        )
        val offlinePresence = UserPresence(
            userId = "bob_2",
            online = false,
            lastSeenAt = Timestamp(Date(now - 15 * 60_000L))
        )

        composeTestRule.setContent {
            var presence by remember { mutableStateOf<UserPresence?>(initialPresence) }
            KindredTheme {
                ChatScreen(
                    currentUserId = "alice_1",
                    thread = sampleThread,
                    messagesState = UiState.Success(emptyList()),
                    partnerPresence = presence,
                    showBackButton = false,
                    onBack = {},
                    onSendMessage = { _, _, _, _ -> },
                    onUnmatch = { presence = offlinePresence }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_presence_status", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Online", useUnmergedTree = true).assertIsDisplayed()

        // Trigger state change to offline presence cleanly via unmatch dialog —
        // verify "Last seen 15m ago" renders accurately on second composition
        composeTestRule.onNodeWithTag("unmatch_button").performClick()
        composeTestRule.onNodeWithText("Unmatch").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_presence_status", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Last seen 15m ago", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `geohash matching excludes blocked profiles from ranked candidates`() {
        val blockedProfile = PublicProfile(
            userId = "user_blocked",
            displayName = "BlockedUser",
            age = 27,
            gender = "Woman",
            coarseLatitude = 37.775,
            coarseLongitude = -122.419,
            interests = listOf("Coffee")
        )
        val allowedProfile = PublicProfile(
            userId = "user_allowed",
            displayName = "Elena",
            age = 26,
            gender = "Woman",
            coarseLatitude = 37.776,
            coarseLongitude = -122.418,
            interests = listOf("Coffee")
        )

        val ranked = GeoHashMatchingService.rankAndFilterCandidates(
            rawProfiles = listOf(blockedProfile, allowedProfile),
            currentUserId = "me",
            myLat = 37.775,
            myLng = -122.419,
            myGeoHash = "9q8yyk8",
            maxDistanceKm = 50.0,
            minAge = 18,
            maxAge = 50,
            interestedIn = "Everyone",
            verifiedOnly = false,
            myInterests = listOf("Coffee"),
            excludedUserIds = emptySet(),
            blockedUserIds = setOf("user_blocked")
        )

        assertEquals(1, ranked.size)
        assertEquals("user_allowed", ranked.first().profile.userId)
    }

    @Test
    fun `fcm notification helper builds deep link intent and deterministic token id`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = KindredNotificationHelper.buildLaunchIntent(
            context = context,
            matchId = "alice_1_bob_2",
            notificationType = KindredNotificationHelper.TYPE_NEW_MESSAGE
        )

        assertEquals("alice_1_bob_2", intent.getStringExtra(KindredNotificationHelper.EXTRA_MATCH_ID))
        assertEquals(
            KindredNotificationHelper.TYPE_NEW_MESSAGE,
            intent.getStringExtra(KindredNotificationHelper.EXTRA_NOTIFICATION_TYPE)
        )

        val id1 = KindredNotificationHelper.tokenDocumentId("fcm_sample_token_12345")
        val id2 = KindredNotificationHelper.tokenDocumentId("fcm_sample_token_12345")
        assertEquals(id1, id2)
        assertEquals(32, id1.length)
        assertTrue(id1.matches(Regex("^[a-f0-9]{32}$")))
    }

    @Test
    fun `discover screen block dialog triggers onBlockUser callback`() {
        var blockedId = ""
        val myProfile = PublicProfile(userId = "me", displayName = "Alex", age = 27)
        val candidate = DiscoveryCandidate(
            profile = PublicProfile(userId = "user_99", displayName = "Sienna", age = 25, gender = "Woman"),
            distanceKm = 2.4,
            proximityMatchScore = 88
        )

        composeTestRule.setContent {
            KindredTheme {
                DiscoverScreen(
                    myProfile = myProfile,
                    privateUser = null,
                    geoHashMeta = null,
                    candidates = listOf(candidate),
                    onSwipe = { _, _ -> },
                    onBlockUser = { blockedId = it.profile.userId },
                    onRefreshLocation = {},
                    onLocationError = {},
                    onUpdateFilters = { _, _, _, _, _ -> },
                    onNavigateToProfile = {},
                    onNavigateToVerify = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("discover_block_button", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_block_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("user_99", blockedId)
    }

    @Test
    fun `chat screen block dialog triggers onBlockUser callback`() {
        var blockedPair: Pair<String, String>? = null

        composeTestRule.setContent {
            KindredTheme {
                ChatScreen(
                    currentUserId = "alice_1",
                    thread = sampleThread,
                    messagesState = UiState.Success(emptyList()),
                    showBackButton = false,
                    onBack = {},
                    onSendMessage = { _, _, _, _ -> },
                    onUnmatch = {},
                    onBlockUser = { id, name -> blockedPair = id to name }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_block_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_chat_block_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("bob_2" to "Elena", blockedPair)
    }

    @Test
    fun `profile screen renders blocked users and triggers unblock callback`() {
        var unblockedUid = ""
        val profile = PublicProfile(userId = "me", displayName = "Alex", age = 27)
        val blockedList = listOf(
            BlockedUserItem(
                blockedUid = "blocked_user_1",
                displayName = "Marcus",
                reason = null,
                isDemo = false
            )
        )

        composeTestRule.setContent {
            KindredTheme {
                ProfileScreen(
                    currentUserId = "me",
                    userEmail = "alex@example.com",
                    defaultDisplayName = "Alex",
                    myProfile = profile,
                    privateUser = null,
                    isBusy = false,
                    onSaveProfile = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    blockedUsers = blockedList,
                    onUnblockUser = { unblockedUid = it },
                    onStatusMessage = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("unblock_button_blocked_user_1").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("blocked_user_1", unblockedUid)
    }

    @Test
    fun `profile screen saves selected country and city`() {
        var savedCountry = ""
        var savedCity = ""
        val profile = PublicProfile(
            userId = "me",
            displayName = "Alex",
            age = 27,
            cityLabel = "San Francisco",
            country = "United States"
        )

        composeTestRule.setContent {
            KindredTheme {
                ProfileScreen(
                    currentUserId = "me",
                    userEmail = "alex@example.com",
                    defaultDisplayName = "Alex",
                    myProfile = profile,
                    privateUser = null,
                    isBusy = false,
                    onSaveProfile = { _, _, _, _, city, country, _, _, _, _, _, _, _, _, _, _, _ ->
                        savedCity = city
                        savedCountry = country
                    },
                    onStatusMessage = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_country_input").performScrollTo()
            .performTextReplacement("Japan")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_city_chip_Kyoto").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("save_profile_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("Japan", savedCountry)
        assertEquals("Kyoto", savedCity)
    }

    @Test
    fun `inbox screen renders unread indicator, direct filter, and triggers search action`() {
        var openedSearch = false
        var selectedId: String? = null
        val unreadMatch = MatchThreadItem(
            match = MatchRecord(
                id = "m_unread",
                participantUids = listOf("alice_1", "bob_2"),
                conversationType = "DIRECT",
                lastMessageText = "Coffee in Tokyo?",
                lastMessageSenderId = "bob_2",
                lastMessageReadByUids = listOf("bob_2"),
                lastMessageAt = Timestamp.now()
            ),
            partnerId = "bob_2",
            partnerProfile = PublicProfile(
                userId = "bob_2",
                displayName = "Elena",
                age = 28,
                cityLabel = "Tokyo",
                country = "Japan"
            ),
            distanceKm = 2.0
        )
        val readMatch = MatchThreadItem(
            match = MatchRecord(
                id = "m_read",
                participantUids = listOf("alice_1", "carol_3"),
                conversationType = "MATCH",
                lastMessageText = "See you tomorrow!",
                lastMessageSenderId = "alice_1",
                lastMessageReadByUids = listOf("alice_1", "carol_3"),
                lastMessageAt = Timestamp.now()
            ),
            partnerId = "carol_3",
            partnerProfile = PublicProfile(
                userId = "carol_3",
                displayName = "Lucas",
                age = 30,
                cityLabel = "Toronto",
                country = "Canada"
            ),
            distanceKm = 5.0
        )

        composeTestRule.setContent {
            KindredTheme {
                MatchesAndChatScreen(
                    currentUserId = "alice_1",
                    matches = listOf(unreadMatch, readMatch),
                    selectedMatchId = null,
                    messagesState = UiState.Success(emptyList()),
                    onSelectMatch = { selectedId = it },
                    onOpenSearch = { openedSearch = true },
                    onSendMessage = { _, _, _, _ -> },
                    onUnmatch = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Inbox").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unread_dot_m_unread", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Coffee in Tokyo?", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("You: See you tomorrow!", useUnmergedTree = true).assertIsDisplayed()

        // Filter to Direct conversations only
        composeTestRule.onNodeWithTag("inbox_filter_direct").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("match_card_m_unread").assertIsDisplayed()
        composeTestRule.onNodeWithTag("match_card_m_read").assertDoesNotExist()

        // Clicking search button opens global profile search
        composeTestRule.onNodeWithTag("inbox_search_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(openedSearch)

        // Clicking unread thread opens conversation
        composeTestRule.onNodeWithTag("match_card_m_unread").performClick()
        composeTestRule.waitForIdle()
        assertEquals("m_unread", selectedId)
    }

    @Test
    fun `profile search sheet searches profiles by name and starts direct message`() {
        var messagedProfileId = ""
        val profiles = listOf(
            DiscoveryCandidate(
                profile = PublicProfile(
                    userId = "u_kenji",
                    displayName = "Kenji Sato",
                    age = 29,
                    cityLabel = "Osaka",
                    country = "Japan",
                    interests = listOf("Ramen", "Photography")
                ),
                distanceKm = 1.8,
                proximityMatchScore = 92
            ),
            DiscoveryCandidate(
                profile = PublicProfile(
                    userId = "u_claire",
                    displayName = "Claire Moreau",
                    age = 27,
                    cityLabel = "Paris",
                    country = "France",
                    interests = listOf("Cinema", "Art")
                ),
                distanceKm = 4.2,
                proximityMatchScore = 85
            )
        )

        composeTestRule.setContent {
            KindredTheme {
                ProfileSearchContent(
                    searchableProfiles = profiles,
                    onMessageProfile = { messagedProfileId = it.userId }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_search_input").performTextInput("Kenji")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("search_result_card_u_kenji").assertIsDisplayed()
        composeTestRule.onNodeWithTag("search_result_card_u_claire").assertDoesNotExist()

        composeTestRule.onNodeWithTag("search_message_button_u_kenji").performClick()
        composeTestRule.waitForIdle()
        assertEquals("u_kenji", messagedProfileId)
    }

    @Test
    fun `settings screen updates dating preferences and invokes save callback`() {
        var savedInterestedIn = ""
        var savedMinAge = 0
        var savedMaxAge = 0
        var savedRadius = 0
        var savedVerifiedOnly = false
        var savedGender: String? = null
        var savedDiscoverable: Boolean? = null

        val profile = PublicProfile(
            userId = "me",
            displayName = "Alex",
            age = 27,
            gender = "Man",
            discoverable = true
        )

        composeTestRule.setContent {
            KindredTheme {
                SettingsScreen(
                    myProfile = profile,
                    privateUser = null,
                    includeDemoProfiles = true,
                    isBusy = false,
                    onBack = {},
                    onToggleDemoProfiles = {},
                    onSavePreferences = { interestedIn, minAge, maxAge, radius, verifiedOnly, myGender, discoverable ->
                        savedInterestedIn = interestedIn
                        savedMinAge = minAge
                        savedMaxAge = maxAge
                        savedRadius = radius
                        savedVerifiedOnly = verifiedOnly
                        savedGender = myGender
                        savedDiscoverable = discoverable
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_interested_in_Women").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("settings_verified_only_switch").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("settings_gender_Non-binary").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("settings_discoverable_switch").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("settings_save_preferences_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("Women", savedInterestedIn)
        assertEquals(20, savedMinAge)
        assertEquals(45, savedMaxAge)
        assertEquals(100, savedRadius)
        assertTrue(savedVerifiedOnly)
        assertEquals("Non-binary", savedGender)
        assertEquals(false, savedDiscoverable)
    }

    @Test
    fun `realtime location distance recalculates accurately as user moves closer`() {
        assertEquals(
            0.0,
            LocationAndGeoHelper.calculateDistanceKm(37.7749, -122.4194, 37.7749, -122.4194),
            0.001
        )
        // San Francisco Ferry Building to Downtown Oakland (~13-14 km)
        val sfToOaklandKm = LocationAndGeoHelper.calculateDistanceKm(
            37.7955, -122.3937,
            37.8044, -122.2712
        )
        assertTrue("Expected ~10.8 km, got $sfToOaklandKm", sfToOaklandKm in 10.0..12.0)

        val candidateProfile = PublicProfile(
            userId = "candidate_oakland",
            displayName = "Aria",
            age = 27,
            gender = "Woman",
            coarseLatitude = 37.8044,
            coarseLongitude = -122.2712,
            cityLabel = "Oakland, CA",
            interests = listOf("Coffee")
        )

        val initialRanked = GeoHashMatchingService.rankAndFilterCandidates(
            rawProfiles = listOf(candidateProfile),
            currentUserId = "me",
            myLat = 37.7955,
            myLng = -122.3937,
            myGeoHash = "9q8yy",
            maxDistanceKm = 50.0,
            minAge = 18,
            maxAge = 50,
            interestedIn = "Everyone",
            verifiedOnly = false,
            myInterests = listOf("Coffee"),
            excludedUserIds = emptySet()
        )
        val movedCloserRanked = GeoHashMatchingService.rankAndFilterCandidates(
            rawProfiles = listOf(candidateProfile),
            currentUserId = "me",
            myLat = 37.8040,
            myLng = -122.2800,
            myGeoHash = "9q9p1",
            maxDistanceKm = 50.0,
            minAge = 18,
            maxAge = 50,
            interestedIn = "Everyone",
            verifiedOnly = false,
            myInterests = listOf("Coffee"),
            excludedUserIds = emptySet()
        )

        assertEquals(1, initialRanked.size)
        assertEquals(1, movedCloserRanked.size)
        assertTrue(initialRanked.first().distanceKm > 9.0)
        assertTrue(movedCloserRanked.first().distanceKm < 1.5)
    }

    @Test
    fun `discover screen shows Live GPS indicator and updated city when latestLocation streams`() {
        val myProfile = PublicProfile(
            userId = "me",
            displayName = "Alex",
            age = 27,
            cityLabel = "San Francisco, CA"
        )
        val liveLocation = ResolvedDeviceLocation(
            exactLatitude = 37.8044,
            exactLongitude = -122.2712,
            coarseLatitude = 37.80,
            coarseLongitude = -122.27,
            cityLabel = "Oakland, CA",
            isLiveGps = true
        )

        composeTestRule.setContent {
            KindredTheme {
                DiscoverScreen(
                    myProfile = myProfile,
                    privateUser = null,
                    geoHashMeta = null,
                    candidates = emptyList(),
                    onSwipe = { _, _ -> },
                    onRefreshLocation = {},
                    onLocationError = {},
                    onUpdateFilters = { _, _, _, _, _ -> },
                    onNavigateToProfile = {},
                    onNavigateToVerify = {},
                    latestLocation = liveLocation
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Live GPS • Oakland, CA • 100 km", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `discover card deck buttons trigger swipes and undo restores last card`() {
        val recordedSwipes = mutableListOf<Pair<String, String>>()
        val myProfile = PublicProfile(userId = "me", displayName = "Alex", age = 27)
        val firstCandidate = DiscoveryCandidate(
            profile = PublicProfile(userId = "user_alpha", displayName = "Sienna", age = 25, gender = "Woman"),
            distanceKm = 1.5,
            proximityMatchScore = 92
        )
        val secondCandidate = DiscoveryCandidate(
            profile = PublicProfile(userId = "user_beta", displayName = "Marcus", age = 30, gender = "Man"),
            distanceKm = 3.1,
            proximityMatchScore = 85
        )

        composeTestRule.setContent {
            var activeCandidates by remember { mutableStateOf(listOf(firstCandidate, secondCandidate)) }
            var lastRemoved by remember { mutableStateOf<DiscoveryCandidate?>(null) }
            KindredTheme {
                DiscoverScreen(
                    myProfile = myProfile,
                    privateUser = null,
                    geoHashMeta = null,
                    candidates = activeCandidates,
                    onSwipe = { candidate, direction ->
                        recordedSwipes += candidate.profile.userId to direction
                        lastRemoved = candidate
                        activeCandidates = activeCandidates.filterNot { it.profile.userId == candidate.profile.userId }
                    },
                    onRefreshLocation = {},
                    onLocationError = {},
                    onUpdateFilters = { _, _, _, _, _ -> },
                    onNavigateToProfile = {},
                    onNavigateToVerify = {},
                    canUndoLastSwipe = lastRemoved != null,
                    onUndoSwipe = {
                        lastRemoved?.let { restored ->
                            activeCandidates = listOf(restored) + activeCandidates
                            lastRemoved = null
                        }
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sienna, 25", useUnmergedTree = true).assertIsDisplayed()

        // Tap Pass button -> animates card out and advances to Marcus
        composeTestRule.onNodeWithTag("pass_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.waitForIdle()
        assertEquals(listOf("user_alpha" to "PASS"), recordedSwipes)
        composeTestRule.onNodeWithText("Marcus, 30", useUnmergedTree = true).assertIsDisplayed()

        // Tap Undo button -> restores Sienna to top of deck
        composeTestRule.onNodeWithTag("undo_swipe_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sienna, 25", useUnmergedTree = true).assertIsDisplayed()

        // Tap Like button -> animates card right and records LIKE
        composeTestRule.onNodeWithTag("like_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.waitForIdle()
        assertEquals(listOf("user_alpha" to "PASS", "user_alpha" to "LIKE"), recordedSwipes)
    }

    @Test
    fun `notification helper creates fcm channels and suppresses foreground active match notifications`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KindredNotificationHelper.ensureChannels(context)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val matchesChannel = manager.getNotificationChannel(KindredNotificationHelper.CHANNEL_MATCHES)
        val messagesChannel = manager.getNotificationChannel(KindredNotificationHelper.CHANNEL_MESSAGES)

        assertTrue(matchesChannel != null)
        assertTrue(messagesChannel != null)
        assertEquals(android.app.NotificationManager.IMPORTANCE_HIGH, matchesChannel.importance)
        assertEquals(android.app.NotificationManager.IMPORTANCE_HIGH, messagesChannel.importance)

        KindredNotificationHelper.activeMatchIdInForeground = "match_123"
        assertTrue(KindredNotificationHelper.shouldSuppressForegroundMessageNotification("match_123"))
        assertFalse(KindredNotificationHelper.shouldSuppressForegroundMessageNotification("match_999"))
        KindredNotificationHelper.activeMatchIdInForeground = null
        assertFalse(KindredNotificationHelper.shouldSuppressForegroundMessageNotification("match_123"))
    }

    @Test
    fun `chat screen displays read receipts, typing indicator, media bubbles, and marks unread read`() {
        val markedReadIds = mutableListOf<String>()
        val now = System.currentTimeMillis()
        val messages = listOf(
            ChatMessage(
                id = "msg_sent_only",
                matchId = sampleThread.match.id,
                senderId = "alice_1",
                participantUids = listOf("alice_1", "bob_2"),
                text = "Read receipt pending",
                messageType = "TEXT",
                readByUids = listOf("alice_1"),
                createdAt = Timestamp(Date(now - 20_000L))
            ),
            ChatMessage(
                id = "msg_read_by_partner",
                matchId = sampleThread.match.id,
                senderId = "alice_1",
                participantUids = listOf("alice_1", "bob_2"),
                text = "Partner saw this!",
                messageType = "TEXT",
                readByUids = listOf("alice_1", "bob_2"),
                createdAt = Timestamp(Date(now - 10_000L))
            ),
            ChatMessage(
                id = "msg_partner_voice",
                matchId = sampleThread.match.id,
                senderId = "bob_2",
                participantUids = listOf("alice_1", "bob_2"),
                text = "",
                messageType = "VOICE",
                mediaDurationSec = 7L,
                readByUids = listOf("bob_2"),
                createdAt = Timestamp(Date(now - 5_000L))
            ),
            ChatMessage(
                id = "msg_partner_gif",
                matchId = sampleThread.match.id,
                senderId = "bob_2",
                participantUids = listOf("alice_1", "bob_2"),
                text = "",
                messageType = "GIF",
                mediaGifId = "gif_cheers_clink",
                readByUids = listOf("alice_1", "bob_2"),
                disappearingSeconds = 30L,
                expiresAtMs = now + 28_000L,
                createdAt = Timestamp(Date(now - 2_000L))
            )
        )

        composeTestRule.setContent {
            KindredTheme {
                ChatScreen(
                    currentUserId = "alice_1",
                    thread = sampleThread,
                    messagesState = UiState.Success(messages),
                    partnerTyping = TypingStatus(
                        matchId = sampleThread.match.id,
                        userId = "bob_2",
                        isTyping = true,
                        updatedAt = Timestamp(Date(now))
                    ),
                    showBackButton = false,
                    onBack = {},
                    onSendMessage = { _, _, _, _ -> },
                    onMarkMessagesRead = { _, ids -> markedReadIds.addAll(ids) },
                    onUnmatch = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Auto-marks unread partner non-viewOnce message (msg_partner_voice) as read
        assertTrue(markedReadIds.contains("msg_partner_voice"))
        assertFalse(markedReadIds.contains("msg_partner_gif"))

        // Partner typing bubble + header status + bottom-most media bubbles
        composeTestRule.onNodeWithTag("partner_typing_indicator", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("typing…", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("gif_bubble_msg_partner_gif", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("timer_badge_msg_partner_gif", useUnmergedTree = true).assertIsDisplayed()

        // Scroll through message list to inspect voice bubble and sent/read receipts
        composeTestRule.onNodeWithTag("chat_message_list").performScrollToIndex(2)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("voice_bubble_msg_partner_voice", useUnmergedTree = true).assertIsDisplayed()

        composeTestRule.onNodeWithTag("chat_message_list").performScrollToIndex(1)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("read_receipt_read_msg_read_by_partner", useUnmergedTree = true).assertIsDisplayed()

        composeTestRule.onNodeWithTag("chat_message_list").performScrollToIndex(0)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("read_receipt_sent_msg_sent_only", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `chat screen filters expired disappearing messages and picks GIF + icebreaker + disappearing timer`() {
        val now = System.currentTimeMillis()
        val deletedIds = mutableListOf<String>()
        val sentMediaTypes = mutableListOf<String>()
        var updatedTtl = -1L

        val expiredMsg = ChatMessage(
            id = "expired_1",
            matchId = sampleThread.match.id,
            senderId = "bob_2",
            participantUids = listOf("alice_1", "bob_2"),
            text = "Already gone!",
            messageType = "TEXT",
            disappearingSeconds = 30L,
            expiresAtMs = now - 5_000L
        )
        val activeMsg = ChatMessage(
            id = "active_1",
            matchId = sampleThread.match.id,
            senderId = "bob_2",
            participantUids = listOf("alice_1", "bob_2"),
            text = "Still visible",
            messageType = "TEXT"
        )

        composeTestRule.setContent {
            KindredTheme {
                ChatScreen(
                    currentUserId = "alice_1",
                    thread = sampleThread,
                    messagesState = UiState.Success(listOf(expiredMsg, activeMsg)),
                    myProfile = PublicProfile(
                        userId = "alice_1",
                        displayName = "Alex",
                        interests = listOf("Coffee", "Photography")
                    ),
                    showBackButton = false,
                    onBack = {},
                    onSendMessage = { _, _, _, _ -> },
                    onSendMediaMessage = { _, _, type, _, _, _, _, _, _ ->
                        sentMediaTypes.add(type)
                    },
                    onDeleteExpiredMessage = { _, msgId -> deletedIds.add(msgId) },
                    onUpdateDisappearingTimer = { _, ttl -> updatedTtl = ttl },
                    onUnmatch = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(listOf("expired_1"), deletedIds)
        composeTestRule.onNodeWithText("Still visible").assertIsDisplayed()
        composeTestRule.onNodeWithText("Already gone!").assertDoesNotExist()

        // Open Disappearing Timer sheet & choose 5 minutes (300s)
        composeTestRule.onNodeWithTag("chat_timer_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("timer_option_300").performClick()
        composeTestRule.waitForIdle()
        assertEquals(300L, updatedTtl)

        // Open GIF picker & send curated reaction
        composeTestRule.onNodeWithTag("chat_gif_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gif_option_gif_coffee_cheers").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("GIF"), sentMediaTypes)
    }

    @Test
    fun `chat screen unmatch dialog offers both unmatch only and unmatch plus block actions`() {
        var unmatchedId = ""
        var blockedPair: Pair<String, String>? = null

        composeTestRule.setContent {
            KindredTheme {
                ChatScreen(
                    currentUserId = "alice_1",
                    thread = sampleThread,
                    messagesState = UiState.Success(emptyList()),
                    showBackButton = false,
                    onBack = {},
                    onSendMessage = { _, _, _, _ -> },
                    onUnmatch = { unmatchedId = it },
                    onBlockUser = { uid, name -> blockedPair = uid to name }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("unmatch_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_unmatch_and_block_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("bob_2" to "Elena", blockedPair)
        assertEquals("", unmatchedId)
    }

    @Test
    fun `chat screen shows incoming call banner and triggers voice and video call actions`() {
        var startedCallType = ""
        var acceptedCallId = ""

        composeTestRule.setContent {
            KindredTheme {
                ChatScreen(
                    currentUserId = "alice_1",
                    thread = sampleThread,
                    messagesState = UiState.Success(emptyList()),
                    activeCallSession = CallSession(
                        id = "call_99",
                        matchId = sampleThread.match.id,
                        callerUid = "bob_2",
                        calleeUid = "alice_1",
                        participantUids = listOf("alice_1", "bob_2"),
                        callType = "VIDEO",
                        status = "RINGING"
                    ),
                    showBackButton = false,
                    onBack = {},
                    onSendMessage = { _, _, _, _ -> },
                    onStartCall = { _, _, _, type -> startedCallType = type },
                    onAnswerCall = { acceptedCallId = it.id },
                    onUnmatch = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("incoming_call_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accept_call_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("call_99", acceptedCallId)

        composeTestRule.onNodeWithTag("chat_voice_call_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("VOICE", startedCallType)
    }

    @Test
    fun `ai icebreaker service builds shared-interest openers and parses Gemini JSON`() {
        val myProfile = PublicProfile(
            userId = "alice_1",
            displayName = "Alex",
            cityLabel = "San Francisco",
            interests = listOf("Coffee", "Photography")
        )
        val partner = PublicProfile(
            userId = "bob_2",
            displayName = "Elena",
            cityLabel = "San Francisco",
            bio = "Love vintage film cameras and weekend espresso tastings.",
            interests = listOf("Coffee", "Hiking")
        )

        val localOpeners = AiIcebreakerService.buildProfileBasedOpeners(myProfile, partner)
        assertTrue(localOpeners.isNotEmpty())
        assertTrue(localOpeners.any { it.prompt.contains("Coffee", ignoreCase = true) })

        val structuredJson = """
            {
              "openers": [
                {
                  "prompt": "Since we both love Coffee, best pour-over spot in SF?",
                  "tone": "Playful",
                  "reason": "Shared interest + city"
                },
                {
                  "prompt": "Film camera or digital for a weekend hike?",
                  "tone": "Curious",
                  "reason": "Connects your photos + bio"
                }
              ]
            }
        """.trimIndent()
        val parsed = AiIcebreakerService.parseStructuredOpeners(structuredJson)
        assertEquals(2, parsed.size)
        assertEquals("Playful", parsed[0].tone)
        assertTrue(parsed[1].prompt.contains("Film camera"))
    }

    private fun hypot(dx: Float, dy: Float): Float = kotlin.math.hypot(dx, dy)
}
