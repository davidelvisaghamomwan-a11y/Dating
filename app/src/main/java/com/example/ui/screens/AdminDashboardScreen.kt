package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.FeedPost
import com.example.model.PublicProfile
import com.example.model.ReportRecord
import com.example.model.VideoPost
import com.example.ui.components.ModeratorDeletePostDialog
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint

private sealed interface AdminModerationContentItem {
    val key: String
    val contentId: String
    val userId: String
    val authorName: String
    val typeBadge: String
    val caption: String
    val createdAtSeconds: Long

    data class PostItem(
        val post: FeedPost,
        override val authorName: String
    ) : AdminModerationContentItem {
        override val contentId: String = post.id.ifBlank { post.postId }
        override val key: String = "admin_post_$contentId"
        override val userId: String = post.userId
        override val typeBadge: String = when {
            post.mediaType == "VIDEO" -> "VIDEO"
            post.allPhotoUrls().isNotEmpty() -> "PHOTO"
            else -> "POST"
        }
        override val caption: String = post.caption
        override val createdAtSeconds: Long = post.createdAt?.seconds ?: 0L
    }

    data class ReelItem(
        val reel: VideoPost,
        override val authorName: String
    ) : AdminModerationContentItem {
        override val contentId: String = reel.id
        override val key: String = "admin_reel_$contentId"
        override val userId: String = reel.userId
        override val typeBadge: String = "REEL"
        override val caption: String = reel.caption
        override val createdAtSeconds: Long = reel.createdAt?.seconds ?: 0L
    }
}

private sealed interface AdminDeleteTarget {
    val authorName: String
    val contentLabel: String
    val preview: String
    val relatedReportId: String?

    data class FeedPostTarget(
        val post: FeedPost,
        override val authorName: String,
        override val relatedReportId: String? = null
    ) : AdminDeleteTarget {
        override val contentLabel: String = when {
            post.mediaType == "VIDEO" -> "video"
            post.allPhotoUrls().isNotEmpty() -> "photo post"
            else -> "post"
        }
        override val preview: String = post.caption
    }

    data class VideoPostTarget(
        val reel: VideoPost,
        override val authorName: String,
        override val relatedReportId: String? = null
    ) : AdminDeleteTarget {
        override val contentLabel: String = "reel"
        override val preview: String = reel.caption
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminDashboardScreen(
    reports: List<ReportRecord>,
    allProfiles: List<PublicProfile>,
    onBack: () -> Unit,
    onUpdateReportStatus: (reportId: String, status: String) -> Unit,
    onSetUserSuspended: (targetUid: String, suspended: Boolean) -> Unit,
    onOpenProfile: (PublicProfile) -> Unit,
    feedPosts: List<FeedPost> = emptyList(),
    videoPosts: List<VideoPost> = emptyList(),
    onModeratorDeleteFeedPost: (post: FeedPost, reason: String, details: String, relatedReportId: String?) -> Unit = { _, _, _, _ -> },
    onModeratorDeleteVideoPost: (reel: VideoPost, reason: String, details: String, relatedReportId: String?) -> Unit = { _, _, _, _ -> }
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<AdminDeleteTarget?>(null) }
    val profilesByUid = remember(allProfiles) { allProfiles.associateBy { it.userId } }

    val moderationContentItems = remember(feedPosts, videoPosts, profilesByUid) {
        buildList {
            feedPosts.forEach { post ->
                val resolvedName = profilesByUid[post.userId]?.displayName?.takeIf { it.isNotBlank() }
                    ?: post.authorName.ifBlank { post.userId }
                add(AdminModerationContentItem.PostItem(post = post, authorName = resolvedName))
            }
            videoPosts.forEach { reel ->
                val resolvedName = profilesByUid[reel.userId]?.displayName?.takeIf { it.isNotBlank() }
                    ?: reel.authorName.ifBlank { reel.userId }
                add(AdminModerationContentItem.ReelItem(reel = reel, authorName = resolvedName))
            }
        }.sortedByDescending { it.createdAtSeconds }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Moderation") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("admin_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .testTag("admin_dashboard_screen")
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Reports (${reports.size})") },
                    modifier = Modifier.testTag("admin_tab_reports")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Members (${allProfiles.size})") },
                    modifier = Modifier.testTag("admin_tab_members")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Content (${moderationContentItems.size})") },
                    modifier = Modifier.testTag("admin_tab_content")
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            items(reports, key = { it.id }) { report ->
                                val target = profilesByUid[report.reportedUid]
                                val authorLabel = target?.displayName?.takeIf { it.isNotBlank() }
                                    ?: report.reportedUid
                                val matchingUserPosts = remember(feedPosts, report.reportedUid) {
                                    feedPosts.filter { it.userId == report.reportedUid }.take(3)
                                }
                                val matchingUserReels = remember(videoPosts, report.reportedUid) {
                                    videoPosts.filter { it.userId == report.reportedUid }.take(2)
                                }

                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "$authorLabel (${report.sourceSurface})",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = report.status,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (report.status == "OPEN") CoralRose else EmeraldMint
                                            )
                                        }
                                        Text(text = "Reason: ${report.reason}", style = MaterialTheme.typography.bodyMedium)
                                        if (!report.details.isNullOrBlank()) {
                                            Text(
                                                text = report.details,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (matchingUserPosts.isNotEmpty() || matchingUserReels.isNotEmpty()) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            )
                                            Text(
                                                text = "Reported user's active posts",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            matchingUserPosts.forEach { post ->
                                                val postId = post.id.ifBlank { post.postId }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = post.caption.ifBlank { "[${post.mediaType} post]" },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(end = 8.dp)
                                                    )
                                                    OutlinedButton(
                                                        onClick = {
                                                            deleteTarget = AdminDeleteTarget.FeedPostTarget(
                                                                post = post,
                                                                authorName = authorLabel,
                                                                relatedReportId = report.id
                                                            )
                                                        },
                                                        modifier = Modifier.testTag("admin_report_remove_post_${report.id}_$postId")
                                                    ) {
                                                        Text("Remove Post", color = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                            matchingUserReels.forEach { reel ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = reel.caption.ifBlank { "[Reel video]" },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(end = 8.dp)
                                                    )
                                                    OutlinedButton(
                                                        onClick = {
                                                            deleteTarget = AdminDeleteTarget.VideoPostTarget(
                                                                reel = reel,
                                                                authorName = authorLabel,
                                                                relatedReportId = report.id
                                                            )
                                                        },
                                                        modifier = Modifier.testTag("admin_report_remove_reel_${report.id}_${reel.id}")
                                                    ) {
                                                        Text("Remove Reel", color = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                        }

                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(onClick = { onUpdateReportStatus(report.id, "RESOLVED") }) {
                                                Text("Mark Resolved")
                                            }
                                            OutlinedButton(onClick = { onUpdateReportStatus(report.id, "DISMISSED") }) {
                                                Text("Dismiss")
                                            }
                                            if (target != null) {
                                                Button(
                                                    onClick = { onSetUserSuspended(target.userId, !target.suspended) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (target.suspended) EmeraldMint else MaterialTheme.colorScheme.error
                                                    )
                                                ) {
                                                    Text(if (target.suspended) "Reinstate" else "Suspend User")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            items(allProfiles, key = { it.userId }) { member ->
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenProfile(member) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${member.displayName}, ${member.age} ${member.flagEmoji()}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${member.formattedLocation()} • ${if (member.suspended) "SUSPENDED" else "Active"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (member.suspended) MaterialTheme.colorScheme.error else ChampagneGold
                                            )
                                        }
                                        OutlinedButton(onClick = { onSetUserSuspended(member.userId, !member.suspended) }) {
                                            Text(if (member.suspended) "Reinstate" else "Suspend")
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            if (moderationContentItems.isEmpty()) {
                                item(key = "admin_content_empty") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No posts or reels available to moderate.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                items(moderationContentItems, key = { it.key }) { item ->
                                    Card(
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer
                                                    ) {
                                                        Text(
                                                            text = item.typeBadge,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = item.authorName,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = item.caption.ifBlank { "[No caption]" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    deleteTarget = when (item) {
                                                        is AdminModerationContentItem.PostItem ->
                                                            AdminDeleteTarget.FeedPostTarget(item.post, item.authorName)
                                                        is AdminModerationContentItem.ReelItem ->
                                                            AdminDeleteTarget.VideoPostTarget(item.reel, item.authorName)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error
                                                ),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                modifier = Modifier.testTag("admin_remove_content_${item.contentId}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Shield,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.size(4.dp))
                                                Text("Remove")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ModeratorDeletePostDialog(
            targetAuthorName = target.authorName,
            contentTypeLabel = target.contentLabel,
            contentPreview = target.preview,
            onDismiss = { deleteTarget = null },
            onConfirmDelete = { reason, details ->
                when (target) {
                    is AdminDeleteTarget.FeedPostTarget ->
                        onModeratorDeleteFeedPost(target.post, reason, details, target.relatedReportId)
                    is AdminDeleteTarget.VideoPostTarget ->
                        onModeratorDeleteVideoPost(target.reel, reason, details, target.relatedReportId)
                }
                deleteTarget = null
            }
        )
    }
}

