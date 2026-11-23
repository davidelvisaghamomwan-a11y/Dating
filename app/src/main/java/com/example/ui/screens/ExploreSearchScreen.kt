package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.DiscoveryCandidate
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.FriendStatus
import com.example.model.PublicProfile
import com.example.ui.components.InstagramFeedPostCard
import com.example.ui.components.PostCommentsSheet
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.theme.EmeraldMint
import com.example.util.CountryFlagHelper
import com.example.util.VerificationAnalyzer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File

private val ExploreInstagramBlue = Color(0xFF0095F6)

private sealed interface ExploreMediaTile {
    val key: String
    val imageUrl: String?
    val isVideo: Boolean
    val isCarousel: Boolean
    val label: String
    val isVerified: Boolean

    data class UserPhotoTile(
        val candidate: DiscoveryCandidate,
        val photoIndex: Int,
        val photoUrl: String,
        val totalPhotos: Int
    ) : ExploreMediaTile {
        override val key: String = "user_${candidate.profile.userId}_$photoIndex"
        override val imageUrl: String = photoUrl
        override val isVideo: Boolean = false
        override val isCarousel: Boolean = totalPhotos > 1
        override val label: String = candidate.profile.displayName.ifBlank { "Member" }
        override val isVerified: Boolean = candidate.profile.isProfileVerified
    }

    data class PostTile(
        val post: FeedPost,
        val resolvedAuthor: PublicProfile?
    ) : ExploreMediaTile {
        private val photos = post.allPhotoUrls()
        override val key: String = "post_${post.id}"
        override val imageUrl: String? = if (post.mediaType == "VIDEO") {
            post.thumbnailDataUrl
        } else {
            photos.firstOrNull()
        }
        override val isVideo: Boolean = post.mediaType == "VIDEO"
        override val isCarousel: Boolean = !isVideo && photos.size > 1
        override val label: String = resolvedAuthor?.displayName?.takeIf { it.isNotBlank() }
            ?: post.authorName.ifBlank { "Kindred" }
        override val isVerified: Boolean = resolvedAuthor?.isProfileVerified ?: post.authorVerified
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreSearchScreen(
    currentUserId: String,
    myProfile: PublicProfile?,
    searchableProfiles: List<DiscoveryCandidate>,
    feedPosts: List<FeedPost>,
    profilesByUid: Map<String, PublicProfile>,
    friendStatusForUid: (String) -> FriendStatus,
    onFriendAction: (PublicProfile) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onMessageProfile: (PublicProfile) -> Unit,
    onToggleFeedPostLike: (postId: String, currentlyLiked: Boolean) -> Unit,
    onToggleFeedPostSave: (postId: String, currentlySaved: Boolean) -> Unit,
    observeFeedComments: (postId: String) -> Flow<List<FeedComment>> = { flowOf(emptyList()) },
    onAddFeedPostComment: (postId: String, text: String, onDone: () -> Unit) -> Unit = { _, _, _ -> },
    onDeleteFeedPostComment: (postId: String, commentId: String) -> Unit = { _, _ -> },
    onDeleteFeedPost: (postId: String) -> Unit = {},
    resolveFeedPostVideoFile: suspend (FeedPost) -> Result<File>
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedUserPhotoTile by remember { mutableStateOf<ExploreMediaTile.UserPhotoTile?>(null) }
    var selectedPost by remember { mutableStateOf<FeedPost?>(null) }
    var activeCommentsPost by remember { mutableStateOf<FeedPost?>(null) }

    val query = searchQuery.trim()

    val matchingUsers = remember(searchableProfiles, query, currentUserId) {
        searchableProfiles
            .filter { it.profile.userId != currentUserId }
            .filter { candidate ->
                if (query.isBlank()) {
                    true
                } else {
                    val p = candidate.profile
                    p.displayName.contains(query, ignoreCase = true) ||
                        p.cityLabel.contains(query, ignoreCase = true) ||
                        p.country.contains(query, ignoreCase = true) ||
                        p.bio.contains(query, ignoreCase = true)
                }
            }
            .sortedWith(
                compareByDescending<DiscoveryCandidate> {
                    query.isNotBlank() && it.profile.displayName.lowercase().startsWith(query.lowercase())
                }
                    .thenByDescending { it.profile.allProfilePhotos().isNotEmpty() }
                    .thenBy { it.distanceKm }
            )
    }

    // Build Instagram Explore grid combining real user profile pictures and visual feed posts
    val exploreTiles = remember(searchableProfiles, feedPosts, profilesByUid, query, currentUserId) {
        val list = mutableListOf<ExploreMediaTile>()
        val seenImages = mutableSetOf<String>()

        // 1. User pictures first (each discoverable user's primary & secondary pictures)
        searchableProfiles
            .asSequence()
            .filter { it.profile.userId != currentUserId }
            .filter { candidate ->
                query.isBlank() ||
                    candidate.profile.displayName.contains(query, ignoreCase = true) ||
                    candidate.profile.cityLabel.contains(query, ignoreCase = true) ||
                    candidate.profile.country.contains(query, ignoreCase = true)
            }
            .forEach { candidate ->
                val photos = candidate.profile.allProfilePhotos()
                if (photos.isNotEmpty()) {
                    val primary = photos.first()
                    if (seenImages.add(primary)) {
                        list += ExploreMediaTile.UserPhotoTile(
                            candidate = candidate,
                            photoIndex = 0,
                            photoUrl = primary,
                            totalPhotos = photos.size
                        )
                    }
                }
            }

        // 2. Media Feed Posts (photos & reels/videos)
        feedPosts
            .asSequence()
            .filter { post ->
                val hasMedia = post.mediaType == "VIDEO" || post.allPhotoUrls().isNotEmpty()
                hasMedia && (
                    query.isBlank() ||
                        post.authorName.contains(query, ignoreCase = true) ||
                        post.caption.contains(query, ignoreCase = true) ||
                        post.authorCity.orEmpty().contains(query, ignoreCase = true) ||
                        post.authorCountry.orEmpty().contains(query, ignoreCase = true)
                    )
            }
            .forEach { post ->
                val tile = ExploreMediaTile.PostTile(
                    post = post,
                    resolvedAuthor = profilesByUid[post.userId]
                )
                val thumb = tile.imageUrl
                if (thumb == null || seenImages.add(thumb)) {
                    list += tile
                }
            }

        // 3. Additional photos from user profiles so grid stays richly filled with user pictures
        searchableProfiles
            .asSequence()
            .filter { it.profile.userId != currentUserId }
            .filter { candidate ->
                query.isBlank() ||
                    candidate.profile.displayName.contains(query, ignoreCase = true) ||
                    candidate.profile.cityLabel.contains(query, ignoreCase = true) ||
                    candidate.profile.country.contains(query, ignoreCase = true)
            }
            .forEach { candidate ->
                val photos = candidate.profile.allProfilePhotos()
                photos.drop(1).take(2).forEachIndexed { idx, photo ->
                    if (seenImages.add(photo)) {
                        list += ExploreMediaTile.UserPhotoTile(
                            candidate = candidate,
                            photoIndex = idx + 1,
                            photoUrl = photo,
                            totalPhotos = photos.size
                        )
                    }
                }
            }

        list
    }

    LaunchedEffect(exploreTiles) {
        val urls = exploreTiles.take(24).mapNotNull { it.imageUrl }
        if (urls.isNotEmpty()) {
            VerificationAnalyzer.prefetchDataUrls(urls)
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
            // Instagram-Style Search Input Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // When searching by text, show instant matching user rows at top just like Instagram
                if (query.isNotBlank() && matchingUsers.isNotEmpty()) {
                    item(key = "matching_users_header") {
                        Text(
                            text = "People",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    items(
                        items = matchingUsers.take(15),
                        key = { "search_person_${it.profile.userId}" }
                    ) { candidate ->
                        ExploreSearchUserRow(
                            candidate = candidate,
                            onClick = { onOpenUserProfile(candidate.profile.userId) }
                        )
                    }
                    if (exploreTiles.isNotEmpty()) {
                        item(key = "matching_photos_divider") {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Text(
                                text = "Pictures & Posts",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (exploreTiles.isEmpty() && (query.isBlank() || matchingUsers.isEmpty())) {
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
                                text = if (query.isBlank()) "No user pictures yet" else "No results found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (query.isBlank()) {
                                    "Pictures and posts shared by Kindred members will appear here."
                                } else {
                                    "Try searching another name, city, or country."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val rows = exploreTiles.chunked(3)
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
                                            .aspectRatio(1f)
                                    )
                                } else {
                                    ExploreGridTile(
                                        tile = tile,
                                        onClick = {
                                            when (tile) {
                                                is ExploreMediaTile.UserPhotoTile -> {
                                                    selectedUserPhotoTile = tile
                                                }
                                                is ExploreMediaTile.PostTile -> {
                                                    selectedPost = tile.post
                                                }
                                            }
                                        },
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

    // User Picture Viewer Dialog (leads directly to the user's pictures & profile)
    selectedUserPhotoTile?.let { activeTile ->
        UserPictureLightboxDialog(
            candidate = activeTile.candidate,
            initialPhotoIndex = activeTile.photoIndex,
            friendStatus = friendStatusForUid(activeTile.candidate.profile.userId),
            onViewFullProfile = {
                selectedUserPhotoTile = null
                onOpenUserProfile(activeTile.candidate.profile.userId)
            },
            onMessage = {
                selectedUserPhotoTile = null
                onMessageProfile(activeTile.candidate.profile)
            },
            onFriendAction = {
                onFriendAction(activeTile.candidate.profile)
            },
            onDismiss = { selectedUserPhotoTile = null }
        )
    }

    // Feed Post Viewer Dialog (for post tiles tapped in Explore)
    selectedPost?.let { opened ->
        val livePost = feedPosts.firstOrNull { it.id == opened.id } ?: opened
        val authorProfile = profilesByUid[livePost.userId]
        Dialog(
            onDismissRequest = { selectedPost = null },
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
                            IconButton(onClick = { selectedPost = null }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item(key = livePost.id) {
                            InstagramFeedPostCard(
                                post = livePost,
                                authorProfile = authorProfile,
                                currentUserId = currentUserId,
                                friendStatus = friendStatusForUid(livePost.userId),
                                onFriendAction = {
                                    authorProfile?.let { onFriendAction(it) }
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
                                    selectedPost = null
                                },
                                onOpenAuthor = {
                                    selectedPost = null
                                    onOpenUserProfile(livePost.userId)
                                },
                                isVisibleForAutoPlay = true
                            )
                        }
                    }
                }
            }
        }
    }

    activeCommentsPost?.let { postForComments ->
        PostCommentsSheet(
            post = postForComments,
            currentUserId = currentUserId,
            myProfile = myProfile,
            profilesByUid = profilesByUid,
            observeComments = observeFeedComments,
            onAddComment = onAddFeedPostComment,
            onDeleteComment = onDeleteFeedPostComment,
            onOpenProfile = { uid ->
                activeCommentsPost = null
                selectedPost = null
                onOpenUserProfile(uid)
            },
            onDismiss = { activeCommentsPost = null }
        )
    }
}

@Composable
private fun ExploreSearchUserRow(
    candidate: DiscoveryCandidate,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = profile.displayName.ifBlank { "Kindred Member" },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (profile.isProfileVerified) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Verified",
                        tint = ExploreInstagramBlue,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
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
    }
}

@Composable
private fun ExploreGridTile(
    tile: ExploreMediaTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val url = tile.imageUrl
    val bitmap = remember(url) {
        if (!url.isNullOrBlank() && url.startsWith("data:image")) {
            VerificationAnalyzer.decodeBase64DataUrl(url)
        } else null
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .testTag("explore_grid_tile_${tile.key}")
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = tile.label,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = tile.label,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (tile.isVideo) {
            Icon(
                imageVector = Icons.Filled.Movie,
                contentDescription = "Reel",
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(20.dp)
            )
        } else if (tile.isCarousel) {
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

        // Subtle user name badge at bottom-left on user picture tiles
        if (tile is ExploreMediaTile.UserPhotoTile) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = tile.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (tile.isVerified) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Verified",
                            tint = ExploreInstagramBlue,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserPictureLightboxDialog(
    candidate: DiscoveryCandidate,
    initialPhotoIndex: Int,
    friendStatus: FriendStatus,
    onViewFullProfile: () -> Unit,
    onMessage: () -> Unit,
    onFriendAction: () -> Unit,
    onDismiss: () -> Unit
) {
    val profile = candidate.profile
    val photos = remember(profile) { profile.allProfilePhotos() }
    val pagerState = rememberPagerState(
        initialPage = initialPhotoIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)),
        pageCount = { photos.size.coerceAtLeast(1) }
    )
    val flag = CountryFlagHelper.flagEmojiForCountry(profile.country)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header: Back + User Avatar/Name/Location (tappable to view full profile)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onViewFullProfile)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProfileAvatarImage(
                            photoDataUrl = profile.photoDataUrl,
                            displayName = profile.displayName,
                            seed = profile.avatarSeed,
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            initialsFontSizeSp = 15
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = profile.displayName.ifBlank { "Kindred Member" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (profile.isProfileVerified) {
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = "Verified",
                                        tint = ExploreInstagramBlue,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            val loc = listOfNotNull(
                                profile.cityLabel.takeIf { it.isNotBlank() },
                                profile.country.takeIf { it.isNotBlank() }?.let { c ->
                                    if (flag.isNotBlank()) "$flag $c" else c
                                }
                            ).joinToString(", ")
                            if (loc.isNotBlank()) {
                                Text(
                                    text = loc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Full-width swipable User Picture Pager
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (photos.isNotEmpty()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val photo = photos[page]
                            val bmp = remember(photo) {
                                if (photo.startsWith("data:image")) {
                                    VerificationAnalyzer.decodeBase64DataUrl(photo)
                                } else null
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "${profile.displayName} photo ${page + 1}",
                                    contentScale = ContentScale.Fit,
                                    filterQuality = FilterQuality.High,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AsyncImage(
                                    model = photo,
                                    contentDescription = "${profile.displayName} photo ${page + 1}",
                                    contentScale = ContentScale.Fit,
                                    filterQuality = FilterQuality.High,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        if (photos.size > 1) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "${pagerState.currentPage + 1}/${photos.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Bio & Quick Action Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (profile.bio.isNotBlank()) {
                        Text(
                            text = profile.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onViewFullProfile,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ExploreInstagramBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("explore_view_profile_button")
                        ) {
                            Text("View Profile", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = onMessage,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("explore_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Message")
                        }
                        OutlinedButton(
                            onClick = onFriendAction,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("explore_friend_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = null,
                                tint = if (friendStatus == FriendStatus.FRIENDS) EmeraldMint else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
