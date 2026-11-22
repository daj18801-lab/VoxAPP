package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DirectMessage
import com.example.data.DirectThread
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.VoxStageRepository
import com.example.data.unwrapFirestoreError
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DirectChatViewModel(
    private val repository: VoxStageRepository,
    val currentUserId: String,
    val threadId: String
) : ViewModel() {

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    val threadState: StateFlow<UiState<DirectThread?>> = repository.observeDirectThread(threadId)
        .map<DirectThread?, UiState<DirectThread?>> { UiState.Success(it) }
        .catch { err ->
            val root = unwrapFirestoreError(err)
            Log.w(TAG, "Error observing direct thread $threadId", root)
            emit(UiState.Error(root.message ?: "Failed to open chat"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val messagesState: StateFlow<UiState<List<DirectMessage>>> = repository.observeDirectMessages(threadId)
        .map<List<DirectMessage>, UiState<List<DirectMessage>>> { UiState.Success(it) }
        .catch { err ->
            val root = unwrapFirestoreError(err)
            Log.w(TAG, "Error observing direct messages $threadId", root)
            emit(UiState.Error(root.message ?: "Failed to load messages"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    fun markReadIfNeeded(thread: DirectThread?) {
        if (thread == null || !thread.isUnreadFor(currentUserId)) return
        viewModelScope.launch {
            repository.markDirectThreadRead(thread)
        }
    }

    fun sendMessage(senderName: String, senderTier: StaffTier, text: String) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        val clean = text.trim()
        if (clean.isEmpty() || _isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            repository.sendDirectMessage(
                thread = thread,
                senderName = senderName,
                senderTier = senderTier,
                text = clean
            ).onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not send message")
            }
            _isSending.value = false
        }
    }

    private companion object {
        const val TAG = "DirectChatViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
