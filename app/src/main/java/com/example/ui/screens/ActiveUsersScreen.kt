package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ActiveMemberPresenceItem
import com.example.model.PublicProfile
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveUsersScreen(
    activeMembers: List<ActiveMemberPresenceItem>,
    onBack: () -> Unit,
    onOpenProfile: (PublicProfile) -> Unit,
    onMessageProfile: (PublicProfile) -> Unit
) {
    val onlineNow = remember(activeMembers) { activeMembers.filter { it.isOnline } }
    val recentlyLeft = remember(activeMembers) { activeMembers.filter { !it.isOnline && it.isRecentlyLeft } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Active Users", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${onlineNow.size} online now • ${recentlyLeft.size} recently left",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("active_users_back_button")
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
                .testTag("active_users_screen")
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 620.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "online_header") {
                    SectionPillHeader(
                        title = "Online Now (${onlineNow.size})",
                        accentColor = EmeraldMint
                    )
                }
                if (onlineNow.isEmpty()) {
                    item(key = "online_empty") {
                        Text(
                            text = "No other visible members online right now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(onlineNow, key = { "on_${it.profile.userId}" }) { member ->
                        ActiveMemberRowCard(
                            item = member,
                            onClick = { onOpenProfile(member.profile) },
                            onMessage = { onMessageProfile(member.profile) }
                        )
                    }
                }

                item(key = "recent_header") {
                    Spacer(modifier = Modifier.height(6.dp))
                    SectionPillHeader(
                        title = "Recently Online / Just Left (${recentlyLeft.size})",
                        accentColor = ChampagneGold
                    )
                }
                if (recentlyLeft.isEmpty()) {
                    item(key = "recent_empty") {
                        Text(
                            text = "No members recently left within the last 30 minutes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(recentlyLeft, key = { "rec_${it.profile.userId}" }) { member ->
                        ActiveMemberRowCard(
                            item = member,
                            onClick = { onOpenProfile(member.profile) },
                            onMessage = { onMessageProfile(member.profile) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionPillHeader(
    title: String,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(accentColor)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActiveMemberRowCard(
    item: ActiveMemberPresenceItem,
    onClick: () -> Unit,
    onMessage: () -> Unit
) {
    val profile = item.profile
    val flag = profile.flagEmoji()
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("active_user_card_${profile.userId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(52.dp)) {
                ProfileAvatarImage(
                    photoDataUrl = profile.photoDataUrl,
                    displayName = profile.displayName,
                    seed = profile.avatarSeed,
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    initialsFontSizeSp = 18
                )
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(if (item.isOnline) EmeraldMint else ChampagneGold, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append("${profile.displayName}, ${profile.age}")
                        if (flag.isNotBlank()) append(" $flag")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.statusLabel} • ${profile.formattedLocation()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.isOnline) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onMessage,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Message", tint = CoralRose)
            }
        }
    }
}
