package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.example.model.DiscoveryCandidate
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.PublicProfile
import com.example.model.StoryItem
import com.example.model.VideoPost
import com.example.ui.components.CreatePostComposerSheet
import com.example.ui.components.InstagramFeedPostCard
import com.example.ui.components.ModeratorDeletePostDialog
import com.example.ui.components.PostCommentsSheet
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.components.StoriesTrayRow
import com.example.ui.components.StoryViewerDialog
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.CountryFlagHelper
import com.example.util.LocationAndGeoHelper
import com.example.util.PreparedVideoUpload
import com.example.util.ResolvedDeviceLocation
import com.example.util.MediaImageHelper
import com.example.util.VideoClipHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private enum class FeedFilterMode(val label: String) {
    FOLLOWING("Following"),
    FOLLOWER("Follower")
}

private sealed interface HomeTimelineItem {
    val key: String
    val userId: String
    val createdAtSeconds: Long
    val createdAtNanos: Int
    val isPlayableVideo: Boolean

    data class PostItem(val post: FeedPost) : HomeTimelineItem {
        override val key: String = "post_${post.id.ifBlank { post.postId }}"
        override val userId: String = post.userId.ifBlank { post.authorId }
        override val createdAtSeconds: Long = post.createdAt?.seconds ?: (System.currentTimeMillis() / 1000L)
        override val createdAtNanos: Int = post.createdAt?.nanoseconds ?: 0
        override val isPlayableVideo: Boolean = post.mediaType == "VIDEO"
    }

    data class ReelItem(val reel: VideoPost) : HomeTimelineItem {
        override val key: String = "reel_${reel.id}"
        override val userId: String = reel.userId
        override val createdAtSeconds: Long = reel.createdAt?.seconds ?: (System.currentTimeMillis() / 1000L)
        override val createdAtNanos: Int = reel.createdAt?.nanoseconds ?: 0
        override val isPlayableVideo: Boolean = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    myProfile: PublicProfile?,
    candidates: List<DiscoveryCandidate> = emptyList(),
    onReportUser: (candidate: DiscoveryCandidate, reason: String, details: String, alsoBlock: Boolean) -> Unit = { _, _, _, _ -> },
    onRefreshLocation: (ResolvedDeviceLocation) -> Unit = {},
    onLocationError: (String) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onLocationPermissionGranted: () -> Unit = {},
    activeOnlineCount: Int = 0,
    onOpenActiveUsers: () -> Unit = {},
    stories: List<StoryItem> = emptyList(),
    onPostPhotoStory: (photoDataUrl: String, caption: String, onComplete: (Boolean) -> Unit) -> Unit = { _, _, cb -> cb(true) },
    onPostVideoStory: (prepared: PreparedVideoUpload, caption: String, onComplete: (Boolean) -> Unit) -> Unit = { _, _, cb -> cb(true) },
    onPostPhotoStoryWithResult: ((photoDataUrl: String, caption: String, onComplete: (Boolean, String?) -> Unit) -> Unit)? = null,
    onPostVideoStoryWithResult: ((prepared: PreparedVideoUpload, caption: String, onComplete: (Boolean, String?) -> Unit) -> Unit)? = null,
    onMarkStoryViewed: (String) -> Unit = {},
    onDeleteStory: (String) -> Unit = {},
    resolveStoryVideoFile: suspend (StoryItem) -> Result<File> = { Result.failure(IllegalStateException("Unavailable")) },
    onReplyToStory: (authorUid: String, replyText: String) -> Unit = { _, _ -> },
    onOpenVideoFeed: () -> Unit = {},
    suggestedProfiles: List<DiscoveryCandidate> = emptyList(),
    feedPosts: List<FeedPost> = emptyList(),
    videoPosts: List<VideoPost> = emptyList(),
    currentUserId: String = myProfile?.userId ?: "",
    isFollowingUid: (String) -> Boolean = { false },
    isFollowerUid: (String) -> Boolean = { false },
    onToggleFollow: (PublicProfile) -> Unit = {},
    onOpenUserProfile: (PublicProfile) -> Unit = {},
    onOpenUserProfileById: (String) -> Unit = {},
    onPublishFeedPost: (caption: String, photoDataUrl: String?, photoUrls: List<String>, preparedVideo: PreparedVideoUpload?, onComplete: () -> Unit) -> Unit = { _, _, _, _, cb -> cb() },
    onPublishFeedPostWithResult: ((caption: String, photoDataUrl: String?, photoUrls: List<String>, preparedVideo: PreparedVideoUpload?, onComplete: (Boolean, String?) -> Unit) -> Unit)? = null,
    uploadProgress: Float? = null,
    onToggleFeedPostLike: (postId: String, currentlyLiked: Boolean) -> Unit = { _, _ -> },
    onToggleFeedPostSave: (postId: String, currentlySaved: Boolean) -> Unit = { _, _ -> },
    observeFeedComments: (String) -> Flow<List<FeedComment>> = { flowOf(emptyList()) },
    onAddFeedPostComment: (postId: String, text: String, replyTo: FeedComment?, onDone: () -> Unit) -> Unit = { _, _, _, cb -> cb() },
    onDeleteFeedPostComment: (postId: String, commentId: String) -> Unit = { _, _ -> },
    onToggleCommentLike: (postId: String, commentId: String, currentlyLiked: Boolean) -> Unit = { _, _, _ -> },
    onDeleteFeedPost: (postId: String) -> Unit = {},
    resolveFeedPostVideoFile: suspend (FeedPost) -> Result<File> = { Result.failure(IllegalStateException("Unavailable")) },
    onToggleVideoPostLike: (postId: String, currentlyLiked: Boolean) -> Unit = { _, _ -> },
    onDeleteVideoPost: (postId: String) -> Unit = {},
    resolveVideoPostFile: suspend (VideoPost) -> Result<File> = { Result.failure(IllegalStateException("Unavailable")) },
    observeVideoComments: (postId: String) -> Flow<List<FeedComment>> = { flowOf(emptyList()) },
    onAddVideoComment: (postId: String, text: String, replyTo: FeedComment?, onDone: () -> Unit) -> Unit = { _, _, _, cb -> cb() },
    onDeleteVideoComment: (postId: String, commentId: String) -> Unit = { _, _ -> },
    onOpenReelInVideoFeed: (reelId: String) -> Unit = { onOpenVideoFeed() },
    isFeedRefreshing: Boolean = false,
    onRefreshFeed: () -> Unit = {},
    onAutoRefreshFeed: () -> Unit = onRefreshFeed,
    unreadMessageCount: Int = 0,
    incomingNotificationCount: Int = 0,
    profilesByUid: Map<String, PublicProfile> = emptyMap(),
    isModerator: Boolean = false,
    onModeratorDeleteFeedPost: (post: FeedPost, reason: String, details: String) -> Unit = { _, _, _ -> },
    onModeratorDeleteVideoPost: (reel: VideoPost, reason: String, details: String) -> Unit = { _, _, _ -> },
    onModeratorDeleteStory: (story: StoryItem, reason: String, details: String) -> Unit = { _, _, _ -> },
    onOpenActivity: () -> Unit = {},
    onNavigateToInbox: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var filterMode by remember { mutableStateOf(FeedFilterMode.FOLLOWING) }
    var dismissedSuggestedPostIds by remember { mutableStateOf(emptySet<String>()) }
    var showFeedFilterMenu by remember { mutableStateOf(false) }
    var activeStoryIndex by remember { mutableStateOf<Int?>(null) }
    var showPostComposerSheet by remember { mutableStateOf(false) }
    var activeCommentsPost by remember { mutableStateOf<FeedPost?>(null) }
    var activeCommentsReel by remember { mutableStateOf<VideoPost?>(null) }

    LaunchedEffect(feedPosts, activeCommentsPost?.id) {
        val openPost = activeCommentsPost ?: return@LaunchedEffect
        val openId = openPost.id.ifBlank { openPost.postId }
        if (feedPosts.none { it.id.ifBlank { it.postId } == openId }) {
            activeCommentsPost = null
        }
    }

    LaunchedEffect(videoPosts, activeCommentsReel?.id) {
        val openReelId = activeCommentsReel?.id ?: return@LaunchedEffect
        if (videoPosts.none { it.id == openReelId }) {
            activeCommentsReel = null
        }
    }

    fun fetchAndApplyGps() {
        scope.launch {
            val loc = LocationAndGeoHelper.fetchCurrentLocation(context)
            if (loc != null) {
                onRefreshLocation(loc)
            } else {
                onLocationError("Unable to obtain GPS fix. Ensure location is turned on.")
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            onLocationPermissionGranted()
            fetchAndApplyGps()
        } else {
            onLocationError("Location permission denied. You can set your city manually in Profile.")
        }
    }

    val displayedTimelineItems = remember(feedPosts, videoPosts, filterMode, currentUserId, isFollowingUid, isFollowerUid, dismissedSuggestedPostIds) {
        val rawCombined = buildList<HomeTimelineItem> {
            feedPosts.forEach { post ->
                val resolvedId = post.id.ifBlank { post.postId }
                if (resolvedId.isNotBlank()) {
                    add(HomeTimelineItem.PostItem(post))
                }
            }
            videoPosts.forEach { reel ->
                if (reel.id.isNotBlank()) {
                    add(HomeTimelineItem.ReelItem(reel))
                }
            }
        }.distinctBy { it.key }
        val filtered = when (filterMode) {
            FeedFilterMode.FOLLOWING -> rawCombined.filter { item ->
                item.key !in dismissedSuggestedPostIds
            }
            FeedFilterMode.FOLLOWER -> rawCombined.filter { item ->
                (item.userId == currentUserId || isFollowerUid(item.userId)) && item.key !in dismissedSuggestedPostIds
            }
        }
        filtered.sortedWith(
            compareByDescending<HomeTimelineItem> { it.createdAtSeconds }
                .thenByDescending { it.createdAtNanos }
        )
    }

    // Prioritize other people's Reels for the "Suggested reels" horizontal shelf on Home
    val shelfReels = remember(videoPosts, currentUserId) {
        val validReels = videoPosts.filter { it.id.isNotBlank() }.distinctBy { it.id }
        val others = validReels.filter { it.userId != currentUserId }
        (if (others.isNotEmpty()) others else validReels).take(15)
    }

    val peopleToKnow = remember(suggestedProfiles, candidates) {
        (if (suggestedProfiles.isNotEmpty()) suggestedProfiles else candidates)
            .filter { it.profile.userId.isNotBlank() }
            .distinctBy { it.profile.userId }
            .take(20)
    }

    LaunchedEffect(displayedTimelineItems, stories, profilesByUid) {
        val urlsToWarm = buildList {
            displayedTimelineItems.take(12).forEach { item ->
                when (item) {
                    is HomeTimelineItem.PostItem -> {
                        val post = item.post
                        addAll(post.allPhotoUrls())
                        post.authorPhoto?.let { add(it) }
                        post.thumbnailDataUrl?.let { add(it) }
                        profilesByUid[post.userId]?.photoDataUrl?.let { add(it) }
                    }
                    is HomeTimelineItem.ReelItem -> {
                        val reel = item.reel
                        reel.authorPhoto?.let { add(it) }
                        reel.thumbnailDataUrl?.let { add(it) }
                        profilesByUid[reel.userId]?.photoDataUrl?.let { add(it) }
                    }
                }
            }
            stories.take(12).forEach { story ->
                story.authorPhoto?.let { add(it) }
                story.mediaDataUrl?.let { add(it) }
            }
        }
        MediaImageHelper.prefetchDataUrls(urlsToWarm)
    }

    val pullRefreshState = rememberPullToRefreshState()
    val feedListState = rememberLazyListState()

    var lastSeenTopKey by remember { mutableStateOf<String?>(null) }
    var hasNewPostsBanner by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRefreshFeed()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshFeed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(20_000L)
                onAutoRefreshFeed()
            }
        }
    }

    val currentTopKey = displayedTimelineItems.firstOrNull()?.key
    LaunchedEffect(currentTopKey, feedListState.firstVisibleItemIndex) {
        if (currentTopKey == null) return@LaunchedEffect
        if (lastSeenTopKey == null || feedListState.firstVisibleItemIndex <= 1) {
            lastSeenTopKey = currentTopKey
            hasNewPostsBanner = false
        } else if (currentTopKey != lastSeenTopKey) {
            hasNewPostsBanner = true
        }
    }

    val autoPlayVideoKey by remember(displayedTimelineItems) {
        derivedStateOf {
            val videoKeys = displayedTimelineItems.asSequence()
                .filter { it.isPlayableVideo }
                .map { it.key }
                .toSet()
            if (videoKeys.isEmpty()) return@derivedStateOf null
            val layoutInfo = feedListState.layoutInfo
            val viewStart = layoutInfo.viewportStartOffset
            val viewEnd = layoutInfo.viewportEndOffset
            layoutInfo.visibleItemsInfo
                .asSequence()
                .filter { (it.key as? String) in videoKeys }
                .maxByOrNull { info ->
                    val visibleTop = maxOf(info.offset, viewStart)
                    val visibleBottom = minOf(info.offset + info.size, viewEnd)
                    (visibleBottom - visibleTop).coerceAtLeast(0)
                }
                ?.takeIf { info ->
                    val visibleTop = maxOf(info.offset, viewStart)
                    val visibleBottom = minOf(info.offset + info.size, viewEnd)
                    (visibleBottom - visibleTop) > (info.size / 3)
                }
                ?.key as? String
                ?: layoutInfo.visibleItemsInfo.firstOrNull { (it.key as? String) in videoKeys }?.key as? String
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_feed_screen")
    ) {
        val isWide = maxWidth >= 700.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = if (isWide) 640.dp else 600.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Instagram-Style Top Navigation Bar ("For you v" left, Search, Heart & Send DM right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clickable { showFeedFilterMenu = true }
                            .padding(vertical = 4.dp)
                            .testTag("kindred_home_logo")
                    ) {
                        Text(
                            text = filterMode.label,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 23.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Switch feed",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showFeedFilterMenu,
                        onDismissRequest = { showFeedFilterMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Following") },
                            leadingIcon = { Icon(Icons.Filled.Group, contentDescription = null) },
                            trailingIcon = {
                                if (filterMode == FeedFilterMode.FOLLOWING) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF0095F6))
                                }
                            },
                            onClick = {
                                filterMode = FeedFilterMode.FOLLOWING
                                showFeedFilterMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Follower") },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            trailingIcon = {
                                if (filterMode == FeedFilterMode.FOLLOWER) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF0095F6))
                                }
                            },
                            onClick = {
                                filterMode = FeedFilterMode.FOLLOWER
                                showFeedFilterMenu = false
                            }
                        )
                    }
                }

                // Top Right: Activity Heart (with badge) & Direct Messages Send (with badge)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onOpenActivity,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("home_notifications_button")
                    ) {
                        if (incomingNotificationCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = CoralRose) {
                                        Text(if (incomingNotificationCount > 9) "9+" else "$incomingNotificationCount")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = "Activity",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(25.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Filled.FavoriteBorder,
                                contentDescription = "Activity",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(25.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onNavigateToInbox,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("home_dm_button")
                    ) {
                        if (unreadMessageCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = CoralRose) {
                                        Text(if (unreadMessageCount > 9) "9+" else "$unreadMessageCount")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Direct Messages",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Direct Messages",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // 2. Full-bleed Instagram Social Timeline Feed with Pull-To-Refresh
            PullToRefreshBox(
                isRefreshing = isFeedRefreshing,
                onRefresh = onRefreshFeed,
                state = pullRefreshState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullRefreshState,
                        isRefreshing = isFeedRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        color = Color(0xFF0095F6),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("social_feed_pull_refresh")
            ) {
                LazyColumn(
                    state = feedListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("social_timeline_feed"),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Stories Tray Section directly beneath header
                    item(key = "stories_section") {
                        StoriesTrayRow(
                            myProfile = myProfile,
                            stories = stories,
                            currentUserId = currentUserId,
                            onOpenStory = { idx -> activeStoryIndex = idx },
                            onPostPhotoStory = onPostPhotoStory,
                            onPostVideoStory = onPostVideoStory,
                            uploadProgress = uploadProgress,
                            onPostPhotoStoryWithResult = onPostPhotoStoryWithResult,
                            onPostVideoStoryWithResult = onPostVideoStoryWithResult,
                            onOpenVideoFeed = onOpenVideoFeed,
                            onStatusMessage = onLocationError
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    }

                    // Timeline Posts & Reels Stream
                    if (displayedTimelineItems.isEmpty()) {
                        item(key = "empty_feed_posts") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFF0095F6),
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = when (filterMode) {
                                        FeedFilterMode.FOLLOWING -> "No Posts from Following Yet"
                                        FeedFilterMode.FOLLOWER -> "No Posts from Followers Yet"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when (filterMode) {
                                        FeedFilterMode.FOLLOWING -> "Posts from accounts you follow will appear here."
                                        FeedFilterMode.FOLLOWER -> "Posts from your followers will appear here."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { showPostComposerSheet = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0095F6))
                                ) {
                                    Text("Share First Post")
                                }
                            }
                        }

                        if (peopleToKnow.isNotEmpty()) {
                            item(key = "people_you_may_know_section_empty") {
                                SuggestedPeopleStrip(
                                    peopleToKnow = peopleToKnow,
                                    activeOnlineCount = activeOnlineCount,
                                    isFollowingUid = isFollowingUid,
                                    onOpenActiveUsers = onOpenActiveUsers,
                                    onOpenUserProfile = onOpenUserProfile,
                                    onToggleFollow = onToggleFollow
                                )
                            }
                        }
                    } else {
                        itemsIndexed(displayedTimelineItems, key = { _, item -> item.key }) { index, item ->
                            when (item) {
                                is HomeTimelineItem.PostItem -> {
                                    val post = item.post
                                    val isSuggested = post.userId != currentUserId && !isFollowingUid(post.userId)
                                    InstagramFeedPostCard(
                                        post = post,
                                        authorProfile = profilesByUid[post.userId],
                                        currentUserId = currentUserId,
                                        isFollowing = isFollowingUid(post.userId),
                                        isSuggested = isSuggested,
                                        onDismissSuggestion = {
                                            dismissedSuggestedPostIds = dismissedSuggestedPostIds + item.key
                                        },
                                        onToggleFollow = {
                                            val profile = profilesByUid[post.userId] ?: PublicProfile(
                                                userId = post.userId,
                                                displayName = post.authorName
                                            )
                                            onToggleFollow(profile)
                                        },
                                        resolveVideoFile = resolveFeedPostVideoFile,
                                        onToggleLike = {
                                            onToggleFeedPostLike(post.id, currentUserId in post.likedByUids)
                                        },
                                        onToggleSave = {
                                            onToggleFeedPostSave(post.id, currentUserId in post.savedByUids)
                                        },
                                        onOpenComments = { activeCommentsPost = post },
                                        onDeletePost = { onDeleteFeedPost(post.id.ifBlank { post.postId }) },
                                        onOpenAuthor = { onOpenUserProfileById(post.userId) },
                                        onReportPost = {
                                            val candidate = candidates.firstOrNull { it.profile.userId == post.userId }
                                            if (candidate != null) {
                                                onReportUser(candidate, "Inappropriate Content", "Post ${post.id}", false)
                                            }
                                        },
                                        isModerator = isModerator,
                                        onModeratorDeletePost = { reason, details ->
                                            onModeratorDeleteFeedPost(post, reason, details)
                                        },
                                        isVisibleForAutoPlay = (item.key == autoPlayVideoKey)
                                    )
                                }

                                is HomeTimelineItem.ReelItem -> {
                                    val reel = item.reel
                                    val isSuggested = reel.userId != currentUserId && !isFollowingUid(reel.userId)
                                    HomeReelPostCard(
                                        reel = reel,
                                        authorProfile = profilesByUid[reel.userId],
                                        currentUserId = currentUserId,
                                        isFollowing = isFollowingUid(reel.userId),
                                        isSuggested = isSuggested,
                                        onDismissSuggestion = {
                                            dismissedSuggestedPostIds = dismissedSuggestedPostIds + item.key
                                        },
                                        onToggleFollow = {
                                            val profile = profilesByUid[reel.userId] ?: PublicProfile(
                                                userId = reel.userId,
                                                displayName = reel.authorName
                                            )
                                            onToggleFollow(profile)
                                        },
                                        resolveVideoFile = resolveVideoPostFile,
                                        onToggleLike = {
                                            onToggleVideoPostLike(reel.id, currentUserId in reel.likedByUids)
                                        },
                                        onOpenComments = { activeCommentsReel = reel },
                                        onDeleteReel = { onDeleteVideoPost(reel.id) },
                                        onOpenAuthor = { onOpenUserProfileById(reel.userId) },
                                        onOpenFullScreenReel = { onOpenReelInVideoFeed(reel.id) },
                                        onReportReel = {
                                            val candidate = candidates.firstOrNull { it.profile.userId == reel.userId }
                                            if (candidate != null) {
                                                onReportUser(candidate, "Inappropriate Content", "Reel ${reel.id}", false)
                                            }
                                        },
                                        isModerator = isModerator,
                                        onModeratorDeleteReel = { reason, details ->
                                            onModeratorDeleteVideoPost(reel, reason, details)
                                        },
                                        isVisibleForAutoPlay = (item.key == autoPlayVideoKey)
                                    )
                                }
                            }

                            if (index == 0) {
                                if (shelfReels.isNotEmpty()) {
                                    SuggestedReelsShelf(
                                        reels = shelfReels,
                                        profilesByUid = profilesByUid,
                                        onOpenReel = { reel -> onOpenReelInVideoFeed(reel.id) },
                                        onSeeAllReels = onOpenVideoFeed,
                                        onOpenAuthor = onOpenUserProfileById
                                    )
                                }
                                if (peopleToKnow.isNotEmpty()) {
                                    SuggestedPeopleStrip(
                                        peopleToKnow = peopleToKnow,
                                        activeOnlineCount = activeOnlineCount,
                                        isFollowingUid = isFollowingUid,
                                        onOpenActiveUsers = onOpenActiveUsers,
                                        onOpenUserProfile = onOpenUserProfile,
                                        onToggleFollow = onToggleFollow
                                    )
                                }
                            }
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = hasNewPostsBanner,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                ) {
                    Surface(
                        onClick = {
                            hasNewPostsBanner = false
                            lastSeenTopKey = currentTopKey
                            scope.launch {
                                feedListState.animateScrollToItem(0)
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF0095F6),
                        shadowElevation = 6.dp,
                        modifier = Modifier.testTag("feed_new_posts_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowUpward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "New posts",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    activeStoryIndex?.takeIf { stories.isNotEmpty() }?.let { storyIdx ->
        StoryViewerDialog(
            stories = stories,
            initialIndex = storyIdx.coerceIn(0, stories.lastIndex),
            currentUserId = currentUserId,
            profilesByUid = profilesByUid,
            resolveVideoFile = resolveStoryVideoFile,
            onMarkViewed = onMarkStoryViewed,
            onDeleteStory = onDeleteStory,
            onOpenAuthorProfile = onOpenUserProfileById,
            onReplyToStory = onReplyToStory,
            isModerator = isModerator,
            onModeratorDeleteStory = onModeratorDeleteStory,
            onDismiss = { activeStoryIndex = null }
        )
    }

    if (showPostComposerSheet) {
        CreatePostComposerSheet(
            myProfile = myProfile,
            uploadProgress = uploadProgress,
            onDismiss = { showPostComposerSheet = false },
            onPublish = { caption, photoDataUrl, photoUrls, video ->
                onPublishFeedPost(caption, photoDataUrl, photoUrls, video) {}
            },
            onPublishWithResult = onPublishFeedPostWithResult
        )
    }

    activeCommentsPost?.let { post ->
        PostCommentsSheet(
            post = post,
            currentUserId = currentUserId,
            myProfile = myProfile,
            profilesByUid = profilesByUid,
            observeComments = observeFeedComments,
            onAddComment = onAddFeedPostComment,
            onDeleteComment = onDeleteFeedPostComment,
            onToggleCommentLike = onToggleCommentLike,
            onOpenProfile = onOpenUserProfileById,
            onDismiss = { activeCommentsPost = null }
        )
    }

    activeCommentsReel?.let { vidPost ->
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
            observeComments = observeVideoComments,
            onAddComment = onAddVideoComment,
            onDeleteComment = onDeleteVideoComment,
            onOpenProfile = { uid ->
                activeCommentsReel = null
                onOpenUserProfileById(uid)
            },
            onDismiss = { activeCommentsReel = null }
        )
    }
}

@Composable
private fun SuggestedPeopleStrip(
    peopleToKnow: List<DiscoveryCandidate>,
    activeOnlineCount: Int,
    isFollowingUid: (String) -> Boolean,
    onOpenActiveUsers: () -> Unit,
    onOpenUserProfile: (PublicProfile) -> Unit,
    onToggleFollow: (PublicProfile) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Suggested for you",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (activeOnlineCount > 0) {
                Text(
                    text = "$activeOnlineCount online",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldMint,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onOpenActiveUsers)
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            modifier = Modifier.testTag("people_you_may_know_list")
        ) {
            items(peopleToKnow, key = { it.profile.userId }) { candidate ->
                val person = candidate.profile
                val flag = person.flagEmoji()
                val isFollowingPerson = isFollowingUid(person.userId)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .width(156.dp)
                        .clickable { onOpenUserProfile(person) }
                        .testTag("people_you_may_know_card_${person.userId}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProfileAvatarImage(
                            photoDataUrl = person.photoDataUrl,
                            displayName = person.displayName,
                            seed = person.avatarSeed,
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            initialsFontSizeSp = 20
                        )
                        Text(
                            text = buildString {
                                append("${person.displayName}, ${person.age}")
                                if (flag.isNotBlank()) append(" $flag")
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = person.bio.ifBlank { person.formattedLocation() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        val followBtnLabel = if (isFollowingPerson) "Following" else "Follow"
                        FilledTonalButton(
                            onClick = { onToggleFollow(person) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("feed_add_friend_${person.userId}")
                        ) {
                            Text(followBtnLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun SuggestedReelsShelf(
    reels: List<VideoPost>,
    profilesByUid: Map<String, PublicProfile>,
    onOpenReel: (VideoPost) -> Unit,
    onSeeAllReels: () -> Unit,
    onOpenAuthor: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("home_suggested_reels_shelf"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Reels",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "See all",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF0095F6),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onSeeAllReels)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("home_reels_see_all")
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.testTag("home_reels_carousel")
        ) {
            items(reels, key = { "shelf_reel_${it.id}" }) { reel ->
                val author = profilesByUid[reel.userId]
                val authorName = author?.displayName?.takeIf { it.isNotBlank() }
                    ?: reel.authorName.ifBlank { "Member" }
                val authorPhoto = author?.allProfilePhotos()?.firstOrNull() ?: reel.authorPhoto
                val thumbBitmap = remember(reel.thumbnailDataUrl) {
                    reel.thumbnailDataUrl?.takeIf { it.startsWith("data:image") }
                        ?.let { MediaImageHelper.decodeBase64DataUrl(it) }
                }

                Box(
                    modifier = Modifier
                        .width(136.dp)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141218))
                        .clickable { onOpenReel(reel) }
                        .testTag("home_shelf_reel_${reel.id}")
                ) {
                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap,
                            contentDescription = reel.caption.ifBlank { "Reel by $authorName" },
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (!reel.thumbnailDataUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = reel.thumbnailDataUrl,
                            contentDescription = reel.caption.ifBlank { "Reel by $authorName" },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ProfileAvatarImage(
                            photoDataUrl = authorPhoto,
                            displayName = authorName,
                            seed = reel.userId,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(0.dp),
                            initialsFontSizeSp = 28
                        )
                    }

                    // Bottom readability scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.18f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.78f)
                                    )
                                )
                            )
                    )

                    // Top-right Reels indicator
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = "Reel",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(18.dp)
                    )

                    if (reel.mediaDurationSec > 0L) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = VideoClipHelper.formatDurationLabel(reel.mediaDurationSec),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Bottom creator & caption preview
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.clickable { onOpenAuthor(reel.userId) }
                        ) {
                            ProfileAvatarImage(
                                photoDataUrl = authorPhoto,
                                displayName = authorName,
                                seed = reel.userId,
                                modifier = Modifier
                                    .size(22.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                                shape = CircleShape,
                                initialsFontSizeSp = 9
                            )
                            Text(
                                text = authorName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        if (reel.caption.isNotBlank()) {
                            Text(
                                text = reel.caption,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.88f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun HomeReelPostCard(
    reel: VideoPost,
    authorProfile: PublicProfile? = null,
    currentUserId: String,
    isFollowing: Boolean,
    isSuggested: Boolean = false,
    onDismissSuggestion: () -> Unit = {},
    onToggleFollow: () -> Unit,
    resolveVideoFile: suspend (VideoPost) -> Result<File>,
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    onDeleteReel: () -> Unit,
    onOpenAuthor: () -> Unit,
    onOpenFullScreenReel: () -> Unit,
    onReportReel: () -> Unit = {},
    isModerator: Boolean = false,
    onModeratorDeleteReel: ((reason: String, details: String) -> Unit)? = null,
    isVisibleForAutoPlay: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLiked = currentUserId in reel.likedByUids
    val isOwner = reel.userId == currentUserId

    val displayAuthorName = authorProfile?.displayName?.takeIf { it.isNotBlank() }
        ?: reel.authorName.ifBlank { "Kindred Member" }
    val displayAuthorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull() ?: reel.authorPhoto
    val displayCountry = authorProfile?.country?.takeIf { it.isNotBlank() } ?: reel.authorCountry.orEmpty()
    val flag = CountryFlagHelper.flagEmojiForCountry(displayCountry)

    val timeLabel = remember(reel.createdAt) {
        reel.createdAt?.toDate()?.let { date ->
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
    var showDeleteConfirm by remember(reel.id) { mutableStateOf(false) }
    var showModeratorDeleteDialog by remember(reel.id) { mutableStateOf(false) }
    var showHeartPop by remember { mutableStateOf(false) }
    val heartScale = remember { Animatable(0.2f) }
    val heartAlpha = remember { Animatable(1f) }
    val likeScale = remember { Animatable(1f) }

    val currentIsLiked by rememberUpdatedState(isLiked)
    val currentOnToggleLike by rememberUpdatedState(onToggleLike)

    fun handleReelTapLikeToggle() {
        val willLike = !currentIsLiked
        currentOnToggleLike()
        scope.launch {
            launch {
                likeScale.animateTo(1.35f, spring(Spring.DampingRatioMediumBouncy))
                likeScale.animateTo(1f)
            }
            if (willLike) {
                showHeartPop = true
                heartScale.snapTo(0.2f)
                heartAlpha.snapTo(1f)
                heartScale.animateTo(
                    targetValue = 1.3f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                heartScale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
                delay(500L)
                launch {
                    heartScale.animateTo(
                        targetValue = 1.2f,
                        animationSpec = tween(durationMillis = 250)
                    )
                }
                heartAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 250)
                )
                showHeartPop = false
            }
        }
    }
    var userPaused by remember(reel.id) { mutableStateOf(false) }
    var isMuted by remember(reel.id) { mutableStateOf(false) }
    var resolvedVideoFile by remember(reel.id) { mutableStateOf<File?>(null) }
    var loadingVideo by remember(reel.id) { mutableStateOf(false) }
    var isFirstFrameRendered by remember(reel.id) { mutableStateOf(false) }

    LaunchedEffect(isVisibleForAutoPlay) {
        if (!isVisibleForAutoPlay) {
            userPaused = false
        }
    }

    val playVideo = isVisibleForAutoPlay && !userPaused

    LaunchedEffect(reel.id) {
        if (resolvedVideoFile == null && !loadingVideo) {
            loadingVideo = true
            resolvedVideoFile = resolveVideoFile(reel).getOrNull()
            loadingVideo = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF000000))
            .testTag("home_reel_card_${reel.id}")
    ) {
        if (isSuggested) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Suggested for you",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White.copy(alpha = 0.75f)
                )
                IconButton(
                    onClick = onDismissSuggestion,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss suggestion",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 1. Instagram-style Header (Avatar, Username + Verified, Original Audio • Reel, Follow, More)
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
                    seed = reel.userId,
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
                        if (flag.isNotBlank()) {
                            Text(text = flag, fontSize = 12.sp)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "$displayAuthorName • Original audio",
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
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (!isOwner) {
                    val followLabel = if (isFollowing) "Following" else "Follow"
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF262626),
                        modifier = Modifier
                            .height(32.dp)
                            .clickable(onClick = onToggleFollow)
                            .testTag("reel_follow_btn_${reel.id}")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = followLabel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Reel options",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Watch in Reels") },
                            leadingIcon = { Icon(Icons.Filled.Movie, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onOpenFullScreenReel()
                            }
                        )
                        if (isOwner) {
                            DropdownMenuItem(
                                text = { Text("Delete Reel") },
                                leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Report Reel") },
                                leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onReportReel()
                                }
                            )
                            if (isModerator && onModeratorDeleteReel != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Remove Reel (Moderator)",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Shield,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showModeratorDeleteDialog = true
                                    },
                                    modifier = Modifier.testTag("moderator_delete_reel_menu_${reel.id}")
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("Share Reel") },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "$displayAuthorName's reel on Kindred: ${reel.caption}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share reel"))
                            }
                        )
                    }
                }
            }
        }

        // 2. Full-bleed 4:5 Feed Video Player (1080x1350 aspect)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(com.example.model.KindredConstants.FEED_VIDEO_ASPECT_RATIO)
                .background(Color(0xFF0D0C10))
                .pointerInput(reel.id) {
                    detectTapGestures(
                        onTap = {
                            handleReelTapLikeToggle()
                        }
                    )
                }
                .testTag("home_reel_media_${reel.id}"),
            contentAlignment = Alignment.Center
        ) {
            val file = resolvedVideoFile
            if (file != null) {
                com.example.ui.components.LoopingVideoPlayer(
                    videoFile = file,
                    shouldPlay = playVideo,
                    isMuted = isMuted,
                    isActiveInViewport = isVisibleForAutoPlay,
                    onFirstFrameRendered = { isFirstFrameRendered = true },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (file == null || !isFirstFrameRendered || (!playVideo && !loadingVideo)) {
                val thumb = remember(reel.thumbnailDataUrl) {
                    reel.thumbnailDataUrl?.let { MediaImageHelper.decodeBase64DataUrl(it) }
                }
                if ((file == null || !isFirstFrameRendered) && thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = "Reel preview",
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
                                contentDescription = "Play reel",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
            }

            // Top-right clickable "Reels" pill badge to open full-screen Reels viewer
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenFullScreenReel)
                    .testTag("reel_expand_pill_${reel.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = "Watch full Reel",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Reel",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Play/Pause control bottom-start
            if (file != null) {
                IconButton(
                    onClick = { userPaused = !userPaused },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .size(34.dp)
                        .testTag("home_reel_play_pause_${reel.id}")
                ) {
                    Icon(
                        imageVector = if (playVideo) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playVideo) "Pause reel" else "Play reel",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Mute/Unmute toggle bottom-right
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

            if (showHeartPop) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Liked",
                        tint = Color.White.copy(alpha = heartAlpha.value),
                        modifier = Modifier
                            .size(105.dp)
                            .scale(heartScale.value)
                            .graphicsLayer {
                                shadowElevation = 20f
                            }
                    )
                }
            }
        }

        // 3. Action Bar: Like, Comment, Share, Watch in Reels
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
                        .minimumInteractiveComponentSize()
                        .clickable {
                            scope.launch {
                                likeScale.animateTo(1.35f, spring(Spring.DampingRatioMediumBouncy))
                                likeScale.animateTo(1f)
                            }
                            onToggleLike()
                        }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .testTag("home_reel_like_${reel.id}")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like reel",
                        tint = if (isLiked) CoralRose else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .size(25.dp)
                            .scale(likeScale.value)
                    )
                    if (reel.likedByUids.isNotEmpty()) {
                        Text(
                            text = "${reel.likedByUids.size}",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                IconButton(
                    onClick = onOpenComments,
                    modifier = Modifier.testTag("home_reel_comment_${reel.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = "Comment on reel",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(23.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Watch $displayAuthorName's reel on Kindred: ${reel.caption}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share reel"))
                    },
                    modifier = Modifier.testTag("home_reel_share_${reel.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Share reel",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            IconButton(
                onClick = onOpenFullScreenReel,
                modifier = Modifier.testTag("home_reel_open_viewer_${reel.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = "Open Reels Viewer",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 4. Caption, Comment trigger & Timestamp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (reel.caption.isNotBlank()) {
                val captionAnnotated = remember(reel.caption, displayAuthorName) {
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(displayAuthorName)
                            append(" ")
                        }
                        val tokens = reel.caption.split(" ")
                        tokens.forEachIndexed { idx, token ->
                            if (token.startsWith("#") && token.length > 1) {
                                withStyle(SpanStyle(color = Color(0xFF0095F6), fontWeight = FontWeight.Medium)) {
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

            Text(
                text = "Add a comment...",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier
                    .clickable(onClick = onOpenComments)
                    .padding(vertical = 1.dp)
            )

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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Video?") },
            text = { Text("Are you sure you want to delete this video reel? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteReel()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_reel_${reel.id}")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.testTag("cancel_delete_reel_${reel.id}")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showModeratorDeleteDialog && onModeratorDeleteReel != null) {
        ModeratorDeletePostDialog(
            targetAuthorName = displayAuthorName,
            contentTypeLabel = "reel",
            contentPreview = reel.caption,
            onDismiss = { showModeratorDeleteDialog = false },
            onConfirmDelete = { reason, details ->
                showModeratorDeleteDialog = false
                onModeratorDeleteReel(reason, details)
            }
        )
    }
}

