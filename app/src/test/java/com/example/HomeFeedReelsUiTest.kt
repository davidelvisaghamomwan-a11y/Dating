package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.example.model.FeedPost
import com.example.model.PublicProfile
import com.example.model.VideoPost
import com.example.ui.screens.HomeFeedScreen
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class HomeFeedReelsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeFeedScreen_displaysOtherUsersReelVideoPictureAndTextPost() {
        val currentUser = PublicProfile(userId = "me_1", displayName = "Me")
        val otherUser = PublicProfile(
            userId = "user_2",
            displayName = "Elena Vance",
            cityLabel = "London",
            country = "United Kingdom"
        )

        val picturePost = FeedPost(
            id = "pic_1",
            userId = otherUser.userId,
            authorName = otherUser.displayName,
            caption = "Sunset by the Thames #london",
            mediaType = "PHOTO",
            mediaDataUrl = "https://example.com/photo.jpg",
            createdAt = Timestamp(400L, 0)
        )
        val otherReel = VideoPost(
            id = "reel_1",
            userId = otherUser.userId,
            authorName = otherUser.displayName,
            caption = "City walk reel #reels",
            mediaDurationSec = 14L,
            createdAt = Timestamp(300L, 0)
        )
        val videoPost = FeedPost(
            id = "vid_1",
            userId = otherUser.userId,
            authorName = otherUser.displayName,
            caption = "Night lights video clip",
            mediaType = "VIDEO",
            mediaDurationSec = 10L,
            createdAt = Timestamp(200L, 0)
        )
        val textPost = FeedPost(
            id = "txt_1",
            userId = otherUser.userId,
            authorName = otherUser.displayName,
            caption = "Coffee spot recommendations?",
            mediaType = "TEXT",
            createdAt = Timestamp(100L, 0)
        )

        var openedReelId: String? = null
        var likedReelId: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    HomeFeedScreen(
                        myProfile = currentUser,
                        currentUserId = currentUser.userId,
                        feedPosts = listOf(picturePost, videoPost, textPost),
                        videoPosts = listOf(otherReel),
                        profilesByUid = mapOf(
                            currentUser.userId to currentUser,
                            otherUser.userId to otherUser
                        ),
                        onToggleVideoPostLike = { postId, _ -> likedReelId = postId },
                        onOpenReelInVideoFeed = { reelId -> openedReelId = reelId }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Picture post at top
        composeTestRule.onNodeWithTag("feed_post_card_pic_1").assertIsDisplayed()

        // Suggested Reels shelf appears right under first post showing other user's reel
        composeTestRule.onNodeWithTag("social_timeline_feed")
            .performScrollToNode(hasTestTag("home_suggested_reels_shelf"))
        composeTestRule.onNodeWithTag("home_suggested_reels_shelf").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_shelf_reel_reel_1").assertIsDisplayed()

        // Inline Reel card in Home timeline
        composeTestRule.onNodeWithTag("social_timeline_feed")
            .performScrollToNode(hasTestTag("home_reel_card_reel_1"))
        composeTestRule.onNodeWithTag("home_reel_card_reel_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("social_timeline_feed")
            .performScrollToNode(hasTestTag("home_reel_like_reel_1"))
        composeTestRule.onNodeWithTag("home_reel_like_reel_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals("reel_1", likedReelId)

        // Video post and Text post in Home timeline
        composeTestRule.onNodeWithTag("social_timeline_feed")
            .performScrollToNode(hasTestTag("feed_post_card_vid_1"))
        composeTestRule.onNodeWithTag("feed_post_card_vid_1").assertIsDisplayed()

        composeTestRule.onNodeWithTag("social_timeline_feed")
            .performScrollToNode(hasTestTag("feed_post_card_txt_1"))
        composeTestRule.onNodeWithTag("feed_post_card_txt_1").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Coffee spot recommendations?", substring = true)[0].assertIsDisplayed()

        // Clicking Suggested Reel shelf card opens full-screen Reels at that reel
        composeTestRule.onNodeWithTag("social_timeline_feed")
            .performScrollToNode(hasTestTag("home_suggested_reels_shelf"))
        composeTestRule.onNodeWithTag("home_shelf_reel_reel_1").performClick()
        assertEquals("reel_1", openedReelId)
    }

    @Test
    fun homeFeedScreen_deletingOwnPostRemovesItImmediatelyAfterConfirm() {
        val currentUser = PublicProfile(userId = "me_1", displayName = "Me")
        val myPost = FeedPost(
            id = "my_post_doc_1",
            postId = "my_post_doc_1",
            userId = currentUser.userId,
            authorName = currentUser.displayName,
            caption = "Temporary update",
            mediaType = "PHOTO",
            mediaDataUrl = "https://example.com/update.jpg",
            createdAt = Timestamp(500L, 0)
        )
        val postsState = androidx.compose.runtime.mutableStateOf(listOf(myPost))
        var deletedPostId: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    HomeFeedScreen(
                        myProfile = currentUser,
                        currentUserId = currentUser.userId,
                        feedPosts = postsState.value,
                        profilesByUid = mapOf(currentUser.userId to currentUser),
                        onDeleteFeedPost = { postId ->
                            deletedPostId = postId
                            postsState.value = postsState.value.filterNot { it.id == postId }
                        }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("feed_post_card_${myPost.id}").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Post options").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Delete Post")[0].performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_delete_post_${myPost.id}").performClick()
        composeTestRule.waitForIdle()

        assertEquals(myPost.id, deletedPostId)
        composeTestRule.onAllNodesWithText("Temporary update").let { nodes ->
            org.junit.Assert.assertEquals(0, nodes.fetchSemanticsNodes().size)
        }
    }
}
