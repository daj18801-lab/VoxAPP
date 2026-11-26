package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DirectMessage
import com.example.data.DirectThread
import com.example.data.EmployeeRequestCategory
import com.example.data.ReportCategory
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.VoxStageRepository
import com.example.data.WorkOrderPriority
import com.example.data.WorkOrderType
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
        val needsAckChirp = thread.hasActivePriorityChirpFor(currentUserId)
        if (!needsClearUnread && !needsReceipt && !needsAckChirp) return
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
        audioDurationSec: Long = 0L,
        hideSenderName: Boolean = false
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
                audioDurationSec = audioDurationSec,
                hideSenderName = hideSenderName
            ).onSuccess {
                if (cleanAudio.isNotEmpty()) {
                    repository.completeReferralBonusOnFirstPttClip(senderName)
                        .onSuccess { award ->
                            if (award != null) {
                                _snackbarEvents.tryEmit(
                                    "Invite bonus unlocked! You and ${award.inviterName} each earned +${award.bonusPointsEach} points!"
                                )
                            }
                        }
                }
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not send message")
            }
            _isSending.value = false
        }
    }

    fun toggleMessageReaction(message: DirectMessage, emoji: String) {
        if (message.id.isBlank()) return
        viewModelScope.launch {
            repository.toggleDirectMessageReaction(
                threadId = threadId,
                messageId = message.id,
                currentReactions = message.reactionsByUserId,
                emoji = emoji
            ).onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update reaction")
            }
        }
    }

    fun sendScreenshotNotice(userName: String, senderTier: StaffTier) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        val safeName = userName.trim().ifBlank { "Someone" }
        viewModelScope.launch {
            repository.sendDirectMessage(
                thread = thread,
                senderName = safeName,
                senderTier = senderTier,
                text = "$safeName took a screenshot of this chat",
                messageType = DirectMessage.TYPE_SCREENSHOT
            ).onSuccess {
                _snackbarEvents.tryEmit("Screenshot detected — the other user was notified.")
            }
        }
    }

    fun reportMessage(
        reporterName: String,
        message: DirectMessage,
        category: ReportCategory,
        details: String,
        onReported: () -> Unit = {}
    ) {
        val peerTitle = currentPeerTitle()
        viewModelScope.launch {
            repository.reportDirectMessage(
                threadId = threadId,
                peerTitle = peerTitle,
                reporterName = reporterName,
                message = message,
                category = category,
                extraDetails = details
            ).onSuccess {
                _snackbarEvents.tryEmit("Message reported to Admin & Support Safety Center.")
                onReported()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not submit message report")
            }
        }
    }

    fun pinMessage(message: DirectMessage, pinnedByName: String) {
        if (message.id.isBlank() || message.isSystemNotice()) return
        viewModelScope.launch {
            repository.pinDirectMessage(
                threadId = threadId,
                message = message,
                pinnedByName = pinnedByName
            ).onSuccess {
                _snackbarEvents.tryEmit("Message pinned to the top of this chat.")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not pin message")
            }
        }
    }

    fun unpinMessage() {
        viewModelScope.launch {
            repository.unpinDirectMessage(threadId)
                .onSuccess {
                    _snackbarEvents.tryEmit("Pinned message removed.")
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not unpin message")
                }
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
        val thread = (threadState.value as? UiState.Success)?.data
        val wantActive = !_isMicActive.value
        val isGroupAdminHere = thread?.isGroupAdmin(currentUserId) == true
        if (wantActive && thread != null && thread.isBlockedByGroupAdminTalking(currentUserId)) {
            _snackbarEvents.tryEmit("Group DM Admin has priority mic — please wait until they finish speaking.")
            return
        }
        viewModelScope.launch {
            if (wantActive) {
                LivePttSessionManager.suppressForPrivateCall(appContext, threadId)
            }
            if (wantActive && !ensureCallConnected(myName)) return@launch
            _isMicActive.value = wantActive
            liveKitManager.setMicEnabled(wantActive)
            if (isGroupAdminHere) {
                repository.setGroupDmAdminTalking(threadId, wantActive)
            }
            if (wantActive) {
                PrivateCallForegroundService.start(appContext, threadId, currentPeerTitle())
                _snackbarEvents.tryEmit(
                    if (isGroupAdminHere) "Group DM Admin Priority Mic LIVE — you can talk over anyone"
                    else "Private voice mic is LIVE (continues in background)"
                )
            }
        }
    }

    fun setPttEnabled(enabled: Boolean, isAdmin: Boolean = false) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        val peerName = thread.displayTitle(currentUserId).ifBlank { "this chat" }
        viewModelScope.launch {
            repository.setDirectThreadPttEnabled(
                thread = thread,
                enabled = enabled,
                autoEnableAllParticipants = isAdmin
            )
                .onSuccess {
                    if (!enabled && LivePttSessionManager.activeThreadId.value == threadId) {
                        LivePttSessionManager.disconnect(appContext)
                    }
                    val otherAlreadyEnabled = thread.isOtherPttEnabled(currentUserId)
                    val msg = when {
                        enabled && isAdmin ->
                            "Live Push-to-Talk CONNECTED! Automatically enabled for $peerName too."
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
        val thread = (threadState.value as? UiState.Success)?.data
        val isGroupAdminHere = thread?.isGroupAdmin(currentUserId) == true
        if (speaking && thread != null && thread.isBlockedByGroupAdminTalking(currentUserId)) {
            _snackbarEvents.tryEmit("Group DM Admin has priority mic right now.")
            return
        }
        viewModelScope.launch {
            if (speaking) {
                LivePttSessionManager.suppressForPrivateCall(appContext, threadId)
            }
            if (speaking && !ensureCallConnected(myName)) return@launch
            _isMicActive.value = speaking
            liveKitManager.setMicEnabled(speaking)
            if (isGroupAdminHere) {
                repository.setGroupDmAdminTalking(threadId, speaking)
            }
            if (speaking) {
                PrivateCallForegroundService.start(appContext, threadId, currentPeerTitle())
            }
        }
    }

    fun setGroupAdminTalking(talking: Boolean) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        if (!thread.isGroup || !thread.isGroupAdmin(currentUserId)) return
        viewModelScope.launch {
            repository.setGroupDmAdminTalking(threadId, talking)
        }
    }

    fun forceMuteForGroupAdminOverride() {
        if (!_isMicActive.value) return
        _isMicActive.value = false
        viewModelScope.launch {
            liveKitManager.setMicEnabled(false)
            _snackbarEvents.tryEmit("Muted — Group DM Admin took priority microphone.")
        }
    }

    fun toggleCallVideo(myName: String) {
        if (_isConnectingCall.value) return
        val wantActive = !_isCamActive.value
        viewModelScope.launch {
            if (wantActive) {
                // Disconnect & suppress background PTT on the same LiveKit room so it never kicks this video call
                LivePttSessionManager.suppressForPrivateCall(appContext, threadId)
            }
            if (wantActive && !ensureCallConnected(myName)) return@launch
            _isCamActive.value = wantActive
            val ok = liveKitManager.setCameraEnabled(wantActive)
            if (wantActive) {
                if (!ok && liveKitManager.localVideoTrack.value == null) {
                    _isCamActive.value = false
                    _snackbarEvents.tryEmit("Camera failed to start — please check camera permission and retry.")
                } else {
                    PrivateCallForegroundService.start(appContext, threadId, currentPeerTitle())
                    _snackbarEvents.tryEmit("Private video camera is LIVE")
                }
            }
        }
    }

    fun flipCamera() {
        val nextFront = !_useFrontCamera.value
        _useFrontCamera.value = nextFront
        liveKitManager.flipCamera(nextFront)
    }

    fun endCall() {
        val thread = (threadState.value as? UiState.Success)?.data
        val isGroupAdminHere = thread?.isGroupAdmin(currentUserId) == true
        viewModelScope.launch {
            if (isGroupAdminHere && thread?.groupAdminTalking == true) {
                repository.setGroupDmAdminTalking(threadId, false)
            }
            liveKitManager.setMicEnabled(false)
            liveKitManager.setCameraEnabled(false)
            liveKitManager.disconnect()
            _isMicActive.value = false
            _isCamActive.value = false
            _isInCall.value = false
            LivePttSessionManager.clearPrivateCallSuppression(threadId)
            PrivateCallForegroundService.stop(appContext)
            _snackbarEvents.tryEmit("Private call ended")
        }
    }

    fun setPriorityChirpOptIn(optIn: Boolean) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.setDirectThreadPriorityChirpOptIn(thread, optIn)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (optIn) "Priority Break-In Chirps enabled for this chat."
                        else "Priority Break-In Chirps turned off for this chat."
                    )
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update Priority Chirp setting")
                }
        }
    }

    fun sendPriorityBreakInChirp(
        senderName: String,
        senderTier: StaffTier,
        messageText: String,
        pointsCost: Int,
        onInsufficientPoints: () -> Unit = {}
    ) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        if (_isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            repository.sendPriorityBreakInChirp(
                thread = thread,
                senderName = senderName,
                senderTier = senderTier,
                messageText = messageText,
                pointsCost = pointsCost
            ).onSuccess {
                _snackbarEvents.tryEmit(
                    if (pointsCost >= 2) "Urgent Break-In Chirp sent! (-2 pts)"
                    else "Priority Chirp sent! (-1 pt)"
                )
            }.onFailure { err ->
                val msg = err.message ?: "Could not send Priority Chirp"
                _snackbarEvents.tryEmit(msg)
                if (msg.contains("need", ignoreCase = true) && msg.contains("point", ignoreCase = true)) {
                    onInsufficientPoints()
                }
            }
            _isSending.value = false
        }
    }

    fun purchaseScrollingBanner(
        bannerText: String,
        colorHex: String,
        buyerName: String,
        onInsufficientPoints: () -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        if (_isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            repository.purchaseDirectThreadBanner(
                thread = thread,
                bannerText = bannerText,
                colorHex = colorHex,
                buyerName = buyerName
            ).onSuccess {
                _snackbarEvents.tryEmit("24-Hour Scrolling Banner is LIVE in this DM! (-1 pt)")
                onSuccess()
            }.onFailure { err ->
                val msg = err.message ?: "Could not set scrolling banner"
                _snackbarEvents.tryEmit(msg)
                if (msg.contains("need", ignoreCase = true) && msg.contains("point", ignoreCase = true)) {
                    onInsufficientPoints()
                }
            }
            _isSending.value = false
        }
    }

    fun setGroupMemberMuted(targetUserId: String, targetName: String, mute: Boolean) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.setGroupDmUserMuted(thread, targetUserId, targetName, mute)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (mute) "$targetName has been muted in this group DM."
                        else "$targetName has been unmuted."
                    )
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update mute state")
                }
        }
    }

    fun bounceGroupMember(targetUserId: String, targetName: String) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.bounceGroupDmUser(thread, targetUserId, targetName)
                .onSuccess {
                    _snackbarEvents.tryEmit("$targetName bounced from this group DM for 24 hours.")
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not bounce member")
                }
        }
    }

    fun removeGroupBounce(targetUserId: String, targetName: String) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.removeGroupDmBounce(thread, targetUserId)
                .onSuccess {
                    _snackbarEvents.tryEmit("Removed 24h bounce for $targetName.")
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not remove bounce")
                }
        }
    }

    fun banGroupMember(targetUserId: String, targetName: String) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.banGroupDmUser(thread, targetUserId, targetName)
                .onSuccess {
                    _snackbarEvents.tryEmit("$targetName banned from this group DM.")
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not ban member")
                }
        }
    }

    fun unbanGroupMember(targetUserId: String, targetName: String) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.unbanGroupDmUser(thread, targetUserId)
                .onSuccess {
                    _snackbarEvents.tryEmit("Unbanned $targetName from this group DM.")
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not unban member")
                }
        }
    }

    fun setGroupSupportStaff(targetUserId: String, targetName: String, promote: Boolean) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.setGroupDmSupportStaff(thread, targetUserId, promote)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (promote) "$targetName is now Support Staff in this group DM (can mute & bounce)."
                        else "Removed $targetName from Group DM Support Staff."
                    )
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update Group DM Support Staff")
                }
        }
    }

    fun setGroupPttAllowed(targetUserId: String, targetName: String, allowed: Boolean) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.setGroupDmPttAllowed(thread, targetUserId, allowed)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (allowed) "$targetName can now use Push-to-Talk in this group DM."
                        else "Revoked Push-to-Talk access for $targetName in this group DM."
                    )
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update PTT speaker permissions")
                }
        }
    }

    fun setGroupSupervisor(targetUserId: String, targetName: String, makeSupervisor: Boolean) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.setGroupDmSupervisor(thread, targetUserId, targetName, makeSupervisor)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (makeSupervisor) "$targetName is now a Supervisor (Admin) in this company group DM."
                        else "Removed $targetName as Supervisor."
                    )
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update Supervisor role")
                }
        }
    }

    fun sendWorkOrderFromChat(
        senderName: String,
        senderRole: String,
        targetUserId: String,
        targetName: String,
        orderType: WorkOrderType,
        priority: WorkOrderPriority,
        title: String,
        body: String,
        onSent: () -> Unit = {}
    ) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        val ownerId = thread.companyOwnerId.ifBlank { thread.effectiveGroupAdminId() }
        viewModelScope.launch {
            repository.sendWorkOrder(
                companyOwnerId = ownerId,
                threadId = thread.id,
                supervisorUids = thread.groupSupervisorIds,
                senderName = senderName,
                senderRole = senderRole,
                targetUserId = targetUserId,
                targetName = targetName,
                orderType = orderType,
                priority = priority,
                title = title,
                body = body
            ).onSuccess {
                val label = if (priority == WorkOrderPriority.URGENT) "URGENT ${orderType.label}" else orderType.label
                _snackbarEvents.tryEmit("$label sent to $targetName.")
                onSent()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not send work order")
            }
        }
    }

    fun submitEmployeeRequestFromChat(
        requesterName: String,
        category: EmployeeRequestCategory,
        subject: String,
        details: String,
        urgent: Boolean,
        onSubmitted: () -> Unit = {}
    ) {
        val thread = (threadState.value as? UiState.Success)?.data ?: return
        val ownerId = thread.companyOwnerId.ifBlank { thread.effectiveGroupAdminId() }
        viewModelScope.launch {
            repository.submitEmployeeRequest(
                companyOwnerId = ownerId,
                threadId = thread.id,
                supervisorUids = thread.groupSupervisorIds,
                requesterName = requesterName,
                category = category,
                subject = subject,
                details = details,
                urgent = urgent
            ).onSuccess {
                _snackbarEvents.tryEmit("Request submitted to your Company Owner & Supervisors.")
                onSubmitted()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not submit request")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        LivePttSessionManager.clearPrivateCallSuppression(threadId)
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
