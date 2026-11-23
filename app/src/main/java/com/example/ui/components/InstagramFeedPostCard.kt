package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.model.FeedPost
import com.example.model.FriendStatus
import com.example.model.PublicProfile
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.util.CountryFlagHelper
import com.example.util.VerificationAnalyzer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private val InstagramBlue = Color(0xFF0095F6)

@Composable
fun InstagramFeedPostCard(
    post: FeedPost,
    authorProfile: PublicProfile? = null,
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

    val displayAuthorName = authorProfile?.displayName?.takeIf { it.isNotBlank() }
        ?: post.authorName.ifBlank { "Kindred Member" }
    val displayAuthorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull() ?: post.authorPhoto
    val isAuthorVerified = authorProfile?.isProfileVerified ?: post.authorVerified
    val displayCountry = authorProfile?.country?.takeIf { it.isNotBlank() } ?: post.authorCountry.orEmpty()
    val displayCity = post.authorCity?.takeIf { it.isNotBlank() } ?: authorProfile?.cityLabel.orEmpty()
    val flag = CountryFlagHelper.flagEmojiForCountry(displayCountry)

    val timeLabel = remember(post.createdAt) {
        post.createdAt?.toDate()?.let { date ->
            val diffMs = (System.currentTimeMillis() - date.time).coerceAtLeast(0L)
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

    // Video State: Prefetch video file immediately so it starts playing automatically on scroll into view
    var userPaused by remember(post.id) { mutableStateOf(false) }
    var isMuted by remember(post.id) { mutableStateOf(false) }
    var resolvedVideoFile by remember(post.id) { mutableStateOf<File?>(null) }
    var loadingVideo by remember(post.id) { mutableStateOf(false) }
    var activeMediaPlayer by remember(post.id) { mutableStateOf<android.media.MediaPlayer?>(null) }

    LaunchedEffect(isVisibleForAutoPlay) {
        if (!isVisibleForAutoPlay) {
            userPaused = false
        }
    }

    val playVideo = isVisibleForAutoPlay && !userPaused

    LaunchedEffect(isMuted, activeMediaPlayer) {
        activeMediaPlayer?.let { mp ->
            runCatching {
                val vol = if (isMuted) 0f else 1f
                mp.setVolume(vol, vol)
            }
        }
    }

    LaunchedEffect(post.id, post.mediaType) {
        if (post.mediaType == "VIDEO" && resolvedVideoFile == null && !loadingVideo) {
            loadingVideo = true
            resolvedVideoFile = resolveVideoFile(post).getOrNull()
            loadingVideo = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .testTag("feed_post_card_${post.id}")
    ) {
        // 1. Professional Post Header: Avatar, Username + Verified Badge, Location/Audio Subtitle, Follow & Options
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    photoDataUrl = displayAuthorPhoto,
                    displayName = displayAuthorName,
                    seed = post.userId,
                    modifier = Modifier
                        .size(36.dp)
                        .border(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            CircleShape
                        ),
                    shape = CircleShape,
                    initialsFontSizeSp = 14
                )

                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = displayAuthorName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 14.sp,
                                lineHeight = 18.sp
                            ),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isAuthorVerified) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = "Verified creator",
                                tint = InstagramBlue,
                                modifier = Modifier
                                    .size(15.dp)
                                    .testTag("post_verified_badge_${post.id}")
                            )
                        }
                        if (flag.isNotBlank()) {
                            Text(text = flag, fontSize = 12.sp)
                        }
                    }

                    val subtitleLabel = buildString {
                        if (post.mediaType == "VIDEO") {
                            append(displayAuthorName)
                            append(" • Original audio")
                        } else if (displayCity.isNotBlank()) {
                            append(displayCity)
                        } else {
                            append(timeLabel)
                        }
                    }
                    Text(
                        text = subtitleLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (!isOwner && friendStatus != FriendStatus.FRIENDS) {
                    val friendBtnLabel = when (friendStatus) {
                        FriendStatus.NONE -> "Follow"
                        FriendStatus.OUTGOING_PENDING -> "Requested"
                        FriendStatus.INCOMING_PENDING -> "Accept"
                        FriendStatus.FRIENDS -> "Following"
                    }
                    TextButton(
                        onClick = onFriendAction,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("post_follow_btn_${post.id}")
                    ) {
                        Text(
                            text = friendBtnLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = InstagramBlue
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Post options",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
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
                                    putExtra(Intent.EXTRA_TEXT, "$displayAuthorName's post on Kindred: ${post.caption}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share post"))
                            }
                        )
                    }
                }
            }
        }

        // 2. Post Media Presentation (Full-bleed, high-resolution, aspect-ratio-preserving)
        val photoUrls = remember(post.id, post.mediaDataUrl, post.mediaUrls) { post.allPhotoUrls() }
        val decodedBitmaps = remember(post.id, photoUrls) {
            photoUrls.associateWith { url ->
                if (url.startsWith("data:image")) VerificationAnalyzer.decodeBase64DataUrl(url) else null
            }
        }
        val firstBitmap: ImageBitmap? = photoUrls.firstOrNull()?.let { decodedBitmaps[it] }
        val primaryAspectRatio = remember(post.id, firstBitmap, post.mediaType) {
            when {
                post.mediaType == "VIDEO" -> 0.8f
                firstBitmap != null && firstBitmap.height > 0 -> {
                    (firstBitmap.width.toFloat() / firstBitmap.height.toFloat()).coerceIn(0.75f, 1.91f)
                }
                else -> 1f
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (post.mediaType == "TEXT" && photoUrls.isEmpty()) 1.5f else primaryAspectRatio)
                .background(Color(0xFF0D0C10))
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
                                userPaused = !userPaused
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                post.mediaType == "VIDEO" -> {
                    val file = resolvedVideoFile
                    if (file != null) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    setOnErrorListener { _, _, _ -> true }
                                    setVideoURI(Uri.fromFile(file))
                                    setOnPreparedListener { mp ->
                                        activeMediaPlayer = mp
                                        mp.isLooping = true
                                        val vol = if (isMuted) 0f else 1f
                                        mp.setVolume(vol, vol)
                                        if (playVideo) start()
                                    }
                                }
                            },
                            update = { vv ->
                                if (playVideo && !vv.isPlaying) {
                                    runCatching { vv.start() }
                                } else if (!playVideo && vv.isPlaying) {
                                    runCatching { vv.pause() }
                                }
                            },
                            onRelease = {
                                activeMediaPlayer = null
                                runCatching { it.stopPlayback() }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (file == null || (!playVideo && !loadingVideo)) {
                        val thumb = remember(post.thumbnailDataUrl) {
                            post.thumbnailDataUrl?.let { VerificationAnalyzer.decodeBase64DataUrl(it) }
                        }
                        if (file == null && thumb != null) {
                            Image(
                                bitmap = thumb,
                                contentDescription = "Video preview",
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.High,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (loadingVideo && file == null) {
                            CircularProgressIndicator(color = CoralRose)
                        } else if (userPaused || file == null) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.45f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play video",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Mute/Unmute audio control
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute audio" else "Mute audio",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                photoUrls.size > 1 -> {
                    val pagerState = rememberPagerState(pageCount = { photoUrls.size })
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val url = photoUrls[page]
                        val bmp = decodedBitmaps[url]
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = "Photo ${page + 1}",
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.High,
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
                                    .background(if (active) InstagramBlue else Color.White.copy(alpha = 0.55f))
                            )
                        }
                    }
                }

                photoUrls.size == 1 -> {
                    val url = photoUrls.first()
                    val bmp = decodedBitmaps[url]
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Post photo",
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
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
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }

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

        // 3. Post Actions Row: Consistent 12.dp horizontal alignment
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clickable {
                            scope.launch {
                                likeScale.animateTo(1.35f, spring(Spring.DampingRatioMediumBouncy))
                                likeScale.animateTo(1f)
                            }
                            onToggleLike()
                        }
                        .padding(vertical = 2.dp)
                        .testTag("feed_like_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) CoralRose else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .size(25.dp)
                            .scale(likeScale.value)
                    )
                    if (post.likedByUids.isNotEmpty()) {
                        Text(
                            text = "${post.likedByUids.size}",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clickable(onClick = onOpenComments)
                        .padding(vertical = 2.dp)
                        .testTag("feed_comment_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = "Comment",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(23.dp)
                    )
                    if (post.commentCount > 0) {
                        Text(
                            text = "${post.commentCount}",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(23.dp)
                        .clickable {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Check out $displayAuthorName's post on Kindred: ${post.caption}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share post"))
                        }
                        .testTag("feed_share_button_${post.id}")
                )
            }

            Icon(
                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = "Save post",
                tint = if (isSaved) ChampagneGold else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(25.dp)
                    .clickable(onClick = onToggleSave)
                    .testTag("feed_save_button_${post.id}")
            )
        }

        // 4. Caption, Hashtags, Comments Link & Timestamp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (post.caption.isNotBlank()) {
                val captionAnnotated = remember(post.caption, displayAuthorName) {
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(displayAuthorName)
                            append(" ")
                        }
                        val tokens = post.caption.split(" ")
                        tokens.forEachIndexed { idx, token ->
                            if (token.startsWith("#") && token.length > 1) {
                                withStyle(SpanStyle(color = InstagramBlue, fontWeight = FontWeight.Medium)) {
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
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            val count = post.commentCount.coerceAtLeast(0L)
            if (count > 0) {
                Text(
                    text = "View all $count ${if (count == 1L) "comment" else "comments"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable(onClick = onOpenComments)
                        .padding(vertical = 1.dp)
                )
            } else {
                Text(
                    text = "Add a comment...",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier
                        .clickable(onClick = onOpenComments)
                        .padding(vertical = 1.dp)
                )
            }

            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            )
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    }
}
