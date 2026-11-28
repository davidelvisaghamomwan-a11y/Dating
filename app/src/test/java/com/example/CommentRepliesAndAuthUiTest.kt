package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.PublicProfile
import com.example.ui.components.PostCommentsSheet
import com.example.ui.components.buildCommentThreadRows
import com.example.ui.screens.AuthScreen
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class CommentRepliesAndAuthUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun buildCommentThreadRows_nestsDirectAndNestedRepliesUnderRootAndKeepsOrphansVisible() {
        val comments = listOf(
            FeedComment(
                id = "c1",
                postId = "p1",
                userId = "u1",
                authorName = "Maya",
                text = "Love this!",
                createdAt = Timestamp(Date(1_000L))
            ),
            FeedComment(
                id = "c2",
                postId = "p1",
                userId = "u2",
                authorName = "Liam",
                text = "Awesome photo!",
                createdAt = Timestamp(Date(2_000L))
            ),
            FeedComment(
                id = "c3_reply_to_c1",
                postId = "p1",
                userId = "u3",
                authorName = "Sofia",
                text = "Me too!",
                replyToCommentId = "c1",
                replyToAuthorName = "Maya",
                replyToText = "Love this!",
                createdAt = Timestamp(Date(3_000L))
            ),
            FeedComment(
                id = "c4_reply_to_c3",
                postId = "p1",
                userId = "u1",
                authorName = "Maya",
                text = "Thank you Sofia!",
                replyToCommentId = "c3_reply_to_c1",
                replyToAuthorName = "Sofia",
                replyToText = "Me too!",
                createdAt = Timestamp(Date(4_000L))
            ),
            FeedComment(
                id = "c5_orphan",
                postId = "p1",
                userId = "u4",
                authorName = "Noah",
                text = "Agreed on that point",
                replyToCommentId = "deleted_parent",
                replyToAuthorName = "DeletedUser",
                createdAt = Timestamp(Date(5_000L))
            )
        )

        val rows = buildCommentThreadRows(comments)
        assertEquals(
            listOf("c1", "c3_reply_to_c1", "c4_reply_to_c3", "c2", "c5_orphan"),
            rows.map { it.comment.id }
        )
        assertFalse(rows[0].isReply)
        assertTrue(rows[1].isReply)
        assertTrue(rows[2].isReply)
        assertFalse(rows[3].isReply)
        assertFalse(rows[4].isReply) // Orphan stays visible at top level with replyToAuthorName badge
    }

    @Test
    fun postCommentsSheet_allowsSelectingReplyingCancelingAndSendingDirectReply() {
        composeTestRule.mainClock.autoAdvance = false
        val post = FeedPost(
            id = "post_1",
            userId = "author_1",
            authorName = "Jordan",
            caption = "Evening walk",
            mediaType = "PHOTO"
        )
        val initialComment = FeedComment(
            id = "c_maya",
            postId = "post_1",
            userId = "u_maya",
            authorName = "Maya",
            text = "What lens did you use?",
            createdAt = Timestamp(Date(1_000L))
        )
        val secondComment = FeedComment(
            id = "c_reply_existing",
            postId = "post_1",
            userId = "u_alex",
            authorName = "Alex",
            text = "Looks like 35mm!",
            replyToCommentId = "c_maya",
            replyToAuthorName = "Maya",
            createdAt = Timestamp(Date(2_000L))
        )

        var capturedText: String? = null
        var capturedReplyTo: FeedComment? = null

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    PostCommentsSheet(
                        post = post,
                        currentUserId = "me_uid",
                        myProfile = PublicProfile(userId = "me_uid", displayName = "Me"),
                        observeComments = { flowOf(listOf(initialComment, secondComment)) },
                        onAddComment = { _, text, replyTo, onDone ->
                            capturedText = text
                            capturedReplyTo = replyTo
                            onDone()
                        },
                        onDeleteComment = { _, _ -> },
                        onOpenProfile = {},
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(150)

        composeTestRule.onNodeWithTag("comment_reply_btn_c_maya").assertIsDisplayed()
        composeTestRule.onNodeWithText("Replying to @Maya").assertIsDisplayed()

        // Tap Reply on Maya's comment
        composeTestRule.onNodeWithTag("comment_reply_btn_c_maya").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onNodeWithTag("comment_replying_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Replying to Maya").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reply to Maya...").assertIsDisplayed()

        // Cancel reply banner
        composeTestRule.onNodeWithTag("comment_cancel_reply_btn").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)
        assertEquals(0, composeTestRule.onAllNodesWithTag("comment_replying_banner").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithText("Add a comment...").assertIsDisplayed()

        // Reply directly to Alex's existing reply
        composeTestRule.onNodeWithTag("comment_reply_btn_c_reply_existing").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onNodeWithText("Replying to Alex").assertIsDisplayed()

        composeTestRule.onNodeWithTag("comment_input_field").performTextInput("Yes, 35mm prime!")
        composeTestRule.mainClock.advanceTimeBy(50)
        composeTestRule.onNodeWithTag("comment_send_btn").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)

        assertEquals("Yes, 35mm prime!", capturedText)
        assertNotNull(capturedReplyTo)
        assertEquals("c_reply_existing", capturedReplyTo?.id)
        assertEquals("Alex", capturedReplyTo?.authorName)
        assertEquals(0, composeTestRule.onAllNodesWithTag("comment_replying_banner").fetchSemanticsNodes().size)
    }

    @Test
    fun authScreen_rendersCleanWhiteLoginLayoutWithoutWallpaperOverlay() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(412.dp, 915.dp)) {
                    AuthScreen(
                        onAuthSuccess = {},
                        enableAutoSignIn = false
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(150)

        composeTestRule.onNodeWithTag("auth_brand_wordmark").assertIsDisplayed()
        composeTestRule.onNodeWithText("Phone number, username or email").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("Forgot password?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_login_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Log In").assertIsDisplayed()
        composeTestRule.onNodeWithText("OR").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_switch_mode_text_button").assertIsDisplayed()

        // Toggle to Sign Up and verify extra fields appear cleanly
        composeTestRule.onNodeWithTag("auth_switch_mode_text_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.onNodeWithTag("auth_signup_name_input").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("auth_forgot_password_button").fetchSemanticsNodes().size)
    }
}
