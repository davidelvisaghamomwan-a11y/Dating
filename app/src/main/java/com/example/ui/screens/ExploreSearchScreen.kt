package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.ActiveMemberPresenceItem
import com.example.model.DiscoveryCandidate
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.PublicProfile
import com.example.model.VideoPost
import com.example.ui.components.InstagramFeedPostCard
import com.example.ui.components.PostCommentsSheet
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.CountryFlagHelper
import com.example.util.MediaImageHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File
import kotlin.random.Random

private val ExploreInstagramBlue = Color(0xFF0095F6)

private sealed interface ExplorePostItem {
    val key: String
    val userId: String
    val authorName: String
    val imageUrl: String?
    val isVideo: Boolean
    val isCarousel: Boolean

    data class FeedEntry(
        val post: FeedPost,
        val resolvedAuthor: PublicProfile?
    ) : ExplorePostItem {
        private val photos = post.allPhotoUrls()
        override val key: String = "feed_${post.id}"
        override val userId: String = post.userId
        override val authorName: String = resolvedAuthor?.displayName?.takeIf { it.isNotBlank() }
            ?: post.authorName
        override val imageUrl: String? = if (post.mediaType == "VIDEO") {
            post.thumbnailDataUrl
        } else {
            photos.firstOrNull()
        }
        override val isVideo: Boolean = post.mediaType == "VIDEO"
        override val isCarousel: Boolean = !isVideo && photos.size > 1
    }

    data class ReelEntry(
        val videoPost: VideoPost,
        val resolvedAuthor: PublicProfile?
    ) : ExplorePostItem {
        override val key: String = "reel_${videoPost.id}"
        override val userId: String = videoPost.userId
        override val authorName: String = resolvedAuthor?.displayName?.takeIf { it.isNotBlank() }
            ?: videoPost.authorName
        override val imageUrl: String? = videoPost.thumbnailDataUrl
        override val isVideo: Boolean = true
        override val isCarousel: Boolean = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreSearchScreen(
    currentUserId: String,
    myProfile: PublicProfile?,
    searchableProfiles: List<DiscoveryCandidate>,
    feedPosts: List<FeedPost>,
    videoPosts: List<VideoPost> = emptyList(),
    profilesByUid: Map<String, PublicProfile>,
    activeMembers: List<ActiveMemberPresenceItem> = emptyList(),
    onOpenActiveUsers: () -> Unit = {},
    isFollowingUid: (String) -> Boolean = { false },
    onToggleFollow: (PublicProfile) -> Unit = {},
    onOpenUserProfile: (String) -> Unit,
    onToggleFeedPostLike: (postId: String, currentlyLiked: Boolean) -> Unit,
    onToggleFeedPostSave: (postId: String, currentlySaved: Boolean) -> Unit,
    onToggleVideoPostLike: (postId: String, currentlyLiked: Boolean) -> Unit = { _, _ -> },
    observeFeedComments: (postId: String) -> Flow<List<FeedComment>> = { flowOf(emptyList()) },
    onAddFeedPostComment: (postId: String, text: String, onDone: () -> Unit) -> Unit = { _, _, _ -> },
    onDeleteFeedPostComment: (postId: String, commentId: String) -> Unit = { _, _ -> },
    observeVideoComments: (postId: String) -> Flow<List<FeedComment>> = { flowOf(emptyList()) },
    onAddVideoComment: (postId: String, text: String, onDone: () -> Unit) -> Unit = { _, _, _ -> },
    onDeleteVideoComment: (postId: String, commentId: String) -> Unit = { _, _ -> },
    onDeleteFeedPost: (postId: String) -> Unit = {},
    resolveFeedPostVideoFile: suspend (FeedPost) -> Result<File>,
    resolveVideoPostFile: suspend (VideoPost) -> Result<File> = { Result.failure(IllegalStateException("Unavailable")) },
    onRefreshExplore: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var shuffleSeed by remember { mutableIntStateOf(Random.nextInt()) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var selectedExploreIndex by remember { mutableStateOf<Int?>(null) }
    var activeCommentsPost by remember { mutableStateOf<FeedPost?>(null) }

    val query = searchQuery.trim()

    val matchingUsers = remember(searchableProfiles, query, currentUserId) {
        if (query.isBlank()) {
            emptyList()
        } else {
            searchableProfiles
                .filter { it.profile.userId != currentUserId }
                .filter { candidate ->
                    val p = candidate.profile
                    p.displayName.contains(query, ignoreCase = true) ||
                        p.cityLabel.contains(query, ignoreCase = true) ||
                        p.country.contains(query, ignoreCase = true) ||
                        p.bio.contains(query, ignoreCase = true)
                }
                .sortedWith(
                    compareByDescending<DiscoveryCandidate> {
                        it.profile.displayName.lowercase().startsWith(query.lowercase())
                    }.thenBy { it.distanceKm }
                )
        }
    }

    // Build randomized Explore post grid from user feed posts and reel video posts (no profile cards)
    val allExplorePosts = remember(feedPosts, videoPosts, profilesByUid, shuffleSeed) {
        val seenThumbnails = mutableSetOf<String>()
        val combined = mutableListOf<ExplorePostItem>()

        feedPosts.forEach { post ->
            val hasVisualMedia = post.mediaType == "VIDEO" || post.allPhotoUrls().isNotEmpty()
            if (hasVisualMedia) {
                val entry = ExplorePostItem.FeedEntry(
                    post = post,
                    resolvedAuthor = profilesByUid[post.userId]
                )
                val thumb = entry.imageUrl
                if (thumb.isNullOrBlank() || seenThumbnails.add(thumb)) {
                    combined += entry
                }
            }
        }

        videoPosts.forEach { reel ->
            val entry = ExplorePostItem.ReelEntry(
                videoPost = reel,
                resolvedAuthor = profilesByUid[reel.userId]
            )
            val thumb = entry.imageUrl
            if (thumb.isNullOrBlank() || seenThumbnails.add(thumb)) {
                combined += entry
            }
        }

        combined.shuffled(Random(shuffleSeed))
    }

    val displayedExplorePosts = remember(allExplorePosts, query) {
        if (query.isBlank()) {
            allExplorePosts
        } else {
            allExplorePosts.filter { item ->
                when (item) {
                    is ExplorePostItem.FeedEntry -> {
                        item.authorName.contains(query, ignoreCase = true) ||
                            item.post.caption.contains(query, ignoreCase = true) ||
                            item.post.authorCity.orEmpty().contains(query, ignoreCase = true) ||
                            item.post.authorCountry.orEmpty().contains(query, ignoreCase = true)
                    }
                    is ExplorePostItem.ReelEntry -> {
                        item.authorName.contains(query, ignoreCase = true) ||
                            item.videoPost.caption.contains(query, ignoreCase = true) ||
                            item.videoPost.authorCountry.orEmpty().contains(query, ignoreCase = true)
                    }
                }
            }
        }
    }

    LaunchedEffect(displayedExplorePosts) {
        val urls = displayedExplorePosts.take(24).mapNotNull { it.imageUrl }
        if (urls.isNotEmpty()) {
            MediaImageHelper.prefetchDataUrls(urls)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("explore_search_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
        ) {
            // Instagram-Style Top Search Pill + Three-Dot Overflow Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { if (it.length <= 60) searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("explore_search_input")
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("explore_search_clear")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.testTag("explore_more_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreHoriz,
                            contentDescription = "Explore options",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Shuffle posts") },
                            leadingIcon = { Icon(Icons.Filled.Shuffle, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                shuffleSeed = Random.nextInt()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh Explore") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onRefreshExplore()
                                shuffleSeed = Random.nextInt()
                            }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (query.isBlank() && activeMembers.isNotEmpty()) {
                    item(key = "explore_active_now_strip") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldMint)
                                    )
                                    Text(
                                        text = "Active now",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "See all",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ExploreInstagramBlue,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable(onClick = onOpenActiveUsers)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .testTag("explore_active_see_all")
                                )
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.testTag("explore_active_users_row")
                            ) {
                                items(activeMembers.take(16), key = { "active_${it.profile.userId}" }) { member ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .width(64.dp)
                                            .clickable { onOpenUserProfile(member.profile.userId) }
                                            .testTag("explore_active_user_${member.profile.userId}")
                                    ) {
                                        Box {
                                            ProfileAvatarImage(
                                                photoDataUrl = member.profile.photoDataUrl,
                                                displayName = member.profile.displayName,
                                                seed = member.profile.avatarSeed,
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .border(
                                                        1.5.dp,
                                                        if (member.isOnline) EmeraldMint else MaterialTheme.colorScheme.outlineVariant,
                                                        CircleShape
                                                    )
                                                    .padding(2.dp),
                                                shape = CircleShape,
                                                initialsFontSizeSp = 18
                                            )
                                            if (member.isOnline) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(13.dp)
                                                        .align(Alignment.BottomEnd)
                                                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                                        .clip(CircleShape)
                                                        .background(EmeraldMint)
                                                )
                                            }
                                        }
                                        Text(
                                            text = member.profile.displayName.ifBlank { "Member" },
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Only show matching People rows while actively typing a search query
                if (query.isNotBlank() && matchingUsers.isNotEmpty()) {
                    item(key = "matching_users_header") {
                        Text(
                            text = "Accounts",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    items(
                        items = matchingUsers.take(12),
                        key = { "search_person_${it.profile.userId}" }
                    ) { candidate ->
                        ExploreSearchUserRow(
                            candidate = candidate,
                            isFollowing = isFollowingUid(candidate.profile.userId),
                            onToggleFollow = { onToggleFollow(candidate.profile) },
                            onClick = { onOpenUserProfile(candidate.profile.userId) }
                        )
                    }
                    if (displayedExplorePosts.isNotEmpty()) {
                        item(key = "matching_photos_divider") {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Text(
                                text = "Posts",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (displayedExplorePosts.isEmpty() && (query.isBlank() || matchingUsers.isEmpty())) {
                    item(key = "explore_empty_state") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = if (query.isBlank()) "No Explore posts yet" else "No matching results",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (query.isBlank()) {
                                    "Photos and videos posted across Kindred will appear here."
                                } else {
                                    "Try another name, hashtag, city, or topic."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val rows = displayedExplorePosts.chunked(3)
                    items(
                        count = rows.size,
                        key = { idx -> "explore_grid_row_$idx" }
                    ) { rowIdx ->
                        val rowItems = rows[rowIdx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 1.5.dp),
                            horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                        ) {
                            for (col in 0 until 3) {
                                val tile = rowItems.getOrNull(col)
                                if (tile == null) {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(0.84f)
                                    )
                                } else {
                                    val absoluteIndex = rowIdx * 3 + col
                                    ExplorePostGridTile(
                                        item = tile,
                                        onClick = { selectedExploreIndex = absoluteIndex },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Explore Post Feed Dialog: opens directly at the clicked post, displays creator's name/avatar/follow,
    // and tapping the poster's name or avatar takes you straight into their profile page just like Instagram!
    selectedExploreIndex?.let { startIndex ->
        if (displayedExplorePosts.isNotEmpty()) {
            val clampedStart = startIndex.coerceIn(0, displayedExplorePosts.lastIndex)
            val feedListState = rememberLazyListState(initialFirstVisibleItemIndex = clampedStart)
            val autoPlayKey by remember(displayedExplorePosts) {
                derivedStateOf {
                    val videoKeys = displayedExplorePosts
                        .asSequence()
                        .filter { it.isVideo }
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
                        ?.key as? String
                }
            }

            Dialog(
                onDismissRequest = { selectedExploreIndex = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Explore",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = { selectedExploreIndex = null },
                                    modifier = Modifier.testTag("explore_detail_back")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to Explore"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )

                        LazyColumn(
                            state = feedListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(
                                items = displayedExplorePosts,
                                key = { it.key }
                            ) { exploreItem ->
                                when (exploreItem) {
                                    is ExplorePostItem.FeedEntry -> {
                                        val livePost = feedPosts.firstOrNull { it.id == exploreItem.post.id }
                                            ?: exploreItem.post
                                        val authorProfile = profilesByUid[livePost.userId]
                                            ?: exploreItem.resolvedAuthor
                                        InstagramFeedPostCard(
                                            post = livePost,
                                            authorProfile = authorProfile,
                                            currentUserId = currentUserId,
                                            isFollowing = isFollowingUid(livePost.userId),
                                            onToggleFollow = {
                                                val target = authorProfile ?: PublicProfile(
                                                    userId = livePost.userId,
                                                    displayName = livePost.authorName
                                                )
                                                onToggleFollow(target)
                                            },
                                            resolveVideoFile = resolveFeedPostVideoFile,
                                            onToggleLike = {
                                                onToggleFeedPostLike(
                                                    livePost.id,
                                                    currentUserId in livePost.likedByUids
                                                )
                                            },
                                            onToggleSave = {
                                                onToggleFeedPostSave(
                                                    livePost.id,
                                                    currentUserId in livePost.savedByUids
                                                )
                                            },
                                            onOpenComments = { activeCommentsPost = livePost },
                                            onDeletePost = {
                                                onDeleteFeedPost(livePost.id)
                                            },
                                            onOpenAuthor = {
                                                selectedExploreIndex = null
                                                onOpenUserProfile(livePost.userId)
                                            },
                                            isVisibleForAutoPlay = autoPlayKey == exploreItem.key
                                        )
                                    }

                                    is ExplorePostItem.ReelEntry -> {
                                        val liveReel = videoPosts.firstOrNull { it.id == exploreItem.videoPost.id }
                                            ?: exploreItem.videoPost
                                        val authorProfile = profilesByUid[liveReel.userId]
                                            ?: exploreItem.resolvedAuthor
                                        ExploreReelFeedCard(
                                            videoPost = liveReel,
                                            authorProfile = authorProfile,
                                            currentUserId = currentUserId,
                                            isFollowing = isFollowingUid(liveReel.userId),
                                            resolveVideoFile = resolveVideoPostFile,
                                            isVisibleForAutoPlay = autoPlayKey == exploreItem.key,
                                            onToggleLike = {
                                                onToggleVideoPostLike(
                                                    liveReel.id,
                                                    currentUserId in liveReel.likedByUids
                                                )
                                            },
                                            onToggleFollow = {
                                                val target = authorProfile ?: PublicProfile(
                                                    userId = liveReel.userId,
                                                    displayName = liveReel.authorName
                                                )
                                                onToggleFollow(target)
                                            },
                                            onOpenAuthor = {
                                                selectedExploreIndex = null
                                                onOpenUserProfile(liveReel.userId)
                                            },
                                            onOpenComments = {
                                                activeCommentsPost = FeedPost(
                                                    id = liveReel.id,
                                                    userId = liveReel.userId,
                                                    authorName = liveReel.authorName,
                                                    authorPhoto = liveReel.authorPhoto,
                                                    authorCountry = liveReel.authorCountry,
                                                    caption = liveReel.caption,
                                                    mediaType = "VIDEO",
                                                    mediaDataUrl = liveReel.mediaDataUrl,
                                                    thumbnailDataUrl = liveReel.thumbnailDataUrl,
                                                    likedByUids = liveReel.likedByUids,
                                                    createdAt = liveReel.createdAt
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    activeCommentsPost?.let { postForComments ->
        val isVideoReel = videoPosts.any { it.id == postForComments.id }
        PostCommentsSheet(
            post = postForComments,
            currentUserId = currentUserId,
            myProfile = myProfile,
            profilesByUid = profilesByUid,
            observeComments = if (isVideoReel) observeVideoComments else observeFeedComments,
            onAddComment = if (isVideoReel) onAddVideoComment else onAddFeedPostComment,
            onDeleteComment = if (isVideoReel) onDeleteVideoComment else onDeleteFeedPostComment,
            onOpenProfile = { uid ->
                activeCommentsPost = null
                selectedExploreIndex = null
                onOpenUserProfile(uid)
            },
            onDismiss = { activeCommentsPost = null }
        )
    }
}

@Composable
private fun ExploreSearchUserRow(
    candidate: DiscoveryCandidate,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onClick: () -> Unit
) {
    val profile = candidate.profile
    val flag = CountryFlagHelper.flagEmojiForCountry(profile.country)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .testTag("explore_user_row_${profile.userId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProfileAvatarImage(
            photoDataUrl = profile.photoDataUrl,
            displayName = profile.displayName,
            seed = profile.avatarSeed,
            modifier = Modifier.size(50.dp),
            shape = CircleShape,
            initialsFontSizeSp = 18
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.displayName.ifBlank { "Kindred Member" },
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val locationLine = listOfNotNull(
                profile.cityLabel.takeIf { it.isNotBlank() },
                profile.country.takeIf { it.isNotBlank() }?.let { c ->
                    if (flag.isNotBlank()) "$flag $c" else c
                }
            ).joinToString(", ")
            if (locationLine.isNotBlank()) {
                Text(
                    text = locationLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TextButton(
            onClick = onToggleFollow,
            modifier = Modifier.testTag("explore_user_follow_${profile.userId}")
        ) {
            Text(
                text = if (isFollowing) "Following" else "Follow",
                color = if (isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else ExploreInstagramBlue,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ExplorePostGridTile(
    item: ExplorePostItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val url = item.imageUrl
    val bitmap = remember(url) {
        if (!url.isNullOrBlank() && url.startsWith("data:image")) {
            MediaImageHelper.decodeBase64DataUrl(url)
        } else null
    }

    Box(
        modifier = modifier
            .aspectRatio(0.84f)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .testTag("explore_grid_tile_${item.key}")
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Post by ${item.authorName}",
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = "Post by ${item.authorName}",
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (item.isVideo) {
            Icon(
                imageVector = Icons.Filled.Movie,
                contentDescription = "Reel",
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(20.dp)
            )
        } else if (item.isCarousel) {
            Icon(
                imageVector = Icons.Filled.FilterNone,
                contentDescription = "Multiple photos",
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(18.dp)
            )
        }
    }
}

@Composable
private fun ExploreReelFeedCard(
    videoPost: VideoPost,
    authorProfile: PublicProfile?,
    currentUserId: String,
    isFollowing: Boolean,
    resolveVideoFile: suspend (VideoPost) -> Result<File>,
    isVisibleForAutoPlay: Boolean,
    onToggleLike: () -> Unit,
    onToggleFollow: () -> Unit,
    onOpenAuthor: () -> Unit,
    onOpenComments: () -> Unit
) {
    val displayAuthorName = authorProfile?.displayName?.takeIf { it.isNotBlank() }
        ?: videoPost.authorName.ifBlank { "Kindred Member" }
    val displayAuthorPhoto = authorProfile?.allProfilePhotos()?.firstOrNull() ?: videoPost.authorPhoto
    val isLiked = currentUserId in videoPost.likedByUids
    val isOwner = videoPost.userId == currentUserId

    var resolvedFile by remember(videoPost.id) { mutableStateOf<File?>(null) }
    var isMuted by remember(videoPost.id) { mutableStateOf(false) }
    var userPaused by remember(videoPost.id) { mutableStateOf(false) }
    var videoViewRef by remember(videoPost.id) { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember(videoPost.id) { mutableStateOf<android.media.MediaPlayer?>(null) }

    LaunchedEffect(videoPost.id) {
        resolveVideoFile(videoPost).onSuccess { resolvedFile = it }
    }

    LaunchedEffect(isVisibleForAutoPlay, resolvedFile, userPaused) {
        val vv = videoViewRef ?: return@LaunchedEffect
        if (isVisibleForAutoPlay && resolvedFile != null && !userPaused) {
            if (!vv.isPlaying) vv.start()
        } else {
            if (vv.isPlaying) vv.pause()
        }
    }

    LaunchedEffect(isMuted) {
        runCatching {
            val vol = if (isMuted) 0f else 1f
            mediaPlayerRef?.setVolume(vol, vol)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Post Header with Author Avatar, Name, Verified Badge & Follow button -> clicking opens user's profile
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenAuthor),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileAvatarImage(
                    photoDataUrl = displayAuthorPhoto,
                    displayName = displayAuthorName,
                    seed = videoPost.userId,
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    initialsFontSizeSp = 14
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayAuthorName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Original audio • Reel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            if (!isOwner) {
                TextButton(onClick = onToggleFollow) {
                    Text(
                        text = if (isFollowing) "Following" else "Follow",
                        color = if (isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else ExploreInstagramBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Video Player Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .background(Color.Black)
                .clickable { userPaused = !userPaused }
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
                                if (isVisibleForAutoPlay && !userPaused) {
                                    start()
                                }
                            }
                            videoViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val thumb = remember(videoPost.thumbnailDataUrl) {
                    videoPost.thumbnailDataUrl?.let { MediaImageHelper.decodeBase64DataUrl(it) }
                }
                val isRemoteThumb = videoPost.thumbnailDataUrl?.startsWith("http://", ignoreCase = true) == true ||
                    videoPost.thumbnailDataUrl?.startsWith("https://", ignoreCase = true) == true
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = videoPost.caption,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (isRemoteThumb) {
                    AsyncImage(
                        model = videoPost.thumbnailDataUrl,
                        contentDescription = videoPost.caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.Center)
                )
            }

            if (userPaused) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .size(54.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = { isMuted = !isMuted },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Likes & Caption with clickable Creator Name
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleLike) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) CoralRose else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onOpenComments) {
                Icon(
                    imageVector = Icons.Filled.ChatBubble,
                    contentDescription = "Comments",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (videoPost.likedByUids.isNotEmpty()) {
                Text(
                    text = "${videoPost.likedByUids.size} likes",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (videoPost.caption.isNotBlank()) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(displayAuthorName)
                        append(" ")
                    }
                    append(videoPost.caption)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clickable(onClick = onOpenAuthor)
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }
}
