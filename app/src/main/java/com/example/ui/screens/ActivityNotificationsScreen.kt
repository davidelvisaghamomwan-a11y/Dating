package com.example.ui.screens

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.FeedPost
import com.example.model.PublicProfile
import com.example.model.SocialActivityItem
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.util.MediaImageHelper

private val InstagramBlue = Color(0xFF0095F6)

private enum class ActivityFilter(val label: String) {
    ALL("All"),
    FOLLOWS("Follows"),
    LIKES("Likes"),
    COMMENTS("Comments")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityNotificationsScreen(
    activities: List<SocialActivityItem>,
    followingIds: Set<String>,
    followerIds: Set<String>,
    profileByUid: Map<String, PublicProfile>,
    onToggleFollow: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenReels: (String) -> Unit,
    onOpenExplore: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    var selectedFilter by rememberSaveable { mutableStateOf(ActivityFilter.ALL) }

    val filteredActivities = remember(activities, selectedFilter) {
        when (selectedFilter) {
            ActivityFilter.ALL -> activities
            ActivityFilter.FOLLOWS -> activities.filterIsInstance<SocialActivityItem.NewFollower>()
            ActivityFilter.LIKES -> activities.filter {
                it is SocialActivityItem.PostLike || it is SocialActivityItem.ReelLike
            }
            ActivityFilter.COMMENTS -> activities.filter {
                it is SocialActivityItem.PostComment || it is SocialActivityItem.ReelComment
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("activity_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("activity_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Activity",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${followerIds.size} followers · ${followingIds.size} following",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ActivityFilter.entries) { filter ->
                    val count = when (filter) {
                        ActivityFilter.ALL -> activities.size
                        ActivityFilter.FOLLOWS -> activities.count { it is SocialActivityItem.NewFollower }
                        ActivityFilter.LIKES -> activities.count {
                            it is SocialActivityItem.PostLike || it is SocialActivityItem.ReelLike
                        }
                        ActivityFilter.COMMENTS -> activities.count {
                            it is SocialActivityItem.PostComment || it is SocialActivityItem.ReelComment
                        }
                    }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = if (count > 0) "${filter.label} ($count)" else filter.label,
                                fontWeight = if (selectedFilter == filter) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("activity_filter_${filter.name.lowercase()}")
                    )
                }
            }

            if (filteredActivities.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(38.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(38.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (selectedFilter) {
                                    ActivityFilter.COMMENTS -> Icons.Outlined.ModeComment
                                    ActivityFilter.LIKES -> Icons.Outlined.FavoriteBorder
                                    else -> Icons.Outlined.NotificationsNone
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Text(
                            text = when (selectedFilter) {
                                ActivityFilter.FOLLOWS -> "No new followers yet"
                                ActivityFilter.LIKES -> "No likes yet"
                                ActivityFilter.COMMENTS -> "No comments yet"
                                ActivityFilter.ALL -> "Activity on your posts and reels"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "When someone follows you, likes a post, or comments on your reels, notifications appear right here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = onOpenExplore,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = InstagramBlue),
                            modifier = Modifier.testTag("activity_explore_cta")
                        ) {
                            Text("Explore creators & posts", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredActivities, key = { it.id }) { item ->
                        ActivityNotificationRow(
                            item = item,
                            isFollowingActor = item.actorUid in followingIds,
                            actorProfile = profileByUid[item.actorUid],
                            onToggleFollow = { onToggleFollow(item.actorUid) },
                            onOpenProfile = { onOpenProfile(item.actorUid) },
                            onOpenReel = { reelId -> onOpenReels(reelId) },
                            onOpenPostOwner = { post -> onOpenProfile(post.userId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityNotificationRow(
    item: SocialActivityItem,
    isFollowingActor: Boolean,
    actorProfile: PublicProfile?,
    onToggleFollow: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenReel: (String) -> Unit,
    onOpenPostOwner: (FeedPost) -> Unit
) {
    val avatarPhoto = actorProfile?.allProfilePhotos()?.firstOrNull() ?: item.actorPhoto
    val timeAgo = remember(item.timestampMs) {
        if (item.timestampMs > 0L) {
            DateUtils.getRelativeTimeSpanString(
                item.timestampMs,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        } else {
            "Just now"
        }
    }

    val onRowClick: () -> Unit = when (item) {
        is SocialActivityItem.NewFollower -> onOpenProfile
        is SocialActivityItem.PostLike -> { { onOpenPostOwner(item.post) } }
        is SocialActivityItem.PostComment -> { { onOpenPostOwner(item.post) } }
        is SocialActivityItem.ReelLike -> { { onOpenReel(item.reel.id) } }
        is SocialActivityItem.ReelComment -> { { onOpenReel(item.reel.id) } }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("activity_item_${item.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileAvatarImage(
            photoDataUrl = avatarPhoto,
            displayName = item.actorName,
            seed = item.actorUid,
            modifier = Modifier
                .size(46.dp)
                .clickable(onClick = onOpenProfile),
            initialsFontSizeSp = 18
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            val summaryText = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(item.actorName)
                }
                append(" ")
                when (item) {
                    is SocialActivityItem.NewFollower -> append("started following you.")
                    is SocialActivityItem.PostLike -> append("liked your post.")
                    is SocialActivityItem.ReelLike -> append("liked your reel.")
                    is SocialActivityItem.PostComment -> {
                        append("commented on your post: ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                            append("\"${item.comment.text}\"")
                        }
                    }
                    is SocialActivityItem.ReelComment -> {
                        append("commented on your reel: ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                            append("\"${item.comment.text}\"")
                        }
                    }
                }
            }

            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = timeAgo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        when (item) {
            is SocialActivityItem.NewFollower -> {
                if (isFollowingActor) {
                    OutlinedButton(
                        onClick = onToggleFollow,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("activity_follow_btn_${item.actorUid}")
                    ) {
                        Text(
                            text = "Following",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Button(
                        onClick = onToggleFollow,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InstagramBlue,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("activity_follow_btn_${item.actorUid}")
                    ) {
                        Text(
                            text = "Follow back",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            is SocialActivityItem.PostLike -> {
                MediaThumbPreview(
                    thumbUrl = item.post.thumbnailDataUrl ?: item.post.allPhotoUrls().firstOrNull(),
                    isVideo = item.post.mediaType == "VIDEO",
                    fallbackText = item.post.caption
                )
            }

            is SocialActivityItem.PostComment -> {
                MediaThumbPreview(
                    thumbUrl = item.post.thumbnailDataUrl ?: item.post.allPhotoUrls().firstOrNull(),
                    isVideo = item.post.mediaType == "VIDEO",
                    fallbackText = item.post.caption
                )
            }

            is SocialActivityItem.ReelLike -> {
                MediaThumbPreview(
                    thumbUrl = item.reel.thumbnailDataUrl,
                    isVideo = true,
                    fallbackText = item.reel.caption
                )
            }

            is SocialActivityItem.ReelComment -> {
                MediaThumbPreview(
                    thumbUrl = item.reel.thumbnailDataUrl,
                    isVideo = true,
                    fallbackText = item.reel.caption
                )
            }
        }
    }
}

@Composable
private fun MediaThumbPreview(
    thumbUrl: String?,
    isVideo: Boolean,
    fallbackText: String
) {
    val bmp = remember(thumbUrl) {
        MediaImageHelper.decodeBase64DataUrl(thumbUrl)
    }
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isVideo) CoralRose.copy(alpha = 0.16f)
                else ChampagneGold.copy(alpha = 0.15f)
            )
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "Media preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!thumbUrl.isNullOrBlank() && thumbUrl.startsWith("http")) {
            AsyncImage(
                model = thumbUrl,
                contentDescription = "Media preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = fallbackText.take(16).ifBlank { if (isVideo) "Reel" else "Post" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(4.dp)
            )
        }
        if (isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
