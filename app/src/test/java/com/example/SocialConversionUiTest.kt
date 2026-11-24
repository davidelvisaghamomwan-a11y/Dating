package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.model.ActiveMemberPresenceItem
import com.example.model.DiscoveryCandidate
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.FollowRecord
import com.example.model.PublicProfile
import com.example.model.SocialActivityItem
import com.example.model.UiState
import com.example.model.UserPresence
import com.example.ui.screens.ActivityNotificationsScreen
import com.example.ui.screens.ExploreSearchScreen
import com.example.ui.screens.FriendsHubScreen
import com.example.ui.screens.MatchesAndChatScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class SocialConversionUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun activityNotificationsScreen_displaysSocialAlertsAndFollowBack() {
        val follower = PublicProfile(userId = "u_alex", displayName = "Alex Mercer")
        val liker = PublicProfile(userId = "u_maya", displayName = "Maya Lin")
        val myPost = FeedPost(
            id = "p_1",
            userId = "me_1",
            authorName = "Me",
            caption = "Morning coffee"
        )
        val activities = listOf(
            SocialActivityItem.NewFollower(
                id = "act_follow_1",
                timestampMs = 3000L,
                actorUid = follower.userId,
                actorName = follower.displayName,
                actorPhoto = null,
                actorVerified = false,
                follow = FollowRecord(
                    id = "f1",
                    followerUid = follower.userId,
                    followingUid = "me_1",
                    followerName = follower.displayName
                )
            ),
            SocialActivityItem.PostLike(
                id = "act_like_1",
                timestampMs = 2000L,
                actorUid = liker.userId,
                actorName = liker.displayName,
                actorPhoto = null,
                actorVerified = true,
                post = myPost
            ),
            SocialActivityItem.PostComment(
                id = "act_comment_1",
                timestampMs = 1000L,
                actorUid = liker.userId,
                actorName = liker.displayName,
                actorPhoto = null,
                actorVerified = true,
                post = myPost,
                comment = FeedComment(id = "c1", userId = liker.userId, authorName = liker.displayName, text = "Great shot!")
            )
        )

        var toggledFollowUid: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    ActivityNotificationsScreen(
                        activities = activities,
                        followingIds = emptySet(),
                        followerIds = setOf(follower.userId),
                        profileByUid = mapOf(follower.userId to follower, liker.userId to liker),
                        onToggleFollow = { toggledFollowUid = it },
                        onOpenProfile = {},
                        onOpenReels = {},
                        onOpenExplore = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("activity_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("activity_item_act_follow_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("activity_item_act_like_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("activity_item_act_comment_1").assertIsDisplayed()

        composeTestRule.onNodeWithTag("activity_follow_btn_${follower.userId}").performClick()
        assertEquals(follower.userId, toggledFollowUid)

        composeTestRule.onNodeWithTag("activity_filter_comments").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("activity_item_act_comment_1").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("activity_item_act_follow_1").assertCountEquals(0)
    }

    @Test
    fun friendsHubScreen_supportsFollowersFollowingAndSuggestedTabs() {
        val follower = PublicProfile(userId = "u_f1", displayName = "Jordan Patel", cityLabel = "Austin")
        val following = PublicProfile(userId = "u_f2", displayName = "Sora Takahashi", cityLabel = "Tokyo")
        val suggested = PublicProfile(userId = "u_s1", displayName = "Nadia K", cityLabel = "Paris")

        var toggledUid: String? = null
        var removedFollowerUid: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    FriendsHubScreen(
                        currentUserId = "me_1",
                        followerIds = setOf(follower.userId),
                        followingIds = setOf(following.userId),
                        suggestedProfiles = listOf(suggested),
                        profilesByUid = mapOf(
                            follower.userId to follower,
                            following.userId to following,
                            suggested.userId to suggested
                        ),
                        initialTab = 0,
                        onBack = {},
                        onOpenProfile = {},
                        onMessageProfile = {},
                        onToggleFollow = { toggledUid = it },
                        onRemoveFollower = { removedFollowerUid = it }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("friends_hub_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("follow_hub_card_${follower.userId}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("follower_toggle_btn_${follower.userId}").performClick()
        assertEquals(follower.userId, toggledUid)

        composeTestRule.onNodeWithTag("remove_follower_${follower.userId}").performClick()
        assertEquals(follower.userId, removedFollowerUid)

        composeTestRule.onNodeWithTag("hub_tab_following").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("follow_hub_card_${following.userId}").assertIsDisplayed()

        composeTestRule.onNodeWithTag("hub_tab_suggested").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("follow_hub_card_${suggested.userId}").assertIsDisplayed()
    }

    @Test
    fun matchesAndChatScreen_allowsMessagingFollowingWithoutMatch() {
        val followedCreator = PublicProfile(userId = "creator_1", displayName = "Clara Vance", cityLabel = "Berlin")
        var messagedProfileId: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    MatchesAndChatScreen(
                        currentUserId = "me_1",
                        myProfile = PublicProfile(userId = "me_1", displayName = "Me"),
                        matches = emptyList(),
                        selectedMatchId = null,
                        messagesState = UiState.Success(emptyList()),
                        followingProfiles = listOf(followedCreator),
                        onlineUserIds = setOf(followedCreator.userId),
                        onMessageProfile = { messagedProfileId = it.userId },
                        onSelectMatch = {},
                        onSendMessage = { _, _, _, _ -> },
                        onUnmatch = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("inbox_following_dm_strip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("inbox_following_dm_${followedCreator.userId}").performClick()
        assertEquals(followedCreator.userId, messagedProfileId)
    }

    @Test
    fun exploreSearchScreen_showsActiveUsersStripAndFollowSearchRow() {
        val activeUser = PublicProfile(userId = "u_active", displayName = "Kai Rivera", cityLabel = "Miami")
        var followedUid: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    ExploreSearchScreen(
                        currentUserId = "me_1",
                        myProfile = PublicProfile(userId = "me_1", displayName = "Me"),
                        searchableProfiles = listOf(DiscoveryCandidate(profile = activeUser, distanceKm = 4.0)),
                        feedPosts = listOf(
                            FeedPost(
                                id = "fp_1",
                                userId = activeUser.userId,
                                authorName = activeUser.displayName,
                                caption = "Beach walk",
                                mediaType = "PHOTO",
                                mediaDataUrl = "https://example.com/p.jpg",
                                createdAt = Timestamp(100L, 0)
                            )
                        ),
                        videoPosts = emptyList(),
                        profilesByUid = mapOf(activeUser.userId to activeUser),
                        activeMembers = listOf(
                            ActiveMemberPresenceItem(
                                profile = activeUser,
                                presence = UserPresence(userId = activeUser.userId, online = true),
                                isOnline = true,
                                isRecentlyLeft = false,
                                statusLabel = "Active now"
                            )
                        ),
                        isFollowingUid = { false },
                        onToggleFollow = { followedUid = it.userId },
                        onOpenUserProfile = {},
                        onToggleFeedPostLike = { _, _ -> },
                        onToggleFeedPostSave = { _, _ -> },
                        resolveFeedPostVideoFile = { Result.failure(IllegalStateException("none")) }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("explore_active_users_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explore_active_user_${activeUser.userId}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explore_search_input").performTextInput("Kai")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("explore_user_row_${activeUser.userId}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explore_user_follow_${activeUser.userId}").performClick()
        assertEquals(activeUser.userId, followedUid)
    }

    @Test
    fun settingsScreen_removesDatingFiltersAndShowsSocialRows() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    SettingsScreen(
                        myProfile = PublicProfile(userId = "me_1", displayName = "Me"),
                        privateUser = null,
                        isBusy = false,
                        onBack = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_row_friends").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_row_activity").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Discovery & feed preferences").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Interested in").assertCountEquals(0)
    }

    @Test
    fun profileScreen_showsFollowerFollowingCountsAndTriggersHubCallback() {
        var openedTab = -1
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    ProfileScreen(
                        currentUserId = "me_1",
                        userEmail = "me@example.com",
                        defaultDisplayName = "Me",
                        myProfile = PublicProfile(userId = "me_1", displayName = "Me"),
                        followersCount = 14,
                        followingCount = 9,
                        onOpenFollowersFollowing = { openedTab = it }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_stat_followers").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_stat_following").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Followers")[0].assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Following")[0].assertIsDisplayed()

        composeTestRule.onNodeWithTag("profile_stat_following").performClick()
        assertEquals(1, openedTab)
    }
}
