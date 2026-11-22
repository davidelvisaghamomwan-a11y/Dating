package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.PublicProfile
import com.example.model.StoryItem
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.util.PreparedVideoUpload
import com.example.util.VerificationAnalyzer
import com.example.util.VideoClipHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoriesTrayRow(
    myProfile: PublicProfile?,
    stories: List<StoryItem>,
    currentUserId: String,
    onOpenStory: (Int) -> Unit,
    onPostPhotoStory: (photoDataUrl: String, caption: String) -> Unit,
    onPostVideoStory: (prepared: PreparedVideoUpload, caption: String) -> Unit,
    onOpenVideoFeed: () -> Unit = {},
    onStatusMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCreateStorySheet by remember { mutableStateOf(false) }
    var pendingStoryCaption by remember { mutableStateOf("") }
    var pendingPhotoDataUrl by remember { mutableStateOf<String?>(null) }
    var pendingPreparedVideo by remember { mutableStateOf<PreparedVideoUpload?>(null) }
    var isPreparingMedia by remember { mutableStateOf(false) }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isPreparingMedia = true
                val dataUrl = VerificationAnalyzer.compressUriToDataUrl(context, uri)
                isPreparingMedia = false
                if (dataUrl != null) {
                    pendingPreparedVideo = null
                    pendingPhotoDataUrl = dataUrl
                    showCreateStorySheet = true
                } else {
                    onStatusMessage("Could not load photo for story")
                }
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isPreparingMedia = true
                val prepared = VideoClipHelper.prepareVideoForUpload(context, uri)
                isPreparingMedia = false
                prepared.onSuccess { clip ->
                    pendingPhotoDataUrl = null
                    pendingPreparedVideo = clip
                    showCreateStorySheet = true
                }.onFailure { err ->
                    onStatusMessage(err.localizedMessage ?: "Could not prepare story video")
                }
            }
        }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("stories_tray_row"),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "add_story_ring") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(70.dp)
                    .clickable { showCreateStorySheet = true }
                    .testTag("add_story_button")
            ) {
                Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                    ProfileAvatarImage(
                        photoDataUrl = myProfile?.photoDataUrl,
                        displayName = myProfile?.displayName ?: "You",
                        seed = myProfile?.avatarSeed ?: currentUserId,
                        modifier = Modifier
                            .size(56.dp)
                            .border(2.dp, CoralRose.copy(alpha = 0.65f), CircleShape),
                        shape = CircleShape,
                        initialsFontSizeSp = 18
                    )
                    Surface(
                        shape = CircleShape,
                        color = CoralRose,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.background),
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.BottomEnd)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add story",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your Story",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        item(key = "video_feed_chip") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(70.dp)
                    .clickable(onClick = onOpenVideoFeed)
                    .testTag("open_video_feed_story_chip")
            ) {
                Surface(
                    shape = CircleShape,
                    color = ChampagneGold.copy(alpha = 0.16f),
                    border = BorderStroke(2.dp, ChampagneGold),
                    modifier = Modifier.size(58.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircleOutline,
                            contentDescription = "Community video feed",
                            tint = ChampagneGold,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reels",
                    style = MaterialTheme.typography.labelSmall,
                    color = ChampagneGold,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        items(stories.size, key = { idx -> stories[idx].id.ifBlank { "story_$idx" } }) { index ->
            val story = stories[index]
            val seen = currentUserId in story.viewerUids
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(70.dp)
                    .clickable { onOpenStory(index) }
                    .testTag("story_ring_${story.id}")
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
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
                                Brush.linearGradient(listOf(CoralRose, ChampagneGold))
                            },
                            shape = CircleShape
                        )
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ProfileAvatarImage(
                        photoDataUrl = story.authorPhoto,
                        displayName = story.authorName.ifBlank { "Member" },
                        seed = story.userId,
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        initialsFontSizeSp = 18
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (story.userId == currentUserId) "You" else story.authorName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showCreateStorySheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showCreateStorySheet = false
                pendingPhotoDataUrl = null
                pendingPreparedVideo = null
                pendingStoryCaption = ""
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .testTag("create_story_sheet"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Share 24h Story", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "Share a moment with nearby Kindred members and friends for 24 hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isPreparingMedia) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CoralRose, strokeWidth = 2.dp)
                        Text("Preparing media…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (pendingPhotoDataUrl == null && pendingPreparedVideo == null) {
                    FilledTonalButton(
                        onClick = {
                            photoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pick_photo_story_button")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Photo Story")
                    }
                    OutlinedButton(
                        onClick = {
                            videoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pick_video_story_button")
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Video Story")
                    }
                } else {
                    OutlinedTextField(
                        value = pendingStoryCaption,
                        onValueChange = { if (it.length <= 160) pendingStoryCaption = it },
                        label = { Text("Caption (optional)") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("story_caption_input")
                    )
                    Button(
                        onClick = {
                            val photo = pendingPhotoDataUrl
                            val vid = pendingPreparedVideo
                            val cap = pendingStoryCaption.trim()
                            showCreateStorySheet = false
                            pendingPhotoDataUrl = null
                            pendingPreparedVideo = null
                            pendingStoryCaption = ""
                            if (photo != null) {
                                onPostPhotoStory(photo, cap)
                            } else if (vid != null) {
                                onPostVideoStory(vid, cap)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralRose, contentColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("publish_story_button")
                    ) {
                        Text("Publish Story", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StoryViewerDialog(
    stories: List<StoryItem>,
    initialIndex: Int,
    currentUserId: String,
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

    LaunchedEffect(story.id) {
        if (story.id.isNotBlank() && currentUserId !in story.viewerUids) {
            onMarkViewed(story.id)
        }
        if (story.mediaType == "VIDEO") {
            isLoadingVideo = true
            resolvedVideo = resolveVideoFile(story).getOrNull()
            isLoadingVideo = false
        } else {
            delay(6_000L)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stories.indices.forEach { idx ->
                        LinearProgressIndicator(
                            progress = { if (idx <= activeIndex) 1f else 0f },
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
                        Column {
                            Text(
                                text = story.authorName.ifBlank { "Member" },
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
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
