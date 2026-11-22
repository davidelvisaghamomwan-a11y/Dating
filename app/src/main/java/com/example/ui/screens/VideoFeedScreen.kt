package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.VideoPost
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.theme.CoralRose
import com.example.util.CountryFlagHelper
import com.example.util.PreparedVideoUpload
import com.example.util.VerificationAnalyzer
import com.example.util.VideoClipHelper
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFeedScreen(
    videoPosts: List<VideoPost>,
    currentUserId: String,
    resolveVideoFile: suspend (VideoPost) -> Result<File>,
    onPublishVideoPost: (caption: String, prepared: PreparedVideoUpload) -> Unit,
    onToggleLike: (postId: String, currentlyLiked: Boolean) -> Unit,
    onDeletePost: (postId: String) -> Unit,
    onOpenAuthorProfile: (String) -> Unit,
    onBack: () -> Unit,
    onStatusMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingUpload by remember { mutableStateOf<PreparedVideoUpload?>(null) }
    var captionDraft by remember { mutableStateOf("") }
    var isPreparing by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Community Video Feed") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("video_feed_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    videoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                containerColor = CoralRose,
                contentColor = Color.White,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("video_feed_upload_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Post Video")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .testTag("video_feed_screen")
        ) {
            if (isPreparing) {
                CircularProgressIndicator(
                    color = CoralRose,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (videoPosts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("No Community Videos Yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Tap + to upload high-resolution clips of any length to the Kindred feed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 620.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(videoPosts, key = { it.id }) { post ->
                        VideoPostFeedCard(
                            post = post,
                            currentUserId = currentUserId,
                            resolveVideoFile = resolveVideoFile,
                            onToggleLike = {
                                onToggleLike(post.id, currentUserId in post.likedByUids)
                            },
                            onDelete = { onDeletePost(post.id) },
                            onOpenAuthor = { onOpenAuthorProfile(post.userId) }
                        )
                    }
                }
            }
        }
    }

    pendingUpload?.let { upload ->
        AlertDialog(
            onDismissRequest = {
                pendingUpload = null
                captionDraft = ""
            },
            title = { Text("Publish Video Post") },
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
                        label = { Text("Caption") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPublishVideoPost(captionDraft.trim(), upload)
                        pendingUpload = null
                        captionDraft = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
                ) {
                    Text("Post Video")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingUpload = null
                        captionDraft = ""
                    }
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun VideoPostFeedCard(
    post: VideoPost,
    currentUserId: String,
    resolveVideoFile: suspend (VideoPost) -> Result<File>,
    onToggleLike: () -> Unit,
    onDelete: () -> Unit,
    onOpenAuthor: () -> Unit
) {
    var playRequested by remember(post.id) { mutableStateOf(false) }
    var resolvedFile by remember(post.id) { mutableStateOf<File?>(null) }
    var isLoading by remember(post.id) { mutableStateOf(false) }

    LaunchedEffect(post.id, playRequested) {
        if (playRequested && resolvedFile == null && !isLoading) {
            isLoading = true
            resolvedFile = resolveVideoFile(post).getOrNull()
            isLoading = false
        }
    }

    val isLiked = currentUserId in post.likedByUids
    val flag = CountryFlagHelper.flagEmojiForCountry(post.authorCountry.orEmpty())

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        .clickable(onClick = onOpenAuthor)
                ) {
                    ProfileAvatarImage(
                        photoDataUrl = post.authorPhoto,
                        displayName = post.authorName.ifBlank { "Member" },
                        seed = post.userId,
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        initialsFontSizeSp = 15
                    )
                    Text(
                        text = buildString {
                            append(post.authorName.ifBlank { "Member" })
                            if (flag.isNotBlank()) append(" $flag")
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (post.userId == currentUserId) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete video post")
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141118))
                    .clickable { playRequested = true }
            ) {
                val file = resolvedFile
                if (playRequested && file != null) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                val ctrl = MediaController(ctx).apply { setAnchorView(this@apply) }
                                setMediaController(ctrl)
                                setVideoURI(Uri.fromFile(file))
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        },
                        onRelease = { runCatching { it.stopPlayback() } },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val thumb = remember(post.thumbnailDataUrl) {
                        post.thumbnailDataUrl?.let { VerificationAnalyzer.decodeBase64DataUrl(it) }
                    }
                    if (thumb != null) {
                        Image(
                            bitmap = thumb,
                            contentDescription = "Video thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (isLoading) {
                        CircularProgressIndicator(color = CoralRose, modifier = Modifier.align(Alignment.Center))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play video",
                            tint = Color.White,
                            modifier = Modifier
                                .size(54.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
            }

            if (post.caption.isNotBlank()) {
                Text(text = post.caption, style = MaterialTheme.typography.bodyMedium)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleLike) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like video",
                        tint = if (isLiked) CoralRose else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${post.likedByUids.size} likes",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
