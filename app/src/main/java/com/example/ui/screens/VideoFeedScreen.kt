package com.example.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ChatBubble
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.ui.components.PostCommentsSheet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.PublicProfile
import com.example.model.VideoPost
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.theme.CoralRose
import com.example.util.CountryFlagHelper
import com.example.util.PreparedVideoUpload
import com.example.util.MediaImageHelper
import com.example.util.VideoClipHelper
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFeedScreen(
    videoPosts: List<VideoPost>,
    currentUserId: String,
    profilesByUid: Map<String, PublicProfile> = emptyMap(),
    resolveVideoFile: suspend (VideoPost) -> Result<File>,
    onPublishVideoPost: (caption: String, prepared: PreparedVideoUpload) -> Unit,
    onPublishVideoPostWithResult: ((caption: String, prepared: PreparedVideoUpload, onComplete: (Boolean, String?) -> Unit) -> Unit)? = null,
    onToggleLike: (postId: String, currentlyLiked: Boolean) -> Unit,
    onDeletePost: (postId: String) -> Unit,
    onOpenAuthorProfile: (String) -> Unit,
    onBack: () -> Unit,
    onStatusMessage: (String) -> Unit = {},
    isFollowingUid: (String) -> Boolean = { false },
    onToggleFollow: (String) -> Unit = {},
    myProfile: PublicProfile? = null,
    observeComments: (String) -> Flow<List<FeedComment>> = { flowOf(emptyList()) },
    onAddComment: (String, String) -> Unit = { _, _ -> },
    onDeleteComment: (String, String) -> Unit = { _, _ -> },
    initialPostId: String? = null
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingUpload by remember { mutableStateOf<PreparedVideoUpload?>(null) }
    var captionDraft by remember { mutableStateOf("") }
    var isPreparing by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var activeCommentsVideoPost by remember { mutableStateOf<VideoPost?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isPreparing = true
                val res = VideoClipHelper.prepareVideoForUpload(context, uri)
                isPreparing = false
                res.onSuccess { clip ->
                    pendingUpload = clip
                }.onFailure { err ->
                    onStatusMessage(err.localizedMessage ?: "Unable to prepare video")
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0A0E))
            .testTag("video_feed_screen")
    ) {
        when {
            isPreparing -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = CoralRose)
                    Text(
                        text = "Preparing Reel...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            videoPosts.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = CoralRose,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "No Reels Yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Be the first to share a short video clip on Kindred Reels.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Reel")
                    }
                }
            }

            else -> {
                val targetIndex = remember(initialPostId, videoPosts) {
                    if (initialPostId.isNullOrBlank()) 0
                    else videoPosts.indexOfFirst { it.id == initialPostId }.coerceAtLeast(0)
                }
                val pagerState = rememberPagerState(
                    initialPage = targetIndex,
                    pageCount = { videoPosts.size }
                )
                LaunchedEffect(initialPostId, videoPosts.size) {
                    if (!initialPostId.isNullOrBlank()) {
                        val idx = videoPosts.indexOfFirst { it.id == initialPostId }
                        if (idx >= 0 && idx != pagerState.currentPage) {
                            pagerState.scrollToPage(idx)
                        }
                    }
                }
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("reels_vertical_pager")
                ) { page ->
                    val post = videoPosts[page]
                    val isSettledActive = pagerState.settledPage == page
                    ReelPageItem(
                        post = post,
                        authorProfile = profilesByUid[post.userId],
                        isActive = isSettledActive,
                        isMuted = isMuted,
                        currentUserId = currentUserId,
                        isFollowing = isFollowingUid(post.userId),
                        resolveVideoFile = resolveVideoFile,
                        onToggleMute = { isMuted = !isMuted },
                        onToggleLike = { onToggleLike(post.id, currentUserId in post.likedByUids) },
                        onDelete = { onDeletePost(post.id) },
                        onOpenAuthor = { onOpenAuthorProfile(post.userId) },
                        onToggleFollow = { onToggleFollow(post.userId) },
                        onOpenComments = { activeCommentsVideoPost = post }
                    )
                }
            }
        }

        // Floating Overlay Top Bar (Reels title, Back / Upload)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .testTag("video_feed_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Reels",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = CoralRose,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                    .testTag("video_feed_upload_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Post Reel", tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("New Reel", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    pendingUpload?.let { upload ->
        AlertDialog(
            onDismissRequest = {
                if (!isPublishing) {
                    pendingUpload = null
                    captionDraft = ""
                    uploadError = null
                }
            },
            title = { Text("Publish Reel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Duration: ${VideoClipHelper.formatDurationLabel(upload.durationSec)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = captionDraft,
                        onValueChange = { if (it.length <= 240) captionDraft = it },
                        label = { Text("Caption & #hashtags") },
                        maxLines = 3,
                        enabled = !isPublishing,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (uploadError != null) {
                        Text(
                            text = uploadError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isPublishing) return@Button
                        isPublishing = true
                        uploadError = null
                        val cap = captionDraft.trim()
                        scope.launch {
                            try {
                                kotlinx.coroutines.withTimeout(45_000L) {
                                    val pubRes = onPublishVideoPostWithResult
                                    if (pubRes != null) {
                                        var done = false
                                        pubRes(cap, upload) { success, errMsg ->
                                            done = true
                                            isPublishing = false
                                            if (success) {
                                                pendingUpload = null
                                                captionDraft = ""
                                                uploadError = null
                                            } else {
                                                uploadError = errMsg ?: "Publish failed"
                                            }
                                        }
                                        while (!done) { kotlinx.coroutines.delay(100L) }
                                    } else {
                                        onPublishVideoPost(cap, upload)
                                        isPublishing = false
                                        pendingUpload = null
                                        captionDraft = ""
                                    }
                                }
                            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                android.util.Log.e("VideoFeed", "Reel publish timed out", e)
                                uploadError = "Upload timed out. Check network connection."
                                isPublishing = false
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                isPublishing = false
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.e("VideoFeed", "Reel publish error", e)
                                uploadError = e.localizedMessage ?: "Publish failed"
                                isPublishing = false
                            } finally {
                                isPublishing = false
                            }
                        }
                    },
                    enabled = !isPublishing,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Post Reel")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isPublishing) {
                            pendingUpload = null
                            captionDraft = ""
                            uploadError = null
                        }
                    },
                    enabled = !isPublishing
                ) { Text("Cancel") }
            }
        )
    }

    activeCommentsVideoPost?.let { vidPost ->
        PostCommentsSheet(
            post = FeedPost(
                id = vidPost.id,
                userId = vidPost.userId,
                authorName = vidPost.authorName,
                authorPhoto = vidPost.authorPhoto,
                authorCountry = vidPost.authorCountry,
                caption = vidPost.caption,
                mediaType = "VIDEO",
                mediaDataUrl = vidPost.mediaDataUrl,
                thumbnailDataUrl = vidPost.thumbnailDataUrl,
                likedByUids = vidPost.likedByUids,
                createdAt = vidPost.createdAt
            ),
            currentUserId = currentUserId,
            myProfile = myProfile,
            profilesByUid = profilesByUid,
            observeComments = observeComments,
            onAddComment = { postId, text, onDone ->
                onAddComment(postId, text)
                onDone()
            },
            onDeleteComment = onDeleteComment,
            onOpenProfile = { uid ->
                activeCommentsVideoPost = null
                onOpenAuthorProfile(uid)
            },
            onDismiss = { activeCommentsVideoPost = null }
        )
    }
}

@Composable
private fun ReelPageItem(
    post: VideoPost,
    authorProfile: PublicProfile? = null,
    isActive: Boolean,
    isMuted: Boolean,
    currentUserId: String,
    isFollowing: Boolean,
    resolveVideoFile: suspend (VideoPost) -> Result<File>,
    onToggleMute: () -> Unit,
    onToggleLike: () -> Unit,
    onDelete: () -> Unit,
    onOpenAuthor: () -> Unit,
    onToggleFollow: () -> Unit,
    onOpenComments: () -> Unit
) {
    val context = LocalContext.current
    var resolvedFile by remember(post.id) { mutableStateOf<File?>(null) }
    var isLoading by remember(post.id) { mutableStateOf(false) }
    var userPaused by remember(post.id) { mutableStateOf(false) }
    var mediaPlayerRef by remember(post.id) { mutableStateOf<MediaPlayer?>(null) }

    val shouldPlay = isActive && !userPaused

    LaunchedEffect(post.id, isActive) {
        if (!isActive) {
            userPaused = false
        } else if (resolvedFile == null && !isLoading) {
            isLoading = true
            resolvedFile = resolveVideoFile(post).getOrNull()
            isLoading = false
        }
    }

    LaunchedEffect(isMuted, mediaPlayerRef) {
        mediaPlayerRef?.let { mp ->
            runCatching {
                val vol = if (isMuted) 0f else 1f
                mp.setVolume(vol, vol)
            }
        }
    }

    val isLiked = currentUserId in post.likedByUids
    val isOwner = post.userId == currentUserId
    val displayAuthorName = authorProfile?.displayName?.takeIf { it.isNotBlank() }
        ?: post.authorName.ifBlank { "Member" }
    val displayAuthorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull() ?: post.authorPhoto
    val flag = CountryFlagHelper.flagEmojiForCountry(
        authorProfile?.country?.takeIf { it.isNotBlank() } ?: post.authorCountry.orEmpty()
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0A0E))
            .pointerInput(post.id) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!isLiked) onToggleLike()
                    },
                    onTap = {
                        userPaused = !userPaused
                    }
                )
            }
    ) {
        val file = resolvedFile
        if (file != null) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setVideoURI(Uri.fromFile(file))
                        setOnPreparedListener { mp ->
                            mediaPlayerRef = mp
                            mp.isLooping = true
                            val vol = if (isMuted) 0f else 1f
                            mp.setVolume(vol, vol)
                            if (shouldPlay) start()
                        }
                    }
                },
                update = { vv ->
                    if (shouldPlay && !vv.isPlaying) {
                        runCatching { vv.start() }
                    } else if (!shouldPlay && vv.isPlaying) {
                        runCatching { vv.pause() }
                    }
                },
                onRelease = {
                    mediaPlayerRef = null
                    runCatching { it.stopPlayback() }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val thumb = remember(post.thumbnailDataUrl) {
                post.thumbnailDataUrl?.let { MediaImageHelper.decodeBase64DataUrl(it) }
            }
            if (thumb != null) {
                Image(
                    bitmap = thumb,
                    contentDescription = "Reel thumbnail",
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isLoading) {
                CircularProgressIndicator(color = CoralRose, modifier = Modifier.align(Alignment.Center))
            }
        }

        // Play/Pause indicator overlay
        AnimatedVisibility(
            visible = userPaused && !isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Paused",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        // Bottom Gradient Scrim for text readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                    )
                )
        )

        // Bottom Left: Author profile, follow button, caption
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 20.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable(onClick = onOpenAuthor)
                ) {
                    ProfileAvatarImage(
                        photoDataUrl = displayAuthorPhoto,
                        displayName = displayAuthorName,
                        seed = post.userId,
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        initialsFontSizeSp = 15
                    )
                    Text(
                        text = buildString {
                            append(displayAuthorName)
                            if (flag.isNotBlank()) append(" $flag")
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isOwner) {
                    val label = if (isFollowing) "Following" else "Follow"
                    OutlinedButton(
                        onClick = onToggleFollow,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("reel_follow_btn_${post.id}")
                    ) {
                        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (post.caption.isNotBlank()) {
                Text(
                    text = post.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.94f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Bottom Right Action Column: Like, Share, Mute/Unmute, Pause/Play, Delete
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 12.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like reel",
                        tint = if (isLiked) CoralRose else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = "${post.likedByUids.size}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onOpenComments,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubble,
                        contentDescription = "Comments",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "Comments",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Watch ${post.authorName}'s Reel on Kindred: ${post.caption}")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Reel"))
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Share reel",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = { userPaused = !userPaused },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            ) {
                Icon(
                    imageVector = if (userPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (userPaused) "Play" else "Pause",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (isOwner) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete reel",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
