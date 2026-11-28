package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

data class CommentThreadRow(
    val comment: FeedComment,
    val isReply: Boolean
)

/**
 * Organizes comments into top-level threads with direct replies nested chronologically
 * beneath their root parent comment. Orphaned replies (whose parent was deleted) remain visible
 * at the top level with their "Replying to @Author" context intact.
 */
fun buildCommentThreadRows(comments: List<FeedComment>): List<CommentThreadRow> {
    if (comments.isEmpty()) return emptyList()
    val byId = comments.associateBy { it.id }

    fun findRootAncestorId(comment: FeedComment): String? {
        var parentId = comment.replyToCommentId?.trim().orEmpty()
        if (parentId.isEmpty() || parentId !in byId) return null
        val visited = mutableSetOf(comment.id)
        var currentRoot = parentId
        while (true) {
            if (!visited.add(currentRoot)) break
            val parent = byId[currentRoot] ?: break
            val grandParentId = parent.replyToCommentId?.trim().orEmpty()
            if (grandParentId.isEmpty() || grandParentId !in byId) {
                return parent.id
            }
            currentRoot = grandParentId
        }
        return currentRoot
    }

    val repliesByRoot = linkedMapOf<String, MutableList<FeedComment>>()
    val topLevel = mutableListOf<FeedComment>()

    for (comment in comments) {
        val rootId = findRootAncestorId(comment)
        if (rootId == null) {
            topLevel += comment
        } else {
            repliesByRoot.getOrPut(rootId) { mutableListOf() }.add(comment)
        }
    }

    val result = ArrayList<CommentThreadRow>(comments.size)
    for (root in topLevel) {
        result += CommentThreadRow(comment = root, isReply = false)
        repliesByRoot[root.id]?.forEach { reply ->
            result += CommentThreadRow(comment = reply, isReply = true)
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCommentsSheet(
    post: FeedPost,
    currentUserId: String,
    myProfile: PublicProfile?,
    profilesByUid: Map<String, PublicProfile> = emptyMap(),
    observeComments: (String) -> Flow<List<FeedComment>>,
    onAddComment: (postId: String, text: String, replyTo: FeedComment?, onDone: () -> Unit) -> Unit,
    onDeleteComment: (postId: String, commentId: String) -> Unit,
    onToggleCommentLike: (postId: String, commentId: String, currentlyLiked: Boolean) -> Unit = { _, _, _ -> },
    onOpenProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val comments by observeComments(post.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val threadRows = remember(comments) { buildCommentThreadRows(comments) }
    var commentText by remember { mutableStateOf("") }
    var replyingToComment by remember { mutableStateOf<FeedComment?>(null) }
    var isSending by remember { mutableStateOf(false) }
    val inputFocusRequester = remember { FocusRequester() }

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
                        Text(
                            text = post.authorName.ifBlank { "Author" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
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
                    .height(320.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (threadRows.isEmpty()) {
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
                    items(threadRows, key = { it.comment.id }) { row ->
                        val comment = row.comment
                        val resolvedProfile = profilesByUid[comment.userId]
                        val displayPhoto = comment.authorPhoto ?: resolvedProfile?.allProfilePhotos()?.firstOrNull()
                        val displayAuthorName = comment.authorName.ifBlank {
                            resolvedProfile?.displayName?.ifBlank { "Member" } ?: "Member"
                        }
                        val timeStr = remember(comment.createdAt) {
                            comment.createdAt?.toDate()?.let {
                                SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
                            } ?: "Now"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (row.isReply) {
                                        Modifier
                                            .padding(start = 26.dp)
                                            .height(IntrinsicSize.Min)
                                    } else {
                                        Modifier
                                    }
                                )
                                .testTag("comment_item_${comment.id}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            if (row.isReply) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier.clickable { onOpenProfile(comment.userId) }
                                ) {
                                    ProfileAvatarImage(
                                        photoDataUrl = displayPhoto,
                                        displayName = displayAuthorName,
                                        seed = comment.userId,
                                        modifier = Modifier.size(if (row.isReply) 26.dp else 32.dp),
                                        shape = CircleShape,
                                        initialsFontSizeSp = if (row.isReply) 10 else 12
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = displayAuthorName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { onOpenProfile(comment.userId) }
                                        )
                                        Text(
                                            text = timeStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (!comment.replyToAuthorName.isNullOrBlank()) {
                                        Text(
                                            text = "Replying to @${comment.replyToAuthorName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CoralRose,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                    }

                                    Text(
                                        text = comment.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = "Reply",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .clickable {
                                                    replyingToComment = comment.copy(authorName = displayAuthorName)
                                                    runCatching { inputFocusRequester.requestFocus() }
                                                }
                                                .padding(vertical = 4.dp, horizontal = 2.dp)
                                                .testTag("comment_reply_btn_${comment.id}")
                                        )
                                    }
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

            // Active reply indicator banner above input bar
            AnimatedVisibility(visible = replyingToComment != null) {
                replyingToComment?.let { target ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("comment_replying_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = null,
                                    tint = CoralRose,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Replying to ${target.authorName.ifBlank { "Member" }}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = CoralRose,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (target.text.isNotBlank()) {
                                        Text(
                                            text = target.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { replyingToComment = null },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("comment_cancel_reply_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cancel reply",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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

                val inputPlaceholder = replyingToComment?.let {
                    "Reply to ${it.authorName.ifBlank { "Member" }}..."
                } ?: "Add a comment..."

                OutlinedTextField(
                    value = commentText,
                    onValueChange = { if (it.length <= 400) commentText = it },
                    placeholder = { Text(inputPlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(inputFocusRequester)
                        .testTag("comment_input_field")
                )

                IconButton(
                    onClick = {
                        val text = commentText.trim()
                        if (text.isNotBlank()) {
                            val activeReply = replyingToComment
                            isSending = true
                            onAddComment(post.id, text, activeReply) {
                                isSending = false
                                commentText = ""
                                replyingToComment = null
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
