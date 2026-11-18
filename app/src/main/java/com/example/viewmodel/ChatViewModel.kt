package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.KindredRepository
import com.example.model.ChatMessage
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
 * managing real-time Firestore message and partner presence observation for a single match thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: KindredRepository,
    val matchId: String,
    val currentUserId: String,
    val partnerUserId: String = ""
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
        messageType: String = "TEXT"
    ) {
        viewModelScope.launch {
            val result = repository.sendMessage(
                matchId = matchId,
                participantUids = participantUids,
                text = text,
                messageType = messageType
            )
            result.onFailure { err ->
                _statusError.value = err.localizedMessage ?: "Failed to send message"
            }
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
