package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.model.FeedPost
import com.example.model.ProfileVideoItem
import com.example.model.PublicProfile
import com.example.model.StoryHighlight
import com.example.model.UserPresence
import com.example.model.VideoPost
import com.example.model.VisibilityLevel
import com.example.ui.components.HighlightViewerDialog
import com.example.ui.components.PartnerPhotoGalleryDialog
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.components.ReportUserDialog
import com.example.ui.theme.CoralRose
import com.example.util.CountryFlagHelper
import com.example.util.PresenceUtils
import com.example.util.ProfileCompletionUtils
import com.example.util.MediaImageHelper
import com.example.util.VideoClipHelper
import java.io.File

private val KindredActionBlue = Color(0xFF0095F6)
private val StoryRingColors = listOf(
    Color(0xFFFBAA47),
    Color(0xFFD91A46),
    Color(0xFFA60F93),
    Color(0xFFFBAA47)
)

private sealed interface UserProfileGridItem {
    val id: String
    val thumbnailUrl: String?
    val isVideo: Boolean

    data class PhotoItem(val photoUrl: String, val index: Int) : UserProfileGridItem {
        override val id: String = "photo_$index"
        override val thumbnailUrl: String = photoUrl
        override val isVideo: Boolean = false
    }

    data class VideoItem(val video: ProfileVideoItem) : UserProfileGridItem {
        override val id: String = "pvid_${video.id}"
        override val thumbnailUrl: String? = video.thumbnailDataUrl
        override val isVideo: Boolean = true
    }

    data class ReelItem(val reel: VideoPost) : UserProfileGridItem {
        override val id: String = "reel_${reel.id}"
        override val thumbnailUrl: String? = reel.thumbnailDataUrl
        override val isVideo: Boolean = true
    }

    data class PostItem(val post: FeedPost) : UserProfileGridItem {
        override val id: String = "post_${post.id}"
        override val thumbnailUrl: String? = post.thumbnailDataUrl ?: post.mediaDataUrl ?: post.mediaUrls.firstOrNull()
        override val isVideo: Boolean = post.mediaType == "VIDEO"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileDetailScreen(
    profile: PublicProfile,
    currentUserId: String,
    videos: List<ProfileVideoItem> = emptyList(),
    followerIds: Set<String> = emptySet(),
    followingIds: Set<String> = emptySet(),
    mutualFollowerProfiles: List<PublicProfile> = emptyList(),
    isFollowing: Boolean = false,
    presence: UserPresence? = null,
    distanceKm: Double? = null,
    isLiked: Boolean = false,
    isFavorited: Boolean = false,
    isBlocked: Boolean = false,
    resolveVideoFile: (suspend (ProfileVideoItem) -> Result<File>)? = null,
    onBack: () -> Unit,
    onRecordProfileView: () -> Unit = {},
    onOpenMutualProfile: (PublicProfile) -> Unit = {},
    onOpenFollowersFollowing: (Int) -> Unit = {},
    onMessageClick: (PublicProfile) -> Unit,
    onToggleFollow: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onReportUser: (reason: String, details: String, alsoBlock: Boolean) -> Unit,
    onBlockUser: () -> Unit,
    onUnblockUser: () -> Unit = {},
    highlights: List<StoryHighlight> = emptyList(),
    userPosts: List<FeedPost> = emptyList(),
    userReels: List<VideoPost> = emptyList(),
    onStatusMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isOwner = profile.userId == currentUserId
    val canSeePhotos = ProfileCompletionUtils.canViewerSeeSection(profile.photosVisibility, isOwner, isFollowing)
    val canSeeVideos = ProfileCompletionUtils.canViewerSeeSection(profile.videosVisibility, isOwner, isFollowing)
    val canSeeInfo = ProfileCompletionUtils.canViewerSeeSection(profile.infoVisibility, isOwner, isFollowing)

    val cleanHandle = remember(profile.displayName) {
        val sanitized = profile.displayName.lowercase()
            .replace(" ", "_")
            .filter { it.isLetterOrDigit() || it == '_' }
            .take(24)
        sanitized.ifBlank { "member" }
    }

    val photos = remember(profile.userId, profile.photoDataUrl, profile.profilePhotos, canSeePhotos) {
        if (canSeePhotos) profile.allProfilePhotos() else emptyList()
    }
    val visibleVideos = remember(videos, canSeeVideos) {
        if (canSeeVideos) videos else emptyList()
    }
    val galleryPhotos = remember(photos, userPosts, canSeePhotos) {
        if (canSeePhotos) {
            (photos + userPosts.flatMap { it.allPhotoUrls() }).distinct()
        } else {
            emptyList()
        }
    }

    val flag = profile.flagEmoji()
    val presenceDisplay = remember(presence, profile.hideOnlineStatus) {
        if (profile.hideOnlineStatus || presence?.hideOnlineStatus == true) null
        else PresenceUtils.resolvePresenceDisplay(presence)
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var galleryInitialIndex by remember(profile.userId) { mutableStateOf<Int?>(null) }
    var activeHighlightViewer by remember { mutableStateOf<StoryHighlight?>(null) }

    // Bottom Sheets & Dialogs for 3-dot menu matching Screenshot 2
    var showOverflowSheet by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showAboutAccountSheet by remember { mutableStateOf(false) }
    var showQrCodeDialog by remember { mutableStateOf(false) }
    var isRestricted by remember { mutableStateOf(false) }
    var isStoryHidden by remember { mutableStateOf(false) }

    val overflowSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val aboutSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(profile.userId) {
        if (!isOwner) onRecordProfileView()
    }

    val displayHighlights = remember(highlights) { highlights }

    // Grid items for Tab 0 (All Posts / Photos)
    val gridItems = remember(photos, userPosts, visibleVideos) {
        if (userPosts.isNotEmpty()) {
            userPosts.map { UserProfileGridItem.PostItem(it) }
        } else {
            buildList<UserProfileGridItem> {
                photos.forEachIndexed { idx, p -> add(UserProfileGridItem.PhotoItem(p, idx)) }
                visibleVideos.forEach { v -> add(UserProfileGridItem.VideoItem(v)) }
            }
        }
    }

    // Grid items for Tab 1 (Reels)
    val reelItems = remember(userReels, visibleVideos) {
        if (userReels.isNotEmpty()) {
            userReels.map { UserProfileGridItem.ReelItem(it) }
        } else {
            visibleVideos.map { UserProfileGridItem.VideoItem(it) }
        }
    }

    val totalPostsCount = remember(gridItems, reelItems) {
        (gridItems.size + reelItems.size).coerceAtLeast(photos.size + visibleVideos.size)
    }

    val followersDisplay = remember(followerIds) {
        val count = followerIds.size
        when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    val followingDisplay = remember(followingIds) {
        val count = followingIds.size
        when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = cleanHandle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("user_detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // 3-dot menu button in top right corner (Screenshot 1 & 2)
                    IconButton(
                        onClick = { showOverflowSheet = true },
                        modifier = Modifier.testTag("user_detail_more_options_button")
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .testTag("user_profile_detail_screen"),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Profile Header Row (Avatar + 3 Stat Columns)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Avatar with colorful Instagram Story Ring
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .border(
                                    BorderStroke(2.5.dp, Brush.sweepGradient(StoryRingColors)),
                                    CircleShape
                                )
                                .padding(4.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (photos.isNotEmpty() || visibleVideos.isNotEmpty()) {
                                        galleryInitialIndex = 0
                                    }
                                }
                                .testTag("user_detail_avatar_story_ring")
                        ) {
                            ProfileAvatarImage(
                                photoDataUrl = profile.photoDataUrl,
                                displayName = profile.displayName,
                                seed = profile.avatarSeed,
                                modifier = Modifier.fillMaxSize(),
                                shape = CircleShape,
                                initialsFontSizeSp = 28
                            )
                        }

                        // 3 Stat Columns: Posts, Followers, Following
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProfileStatColumn(count = totalPostsCount.toString(), label = "Posts")
                            ProfileStatColumn(
                                count = followersDisplay,
                                label = "Followers",
                                onClick = { onOpenFollowersFollowing(0) }
                            )
                            ProfileStatColumn(
                                count = followingDisplay,
                                label = "Following",
                                onClick = { onOpenFollowersFollowing(1) }
                            )
                        }
                    }

                    // Display Name, Category, Bio, Link
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        // Category / Subtitle from profile metadata
                        val profileSubtitle = remember(profile.interests, profile.cityLabel, profile.country) {
                            profile.interests.take(2).joinToString(" • ").ifBlank { profile.formattedLocation() }
                        }
                        if (canSeeInfo && profileSubtitle.isNotBlank()) {
                            Text(
                                text = profileSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Bio
                        if (canSeeInfo && profile.bio.isNotBlank()) {
                            Text(
                                text = profile.bio,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // Website / Link
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = ClipData.newPlainText("Kindred Link", "https://kindred.app/u/$cleanHandle")
                                    clipboard?.setPrimaryClip(clip)
                                    onStatusMessage("Link copied to clipboard")
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = null,
                                tint = KindredActionBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "kindred.app/$cleanHandle",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = KindredActionBlue
                            )
                        }

                        // Mutual Followers Row
                        if (mutualFollowerProfiles.isNotEmpty()) {
                            val firstFollower = mutualFollowerProfiles.first()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clickable { onOpenMutualProfile(firstFollower) }
                                    .padding(top = 4.dp)
                            ) {
                                ProfileAvatarImage(
                                    photoDataUrl = firstFollower.photoDataUrl,
                                    displayName = firstFollower.displayName,
                                    seed = firstFollower.avatarSeed,
                                    modifier = Modifier.size(16.dp),
                                    shape = CircleShape,
                                    initialsFontSizeSp = 8
                                )
                                Text(
                                    text = buildString {
                                        append("Followed by ")
                                        append(firstFollower.displayName)
                                        if (mutualFollowerProfiles.size > 1) {
                                            append(" +${mutualFollowerProfiles.size - 1} others")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Action Buttons Row: Follow, Message, Suggestion
                    if (!isOwner) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Primary Follow Button
                            val followBtnColor = if (isFollowing) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                KindredActionBlue
                            }
                            val followTextColor = if (isFollowing) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                Color.White
                            }
                            val followBtnLabel = if (isFollowing) "Following" else "Follow"

                            Button(
                                onClick = onToggleFollow,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = followBtnColor,
                                    contentColor = followTextColor
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .testTag("user_detail_friend_button")
                            ) {
                                Text(
                                    text = followBtnLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Secondary Message Button
                            Button(
                                onClick = { onMessageClick(profile) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .testTag("user_detail_message_button")
                            ) {
                                Text(
                                    text = "Message",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Tertiary Suggestion Button
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenFollowersFollowing(2) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.PersonAdd,
                                        contentDescription = "Suggested profiles",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Story Highlights Tray (Screenshot 1)
            if (displayHighlights.isNotEmpty()) {
                item {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(
                            items = displayHighlights,
                            key = { idx, hl -> "ud_hl_${idx}_${hl.id}" }
                        ) { _, hl ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clickable { activeHighlightViewer = hl }
                                    .testTag("user_detail_highlight_${hl.id}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .border(
                                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                            CircleShape
                                        )
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    val bmp = remember(hl.coverUrl) {
                                        if (hl.coverUrl.startsWith("data:image", ignoreCase = true)) {
                                            MediaImageHelper.decodeBase64DataUrl(hl.coverUrl)
                                        } else null
                                    }
                                    val isRemoteHttpCover = hl.coverUrl.startsWith("http://", ignoreCase = true) ||
                                        hl.coverUrl.startsWith("https://", ignoreCase = true)
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = hl.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (isRemoteHttpCover) {
                                        AsyncImage(
                                            model = hl.coverUrl,
                                            contentDescription = hl.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        ProfileAvatarImage(
                                            photoDataUrl = profile.photoDataUrl,
                                            displayName = hl.title,
                                            seed = hl.id,
                                            modifier = Modifier.fillMaxSize(),
                                            shape = CircleShape,
                                            initialsFontSizeSp = 18
                                        )
                                    }
                                }
                                Text(
                                    text = hl.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.widthIn(max = 68.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Tabs: Posts (Grid), Reels, Tagged (Screenshot 1)
            item {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.onBackground,
                            height = 1.5.dp
                        )
                    },
                    divider = {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            thickness = 0.5.dp
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTabIndex == 0) Icons.Filled.GridOn else Icons.Outlined.GridOn,
                                contentDescription = "Posts",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTabIndex == 1) Icons.Filled.Movie else Icons.Outlined.Movie,
                                contentDescription = "Reels",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTabIndex == 2) Icons.Filled.AccountBox else Icons.Outlined.AccountBox,
                                contentDescription = "Tagged",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                }
            }

            // 3-Column Grid content
            val currentItems = when (selectedTabIndex) {
                0 -> gridItems
                1 -> reelItems
                else -> gridItems.filter { !it.isVideo }
            }

            if (!canSeePhotos && selectedTabIndex == 0) {
                item {
                    Box(modifier = Modifier.padding(24.dp)) {
                        PrivateSectionNotice(level = profile.photosVisibility, label = "Photos")
                    }
                }
            } else if (!canSeeVideos && selectedTabIndex == 1) {
                item {
                    Box(modifier = Modifier.padding(24.dp)) {
                        PrivateSectionNotice(level = profile.videosVisibility, label = "Videos")
                    }
                }
            } else if (currentItems.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = when (selectedTabIndex) {
                                1 -> Icons.Outlined.Movie
                                2 -> Icons.Outlined.AccountBox
                                else -> Icons.Outlined.GridOn
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            text = when (selectedTabIndex) {
                                1 -> "No Reels Yet"
                                2 -> "Photos of ${profile.displayName}"
                                else -> "No Posts Yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            } else {
                val chunkedRows = currentItems.chunked(3)
                items(chunkedRows.size) { rIdx ->
                    val rowItems = chunkedRows[rIdx]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 1.5.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        for (colIdx in 0 until 3) {
                            if (colIdx < rowItems.size) {
                                val item = rowItems[colIdx]
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable {
                                            val totalMedia = galleryPhotos.size + visibleVideos.size
                                            if (totalMedia > 0) {
                                                val clickedIdx = when (item) {
                                                    is UserProfileGridItem.PhotoItem -> item.index
                                                    is UserProfileGridItem.VideoItem -> {
                                                        val vIdx = visibleVideos.indexOfFirst { it.id == item.video.id }.coerceAtLeast(0)
                                                        galleryPhotos.size + vIdx
                                                    }
                                                    is UserProfileGridItem.PostItem -> {
                                                        val firstUrl = item.post.allPhotoUrls().firstOrNull()
                                                        val pIdx = if (firstUrl != null) galleryPhotos.indexOf(firstUrl) else -1
                                                        if (pIdx >= 0) pIdx else (rIdx * 3 + colIdx)
                                                    }
                                                    is UserProfileGridItem.ReelItem -> rIdx * 3 + colIdx
                                                }
                                                galleryInitialIndex = clickedIdx.coerceIn(0, totalMedia - 1)
                                            }
                                        }
                                        .testTag("user_detail_grid_item_${item.id}")
                                ) {
                                    val thumb = remember(item.thumbnailUrl) {
                                        MediaImageHelper.decodeBase64DataUrl(item.thumbnailUrl)
                                    }
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (!item.thumbnailUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = item.thumbnailUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        ProfileAvatarImage(
                                            photoDataUrl = profile.photoDataUrl,
                                            displayName = profile.displayName,
                                            seed = item.id,
                                            modifier = Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(0.dp),
                                            initialsFontSizeSp = 18
                                        )
                                    }

                                    if (item.isVideo) {
                                        Icon(
                                            imageVector = Icons.Filled.Movie,
                                            contentDescription = "Video",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(16.dp)
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: 3-Dot Options matching Screenshot 2
    if (showOverflowSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOverflowSheet = false },
            sheetState = overflowSheetState,
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .testTag("user_profile_overflow_sheet")
            ) {
                // 1. Report...
                InstagramActionRow(
                    text = "Report...",
                    isDestructive = true,
                    onClick = {
                        showOverflowSheet = false
                        showReportDialog = true
                    }
                )

                // 2. Block
                InstagramActionRow(
                    text = "Block",
                    isDestructive = true,
                    onClick = {
                        showOverflowSheet = false
                        showBlockDialog = true
                    }
                )

                // 3. About this account
                InstagramActionRow(
                    text = "About this account",
                    onClick = {
                        showOverflowSheet = false
                        showAboutAccountSheet = true
                    }
                )

                // 4. Restrict
                InstagramActionRow(
                    text = if (isRestricted) "Unrestrict" else "Restrict",
                    onClick = {
                        showOverflowSheet = false
                        isRestricted = !isRestricted
                        onStatusMessage(if (isRestricted) "Account restricted." else "Account unrestricted.")
                    }
                )

                // 5. Hide your story
                InstagramActionRow(
                    text = if (isStoryHidden) "Unhide your story" else "Hide your story",
                    onClick = {
                        showOverflowSheet = false
                        isStoryHidden = !isStoryHidden
                        onStatusMessage(if (isStoryHidden) "Your stories are hidden from ${profile.displayName}." else "Stories visible to ${profile.displayName}.")
                    }
                )

                // 6. Copy profile URL
                InstagramActionRow(
                    text = "Copy profile URL",
                    onClick = {
                        showOverflowSheet = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("Kindred Profile", "https://kindred.app/u/$cleanHandle")
                        clipboard?.setPrimaryClip(clip)
                        onStatusMessage("Profile link copied to clipboard")
                    }
                )

                // 7. Show QR code
                InstagramActionRow(
                    text = "Show QR code",
                    onClick = {
                        showOverflowSheet = false
                        showQrCodeDialog = true
                    }
                )

                // 8. Share this profile
                InstagramActionRow(
                    text = "Share this profile",
                    onClick = {
                        showOverflowSheet = false
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Check out ${profile.displayName}'s profile on Kindred: https://kindred.app/u/$cleanHandle"
                            )
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share ${profile.displayName}'s profile"))
                    }
                )
            }
        }
    }

    // "About this account" Sheet matching Instagram
    if (showAboutAccountSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAboutAccountSheet = false },
            sheetState = aboutSheetState,
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProfileAvatarImage(
                    photoDataUrl = profile.photoDataUrl,
                    displayName = profile.displayName,
                    seed = profile.avatarSeed,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    initialsFontSizeSp = 24
                )
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                val joinedLabel = remember(profile.createdAt) {
                    profile.createdAt?.toDate()?.let { date ->
                        java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(date)
                    } ?: "Kindred Member"
                }
                AboutAccountDetailRow(
                    label = "Date joined",
                    value = joinedLabel,
                    icon = Icons.Filled.Info
                )
                AboutAccountDetailRow(
                    label = "Account based in",
                    value = profile.country.ifBlank { profile.cityLabel.ifBlank { "United States" } },
                    icon = Icons.Filled.AccountBox
                )
                AboutAccountDetailRow(
                    label = "Former usernames",
                    value = "0",
                    icon = Icons.Filled.ContentCopy
                )

                Button(
                    onClick = { showAboutAccountSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = KindredActionBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }

    // QR Code / Nametag Dialog matching Instagram
    if (showQrCodeDialog) {
        Dialog(onDismissRequest = { showQrCodeDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF181818),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "KINDRED NAMETAG",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 2.sp
                    )

                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .background(
                                Brush.linearGradient(StoryRingColors),
                                RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            modifier = Modifier
                                .size(160.dp)
                                .padding(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.QrCode,
                                    contentDescription = "QR Code",
                                    tint = Color.Black,
                                    modifier = Modifier.size(130.dp)
                                )
                                Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(40.dp)) {
                                    ProfileAvatarImage(
                                        photoDataUrl = profile.photoDataUrl,
                                        displayName = profile.displayName,
                                        seed = profile.avatarSeed,
                                        modifier = Modifier.fillMaxSize(),
                                        shape = CircleShape,
                                        initialsFontSizeSp = 14
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "@$cleanHandle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Button(
                        onClick = {
                            showQrCodeDialog = false
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Scan my Kindred profile: https://kindred.app/u/$cleanHandle")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share QR Nametag"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KindredActionBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Share Profile")
                    }
                }
            }
        }
    }

    // Photo Gallery Dialog when photo/video is tapped
    val startGalleryIdx = galleryInitialIndex
    if (startGalleryIdx != null && (galleryPhotos.isNotEmpty() || visibleVideos.isNotEmpty())) {
        PartnerPhotoGalleryDialog(
            photos = galleryPhotos,
            initialIndex = startGalleryIdx.coerceIn(0, (galleryPhotos.size + visibleVideos.size - 1).coerceAtLeast(0)),
            partnerName = profile.displayName,
            videos = visibleVideos,
            resolveVideoFile = resolveVideoFile,
            onDismiss = { galleryInitialIndex = null }
        )
    }

    // Report User Dialog
    if (showReportDialog) {
        ReportUserDialog(
            targetName = profile.displayName,
            onDismiss = { showReportDialog = false },
            onSubmitReport = { reason, details, alsoBlock ->
                showReportDialog = false
                onReportUser(reason, details, alsoBlock)
            }
        )
    }

    // Block User Dialog
    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text("Block ${profile.displayName}?") },
            text = { Text("They won't be able to find your profile, posts, or stories on Kindred. They won't be notified that you blocked them.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockDialog = false
                        onBlockUser()
                    },
                    modifier = Modifier.testTag("user_detail_confirm_block_button")
                ) {
                    Text("Block", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Highlights Viewer
    activeHighlightViewer?.let { highlight ->
        HighlightViewerDialog(
            highlight = highlight,
            isOwner = isOwner,
            onDismiss = { activeHighlightViewer = null }
        )
    }
}

@Composable
private fun ProfileStatColumn(
    count: String,
    label: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = if (onClick != null) {
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        } else {
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        }
    ) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun InstagramActionRow(
    text: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isDestructive) Color(0xFFED4956) else Color.White,
            fontWeight = if (isDestructive) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun AboutAccountDetailRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
        }
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PrivateSectionNotice(
    level: String,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = CoralRose, modifier = Modifier.size(16.dp))
            Text(
                text = "$label visible to ${VisibilityLevel.labelFor(level).lowercase()} only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
