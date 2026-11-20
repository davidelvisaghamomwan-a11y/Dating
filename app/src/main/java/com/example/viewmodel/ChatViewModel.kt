package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiIcebreakerService
import com.example.ai.IcebreakerAiUiState
import com.example.data.KindredRepository
import com.example.model.CallAudioChunk
import com.example.model.CallSession
import com.example.model.ChatMessage
import com.example.model.PublicProfile
import com.example.model.TypingStatus
import com.example.model.UiState
import com.example.model.UserPresence
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Dedicated ViewModel for standalone [com.example.ui.screens.ChatScreen] invocations,
 * managing real-time Firestore messages, read receipts, typing indicators, partner presence,
 * call signaling, and AI icebreakers for a single match thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: KindredRepository,
    val matchId: String,
    val currentUserId: String,
    val partnerUserId: String = "",
    private val aiIcebreakerService: AiIcebreakerService = AiIcebreakerService()
) : ViewModel() {

    private val _retryNonce = MutableStateFlow(0)

    val messagesState: StateFlow<UiState<List<ChatMessage>>> = _retryNonce
        .flatMapLatest {
            if (currentUserId.isBlank() || matchId.isBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeMessages(matchId, currentUserId)
            }
        }
        .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing chat messages for match=$matchId", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load chat messages"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val partnerPresence: StateFlow<UserPresence?> = if (currentUserId.isBlank() || partnerUserId.isBlank()) {
        MutableStateFlow(null)
    } else {
        repository.observeUserPresence(partnerUserId, currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing presence for $partnerUserId", firestoreError ?: error)
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = null
            )
    }

    val partnerTyping: StateFlow<TypingStatus?> = if (currentUserId.isBlank() || partnerUserId.isBlank() || matchId.isBlank()) {
        MutableStateFlow(null)
    } else {
        repository.observePartnerTyping(matchId, partnerUserId, currentUserId)
            .catch { error ->
                if (error is CancellationException) throw error
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = null
            )
    }

    val activeCallSession: StateFlow<CallSession?> = if (currentUserId.isBlank() || matchId.isBlank()) {
        MutableStateFlow(null)
    } else {
        repository.observeMatchCalls(matchId, currentUserId)
            .map { list -> list.firstOrNull { it.status == "ACTIVE" || it.status == "RINGING" } }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = null
            )
    }

    val activeCallAudioChunks: StateFlow<List<CallAudioChunk>> = activeCallSession
        .flatMapLatest { session ->
            if (session == null || session.status != "ACTIVE") {
                flowOf(emptyList())
            } else {
                repository.observeCallAudioChunks(session.matchId, session.id, currentUserId)
                    .map { chunks -> chunks.filter { it.senderUid != currentUserId } }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    private val _icebreakerAiState = MutableStateFlow<IcebreakerAiUiState>(IcebreakerAiUiState.Idle)
    val icebreakerAiState: StateFlow<IcebreakerAiUiState> = _icebreakerAiState.asStateFlow()

    private val _statusError = MutableStateFlow<String?>(null)
    val statusError: StateFlow<String?> = _statusError.asStateFlow()

    fun retry() {
        _retryNonce.value += 1
    }

    fun clearError() {
        _statusError.value = null
    }

    fun sendMessage(
        participantUids: List<String>,
        text: String,
        messageType: String = "TEXT",
        mediaDataUrl: String? = null,
        mediaGifId: String? = null,
        mediaDurationSec: Long = 0L,
        disappearingSeconds: Long = 0L,
        viewOnce: Boolean = false
    ) {
        viewModelScope.launch {
            val result = repository.sendMessage(
                matchId = matchId,
                participantUids = participantUids,
                text = text,
                messageType = messageType,
                mediaDataUrl = mediaDataUrl,
                mediaGifId = mediaGifId,
                mediaDurationSec = mediaDurationSec,
                disappearingSeconds = disappearingSeconds,
                viewOnce = viewOnce
            )
            result.onFailure { err ->
                _statusError.value = err.localizedMessage ?: "Failed to send message"
            }
        }
    }

    fun markMessagesAsRead(messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            repository.markMessagesAsRead(matchId, messageIds)
        }
    }

    fun markViewOnceOpened(messageId: String) {
        viewModelScope.launch {
            repository.markViewOnceOpened(matchId, messageId)
        }
    }

    fun deleteExpiredMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(matchId, messageId)
        }
    }

    fun updateDisappearingTimer(ttlSeconds: Long) {
        viewModelScope.launch {
            repository.updateMatchDisappearingTimer(matchId, ttlSeconds)
        }
    }

    fun setTypingStatus(isTyping: Boolean) {
        viewModelScope.launch {
            repository.setTypingStatus(matchId, isTyping)
        }
    }

    fun generateAiIcebreakers(partnerProfile: PublicProfile?, tone: String) {
        if (_icebreakerAiState.value is IcebreakerAiUiState.Loading) return
        _icebreakerAiState.value = IcebreakerAiUiState.Loading
        viewModelScope.launch {
            aiIcebreakerService.generateAiOpeners(
                myProfile = null,
                partnerProfile = partnerProfile,
                tonePreference = tone
            ).onSuccess {
                _icebreakerAiState.value = IcebreakerAiUiState.Success(it)
            }.onFailure { err ->
                _icebreakerAiState.value = IcebreakerAiUiState.Error(
                    err.localizedMessage ?: "Failed to generate AI icebreakers"
                )
            }
        }
    }

    fun startCall(calleeUid: String, participantUids: List<String>, callType: String) {
        viewModelScope.launch {
            repository.startCall(matchId, calleeUid, participantUids, callType)
                .onFailure { err ->
                    _statusError.value = err.localizedMessage ?: "Unable to start call"
                }
        }
    }

    fun answerCall(session: CallSession) {
        viewModelScope.launch {
            repository.updateCallSession(matchId = session.matchId, callId = session.id, status = "ACTIVE")
        }
    }

    fun declineCall(session: CallSession) {
        viewModelScope.launch {
            repository.endCallAndLogMessage(
                matchId = session.matchId,
                callId = session.id,
                participantUids = session.participantUids,
                callType = session.callType,
                durationSeconds = 0L,
                declined = true
            )
        }
    }

    fun toggleCallMute(session: CallSession) {
        val isCaller = session.callerUid == currentUserId
        viewModelScope.launch {
            repository.updateCallSession(
                matchId = session.matchId,
                callId = session.id,
                callerMuted = if (isCaller) !session.callerMuted else null,
                calleeMuted = if (!isCaller) !session.calleeMuted else null
            )
        }
    }

    fun toggleCallCamera(session: CallSession) {
        val isCaller = session.callerUid == currentUserId
        viewModelScope.launch {
            repository.updateCallSession(
                matchId = session.matchId,
                callId = session.id,
                callerCameraOn = if (isCaller) !session.callerCameraOn else null,
                calleeCameraOn = if (!isCaller) !session.calleeCameraOn else null
            )
        }
    }

    fun endCall(session: CallSession, durationSeconds: Long) {
        viewModelScope.launch {
            repository.endCallAndLogMessage(
                matchId = session.matchId,
                callId = session.id,
                participantUids = session.participantUids,
                callType = session.callType,
                durationSeconds = durationSeconds,
                declined = false
            )
        }
    }

    fun sendCallAudioChunk(session: CallSession, seq: Long, sampleRateHz: Int, audioBase64: String) {
        viewModelScope.launch {
            repository.sendCallAudioChunk(
                matchId = session.matchId,
                callId = session.id,
                participantUids = session.participantUids,
                seq = seq,
                sampleRateHz = sampleRateHz,
                audioBase64 = audioBase64
            )
        }
    }

    fun unmatch(onComplete: () -> Unit) {
        viewModelScope.launch {
            val result = repository.unmatch(matchId)
            result.onSuccess {
                onComplete()
            }.onFailure { err ->
                _statusError.value = err.localizedMessage ?: "Failed to unmatch"
            }
        }
    }

    private companion object {
        const val TAG = "ChatViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
