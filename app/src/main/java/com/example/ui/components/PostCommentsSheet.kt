package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.PublicProfile
import com.example.ui.theme.CoralRose
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCommentsSheet(
    post: FeedPost,
    currentUserId: String,
    myProfile: PublicProfile?,
    profilesByUid: Map<String, PublicProfile> = emptyMap(),
    observeComments: (String) -> Flow<List<FeedComment>>,
    onAddComment: (postId: String, text: String, onDone: () -> Unit) -> Unit,
    onDeleteComment: (postId: String, commentId: String) -> Unit,
    onToggleCommentLike: (postId: String, commentId: String, currentlyLiked: Boolean) -> Unit = { _, _, _ -> },
    onOpenProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val comments by observeComments(post.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var commentText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        modifier = Modifier.testTag("post_comments_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .imePadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Comments (${comments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Post caption snippet
            if (post.caption.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileAvatarImage(
                        photoDataUrl = post.authorPhoto,
                        displayName = post.authorName,
                        seed = post.userId,
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        initialsFontSizeSp = 13
                    )
                    Column {
                        val isPostAuthorVerified = profilesByUid[post.userId]?.isProfileVerified ?: post.authorVerified
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = post.authorName.ifBlank { "Author" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isPostAuthorVerified) {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF0095F6),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Text(
                            text = post.caption,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Comments list
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .height(300.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (comments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No comments yet. Be the first to reply!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(comments, key = { it.id }) { comment ->
                        val timeStr = remember(comment.createdAt) {
                            comment.createdAt?.toDate()?.let {
                                SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
                            } ?: "Now"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpenProfile(comment.userId) },
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ProfileAvatarImage(
                                    photoDataUrl = comment.authorPhoto,
                                    displayName = comment.authorName,
                                    seed = comment.userId,
                                    modifier = Modifier.size(32.dp),
                                    shape = CircleShape,
                                    initialsFontSizeSp = 12
                                )
                                Column {
                                    val isCommenterVerified = profilesByUid[comment.userId]?.isProfileVerified ?: comment.authorVerified
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = comment.authorName.ifBlank { "Member" },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isCommenterVerified) {
                                            Icon(
                                                imageVector = Icons.Filled.Verified,
                                                contentDescription = "Verified commenter",
                                                tint = Color(0xFF0095F6),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Text(
                                            text = timeStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = comment.text,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            val isCommentLiked = currentUserId in comment.likedByUids
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                IconButton(
                                    onClick = { onToggleCommentLike(post.id, comment.id, isCommentLiked) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCommentLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = "Like comment",
                                        tint = if (isCommentLiked) CoralRose else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                if (comment.likedByUids.isNotEmpty()) {
                                    Text(
                                        text = "${comment.likedByUids.size}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (comment.userId == currentUserId || post.userId == currentUserId) {
                                    IconButton(
                                        onClick = { onDeleteComment(post.id, comment.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete comment",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Input Bar
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileAvatarImage(
                    photoDataUrl = myProfile?.photoDataUrl,
                    displayName = myProfile?.displayName ?: "Me",
                    seed = myProfile?.avatarSeed ?: "me",
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    initialsFontSizeSp = 14
                )

                OutlinedTextField(
                    value = commentText,
                    onValueChange = { if (it.length <= 400) commentText = it },
                    placeholder = { Text("Add a comment...") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("comment_input_field")
                )

                IconButton(
                    onClick = {
                        val text = commentText.trim()
                        if (text.isNotBlank()) {
                            isSending = true
                            onAddComment(post.id, text) {
                                isSending = false
                                commentText = ""
                            }
                        }
                    },
                    enabled = commentText.isNotBlank() && !isSending,
                    modifier = Modifier.testTag("comment_send_btn")
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CoralRose)
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Post Comment",
                            tint = if (commentText.isNotBlank()) CoralRose else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
