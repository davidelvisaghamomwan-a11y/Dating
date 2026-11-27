package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.auth.GoogleAuthHelper
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.ProfileVideoItem
import com.example.model.PublicProfile
import com.example.model.StoryHighlight
import com.example.model.StoryItem
import com.example.ui.components.CreateHighlightDialog
import com.example.ui.components.HighlightViewerDialog
import com.example.ui.components.InstagramFeedPostCard
import com.example.ui.components.PostCommentsSheet
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.components.StoryHighlightsSection
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.MediaImageHelper
import com.example.util.VideoClipHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File

private val KindredActionBlue = Color(0xFF0095F6)

private sealed interface ProfileGridEntry {
    val sortTimestamp: Long
    val key: String

    data class PostEntry(val post: FeedPost) : ProfileGridEntry {
        override val sortTimestamp: Long = post.createdAt?.toDate()?.time ?: 0L
        override val key: String = "post_${post.id}"
    }

    data class ShowcaseVideoEntry(val video: ProfileVideoItem) : ProfileGridEntry {
        override val sortTimestamp: Long = video.createdAt?.toDate()?.time ?: 0L
        override val key: String = "video_${video.id}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    currentUserId: String,
    userEmail: String?,
    defaultDisplayName: String?,
    myProfile: PublicProfile?,
    myPosts: List<FeedPost> = emptyList(),
    savedPosts: List<FeedPost> = emptyList(),
    profilesByUid: Map<String, PublicProfile> = emptyMap(),
    myVideos: List<ProfileVideoItem> = emptyList(),
    highlights: List<StoryHighlight> = emptyList(),
    archivedStories: List<StoryItem> = emptyList(),
    followersCount: Int = 0,
    followingCount: Int = 0,
    profileViewCount: Int = 0,
    activityBadgeCount: Int = 0,
    isAdmin: Boolean = false,
    isFollowingUid: (String) -> Boolean = { false },
    onToggleFollow: (PublicProfile) -> Unit = {},
    onEditProfile: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenFollowersFollowing: (Int) -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenAdminDashboard: () -> Unit = {},
    onOpenVideoFeed: () -> Unit = {},
    onOpenCreatePost: () -> Unit = {},
    onOpenCreateStory: () -> Unit = {},
    onOpenUserProfile: (String) -> Unit = {},
    onToggleFeedPostLike: (postId: String, currentlyLiked: Boolean) -> Unit = { _, _ -> },
    onToggleFeedPostSave: (postId: String, currentlySaved: Boolean) -> Unit = { _, _ -> },
    observeFeedComments: (postId: String) -> Flow<List<FeedComment>> = { flowOf(emptyList()) },
    onAddFeedPostComment: (postId: String, text: String, onDone: () -> Unit) -> Unit = { _, _, _ -> },
    onDeleteFeedPostComment: (postId: String, commentId: String) -> Unit = { _, _ -> },
    onDeleteFeedPost: (postId: String) -> Unit = {},
    resolveFeedPostVideoFile: suspend (FeedPost) -> Result<File> = { Result.failure(IllegalStateException("Unavailable")) },
    resolveProfileVideoFile: suspend (ProfileVideoItem) -> Result<File> = { Result.failure(IllegalStateException("Unavailable")) },
    onDeleteVideo: (String) -> Unit = {},
    onCreateHighlight: (title: String, coverUrl: String, items: List<StoryItem>, onComplete: (Boolean) -> Unit) -> Unit = { _, _, _, cb -> cb(true) },
    onCreateHighlightWithResult: ((title: String, coverUrl: String, items: List<StoryItem>, onComplete: (Boolean, String?) -> Unit) -> Unit)? = null,
    uploadProgress: Float? = null,
    onDeleteHighlight: (String) -> Unit = {},
    onBeforeSignOut: suspend () -> Unit = {},
    onStatusMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val displayName = remember(myProfile, defaultDisplayName) {
        myProfile?.displayName?.takeIf { it.isNotBlank() }
            ?: defaultDisplayName?.takeIf { it.isNotBlank() }
            ?: "Kindred Member"
    }
    val handle = remember(myProfile, userEmail, displayName) {
        val emailPrefix = userEmail
            ?.substringBefore("@")
            ?.trim()
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9._]"), "")
            ?.takeIf { it.length >= 2 }
        emailPrefix ?: displayName
            .lowercase()
            .replace(Regex("[^a-z0-9._]"), "")
            .ifBlank { "kindred.member" }
    }
    val primaryPhoto = remember(myProfile) { myProfile?.allProfilePhotos()?.firstOrNull() }
    val avatarSeed = myProfile?.avatarSeed ?: currentUserId
    val bio = myProfile?.bio.orEmpty().trim()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Posts, 1 = Reels, 2 = Saved
    var showAccountQuickMenu by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var activeHighlightViewer by remember { mutableStateOf<StoryHighlight?>(null) }
    var showCreateHighlightDialog by remember { mutableStateOf(false) }
    var selectedFeedPost by remember { mutableStateOf<FeedPost?>(null) }
    var activeCommentsPost by remember { mutableStateOf<FeedPost?>(null) }
    var selectedShowcaseVideo by remember { mutableStateOf<ProfileVideoItem?>(null) }

    LaunchedEffect(myPosts, savedPosts, selectedFeedPost?.id) {
        val opened = selectedFeedPost ?: return@LaunchedEffect
        val openedId = opened.id.ifBlank { opened.postId }
        if ((myPosts + savedPosts).none { it.id.ifBlank { it.postId } == openedId }) {
            selectedFeedPost = null
            activeCommentsPost = null
        }
    }

    LaunchedEffect(myVideos, selectedShowcaseVideo?.id) {
        val openedId = selectedShowcaseVideo?.id ?: return@LaunchedEffect
        if (myVideos.none { it.id == openedId }) {
            selectedShowcaseVideo = null
        }
    }

    val allGridEntries = remember(myPosts, myVideos) {
        (myPosts.map { ProfileGridEntry.PostEntry(it) } +
            myVideos.map { ProfileGridEntry.ShowcaseVideoEntry(it) })
            .sortedByDescending { it.sortTimestamp }
    }
    val reelEntries = remember(myPosts, myVideos) {
        (myPosts.filter { it.mediaType == "VIDEO" }.map { ProfileGridEntry.PostEntry(it) } +
            myVideos.map { ProfileGridEntry.ShowcaseVideoEntry(it) })
            .sortedByDescending { it.sortTimestamp }
    }
    val savedEntries = remember(savedPosts) {
        savedPosts.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            .map { ProfileGridEntry.PostEntry(it) }
    }

    val activeEntries = when (selectedTab) {
        1 -> reelEntries
        2 -> savedEntries
        else -> allGridEntries
    }

    val postsCount = allGridEntries.size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("profile_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 1. Instagram Top Profile Header Bar: handle v   [+]  [=]
            item(key = "ig_profile_top_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAccountQuickMenu = true }
                                .padding(vertical = 8.dp, horizontal = 2.dp)
                                .testTag("profile_handle_menu_trigger")
                        ) {
                            Text(
                                text = handle,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Account menu",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showAccountQuickMenu,
                            onDismissRequest = { showAccountQuickMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Followers & Following (${followersCount + followingCount})") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Group, contentDescription = null)
                                },
                                onClick = {
                                    showAccountQuickMenu = false
                                    onOpenFollowersFollowing(0)
                                }
                            )
                            if (isAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Admin Moderation") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                                    },
                                    onClick = {
                                        showAccountQuickMenu = false
                                        onOpenAdminDashboard()
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Sign out",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showAccountQuickMenu = false
                                    GoogleAuthHelper.signOut(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onBeforeSignOut = onBeforeSignOut
                                    )
                                }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showCreateSheet = true },
                            modifier = Modifier.testTag("profile_create_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddBox,
                                contentDescription = "Create post, story, reel, or highlight",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("profile_settings_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = "Settings and activity",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(27.dp)
                                )
                            }
                            if (activityBadgeCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-9).dp, y = 11.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF3040))
                                )
                            }
                        }
                    }
                }
            }

            // 2. Avatar + Posts / Followers / Following Counts Row
            item(key = "ig_profile_header_stats") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    ProfileAvatarImage(
                        photoDataUrl = primaryPhoto,
                        displayName = displayName,
                        seed = avatarSeed,
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onEditProfile)
                            .testTag("profile_avatar_image"),
                        shape = CircleShape,
                        initialsFontSizeSp = 30
                    )

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileStatColumn(
                            count = postsCount,
                            label = "Posts",
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.testTag("profile_stat_posts")
                        )
                        ProfileStatColumn(
                            count = followersCount,
                            label = "Followers",
                            onClick = { onOpenFollowersFollowing(0) },
                            modifier = Modifier.testTag("profile_stat_followers")
                        )
                        ProfileStatColumn(
                            count = followingCount,
                            label = "Following",
                            onClick = { onOpenFollowersFollowing(1) },
                            modifier = Modifier.testTag("profile_stat_following")
                        )
                    }
                }
            }

            // 3. Display Name + Verified Badge + Bio
            item(key = "ig_profile_bio") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (bio.isNotBlank()) {
                        Text(
                            text = bio,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 19.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("profile_bio_text")
                        )
                    } else {
                        Text(
                            text = "Tap Edit profile to add your bio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable(onClick = onEditProfile)
                                .testTag("profile_bio_placeholder")
                        )
                    }
                }
            }

            // 4. Compact Professional Dashboard / Insights Banner
            item(key = "ig_profile_insights") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenActivity)
                        .testTag("profile_insights_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Professional dashboard",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = EmeraldMint,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "$profileViewCount profile views in the last 30 days.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 5. Two Side-by-Side Tonal Action Buttons: [Edit profile] [Share profile]
            item(key = "ig_profile_action_buttons") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InstagramProfileActionButton(
                        label = "Edit profile",
                        onClick = onEditProfile,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_profile_button")
                    )
                    InstagramProfileActionButton(
                        label = "Share profile",
                        onClick = {
                            val shareText = buildString {
                                append("Connect with $displayName (@$handle) on Kindred")
                                if (bio.isNotBlank()) {
                                    append("\n\"$bio\"")
                                }
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(shareIntent, "Share profile"))
                            }.onFailure {
                                onStatusMessage("Unable to open share menu")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_profile_button")
                    )
                }
            }

            // 6. Story Highlights Tray (existing highlights first, thin outlined + New button after)
            item(key = "ig_profile_highlights") {
                Spacer(modifier = Modifier.height(14.dp))
                StoryHighlightsSection(
                    highlights = highlights,
                    isOwner = true,
                    onOpenHighlight = { activeHighlightViewer = it },
                    onOpenCreateHighlight = { showCreateHighlightDialog = true },
                    onDeleteHighlight = onDeleteHighlight,
                    compactInstagramStyle = true
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 7. Three-Tab Selector Bar: Grid (Posts) | Reels | Saved
            item(key = "ig_profile_tabs") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileGridTabItem(
                            selected = selectedTab == 0,
                            icon = if (selectedTab == 0) Icons.Filled.GridOn else Icons.Outlined.GridOn,
                            contentDescription = "Posts",
                            onClick = { selectedTab = 0 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_tab_posts")
                        )
                        ProfileGridTabItem(
                            selected = selectedTab == 1,
                            icon = if (selectedTab == 1) Icons.Filled.Movie else Icons.Outlined.Movie,
                            contentDescription = "Reels",
                            onClick = { selectedTab = 1 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_tab_reels")
                        )
                        ProfileGridTabItem(
                            selected = selectedTab == 2,
                            icon = if (selectedTab == 2) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Saved",
                            onClick = { selectedTab = 2 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_tab_saved")
                        )
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // 8. 3-Column Full-Bleed Media Grid or Clean Empty State
            if (activeEntries.isEmpty()) {
                item(key = "ig_profile_empty_$selectedTab") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                            color = Color.Transparent,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (selectedTab) {
                                        1 -> Icons.Outlined.Movie
                                        2 -> Icons.Outlined.BookmarkBorder
                                        else -> Icons.Outlined.CameraAlt
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Text(
                            text = when (selectedTab) {
                                1 -> "No Reels Yet"
                                2 -> "No Saved Posts"
                                else -> "No Posts Yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (selectedTab) {
                                1 -> "Short videos you publish to Reels or your profile appear here."
                                2 -> "Bookmark posts from your timeline to revisit them anytime."
                                else -> "When you share photos and videos, they will appear on your profile."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (selectedTab != 2) {
                            TextButton(
                                onClick = {
                                    if (selectedTab == 1) onOpenVideoFeed() else onOpenCreatePost()
                                }
                            ) {
                                Text(
                                    text = if (selectedTab == 1) "Open Reels" else "Share your first post",
                                    color = KindredActionBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else {
                val rows = activeEntries.chunked(3)
                items(
                    count = rows.size,
                    key = { rowIdx -> "grid_row_${selectedTab}_$rowIdx" }
                ) { rowIdx ->
                    val rowItems = rows[rowIdx]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 1.5.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        for (col in 0 until 3) {
                            val entry = rowItems.getOrNull(col)
                            if (entry == null) {
                                Spacer(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(if (selectedTab == 1) 0.72f else 1f)
                                )
                            } else {
                                ProfileGridTile(
                                    entry = entry,
                                    aspectRatio = if (selectedTab == 1) 0.72f else 1f,
                                    onClick = {
                                        when (entry) {
                                            is ProfileGridEntry.PostEntry -> selectedFeedPost = entry.post
                                            is ProfileGridEntry.ShowcaseVideoEntry -> selectedShowcaseVideo = entry.video
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

    // Create (+) Bottom Sheet matching Instagram Create Drawer
    if (showCreateSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = "Create",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                CreateMenuOptionRow(
                    icon = Icons.Outlined.GridOn,
                    label = "Post",
                    onClick = {
                        showCreateSheet = false
                        onOpenCreatePost()
                    }
                )
                CreateMenuOptionRow(
                    icon = Icons.Outlined.Movie,
                    label = "Reel",
                    onClick = {
                        showCreateSheet = false
                        onOpenVideoFeed()
                    }
                )
                CreateMenuOptionRow(
                    icon = Icons.Outlined.AddCircleOutline,
                    label = "Story",
                    onClick = {
                        showCreateSheet = false
                        onOpenCreateStory()
                    }
                )
                CreateMenuOptionRow(
                    icon = Icons.Outlined.AutoAwesome,
                    label = "Story highlight",
                    onClick = {
                        showCreateSheet = false
                        showCreateHighlightDialog = true
                    }
                )
            }
        }
    }

    // Full Post Detail Dialog when tapping a tile in Posts / Reels / Saved grid
    selectedFeedPost?.let { opened ->
        val livePost = (myPosts + savedPosts).firstOrNull { it.id == opened.id } ?: opened
        val authorProfile = profilesByUid[livePost.userId]
            ?: if (livePost.userId == currentUserId) myProfile else null
        Dialog(
            onDismissRequest = { selectedFeedPost = null },
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
                                text = when (selectedTab) {
                                    1 -> "Reels"
                                    2 -> "Saved"
                                    else -> "Posts"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { selectedFeedPost = null }) {
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
                                isFollowing = isFollowingUid(livePost.userId),
                                onToggleFollow = {
                                    authorProfile?.let { onToggleFollow(it) }
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
                                    onDeleteFeedPost(livePost.id.ifBlank { livePost.postId })
                                    selectedFeedPost = null
                                },
                                onOpenAuthor = {
                                    if (livePost.userId != currentUserId) {
                                        selectedFeedPost = null
                                        onOpenUserProfile(livePost.userId)
                                    }
                                },
                                isVisibleForAutoPlay = true
                            )
                        }
                    }
                }
            }
        }
    }

    // Comments Sheet when tapping comment inside Post Detail Dialog
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
                selectedFeedPost = null
                if (uid != currentUserId) {
                    onOpenUserProfile(uid)
                }
            },
            onDismiss = { activeCommentsPost = null }
        )
    }

    // Fullscreen Showcase Video Player Dialog
    selectedShowcaseVideo?.let { video ->
        ShowcaseVideoPlayerDialog(
            video = video,
            resolveProfileVideoFile = resolveProfileVideoFile,
            onDelete = {
                onDeleteVideo(video.id)
                selectedShowcaseVideo = null
            },
            onDismiss = { selectedShowcaseVideo = null }
        )
    }

    activeHighlightViewer?.let { highlight ->
        HighlightViewerDialog(
            highlight = highlight,
            isOwner = true,
            onDismiss = { activeHighlightViewer = null },
            onDeleteHighlight = onDeleteHighlight
        )
    }

    if (showCreateHighlightDialog) {
        CreateHighlightDialog(
            archivedStories = archivedStories,
            uploadProgress = uploadProgress,
            onDismiss = { showCreateHighlightDialog = false },
            onSaveHighlight = onCreateHighlight,
            onSaveHighlightWithResult = onCreateHighlightWithResult
        )
    }
}

@Composable
private fun ProfileStatColumn(
    count: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InstagramProfileActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProfileGridTabItem(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                },
                modifier = Modifier.size(24.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.8.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
                )
        )
    }
}

@Composable
private fun ProfileGridTile(
    entry: ProfileGridEntry,
    aspectRatio: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbSource: String?
    val isVideo: Boolean
    val isCarousel: Boolean
    val captionFallback: String

    when (entry) {
        is ProfileGridEntry.PostEntry -> {
            val post = entry.post
            val photos = post.allPhotoUrls()
            isVideo = post.mediaType == "VIDEO"
            isCarousel = !isVideo && photos.size > 1
            thumbSource = if (isVideo) {
                post.thumbnailDataUrl
            } else {
                photos.firstOrNull()
            }
            captionFallback = post.caption
        }

        is ProfileGridEntry.ShowcaseVideoEntry -> {
            isVideo = true
            isCarousel = false
            thumbSource = entry.video.thumbnailDataUrl
            captionFallback = entry.video.caption
        }
    }

    val decodedBitmap = remember(thumbSource) {
        if (!thumbSource.isNullOrBlank() && thumbSource.startsWith("data:image")) {
            MediaImageHelper.decodeBase64DataUrl(thumbSource)
        } else null
    }

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .testTag("profile_grid_tile_${entry.key}")
    ) {
        if (decodedBitmap != null) {
            Image(
                bitmap = decodedBitmap,
                contentDescription = captionFallback.ifBlank { "Profile post" },
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!thumbSource.isNullOrBlank()) {
            AsyncImage(
                model = thumbSource,
                contentDescription = captionFallback.ifBlank { "Profile post" },
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = captionFallback.ifBlank { "Kindred" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (isVideo) {
            Icon(
                imageVector = Icons.Filled.Movie,
                contentDescription = "Reel",
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(20.dp)
            )
        } else if (isCarousel) {
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
private fun CreateMenuOptionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ShowcaseVideoPlayerDialog(
    video: ProfileVideoItem,
    resolveProfileVideoFile: suspend (ProfileVideoItem) -> Result<File>,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var resolvedFile by remember(video.id) { mutableStateOf<File?>(null) }
    var loadingError by remember(video.id) { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(video.id) {
        resolveProfileVideoFile(video)
            .onSuccess { resolvedFile = it }
            .onFailure { loadingError = it.localizedMessage ?: "Unable to play video" }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val file = resolvedFile
            when {
                file != null -> {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setVideoURI(Uri.fromFile(file))
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                loadingError != null -> {
                    Text(
                        text = loadingError ?: "Unable to play video",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                else -> {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close video",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete video",
                        tint = Color.White
                    )
                }
            }

            if (video.caption.isNotBlank() || video.mediaDurationSec > 0L) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (video.mediaDurationSec > 0L) {
                        Text(
                            text = VideoClipHelper.formatDurationLabel(video.mediaDurationSec),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    if (video.caption.isNotBlank()) {
                        Text(
                            text = video.caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Video?") },
            text = { Text("Remove this showcase video from your Kindred profile?") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
