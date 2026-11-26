package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.communication.VoiceNotePlayer
import com.example.communication.VoiceNoteRecorder
import com.example.communication.VoicePermissionHelper
import com.example.data.KindredRepository
import com.example.model.CallAudioChunk
import com.example.model.CallSession
import com.example.model.CallVideoFrame
import com.example.model.ChatMessage
import com.example.model.MatchRecord
import com.example.model.MatchThreadItem
import com.example.model.PublicProfile
import com.example.model.TypingStatus
import com.example.model.UiState
import com.example.model.UserPresence
import com.example.ui.components.ChatBubble
import com.example.ui.components.DayDividerPill
import com.example.ui.components.DisappearingTimerSheetContent
import com.example.ui.components.FullscreenIncomingCallDialog
import com.example.ui.components.GifPickerSheetContent
import com.example.ui.components.InAppCallOverlay
import com.example.ui.components.MediaViewerDialog
import com.example.ui.components.PartnerProfileQuickSheet
import com.example.ui.components.PartnerTypingBubble
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.components.ReportUserDialog
import com.example.ui.components.UnmatchOrBlockDialog
import com.example.ui.components.dayBucketLabel
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.LocationAndGeoHelper
import com.example.util.MediaDownloadHelper
import com.example.util.MediaImageHelper
import com.example.util.PreparedVideoUpload
import com.example.util.PresenceUtils
import com.example.util.VideoClipHelper
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private const val MAX_CHAT_MESSAGE_LENGTH = 1000
private const val TYPING_STALE_WINDOW_MS = 10_000L
private const val TYPING_IDLE_DEBOUNCE_MS = 4_500L

private class CaptureUnlimitedVideoContract : ActivityResultContracts.CaptureVideo() {
    override fun createIntent(context: Context, input: Uri): Intent {
        return super.createIntent(context, input).apply {
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
        }
    }
}

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
    onReportUser: (partnerId: String, partnerName: String, reason: String, details: String, alsoBlock: Boolean) -> Unit = { _, _, _, _, _ -> },
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
    val partnerTyping by chatViewModel.partnerTyping.collectAsStateWithLifecycle()
    val activeCallSession by chatViewModel.activeCallSession.collectAsStateWithLifecycle()
    val activeCallAudioChunks by chatViewModel.activeCallAudioChunks.collectAsStateWithLifecycle()
    val activeCallVideoFrames by chatViewModel.activeCallVideoFrames.collectAsStateWithLifecycle()
    val statusError by chatViewModel.statusError.collectAsStateWithLifecycle()
    val isUploadingMedia by chatViewModel.isUploadingMedia.collectAsStateWithLifecycle()
    val uploadProgress by chatViewModel.uploadProgress.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
            partnerTyping = partnerTyping,
            activeCallSession = activeCallSession,
            activeCallAudioChunks = activeCallAudioChunks,
            activeCallVideoFrames = activeCallVideoFrames,
            isUploadingMedia = isUploadingMedia,
            uploadProgress = uploadProgress,
            showBackButton = showBackButton,
            onBack = onBack,
            onSendMessage = { _, participants, text, messageType ->
                chatViewModel.sendMessage(participants, text, messageType)
            },
            onSendMediaMessage = { _, participants, type, text, mediaDataUrl, mediaGifId, durationSec, disappearingSec, viewOnce ->
                chatViewModel.sendMessage(
                    participantUids = participants,
                    text = text,
                    messageType = type,
                    mediaDataUrl = mediaDataUrl,
                    mediaGifId = mediaGifId,
                    mediaDurationSec = durationSec,
                    disappearingSeconds = disappearingSec,
                    viewOnce = viewOnce
                )
            },
            onSendRichMessageWithReply = { _, participants, type, text, mediaDataUrl, mediaGifId, durationSec, disappearingSec, viewOnce, preparedVideo, replyTo ->
                chatViewModel.sendMessage(
                    participantUids = participants,
                    text = text,
                    messageType = type,
                    mediaDataUrl = mediaDataUrl,
                    mediaGifId = mediaGifId,
                    mediaDurationSec = durationSec,
                    disappearingSeconds = disappearingSec,
                    viewOnce = viewOnce,
                    preparedVideo = preparedVideo,
                    replyToMessageId = replyTo?.id,
                    replyToSenderId = replyTo?.senderId,
                    replyToText = replyTo?.text?.takeIf { it.isNotBlank() } ?: replyTo?.messageType,
                    replyToType = replyTo?.messageType
                )
            },
            onMarkMessagesRead = { _, ids -> chatViewModel.markMessagesAsRead(ids) },
            onMarkViewOnceOpened = { _, msgId -> chatViewModel.markViewOnceOpened(msgId) },
            onDeleteExpiredMessage = { _, msgId -> chatViewModel.deleteExpiredMessage(msgId) },
            onDeleteMessageForMe = { _, msgId -> chatViewModel.deleteMessageForMe(msgId) },
            onUnsendMessage = { _, msgId, isLast -> chatViewModel.unsendMessage(msgId, isLast) },
            onResolveVideoFile = { msg -> chatViewModel.resolveMessageVideoFile(context, msg) },
            onTypingChanged = { _, isTyping -> chatViewModel.setTypingStatus(isTyping) },
            onUpdateDisappearingTimer = { _, ttl -> chatViewModel.updateDisappearingTimer(ttl) },
            onStartCall = { _, calleeUid, participants, callType ->
                chatViewModel.startCall(calleeUid, participants, callType)
            },
            onAnswerCall = { chatViewModel.answerCall(it) },
            onDeclineCall = { chatViewModel.declineCall(it) },
            onToggleCallMute = { chatViewModel.toggleCallMute(it) },
            onToggleCallCamera = { chatViewModel.toggleCallCamera(it) },
            onSendCallAudioChunk = { session, seq, sr, b64 ->
                chatViewModel.sendCallAudioChunk(session, seq, sr, b64)
            },
            onSendCallVideoFrame = { session, seq, frameB64 ->
                chatViewModel.sendCallVideoFrame(session, seq, frameB64)
            },
            onEndCall = { session, duration -> chatViewModel.endCall(session, duration) },
            onUnmatch = { chatViewModel.unmatch(onComplete = onBack) },
            onBlockUser = onBlockUser,
            onReportUser = onReportUser,
            onRetry = { chatViewModel.retry() },
            onStatusMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUserId: String,
    thread: MatchThreadItem,
    messagesState: UiState<List<ChatMessage>>,
    partnerPresence: UserPresence? = null,
    myProfile: PublicProfile? = null,
    partnerTyping: TypingStatus? = null,
    activeCallSession: CallSession? = null,
    activeCallAudioChunks: List<CallAudioChunk> = emptyList(),
    activeCallVideoFrames: List<CallVideoFrame> = emptyList(),
    isUploadingMedia: Boolean = false,
    uploadProgress: Float? = null,
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onSendMessage: (matchId: String, participantUids: List<String>, text: String, messageType: String) -> Unit,
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
    onSendRichMessageWithReply: (
        (
            matchId: String,
            participantUids: List<String>,
            messageType: String,
            text: String,
            mediaDataUrl: String?,
            mediaGifId: String?,
            durationSec: Long,
            disappearingSec: Long,
            viewOnce: Boolean,
            preparedVideo: PreparedVideoUpload?,
            replyTo: ChatMessage?
        ) -> Unit
    )? = null,
    onMarkMessagesRead: (matchId: String, messageIds: List<String>) -> Unit = { _, _ -> },
    onMarkViewOnceOpened: (matchId: String, messageId: String) -> Unit = { _, _ -> },
    onDeleteExpiredMessage: (matchId: String, messageId: String) -> Unit = { _, _ -> },
    onDeleteMessageForMe: (matchId: String, messageId: String) -> Unit = { _, _ -> },
    onUnsendMessage: (matchId: String, messageId: String, isLastMessage: Boolean) -> Unit = { _, _, _ -> },
    onResolveVideoFile: (suspend (ChatMessage) -> Result<File>)? = null,
    onTypingChanged: (matchId: String, isTyping: Boolean) -> Unit = { _, _ -> },
    onUpdateDisappearingTimer: (matchId: String, ttlSeconds: Long) -> Unit = { _, _ -> },
    onStartCall: (matchId: String, calleeUid: String, participantUids: List<String>, callType: String) -> Unit = { _, _, _, _ -> },
    onAnswerCall: (CallSession) -> Unit = {},
    onDeclineCall: (CallSession) -> Unit = {},
    onToggleCallMute: (CallSession) -> Unit = {},
    onToggleCallCamera: (CallSession) -> Unit = {},
    onSendCallAudioChunk: (session: CallSession, seq: Long, sampleRateHz: Int, audioBase64: String) -> Unit = { _, _, _, _ -> },
    onSendCallVideoFrame: (session: CallSession, seq: Long, frameBase64: String) -> Unit = { _, _, _ -> },
    onEndCall: (CallSession, Long) -> Unit = { _, _ -> },
    onUnmatch: (matchId: String) -> Unit,
    onBlockUser: (partnerId: String, partnerName: String) -> Unit = { _, _ -> },
    onReportUser: (partnerId: String, partnerName: String, reason: String, details: String, alsoBlock: Boolean) -> Unit = { _, _, _, _, _ -> },
    onRetry: () -> Unit = {},
    onStatusMessage: (String) -> Unit = {},
    onOpenPartnerFullProfile: ((PublicProfile) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val partner = thread.partnerProfile
    val partnerName = partner?.displayName ?: "this user"
    val partnerFlag = partner?.flagEmoji().orEmpty()
    val matchId = thread.match.id
    val participants = thread.match.participantUids
    val disappearingTtl = thread.match.disappearingTtlSeconds

    var messageDraft by remember(matchId) { mutableStateOf("") }
    var replyingToMessage by remember(matchId) { mutableStateOf<ChatMessage?>(null) }
    var actionTargetMessage by remember(matchId) { mutableStateOf<ChatMessage?>(null) }
    var showMessageSearchBar by remember(matchId) { mutableStateOf(false) }
    var messageSearchQuery by remember(matchId) { mutableStateOf("") }
    var showUnmatchDialog by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showPartnerSheet by remember { mutableStateOf(false) }
    var showGifSheet by remember { mutableStateOf(false) }
    var showTimerSheet by remember { mutableStateOf(false) }
    var showVideoOptionsSheet by remember { mutableStateOf(false) }
    var pendingCaptureVideoUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessingVideo by remember { mutableStateOf(false) }
    var handledCallIds by remember(matchId) { mutableStateOf(emptySet<String>()) }
    var viewOnceNextMedia by remember(matchId) { mutableStateOf(false) }
    var viewingMediaMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var expiredCleanupsSent by remember(matchId) { mutableStateOf(emptySet<String>()) }

    var isRecordingVoice by remember(matchId) { mutableStateOf(false) }
    var recordingSeconds by remember(matchId) { mutableLongStateOf(0L) }
    var playingVoiceId by remember(matchId) { mutableStateOf<String?>(null) }

    val voiceRecorder = remember(context) { VoiceNoteRecorder(context.applicationContext) }
    val voicePlayer = remember(context) { VoiceNotePlayer(context.applicationContext) }

    DisposableEffect(matchId) {
        onDispose {
            voiceRecorder.stopSilently()
            voicePlayer.stop()
            onTypingChanged(matchId, false)
        }
    }

    var nowMillis by remember(matchId) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(matchId) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingSeconds = 0L
            while (isRecordingVoice) {
                delay(1000L)
                recordingSeconds += 1L
                if (recordingSeconds >= 25L) {
                    voiceRecorder.stopAndEncode().onSuccess { clip ->
                        isRecordingVoice = false
                        val activeReply = replyingToMessage
                        dispatchRichMessage(
                            matchId = matchId,
                            participants = participants,
                            type = "VOICE",
                            text = "",
                            mediaDataUrl = clip.dataUrl,
                            mediaGifId = null,
                            durationSec = clip.durationSec,
                            disappearingSec = disappearingTtl,
                            viewOnce = false,
                            preparedVideo = null,
                            replyTo = activeReply,
                            onSendRichMessageWithReply = onSendRichMessageWithReply,
                            onSendMediaMessage = onSendMediaMessage,
                            onSendMessage = onSendMessage
                        )
                        replyingToMessage = null
                    }.onFailure { err ->
                        isRecordingVoice = false
                        onStatusMessage(err.localizedMessage ?: "Recording stopped")
                    }
                    break
                }
            }
        }
    }

    // Broadcast typing status with automatic idle reset
    LaunchedEffect(messageDraft, matchId) {
        if (messageDraft.isNotBlank()) {
            onTypingChanged(matchId, true)
            delay(TYPING_IDLE_DEBOUNCE_MS)
            onTypingChanged(matchId, false)
        } else {
            onTypingChanged(matchId, false)
        }
    }

    val presenceState = remember(partnerPresence, nowMillis) {
        PresenceUtils.resolvePresenceDisplay(partnerPresence, nowMillis)
    }
    val partnerIsTyping = remember(partnerTyping, nowMillis) {
        val status = partnerTyping ?: return@remember false
        if (!status.isTyping) return@remember false
        val updatedAtMs = status.updatedAt?.toDate()?.time ?: nowMillis
        (nowMillis - updatedAtMs) <= TYPING_STALE_WINDOW_MS
    }

    // Mark unread messages as read automatically
    val rawMessages = (messagesState as? UiState.Success)?.data.orEmpty()
    val unreadIds = remember(rawMessages, currentUserId) {
        rawMessages.filter { msg ->
            msg.id.isNotBlank() &&
                msg.senderId != currentUserId &&
                !msg.isReadBy(currentUserId) &&
                !msg.viewOnce
        }.map { it.id }
    }
    LaunchedEffect(unreadIds) {
        if (unreadIds.isNotEmpty()) {
            onMarkMessagesRead(matchId, unreadIds)
        }
    }

    // Purge & hide expired disappearing messages + filter by search & delete-for-me
    val visibleMessages = remember(rawMessages, nowMillis, messageSearchQuery, currentUserId) {
        val q = messageSearchQuery.trim()
        rawMessages.filter { msg ->
            !msg.isExpiredAt(nowMillis) &&
                !msg.isDeletedFor(currentUserId) &&
                (q.isBlank() || msg.text.contains(q, ignoreCase = true))
        }
    }
    LaunchedEffect(rawMessages, nowMillis) {
        rawMessages.filter { msg ->
            msg.id.isNotBlank() && msg.isExpiredAt(nowMillis) && msg.id !in expiredCleanupsSent
        }.forEach { expired ->
            expiredCleanupsSent = expiredCleanupsSent + expired.id
            onDeleteExpiredMessage(matchId, expired.id)
        }
    }

    fun sendOrDispatch(
        type: String,
        text: String,
        mediaDataUrl: String? = null,
        mediaGifId: String? = null,
        durationSec: Long = 0L,
        viewOnce: Boolean = false,
        preparedVideo: PreparedVideoUpload? = null
    ) {
        val activeReply = replyingToMessage
        dispatchRichMessage(
            matchId = matchId,
            participants = participants,
            type = type,
            text = text,
            mediaDataUrl = mediaDataUrl,
            mediaGifId = mediaGifId,
            durationSec = durationSec,
            disappearingSec = disappearingTtl,
            viewOnce = viewOnce,
            preparedVideo = preparedVideo,
            replyTo = activeReply,
            onSendRichMessageWithReply = onSendRichMessageWithReply,
            onSendMediaMessage = onSendMediaMessage,
            onSendMessage = onSendMessage
        )
        replyingToMessage = null
    }

    fun downloadChatMedia(msg: ChatMessage) {
        scope.launch {
            onStatusMessage("Saving media to Downloads / Gallery…")
            if (msg.messageType == "PHOTO" && !msg.mediaDataUrl.isNullOrBlank()) {
                MediaDownloadHelper.saveImageToDevice(
                    context = context,
                    mediaDataUrl = msg.mediaDataUrl,
                    fileNamePrefix = "Kindred_${msg.id.take(8)}"
                ).onSuccess {
                    onStatusMessage("Photo saved to Pictures/Kindred")
                }.onFailure { err ->
                    onStatusMessage(err.localizedMessage ?: "Failed to save photo")
                }
            } else if (msg.messageType == "VIDEO") {
                val resolvedResult = if (onResolveVideoFile != null) {
                    onResolveVideoFile(msg)
                } else {
                    val file = VideoClipHelper.materializeVideoFile(context, msg.id, msg.mediaDataUrl)
                    if (file != null) Result.success(file) else Result.failure(IllegalStateException("Video unavailable"))
                }
                resolvedResult.onSuccess { file ->
                    MediaDownloadHelper.saveVideoFileToDevice(
                        context = context,
                        sourceFile = file,
                        fileNamePrefix = "Kindred_Video_${msg.id.take(8)}"
                    ).onSuccess {
                        onStatusMessage("Video saved to Movies/Kindred")
                    }.onFailure { err ->
                        onStatusMessage(err.localizedMessage ?: "Failed to save video")
                    }
                }.onFailure { err ->
                    onStatusMessage(err.localizedMessage ?: "Could not fetch video for download")
                }
            } else {
                onStatusMessage("No downloadable media on this message")
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && participants.size == 2) {
            scope.launch {
                val dataUrl = MediaImageHelper.compressUriToDataUrl(context, uri)
                if (dataUrl != null) {
                    val caption = messageDraft.trim()
                    sendOrDispatch(
                        type = "PHOTO",
                        text = caption,
                        mediaDataUrl = dataUrl,
                        viewOnce = viewOnceNextMedia
                    )
                    if (caption.isNotEmpty()) messageDraft = ""
                    viewOnceNextMedia = false
                } else {
                    onStatusMessage("Could not load selected photo.")
                }
            }
        }
    }

    fun processAndSendVideo(uri: Uri) {
        if (participants.size != 2 || isProcessingVideo) return
        isProcessingVideo = true
        scope.launch {
            val result = VideoClipHelper.prepareVideoForUpload(context, uri)
            isProcessingVideo = false
            result.onSuccess { clip ->
                val caption = messageDraft.trim()
                sendOrDispatch(
                    type = "VIDEO",
                    text = caption,
                    mediaDataUrl = clip.inlineDataUrl,
                    durationSec = clip.durationSec,
                    preparedVideo = clip
                )
                if (caption.isNotEmpty()) messageDraft = ""
            }.onFailure { err ->
                onStatusMessage(err.localizedMessage ?: "Unable to attach video.")
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            processAndSendVideo(uri)
        }
    }

    val captureUnlimitedVideoLauncher = rememberLauncherForActivityResult(
        contract = CaptureUnlimitedVideoContract()
    ) { saved ->
        val uri = pendingCaptureVideoUri
        pendingCaptureVideoUri = null
        if (saved && uri != null) {
            processAndSendVideo(uri)
        }
    }

    fun launchVideoCapture() {
        runCatching {
            val (_, uri) = VideoClipHelper.createCaptureVideoUri(context)
            pendingCaptureVideoUri = uri
            captureUnlimitedVideoLauncher.launch(uri)
        }.onFailure { err ->
            onStatusMessage(err.localizedMessage ?: "Camera video capture unavailable on this device.")
        }
    }

    val cameraVideoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchVideoCapture()
        } else {
            onStatusMessage("Camera permission is needed to record a video.")
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceRecorder.startRecording()
                .onSuccess { isRecordingVoice = true }
                .onFailure { onStatusMessage(it.localizedMessage ?: "Unable to record audio") }
        } else {
            onStatusMessage("Microphone permission is needed for voice notes.")
        }
    }

    val listState = rememberLazyListState()

    fun submitDraft() {
        val clean = messageDraft.trim()
        if (clean.isNotEmpty() && participants.size == 2) {
            sendOrDispatch(
                type = "TEXT",
                text = clean
            )
            messageDraft = ""
            onTypingChanged(matchId, false)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .testTag("chat_screen")
    ) {
        // Real-time Chat Header + Quick Safety/Timer Strip
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
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
                        Spacer(modifier = Modifier.width(6.dp))
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
                        Box(modifier = Modifier.size(44.dp)) {
                            ProfileAvatarImage(
                                photoDataUrl = partner?.photoDataUrl,
                                displayName = partner?.displayName ?: "Match",
                                seed = partner?.avatarSeed ?: thread.partnerId,
                                modifier = Modifier.size(42.dp),
                                initialsFontSizeSp = 16
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
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

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = buildString {
                                        if (partner != null) {
                                            append("${partner.displayName}, ${partner.age}")
                                            if (partnerFlag.isNotBlank()) append(" $partnerFlag")
                                        } else {
                                            append("Kindred Match")
                                        }
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                                            color = when {
                                                partnerIsTyping -> CoralRose
                                                presenceState.isOnline -> EmeraldMint
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            },
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = if (partnerIsTyping) stringResource(R.string.chat_typing_label) else presenceState.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (partnerIsTyping || presenceState.isOnline) FontWeight.SemiBold else FontWeight.Normal,
                                    color = when {
                                        partnerIsTyping -> CoralRose
                                        presenceState.isOnline -> EmeraldMint
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("chat_presence_status")
                                )
                                if (!partnerIsTyping && thread.distanceKm != null) {
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
                        onClick = { if (partner != null) showPartnerSheet = true },
                        enabled = partner != null,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chat_view_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = stringResource(R.string.chat_view_profile_cd),
                            tint = CoralRose
                        )
                    }

                    IconButton(
                        onClick = {
                            if (participants.size == 2 && thread.partnerId.isNotBlank()) {
                                onStartCall(matchId, thread.partnerId, participants, "VOICE")
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chat_voice_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = stringResource(R.string.chat_voice_call_cd),
                            tint = EmeraldMint
                        )
                    }

                    IconButton(
                        onClick = {
                            if (participants.size == 2 && thread.partnerId.isNotBlank()) {
                                onStartCall(matchId, thread.partnerId, participants, "VIDEO")
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chat_video_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = stringResource(R.string.chat_video_call_cd),
                            tint = CoralRose
                        )
                    }

                    IconButton(
                        onClick = { showUnmatchDialog = true },
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

                // Compact utility strip for profile/photos, disappearing timer, message search, and quick block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (partner != null) {
                        AssistChip(
                            onClick = { showPartnerSheet = true },
                            modifier = Modifier.testTag("chat_header_profile_chip"),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = stringResource(R.string.chat_view_profile_cd),
                                    tint = CoralRose,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Profile & Photos", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    AssistChip(
                        onClick = { showTimerSheet = true },
                        modifier = Modifier.testTag("chat_timer_button"),
                        leadingIcon = {
                            Icon(
                                imageVector = if (viewOnceNextMedia) Icons.Filled.Lock else Icons.Filled.Timer,
                                contentDescription = stringResource(R.string.chat_timer_cd),
                                tint = if (disappearingTtl > 0L || viewOnceNextMedia) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = when {
                                    viewOnceNextMedia && disappearingTtl > 0L -> "Timer ${formatTtlShort(disappearingTtl)} + View Once"
                                    viewOnceNextMedia -> "View-Once Photo On"
                                    disappearingTtl > 0L -> "Disappearing: ${formatTtlShort(disappearingTtl)}"
                                    else -> "Timer: Off"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (disappearingTtl > 0L || viewOnceNextMedia) {
                                ChampagneGold.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        )
                    )

                    AssistChip(
                        onClick = {
                            showMessageSearchBar = !showMessageSearchBar
                            if (!showMessageSearchBar) messageSearchQuery = ""
                        },
                        modifier = Modifier.testTag("chat_search_messages_button"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search messages",
                                tint = if (showMessageSearchBar) CoralRose else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Search", style = MaterialTheme.typography.labelSmall) }
                    )

                    AssistChip(
                        onClick = { showReportDialog = true },
                        modifier = Modifier.testTag("chat_report_button"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Flag,
                                contentDescription = stringResource(R.string.report_user_cd),
                                tint = ChampagneGold,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Report",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampagneGold
                            )
                        }
                    )

                    AssistChip(
                        onClick = { showBlockConfirm = true },
                        modifier = Modifier.testTag("chat_block_button"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Block,
                                contentDescription = stringResource(R.string.chat_block_cd),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Block",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }

                if (showMessageSearchBar) {
                    OutlinedTextField(
                        value = messageSearchQuery,
                        onValueChange = { messageSearchQuery = it },
                        placeholder = { Text("Search messages in conversation…") },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (messageSearchQuery.isNotEmpty()) {
                                        messageSearchQuery = ""
                                    } else {
                                        showMessageSearchBar = false
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("chat_message_search_input")
                    )
                }
            }
        }

        // Instant WhatsApp-style incoming call dialog when partner is ringing this user
        val incomingCall = activeCallSession?.takeIf {
            it.status == "RINGING" && it.calleeUid == currentUserId && it.id !in handledCallIds
        }
        if (incomingCall != null) {
            FullscreenIncomingCallDialog(
                session = incomingCall,
                partner = partner,
                onAccept = {
                    handledCallIds = handledCallIds + incomingCall.id
                    onAnswerCall(incomingCall)
                },
                onDecline = {
                    handledCallIds = handledCallIds + incomingCall.id
                    onDeclineCall(incomingCall)
                }
            )
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.widthIn(max = 400.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                }

                is UiState.Success -> {
                    LaunchedEffect(visibleMessages.size, partnerIsTyping) {
                        val lastIdx = visibleMessages.lastIndex + if (partnerIsTyping) 1 else 0
                        if (lastIdx >= 0) {
                            listState.animateScrollToItem(lastIdx)
                        }
                    }

                    if (visibleMessages.isEmpty() && !partnerIsTyping) {
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
                                    displayName = partner?.displayName ?: "Member",
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
                                    text = "Start a conversation with ${partner?.displayName ?: "this member"}. Send a message, GIF, photo, or voice note.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
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
                                items = visibleMessages,
                                key = { idx, msg -> msg.id.ifBlank { "msg_$idx" } }
                            ) { index, msg ->
                                val prev = visibleMessages.getOrNull(index - 1)
                                val next = visibleMessages.getOrNull(index + 1)

                                val msgDayKey = dayBucketLabel(msg.createdAt?.toDate(), dayFormatter)
                                val prevDayKey = prev?.let { dayBucketLabel(it.createdAt?.toDate(), dayFormatter) }
                                val showDayHeader = prev == null || msgDayKey != prevDayKey

                                val isMine = msg.senderId == currentUserId
                                val isSameSenderAsPrev = !showDayHeader && prev.senderId == msg.senderId
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
                                    partnerId = thread.partnerId,
                                    currentUserId = currentUserId,
                                    nowMillis = nowMillis,
                                    playingVoiceMessageId = playingVoiceId,
                                    onOpenMedia = { mediaMsg ->
                                        viewingMediaMessage = mediaMsg
                                        if (mediaMsg.viewOnce && mediaMsg.senderId != currentUserId && !mediaMsg.openedByRecipient) {
                                            onMarkViewOnceOpened(matchId, mediaMsg.id)
                                        }
                                    },
                                    onToggleVoicePlayback = { voiceMsg ->
                                        val dataUrl = voiceMsg.mediaDataUrl
                                        if (dataUrl.isNullOrBlank()) {
                                            onStatusMessage("Voice clip unavailable.")
                                        } else {
                                            val started = voicePlayer.playOrStop(
                                                messageId = voiceMsg.id,
                                                mediaDataUrl = dataUrl,
                                                onCompleted = { playingVoiceId = null },
                                                onError = { err ->
                                                    playingVoiceId = null
                                                    onStatusMessage(err)
                                                }
                                            )
                                            playingVoiceId = if (started) voiceMsg.id else null
                                        }
                                    },
                                    onMessageActions = { actionTargetMessage = it },
                                    onReplyMessage = { replyingToMessage = it },
                                    onDownloadMedia = { downloadChatMedia(it) }
                                )
                            }

                            if (partnerIsTyping) {
                                item(key = "partner_typing") {
                                    PartnerTypingBubble(
                                        partner = partner,
                                        partnerId = thread.partnerId
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Message Composer + Rich Media Actions
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 4.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (isUploadingMedia) {
                    val pct = ((uploadProgress ?: 0f).coerceIn(0f, 1f) * 100).toInt()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("chat_media_upload_progress"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (uploadProgress != null) {
                            LinearProgressIndicator(
                                progress = { uploadProgress.coerceIn(0f, 1f) },
                                color = CoralRose,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(
                                color = CoralRose,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text(
                            text = if (pct > 0) "Uploading media... $pct%" else "Uploading media...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isRecordingVoice) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(CoralRose.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("voice_recording_bar"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = CoralRose)
                            Text(
                                text = "Recording voice note • ${recordingSeconds}s / 25s",
                                style = MaterialTheme.typography.labelLarge,
                                color = CoralRose
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = {
                                    voiceRecorder.stopSilently()
                                    isRecordingVoice = false
                                }
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel voice note")
                            }
                            Surface(
                                shape = CircleShape,
                                color = CoralRose,
                                modifier = Modifier.size(44.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        voiceRecorder.stopAndEncode().onSuccess { clip ->
                                            isRecordingVoice = false
                                            sendOrDispatch(
                                                type = "VOICE",
                                                text = "",
                                                mediaDataUrl = clip.dataUrl,
                                                durationSec = clip.durationSec
                                            )
                                        }.onFailure { err ->
                                            isRecordingVoice = false
                                            onStatusMessage(err.localizedMessage ?: "Failed to save voice note")
                                        }
                                    },
                                    modifier = Modifier.testTag("chat_voice_send_button")
                                ) {
                                    Icon(Icons.Filled.Stop, contentDescription = "Stop and send voice note", tint = Color.White)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.chat_pick_photo_cd),
                                tint = if (viewOnceNextMedia) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { showVideoOptionsSheet = true },
                            enabled = !isProcessingVideo,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_video_button")
                        ) {
                            if (isProcessingVideo) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = CoralRose
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.VideoLibrary,
                                    contentDescription = stringResource(R.string.chat_pick_video_cd),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { showGifSheet = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_gif_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Gif,
                                contentDescription = stringResource(R.string.chat_pick_gif_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                if (VoicePermissionHelper.hasAudioPermission(context)) {
                                    voiceRecorder.startRecording()
                                        .onSuccess { isRecordingVoice = true }
                                        .onFailure { onStatusMessage(it.localizedMessage ?: "Unable to record audio") }
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_voice_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = stringResource(R.string.chat_record_voice_cd),
                                tint = CoralRose
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        if (viewOnceNextMedia) {
                            Surface(
                                shape = CircleShape,
                                color = ChampagneGold.copy(alpha = 0.2f),
                                modifier = Modifier.clickable { viewOnceNextMedia = false }
                            ) {
                                Text(
                                    text = "1x View-Once ×",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChampagneGold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                replyingToMessage?.let { replyTarget ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, CoralRose.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("reply_composer_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = null,
                                tint = CoralRose,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Replying to ${if (replyTarget.senderId == currentUserId) "yourself" else partnerName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CoralRose,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = replyTarget.text.ifBlank { replyTarget.messageType },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { replyingToMessage = null },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("clear_reply_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

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

                    val canSend = messageDraft.isNotBlank() && participants.size == 2
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

    // Active In-App Voice or Video Call Overlay
    val ongoingCall = activeCallSession?.takeIf {
        it.status == "ACTIVE" || (it.status == "RINGING" && it.callerUid == currentUserId)
    }
    if (ongoingCall != null) {
        InAppCallOverlay(
            session = ongoingCall,
            currentUserId = currentUserId,
            partner = partner,
            incomingAudioChunks = activeCallAudioChunks,
            incomingVideoFrames = activeCallVideoFrames,
            onSendAudioChunk = { seq, sampleRateHz, audioBase64 ->
                onSendCallAudioChunk(ongoingCall, seq, sampleRateHz, audioBase64)
            },
            onSendVideoFrame = { seq, frameBase64 ->
                onSendCallVideoFrame(ongoingCall, seq, frameBase64)
            },
            onToggleMute = { onToggleCallMute(ongoingCall) },
            onToggleCamera = { onToggleCallCamera(ongoingCall) },
            onEndCall = { duration -> onEndCall(ongoingCall, duration) }
        )
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
                onOpenFullProfile = onOpenPartnerFullProfile?.let { callback ->
                    {
                        showPartnerSheet = false
                        callback(partner)
                    }
                },
                onUnmatchClick = {
                    showPartnerSheet = false
                    showUnmatchDialog = true
                },
                onReportClick = {
                    showPartnerSheet = false
                    showReportDialog = true
                },
                onBlockClick = {
                    showPartnerSheet = false
                    showBlockConfirm = true
                }
            )
        }
    }

    if (showGifSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGifSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            GifPickerSheetContent(
                onSelectReaction = { reaction ->
                    showGifSheet = false
                    if (participants.size == 2) {
                        sendOrDispatch(
                            type = "GIF",
                            text = "",
                            mediaGifId = reaction.id
                        )
                    }
                },
                onSendCustomGifUrl = { url ->
                    showGifSheet = false
                    if (participants.size == 2) {
                        sendOrDispatch(
                            type = "GIF",
                            text = "",
                            mediaGifId = url
                        )
                    }
                }
            )
        }
    }

    if (showTimerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTimerSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            DisappearingTimerSheetContent(
                currentMatchTtlSeconds = disappearingTtl,
                viewOnceNextMedia = viewOnceNextMedia,
                onSelectMatchTtl = { ttl ->
                    onUpdateDisappearingTimer(matchId, ttl)
                    showTimerSheet = false
                },
                onToggleViewOnce = { enabled ->
                    viewOnceNextMedia = enabled
                }
            )
        }
    }

    viewingMediaMessage?.let { mediaMsg ->
        MediaViewerDialog(
            message = mediaMsg,
            resolveVideoFile = onResolveVideoFile,
            onDownloadMedia = { downloadChatMedia(it) },
            onDismiss = { viewingMediaMessage = null }
        )
    }

    actionTargetMessage?.let { targetMsg ->
        ModalBottomSheet(
            onDismissRequest = { actionTargetMessage = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .padding(bottom = 20.dp)
                    .testTag("message_actions_sheet"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Message Options",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (!targetMsg.unsent) {
                    FilledTonalButton(
                        onClick = {
                            replyingToMessage = targetMsg
                            actionTargetMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("action_reply_message")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reply to Message")
                    }
                }
                if (!targetMsg.unsent && !targetMsg.viewOnce && (targetMsg.messageType == "PHOTO" || targetMsg.messageType == "VIDEO")) {
                    FilledTonalButton(
                        onClick = {
                            actionTargetMessage = null
                            downloadChatMedia(targetMsg)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("action_download_media")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download to Device")
                    }
                }
                OutlinedButton(
                    onClick = {
                        onDeleteMessageForMe(matchId, targetMsg.id)
                        actionTargetMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("action_delete_for_me")
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete for Me")
                }
                if (targetMsg.senderId == currentUserId && !targetMsg.unsent) {
                    val isLast = rawMessages.lastOrNull()?.id == targetMsg.id
                    OutlinedButton(
                        onClick = {
                            onUnsendMessage(matchId, targetMsg.id, isLast)
                            actionTargetMessage = null
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("action_unsend_message")
                    ) {
                        Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unsend for Everyone")
                    }
                }
            }
        }
    }

    if (showUnmatchDialog) {
        UnmatchOrBlockDialog(
            partnerName = partnerName,
            onDismiss = { showUnmatchDialog = false },
            onUnmatchOnly = {
                showUnmatchDialog = false
                onUnmatch(matchId)
            },
            onUnmatchAndBlock = {
                showUnmatchDialog = false
                onBlockUser(thread.partnerId, partnerName)
            }
        )
    }

    if (showVideoOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVideoOptionsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp)
                    .testTag("short_video_options_sheet"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = CoralRose)
                    Text("Attach High-Res Video (Any Length)", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = "Share high-resolution videos of any length without size limits directly in chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = {
                        showVideoOptionsSheet = false
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("pick_short_video_option")
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick Video from Gallery")
                }
                OutlinedButton(
                    onClick = {
                        showVideoOptionsSheet = false
                        val hasCam = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCam) {
                            launchVideoCapture()
                        } else {
                            cameraVideoPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("record_short_video_option")
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Record Video with Camera")
                }
            }
        }
    }

    if (showReportDialog) {
        ReportUserDialog(
            targetName = partnerName,
            onDismiss = { showReportDialog = false },
            onSubmitReport = { reason, details, alsoBlock ->
                showReportDialog = false
                onReportUser(thread.partnerId, partnerName, reason, details, alsoBlock)
            }
        )
    }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text("Block $partnerName?") },
            text = {
                Text("This permanently closes this conversation, blocks future messages, and hides $partnerName across Kindred. You can manage blocked users in Settings.")
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

private fun dispatchRichMessage(
    matchId: String,
    participants: List<String>,
    type: String,
    text: String,
    mediaDataUrl: String?,
    mediaGifId: String?,
    durationSec: Long,
    disappearingSec: Long,
    viewOnce: Boolean,
    preparedVideo: PreparedVideoUpload? = null,
    replyTo: ChatMessage? = null,
    onSendRichMessageWithReply: (
        (
            String,
            List<String>,
            String,
            String,
            String?,
            String?,
            Long,
            Long,
            Boolean,
            PreparedVideoUpload?,
            ChatMessage?
        ) -> Unit
    )? = null,
    onSendMediaMessage: (
        (String, List<String>, String, String, String?, String?, Long, Long, Boolean) -> Unit
    )? = null,
    onSendMessage: (String, List<String>, String, String) -> Unit
) {
    if (onSendRichMessageWithReply != null) {
        onSendRichMessageWithReply(
            matchId,
            participants,
            type,
            text,
            mediaDataUrl,
            mediaGifId,
            durationSec,
            disappearingSec,
            viewOnce,
            preparedVideo,
            replyTo
        )
    } else if (onSendMediaMessage != null) {
        onSendMediaMessage(
            matchId,
            participants,
            type,
            text,
            mediaDataUrl,
            mediaGifId,
            durationSec,
            disappearingSec,
            viewOnce
        )
    } else {
        onSendMessage(matchId, participants, text.ifBlank { type }, type)
    }
}

private fun formatTtlShort(seconds: Long): String {
    return when {
        seconds >= 3600L -> "${seconds / 3600L}h"
        seconds >= 60L -> "${seconds / 60L}m"
        else -> "${seconds}s"
    }
}
