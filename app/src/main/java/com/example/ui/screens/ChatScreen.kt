package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.KindredRepository
import com.example.model.ChatMessage
import com.example.model.MatchRecord
import com.example.model.MatchThreadItem
import com.example.model.PublicProfile
import com.example.model.UiState
import com.example.model.UserPresence
import com.example.ui.components.ChatBubble
import com.example.ui.components.DayDividerPill
import com.example.ui.components.PartnerProfileQuickSheet
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.components.VerifiedShieldBadge
import com.example.ui.components.dayBucketLabel
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.LocationAndGeoHelper
import com.example.util.PresenceUtils
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

private const val MAX_CHAT_MESSAGE_LENGTH = 1000

/**
 * Self-contained Firestore-backed [ChatScreen] composable for a matched conversation.
 * Connects directly to Firebase Firestore via [KindredRepository] and [ChatViewModel]
 * to stream real-time messages and send new messages between matched users.
 */
@Composable
fun ChatScreen(
    matchId: String,
    participantUids: List<String>,
    currentUserId: String,
    partnerProfile: PublicProfile? = null,
    distanceKm: Double? = null,
    showBackButton: Boolean = true,
    onBack: () -> Unit = {},
    onBlockUser: (partnerId: String, partnerName: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val partnerId = remember(participantUids, currentUserId) {
        participantUids.firstOrNull { it != currentUserId } ?: ""
    }
    val chatViewModel: ChatViewModel = viewModel(
        key = "${currentUserId}_$matchId",
        factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    repository = KindredRepository(context.applicationContext),
                    matchId = matchId,
                    currentUserId = currentUserId,
                    partnerUserId = partnerId
                )
            }
        }
    )

    val messagesState by chatViewModel.messagesState.collectAsStateWithLifecycle()
    val partnerPresence by chatViewModel.partnerPresence.collectAsStateWithLifecycle()
    val statusError by chatViewModel.statusError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusError) {
        val msg = statusError
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            chatViewModel.clearError()
        }
    }

    val thread = remember(matchId, participantUids, partnerId, partnerProfile, distanceKm) {
        MatchThreadItem(
            match = MatchRecord(
                id = matchId,
                participantUids = participantUids
            ),
            partnerId = partnerId,
            partnerProfile = partnerProfile,
            distanceKm = distanceKm
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Transparent
    ) { innerPadding ->
        ChatScreen(
            currentUserId = currentUserId,
            thread = thread,
            messagesState = messagesState,
            partnerPresence = partnerPresence,
            showBackButton = showBackButton,
            onBack = onBack,
            onSendMessage = { _, participants, text, messageType ->
                chatViewModel.sendMessage(participants, text, messageType)
            },
            onUnmatch = {
                chatViewModel.unmatch(onComplete = onBack)
            },
            onBlockUser = onBlockUser,
            onRetry = { chatViewModel.retry() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

/**
 * Stateless, real-time Firestore chat thread composable displaying message bubbles,
 * calendar day dividers, icebreaker prompts, partner profile quick-view, and composer.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUserId: String,
    thread: MatchThreadItem,
    messagesState: UiState<List<ChatMessage>>,
    partnerPresence: UserPresence? = null,
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onSendMessage: (matchId: String, participantUids: List<String>, text: String, messageType: String) -> Unit,
    onUnmatch: (matchId: String) -> Unit,
    onBlockUser: (partnerId: String, partnerName: String) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var messageDraft by remember(thread.match.id) { mutableStateOf("") }
    var showUnmatchConfirm by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showPartnerSheet by remember { mutableStateOf(false) }
    var nowMillis by remember(partnerPresence) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(partnerPresence) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(PresenceUtils.UI_TICKER_INTERVAL_MS)
        }
    }
    val presenceState = remember(partnerPresence, nowMillis) {
        PresenceUtils.resolvePresenceDisplay(partnerPresence, nowMillis)
    }
    val listState = rememberLazyListState()
    val partner = thread.partnerProfile

    fun submitDraft() {
        val clean = messageDraft.trim()
        if (clean.isNotEmpty() && thread.match.participantUids.size == 2) {
            onSendMessage(
                thread.match.id,
                thread.match.participantUids,
                clean,
                "TEXT"
            )
            messageDraft = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("chat_screen")
    ) {
        // Real-time Chat Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBackButton) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.chat_back_cd)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { if (partner != null) showPartnerSheet = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("chat_partner_header"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(46.dp)) {
                        ProfileAvatarImage(
                            photoDataUrl = partner?.photoDataUrl,
                            displayName = partner?.displayName ?: "Match",
                            seed = partner?.avatarSeed ?: thread.partnerId,
                            modifier = Modifier.size(44.dp),
                            initialsFontSizeSp = 17
                        )
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(
                                    if (presenceState.isOnline) EmeraldMint else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                                .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                                .testTag("chat_presence_dot")
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = partner?.let { "${it.displayName}, ${it.age}" } ?: "Kindred Match",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            VerifiedShieldBadge(
                                isVerified = partner?.isProfileVerified == true,
                                compact = true
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        color = if (presenceState.isOnline) {
                                            EmeraldMint
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        },
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = presenceState.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (presenceState.isOnline) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (presenceState.isOnline) {
                                    EmeraldMint
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("chat_presence_status")
                            )
                            if (thread.distanceKm != null) {
                                Text(
                                    text = "• ${LocationAndGeoHelper.formatDistanceLabel(thread.distanceKm)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = { showBlockConfirm = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chat_block_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Block,
                        contentDescription = stringResource(R.string.chat_block_cd),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                    )
                }

                IconButton(
                    onClick = { showUnmatchConfirm = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("unmatch_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.chat_unmatch_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Firestore Messages Stream
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (messagesState) {
                is UiState.Loading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = CoralRose)
                        Text(
                            text = stringResource(R.string.chat_connecting),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is UiState.Error -> {
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .widthIn(max = 400.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Could not sync messages",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = messagesState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = CoralRose),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chat_retry_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.chat_retry))
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    val messages = messagesState.data
                    LaunchedEffect(messages.size) {
                        if (messages.isNotEmpty()) {
                            listState.animateScrollToItem(messages.lastIndex)
                        }
                    }

                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 460.dp)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ProfileAvatarImage(
                                    photoDataUrl = partner?.photoDataUrl,
                                    displayName = partner?.displayName ?: "Match",
                                    seed = partner?.avatarSeed ?: thread.partnerId,
                                    modifier = Modifier.size(72.dp),
                                    initialsFontSizeSp = 26
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = stringResource(R.string.chat_break_the_ice),
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "You matched with ${partner?.displayName ?: "this member"}. Messages sync in real time with Firebase Firestore.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(18.dp))

                                val sharedInterest = partner?.interests?.firstOrNull()
                                val icebreakers = listOfNotNull(
                                    sharedInterest?.let { "I noticed you like $it — any favorite spot nearby?" },
                                    "Coffee walk or weekend trail — which wins?",
                                    "Hey ${partner?.displayName ?: "there"}! Great matching with you."
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    icebreakers.forEachIndexed { index, prompt ->
                                        AssistChip(
                                            onClick = {
                                                if (thread.match.participantUids.size == 2) {
                                                    onSendMessage(
                                                        thread.match.id,
                                                        thread.match.participantUids,
                                                        prompt,
                                                        "ICEBREAKER"
                                                    )
                                                }
                                            },
                                            modifier = Modifier.testTag("icebreaker_chip_$index"),
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = ChampagneGold,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = prompt,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val dayFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("chat_message_list"),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            itemsIndexed(
                                items = messages,
                                key = { idx, msg -> msg.id.ifBlank { "msg_$idx" } }
                            ) { index, msg ->
                                val prev = messages.getOrNull(index - 1)
                                val next = messages.getOrNull(index + 1)

                                val msgDayKey = dayBucketLabel(msg.createdAt?.toDate(), dayFormatter)
                                val prevDayKey = prev?.let { dayBucketLabel(it.createdAt?.toDate(), dayFormatter) }
                                val showDayHeader = prev == null || msgDayKey != prevDayKey

                                val isMine = msg.senderId == currentUserId
                                val isSameSenderAsPrev = !showDayHeader && prev?.senderId == msg.senderId
                                val isLastInCluster = next == null ||
                                    next.senderId != msg.senderId ||
                                    dayBucketLabel(next.createdAt?.toDate(), dayFormatter) != msgDayKey

                                if (showDayHeader) {
                                    DayDividerPill(
                                        label = msgDayKey,
                                        modifier = Modifier.padding(
                                            top = if (index == 0) 2.dp else 12.dp,
                                            bottom = 10.dp
                                        )
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(if (isSameSenderAsPrev) 3.dp else 10.dp))
                                }

                                ChatBubble(
                                    message = msg,
                                    isMine = isMine,
                                    showPartnerAvatar = !isMine && isLastInCluster,
                                    partner = partner,
                                    partnerId = thread.partnerId
                                )
                            }
                        }
                    }
                }
            }
        }

        // Message Composer
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 4.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = messageDraft,
                        onValueChange = { if (it.length <= MAX_CHAT_MESSAGE_LENGTH) messageDraft = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input"),
                        placeholder = { Text(stringResource(R.string.chat_placeholder)) },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { submitDraft() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CoralRose,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    val canSend = messageDraft.isNotBlank() && thread.match.participantUids.size == 2
                    Surface(
                        shape = CircleShape,
                        color = if (canSend) CoralRose else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(48.dp)
                    ) {
                        IconButton(
                            onClick = { submitDraft() },
                            enabled = canSend,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.btn_send),
                                tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (messageDraft.length > 850) {
                    Text(
                        text = "${messageDraft.length}/$MAX_CHAT_MESSAGE_LENGTH",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (messageDraft.length >= MAX_CHAT_MESSAGE_LENGTH) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp, end = 60.dp)
                    )
                }
            }
        }
    }

    if (showPartnerSheet && partner != null) {
        ModalBottomSheet(
            onDismissRequest = { showPartnerSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            PartnerProfileQuickSheet(
                partner = partner,
                distanceKm = thread.distanceKm,
                onBlockClick = {
                    showPartnerSheet = false
                    showBlockConfirm = true
                }
            )
        }
    }

    if (showUnmatchConfirm) {
        AlertDialog(
            onDismissRequest = { showUnmatchConfirm = false },
            title = { Text("Unmatch ${partner?.displayName ?: "member"}?") },
            text = { Text("This removes the match and closes your conversation.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnmatchConfirm = false
                        onUnmatch(thread.match.id)
                    }
                ) {
                    Text("Unmatch", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnmatchConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBlockConfirm) {
        val partnerName = partner?.displayName ?: "this user"
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text("Block $partnerName?") },
            text = {
                Text("This permanently closes the match, blocks future messages, and excludes $partnerName from discovery. You can manage blocked users in Profile.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockConfirm = false
                        onBlockUser(thread.partnerId, partnerName)
                    },
                    modifier = Modifier.testTag("confirm_chat_block_button")
                ) {
                    Text("Block", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
