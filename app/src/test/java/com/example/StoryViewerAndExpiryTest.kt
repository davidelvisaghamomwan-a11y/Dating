package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.model.PublicProfile
import com.example.model.StoryItem
import com.example.ui.components.StoryViewerDialog
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class StoryViewerAndExpiryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun storyViewer_showsPinnedReplyBarAndReactionsForOtherUsersStoryWithoutViewCount() {
        composeTestRule.mainClock.autoAdvance = false
        val otherStory = StoryItem(
            id = "story_other_1",
            userId = "user_other",
            authorName = "Maya Lin",
            mediaType = "PHOTO",
            mediaDataUrl = "https://example.com/tall_story.jpg",
            caption = "Golden hour",
            viewerUids = listOf("user_other", "me_1", "user_3"),
            expiresAtMs = System.currentTimeMillis() + 36_000_000L
        )
        var sentReplyRecipient: String? = null
        var sentReplyText: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    StoryViewerDialog(
                        stories = listOf(otherStory),
                        initialIndex = 0,
                        currentUserId = "me_1",
                        profilesByUid = mapOf(
                            "user_other" to PublicProfile(userId = "user_other", displayName = "Maya Lin")
                        ),
                        resolveVideoFile = { Result.failure(IllegalStateException("Unused")) },
                        onMarkViewed = {},
                        onDeleteStory = {},
                        onOpenAuthorProfile = {},
                        onReplyToStory = { authorUid, replyText ->
                            sentReplyRecipient = authorUid
                            sentReplyText = replyText
                        },
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100)

        composeTestRule.onNodeWithTag("story_viewer_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_media_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_bottom_controls_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Send a reply...").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_reply_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_reply_send_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_quick_reaction_❤️").assertIsDisplayed()

        // Verify media container is strictly above the pinned bottom controls bar (no overlap)
        val mediaBounds = composeTestRule.onNodeWithTag("story_media_container")
            .fetchSemanticsNode().boundsInRoot
        val bottomBarBounds = composeTestRule.onNodeWithTag("story_bottom_controls_bar")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Media container bottom (${mediaBounds.bottom}) must not overlap bottom controls top (${bottomBarBounds.top})",
            mediaBounds.bottom <= bottomBarBounds.top + 1f
        )

        // View count must NOT be displayed when viewing another user's story
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag("story_viewer_list_trigger").fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag("story_owner_view_count_header").fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText("3 views").fetchSemanticsNodes().size
        )

        // Verify typing and sending a reply works
        composeTestRule.onNodeWithTag("story_reply_input").performTextInput("Love this shot!")
        composeTestRule.mainClock.advanceTimeBy(50)
        composeTestRule.onNodeWithTag("story_reply_send_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(50)

        assertEquals("user_other", sentReplyRecipient)
        assertEquals("Love this shot!", sentReplyText)
    }

    @Test
    fun storyViewer_showsViewCountInsteadOfReplyBarForOwnStory() {
        composeTestRule.mainClock.autoAdvance = false
        val ownStory = StoryItem(
            id = "story_mine_1",
            userId = "me_1",
            authorName = "Me",
            mediaType = "PHOTO",
            mediaDataUrl = "https://example.com/my_story.jpg",
            viewerUids = listOf("me_1", "friend_2"),
            expiresAtMs = System.currentTimeMillis() + 36_000_000L
        )

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    StoryViewerDialog(
                        stories = listOf(ownStory),
                        initialIndex = 0,
                        currentUserId = "me_1",
                        resolveVideoFile = { Result.failure(IllegalStateException("Unused")) },
                        onMarkViewed = {},
                        onDeleteStory = {},
                        onOpenAuthorProfile = {},
                        onReplyToStory = { _, _ -> },
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100)

        composeTestRule.onNodeWithTag("story_owner_view_count_header", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_viewer_list_trigger").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 viewers").assertIsDisplayed()

        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag("story_reply_input").fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag("story_quick_reaction_❤️").fetchSemanticsNodes().size
        )
    }

    @Test
    fun storyExpiration_detects24HourBoundaryAcrossTimestampsAndRemainingTtl() {
        val nowMs = 1_750_000_000_000L
        val activeStory = StoryItem(
            id = "active_1",
            userId = "u1",
            expiresAt = Timestamp(Date(nowMs + 3_600_000L)),
            expiresAtMs = nowMs + 3_600_000L
        )
        assertFalse(activeStory.isExpiredAt(nowMs))
        assertEquals(3_600_000L, activeStory.remainingTtlMs(nowMs))

        val expiredByTimestamp = StoryItem(
            id = "exp_1",
            userId = "u1",
            expiresAt = Timestamp(Date(nowMs - 1_000L)),
            expiresAtMs = nowMs - 1_000L
        )
        assertTrue(expiredByTimestamp.isExpiredAt(nowMs))
        assertEquals(0L, expiredByTimestamp.remainingTtlMs(nowMs))

        val expiredByCreatedAtFallback = StoryItem(
            id = "exp_2",
            userId = "u1",
            expiresAt = null,
            expiresAtMs = 0L,
            createdAt = Timestamp(Date(nowMs - StoryItem.STORY_TTL_MS - 500L))
        )
        assertTrue(expiredByCreatedAtFallback.isExpiredAt(nowMs))
        assertEquals(0L, expiredByCreatedAtFallback.remainingTtlMs(nowMs))

        val freshByCreatedAtFallback = StoryItem(
            id = "fresh_2",
            userId = "u1",
            expiresAt = null,
            expiresAtMs = 0L,
            createdAt = Timestamp(Date(nowMs - 60_000L))
        )
        assertFalse(freshByCreatedAtFallback.isExpiredAt(nowMs))
        assertEquals(StoryItem.STORY_TTL_MS - 60_000L, freshByCreatedAtFallback.remainingTtlMs(nowMs))
    }
}
