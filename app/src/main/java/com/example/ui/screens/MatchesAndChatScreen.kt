package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.IcebreakerAiUiState
import com.example.model.CallSession
import com.example.model.ChatMessage
import com.example.model.MatchThreadItem
import com.example.model.PublicProfile
import com.example.model.TypingStatus
import com.example.model.UiState
import com.example.model.UserPresence
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.LocationAndGeoHelper
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class InboxFilterTab(val label: String, val tag: String) {
    ALL("All", "inbox_filter_all"),
    UNREAD("Unread", "inbox_filter_unread"),
    DIRECT("Direct", "inbox_filter_direct"),
    MATCHES("Matches", "inbox_filter_matches")
}

@Composable
fun MatchesAndChatScreen(
    currentUserId: String,
    myProfile: PublicProfile? = null,
    matches: List<MatchThreadItem>,
    selectedMatchId: String?,
    messagesState: UiState<List<ChatMessage>>,
    activePartnerPresence: UserPresence? = null,
    activePartnerTyping: TypingStatus? = null,
    activeCallSession: CallSession? = null,
    icebreakerAiState: IcebreakerAiUiState = IcebreakerAiUiState.Idle,
    onSelectMatch: (String?) -> Unit,
    onOpenSearch: () -> Unit = {},
    onSendMessage: (String, List<String>, String, String) -> Unit,
    onSendMediaMessage: (
        (
            matchId: String,
            participantUids: List<String>,
            messageType: String,
            text: String,
            mediaDataUrl: String?,
            mediaGifId: String?,
            durationSec: Long,
            disappearingSec: Long,
            viewOnce: Boolean
        ) -> Unit
    )? = null,
    onMarkMessagesRead: (matchId: String, messageIds: List<String>) -> Unit = { _, _ -> },
    onMarkViewOnceOpened: (matchId: String, messageId: String) -> Unit = { _, _ -> },
    onDeleteExpiredMessage: (matchId: String, messageId: String) -> Unit = { _, _ -> },
    onTypingChanged: (matchId: String, isTyping: Boolean) -> Unit = { _, _ -> },
    onUpdateDisappearingTimer: (matchId: String, ttlSeconds: Long) -> Unit = { _, _ -> },
    onGenerateAiIcebreakers: (myProfile: PublicProfile?, partnerProfile: PublicProfile?, tone: String) -> Unit = { _, _, _ -> },
    onStartCall: (matchId: String, calleeUid: String, participantUids: List<String>, callType: String) -> Unit = { _, _, _, _ -> },
    onAnswerCall: (CallSession) -> Unit = {},
    onDeclineCall: (CallSession) -> Unit = {},
    onToggleCallMute: (CallSession) -> Unit = {},
    onToggleCallCamera: (CallSession) -> Unit = {},
    onEndCall: (CallSession, Long) -> Unit = { _, _ -> },
    onUnmatch: (String) -> Unit,
    onBlockUser: (partnerId: String, partnerName: String) -> Unit = { _, _ -> },
    onRetryChat: () -> Unit = {},
    onStatusMessage: (String) -> Unit = {}
) {
    val activeThread = remember(matches, selectedMatchId) {
        matches.firstOrNull { it.match.id == selectedMatchId }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 840.dp

        if (isTwoPane) {
            Row(modifier = Modifier.fillMaxSize()) {
                InboxListPane(
                    currentUserId = currentUserId,
                    matches = matches,
                    selectedMatchId = selectedMatchId,
                    onSelectMatch = { onSelectMatch(it) },
                    onOpenSearch = onOpenSearch,
                    modifier = Modifier
                        .width(380.dp)
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
                            myProfile = myProfile,
                            partnerTyping = activePartnerTyping,
                            activeCallSession = activeCallSession,
                            icebreakerAiState = icebreakerAiState,
                            showBackButton = false,
                            onBack = { onSelectMatch(null) },
                            onSendMessage = onSendMessage,
                            onSendMediaMessage = onSendMediaMessage,
                            onMarkMessagesRead = onMarkMessagesRead,
                            onMarkViewOnceOpened = onMarkViewOnceOpened,
                            onDeleteExpiredMessage = onDeleteExpiredMessage,
                            onTypingChanged = onTypingChanged,
                            onUpdateDisappearingTimer = onUpdateDisappearingTimer,
                            onGenerateAiIcebreakers = { tone ->
                                onGenerateAiIcebreakers(myProfile, activeThread.partnerProfile, tone)
                            },
                            onStartCall = onStartCall,
                            onAnswerCall = onAnswerCall,
                            onDeclineCall = onDeclineCall,
                            onToggleCallMute = onToggleCallMute,
                            onToggleCallCamera = onToggleCallCamera,
                            onEndCall = onEndCall,
                            onUnmatch = onUnmatch,
                            onBlockUser = onBlockUser,
                            onRetry = onRetryChat,
                            onStatusMessage = onStatusMessage
                        )
                    } else {
                        SelectConversationPlaceholder()
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
                    myProfile = myProfile,
                    partnerTyping = activePartnerTyping,
                    activeCallSession = activeCallSession,
                    icebreakerAiState = icebreakerAiState,
                    showBackButton = true,
                    onBack = { onSelectMatch(null) },
                    onSendMessage = onSendMessage,
                    onSendMediaMessage = onSendMediaMessage,
                    onMarkMessagesRead = onMarkMessagesRead,
                    onMarkViewOnceOpened = onMarkViewOnceOpened,
                    onDeleteExpiredMessage = onDeleteExpiredMessage,
                    onTypingChanged = onTypingChanged,
                    onUpdateDisappearingTimer = onUpdateDisappearingTimer,
                    onGenerateAiIcebreakers = { tone ->
                        onGenerateAiIcebreakers(myProfile, activeThread.partnerProfile, tone)
                    },
                    onStartCall = onStartCall,
                    onAnswerCall = onAnswerCall,
                    onDeclineCall = onDeclineCall,
                    onToggleCallMute = onToggleCallMute,
                    onToggleCallCamera = onToggleCallCamera,
                    onEndCall = onEndCall,
                    onUnmatch = onUnmatch,
                    onBlockUser = onBlockUser,
                    onRetry = onRetryChat,
                    onStatusMessage = onStatusMessage
                )
            } else {
                InboxListPane(
                    currentUserId = currentUserId,
                    matches = matches,
                    selectedMatchId = null,
                    onSelectMatch = { onSelectMatch(it) },
                    onOpenSearch = onOpenSearch,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun InboxListPane(
    currentUserId: String,
    matches: List<MatchThreadItem>,
    selectedMatchId: String?,
    onSelectMatch: (String) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filterQuery by remember { mutableStateOf("") }
    var activeFilterTab by remember { mutableStateOf(InboxFilterTab.ALL) }

    val unreadCount = remember(matches, currentUserId) {
        matches.count { it.match.isUnreadFor(currentUserId) }
    }

    val filteredMatches = remember(matches, filterQuery, activeFilterTab, currentUserId) {
        val q = filterQuery.trim()
        matches.filter { item ->
            val tabOk = when (activeFilterTab) {
                InboxFilterTab.ALL -> true
                InboxFilterTab.UNREAD -> item.match.isUnreadFor(currentUserId)
                InboxFilterTab.DIRECT -> item.match.conversationType == "DIRECT"
                InboxFilterTab.MATCHES -> item.match.conversationType != "DIRECT"
            }
            val partner = item.partnerProfile
            val queryOk = if (q.isBlank()) {
                true
            } else {
                (partner?.displayName?.contains(q, ignoreCase = true) == true) ||
                    (partner?.cityLabel?.contains(q, ignoreCase = true) == true) ||
                    (partner?.country?.contains(q, ignoreCase = true) == true) ||
                    (item.match.lastMessageText?.contains(q, ignoreCase = true) == true)
            }
            tabOk && queryOk
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.matches_title),
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("${matches.size} ${if (matches.size == 1) "conversation" else "conversations"}")
                        if (unreadCount > 0) {
                            append(" • $unreadCount unread")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unreadCount > 0) CoralRose else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("inbox_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.inbox_search_cd),
                        tint = CoralRose
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (matches.isNotEmpty()) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { if (it.length <= 60) filterQuery = it },
                placeholder = { Text("Filter inbox by name or message...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (filterQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { filterQuery = "" },
                            modifier = Modifier.testTag("clear_inbox_filter_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear filter"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inbox_filter_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InboxFilterTab.entries.forEach { tab ->
                    val label = if (tab == InboxFilterTab.UNREAD && unreadCount > 0) {
                        "${tab.label} ($unreadCount)"
                    } else {
                        tab.label
                    }
                    FilterChip(
                        selected = activeFilterTab == tab,
                        onClick = { activeFilterTab = tab },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoralRose.copy(alpha = 0.2f),
                            selectedLabelColor = CoralRose
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

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
                        text = "Your Inbox Is Quiet",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Like profiles in Discover or search anyone by name to start a live conversation right here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onOpenSearch,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralRose,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("inbox_empty_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search People by Name")
                    }
                }
            }
        } else if (filteredMatches.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "No conversations in this view",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Switch filter tabs or clear your search text to see all threads.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.testTag("inbox_thread_list")
            ) {
                items(filteredMatches, key = { it.match.id }) { item ->
                    val partner = item.partnerProfile
                    val isSelected = item.match.id == selectedMatchId
                    val isUnread = item.match.isUnreadFor(currentUserId)
                    val isDirect = item.match.conversationType == "DIRECT"
                    val timestampLabel = formatInboxTimestamp(
                        item.match.lastMessageAt ?: item.match.updatedAt ?: item.match.createdAt
                    )
                    val previewText = when {
                        item.match.lastMessageText.isNullOrBlank() -> {
                            if (isDirect) "Direct conversation • Say hello" else "Matched! Tap to say hello."
                        }
                        item.match.lastMessageSenderId == currentUserId -> {
                            "You: ${item.match.lastMessageText}"
                        }
                        else -> item.match.lastMessageText
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSelectMatch(item.match.id) }
                            .testTag("match_card_${item.match.id}"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                isUnread -> CoralRose.copy(alpha = 0.08f)
                                else -> MaterialTheme.colorScheme.surfaceContainer
                            }
                        ),
                        border = when {
                            isSelected -> BorderStroke(1.5.dp, CoralRose)
                            isUnread -> BorderStroke(1.dp, CoralRose.copy(alpha = 0.35f))
                            else -> null
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box {
                                ProfileAvatarImage(
                                    photoDataUrl = partner?.photoDataUrl,
                                    displayName = partner?.displayName ?: "Match",
                                    seed = partner?.avatarSeed ?: item.partnerId,
                                    modifier = Modifier.size(58.dp),
                                    initialsFontSizeSp = 22
                                )
                                if (isUnread) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .align(Alignment.TopEnd)
                                            .clip(CircleShape)
                                            .background(CoralRose)
                                            .testTag("unread_dot_${item.match.id}")
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = partner?.let { "${it.displayName}, ${it.age}" } ?: "Kindred Member",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (partner?.isProfileVerified == true) {
                                            Icon(
                                                imageVector = Icons.Filled.VerifiedUser,
                                                contentDescription = stringResource(R.string.cd_verified_badge),
                                                tint = EmeraldMint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        if (isDirect) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = ChampagneGold.copy(alpha = 0.2f),
                                                border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.45f))
                                            ) {
                                                Text(
                                                    text = "Direct",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ChampagneGold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (timestampLabel.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = timestampLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isUnread) CoralRose else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (item.match.disappearingTtlSeconds > 0L) {
                                        Icon(
                                            imageVector = Icons.Filled.Timer,
                                            contentDescription = stringResource(R.string.chat_timer_cd),
                                            tint = ChampagneGold,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = previewText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                                        color = when {
                                            item.match.lastMessageText.isNullOrBlank() -> CoralRose
                                            isUnread -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(3.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = buildString {
                                            if (partner != null) {
                                                append(partner.formattedLocation())
                                            }
                                            if (item.distanceKm != null) {
                                                if (isNotEmpty()) append(" • ")
                                                append(LocationAndGeoHelper.formatDistanceLabel(item.distanceKm))
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isUnread) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = CoralRose
                                        ) {
                                            Text(
                                                text = "New",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
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
    }
}

private fun formatInboxTimestamp(ts: Timestamp?): String {
    if (ts == null) return ""
    val diffMs = (System.currentTimeMillis() - ts.toDate().time).coerceAtLeast(0L)
    val diffMin = diffMs / 60_000L
    val diffHours = diffMin / 60L
    val diffDays = diffHours / 24L
    return when {
        diffMin < 1L -> "Now"
        diffMin < 60L -> "${diffMin}m"
        diffHours < 24L -> "${diffHours}h"
        diffDays < 7L -> "${diffDays}d"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts.toDate().time))
    }
}

@Composable
private fun SelectConversationPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Select a Conversation",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose any thread from your Inbox to read and message in real time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
