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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import java.io.File

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

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("stories_tray_row"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "add_story_ring") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(74.dp)
                    .clickable { showCreateStoryStudio = true }
                    .testTag("add_story_button")
            ) {
                Box(modifier = Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                    ProfileAvatarImage(
                        photoDataUrl = myProfile?.photoDataUrl,
                        displayName = myProfile?.displayName ?: "You",
                        seed = myProfile?.avatarSeed ?: currentUserId,
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        initialsFontSizeSp = 20
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF0095F6),
                        border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.background),
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.BottomEnd)
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
                    text = "Your story",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        items(stories.size, key = { idx -> stories[idx].id.ifBlank { "story_$idx" } }) { index ->
            val story = stories[index]
            val seen = currentUserId in story.viewerUids
            val igStoryBrush = Brush.linearGradient(
                listOf(
                    Color(0xFFFEDA75),
                    Color(0xFFFA7E1E),
                    Color(0xFFD62976),
                    Color(0xFF962FBF),
                    Color(0xFF4F5BD5)
                )
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(74.dp)
                    .clickable { onOpenStory(index) }
                    .testTag("story_ring_${story.id}")
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .border(
                            width = if (seen) 1.5.dp else 2.5.dp,
                            brush = if (seen) {
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.outlineVariant,
                                        MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                            } else {
                                igStoryBrush
                            },
                            shape = CircleShape
                        )
                        .padding(3.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ProfileAvatarImage(
                        photoDataUrl = story.authorPhoto,
                        displayName = story.authorName.ifBlank { "Member" },
                        seed = story.userId,
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        initialsFontSizeSp = 20
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = if (story.userId == currentUserId) "You" else story.authorName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
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
    onDismiss: () -> Unit
) {
    if (stories.isEmpty()) {
        onDismiss()
        return
    }
    var activeIndex by remember(stories.size, initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, stories.lastIndex))
    }
    val story = stories[activeIndex]
    var resolvedVideo by remember(story.id) { mutableStateOf<File?>(null) }
    var isLoadingVideo by remember(story.id) { mutableStateOf(false) }

    // Smooth remaining-time progress bar animation for the active story
    val progressAnim = remember(story.id) { Animatable(0f) }

    LaunchedEffect(story.id) {
        if (story.id.isNotBlank() && currentUserId !in story.viewerUids) {
            onMarkViewed(story.id)
        }
        if (story.mediaType == "VIDEO") {
            isLoadingVideo = true
            resolvedVideo = resolveVideoFile(story).getOrNull()
            isLoadingVideo = false
        } else {
            progressAnim.snapTo(0f)
            progressAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 6000, easing = LinearEasing)
            )
            if (activeIndex < stories.lastIndex) {
                activeIndex += 1
            } else {
                onDismiss()
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
                .padding(14.dp)
                .testTag("story_viewer_dialog")
        ) {
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
                                    start()
                                }
                                setOnCompletionListener {
                                    if (activeIndex < stories.lastIndex) activeIndex += 1 else onDismiss()
                                }
                            }
                        },
                        onRelease = { runCatching { it.stopPlayback() } },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp))
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
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp))
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Progress Bar Indicators matching Instagram story view
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stories.indices.forEach { idx ->
                        val pVal = when {
                            idx < activeIndex -> 1f
                            idx == activeIndex -> if (story.mediaType == "VIDEO") 1f else progressAnim.value
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { pVal },
                            color = CoralRose,
                            trackColor = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CircleShape)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
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

                    Row {
                        if (story.userId == currentUserId) {
                            IconButton(
                                onClick = {
                                    onDeleteStory(story.id)
                                    onDismiss()
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete story", tint = Color.White)
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Filled.Close, contentDescription = "Close story", tint = Color.White)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!story.caption.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = story.caption,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
                if (stories.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FilledTonalButton(
                            onClick = { if (activeIndex > 0) activeIndex -= 1 },
                            enabled = activeIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Prev")
                        }
                        FilledTonalButton(
                            onClick = { if (activeIndex < stories.lastIndex) activeIndex += 1 else onDismiss() }
                        ) {
                            Text(if (activeIndex < stories.lastIndex) "Next" else "Done")
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
