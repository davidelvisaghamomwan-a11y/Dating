package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import com.example.model.FriendRequestRecord
import com.example.model.FriendshipRecord
import com.example.model.PublicProfile
import com.example.model.SuggestedFriendItem
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsHubScreen(
    currentUserId: String,
    friendships: List<FriendshipRecord>,
    friendRequests: List<FriendRequestRecord>,
    suggestedFriends: List<SuggestedFriendItem>,
    profilesByUid: Map<String, PublicProfile>,
    onBack: () -> Unit,
    onOpenProfile: (PublicProfile) -> Unit,
    onMessageProfile: (PublicProfile) -> Unit,
    onAcceptRequest: (fromUid: String) -> Unit,
    onRejectRequest: (fromUid: String) -> Unit,
    onCancelRequest: (toUid: String) -> Unit,
    onSendFriendRequest: (targetUid: String) -> Unit,
    onRemoveFriend: (targetUid: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val connectedProfiles = remember(friendships, profilesByUid, currentUserId) {
        friendships
            .filter { currentUserId in it.participantUids }
            .mapNotNull { fs ->
                val otherUid = fs.participantUids.firstOrNull { it != currentUserId } ?: return@mapNotNull null
                profilesByUid[otherUid]
            }
    }
    val incomingRequests = remember(friendRequests, currentUserId) {
        friendRequests.filter { it.toUid == currentUserId && it.status == "PENDING" }
    }
    val outgoingRequests = remember(friendRequests, currentUserId) {
        friendRequests.filter { it.fromUid == currentUserId && it.status == "PENDING" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends & Requests") },
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
                    text = { Text("Friends (${connectedProfiles.size})", maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Requests (${incomingRequests.size})", maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Suggested (${suggestedFriends.size})", maxLines = 1) }
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
                            if (connectedProfiles.isEmpty()) {
                                item {
                                    Text(
                                        text = "No connections yet. Check the Suggested tab or Explore feed to follow people!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(connectedProfiles, key = { it.userId }) { friend ->
                                    val flag = friend.flagEmoji()
                                    Card(
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenProfile(friend) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ProfileAvatarImage(
                                                photoDataUrl = friend.photoDataUrl,
                                                displayName = friend.displayName,
                                                seed = friend.avatarSeed,
                                                modifier = Modifier.size(50.dp),
                                                shape = CircleShape,
                                                initialsFontSizeSp = 17
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = buildString {
                                                        append("${friend.displayName}, ${friend.age}")
                                                        if (flag.isNotBlank()) append(" $flag")
                                                    },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = friend.formattedLocation(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(
                                                onClick = { onMessageProfile(friend) },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Message", tint = CoralRose)
                                            }
                                            IconButton(
                                                onClick = { onRemoveFriend(friend.userId) },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(Icons.Filled.PersonRemove, contentDescription = "Unfriend", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            if (incomingRequests.isEmpty() && outgoingRequests.isEmpty()) {
                                item {
                                    Text(
                                        text = "No pending friend requests right now.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                if (incomingRequests.isNotEmpty()) {
                                    item {
                                        Text("Incoming Requests", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    }
                                    items(incomingRequests, key = { it.id }) { req ->
                                        val sender = profilesByUid[req.fromUid]
                                        Card(
                                            shape = RoundedCornerShape(18.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = sender != null) { if (sender != null) onOpenProfile(sender) }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                ProfileAvatarImage(
                                                    photoDataUrl = sender?.photoDataUrl,
                                                    displayName = sender?.displayName ?: "Member",
                                                    seed = req.fromUid,
                                                    modifier = Modifier.size(46.dp),
                                                    shape = CircleShape,
                                                    initialsFontSizeSp = 15
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = sender?.displayName ?: "Kindred Member",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = sender?.formattedLocation() ?: "Sent you a friend request",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onAcceptRequest(req.fromUid) },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(Icons.Filled.Check, contentDescription = "Accept", tint = EmeraldMint)
                                                }
                                                IconButton(
                                                    onClick = { onRejectRequest(req.fromUid) },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(Icons.Filled.Close, contentDescription = "Decline", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                                if (outgoingRequests.isNotEmpty()) {
                                    item {
                                        Text("Sent Requests", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    }
                                    items(outgoingRequests, key = { it.id }) { req ->
                                        val target = profilesByUid[req.toUid]
                                        Card(
                                            shape = RoundedCornerShape(18.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = target?.displayName ?: "Kindred Member",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedButton(onClick = { onCancelRequest(req.toUid) }) {
                                                    Text("Cancel")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            items(suggestedFriends, key = { it.profile.userId }) { suggestion ->
                                val p = suggestion.profile
                                val flag = p.flagEmoji()
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenProfile(p) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ProfileAvatarImage(
                                            photoDataUrl = p.photoDataUrl,
                                            displayName = p.displayName,
                                            seed = p.avatarSeed,
                                            modifier = Modifier.size(50.dp),
                                            shape = CircleShape,
                                            initialsFontSizeSp = 17
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = buildString {
                                                    append("${p.displayName}, ${p.age}")
                                                    if (flag.isNotBlank()) append(" $flag")
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = buildString {
                                                    append(p.formattedLocation())
                                                    if (suggestion.sharedInterests.isNotEmpty()) {
                                                        append(" • ${suggestion.sharedInterests.size} shared interests")
                                                    }
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(
                                            onClick = { onSendFriendRequest(p.userId) },
                                            colors = ButtonDefaults.buttonColors(containerColor = CoralRose, contentColor = Color.White),
                                            modifier = Modifier.testTag("suggested_add_friend_${p.userId}")
                                        ) {
                                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Add")
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
