package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.model.FeedPost
import com.example.model.KindredConstants
import com.example.model.ModerationNoticeRecord
import com.example.model.PublicProfile
import com.example.model.ReportRecord
import com.example.model.SocialActivityItem
import com.example.ui.screens.ActivityNotificationsScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.HomeFeedScreen
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class ModerationRemovalUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun moderatorCanDeleteViolatingPostFromFeedWithReasonAndDetails() {
        val moderator = PublicProfile(userId = "mod_1", displayName = "Moderator")
        val offendingUser = PublicProfile(userId = "user_2", displayName = "Alex Offender")
        val offendingPost = FeedPost(
            id = "bad_post_1",
            postId = "bad_post_1",
            userId = offendingUser.userId,
            authorName = offendingUser.displayName,
            caption = "Violating spam link",
            mediaType = "PHOTO",
            mediaDataUrl = "https://example.com/spam.jpg",
            createdAt = Timestamp(600L, 0)
        )
        val postsState = mutableStateOf(listOf(offendingPost))
        var capturedReason: String? = null
        var capturedDetails: String? = null
        var deletedId: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    HomeFeedScreen(
                        myProfile = moderator,
                        currentUserId = moderator.userId,
                        feedPosts = postsState.value,
                        profilesByUid = mapOf(
                            moderator.userId to moderator,
                            offendingUser.userId to offendingUser
                        ),
                        isModerator = true,
                        resolveFeedPostVideoFile = { Result.failure<File>(IllegalStateException("unused")) },
                        onModeratorDeleteFeedPost = { post, reason, details ->
                            deletedId = post.id
                            capturedReason = reason
                            capturedDetails = details
                            postsState.value = postsState.value.filterNot { it.id == post.id }
                        }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("feed_post_card_bad_post_1").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Post options").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("moderator_delete_post_menu_bad_post_1").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("moderator_delete_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("moderator_reason_option_1").performClick()
        composeTestRule.onNodeWithTag("moderator_reason_details_input")
            .performTextInput("Repeated promotional spam")
        composeTestRule.onNodeWithTag("moderator_confirm_delete_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals("bad_post_1", deletedId)
        assertEquals(KindredConstants.MODERATION_VIOLATION_REASONS[1], capturedReason)
        assertEquals("Repeated promotional spam", capturedDetails)
        assertEquals(0, composeTestRule.onAllNodesWithText("Violating spam link").fetchSemanticsNodes().size)
    }

    @Test
    fun nonModeratorDoesNotSeeModeratorRemoveOption() {
        val regularUser = PublicProfile(userId = "viewer_1", displayName = "Viewer")
        val otherUser = PublicProfile(userId = "user_2", displayName = "Author")
        val post = FeedPost(
            id = "regular_post_1",
            postId = "regular_post_1",
            userId = otherUser.userId,
            authorName = otherUser.displayName,
            caption = "Normal photo",
            mediaType = "PHOTO",
            mediaDataUrl = "https://example.com/normal.jpg",
            createdAt = Timestamp(500L, 0)
        )

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    HomeFeedScreen(
                        myProfile = regularUser,
                        currentUserId = regularUser.userId,
                        feedPosts = listOf(post),
                        profilesByUid = mapOf(
                            regularUser.userId to regularUser,
                            otherUser.userId to otherUser
                        ),
                        isModerator = false,
                        resolveFeedPostVideoFile = { Result.failure<File>(IllegalStateException("unused")) }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Post options").performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            0,
            composeTestRule.onAllNodesWithText("Remove Post (Moderator)").fetchSemanticsNodes().size
        )
    }

    @Test
    fun adminDashboardAllowsRemovingPostFromReportAndContentTab() {
        val targetUser = PublicProfile(userId = "user_2", displayName = "Jordan", age = 26)
        val reportedPost = FeedPost(
            id = "post_rep_1",
            postId = "post_rep_1",
            userId = targetUser.userId,
            authorName = targetUser.displayName,
            caption = "Harassing comment screenshot",
            mediaType = "TEXT",
            createdAt = Timestamp(700L, 0)
        )
        val report = ReportRecord(
            id = "report_1",
            reporterUid = "user_3",
            reportedUid = targetUser.userId,
            reason = "Harassment",
            sourceSurface = "HOME_FEED",
            status = "OPEN"
        )
        var capturedReportId: String? = null
        var capturedReason: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    AdminDashboardScreen(
                        reports = listOf(report),
                        allProfiles = listOf(targetUser),
                        onBack = {},
                        onUpdateReportStatus = { _, _ -> },
                        onSetUserSuspended = { _, _ -> },
                        onOpenProfile = {},
                        feedPosts = listOf(reportedPost),
                        onModeratorDeleteFeedPost = { _, reason, _, relatedReportId ->
                            capturedReason = reason
                            capturedReportId = relatedReportId
                        }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("admin_report_remove_post_report_1_post_rep_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("moderator_reason_option_2").performClick()
        composeTestRule.onNodeWithTag("moderator_confirm_delete_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals("report_1", capturedReportId)
        assertEquals(KindredConstants.MODERATION_VIOLATION_REASONS[2], capturedReason)

        composeTestRule.onNodeWithTag("admin_tab_content").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("admin_remove_content_post_rep_1").assertIsDisplayed()
    }

    @Test
    fun affectedUserSeesGuidelineViolationReasonInActivityAndMarksNoticeRead() {
        val notice = ModerationNoticeRecord(
            id = "notice_42",
            targetUid = "user_2",
            moderatorUid = "mod_1",
            contentType = "POST",
            contentId = "bad_post_1",
            contentPreview = "Violating spam link",
            reason = "Spam or Misleading Links",
            details = "Repeated promotional spam",
            read = false,
            createdAt = Timestamp(800L, 0)
        )
        val activities = listOf(
            SocialActivityItem.ModerationRemoval(
                id = "mod_notice_42",
                actorUid = "mod_1",
                actorName = "Moderation Team",
                actorPhoto = null,
                timestampMs = 800_000L,
                notice = notice
            )
        )
        var markedReadId: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    ActivityNotificationsScreen(
                        activities = activities,
                        followingIds = emptySet(),
                        followerIds = emptySet(),
                        profileByUid = emptyMap(),
                        onToggleFollow = {},
                        onOpenProfile = {},
                        onOpenReels = {},
                        onOpenExplore = {},
                        onMarkModerationNoticeRead = { markedReadId = it }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("activity_filter_notices").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("activity_item_mod_notice_42").assertIsDisplayed()
        composeTestRule.onNodeWithTag("activity_mod_notice_notice_42", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("activity_item_mod_notice_42").performClick()
        composeTestRule.waitForIdle()

        assertEquals("notice_42", markedReadId)
        composeTestRule.onNodeWithTag("moderation_notice_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reason: Spam or Misleading Links").assertIsDisplayed()
        composeTestRule.onNodeWithText("Repeated promotional spam").assertIsDisplayed()
    }
}
