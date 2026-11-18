package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ChatMessage
import com.example.model.MatchThreadItem
import com.example.model.UiState
import com.example.model.UserPresence
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.LocationAndGeoHelper

@Composable
fun MatchesAndChatScreen(
    currentUserId: String,
    matches: List<MatchThreadItem>,
    selectedMatchId: String?,
    messagesState: UiState<List<ChatMessage>>,
    activePartnerPresence: UserPresence? = null,
    onSelectMatch: (String?) -> Unit,
    onSendMessage: (String, List<String>, String, String) -> Unit,
    onUnmatch: (String) -> Unit,
    onBlockUser: (partnerId: String, partnerName: String) -> Unit = { _, _ -> },
    onRetryChat: () -> Unit = {}
) {
    val activeThread = remember(matches, selectedMatchId) {
        matches.firstOrNull { it.match.id == selectedMatchId }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 840.dp

        if (isTwoPane) {
            Row(modifier = Modifier.fillMaxSize()) {
                MatchListPane(
                    matches = matches,
                    selectedMatchId = selectedMatchId,
                    onSelectMatch = { onSelectMatch(it) },
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                )
                HorizontalDivider(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (activeThread != null) {
                        ChatScreen(
                            currentUserId = currentUserId,
                            thread = activeThread,
                            messagesState = messagesState,
                            partnerPresence = activePartnerPresence,
                            showBackButton = false,
                            onBack = { onSelectMatch(null) },
                            onSendMessage = onSendMessage,
                            onUnmatch = onUnmatch,
                            onBlockUser = onBlockUser,
                            onRetry = onRetryChat
                        )
                    } else {
                        SelectMatchPlaceholder()
                    }
                }
            }
        } else {
            if (activeThread != null) {
                BackHandler {
                    onSelectMatch(null)
                }
                ChatScreen(
                    currentUserId = currentUserId,
                    thread = activeThread,
                    messagesState = messagesState,
                    partnerPresence = activePartnerPresence,
                    showBackButton = true,
                    onBack = { onSelectMatch(null) },
                    onSendMessage = onSendMessage,
                    onUnmatch = onUnmatch,
                    onBlockUser = onBlockUser,
                    onRetry = onRetryChat
                )
            } else {
                MatchListPane(
                    matches = matches,
                    selectedMatchId = null,
                    onSelectMatch = { onSelectMatch(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun MatchListPane(
    matches: List<MatchThreadItem>,
    selectedMatchId: String?,
    onSelectMatch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.matches_title),
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${matches.size} active ${if (matches.size == 1) "connection" else "connections"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (matches.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_match_celebration),
                        contentDescription = stringResource(R.string.cd_match_art),
                        modifier = Modifier
                            .size(128.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Matches Yet",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Like profiles in Discover. Once both of you like each other, real-time chat unlocks instantly here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(matches, key = { it.match.id }) { item ->
                    val partner = item.partnerProfile
                    val isSelected = item.match.id == selectedMatchId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSelectMatch(item.match.id) }
                            .testTag("match_card_${item.match.id}"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            }
                        ),
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, CoralRose)
                        } else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ProfileAvatarImage(
                                photoDataUrl = partner?.photoDataUrl,
                                displayName = partner?.displayName ?: "Match",
                                seed = partner?.avatarSeed ?: item.partnerId,
                                modifier = Modifier.size(58.dp),
                                initialsFontSizeSp = 22
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = partner?.let { "${it.displayName}, ${it.age}" } ?: "Kindred Member",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (partner?.isProfileVerified == true) {
                                        Icon(
                                            imageVector = Icons.Filled.VerifiedUser,
                                            contentDescription = stringResource(R.string.cd_verified_badge),
                                            tint = EmeraldMint,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = item.match.lastMessageText ?: "Matched! Tap to say hello.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (item.match.lastMessageText != null) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        CoralRose
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.distanceKm != null) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = LocationAndGeoHelper.formatDistanceLabel(item.distanceKm),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun SelectMatchPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Select a Match",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose any conversation from the left panel to chat in real time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
