package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DirectMessage
import com.example.data.DirectThread
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.VoxStageRepository
import com.example.data.unwrapFirestoreError
import com.example.media.LiveKitRoomManager
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
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
    context: Context,
    private val repository: VoxStageRepository,
    val currentUserId: String,
    val threadId: String
) : ViewModel() {

    val liveKitManager = LiveKitRoomManager(context.applicationContext)

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isInCall = MutableStateFlow(false)
    val isInCall: StateFlow<Boolean> = _isInCall.asStateFlow()

    private val _isConnectingCall = MutableStateFlow(false)
    val isConnectingCall: StateFlow<Boolean> = _isConnectingCall.asStateFlow()

    private val _isMicActive = MutableStateFlow(false)
    val isMicActive: StateFlow<Boolean> = _isMicActive.asStateFlow()

    private val _isCamActive = MutableStateFlow(false)
    val isCamActive: StateFlow<Boolean> = _isCamActive.asStateFlow()

    private val _useFrontCamera = MutableStateFlow(true)
    val useFrontCamera: StateFlow<Boolean> = _useFrontCamera.asStateFlow()

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

    fun sendMessage(
        senderName: String,
        senderTier: StaffTier,
        text: String,
        imageBase64: String = ""
    ) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        val clean = text.trim()
        val cleanImage = imageBase64.trim()
        if ((clean.isEmpty() && cleanImage.isEmpty()) || _isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            repository.sendDirectMessage(
                thread = thread,
                senderName = senderName,
                senderTier = senderTier,
                text = clean,
                imageBase64 = cleanImage
            ).onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not send message")
            }
            _isSending.value = false
        }
    }

    private suspend fun ensureCallConnected(myName: String): Boolean {
        if (liveKitManager.isConnected.value) {
            _isInCall.value = true
            return true
        }
        _isConnectingCall.value = true
        val res = liveKitManager.connectToRoom(
            roomName = "dm_$threadId",
            participantIdentity = currentUserId,
            participantName = myName.ifBlank { "Member" }
        )
        _isConnectingCall.value = false
        return res.fold(
            onSuccess = {
                _isInCall.value = true
                true
            },
            onFailure = { err ->
                Log.w(TAG, "Private call LiveKit connection failed", err)
                _snackbarEvents.tryEmit("Call connection failed: ${err.message}")
                false
            }
        )
    }

    fun toggleCallMic(myName: String) {
        if (_isConnectingCall.value) return
        val wantActive = !_isMicActive.value
        viewModelScope.launch {
            if (wantActive && !ensureCallConnected(myName)) return@launch
            _isMicActive.value = wantActive
            liveKitManager.setMicEnabled(wantActive)
            if (wantActive) {
                _snackbarEvents.tryEmit("Private voice mic is LIVE")
            }
        }
    }

    fun toggleCallVideo(myName: String) {
        if (_isConnectingCall.value) return
        val wantActive = !_isCamActive.value
        viewModelScope.launch {
            if (wantActive && !ensureCallConnected(myName)) return@launch
            _isCamActive.value = wantActive
            liveKitManager.setCameraEnabled(wantActive)
            if (wantActive) {
                _snackbarEvents.tryEmit("Private video camera is LIVE")
            }
        }
    }

    fun flipCamera() {
        val nextFront = !_useFrontCamera.value
        _useFrontCamera.value = nextFront
        val videoTrack = liveKitManager.room.localParticipant
            .getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
        videoTrack?.switchCamera(position = if (nextFront) CameraPosition.FRONT else CameraPosition.BACK)
    }

    fun endCall() {
        viewModelScope.launch {
            liveKitManager.setMicEnabled(false)
            liveKitManager.setCameraEnabled(false)
            liveKitManager.disconnect()
            _isMicActive.value = false
            _isCamActive.value = false
            _isInCall.value = false
            _snackbarEvents.tryEmit("Private call ended")
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveKitManager.disconnect()
    }

    private companion object {
        const val TAG = "DirectChatViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
