package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatMessage
import com.example.data.ChatRoom
import com.example.data.MessageType
import com.example.data.ParticipantRole
import com.example.data.ROOM_REACTION_EMOJIS
import com.example.data.RoomParticipant
import com.example.data.StaffCapabilities
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.media.LiveKitVideoView
import com.example.media.LivePttSessionManager
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.RemoteVideoTrack
import com.example.ui.components.LivePulseBadge
import com.example.ui.components.RequestSuspensionDialog
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
    availableRooms: List<ChatRoom> = emptyList(),
    onSendUserToRoom: (targetUserId: String, targetName: String, targetRoom: ChatRoom, fromRoomId: String, reason: String) -> Unit = { _, _, _, _, _ -> },
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
    val localVideoTrack by viewModel.liveKitManager.localVideoTrack.collectAsStateWithLifecycle()
    val remoteVideoTracks by viewModel.liveKitManager.remoteVideoTracks.collectAsStateWithLifecycle()
    val useFrontCamera by viewModel.useFrontCamera.collectAsStateWithLifecycle()
    val isHandRaised by viewModel.isHandRaised.collectAsStateWithLifecycle()
    val pushToTalkMode by viewModel.pushToTalkMode.collectAsStateWithLifecycle()
    val liveVoiceAmplitude by viewModel.liveVoiceAmplitude.collectAsStateWithLifecycle()
    val slowModeCountdown by viewModel.slowModeCountdown.collectAsStateWithLifecycle()
    val isCurrentlyInvisible by viewModel.isCurrentlyInvisible.collectAsStateWithLifecycle()
    val isRoomAudioMutedForPtt by viewModel.isRoomAudioMutedForPtt.collectAsStateWithLifecycle()
    val pttActiveThreadId by LivePttSessionManager.activeThreadId.collectAsStateWithLifecycle()
    val pttIsConnected by LivePttSessionManager.isConnected.collectAsStateWithLifecycle()
    val pttIsConnecting by LivePttSessionManager.isConnecting.collectAsStateWithLifecycle()
    val pttPeerName by LivePttSessionManager.activePeerName.collectAsStateWithLifecycle()
    val isPttActive = !pttActiveThreadId.isNullOrBlank() && (pttIsConnected || pttIsConnecting)

    var showPttConflictDialog by remember { mutableStateOf(false) }
    var handledPttConflictForRoomId by remember { mutableStateOf<String?>(null) }
    var showModCenter by remember { mutableStateOf(false) }
    var showParticipants by remember { mutableStateOf(false) }
    var showEditRoomDialog by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var reportTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var suspendTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var supportSuspensionTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var alertTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var transferTarget by remember { mutableStateOf<ActionTarget?>(null) }
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
        if (showParticipants) {
            showParticipants = false
        } else {
            viewModel.leaveRoomAndExit(onExitRoom)
        }
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

    LaunchedEffect(room?.id, isPttActive) {
        val liveRoomId = room?.takeIf { it.status == "LIVE" }?.id
        if (liveRoomId != null && isPttActive && handledPttConflictForRoomId != liveRoomId) {
            handledPttConflictForRoomId = liveRoomId
            viewModel.setRoomAudioMutedForPtt(true)
            showPttConflictDialog = true
        } else if (!isPttActive && isRoomAudioMutedForPtt) {
            viewModel.setRoomAudioMutedForPtt(false)
            showPttConflictDialog = false
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
                        val isRoomAdmin = activeRoom.isRoomAdmin(viewModel.currentUserId)
                        val invisibleTint = usernameColor(
                            staffCapabilities.tier,
                            isRoomLeader = isRoomAdmin
                        )
                        // Top Live Room Header Bar
                        RoomTopBar(
                            room = activeRoom,
                            participantCount = visibleParticipants.size.coerceAtLeast(activeRoom.participantCount.toInt()),
                            isMod = isMod,
                            canEditRoom = canEditRoom,
                            canToggleInvisible = activeRoom.canEnterInvisibleInRoom(viewModel.currentUserId, staffCapabilities),
                            isCurrentlyInvisible = isCurrentlyInvisible,
                            invisibleAccent = invisibleTint,
                            onBack = { viewModel.leaveRoomAndExit(onExitRoom) },
                            onEditRoomName = { showEditRoomDialog = true },
                            onToggleInvisible = { viewModel.toggleInRoomInvisible() },
                            onOpenParticipants = { showParticipants = true },
                            onOpenModCenter = { showModCenter = true }
                        )

                        // Active PTT Room Audio Muted Banner
                        if (isRoomAudioMutedForPtt) {
                            Surface(
                                color = ModAmber.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("room_ptt_audio_muted_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.VolumeOff,
                                            contentDescription = "Room audio muted while PTT is active",
                                            tint = ModAmber,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "PTT Active (${pttPeerName.ifBlank { "Private Chat" }}) · Room audio muted",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = ModAmber,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(
                                        onClick = {
                                            LivePttSessionManager.disconnect(context)
                                            viewModel.setRoomAudioMutedForPtt(false)
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("room_ptt_banner_close_ptt_button")
                                    ) {
                                        Text(
                                            text = "Close PTT & Hear",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = ModAmber
                                        )
                                    }
                                }
                            }
                        }

                        // Stealth Invisible Mode Banner (shown to the invisible Admin / Support / Room Admin)
                        if (isCurrentlyInvisible) {
                            Surface(
                                color = invisibleTint.copy(alpha = 0.16f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
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
                                            tint = invisibleTint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = when {
                                                staffCapabilities.isAdmin ->
                                                    "INVISIBLE MODE · Hidden from room (Admin can speak on mic anytime)"
                                                isRoomAdmin && !staffCapabilities.isStaff ->
                                                    "INVISIBLE MODE · Hidden from room users (App Admins & Support can see you)"
                                                else ->
                                                    "INVISIBLE MODE · Hidden from room (Go Visible to use mic)"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = invisibleTint
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
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = "Admin mic priority",
                                        tint = adminRed,
                                        modifier = Modifier.size(18.dp)
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
                                        liveKitRoom = viewModel.liveKitManager.room,
                                        localVideoTrack = localVideoTrack,
                                        remoteVideoTracks = remoteVideoTracks,
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
                                        onToggleReaction = { msg, emoji ->
                                            viewModel.toggleMessageReaction(msg, emoji)
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
                                        liveKitRoom = viewModel.liveKitManager.room,
                                        localVideoTrack = localVideoTrack,
                                        remoteVideoTracks = remoteVideoTracks,
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
                                        onToggleReaction = { msg, emoji ->
                                            viewModel.toggleMessageReaction(msg, emoji)
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

        // Right-side slide-over Chatroom User List Drawer
        AnimatedVisibility(
            visible = showParticipants && room != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showParticipants = false }
                    )
                    .testTag("room_user_list_scrim")
            )
        }

        AnimatedVisibility(
            visible = showParticipants && room != null,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        ) {
            val activeDrawerRoom = room
            if (activeDrawerRoom != null) {
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 14.dp,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.84f)
                        .widthIn(max = 360.dp)
                        .testTag("room_user_list_right_drawer")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        ParticipantsSheetContent(
                            room = activeDrawerRoom,
                            participants = participants,
                            currentUserId = viewModel.currentUserId,
                            staffCapabilities = staffCapabilities,
                            onSelectParticipant = { p ->
                                showParticipants = false
                                actionTarget = ActionTarget(p.userId, p.displayName, p.staffTierEnum(), p, null)
                            },
                            onApproveSpeaker = { p ->
                                viewModel.changeParticipantRole(
                                    p.userId,
                                    p.displayName,
                                    userProfile.displayName,
                                    ParticipantRole.SPEAKER
                                )
                            },
                            onClose = { showParticipants = false }
                        )
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
                onRemoveBounce = { uid, name ->
                    viewModel.removeBounce(uid, name, userProfile.displayName)
                },
                onBanBouncedUser = { uid, name ->
                    viewModel.kickOrBanUser(
                        targetUserId = uid,
                        targetName = name,
                        actorName = userProfile.displayName,
                        ban = true,
                        reason = "Banned from room by ${userProfile.displayName}"
                    )
                },
                onUpdateRoomPinLock = { pinLocked, pinCode ->
                    viewModel.updateRoomPinLock(pinLocked, pinCode, userProfile.displayName)
                },
                onCloseRoom = {
                    showModCenter = false
                    viewModel.closeRoomAsHostOrAdmin(userProfile.displayName, onExitRoom)
                }
            )
        }
    }

    // Participants list is now rendered as a right-side slide-over drawer inside the root Box above.

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
                onRequestSupportSuspension = { supportSuspensionTarget = currentTarget },
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
                onSendToRoom = if (staffCapabilities.isAdmin) {
                    { transferTarget = currentTarget }
                } else null,
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

    if (showPttConflictDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.setRoomAudioMutedForPtt(true)
                showPttConflictDialog = false
            },
            modifier = Modifier.testTag("room_ptt_conflict_dialog"),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.RecordVoiceOver,
                    contentDescription = "Push-to-Talk active",
                    tint = ModAmber
                )
            },
            title = {
                Text(
                    text = "Push-to-Talk Is Active",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "You have an active Push-to-Talk session (${pttPeerName.ifBlank { "Private Chat" }}). " +
                        "You cannot hear chat room audio while PTT is open. " +
                        "Choose whether to close PTT so you can hear this room, or keep PTT open and enter with no room audio."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        LivePttSessionManager.disconnect(context)
                        viewModel.setRoomAudioMutedForPtt(false)
                        showPttConflictDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafetyEmerald,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("room_ptt_conflict_close_ptt_button")
                ) {
                    Text(
                        text = "Close PTT & Hear Room",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setRoomAudioMutedForPtt(true)
                        showPttConflictDialog = false
                    },
                    modifier = Modifier.testTag("room_ptt_conflict_keep_ptt_button")
                ) {
                    Text(
                        text = "Keep PTT (No Room Audio)",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }

    transferTarget?.let { target ->
        SendUserToRoomDialog(
            targetName = target.displayName,
            availableRooms = availableRooms,
            currentRoomId = viewModel.roomId,
            onSendToRoom = { destinationRoom, reason ->
                onSendUserToRoom(
                    target.userId,
                    target.displayName,
                    destinationRoom,
                    viewModel.roomId,
                    reason
                )
                transferTarget = null
            },
            onDismiss = { transferTarget = null }
        )
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

    supportSuspensionTarget?.let { target ->
        RequestSuspensionDialog(
            targetName = target.displayName,
            onSubmit = { reason ->
                viewModel.requestSupportSuspension(
                    targetUserId = target.userId,
                    targetName = target.displayName,
                    actorName = userProfile.displayName,
                    reason = reason
                )
                supportSuspensionTarget = null
            },
            onDismiss = { supportSuspensionTarget = null }
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
            onSave = { displayName, handle, bio, globallyBanned, disableCreateRoom, disableEnterRoom, disableMic, disableVideo, suspendedUntilMillis, suspensionReason, pointsBalance, proLifetime, disableMessaging ->
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
                    actorName = userProfile.displayName,
                    pointsBalance = pointsBalance,
                    proLifetime = proLifetime,
                    disableMessaging = disableMessaging
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
    invisibleAccent: Color,
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
                .padding(horizontal = 6.dp, vertical = 8.dp),
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
                    if (room.pinLocked) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ModAmber.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Pin,
                                    contentDescription = "PIN locked room",
                                    tint = ModAmber,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "PIN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ModAmber
                                )
                            }
                        }
                    }
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
                        tint = if (isCurrentlyInvisible) invisibleAccent else MaterialTheme.colorScheme.onSurfaceVariant,
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
    liveKitRoom: Room,
    localVideoTrack: LocalVideoTrack?,
    remoteVideoTracks: Map<String, RemoteVideoTrack>,
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
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse stage" else "Expand stage",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(5.dp))

                if (isCamActive || remoteVideoTracks.isNotEmpty()) {
                    val primaryRemote = remoteVideoTracks.entries.firstOrNull()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (primaryRemote != null && isCamActive) 156.dp else 136.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0A0E17))
                    ) {
                        when {
                            primaryRemote != null -> {
                                LiveKitVideoView(
                                    room = liveKitRoom,
                                    videoTrack = primaryRemote.value,
                                    mirror = false,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (isCamActive && localVideoTrack != null) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .size(width = 76.dp, height = 100.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.Black)
                                    ) {
                                        LiveKitVideoView(
                                            room = liveKitRoom,
                                            videoTrack = localVideoTrack,
                                            mirror = useFrontCamera,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                            localVideoTrack != null -> {
                                LiveKitVideoView(
                                    room = liveKitRoom,
                                    videoTrack = localVideoTrack,
                                    mirror = useFrontCamera,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            else -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Starting camera…",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LivePulseBadge(label = "ON AIR · YOU")
                            Surface(
                                shape = CircleShape,
                                color = Color(0xAA0A0D16),
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable(onClick = onFlipCamera)
                                    .testTag("flip_camera_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Cameraswitch,
                                        contentDescription = "Flip camera",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
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
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                    Spacer(modifier = Modifier.height(6.dp))
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(stageParticipants, key = { it.userId }) { p ->
                        val isSelf = p.userId == currentUserId
                        val effectiveAmp = if (isSelf) liveAmplitude else p.speakingLevel.toFloat()
                        val effectiveMic = if (isSelf) isMicActive else p.micActive
                        val effectiveCam = if (isSelf) isCamActive else p.camActive
                        val tier = p.staffTierEnum()

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (effectiveMic) 1.5.dp else 1.dp,
                                color = if (effectiveMic) VoiceCyan else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .clickable { onSelectParticipant(p) }
                                .testTag("stage_participant_${p.userId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    SpeakingAvatarCircle(
                                        displayName = p.displayName,
                                        colorHex = p.avatarColorHex,
                                        speakingLevel = effectiveAmp,
                                        micActive = effectiveMic,
                                        size = 34.dp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
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
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val isRoomLeader = p.roleEnum() == ParticipantRole.HOST ||
                                            p.roleEnum() == ParticipantRole.MODERATOR
                                        Text(
                                            text = if (isSelf) "${p.displayName} (You)" else p.displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = usernameColor(tier, isRoomLeader = isRoomLeader, userId = p.userId),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (tier != StaffTier.USER) {
                                            StaffTierBadge(tier = tier)
                                        }
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
}

@Composable
private fun ChatTimelineSection(
    room: ChatRoom,
    messages: List<ChatMessage>,
    currentUserId: String,
    onTapMessage: (ChatMessage) -> Unit,
    onToggleReaction: (ChatMessage, String) -> Unit,
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
                    text = "Say hello to kick off the live chat! Tap any message to moderate or react with emojis.",
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
                    val msgRole = ParticipantRole.fromRaw(msg.senderRole)
                    val isSenderRoomLeader = msgRole == ParticipantRole.HOST ||
                        msgRole == ParticipantRole.MODERATOR ||
                        msg.senderId == room.hostId ||
                        room.moderatorIds.contains(msg.senderId)
                    ChatMessageBubble(
                        message = msg,
                        currentUserId = currentUserId,
                        isMine = msg.senderId == currentUserId,
                        isSenderRoomLeader = isSenderRoomLeader,
                        onClick = { onTapMessage(msg) },
                        onToggleReaction = { emoji -> onToggleReaction(msg, emoji) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    currentUserId: String,
    isMine: Boolean,
    isSenderRoomLeader: Boolean = false,
    onClick: () -> Unit,
    onToggleReaction: (String) -> Unit
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
    var showReactionPicker by remember(message.id) { mutableStateOf(false) }
    val activeReactions = remember(message.reactionsByEmoji) { message.nonEmptyReactions() }

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
            verticalArrangement = Arrangement.spacedBy(5.dp)
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
                        color = usernameColor(senderTier, isRoomLeader = isSenderRoomLeader, userId = message.senderId),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formatTimestampShort(message.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!message.deletedByMod) {
                        IconButton(
                            onClick = { showReactionPicker = !showReactionPicker },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("react_msg_${message.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddReaction,
                                contentDescription = "React to message with emoji",
                                tint = if (showReactionPicker) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = message.text,
                style = if (type == MessageType.REACTION) MaterialTheme.typography.headlineSmall
                else MaterialTheme.typography.bodyMedium,
                fontStyle = if (message.deletedByMod) FontStyle.Italic else FontStyle.Normal,
                color = if (message.deletedByMod) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )

            // Expandable Quick Emoji Picker Strip
            if (showReactionPicker && !message.deletedByMod) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.testTag("reaction_picker_${message.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ROOM_REACTION_EMOJIS.forEach { emoji ->
                            val reacted = message.hasReacted(emoji, currentUserId)
                            Surface(
                                shape = CircleShape,
                                color = if (reacted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        showReactionPicker = false
                                        onToggleReaction(emoji)
                                    }
                                    .testTag("react_pick_${message.id}_$emoji")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = emoji,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Active Emoji Reaction Pills (stored in Firestore)
            if (activeReactions.isNotEmpty() && !message.deletedByMod) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeReactions.forEach { (emoji, uids) ->
                        val mine = uids.contains(currentUserId)
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (mine) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (mine) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onToggleReaction(emoji) }
                                .testTag("reaction_chip_${message.id}_$emoji")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = emoji,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "${uids.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (mine) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
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
    val isMessagingDisabled = !staffCapabilities.isAdmin && userProfile.disableMessaging
    val isChatBlocked = isMuted || isMessagingDisabled
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
    val maxPttSec = userProfile.pttMaxDurationSeconds(staffCapabilities.tier)
    var pttHeldSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(isMicActive, pushToTalkMode, maxPttSec) {
        if (isMicActive && pushToTalkMode) {
            pttHeldSeconds = 0
            while (pttHeldSeconds < maxPttSec) {
                kotlinx.coroutines.delay(1000L)
                pttHeldSeconds += 1
            }
            onMicStateChange(false)
        } else {
            pttHeldSeconds = 0
        }
    }

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
                    enabled = !isChatBlocked,
                    placeholder = {
                        Text(
                            text = when {
                                isMessagingDisabled -> "Messaging disabled by Staff"
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
                    color = if (messageInput.isNotBlank() && !isChatBlocked && slowModeCountdown == 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(
                            enabled = messageInput.isNotBlank() && !isChatBlocked && slowModeCountdown == 0,
                            onClick = onSend
                        )
                        .testTag("send_message_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send message",
                            tint = if (messageInput.isNotBlank() && !isChatBlocked && slowModeCountdown == 0) {
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
                            modifier = Modifier.clickable(enabled = !isChatBlocked) { onSendReaction(emoji) }
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
                                isMicActive && pushToTalkMode -> "PTT (${pttHeldSeconds}s/${maxPttSec}s)"
                                isMicActive && staffCapabilities.isAdmin && isCurrentlyInvisible -> "Admin Mic (Ghost)"
                                isMicActive && staffCapabilities.isAdmin -> "Admin Priority Mic"
                                isMicActive -> "Live Mic"
                                pushToTalkMode -> "Hold to Talk (${maxPttSec}s ${if (maxPttSec >= 60) "Pro" else "Free"})"
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
