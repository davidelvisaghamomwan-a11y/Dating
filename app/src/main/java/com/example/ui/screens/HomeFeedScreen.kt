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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
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
import com.example.model.DiscoveryCandidate
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.FriendRequestRecord
import com.example.model.FriendStatus
import com.example.model.ProfileLikeRecord
import com.example.model.PublicProfile
import com.example.model.StoryItem
import com.example.model.VideoPost
import com.example.ui.components.CreatePostComposerSheet
import com.example.ui.components.InstagramFeedPostCard
import com.example.ui.components.PostCommentsSheet
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.components.StoriesTrayRow
import com.example.ui.components.StoryViewerDialog
import com.example.ui.components.TimelineNotificationsSheet
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.CountryFlagHelper
import com.example.util.LocationAndGeoHelper
import com.example.util.PreparedVideoUpload
import com.example.util.ResolvedDeviceLocation
import com.example.util.VerificationAnalyzer
import com.example.util.VideoClipHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private enum class FeedFilterMode(val label: String) {
    FOR_YOU("For you"),
    FOLLOWING("Following")
}

private sealed interface HomeTimelineItem {
    val key: String
    val userId: String
    val createdAtSeconds: Long
    val createdAtNanos: Int
    val isPlayableVideo: Boolean

    data class PostItem(val post: FeedPost) : HomeTimelineItem {
        override val key: String = "post_${post.id}"
        override val userId: String = post.userId
        override val createdAtSeconds: Long = post.createdAt?.seconds ?: 0L
        override val createdAtNanos: Int = post.createdAt?.nanoseconds ?: 0
        override val isPlayableVideo: Boolean = post.mediaType == "VIDEO"
    }

    data class ReelItem(val reel: VideoPost) : HomeTimelineItem {
        override val key: String = "reel_${reel.id}"
        override val userId: String = reel.userId
        override val createdAtSeconds: Long = reel.createdAt?.seconds ?: 0L
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
    onMarkStoryViewed: (String) -> Unit = {},
    onDeleteStory: (String) -> Unit = {},
    resolveStoryVideoFile: suspend (StoryItem) -> Result<File> = { Result.failure(IllegalStateException("Unavailable")) },
    onOpenVideoFeed: () -> Unit = {},
    suggestedProfiles: List<DiscoveryCandidate> = emptyList(),
    feedPosts: List<FeedPost> = emptyList(),
    videoPosts: List<VideoPost> = emptyList(),
    currentUserId: String = myProfile?.userId ?: "",
    friendStatusForUid: (String) -> FriendStatus = { FriendStatus.NONE },
    onFriendAction: (PublicProfile) -> Unit = {},
    onOpenUserProfile: (PublicProfile) -> Unit = {},
    onOpenUserProfileById: (String) -> Unit = {},
    onPublishFeedPost: (caption: String, photoDataUrl: String?, photoUrls: List<String>, preparedVideo: PreparedVideoUpload?, onComplete: () -> Unit) -> Unit = { _, _, _, _, cb -> cb() },
    onToggleFeedPostLike: (postId: String, currentlyLiked: Boolean) -> Unit = { _, _ -> },
    onToggleFeedPostSave: (postId: String, currentlySaved: Boolean) -> Unit = { _, _ -> },
    observeFeedComments: (String) -> Flow<List<FeedComment>> = { flowOf(emptyList()) },
    onAddFeedPostComment: (postId: String, text: String, onDone: () -> Unit) -> Unit = { _, _, cb -> cb() },
    onDeleteFeedPostComment: (postId: String, commentId: String) -> Unit = { _, _ -> },
    onDeleteFeedPost: (postId: String) -> Unit = {},
    resolveFeedPostVideoFile: suspend (FeedPost) -> Result<File> = { Result.failure(IllegalStateException("Unavailable")) },
    onToggleVideoPostLike: (postId: String, currentlyLiked: Boolean) -> Unit = { _, _ -> },
    onDeleteVideoPost: (postId: String) -> Unit = {},
    resolveVideoPostFile: suspend (VideoPost) -> Result<File> = { Result.failure(IllegalStateException("Unavailable")) },
    observeVideoComments: (postId: String) -> Flow<List<FeedComment>> = { flowOf(emptyList()) },
    onAddVideoComment: (postId: String, text: String, onDone: () -> Unit) -> Unit = { _, _, cb -> cb() },
    onDeleteVideoComment: (postId: String, commentId: String) -> Unit = { _, _ -> },
    onOpenReelInVideoFeed: (reelId: String) -> Unit = { onOpenVideoFeed() },
    isFeedRefreshing: Boolean = false,
    onRefreshFeed: () -> Unit = {},
    unreadMessageCount: Int = 0,
    incomingNotificationCount: Int = 0,
    friendRequests: List<FriendRequestRecord> = emptyList(),
    profileLikes: List<ProfileLikeRecord> = emptyList(),
    profilesByUid: Map<String, PublicProfile> = emptyMap(),
    onAcceptFriendRequest: (String) -> Unit = {},
    onRejectFriendRequest: (String) -> Unit = {},
    onNavigateToInbox: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var filterMode by remember { mutableStateOf(FeedFilterMode.FOR_YOU) }
    var showFeedFilterMenu by remember { mutableStateOf(false) }
    var activeStoryIndex by remember { mutableStateOf<Int?>(null) }
    var showPostComposerSheet by remember { mutableStateOf(false) }
    var activeCommentsPost by remember { mutableStateOf<FeedPost?>(null) }
    var activeCommentsReel by remember { mutableStateOf<VideoPost?>(null) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

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

    val displayedTimelineItems = remember(feedPosts, videoPosts, filterMode, currentUserId) {
        val rawCombined = buildList<HomeTimelineItem> {
            feedPosts.forEach { add(HomeTimelineItem.PostItem(it)) }
            videoPosts.forEach { add(HomeTimelineItem.ReelItem(it)) }
        }
        val filtered = if (filterMode == FeedFilterMode.FOLLOWING) {
            rawCombined.filter { item ->
                item.userId == currentUserId || friendStatusForUid(item.userId) == FriendStatus.FRIENDS
            }
        } else {
            rawCombined
        }
        filtered.sortedWith(
            compareByDescending<HomeTimelineItem> { it.createdAtSeconds }
                .thenByDescending { it.createdAtNanos }
        )
    }

    // Prioritize other people's Reels for the "Suggested reels" horizontal shelf on Home
    val shelfReels = remember(videoPosts, filterMode, currentUserId) {
        val base = if (filterMode == FeedFilterMode.FOLLOWING) {
            videoPosts.filter {
                it.userId != currentUserId && friendStatusForUid(it.userId) == FriendStatus.FRIENDS
            }
        } else {
            val others = videoPosts.filter { it.userId != currentUserId }
            others.ifEmpty { videoPosts }
        }
        base.take(15)
    }

    val peopleToKnow = remember(suggestedProfiles, candidates) {
        (if (suggestedProfiles.isNotEmpty()) suggestedProfiles else candidates).take(20)
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
        VerificationAnalyzer.prefetchDataUrls(urlsToWarm)
    }

    val pullRefreshState = rememberPullToRefreshState()
    val feedListState = rememberLazyListState()
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
                            text = { Text("For you") },
                            leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                            trailingIcon = {
                                if (filterMode == FeedFilterMode.FOR_YOU) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF0095F6))
                                }
                            },
                            onClick = {
                                filterMode = FeedFilterMode.FOR_YOU
                                showFeedFilterMenu = false
                            }
                        )
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
                            text = { Text("Search People") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            onClick = {
                                showFeedFilterMenu = false
                                onOpenSearch()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh GPS Location") },
                            leadingIcon = { Icon(Icons.Filled.MyLocation, contentDescription = null) },
                            onClick = {
                                showFeedFilterMenu = false
                                if (LocationAndGeoHelper.hasLocationPermission(context)) {
                                    fetchAndApplyGps()
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    )
                                }
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
                        onClick = { showNotificationsSheet = true },
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
                                    text = if (filterMode == FeedFilterMode.FOLLOWING) {
                                        "No Posts from Friends Yet"
                                    } else {
                                        "Welcome to Kindred"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (filterMode == FeedFilterMode.FOLLOWING) {
                                        "Switch to \"For you\" to explore posts and reels from members across Kindred, or follow people below."
                                    } else {
                                        "Share photos, reels, and updates with the Kindred community."
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
                                    friendStatusForUid = friendStatusForUid,
                                    onOpenActiveUsers = onOpenActiveUsers,
                                    onOpenUserProfile = onOpenUserProfile,
                                    onFriendAction = onFriendAction
                                )
                            }
                        }
                    } else {
                        itemsIndexed(displayedTimelineItems, key = { _, item -> item.key }) { index, item ->
                            when (item) {
                                is HomeTimelineItem.PostItem -> {
                                    val post = item.post
                                    InstagramFeedPostCard(
                                        post = post,
                                        authorProfile = profilesByUid[post.userId],
                                        currentUserId = currentUserId,
                                        friendStatus = friendStatusForUid(post.userId),
                                        onFriendAction = {
                                            val profile = profilesByUid[post.userId]
                                            if (profile != null) onFriendAction(profile)
                                        },
                                        resolveVideoFile = resolveFeedPostVideoFile,
                                        onToggleLike = {
                                            onToggleFeedPostLike(post.id, currentUserId in post.likedByUids)
                                        },
                                        onToggleSave = {
                                            onToggleFeedPostSave(post.id, currentUserId in post.savedByUids)
                                        },
                                        onOpenComments = { activeCommentsPost = post },
                                        onDeletePost = { onDeleteFeedPost(post.id) },
                                        onOpenAuthor = { onOpenUserProfileById(post.userId) },
                                        onReportPost = {
                                            val candidate = candidates.firstOrNull { it.profile.userId == post.userId }
                                            if (candidate != null) {
                                                onReportUser(candidate, "Inappropriate Content", "Post ${post.id}", false)
                                            }
                                        },
                                        isVisibleForAutoPlay = (item.key == autoPlayVideoKey)
                                    )
                                }

                                is HomeTimelineItem.ReelItem -> {
                                    val reel = item.reel
                                    HomeReelPostCard(
                                        reel = reel,
                                        authorProfile = profilesByUid[reel.userId],
                                        currentUserId = currentUserId,
                                        friendStatus = friendStatusForUid(reel.userId),
                                        onFriendAction = {
                                            val profile = profilesByUid[reel.userId] ?: PublicProfile(
                                                userId = reel.userId,
                                                displayName = reel.authorName
                                            )
                                            onFriendAction(profile)
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
                                        friendStatusForUid = friendStatusForUid,
                                        onOpenActiveUsers = onOpenActiveUsers,
                                        onOpenUserProfile = onOpenUserProfile,
                                        onFriendAction = onFriendAction
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (activeStoryIndex != null && stories.isNotEmpty()) {
        StoryViewerDialog(
            stories = stories,
            initialIndex = activeStoryIndex!!.coerceIn(0, stories.lastIndex),
            currentUserId = currentUserId,
            profilesByUid = profilesByUid,
            resolveVideoFile = resolveStoryVideoFile,
            onMarkViewed = onMarkStoryViewed,
            onDeleteStory = onDeleteStory,
            onOpenAuthorProfile = onOpenUserProfileById,
            onDismiss = { activeStoryIndex = null }
        )
    }

    if (showPostComposerSheet) {
        CreatePostComposerSheet(
            myProfile = myProfile,
            onDismiss = { showPostComposerSheet = false },
            onPublish = { caption, photoDataUrl, photoUrls, video ->
                onPublishFeedPost(caption, photoDataUrl, photoUrls, video) {}
            }
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
                authorVerified = vidPost.authorVerified,
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

    if (showNotificationsSheet) {
        TimelineNotificationsSheet(
            friendRequests = friendRequests,
            profileLikes = profileLikes,
            profilesByUid = profilesByUid,
            onAcceptRequest = onAcceptFriendRequest,
            onRejectRequest = onRejectFriendRequest,
            onOpenProfile = onOpenUserProfileById,
            onDismiss = { showNotificationsSheet = false }
        )
    }
}

@Composable
private fun SuggestedPeopleStrip(
    peopleToKnow: List<DiscoveryCandidate>,
    activeOnlineCount: Int,
    friendStatusForUid: (String) -> FriendStatus,
    onOpenActiveUsers: () -> Unit,
    onOpenUserProfile: (PublicProfile) -> Unit,
    onFriendAction: (PublicProfile) -> Unit
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
                val fStatus = friendStatusForUid(person.userId)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = buildString {
                                    append("${person.displayName}, ${person.age}")
                                    if (flag.isNotBlank()) append(" $flag")
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (person.isProfileVerified) {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF0095F6),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = person.bio.ifBlank { person.formattedLocation() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        val friendBtnLabel = when (fStatus) {
                            FriendStatus.NONE -> "Follow"
                            FriendStatus.OUTGOING_PENDING -> "Requested"
                            FriendStatus.INCOMING_PENDING -> "Accept"
                            FriendStatus.FRIENDS -> "Following"
                        }
                        FilledTonalButton(
                            onClick = { onFriendAction(person) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("feed_add_friend_${person.userId}")
                        ) {
                            Text(friendBtnLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
