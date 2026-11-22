package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.FriendStatus
import com.example.model.ProfileVideoItem
import com.example.model.PublicProfile
import com.example.model.UserPresence
import com.example.model.VisibilityLevel
import com.example.ui.components.PartnerPhotoGalleryDialog
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.components.ReportUserDialog
import com.example.ui.components.VerifiedShieldBadge
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.LocationAndGeoHelper
import com.example.util.PresenceUtils
import com.example.util.ProfileCompletionUtils
import com.example.util.VerificationAnalyzer
import com.example.util.VideoClipHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserProfileDetailScreen(
    profile: PublicProfile,
    currentUserId: String,
    videos: List<ProfileVideoItem>,
    friends: List<PublicProfile>,
    friendStatus: FriendStatus,
    presence: UserPresence? = null,
    distanceKm: Double? = null,
    isLiked: Boolean = false,
    isFavorited: Boolean = false,
    isBlocked: Boolean = false,
    isMatched: Boolean = false,
    resolveVideoFile: (suspend (ProfileVideoItem) -> Result<File>)? = null,
    onBack: () -> Unit,
    onRecordProfileView: () -> Unit = {},
    onOpenFriendProfile: (PublicProfile) -> Unit = {},
    onMessageClick: (PublicProfile) -> Unit,
    onFriendAction: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleFavorite: () -> Unit,
    onReportUser: (reason: String, details: String, alsoBlock: Boolean) -> Unit,
    onBlockUser: () -> Unit,
    onUnblockUser: () -> Unit
) {
    val isOwner = profile.userId == currentUserId
    val isFriend = friendStatus == FriendStatus.FRIENDS
    val canSeePhotos = ProfileCompletionUtils.canViewerSeeSection(profile.photosVisibility, isOwner, isFriend, isMatched)
    val canSeeVideos = ProfileCompletionUtils.canViewerSeeSection(profile.videosVisibility, isOwner, isFriend, isMatched)
    val canSeeInfo = ProfileCompletionUtils.canViewerSeeSection(profile.infoVisibility, isOwner, isFriend, isMatched)

    val photos = remember(profile.userId, profile.photoDataUrl, profile.profilePhotos, canSeePhotos) {
        if (canSeePhotos) profile.allProfilePhotos() else emptyList()
    }
    val visibleVideos = remember(videos, canSeeVideos) {
        if (canSeeVideos) videos else emptyList()
    }

    val flag = profile.flagEmoji()
    val presenceDisplay = remember(presence, profile.hideOnlineStatus) {
        if (profile.hideOnlineStatus || presence?.hideOnlineStatus == true) null
        else PresenceUtils.resolvePresenceDisplay(presence)
    }

    var galleryInitialIndex by remember(profile.userId) { mutableStateOf<Int?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }

    LaunchedEffect(profile.userId) {
        if (!isOwner) onRecordProfileView()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = buildString {
                            append(profile.displayName)
                            if (flag.isNotBlank()) append(" $flag")
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("user_detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("user_profile_detail_screen")
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(enabled = photos.isNotEmpty() || visibleVideos.isNotEmpty()) {
                                        galleryInitialIndex = 0
                                    }
                            ) {
                                ProfileAvatarImage(
                                    photoDataUrl = profile.photoDataUrl,
                                    displayName = profile.displayName,
                                    seed = profile.avatarSeed,
                                    modifier = Modifier.size(84.dp),
                                    shape = CircleShape,
                                    initialsFontSizeSp = 28
                                )
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = buildString {
                                            append("${profile.displayName}, ${profile.age}")
                                            if (flag.isNotBlank()) append(" $flag")
                                        },
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("user_detail_name_age_flag")
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    VerifiedShieldBadge(isVerified = profile.isProfileVerified, compact = true)
                                    if (presenceDisplay != null) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (presenceDisplay.isOnline) {
                                                EmeraldMint.copy(alpha = 0.16f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            }
                                        ) {
                                            Text(
                                                text = presenceDisplay.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (presenceDisplay.isOnline) {
                                                    EmeraldMint
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = buildString {
                                            append(profile.formattedLocation())
                                            if (distanceKm != null) {
                                                append(" • ")
                                                append(LocationAndGeoHelper.formatDistanceLabel(distanceKm))
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (!isOwner) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("user_detail_actions_row"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onMessageClick(profile) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CoralRose, contentColor = Color.White),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("user_detail_message_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Message", maxLines = 1)
                                }

                                val friendLabel = when (friendStatus) {
                                    FriendStatus.NONE -> "Add Friend"
                                    FriendStatus.OUTGOING_PENDING -> "Request Sent"
                                    FriendStatus.INCOMING_PENDING -> "Accept Friend"
                                    FriendStatus.FRIENDS -> "Friends"
                                }
                                OutlinedButton(
                                    onClick = onFriendAction,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("user_detail_friend_button")
                                ) {
                                    Icon(
                                        imageVector = if (friendStatus == FriendStatus.FRIENDS) Icons.Filled.Check else Icons.Filled.PersonAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(friendLabel, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = onToggleLike,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (isLiked) CoralRose else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("user_detail_like_button")
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isLiked) "Liked" else "Like", maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = onToggleFavorite,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (isFavorited) ChampagneGold else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("user_detail_favorite_button")
                                ) {
                                    Icon(
                                        imageVector = if (isFavorited) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isFavorited) "Saved" else "Favorite", maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = { showReportDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ChampagneGold),
                                    border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("user_detail_report_button")
                                ) {
                                    Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Report", maxLines = 1)
                                }

                                if (isBlocked) {
                                    OutlinedButton(
                                        onClick = onUnblockUser,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldMint),
                                        border = BorderStroke(1.dp, EmeraldMint.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("user_detail_unblock_button")
                                    ) {
                                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Unblock", maxLines = 1)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { showBlockDialog = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("user_detail_block_button")
                                    ) {
                                        Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(17.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Block", maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                // Bio & Interests
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("About ${profile.displayName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (!canSeeInfo) {
                            PrivateSectionNotice(level = profile.infoVisibility, label = "Bio & interests")
                        } else {
                            if (profile.bio.isNotBlank()) {
                                Text(text = profile.bio, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (profile.interests.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    profile.interests.forEach { tag ->
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                text = tag,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Photos Album
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = CoralRose, modifier = Modifier.size(18.dp))
                                Text("Photos (${photos.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            if (photos.isNotEmpty()) {
                                TextButton(onClick = { galleryInitialIndex = 0 }) {
                                    Text("Open Gallery")
                                }
                            }
                        }

                        if (!canSeePhotos) {
                            PrivateSectionNotice(level = profile.photosVisibility, label = "Photos")
                        } else if (photos.isEmpty()) {
                            Text(
                                text = "No photos uploaded yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            photos.chunked(3).forEachIndexed { rowIdx, rowPhotos ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowPhotos.forEachIndexed { colIdx, photoUrl ->
                                        val flatIdx = rowIdx * 3 + colIdx
                                        ProfileAvatarImage(
                                            photoDataUrl = photoUrl,
                                            displayName = profile.displayName,
                                            seed = profile.avatarSeed,
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(0.85f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { galleryInitialIndex = flatIdx }
                                                .testTag("user_detail_photo_$flatIdx"),
                                            shape = RoundedCornerShape(14.dp),
                                            initialsFontSizeSp = 22
                                        )
                                    }
                                    repeat(3 - rowPhotos.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                // Videos Section
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.testTag("user_detail_videos_section")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(18.dp))
                            Text("Videos (${visibleVideos.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        if (!canSeeVideos) {
                            PrivateSectionNotice(level = profile.videosVisibility, label = "Videos")
                        } else if (visibleVideos.isEmpty()) {
                            Text(
                                text = "No showcase videos added yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(visibleVideos.size, key = { idx -> visibleVideos[idx].id }) { vIdx ->
                                    val video = visibleVideos[vIdx]
                                    val thumb = remember(video.thumbnailDataUrl) {
                                        video.thumbnailDataUrl?.let { VerificationAnalyzer.decodeBase64DataUrl(it) }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(width = 132.dp, height = 176.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF1A1423))
                                            .clickable {
                                                galleryInitialIndex = photos.size + vIdx
                                            }
                                            .testTag("user_detail_video_$vIdx")
                                    ) {
                                        if (thumb != null) {
                                            Image(
                                                bitmap = thumb,
                                                contentDescription = video.caption.ifBlank { "Showcase video" },
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Play showcase video",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .align(Alignment.Center)
                                        )
                                        if (video.mediaDurationSec > 0L) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.Black.copy(alpha = 0.65f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(6.dp)
                                            ) {
                                                Text(
                                                    text = VideoClipHelper.formatDurationLabel(video.mediaDurationSec),
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Friends List Section
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.testTag("user_detail_friends_section")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Friends (${friends.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (friends.isEmpty()) {
                            Text(
                                text = "No connected friends listed yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(friends, key = { it.userId }) { friend ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(74.dp)
                                            .clickable { onOpenFriendProfile(friend) }
                                            .testTag("user_detail_friend_${friend.userId}")
                                    ) {
                                        ProfileAvatarImage(
                                            photoDataUrl = friend.photoDataUrl,
                                            displayName = friend.displayName,
                                            seed = friend.avatarSeed,
                                            modifier = Modifier.size(56.dp),
                                            shape = CircleShape,
                                            initialsFontSizeSp = 18
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = friend.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    val startGalleryIdx = galleryInitialIndex
    if (startGalleryIdx != null && (photos.isNotEmpty() || visibleVideos.isNotEmpty())) {
        PartnerPhotoGalleryDialog(
            photos = photos,
            initialIndex = startGalleryIdx,
            partnerName = profile.displayName,
            videos = visibleVideos,
            resolveVideoFile = resolveVideoFile,
            onDismiss = { galleryInitialIndex = null }
        )
    }

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

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text("Block ${profile.displayName}?") },
            text = { Text("They will be hidden from discovery, chat, and community feeds. You can unblock anytime.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockDialog = false
                        onBlockUser()
                    },
                    modifier = Modifier.testTag("user_detail_confirm_block_button")
                ) {
                    Text("Block", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) { Text("Cancel") }
            }
        )
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
            Icon(Icons.Filled.Lock, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(16.dp))
            Text(
                text = "$label visible to ${VisibilityLevel.labelFor(level).lowercase()} only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
