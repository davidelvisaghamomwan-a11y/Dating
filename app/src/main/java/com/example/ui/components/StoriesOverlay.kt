package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.PublicProfile
import com.example.model.StoryItem
import com.example.ui.theme.CoralRose
import com.example.util.PreparedVideoUpload
import com.example.util.VerificationAnalyzer
import kotlinx.coroutines.delay
import java.io.File

private val InstagramStoryGradient = Brush.sweepGradient(
    listOf(
        Color(0xFFFBAA47),
        Color(0xFFD91A46),
        Color(0xFFA60F93),
        Color(0xFF833AB4),
        Color(0xFF4F5BD5),
        Color(0xFFFBAA47)
    )
)

private val ViewedStoryGrayBrush = Brush.linearGradient(
    listOf(
        Color(0xFF8E8E93),
        Color(0xFF8E8E93)
    )
)

private data class StoryAuthorTrayItem(
    val authorId: String,
    val authorName: String,
    val authorPhoto: String?,
    val targetStoryIndex: Int,
    val allViewed: Boolean,
    val representativeStoryId: String
)

@Composable
fun StoriesTrayRow(
    myProfile: PublicProfile?,
    stories: List<StoryItem>,
    currentUserId: String,
    onOpenStory: (Int) -> Unit,
    onPostPhotoStory: (photoDataUrl: String, caption: String, onComplete: (Boolean) -> Unit) -> Unit,
    onPostVideoStory: (prepared: PreparedVideoUpload, caption: String, onComplete: (Boolean) -> Unit) -> Unit,
    onOpenVideoFeed: () -> Unit = {},
    onStatusMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showCreateStoryStudio by remember { mutableStateOf(false) }

    val myStories = remember(stories, currentUserId) { stories.filter { it.userId == currentUserId } }
    val myFirstStoryIndex = remember(stories, currentUserId) { stories.indexOfFirst { it.userId == currentUserId } }
    val hasMyStory = myFirstStoryIndex >= 0
    val isMyStoryAllViewed = hasMyStory && myStories.all { currentUserId in it.viewerUids }

    val otherStoryAuthors = remember(stories, currentUserId) {
        val authorMap = linkedMapOf<String, MutableList<Pair<Int, StoryItem>>>()
        stories.forEachIndexed { index, story ->
            if (story.userId != currentUserId) {
                authorMap.getOrPut(story.userId) { mutableListOf() }.add(index to story)
            }
        }
        authorMap.map { (uid, storyPairs) ->
            val unviewedPair = storyPairs.firstOrNull { currentUserId !in it.second.viewerUids }
            val targetPair = unviewedPair ?: storyPairs.first()
            val allViewed = storyPairs.all { currentUserId in it.second.viewerUids }
            val repStory = storyPairs.first().second
            StoryAuthorTrayItem(
                authorId = uid,
                authorName = repStory.authorName.ifBlank { "Member" },
                authorPhoto = repStory.authorPhoto,
                targetStoryIndex = targetPair.first,
                allViewed = allViewed,
                representativeStoryId = repStory.id
            )
        }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("stories_tray_row"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // First item: "Your Story" with + badge
        item(key = "add_story_ring") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(74.dp)
                    .clickable {
                        if (hasMyStory) {
                            onOpenStory(myFirstStoryIndex)
                        } else {
                            showCreateStoryStudio = true
                        }
                    }
                    .testTag("add_story_button")
            ) {
                Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .then(
                                if (hasMyStory) {
                                    Modifier
                                        .border(
                                            width = if (isMyStoryAllViewed) 1.5.dp else 2.5.dp,
                                            brush = if (isMyStoryAllViewed) ViewedStoryGrayBrush else InstagramStoryGradient,
                                            shape = CircleShape
                                        )
                                        .padding(3.5.dp)
                                } else {
                                    Modifier.padding(2.dp)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        ProfileAvatarImage(
                            photoDataUrl = myProfile?.photoDataUrl,
                            displayName = myProfile?.displayName ?: "You",
                            seed = myProfile?.avatarSeed ?: currentUserId,
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            initialsFontSizeSp = 20
                        )
                    }

                    // Circular + badge on bottom right
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF0095F6),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.background),
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.BottomEnd)
                            .clickable { showCreateStoryStudio = true }
                            .testTag("your_story_plus_badge")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add story",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "Your Story",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasMyStory && !isMyStoryAllViewed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Active stories for other users
        items(otherStoryAuthors, key = { it.authorId }) { authorGroup ->
            val seen = authorGroup.allViewed
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(74.dp)
                    .clickable { onOpenStory(authorGroup.targetStoryIndex) }
                    .testTag("story_ring_${authorGroup.representativeStoryId}")
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .border(
                            width = if (seen) 1.5.dp else 2.5.dp,
                            brush = if (seen) ViewedStoryGrayBrush else InstagramStoryGradient,
                            shape = CircleShape
                        )
                        .padding(3.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ProfileAvatarImage(
                        photoDataUrl = authorGroup.authorPhoto,
                        displayName = authorGroup.authorName,
                        seed = authorGroup.authorId,
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        initialsFontSizeSp = 20
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = authorGroup.authorName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (seen) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (seen) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showCreateStoryStudio) {
        CreateStoryStudioDialog(
            myProfile = myProfile,
            currentUserId = currentUserId,
            onPostPhotoStory = onPostPhotoStory,
            onPostVideoStory = onPostVideoStory,
            onStatusMessage = onStatusMessage,
            onDismiss = { showCreateStoryStudio = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryViewerDialog(
    stories: List<StoryItem>,
    initialIndex: Int,
    currentUserId: String,
    profilesByUid: Map<String, PublicProfile> = emptyMap(),
    resolveVideoFile: suspend (StoryItem) -> Result<File>,
    onMarkViewed: (String) -> Unit,
    onDeleteStory: (String) -> Unit,
    onOpenAuthorProfile: (String) -> Unit,
    onReplyToStory: ((authorUid: String, replyText: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    LaunchedEffect(stories.isEmpty()) {
        if (stories.isEmpty()) {
            onDismiss()
        }
    }
    if (stories.isEmpty()) {
        return
    }
    var activeIndex by remember(stories.size, initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, stories.lastIndex))
    }
    val story = stories[activeIndex]
    val isOwner = story.userId == currentUserId

    var resolvedVideo by remember(story.id) { mutableStateOf<File?>(null) }
    var isLoadingVideo by remember(story.id) { mutableStateOf(false) }
    var activeVideoView by remember { mutableStateOf<VideoView?>(null) }

    var isHeldPaused by remember { mutableStateOf(false) }
    var showViewerListSheet by remember { mutableStateOf(false) }
    var replyDraft by remember { mutableStateOf("") }
    var isSendingReply by remember { mutableStateOf(false) }

    val storyDurationMs = 5000L
    val timerStepMs = 50L
    var elapsedMs by remember(story.id) { mutableLongStateOf(0L) }

    LaunchedEffect(story.id) {
        elapsedMs = 0L
        if (story.id.isNotBlank() && currentUserId !in story.viewerUids) {
            onMarkViewed(story.id)
        }
        if (story.mediaType == "VIDEO") {
            isLoadingVideo = true
            resolvedVideo = resolveVideoFile(story).getOrNull()
            isLoadingVideo = false
        }
    }

    // Photo story timer loop that pauses when user holds down
    LaunchedEffect(story.id, isHeldPaused) {
        if (story.mediaType != "VIDEO" && !isHeldPaused) {
            while (elapsedMs < storyDurationMs) {
                delay(timerStepMs)
                if (!isHeldPaused) {
                    elapsedMs += timerStepMs
                }
            }
            if (activeIndex < stories.lastIndex) {
                activeIndex += 1
            } else {
                onDismiss()
            }
        }
    }

    // Pause/Resume video playback when user holds down
    LaunchedEffect(isHeldPaused) {
        activeVideoView?.let { vv ->
            if (isHeldPaused) {
                runCatching { vv.pause() }
            } else {
                runCatching { vv.start() }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("story_viewer_dialog")
        ) {
            // Media Layer
            if (story.mediaType == "VIDEO") {
                val vidFile = resolvedVideo
                if (vidFile != null) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setVideoURI(Uri.fromFile(vidFile))
                                setOnPreparedListener { mp ->
                                    mp.isLooping = false
                                    activeVideoView = this
                                    if (!isHeldPaused) start()
                                }
                                setOnCompletionListener {
                                    if (activeIndex < stories.lastIndex) activeIndex += 1 else onDismiss()
                                }
                            }
                        },
                        onRelease = {
                            activeVideoView = null
                            runCatching { it.stopPlayback() }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (isLoadingVideo) CircularProgressIndicator(color = CoralRose)
                        else Text("Video unavailable", color = Color.White)
                    }
                }
            } else {
                val bitmap = remember(story.mediaDataUrl) {
                    story.mediaDataUrl?.let { VerificationAnalyzer.decodeBase64DataUrl(it) }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Story photo by ${story.authorName}",
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Optional Text Overlay in center of story
            if (!story.caption.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = story.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            // Touch gesture detector: Tap left to go back, tap right to advance, hold to pause
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(story.id) {
                        detectTapGestures(
                            onPress = {
                                isHeldPaused = true
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    isHeldPaused = false
                                }
                            },
                            onTap = { offset ->
                                if (offset.x < size.width * 0.35f) {
                                    if (activeIndex > 0) activeIndex -= 1
                                } else {
                                    if (activeIndex < stories.lastIndex) activeIndex += 1 else onDismiss()
                                }
                            }
                        )
                    }
            )

            // Top Header: Segmented Progress Bars & Author Details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Segmented Progress Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stories.indices.forEach { idx ->
                        val pVal = when {
                            idx < activeIndex -> 1f
                            idx == activeIndex -> {
                                if (story.mediaType == "VIDEO") 1f
                                else (elapsedMs.toFloat() / storyDurationMs.toFloat()).coerceIn(0f, 1f)
                            }
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { pVal },
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier
                                .weight(1f)
                                .height(2.5.dp)
                                .clip(CircleShape)
                        )
                    }
                }

                // Author Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clickable {
                                onDismiss()
                                onOpenAuthorProfile(story.userId)
                            }
                    ) {
                        ProfileAvatarImage(
                            photoDataUrl = story.authorPhoto,
                            displayName = story.authorName,
                            seed = story.userId,
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            initialsFontSizeSp = 15
                        )
                        val isVerified = profilesByUid[story.userId]?.isProfileVerified ?: story.authorVerified
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = story.authorName.ifBlank { "Member" },
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isVerified) {
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = "Verified creator",
                                        tint = Color(0xFF0095F6),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${story.viewerUids.size} views",
                                color = Color.White.copy(alpha = 0.78f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isOwner) {
                            IconButton(
                                onClick = {
                                    onDeleteStory(story.id)
                                    onDismiss()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete story", tint = Color.White)
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close story", tint = Color.White)
                        }
                    }
                }
            }

            // Bottom Actions: Viewer List for Owner OR Send message reply bar for others
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = 12.dp)
            ) {
                if (isOwner) {
                    // Viewer list button for the story owner
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .clickable { showViewerListSheet = true }
                            .testTag("story_viewer_list_trigger")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = "Viewers", tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(
                                text = "${story.viewerUids.size} ${if (story.viewerUids.size == 1) "viewer" else "viewers"}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    // Send message reply bar directly to DM
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = replyDraft,
                            onValueChange = { replyDraft = it },
                            placeholder = { Text("Send message...", color = Color.White.copy(alpha = 0.7f)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("story_reply_input")
                        )
                        IconButton(
                            onClick = {
                                val text = replyDraft.trim()
                                if (text.isNotBlank()) {
                                    isSendingReply = true
                                    onReplyToStory?.invoke(story.userId, text)
                                    replyDraft = ""
                                    isSendingReply = false
                                }
                            },
                            enabled = replyDraft.isNotBlank() && !isSendingReply,
                            modifier = Modifier.testTag("story_reply_send_button")
                        ) {
                            if (isSendingReply) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Reply",
                                    tint = if (replyDraft.isNotBlank()) Color(0xFF0095F6) else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Story Viewers Modal Bottom Sheet
    if (showViewerListSheet) {
        ModalBottomSheet(
            onDismissRequest = { showViewerListSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("story_viewer_list_sheet"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Viewers (${story.viewerUids.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (story.viewerUids.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No viewers yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    story.viewerUids.forEach { viewerUid ->
                        val viewerProfile = profilesByUid[viewerUid]
                        val name = viewerProfile?.displayName ?: if (viewerUid == currentUserId) "You" else "Member"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showViewerListSheet = false
                                    onDismiss()
                                    onOpenAuthorProfile(viewerUid)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProfileAvatarImage(
                                photoDataUrl = viewerProfile?.photoDataUrl,
                                displayName = name,
                                seed = viewerUid,
                                modifier = Modifier.size(42.dp),
                                shape = CircleShape,
                                initialsFontSizeSp = 15
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    if (viewerProfile?.isProfileVerified == true) {
                                        Icon(
                                            imageVector = Icons.Filled.Verified,
                                            contentDescription = "Verified",
                                            tint = Color(0xFF0095F6),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Viewed story",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                            Icon(Icons.Filled.Visibility, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
