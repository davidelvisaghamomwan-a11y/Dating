package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.PublicProfile
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.theme.CoralRose

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsHubScreen(
    currentUserId: String,
    followerIds: Set<String>,
    followingIds: Set<String>,
    suggestedProfiles: List<PublicProfile>,
    profilesByUid: Map<String, PublicProfile>,
    initialTab: Int = 0,
    onBack: () -> Unit,
    onOpenProfile: (PublicProfile) -> Unit,
    onMessageProfile: (PublicProfile) -> Unit,
    onToggleFollow: (targetUid: String) -> Unit,
    onRemoveFollower: (followerUid: String) -> Unit
) {
    BackHandler { onBack() }

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }

    val followerProfiles = remember(followerIds, profilesByUid, currentUserId) {
        followerIds
            .filter { it != currentUserId }
            .mapNotNull { profilesByUid[it] }
            .sortedBy { it.displayName.lowercase() }
    }
    val followingProfiles = remember(followingIds, profilesByUid, currentUserId) {
        followingIds
            .filter { it != currentUserId }
            .mapNotNull { profilesByUid[it] }
            .sortedBy { it.displayName.lowercase() }
    }
    val filteredSuggested = remember(suggestedProfiles, followingIds, currentUserId) {
        suggestedProfiles.filter { it.userId != currentUserId && it.userId !in followingIds }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Followers & Following") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("friends_hub_back_button")
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
                .testTag("friends_hub_screen")
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Followers (${followerProfiles.size})", maxLines = 1) },
                    modifier = Modifier.testTag("hub_tab_followers")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Following (${followingProfiles.size})", maxLines = 1) },
                    modifier = Modifier.testTag("hub_tab_following")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Suggested (${filteredSuggested.size})", maxLines = 1) },
                    modifier = Modifier.testTag("hub_tab_suggested")
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 620.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            if (followerProfiles.isEmpty()) {
                                item {
                                    Text(
                                        text = "No followers yet. Share photos and reels so creators can discover and follow you.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(followerProfiles, key = { it.userId }) { follower ->
                                    val isFollowingBack = follower.userId in followingIds
                                    FollowUserRowCard(
                                        profile = follower,
                                        primaryActionLabel = if (isFollowingBack) "Following" else "Follow back",
                                        isPrimaryFilled = !isFollowingBack,
                                        onOpenProfile = { onOpenProfile(follower) },
                                        onMessageProfile = { onMessageProfile(follower) },
                                        onPrimaryAction = { onToggleFollow(follower.userId) },
                                        onSecondaryRemove = { onRemoveFollower(follower.userId) },
                                        primaryTestTag = "follower_toggle_btn_${follower.userId}"
                                    )
                                }
                            }
                        }

                        1 -> {
                            if (followingProfiles.isEmpty()) {
                                item {
                                    Text(
                                        text = "You aren't following anyone yet. Browse Explore or Suggested creators to follow.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(followingProfiles, key = { it.userId }) { person ->
                                    FollowUserRowCard(
                                        profile = person,
                                        primaryActionLabel = "Following",
                                        isPrimaryFilled = false,
                                        onOpenProfile = { onOpenProfile(person) },
                                        onMessageProfile = { onMessageProfile(person) },
                                        onPrimaryAction = { onToggleFollow(person.userId) },
                                        primaryTestTag = "following_toggle_btn_${person.userId}"
                                    )
                                }
                            }
                        }

                        else -> {
                            if (filteredSuggested.isEmpty()) {
                                item {
                                    Text(
                                        text = "You're caught up with suggested creators right now.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(filteredSuggested, key = { it.userId }) { person ->
                                    FollowUserRowCard(
                                        profile = person,
                                        primaryActionLabel = "Follow",
                                        isPrimaryFilled = true,
                                        onOpenProfile = { onOpenProfile(person) },
                                        onMessageProfile = { onMessageProfile(person) },
                                        onPrimaryAction = { onToggleFollow(person.userId) },
                                        primaryTestTag = "suggested_follow_btn_${person.userId}"
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

@Composable
private fun FollowUserRowCard(
    profile: PublicProfile,
    primaryActionLabel: String,
    isPrimaryFilled: Boolean,
    onOpenProfile: () -> Unit,
    onMessageProfile: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSecondaryRemove: (() -> Unit)? = null,
    primaryTestTag: String
) {
    val flag = profile.flagEmoji()
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenProfile)
            .testTag("follow_hub_card_${profile.userId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatarImage(
                photoDataUrl = profile.photoDataUrl,
                displayName = profile.displayName,
                seed = profile.avatarSeed,
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                initialsFontSizeSp = 17
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(profile.displayName)
                        if (flag.isNotBlank()) append(" $flag")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = profile.formattedLocation().ifBlank { "Kindred Creator" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onMessageProfile,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("follow_hub_msg_${profile.userId}")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Message", tint = CoralRose)
            }
            if (isPrimaryFilled) {
                Button(
                    onClick = onPrimaryAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0095F6),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag(primaryTestTag)
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(primaryActionLabel, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                OutlinedButton(
                    onClick = onPrimaryAction,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag(primaryTestTag)
                ) {
                    Text(primaryActionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (onSecondaryRemove != null) {
                IconButton(
                    onClick = onSecondaryRemove,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("remove_follower_${profile.userId}")
                ) {
                    Icon(
                        Icons.Filled.PersonRemove,
                        contentDescription = "Remove follower",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
