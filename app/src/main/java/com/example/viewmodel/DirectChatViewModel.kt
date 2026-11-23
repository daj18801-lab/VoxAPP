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
import com.example.media.LivePttSessionManager
import com.example.media.PrivateCallForegroundService
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

    private val appContext = context.applicationContext
    val liveKitManager = LiveKitRoomManager(appContext)

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

    init {
        viewModelScope.launch {
            PrivateCallForegroundService.endCallEvents.collect { endedThreadId ->
                if (endedThreadId == threadId && _isInCall.value) {
                    endCall()
                }
            }
        }
    }

    private val purgedMessageIds = mutableSetOf<String>()

    fun markReadIfNeeded(thread: DirectThread?, sendReadReceipt: Boolean = true) {
        if (thread == null) return
        val needsClearUnread = thread.isUnreadFor(currentUserId)
        val allowReceipt = sendReadReceipt && thread.isReadReceiptsEnabledFor(currentUserId)
        val threadUpdatedMs = thread.updatedAt?.toDate()?.time ?: 0L
        val myLastReadMs = thread.lastReadMillisFor(currentUserId)
        val needsReceipt = allowReceipt &&
            thread.lastMessageSenderId.isNotBlank() &&
            thread.lastMessageSenderId != currentUserId &&
            (needsClearUnread || myLastReadMs < threadUpdatedMs)
        if (!needsClearUnread && !needsReceipt) return
        viewModelScope.launch {
            repository.markDirectThreadRead(thread, sendReadReceipt = sendReadReceipt)
        }
    }

    fun setDisappearingDuration(durationSec: Long) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.setDirectThreadDisappearingDuration(thread, durationSec)
                .onSuccess {
                    val label = DirectThread.formatDisappearingDuration(durationSec)
                    val msg = if (durationSec > 0L) {
                        "Disappearing messages set to $label for new messages."
                    } else {
                        "Disappearing messages turned off."
                    }
                    _snackbarEvents.tryEmit(msg)
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update disappearing timer")
                }
        }
    }

    fun setReadReceiptsEnabled(enabled: Boolean) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.setDirectThreadReadReceiptsEnabled(thread, enabled)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (enabled) "Read receipts enabled for this chat."
                        else "Read receipts hidden for this chat."
                    )
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update read receipts")
                }
        }
    }

    fun purgeExpiredSentMessages(messages: List<DirectMessage>, nowMs: Long) {
        val expiredMine = messages.filter { msg ->
            msg.senderId == currentUserId &&
                msg.isExpired(nowMs) &&
                msg.id.isNotBlank() &&
                purgedMessageIds.add(msg.id)
        }
        if (expiredMine.isEmpty()) return
        viewModelScope.launch {
            expiredMine.forEach { msg ->
                repository.deleteDirectMessage(threadId, msg.id)
            }
        }
    }

    fun sendMessage(
        senderName: String,
        senderTier: StaffTier,
        text: String,
        imageBase64: String = "",
        audioBase64: String = "",
        audioDurationSec: Long = 0L
    ) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        val clean = text.trim()
        val cleanImage = imageBase64.trim()
        val cleanAudio = audioBase64.trim()
        if ((clean.isEmpty() && cleanImage.isEmpty() && cleanAudio.isEmpty()) || _isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            repository.sendDirectMessage(
                thread = thread,
                senderName = senderName,
                senderTier = senderTier,
                text = clean,
                imageBase64 = cleanImage,
                audioBase64 = cleanAudio,
                audioDurationSec = audioDurationSec
            ).onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not send message")
            }
            _isSending.value = false
        }
    }

    private fun currentPeerTitle(): String {
        val thread = (threadState.value as? UiState.Success)?.data
        return thread?.displayTitle(currentUserId)?.ifBlank { "Private Chat" } ?: "Private Chat"
    }

    private suspend fun ensureCallConnected(myName: String): Boolean {
        if (liveKitManager.isConnected.value) {
            _isInCall.value = true
            PrivateCallForegroundService.start(appContext, threadId, currentPeerTitle())
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
                PrivateCallForegroundService.start(appContext, threadId, currentPeerTitle())
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
            if (wantActive && LivePttSessionManager.activeThreadId.value == threadId) {
                LivePttSessionManager.disconnect(appContext)
            }
            if (wantActive && !ensureCallConnected(myName)) return@launch
            _isMicActive.value = wantActive
            liveKitManager.setMicEnabled(wantActive)
            if (wantActive) {
                PrivateCallForegroundService.start(appContext, threadId, currentPeerTitle())
                _snackbarEvents.tryEmit("Private voice mic is LIVE (continues in background)")
            }
        }
    }

    fun setPttEnabled(enabled: Boolean) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        val peerName = thread.displayTitle(currentUserId).ifBlank { "this chat" }
        viewModelScope.launch {
            repository.setDirectThreadPttEnabled(thread, enabled)
                .onSuccess {
                    if (!enabled && LivePttSessionManager.activeThreadId.value == threadId) {
                        LivePttSessionManager.disconnect(appContext)
                    }
                    val otherAlreadyEnabled = thread.isOtherPttEnabled(currentUserId)
                    val msg = when {
                        enabled && otherAlreadyEnabled ->
                            "Live Push-to-Talk CONNECTED with $peerName! Hold the button to stream live audio."
                        enabled ->
                            "Live Push-to-Talk enabled — waiting for $peerName to also turn on PTT."
                        else ->
                            "Live Push-to-Talk turned off for $peerName."
                    }
                    _snackbarEvents.tryEmit(msg)
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update Push-to-Talk")
                }
        }
    }

    fun setPttLiveMic(myName: String, speaking: Boolean) {
        if (_isConnectingCall.value) return
        viewModelScope.launch {
            if (speaking && !ensureCallConnected(myName)) return@launch
            _isMicActive.value = speaking
            liveKitManager.setMicEnabled(speaking)
            if (speaking) {
                PrivateCallForegroundService.start(appContext, threadId, currentPeerTitle())
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
            PrivateCallForegroundService.stop(appContext)
            _snackbarEvents.tryEmit("Private call ended")
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (!PrivateCallForegroundService.isPttMode && PrivateCallForegroundService.activeThreadId == threadId) {
            PrivateCallForegroundService.stop(appContext)
        }
        liveKitManager.disconnect()
    }

    private companion object {
        const val TAG = "DirectChatViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
