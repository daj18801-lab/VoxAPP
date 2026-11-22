package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AlertSeverity
import com.example.data.AutoModEvaluation
import com.example.data.ChatMessage
import com.example.data.ChatRoom
import com.example.data.MessageType
import com.example.data.ModActionType
import com.example.data.ModerationEngine
import com.example.data.ModerationLog
import com.example.data.ParticipantRole
import com.example.data.ReportCategory
import com.example.data.RoomParticipant
import com.example.data.StaffCapabilities
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.VoxStageRepository
import com.example.data.unwrapFirestoreError
import com.example.media.LiveKitRoomManager
import com.example.media.VoiceMeterEngine
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoomViewModel(
    context: Context,
    private val repository: VoxStageRepository,
    val currentUserId: String,
    val roomId: String,
    initialStaffCapabilities: StaffCapabilities
) : ViewModel() {

    val liveKitManager = LiveKitRoomManager(context.applicationContext)
    private val voiceMeterEngine = VoiceMeterEngine()
    val liveVoiceAmplitude: StateFlow<Float> = voiceMeterEngine.amplitude

    private val _staffCapabilities = MutableStateFlow(initialStaffCapabilities)
    val staffCapabilities: StateFlow<StaffCapabilities> = _staffCapabilities.asStateFlow()

    private val _isCurrentlyInvisible = MutableStateFlow(false)
    val isCurrentlyInvisible: StateFlow<Boolean> = _isCurrentlyInvisible.asStateFlow()

    private val _isMicActive = MutableStateFlow(false)
    val isMicActive: StateFlow<Boolean> = _isMicActive.asStateFlow()

    private val _isCamActive = MutableStateFlow(false)
    val isCamActive: StateFlow<Boolean> = _isCamActive.asStateFlow()

    private val _useFrontCamera = MutableStateFlow(true)
    val useFrontCamera: StateFlow<Boolean> = _useFrontCamera.asStateFlow()

    private val _isHandRaised = MutableStateFlow(false)
    val isHandRaised: StateFlow<Boolean> = _isHandRaised.asStateFlow()

    private val _pushToTalkMode = MutableStateFlow(true)
    val pushToTalkMode: StateFlow<Boolean> = _pushToTalkMode.asStateFlow()

    private val _slowModeCountdown = MutableStateFlow(0)
    val slowModeCountdown: StateFlow<Int> = _slowModeCountdown.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _roomExitSignal = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val roomExitSignal: SharedFlow<String> = _roomExitSignal.asSharedFlow()

    private var latestProfile: UserProfile? = null
    private var hasJoinedRoom = false
    private var observedSelfInParticipants = false
    private var isLeavingVoluntarily = false
    private var lastMessageSentMs = 0L
    private var adminPriorityClaimedByMe = false
    private var slowModeJob: Job? = null
    private var levelSyncJob: Job? = null

    val roomState: StateFlow<UiState<ChatRoom?>> = repository.observeRoom(roomId)
        .map<ChatRoom?, UiState<ChatRoom?>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing room $roomId", root)
            emit(UiState.Error(root.message ?: "Failed to load room"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val participantsState: StateFlow<UiState<List<RoomParticipant>>> =
        repository.observeParticipants(roomId)
            .map<List<RoomParticipant>, UiState<List<RoomParticipant>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing participants", root)
                emit(UiState.Error(root.message ?: "Failed to load participants"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val messagesState: StateFlow<UiState<List<ChatMessage>>> =
        repository.observeMessages(roomId)
            .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing messages", root)
                emit(UiState.Error(root.message ?: "Failed to load chat"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val modLogsState: StateFlow<UiState<List<ModerationLog>>> =
        repository.observeModLogs(roomId)
            .map<List<ModerationLog>, UiState<List<ModerationLog>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing mod logs", root)
                emit(UiState.Error(root.message ?: "Failed to load moderation logs"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    init {
        viewModelScope.launch {
            roomState.collectLatest { state ->
                val room = (state as? UiState.Success)?.data ?: return@collectLatest
                val caps = _staffCapabilities.value
                if (room.bannedUserIds.contains(currentUserId) && !caps.isAdmin) {
                    isLeavingVoluntarily = true
                    stopHardwareCapture()
                    liveKitManager.disconnect()
                    _roomExitSignal.tryEmit("You have been banned from this room.")
                } else if (room.status == "CLOSED") {
                    isLeavingVoluntarily = true
                    stopHardwareCapture()
                    liveKitManager.disconnect()
                    _roomExitSignal.tryEmit("This room was closed.")
                } else if (!caps.isAdmin && room.adminMicPriorityActive) {
                    if (_isMicActive.value) {
                        setMicLive(active = false)
                        _events.tryEmit("Main Administrator took exclusive microphone control.")
                    }
                } else if (
                    !caps.isAdmin && (
                        room.mutedUserIds.contains(currentUserId) ||
                            (room.stageMicLocked && !room.hasModPrivileges(currentUserId, caps.tier))
                        )
                ) {
                    if (_isMicActive.value) {
                        setMicLive(active = false)
                        _events.tryEmit("Your microphone was muted by room controls.")
                    }
                }
            }
        }

        viewModelScope.launch {
            participantsState.collectLatest { state ->
                val list = (state as? UiState.Success)?.data ?: return@collectLatest
                val selfDoc = list.firstOrNull { it.userId == currentUserId }
                if (selfDoc != null) {
                    observedSelfInParticipants = true
                    _isCurrentlyInvisible.value = selfDoc.invisible
                } else if (hasJoinedRoom && observedSelfInParticipants && !isLeavingVoluntarily) {
                    isLeavingVoluntarily = true
                    stopHardwareCapture()
                    liveKitManager.disconnect()
                    _roomExitSignal.tryEmit("You were bounced or removed from this room.")
                }
            }
        }
    }

    fun updateStaffCapabilities(caps: StaffCapabilities) {
        _staffCapabilities.value = caps
    }

    fun onUserProfileUpdated(profile: UserProfile) {
        latestProfile = profile
        if (_staffCapabilities.value.isAdmin) return

        if (profile.globallyBanned && !isLeavingVoluntarily) {
            isLeavingVoluntarily = true
            stopHardwareCapture()
            liveKitManager.disconnect()
            _roomExitSignal.tryEmit("Your account was banned by an Administrator.")
            return
        }
        if (profile.currentlySuspended() && !isLeavingVoluntarily) {
            isLeavingVoluntarily = true
            stopHardwareCapture()
            liveKitManager.disconnect()
            val left = profile.remainingSuspensionLabel()
            _roomExitSignal.tryEmit("You have been suspended for $left.")
            return
        }
        if (profile.disableEnterRoom && !isLeavingVoluntarily) {
            isLeavingVoluntarily = true
            stopHardwareCapture()
            liveKitManager.disconnect()
            _roomExitSignal.tryEmit("Room access was disabled on your account by an Administrator.")
            return
        }
        if (profile.disableMic && _isMicActive.value) {
            setMicLive(false)
            _events.tryEmit("Your microphone was disabled by an Administrator.")
        }
        if (profile.disableVideo && _isCamActive.value) {
            setCameraLive(false)
            _events.tryEmit("Your video streaming was disabled by an Administrator.")
        }
    }

    fun ensureJoined(room: ChatRoom, profile: UserProfile) {
        latestProfile = profile
        if (hasJoinedRoom) return
        hasJoinedRoom = true
        _pushToTalkMode.value = profile.pushToTalkPreferred
        val caps = _staffCapabilities.value
        val wantInvisible = profile.invisibleModePreferred && caps.effectiveCanEnterInvisible

        viewModelScope.launch {
            repository.joinRoom(
                room = room,
                profile = profile,
                staffCapabilities = caps,
                enterInvisible = wantInvisible
            ).onSuccess { enteredInvisibly ->
                _isCurrentlyInvisible.value = enteredInvisibly
                liveKitManager.connectToRoom(
                    roomName = roomId,
                    participantIdentity = currentUserId,
                    participantName = profile.displayName.ifBlank { "Member" }
                ).onFailure { lkErr ->
                    Log.w(TAG, "LiveKit connection failed", lkErr)
                    _events.tryEmit("Audio/video server error: ${lkErr.message}")
                }
                if (enteredInvisibly) {
                    _events.tryEmit("Entered room invisibly — nobody can see you.")
                }
            }.onFailure { err ->
                hasJoinedRoom = false
                isLeavingVoluntarily = true
                _roomExitSignal.tryEmit(err.message ?: "Unable to join this room.")
            }
        }
    }

    fun toggleInRoomInvisible() {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        val caps = _staffCapabilities.value
        val next = !_isCurrentlyInvisible.value
        if (next && !caps.effectiveCanEnterInvisible) {
            _events.tryEmit("Admin must allow invisible mode on your Support account first.")
            return
        }
        viewModelScope.launch {
            if (next) {
                if (caps.isAdmin) {
                    _isCamActive.value = false
                    _isHandRaised.value = false
                    liveKitManager.setCameraEnabled(false)
                } else {
                    stopHardwareCapture()
                }
            }
            repository.toggleInvisibleInRoom(room, next, caps)
                .onSuccess { nowInvisible ->
                    _isCurrentlyInvisible.value = nowInvisible
                    _events.tryEmit(
                        when {
                            nowInvisible && caps.isAdmin ->
                                "Invisible Mode ON — hidden from room (you can still use the mic anytime)"
                            nowInvisible ->
                                "Invisible Mode ON — hidden from room (switch to Visible to use mic)"
                            else ->
                                "Visible Mode ON — you are now visible in the room"
                        }
                    )
                }
                .onFailure { err ->
                    _events.tryEmit(err.message ?: "Could not switch visibility")
                }
        }
    }

    fun togglePushToTalkMode() {
        val next = !_pushToTalkMode.value
        _pushToTalkMode.value = next
        if (next && _isMicActive.value) {
            setMicLive(false)
        }
    }

    fun setMicLive(active: Boolean) {
        val room = (roomState.value as? UiState.Success)?.data
        val caps = _staffCapabilities.value
        val profile = latestProfile

        if (active) {
            if (_isCurrentlyInvisible.value && !caps.isAdmin) {
                _events.tryEmit("Support staff must be visible in the room before using the microphone.")
                return
            }
            if (!caps.isAdmin && profile?.disableMic == true) {
                _events.tryEmit("Microphone access has been disabled on your account by an Administrator.")
                return
            }
            if (room != null && !caps.isAdmin) {
                if (room.adminMicPriorityActive) {
                    _events.tryEmit("Main Administrator is on the microphone — nobody else can use the mic.")
                    return
                }
                if (room.mutedUserIds.contains(currentUserId)) {
                    _events.tryEmit("You are muted by a room moderator.")
                    return
                }
                if (room.stageMicLocked && !room.hasModPrivileges(currentUserId, caps.tier)) {
                    _events.tryEmit("Stage microphones are currently locked by moderators.")
                    return
                }
            }
        }

        _isMicActive.value = active
        viewModelScope.launch {
            liveKitManager.setMicEnabled(active)
        }
        if (caps.isAdmin) {
            adminPriorityClaimedByMe = active
            viewModelScope.launch {
                repository.setAdminMicPriority(roomId, active)
            }
            if (active) {
                _events.tryEmit(
                    if (_isCurrentlyInvisible.value) {
                        "Admin Mic LIVE (Invisible) — exclusive mic priority active"
                    } else {
                        "Exclusive Admin Mic ON — all other microphones locked"
                    }
                )
            }
        }
        if (active) {
            voiceMeterEngine.start(viewModelScope)
            startLevelSyncLoop()
        } else {
            levelSyncJob?.cancel()
            levelSyncJob = null
            voiceMeterEngine.stop()
            syncMediaStateNow(0.0)
        }
    }

    fun setCameraLive(active: Boolean) {
        val room = (roomState.value as? UiState.Success)?.data
        val caps = _staffCapabilities.value
        val profile = latestProfile

        if (active) {
            if (_isCurrentlyInvisible.value) {
                _events.tryEmit("Switch to Visible Mode before starting video.")
                return
            }
            if (!caps.isAdmin && profile?.disableVideo == true) {
                _events.tryEmit("Video streaming has been disabled on your account by an Administrator.")
                return
            }
            if (room != null && !room.videoEnabled) {
                _events.tryEmit("Video streaming is disabled by room settings.")
                return
            }
        }
        _isCamActive.value = active
        viewModelScope.launch {
            liveKitManager.setCameraEnabled(active)
        }
        syncMediaStateNow(if (_isMicActive.value) liveVoiceAmplitude.value.toDouble() else 0.0)
    }

    fun flipCamera() {
        val nextFront = !_useFrontCamera.value
        _useFrontCamera.value = nextFront
        val videoTrack = liveKitManager.room.localParticipant
            .getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
        videoTrack?.switchCamera(position = if (nextFront) CameraPosition.FRONT else CameraPosition.BACK)
    }

    fun toggleRaiseHand() {
        if (_isCurrentlyInvisible.value) {
            _events.tryEmit("You are currently invisible.")
            return
        }
        _isHandRaised.value = !_isHandRaised.value
        syncMediaStateNow(if (_isMicActive.value) liveVoiceAmplitude.value.toDouble() else 0.0)
    }

    private fun startLevelSyncLoop() {
        levelSyncJob?.cancel()
        levelSyncJob = viewModelScope.launch {
            syncMediaStateNow(0.15)
            while (_isMicActive.value) {
                delay(500L)
                syncMediaStateNow(liveVoiceAmplitude.value.toDouble())
            }
        }
    }

    private fun syncMediaStateNow(level: Double) {
        viewModelScope.launch {
            repository.updateSelfMediaState(
                roomId = roomId,
                micActive = _isMicActive.value,
                camActive = _isCamActive.value,
                handRaised = _isHandRaised.value,
                speakingLevel = level
            )
        }
    }
