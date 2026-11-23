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
                } else if (room.isBouncedOut(currentUserId) && !caps.isAdmin) {
                    isLeavingVoluntarily = true
                    stopHardwareCapture()
                    liveKitManager.disconnect()
                    val left = room.remainingBounceLabel(currentUserId)
                    _roomExitSignal.tryEmit("You were bounced from this room and cannot enter for $left.")
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
    }    fun sendChatMessage(rawText: String, profile: UserProfile, isReaction: Boolean = false) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        val caps = _staffCapabilities.value
        if (!caps.isAdmin) {
            if (profile.globallyBanned) {
                _events.tryEmit("Your account is banned from sending messages.")
                return
            }
            if (profile.currentlySuspended()) {
                _events.tryEmit("You are suspended for ${profile.remainingSuspensionLabel()}.")
                return
            }
            if (room.mutedUserIds.contains(currentUserId)) {
                _events.tryEmit("You are currently muted in this room.")
                return
            }
            if (room.bannedUserIds.contains(currentUserId)) {
                _events.tryEmit("You are banned from this room.")
                return
            }
        }

        val isModOrStaff = room.hasModPrivileges(currentUserId, caps.tier)
        if (!isModOrStaff && room.slowModeSeconds > 0L) {
            val remaining = ModerationEngine.remainingSlowModeSeconds(
                lastSentEpochMs = lastMessageSentMs,
                slowModeSeconds = room.slowModeSeconds
            )
            if (remaining > 0) {
                _events.tryEmit("Slow mode active — wait ${remaining}s before sending.")
                startSlowModeCountdown(remaining)
                return
            }
        }

        val participants = (participantsState.value as? UiState.Success)?.data.orEmpty()
        val myRole = participants.firstOrNull { it.userId == currentUserId }?.roleEnum()
            ?: if (room.hostId == currentUserId) ParticipantRole.HOST
            else if (caps.isStaff) ParticipantRole.MODERATOR
            else ParticipantRole.SPEAKER

        if (isReaction) {
            viewModelScope.launch {
                repository.sendMessage(
                    roomId = roomId,
                    senderName = profile.displayName,
                    senderRole = myRole,
                    senderTier = caps.tier,
                    text = rawText,
                    messageType = MessageType.REACTION,
                    flaggedByAutoMod = false
                ).onFailure { err ->
                    _events.tryEmit(err.message ?: "Failed to send reaction")
                }
            }
            return
        }

        when (val eval = ModerationEngine.evaluateMessage(rawText, room)) {
            is AutoModEvaluation.Blocked -> {
                _events.tryEmit(eval.reason)
                if (eval.matchedTerms.isNotEmpty()) {
                    viewModelScope.launch {
                        repository.logAutoModTrigger(
                            roomId = roomId,
                            actorName = profile.displayName,
                            reason = eval.reason
                        )
                    }
                }
            }

            is AutoModEvaluation.Masked -> {
                lastMessageSentMs = System.currentTimeMillis()
                if (!isModOrStaff && room.slowModeSeconds > 0L) {
                    startSlowModeCountdown(room.slowModeSeconds.toInt())
                }
                viewModelScope.launch {
                    repository.sendMessage(
                        roomId = roomId,
                        senderName = profile.displayName,
                        senderRole = myRole,
                        senderTier = caps.tier,
                        text = eval.sanitizedText,
                        messageType = MessageType.TEXT,
                        flaggedByAutoMod = true
                    ).onSuccess {
                        _events.tryEmit("Auto-Mod masked restricted words in your message.")
                        repository.logAutoModTrigger(
                            roomId = roomId,
                            actorName = profile.displayName,
                            reason = "Auto-Mod masked terms: ${eval.matchedTerms.joinToString(", ")}"
                        )
                    }.onFailure { err ->
                        _events.tryEmit(err.message ?: "Could not send message")
                    }
                }
            }

            is AutoModEvaluation.Clean -> {
                lastMessageSentMs = System.currentTimeMillis()
                if (!isModOrStaff && room.slowModeSeconds > 0L) {
                    startSlowModeCountdown(room.slowModeSeconds.toInt())
                }
                viewModelScope.launch {
                    repository.sendMessage(
                        roomId = roomId,
                        senderName = profile.displayName,
                        senderRole = myRole,
                        senderTier = caps.tier,
                        text = eval.text,
                        messageType = MessageType.TEXT,
                        flaggedByAutoMod = false
                    ).onFailure { err ->
                        _events.tryEmit(err.message ?: "Could not send message")
                    }
                }
            }
        }
    }

    fun toggleMessageReaction(message: ChatMessage, emoji: String) {
        viewModelScope.launch {
            repository.toggleRoomMessageReaction(
                roomId = roomId,
                message = message,
                emoji = emoji
            ).onFailure { err ->
                _events.tryEmit(err.message ?: "Could not update reaction")
            }
        }
    }

    private fun startSlowModeCountdown(seconds: Int) {
        slowModeJob?.cancel()
        slowModeJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                _slowModeCountdown.value = remaining
                delay(1000L)
                remaining--
            }
            _slowModeCountdown.value = 0
        }
    }

    fun editRoomDetails(actorName: String, newTitle: String, newTopic: String) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.editRoomDetails(
                room = room,
                actorName = actorName,
                actorTier = _staffCapabilities.value.tier,
                newTitle = newTitle,
                newTopic = newTopic
            ).onSuccess {
                val trimmed = newTitle.trim()
                _events.tryEmit(if (trimmed.isEmpty()) "Room name deleted" else "Room renamed to \"$trimmed\"")
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Could not edit room")
            }
        }
    }

    fun updateSafetySettings(
        actorName: String,
        autoModEnabled: Boolean? = null,
        autoModBlockMode: Boolean? = null,
        linksBlocked: Boolean? = null,
        roomLocked: Boolean? = null,
        stageMicLocked: Boolean? = null,
        slowModeSeconds: Long? = null,
        videoEnabled: Boolean? = null,
        blockedWords: List<String>? = null,
        actionType: ModActionType = ModActionType.SETTINGS,
        reason: String = "Updated safety settings"
    ) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.updateRoomModerationSettings(
                room = room,
                actorName = actorName,
                actorTier = _staffCapabilities.value.tier,
                autoModEnabled = autoModEnabled ?: room.autoModEnabled,
                autoModBlockMode = autoModBlockMode ?: room.autoModBlockMode,
                linksBlocked = linksBlocked ?: room.linksBlocked,
                roomLocked = roomLocked ?: room.roomLocked,
                stageMicLocked = stageMicLocked ?: room.stageMicLocked,
                slowModeSeconds = slowModeSeconds ?: room.slowModeSeconds,
                videoEnabled = videoEnabled ?: room.videoEnabled,
                blockedWords = blockedWords ?: room.blockedWords,
                actionType = actionType,
                reason = reason
            ).onSuccess {
                _events.tryEmit(reason)
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Could not update room safety")
            }
        }
    }

    fun toggleMuteParticipant(
        targetUserId: String,
        targetName: String,
        actorName: String,
        mute: Boolean
    ) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.setParticipantMuted(
                room = room,
                targetUserId = targetUserId,
                targetName = targetName,
                actorName = actorName,
                actorTier = _staffCapabilities.value.tier,
                mute = mute,
                reason = if (mute) "Muted by $actorName" else "Unmuted by $actorName"
            ).onSuccess {
                _events.tryEmit(if (mute) "Muted $targetName" else "Unmuted $targetName")
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Action failed")
            }
        }
    }

    fun bounceUser(
        targetUserId: String,
        targetName: String,
        actorName: String,
        reason: String
    ) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.bounceParticipant(
                room = room,
                targetUserId = targetUserId,
                targetName = targetName,
                actorName = actorName,
                actorTier = _staffCapabilities.value.tier,
                reason = reason
            ).onSuccess {
                val msg = if (_staffCapabilities.value.isStaff) {
                    "Bounced $targetName from the room (locked out for 24 hours)"
                } else {
                    "Bounced $targetName from the room"
                }
                _events.tryEmit(msg)
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Could not bounce user")
            }
        }
    }

    fun suspendUser(
        targetUserId: String,
        targetName: String,
        actorName: String,
        durationMinutes: Long,
        reason: String
    ) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.suspendUserForMinutes(
                targetUserId = targetUserId,
                targetName = targetName,
                actorName = actorName,
                actorTier = _staffCapabilities.value.tier,
                durationMinutes = durationMinutes,
                reason = reason,
                room = room
            ).onSuccess {
                _events.tryEmit("Suspended $targetName for${durationMinutes}m")
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Could not suspend user")
            }
        }
    }


        fun sendAlertToUser(
        targetUserId: String,
        targetName: String,
        senderName: String,
        title: String,
        message: String,
        severity: AlertSeverity
    ) {
        viewModelScope.launch {
            repository.sendUserAlert(
                targetUserId = targetUserId,
                targetName = targetName,
                senderName = senderName,
                senderTier = _staffCapabilities.value.tier,
                roomId = roomId,
                title = title,
                message = message,
                severity = severity
            ).onSuccess {
                _events.tryEmit("Alert sent to $targetName")
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Could not send alert")
            }
        }
    }

    fun setStaffTierInRoom(
        targetUserId: String,
        targetName: String,
        tier: StaffTier,
        canEnterInvisible: Boolean,
        adminName: String
    ) {
        viewModelScope.launch {
            repository.setUserStaffRole(
                targetUserId = targetUserId,
                targetName = targetName,
                targetEmail = "",
                tier = tier,
                canEnterInvisible = tier == StaffTier.ADMIN || canEnterInvisible,
                adminName = adminName,
                roomId = roomId
            ).onSuccess {
                val msg = when (tier) {
                    StaffTier.ADMIN -> "$targetName promoted to App Administrator"
                    StaffTier.SUPPORT -> "$targetName promoted to Support Staff"
                    StaffTier.USER -> "Removed staff privileges from $targetName"
                }
                _events.tryEmit(msg)
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Could not update staff role")
            }
        }
    }

    fun fetchTargetUserProfile(targetUserId: String, onLoaded: (UserProfile?) -> Unit) {
        viewModelScope.launch {
            repository.fetchUserProfileForStaff(targetUserId)
                .onSuccess { onLoaded(it) }
                .onFailure { err ->
                    _events.tryEmit(err.message ?: "Could not load user profile")
                    onLoaded(null)
                }
        }
    }

    fun adminEditUserInRoom(
        targetUserId: String,
        displayName: String,
        handle: String,
        bio: String,
        globallyBanned: Boolean,
        disableCreateRoom: Boolean,
        disableEnterRoom: Boolean,
        disableMic: Boolean,
        disableVideo: Boolean,
        suspendedUntilMillis: Long,
        suspensionReason: String,
        actorName: String
    ) {
        viewModelScope.launch {
            repository.adminEditUserProfileAndRestrictions(
                targetUserId = targetUserId,
                displayName = displayName,
                handle = handle,
                bio = bio,
                globallyBanned = globallyBanned,
                disableCreateRoom = disableCreateRoom,
                disableEnterRoom = disableEnterRoom,
                disableMic = disableMic,
                disableVideo = disableVideo,
                suspendedUntilMillis = suspendedUntilMillis,
                suspensionReason = suspensionReason,
                actorName = actorName,
                roomId = roomId
            ).onSuccess {
                _events.tryEmit("Updated user $displayName")
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Could not edit user")
            }
        }
    }

    fun changeParticipantRole(
        targetUserId: String,
        targetName: String,
        actorName: String,
        newRole: ParticipantRole
    ) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.setParticipantRole(
                room = room,
                targetUserId = targetUserId,
                targetName = targetName,
                actorName = actorName,
                actorTier = _staffCapabilities.value.tier,
                newRole = newRole
            ).onSuccess {
                _events.tryEmit("$targetName is now ${newRole.label}")
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Could not update role")
            }
        }
    }

    fun kickOrBanUser(
        targetUserId: String,
        targetName: String,
        actorName: String,
        ban: Boolean,
        reason: String
    ) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.kickOrBanParticipant(
                room = room,
                targetUserId = targetUserId,
                targetName = targetName,
                actorName = actorName,
                actorTier = _staffCapabilities.value.tier,
                ban = ban,
                reason = reason
            ).onSuccess {
                _events.tryEmit(if (ban) "Banned $targetName" else "Kicked $targetName from room")
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Could not remove user")
            }
        }
    }

    fun unbanUser(targetUserId: String, actorName: String) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.unbanUser(room, targetUserId, actorName, _staffCapabilities.value.tier)
                .onSuccess { _events.tryEmit("User unbanned") }
                .onFailure { err -> _events.tryEmit(err.message ?: "Could not unban user") }
        }
    }

    fun redactMessage(message: ChatMessage, actorName: String) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.redactMessage(room, message, actorName, _staffCapabilities.value.tier)
                .onSuccess { _events.tryEmit("Message redacted") }
                .onFailure { err -> _events.tryEmit(err.message ?: "Could not redact message") }
        }
    }

    fun submitReport(
        reporterName: String,
        targetUserId: String,
        targetName: String,
        category: ReportCategory,
        details: String
    ) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.submitSafetyReport(
                room = room,
                reporterName = reporterName,
                targetUserId = targetUserId,
                targetName = targetName,
                category = category,
                details = details
            ).onSuccess {
                _events.tryEmit("Safety report submitted for review")
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Could not submit report")
            }
        }
    }

    fun blockUser(targetUserId: String, targetName: String, currentBlocked: List<String>) {
        viewModelScope.launch {
            repository.setUserBlocked(targetUserId, block = true, currentBlocked = currentBlocked)
                .onSuccess { _events.tryEmit("Blocked $targetName. Their messages are now hidden.") }
                .onFailure { err -> _events.tryEmit(err.message ?: "Could not block user") }
        }
    }

    fun closeRoomAsHostOrAdmin(actorName: String, onClosed: () -> Unit) {
        isLeavingVoluntarily = true
        viewModelScope.launch {
            withContext(NonCancellable) {
                stopHardwareCapture()
                liveKitManager.disconnect()
                releaseAdminMicPriorityIfHeld()
                val count = (roomState.value as? UiState.Success)?.data?.participantCount ?: 0L
                repository.leaveRoom(roomId, count, _isCurrentlyInvisible.value)
            }
            repository.closeRoom(roomId, actorName)
                .onSuccess { onClosed() }
                .onFailure { err -> _events.tryEmit(err.message ?: "Could not close room") }
        }
    }

    fun leaveRoomAndExit(onLeft: () -> Unit) {
        isLeavingVoluntarily = true
        viewModelScope.launch {
            withContext(NonCancellable) {
                stopHardwareCapture()
                liveKitManager.disconnect()
                releaseAdminMicPriorityIfHeld()
                val count = (roomState.value as? UiState.Success)?.data?.participantCount ?: 0L
                repository.leaveRoom(roomId, count, _isCurrentlyInvisible.value)
            }
            onLeft()
        }
    }

    private suspend fun releaseAdminMicPriorityIfHeld() {
        val roomHasPriority = (roomState.value as? UiState.Success)?.data?.adminMicPriorityActive == true
        if (adminPriorityClaimedByMe || (_staffCapabilities.value.isAdmin && roomHasPriority)) {
            adminPriorityClaimedByMe = false
            repository.setAdminMicPriority(roomId, false)
        }
    }

    private fun stopHardwareCapture() {
        levelSyncJob?.cancel()
        levelSyncJob = null
        voiceMeterEngine.stop()
        viewModelScope.launch {
            liveKitManager.setMicEnabled(false)
            liveKitManager.setCameraEnabled(false)
        }
        _isMicActive.value = false
        _isCamActive.value = false
    }

    override fun onCleared() {
        super.onCleared()
        stopHardwareCapture()
        liveKitManager.disconnect()
        slowModeJob?.cancel()
    }

    private companion object {
        const val TAG = "RoomViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}

