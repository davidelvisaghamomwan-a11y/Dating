package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.model.FeedPost
import com.example.model.FriendStatus
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.util.CountryFlagHelper
import com.example.util.VerificationAnalyzer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun InstagramFeedPostCard(
    post: FeedPost,
    currentUserId: String,
    friendStatus: FriendStatus,
    onFriendAction: () -> Unit,
    resolveVideoFile: suspend (FeedPost) -> Result<File>,
    onToggleLike: () -> Unit,
    onToggleSave: () -> Unit,
    onOpenComments: () -> Unit,
    onDeletePost: () -> Unit,
    onOpenAuthor: () -> Unit,
    onReportPost: () -> Unit = {},
    isVisibleForAutoPlay: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLiked = currentUserId in post.likedByUids
    val isSaved = currentUserId in post.savedByUids
    val isOwner = post.userId == currentUserId
    val flag = CountryFlagHelper.flagEmojiForCountry(post.authorCountry.orEmpty())

    val timeLabel = remember(post.createdAt) {
        post.createdAt?.toDate()?.let { date ->
            val diffMs = System.currentTimeMillis() - date.time
            val diffMinutes = diffMs / 60_000
            val diffHours = diffMinutes / 60
            val diffDays = diffHours / 24
            when {
                diffMinutes < 1 -> "Just now"
                diffMinutes < 60 -> "${diffMinutes}m ago"
                diffHours < 24 -> "${diffHours}h ago"
                diffDays < 7 -> "${diffDays}d ago"
                else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
            }
        } ?: "Just now"
    }

    var showMenu by remember { mutableStateOf(false) }
    var showHeartPop by remember { mutableStateOf(false) }
    val likeScale = remember { Animatable(1f) }

    // Video State
    var userPaused by remember(post.id) { mutableStateOf(false) }
    var manualPlay by remember(post.id) { mutableStateOf(false) }
    val playVideo = (isVisibleForAutoPlay && !userPaused) || manualPlay
    var isMuted by remember(post.id) { mutableStateOf(false) }
    var resolvedVideoFile by remember(post.id) { mutableStateOf<File?>(null) }
    var loadingVideo by remember(post.id) { mutableStateOf(false) }
    var activeMediaPlayer by remember(post.id) { mutableStateOf<android.media.MediaPlayer?>(null) }

    LaunchedEffect(isMuted, activeMediaPlayer) {
        activeMediaPlayer?.let { mp ->
            runCatching {
                val vol = if (isMuted) 0f else 1f
                mp.setVolume(vol, vol)
            }
        }
    }

    LaunchedEffect(post.id, playVideo) {
        if (post.mediaType == "VIDEO" && playVideo && resolvedVideoFile == null && !loadingVideo) {
            loadingVideo = true
            resolvedVideoFile = resolveVideoFile(post).getOrNull()
            loadingVideo = false
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier
            .fillMaxWidth()
            .testTag("feed_post_card_${post.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Post Header: Avatar, Name, Flag, Location, Follow/Friend, Three-Dot Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
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
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        initialsFontSizeSp = 16
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = post.authorName.ifBlank { "Kindred Member" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (flag.isNotBlank()) {
                                Text(text = flag, fontSize = 13.sp)
                            }
                        }

                        val locationLabel = buildString {
                            if (!post.authorCity.isNullOrBlank()) {
                                append(post.authorCity)
                                append(" • ")
                            }
                            append(timeLabel)
                        }
                        Text(
                            text = locationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!isOwner && friendStatus != FriendStatus.FRIENDS) {
                        val friendBtnLabel = when (friendStatus) {
                            FriendStatus.NONE -> "Follow"
                            FriendStatus.OUTGOING_PENDING -> "Sent"
                            FriendStatus.INCOMING_PENDING -> "Accept"
                            FriendStatus.FRIENDS -> "Friends"
                        }
                        FilledTonalButton(
                            onClick = onFriendAction,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("post_follow_btn_${post.id}")
                        ) {
                            Text(text = friendBtnLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (isOwner) {
                                DropdownMenuItem(
                                    text = { Text("Delete Post") },
                                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onDeletePost()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Report Post") },
                                    leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onReportPost()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Share Post") },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "${post.authorName}'s post on Kindred: ${post.caption}")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share post"))
                                }
                            )
                        }
                    }
                }
            }

            // 2. Post Media Presentation
            val photoUrls = post.allPhotoUrls()
            val primaryAspectRatio = remember(post.id, photoUrls) {
                val first = photoUrls.firstOrNull()
                if (first != null && first.startsWith("data:image")) {
                    val bmp = VerificationAnalyzer.decodeBase64DataUrl(first)
                    if (bmp != null && bmp.height > 0) {
                        (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.75f, 1.55f)
                    } else 1f
                } else 1f
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (post.mediaType == "TEXT" && photoUrls.isEmpty()) 1.6f else primaryAspectRatio)
                    .background(Color(0xFF141118))
                    .pointerInput(post.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (!isLiked) {
                                    onToggleLike()
                                }
                                scope.launch {
                                    showHeartPop = true
                                    likeScale.animateTo(1.3f, spring(Spring.DampingRatioMediumBouncy))
                                    likeScale.animateTo(1f)
                                    delay(400L)
                                    showHeartPop = false
                                }
                            },
                            onTap = {
                                if (post.mediaType == "VIDEO") {
                                    if (playVideo) {
                                        userPaused = true
                                        manualPlay = false
                                    } else {
                                        userPaused = false
                                        manualPlay = true
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    post.mediaType == "VIDEO" -> {
                        val file = resolvedVideoFile
                        if (playVideo && file != null) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        val videoView = this
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        val ctrl = MediaController(ctx).apply { setAnchorView(videoView) }
                                        setMediaController(ctrl)
                                        setVideoURI(Uri.fromFile(file))
                                        setOnPreparedListener { mp ->
                                            activeMediaPlayer = mp
                                            mp.isLooping = true
                                            val vol = if (isMuted) 0f else 1f
                                            mp.setVolume(vol, vol)
                                            start()
                                        }
                                    }
                                },
                                update = { vv ->
                                    if (!playVideo && vv.isPlaying) {
                                        vv.pause()
                                    } else if (playVideo && !vv.isPlaying) {
                                        runCatching { vv.start() }
                                    }
                                },
                                onRelease = {
                                    activeMediaPlayer = null
                                    runCatching { it.stopPlayback() }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val thumb = remember(post.thumbnailDataUrl) {
                                post.thumbnailDataUrl?.let { VerificationAnalyzer.decodeBase64DataUrl(it) }
                            }
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb,
                                    contentDescription = "Video preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (loadingVideo) {
                                CircularProgressIndicator(color = CoralRose)
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Play video",
                                    tint = Color.White,
                                    modifier = Modifier.size(54.dp)
                                )
                            }
                        }

                        // Mute/Unmute audio control
                        if (playVideo) {
                            IconButton(
                                onClick = { isMuted = !isMuted },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                    contentDescription = "Mute audio",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    photoUrls.size > 1 -> {
                        // Multi-Photo Carousel
                        val pagerState = rememberPagerState(pageCount = { photoUrls.size })
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val url = photoUrls[page]
                            val bmp = remember(url) {
                                if (url.startsWith("data:image")) {
                                    VerificationAnalyzer.decodeBase64DataUrl(url)
                                } else null
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "Photo ${page + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Photo ${page + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Position indicator badge: e.g. "1/3"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1}/${photoUrls.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Page indicator dots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                        ) {
                            repeat(photoUrls.size) { i ->
                                val active = pagerState.currentPage == i
                                Box(
                                    modifier = Modifier
                                        .size(if (active) 7.dp else 5.dp)
                                        .clip(CircleShape)
                                        .background(if (active) CoralRose else Color.White.copy(alpha = 0.5f))
                                )
                            }
                        }
                    }

                    photoUrls.size == 1 -> {
                        // Single photo
                        val url = photoUrls.first()
                        val bmp = remember(url) {
                            if (url.startsWith("data:image")) {
                                VerificationAnalyzer.decodeBase64DataUrl(url)
                            } else null
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = "Post photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AsyncImage(
                                model = url,
                                contentDescription = "Post photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    else -> {
                        // Text only or fallback
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF221C28)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = post.caption.take(120),
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                }

                // Heart Pop animation on double tap
                androidx.compose.animation.AnimatedVisibility(
                    visible = showHeartPop,
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier
                            .size(90.dp)
                            .scale(likeScale.value)
                    )
                }
            }

            // 3. Post Actions Row: Like, Comment, Share, Bookmark/Save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                likeScale.animateTo(1.35f, spring(Spring.DampingRatioMediumBouncy))
                                likeScale.animateTo(1f)
                            }
                            onToggleLike()
                        },
                        modifier = Modifier.testTag("feed_like_button_${post.id}")
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) CoralRose else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(26.dp)
                                .scale(likeScale.value)
                        )
                    }

                    IconButton(
                        onClick = onOpenComments,
                        modifier = Modifier.testTag("feed_comment_button_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChatBubbleOutline,
                            contentDescription = "Comment",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Check out ${post.authorName}'s post on Kindred: ${post.caption}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share post"))
                        },
                        modifier = Modifier.testTag("feed_share_button_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier.testTag("feed_save_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Save post",
                        tint = if (isSaved) ChampagneGold else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // 4. Likes count & Caption & Hashtags
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (post.likedByUids.isNotEmpty()) {
                    Text(
                        text = "${post.likedByUids.size} ${if (post.likedByUids.size == 1) "like" else "likes"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (post.caption.isNotBlank()) {
                    val captionAnnotated = remember(post.caption, post.authorName) {
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(post.authorName.ifBlank { "Member" })
                                append(" ")
                            }
                            val tokens = post.caption.split(" ")
                            tokens.forEachIndexed { idx, token ->
                                if (token.startsWith("#") && token.length > 1) {
                                    withStyle(SpanStyle(color = CoralRose, fontWeight = FontWeight.SemiBold)) {
                                        append(token)
                                    }
                                } else {
                                    append(token)
                                }
                                if (idx < tokens.lastIndex) append(" ")
                            }
                        }
                    }
                    Text(
                        text = captionAnnotated,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // View comments button
                val count = post.commentCount.coerceAtLeast(0L)
                if (count > 0) {
                    Text(
                        text = "View all $count ${if (count == 1L) "comment" else "comments"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable(onClick = onOpenComments)
                            .padding(vertical = 2.dp)
                    )
                } else {
                    Text(
                        text = "Add a comment...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clickable(onClick = onOpenComments)
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
