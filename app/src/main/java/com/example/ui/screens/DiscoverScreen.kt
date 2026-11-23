package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DiscoveryCandidate
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.FriendRequestRecord
import com.example.model.FriendStatus
import com.example.model.PrivateUser
import com.example.model.ProfileLikeRecord
import com.example.model.PublicProfile
import com.example.model.StoryItem
import com.example.ui.components.CreatePostComposerSheet
import com.example.ui.components.CreatePostInlineBanner
import com.example.ui.components.InstagramFeedPostCard
import com.example.ui.components.PostCommentsSheet
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.components.StoriesTrayRow
import com.example.ui.components.StoryViewerDialog
import com.example.ui.components.TimelineNotificationsSheet
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.LocationAndGeoHelper
import com.example.util.PreparedVideoUpload
import com.example.util.ResolvedDeviceLocation
import com.example.viewmodel.GeoHashContextMeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    myProfile: PublicProfile?,
    privateUser: PrivateUser?,
    geoHashMeta: GeoHashContextMeta?,
    candidates: List<DiscoveryCandidate> = emptyList(),
    onSwipe: (DiscoveryCandidate, String) -> Unit = { _, _ -> },
    onBlockUser: (DiscoveryCandidate) -> Unit = {},
    onReportUser: (candidate: DiscoveryCandidate, reason: String, details: String, alsoBlock: Boolean) -> Unit = { _, _, _, _ -> },
    onRefreshLocation: (ResolvedDeviceLocation) -> Unit = {},
    onLocationError: (String) -> Unit = {},
    onUpdateFilters: (String, Int, Int, Int, Boolean) -> Unit = { _, _, _, _, _ -> },
    onNavigateToProfile: () -> Unit = {},
    onNavigateToVerify: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    latestLocation: ResolvedDeviceLocation? = null,
    onLocationPermissionGranted: () -> Unit = {},
    canUndoLastSwipe: Boolean = false,
    onUndoSwipe: () -> Unit = {},
    activeOnlineCount: Int = 0,
    onOpenActiveUsers: () -> Unit = {},
    stories: List<StoryItem> = emptyList(),
    onPostPhotoStory: (photoDataUrl: String, caption: String) -> Unit = { _, _ -> },
    onPostVideoStory: (prepared: PreparedVideoUpload, caption: String) -> Unit = { _, _ -> },
    onMarkStoryViewed: (String) -> Unit = {},
    onDeleteStory: (String) -> Unit = {},
    resolveStoryVideoFile: suspend (StoryItem) -> Result<File> = { Result.failure(IllegalStateException("Unavailable")) },
    onOpenVideoFeed: () -> Unit = {},
    suggestedProfiles: List<DiscoveryCandidate> = emptyList(),
    feedPosts: List<FeedPost> = emptyList(),
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
    var isLocating by remember { mutableStateOf(false) }

    var activeStoryIndex by remember { mutableStateOf<Int?>(null) }
    var showPostComposerSheet by remember { mutableStateOf(false) }
    var activeCommentsPost by remember { mutableStateOf<FeedPost?>(null) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    fun fetchAndApplyGps() {
        isLocating = true
        scope.launch {
            val loc = LocationAndGeoHelper.fetchCurrentLocation(context)
            isLocating = false
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
            onLocationError("Location permission denied. You can still set your city manually in Profile.")
        }
    }

    val isLiveGps = latestLocation?.isLiveGps == true
    val cityDisplay = latestLocation?.let { loc ->
        if (loc.countryName.isNotBlank() && !loc.cityLabel.contains(loc.countryName, ignoreCase = true)) {
            "${loc.cityLabel}, ${loc.countryName}"
        } else {
            loc.cityLabel
        }
    } ?: myProfile?.formattedLocation() ?: "Set location"

    val peopleToKnow = remember(suggestedProfiles, candidates) {
        (if (suggestedProfiles.isNotEmpty()) suggestedProfiles else candidates).take(20)
    }

    val pullRefreshState = rememberPullToRefreshState()
    val feedListState = rememberLazyListState()
    val autoPlayVideoPostId by remember(feedPosts) {
        derivedStateOf {
            val videoIds = feedPosts.asSequence().filter { it.mediaType == "VIDEO" }.map { it.id }.toSet()
            feedListState.layoutInfo.visibleItemsInfo
                .firstOrNull { info -> (info.key as? String) in videoIds }
                ?.key as? String
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("discover_screen")
    ) {
        val isWide = maxWidth >= 700.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = if (isWide) 640.dp else 560.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Navigation Bar (Fixed at top)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Kindred Logo Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.testTag("kindred_home_logo")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = CoralRose,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Kindred",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Top Right Action Icons: GPS Chip + Search + Notification Bell + Direct Messages
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Location Pill
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
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
                            .testTag("refresh_location_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isLocating) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.MyLocation,
                                    contentDescription = "Location",
                                    tint = if (isLiveGps) EmeraldMint else CoralRose,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = cityDisplay.take(16),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Search Button
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("discover_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Notification Bell
                    IconButton(
                        onClick = { showNotificationsSheet = true },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("discover_notifications_button")
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
                                    imageVector = Icons.Filled.Notifications,
                                    contentDescription = "Notifications",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Filled.NotificationsNone,
                                contentDescription = "Notifications",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Direct Messages Icon
                    IconButton(
                        onClick = onNavigateToInbox,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("discover_dm_button")
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
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Direct Messages",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 2. Main Social Timeline Feed with Pull-To-Refresh
            PullToRefreshBox(
                isRefreshing = isFeedRefreshing,
                onRefresh = onRefreshFeed,
                state = pullRefreshState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = feedListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("social_timeline_feed"),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Stories Section
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
                    }

                    // Create Post Section
                    item(key = "create_post_section") {
                        CreatePostInlineBanner(
                            myProfile = myProfile,
                            onOpenComposer = { showPostComposerSheet = true },
                            onPhotoClick = { showPostComposerSheet = true },
                            onVideoClick = { showPostComposerSheet = true }
                        )
                    }

                    // People You May Know Section
                    if (peopleToKnow.isNotEmpty()) {
                        item(key = "people_you_may_know_section") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "People You May Know",
                                        style = MaterialTheme.typography.titleMedium,
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
                                    contentPadding = PaddingValues(vertical = 2.dp),
                                    modifier = Modifier.testTag("people_you_may_know_list")
                                ) {
                                    items(peopleToKnow, key = { it.profile.userId }) { candidate ->
                                        val person = candidate.profile
                                        val flag = person.flagEmoji()
                                        val fStatus = friendStatusForUid(person.userId)
                                        Card(
                                            shape = RoundedCornerShape(18.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                            modifier = Modifier
                                                .width(166.dp)
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
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
                                                val friendBtnLabel = when (fStatus) {
                                                    FriendStatus.NONE -> "Add Friend"
                                                    FriendStatus.OUTGOING_PENDING -> "Sent"
                                                    FriendStatus.INCOMING_PENDING -> "Accept"
                                                    FriendStatus.FRIENDS -> "Friends ✓"
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
                            }
                        }
                    }

                    // Main Timeline Posts Header
                    item(key = "feed_posts_header") {
                        Text(
                            text = "Timeline Feed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Timeline Posts List
                    if (feedPosts.isEmpty()) {
                        item(key = "empty_feed_posts") {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = CoralRose,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "Welcome to your Kindred Feed",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Share photos, video clips, and updates with friends and discover connections around the world.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = { showPostComposerSheet = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
                                    ) {
                                        Text("Create First Post")
                                    }
                                }
                            }
                        }
                    } else {
                        items(feedPosts, key = { it.id }) { post ->
                            InstagramFeedPostCard(
                                post = post,
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
                                isVisibleForAutoPlay = (post.id == autoPlayVideoPostId)
                            )
                        }
                    }
                }
            }
        }
    }

    // Story Viewer Dialog
    if (activeStoryIndex != null && stories.isNotEmpty()) {
        StoryViewerDialog(
            stories = stories,
            initialIndex = activeStoryIndex!!.coerceIn(0, stories.lastIndex),
            currentUserId = currentUserId,
            resolveVideoFile = resolveStoryVideoFile,
            onMarkViewed = onMarkStoryViewed,
            onDeleteStory = onDeleteStory,
            onOpenAuthorProfile = onOpenUserProfileById,
            onDismiss = { activeStoryIndex = null }
        )
    }

    // Post Composer Sheet
    if (showPostComposerSheet) {
        CreatePostComposerSheet(
            myProfile = myProfile,
            onDismiss = { showPostComposerSheet = false },
            onPublish = { caption, photoDataUrl, photoUrls, video ->
                onPublishFeedPost(caption, photoDataUrl, photoUrls, video) {}
            }
        )
    }

    // Post Comments Sheet
    activeCommentsPost?.let { post ->
        PostCommentsSheet(
            post = post,
            currentUserId = currentUserId,
            myProfile = myProfile,
            observeComments = observeFeedComments,
            onAddComment = onAddFeedPostComment,
            onDeleteComment = onDeleteFeedPostComment,
            onOpenProfile = onOpenUserProfileById,
            onDismiss = { activeCommentsPost = null }
        )
    }

    // Notifications & Activity Sheet
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
