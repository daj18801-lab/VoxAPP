package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatMessage
import com.example.data.ChatRoom
import com.example.data.MessageType
import com.example.data.ParticipantRole
import com.example.data.RoomParticipant
import com.example.data.StaffCapabilities
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.media.LiveCameraPreview
import com.example.ui.components.LivePulseBadge
import com.example.ui.components.RolePillBadge
import com.example.ui.components.SafetyTagChip
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.TopSnackbarOverlay
import com.example.ui.components.VoiceWaveformBars
import com.example.ui.components.formatTimestampShort
import com.example.ui.components.showTopMessage
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.VoiceCyan
import com.example.viewmodel.RoomViewModel

private data class ActionTarget(
    val userId: String,
    val displayName: String,
    val tier: StaffTier,
    val participant: RoomParticipant?,
    val message: ChatMessage?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    viewModel: RoomViewModel,
    userProfile: UserProfile,
    staffCapabilities: StaffCapabilities,
    onExitRoom: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val roomState by viewModel.roomState.collectAsStateWithLifecycle()
    val participantsState by viewModel.participantsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val modLogsState by viewModel.modLogsState.collectAsStateWithLifecycle()
    val isMicActive by viewModel.isMicActive.collectAsStateWithLifecycle()
    val isCamActive by viewModel.isCamActive.collectAsStateWithLifecycle()
    val useFrontCamera by viewModel.useFrontCamera.collectAsStateWithLifecycle()
    val isHandRaised by viewModel.isHandRaised.collectAsStateWithLifecycle()
    val pushToTalkMode by viewModel.pushToTalkMode.collectAsStateWithLifecycle()
    val liveVoiceAmplitude by viewModel.liveVoiceAmplitude.collectAsStateWithLifecycle()
    val slowModeCountdown by viewModel.slowModeCountdown.collectAsStateWithLifecycle()
    val isCurrentlyInvisible by viewModel.isCurrentlyInvisible.collectAsStateWithLifecycle()

    var showModCenter by remember { mutableStateOf(false) }
    var showParticipants by remember { mutableStateOf(false) }
    var showEditRoomDialog by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var reportTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var suspendTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var alertTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var editUserProfileTarget by remember { mutableStateOf<UserProfile?>(null) }
    var stageExpanded by remember { mutableStateOf(true) }
    var messageInput by remember { mutableStateOf("") }

    LaunchedEffect(staffCapabilities) {
        viewModel.updateStaffCapabilities(staffCapabilities)
    }

    LaunchedEffect(userProfile) {
        viewModel.onUserProfileUpdated(userProfile)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setCameraLive(true)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setMicLive(true)
        }
    }

    fun requestOrToggleCamera() {
        if (isCamActive) {
            viewModel.setCameraLive(false)
        } else {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) viewModel.setCameraLive(true)
            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun requestOrSetMic(live: Boolean) {
        if (!live) {
            viewModel.setMicLive(false)
            return
        }
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) viewModel.setMicLive(true)
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    BackHandler {
        viewModel.leaveRoomAndExit(onExitRoom)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showTopMessage(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.roomExitSignal.collect { reason ->
            snackbarHostState.showTopMessage(reason)
            viewModel.leaveRoomAndExit(onExitRoom)
        }
    }

    val room = (roomState as? UiState.Success)?.data
    val participants = (participantsState as? UiState.Success)?.data.orEmpty()
    val visibleParticipants = remember(participants) {
        participants.filter { !it.invisible }
    }
    val allMessages = (messagesState as? UiState.Success)?.data.orEmpty()
    val visibleMessages = remember(allMessages, userProfile.blockedUserIds) {
        allMessages.filterNot { it.senderId in userProfile.blockedUserIds }
    }
    val modLogs = (modLogsState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(room?.id) {
        if (room != null && room.status == "LIVE") {
            viewModel.ensureJoined(room, userProfile)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { _ ->
        when (val state = roomState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VoiceCyan)
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is UiState.Success -> {
                val activeRoom = state.data
                if (activeRoom == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Room not found or ended.")
                    }
                } else {
                    val canEditRoom = activeRoom.canEditOrCloseRoom(viewModel.currentUserId, staffCapabilities.tier)
                    val isMod = activeRoom.hasModPrivileges(viewModel.currentUserId, staffCapabilities.tier)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        // Top Live Room Header Bar
                        RoomTopBar(
                            room = activeRoom,
                            participantCount = visibleParticipants.size.coerceAtLeast(activeRoom.participantCount.toInt()),
                            isMod = isMod,
                            canEditRoom = canEditRoom,
                            canToggleInvisible = staffCapabilities.effectiveCanEnterInvisible,
                            isCurrentlyInvisible = isCurrentlyInvisible,
                            staffTier = staffCapabilities.tier,
                            onBack = { viewModel.leaveRoomAndExit(onExitRoom) },
                            onEditRoomName = { showEditRoomDialog = true },
                            onToggleInvisible = { viewModel.toggleInRoomInvisible() },
                            onOpenParticipants = { showParticipants = true },
                            onOpenModCenter = { showModCenter = true }
                        )

                        // Stealth Invisible Mode Banner (only shown to the invisible Admin / Support user)
                        if (isCurrentlyInvisible) {
                            Surface(
                                color = usernameColor(staffCapabilities.tier).copy(alpha = 0.16f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontaLIVE =10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.VisibilityOff,
                                            contentDescription = "Invisible mode active",
                                            tint = usernameColor(staffCapabilities.tier),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (staffCapabilities.isAdmin) {
                                                "INVISIBLE MODE · Hidden from room (Admin can speak on mic anytime)"
                                            } else {
                                                "INVISIBLE MODE · Hidden from room (Go Visible to use mic)"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = usernameColor(staffCapabilities.tier)
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.toggleInRoomInvisible() },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("go_visible_banner_button")
                                    ) {
                                        Text("Go Visible", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        // Exclusive App Owner Admin Mic Priority Banner
                        if (activeRoom.adminMicPriorityActive) {
                            val adminRed = usernameColor(StaffTier.ADMIN)
                            Surface(
                                color = adminRed.copy(alpha = 0.18f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = "Admin mic priority",
                                        tint = adminRed,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (staffCapabilities.isAdmin) {
                                            if (isCurrentlyInvisible) {
                                                "EXCLUSIVE ADMIN MIC LIVE (INVISIBLE) — All other microphones locked"
                                            } else {
                                                "EXCLUSIVE ADMIN MIC PRIORITY ACTIVE — Nobody else can use the mic"
                                            }
                                        } else {
                                            "Main Administrator is on the microphone — all other microphones are locked"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = adminRed
                                    )
                                }
                            }
                        }

                        // Adaptive Stage + Chat Body
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            val isWide = maxWidth >= 700.dp
                            if (isWide) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    StageSection(
                                        room = activeRoom,
                                        participants = visibleParticipants,
                                        currentUserId = viewModel.currentUserId,
                                        isCamActive = isCamActive,
                                        useFrontCamera = useFrontCamera,
                                        isMicActive = isMicActive,
                                        liveAmplitude = liveVoiceAmplitude,
                                        expanded = true,
                                        onToggleExpand = {},
                                        onFlipCamera = { viewModel.flipCamera() },
                                        onSelectParticipant = { p ->
                                            actionTarget = ActionTarget(p.userId, p.displayName, p.staffTierEnum(), p, null)
                                        },
                                        modifier = Modifier
                                            .weight(0.46f)
                                            .fillMaxHeight()
                                    )
                                    ChatTimelineSection(
                                        room = activeRoom,
                                        messages = visibleMessages,
                                        currentUserId = viewModel.currentUserId,
                                        onTapMessage = { msg ->
                                            val p = participants.firstOrNull { it.userId == msg.senderId }
                                            actionTarget = ActionTarget(
                                                userId = msg.senderId,
                                                displayName = msg.senderName,
                                                tier = p?.staffTierEnum() ?: msg.senderTierEnum(),
                                                participant = p,
                                                message = msg
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(0.54f)
                                            .fillMaxHeight()
                                    )
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    StageSection(
                                        room = activeRoom,
                                        participants = visibleParticipants,
                                        currentUserId = viewModel.currentUserId,
                                        isCamActive = isCamActive,
                                        useFrontCamera = useFrontCamera,
                                        isMicActive = isMicActive,
                                        liveAmplitude = liveVoiceAmplitude,
                                        expanded = stageExpanded,
                                        onToggleExpand = { stageExpanded = !stageExpanded },
                                        onFlipCamera = { viewModel.flipCamera() },
                                        onSelectParticipant = { p ->
                                            actionTarget = ActionTarget(p.userId, p.displayName, p.staffTierEnum(), p, null)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    ChatTimelineSection(
                                        room = activeRoom,
                                        messages = visibleMessages,
                                        currentUserId = viewModel.currentUserId,
                                        onTapMessage = { msg ->
                                            val p = participants.firstOrNull { it.userId == msg.senderId }
                                            actionTarget = ActionTarget(
                                                userId = msg.senderId,
                                                displayName = msg.senderName,
                                                tier = p?.staffTierEnum() ?: msg.senderTierEnum(),
                                                participant = p,
                                                message = msg
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Bottom Real-Time Chat Input + Voice/Video Stage Control Deck
                        RoomBottomDock(
                            room = activeRoom,
                            currentUserId = viewModel.currentUserId,
                            userProfile = userProfile,
                            staffCapabilities = staffCapabilities,
                            isCurrentlyInvisible = isCurrentlyInvisible,
                            messageInput = messageInput,
                            onMessageInputChange = { messageInput = it.take(500) },
                            slowModeCountdown = slowModeCountdown,
                            isMicActive = isMicActive,
                            isCamActive = isCamActive,
                            isHandRaised = isHandRaised,
                            pushToTalkMode = pushToTalkMode,
                            liveAmplitude = liveVoiceAmplitude,
                            onSend = {
                                if (messageInput.isNotBlank()) {
                                    viewModel.sendChatMessage(messageInput, userProfile)
                                    messageInput = ""
                                }
                            },
                            onSendReaction = { emoji ->
                                viewModel.sendChatMessage(emoji, userProfile, isReaction = true)
                            },
                            onToggleMicMode = { viewModel.togglePushToTalkMode() },
                            onMicStateChange = { live -> requestOrSetMic(live) },
                            onToggleCamera = { requestOrToggleCamera() },
                            onToggleRaiseHand = { viewModel.toggleRaiseHand() }
                        )
                    }
                }
            }
        }
        }

        TopSnackbarOverlay(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (showEditRoomDialog && room != null) {
        EditRoomNameDialog(
            room = room,
            onSave = { newTitle, newTopic ->
                viewModel.editRoomDetails(userProfile.displayName, newTitle, newTopic)
            },
            onDeleteName = { topic ->
                viewModel.editRoomDetails(userProfile.displayName, "", topic)
            },
            onDismiss = { showEditRoomDialog = false }
        )
    }

    if (showModCenter && room != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showModCenter = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ModerationCenterSheetContent(
                room = room,
                currentUserId = viewModel.currentUserId,
                staffCapabilities = staffCapabilities,
                modLogs = modLogs,
                onUpdateSettings = { autoMod, strict, links, locked, stageLocked, slow, video, words, actionType, reason ->
                    viewModel.updateSafetySettings(
                        actorName = userProfile.displayName,
                        autoModEnabled = autoMod,
                        autoModBlockMode = strict,
                        linksBlocked = links,
                        roomLocked = locked,
                        stageMicLocked = stageLocked,
                        slowModeSeconds = slow,
                        videoEnabled = video,
                        blockedWords = words,
                        actionType = actionType,
                        reason = reason
                    )
                },
                onOpenEditRoom = {
                    showModCenter = false
                    showEditRoomDialog = true
                },
                onUnmuteUser = { uid ->
                    viewModel.toggleMuteParticipant(uid, "User", userProfile.displayName, mute = false)
                },
                onUnbanUser = { uid ->
                    viewModel.unbanUser(uid, userProfile.displayName)
                },
                onCloseRoom = {
                    showModCenter = false
                    viewModel.closeRoomAsHostOrAdmin(userProfile.displayName, onExitRoom)
                }
            )
        }
    }

    if (showParticipants && room != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showParticipants = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ParticipantsSheetContent(
                room = room,
                participants = participants,
                currentUserId = viewModel.currentUserId,
                staffCapabilities = staffCapabilities,
                onSelectParticipant = { p ->
                    showParticipants = false
                    actionTarget = ActionTarget(p.userId, p.displayName, p.staffTierEnum(), p, null)
                },
                onApproveSpeaker = { p ->
                    viewModel.changeParticipantRole(p.userId, p.displayName, userProfile.displayName, ParticipantRole.SPEAKER)
                }
            )
        }
    }

    val currentTarget = actionTarget
    if (currentTarget != null && room != null) {
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            UserModerationActionSheet(
                room = room,
                currentUserId = viewModel.currentUserId,
                staffCapabilities = staffCapabilities,
                targetUserId = currentTarget.userId,
                targetName = currentTarget.displayName,
                targetTier = currentTarget.tier,
                targetParticipant = currentTarget.participant,
                selectedMessage = currentTarget.message,
                onMuteToggle = { mute ->
                    viewModel.toggleMuteParticipant(
                        targetUserId = currentTarget.userId,
                        targetName = currentTarget.displayName,
                        actorName = userProfile.displayName,
                        mute = mute
                    )
                },
                onChangeRole = { newRole ->
                    viewModel.changeParticipantRole(
                        targetUserId = currentTarget.userId,
                        targetName = currentTarget.displayName,
                        actorName = userProfile.displayName,
                        newRole = newRole
                    )
                },
                onBounce = {
                    viewModel.bounceUser(
                        targetUserId = currentTarget.userId,
                        targetName = currentTarget.displayName,
                        actorName = userProfile.displayName,
                        reason = "Bounced by ${userProfile.displayName}"
                    )
                },
                onKick = {
                    viewModel.kickOrBanUser(
                        targetUserId = currentTarget.userId,
                        targetName = currentTarget.displayName,
                        actorName = userProfile.displayName,
                        ban = false,
                        reason = "Kicked by ${userProfile.displayName}"
                    )
                },
                onBan = {
                    viewModel.kickOrBanUser(
                        targetUserId = currentTarget.userId,
                        targetName = currentTarget.displayName,
                        actorName = userProfile.displayName,
                        ban = true,
                        reason = "Banned by ${userProfile.displayName}"
                    )
                },
                onSuspend = { suspendTarget = currentTarget },
                onSendAlert = { alertTarget = currentTarget },
                onSetStaffTier = { nextTier ->
                    viewModel.setStaffTierInRoom(
                        targetUserId = currentTarget.userId,
                        targetName = currentTarget.displayName,
                        tier = nextTier,
                        canEnterInvisible = nextTier == StaffTier.ADMIN,
                        adminName = userProfile.displayName
                    )
                },
                onEditUserAdmin = {
                    viewModel.fetchTargetUserProfile(currentTarget.userId) { loaded ->
                        if (loaded != null) {
                            editUserProfileTarget = loaded
                        }
                    }
                },
                onRedactMessage = {
                    currentTarget.message?.let { msg ->
                        viewModel.redactMessage(msg, userProfile.displayName)
                    }
                },
                onReport = { reportTarget = currentTarget },
                onBlockUser = {
                    viewModel.blockUser(
                        targetUserId = currentTarget.userId,
                        targetName = currentTarget.displayName,
                        currentBlocked = userProfile.blockedUserIds
                    )
                },
                onDismiss = { actionTarget = null }
            )
        }
    }

    reportTarget?.let { target ->
        ReportSafetyDialog(
            targetName = target.displayName,
            onDismiss = { reportTarget = null },
            onSubmit = { category, details ->
                viewModel.submitReport(
                    reporterName = userProfile.displayName,
                    targetUserId = target.userId,
                    targetName = target.displayName,
                    category = category,
                    details = details
                )
                reportTarget = null
            }
        )
    }

    suspendTarget?.let { target ->
        SuspendUserDialog(
            targetName = target.displayName,
            onSuspend = { minutes, reason ->
                viewModel.suspendUser(
                    targetUserId = target.userId,
                    targetName = target.displayName,
                    actorName = userProfile.displayName,
                    durationMinutes = minutes,
                    reason = reason
                )
            },
            onDismiss = { suspendTarget = null }
        )
    }

    alertTarget?.let { target ->
        SendOrEditAlertDialog(
            existingAlert = null,
            targetName = target.displayName,
            onSave = { title, message, severity, _ ->
                viewModel.sendAlertToUser(
                    targetUserId = target.userId,
                    targetName = target.displayName,
                    senderName = userProfile.displayName,
                    title = title,
                    message = message,
                    severity = severity
                )
            },
            onDismiss = { alertTarget = null }
        )
    }

    editUserProfileTarget?.let { loadedProfile ->
        EditUserAdminDialog(
            profile = loadedProfile,
            onSave = { displayName, handle, bio, globallyBanned, disableCreateRoom, disableEnterRoom, disableMic, disableVideo, suspendedUntilMillis, suspensionReason ->
                viewModel.adminEditUserInRoom(
                    targetUserId = loadedProfile.userId,
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
                    actorName = userProfile.displayName
                )
            },
            onDismiss = { editUserProfileTarget = null }
        )
    }
}

@Composable
private fun RoomTopBar(
    room: ChatRoom,
    participantCount: Int,
    isMod: Boolean,
    canEditRoom: Boolean,
    canToggleInvisible: Boolean,
    isCurrentlyInvisible: Boolean,
    staffTier: StaffTier,
    onBack: () -> Unit,
    onEditRoomName: () -> Unit,
    onToggleInvisible: () -> Unit,
    onOpenParticipants: () -> Unit,
    onOpenModCenter: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontaLIVE=6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("leave_room_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Leave room"
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LivePulseBadge()
                    Text(
                        text = room.displayTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = room.categoryEnum().label,
                        style = MaterialTheme.typography.bodySmall,
                        color = VoiceCyan
                    )
                    if (room.roomLocked) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Room locked",
                            tint = LiveCoral,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (room.stageMicLocked) {
                        Text(
                            text = "· Mics Locked",
                            style = MaterialTheme.typography.labelSmall,
                            color = LiveCoral
                        )
                    }
                }
            }

            if (canEditRoom) {
                IconButton(
                    onClick = onEditRoomName,
                    modifier = Modifier.testTag("room_topbar_edit_name")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Change or delete room name",
                        tint = VoiceCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (canToggleInvisible) {
                IconButton(
                    onClick = onToggleInvisible,
                    modifier = Modifier.testTag("room_topbar_invisible_toggle")
                ) {
                    Icon(
                        imageVector = if (isCurrentlyInvisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (isCurrentlyInvisible) "Switch to visible" else "Switch to invisible",
                        tint = if (isCurrentlyInvisible) usernameColor(staffTier) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onOpenParticipants)
                    .testTag("participants_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = "Participants",
                        tint = VoiceCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "$participantCount",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            IconButton(
                onClick = onOpenModCenter,
                modifier = Modifier.testTag("moderation_shield_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = "Moderation controls",
                    tint = if (isMod) SafetyEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StageSection(
    room: ChatRoom,
    participants: List<RoomParticipant>,
    currentUserId: String,
    isCamActive: Boolean,
    useFrontCamera: Boolean,
    isMicActive: Boolean,
    liveAmplitude: Float,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onFlipCamera: () -> Unit,
    onSelectParticipant: (RoomParticipant) -> Unit,
    modifier: Modifier = Modifier
) {
    val stageParticipants = participants.filter { it.onStage() }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "LIVE STAGE (${stageParticipants.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = VoiceCyan
                    )
                    if (room.autoModEnabled) {
                        SafetyTagChip(
                            icon = Icons.Rounded.Shield,
                            text = if (room.autoModBlockMode) "Strict Shield" else "Auto-Mod",
                            tint = SafetyEmerald
                        )
                    }
                }
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse stage" else "Expand stage"
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))

                if (isCamActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(172.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        LiveCameraPreview(
                            useFrontCamera = useFrontCamera,
                            modifier = Modifier.fillMaxSize()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LivePulseBadge(label = "ON AIR · YOU")
                            Surface(
                                shape = CircleShape,
                                color = Color(0xAA0A0D16),
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable(onClick = onFlipCamera)
                                    .testTag("flip_camera_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Cameraswitch,
                                        contentDescription = "Flip camera",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        if (isMicActive) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xBB0A0D16),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    VoiceWaveformBars(amplitude = liveAmplitude, active = true)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "MIC LIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VoiceCyan
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(stageParticipants, key = { it.userId }) { p ->
                        val isSelf = p.userId == currentUserId
                        val effectiveAmp = if (isSelf) liveAmplitude else p.speakingLevel.toFloat()
                        val effectiveMic = if (isSelf) isMicActive else p.micActive
                        val effectiveCam = if (isSelf) isCamActive else p.camActive
                        val tier = p.staffTierEnum()

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (effectiveMic) 1.5.dp else 1.dp,
                                color = if (effectiveMic) VoiceCyan else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .width(120.dp)
                                .clickable { onSelectParticipant(p) }
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    SpeakingAvatarCircle(
                                        displayName = p.displayName,
                                        colorHex = p.avatarColorHex,
                                        speakingLevel = effectiveAmp,
                                        micActive = effectiveMic,
                                        size = 52.dp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    p.mutedByMod -> LiveCoral
                                                    effectiveCam -> LiveCoral
                                                    effectiveMic -> VoiceCyan
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                p.mutedByMod -> Icons.Rounded.MicOff
                                                effectiveCam -> Icons.Rounded.Videocam
                                                effectiveMic -> Icons.Rounded.Mic
                                                else -> Icons.Rounded.MicOff
                                            },
                                            contentDescription = "Media status",
                                            tint = if (effectiveMic || effectiveCam || p.mutedByMod) Color.White
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = if (isSelf) "${p.displayName} (You)" else p.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = usernameColor(tier),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (tier != StaffTier.USER) {
                                    StaffTierBadge(tier = tier)
                                }
                                RolePillBadge(role = p.roleEnum())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatTimelineSection(
    room: ChatRoom,
    messages: List<ChatMessage>,
    currentUserId: String,
    onTapMessage: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = modifier) {
        if (room.topic.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Room Rules: ${room.topic}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Say hello to kick off the live chat! Tap any message to moderate or report.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageBubble(
                        message = msg,
                        isMine = msg.senderId == currentUserId,
                        onClick = { onTapMessage(msg) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    onClick: () -> Unit
) {
    val type = message.messageTypeEnum()

    if (type == MessageType.MOD_NOTICE || type == MessageType.JOIN_LEAVE) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (type == MessageType.MOD_NOTICE) ModAmber.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (type == MessageType.MOD_NOTICE) ModAmber
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        return
    }

    val role = ParticipantRole.fromRaw(message.senderRole)
    val senderTier = message.senderTierEnum()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isMine) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (message.flaggedByAutoMod) ModAmber.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isMine) "${message.senderName} (You)" else message.senderName,
                        style = MaterialTheme.typography.labelLarge,
                        color = usernameColor(senderTier),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    StaffTierBadge(tier = senderTier)
                    if (role == ParticipantRole.HOST || role == ParticipantRole.MODERATOR) {
                        RolePillBadge(role = role)
                    }
                    if (message.flaggedByAutoMod) {
                        Text(
                            text = "FILTERED",
                            style = MaterialTheme.typography.labelSmall,
                            color = ModAmber
                        )
                    }
                }
                Text(
                    text = formatTimestampShort(message.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = message.text,
                style = if (type == MessageType.REACTION) MaterialTheme.typography.headlineSmall
                else MaterialTheme.typography.bodyMedium,
                fontStyle = if (message.deletedByMod) FontStyle.Italic else FontStyle.Normal,
                color = if (message.deletedByMod) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RoomBottomDock(
    room: ChatRoom,
    currentUserId: String,
    userProfile: UserProfile,
    staffCapabilities: StaffCapabilities,
    isCurrentlyInvisible: Boolean,
    messageInput: String,
    onMessageInputChange: (String) -> Unit,
    slowModeCountdown: Int,
    isMicActive: Boolean,
    isCamActive: Boolean,
    isHandRaised: Boolean,
    pushToTalkMode: Boolean,
    liveAmplitude: Float,
    onSend: () -> Unit,
    onSendReaction: (String) -> Unit,
    onToggleMicMode: () -> Unit,
    onMicStateChange: (Boolean) -> Unit,
    onToggleCamera: () -> Unit,
    onToggleRaiseHand: () -> Unit
) {
    val isMuted = !staffCapabilities.isAdmin && room.mutedUserIds.contains(currentUserId)
    val micBlockedReason: String? = when {
        !staffCapabilities.isAdmin && room.adminMicPriorityActive -> "Admin on Mic"
        !staffCapabilities.isAdmin && userProfile.disableMic -> "Mic Disabled"
        isCurrentlyInvisible && !staffCapabilities.isAdmin -> "Visible Req."
        isMuted -> "Muted"
        !staffCapabilities.isAdmin && room.stageMicLocked && !room.hasModPrivileges(currentUserId, staffCapabilities.tier) -> "Mics Locked"
        else -> null
    }
    val micAllowed = micBlockedReason == null
    val camAllowed = room.videoEnabled && !isCurrentlyInvisible && (staffCapabilities.isAdmin || !userProfile.disableVideo)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = onMessageInputChange,
                    enabled = !isMuted,
                    placeholder = {
                        Text(
                            text = when {
                                isMuted -> "You are muted by moderators"
                                slowModeCountdown > 0 -> "Slow mode (${slowModeCountdown}s)…"
                                else -> "Message the room…"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )

                Surface(
                    shape = CircleShape,
                    color = if (messageInput.isNotBlank() && !isMuted && slowModeCountdown == 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(
                            enabled = messageInput.isNotBlank() && !isMuted && slowModeCountdown == 0,
                            onClick = onSend
                        )
                        .testTag("send_message_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send message",
                            tint = if (messageInput.isNotBlank() && !isMuted && slowModeCountdown == 0) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("👏", "🔥", "❤️", "🙌").forEach { emoji ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.clickable(enabled = !isMuted) { onSendReaction(emoji) }
                        ) {
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clickable(onClick = onToggleMicMode)
                        .testTag("toggle_ptt_mode_button")
                ) {
                    Text(
                        text = if (pushToTalkMode) "Mode: Hold-to-Talk" else "Mode: Open Mic",
                        style = MaterialTheme.typography.labelSmall,
                        color = VoiceCyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                        isCamActive -> LiveCoral
                        !camAllowed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .height(50.dp)
                        .clickable(enabled = camAllowed, onClick = onToggleCamera)
                        .testTag("toggle_camera_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCamActive) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                            contentDescription = "Toggle camera video",
                            tint = if (isCamActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCamActive) "Cam On" else "Video",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCamActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val micModifier = if (pushToTalkMode) {
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .pointerInput(micAllowed) {
                            detectTapGestures(
                                onPress = {
                                    if (micAllowed) {
                                        onMicStateChange(true)
                                        tryAwaitRelease()
                                        onMicStateChange(false)
                                    } else {
                                        onMicStateChange(true)
                                    }
                                }
                            )
                        }
                        .testTag("push_to_talk_button")
                } else {
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clickable { onMicStateChange(!isMicActive) }
                        .testTag("mic_toggle_button")
                }

                val adminRed = usernameColor(StaffTier.ADMIN)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                        !micAllowed -> MaterialTheme.colorScheme.errorContainer
                        isMicActive && staffCapabilities.isAdmin -> adminRed
                        isMicActive -> VoiceCyan
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = micModifier
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isMicActive) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                            contentDescription = "Microphone control",
                            tint = when {
                                !micAllowed -> MaterialTheme.colorScheme.onErrorContainer
                                isMicActive && staffCapabilities.isAdmin -> Color.White
                                isMicActive -> Color(0xFF002620)
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isMicActive) {
                            VoiceWaveformBars(
                                amplitude = liveAmplitude,
                                active = true,
                                barColor = if (staffCapabilities.isAdmin) Color.White else Color(0xFF002620)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = when {
                                micBlockedReason != null -> micBlockedReason
                                isMicActive && staffCapabilities.isAdmin && isCurrentlyInvisible -> "Admin Mic (Ghost)"
                                isMicActive && staffCapabilities.isAdmin -> "Admin Priority Mic"
                                isMicActive -> "Live Mic"
                                pushToTalkMode -> "Hold to Talk"
                                else -> "Tap to Speak"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = when {
                                !micAllowed -> MaterialTheme.colorScheme.onErrorContainer
                                isMicActive && staffCapabilities.isAdmin -> Color.White
                                isMicActive -> Color(0xFF002620)
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isHandRaised) ModAmber else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(50.dp)
                        .clickable(enabled = !isCurrentlyInvisible, onClick = onToggleRaiseHand)
                        .testTag("raise_hand_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isHandRaised) Icons.Rounded.RecordVoiceOver else Icons.Rounded.WavingHand,
                            contentDescription = "Raise hand to speak",
                            tint = if (isHandRaised) Color(0xFF2B1B00) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
