package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.DirectMessage
import com.example.data.DirectThread
import com.example.data.DirectoryEntry
import com.example.data.DmAccessPass
import com.example.data.DmPolicy
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.media.LiveKitVideoView
import com.example.media.LivePttSessionManager
import com.example.media.VoiceNotePlayer
import com.example.media.VoiceNoteRecorder
import com.example.media.formatVoiceDuration
import com.example.notifications.DirectMessageNotificationHelper
import com.example.notifications.DirectMessageNotificationPrefs
import com.example.notifications.VoxStageNotificationState
import com.example.ui.components.FullScreenPhotoDialog
import com.example.ui.components.OnlinePresenceDot
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.TopSnackbarOverlay
import com.example.ui.components.encodeBitmapToBase64Jpeg
import com.example.ui.components.encodeImageUriToBase64Jpeg
import com.example.ui.components.formatTimestampShort
import com.example.ui.components.rememberBase64ImageBitmap
import com.example.ui.components.rememberPresenceClockMillis
import com.example.ui.components.showTopMessage
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.PresenceOnlineGreen
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.SnapBlue
import com.example.viewmodel.DirectChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectChatScreen(
    viewModel: DirectChatViewModel,
    userProfile: UserProfile,
    myStaffTier: StaffTier,
    otherPhotoBase64: String = "",
    directoryEntries: List<DirectoryEntry> = emptyList(),
    paidDmMembersForMe: List<DmAccessPass> = emptyList(),
    isAcceptedFriend: Boolean = false,
    onAddUserToChat: (DirectThread, DirectoryEntry) -> Unit = { _, _ -> },
    onKickPaidMember: (senderId: String, senderName: String) -> Unit = { _, _ -> },
    onBlockContact: (targetUserId: String, targetName: String) -> Unit = { _, _ -> },
    onUnblockContact: (targetUserId: String) -> Unit = {},
    onSendFriendAttentionAlert: (DirectThread) -> Unit = {},
    onOpenUserProfile: (String) -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val threadState by viewModel.threadState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val isInCall by viewModel.isInCall.collectAsStateWithLifecycle()
    val isConnectingCall by viewModel.isConnectingCall.collectAsStateWithLifecycle()
    val isMicActive by viewModel.isMicActive.collectAsStateWithLifecycle()
    val isCamActive by viewModel.isCamActive.collectAsStateWithLifecycle()
    val useFrontCamera by viewModel.useFrontCamera.collectAsStateWithLifecycle()
    val localVideoTrack by viewModel.liveKitManager.localVideoTrack.collectAsStateWithLifecycle()
    val remoteVideoTracks by viewModel.liveKitManager.remoteVideoTracks.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var composerText by remember { mutableStateOf("") }
    var draftSnapBase64 by remember { mutableStateOf("") }
    var isEncodingSnap by remember { mutableStateOf(false) }
    var fullScreenPhotoBase64 by remember { mutableStateOf<String?>(null) }
    var pendingCameraAction by remember { mutableStateOf<String?>(null) } // "VIDEO_CALL" or "SNAP_PHOTO"
    var pendingMicAction by remember { mutableStateOf<String?>(null) } // "VOICE_CALL", "VOICE_NOTE", or "PTT"
    var showAddPersonSheet by remember { mutableStateOf(false) }
    var showKickPaidMemberDialog by remember { mutableStateOf(false) }
    var showBlockContactDialog by remember { mutableStateOf(false) }
    var showChatPrivacyDialog by remember { mutableStateOf(false) }
    val globalReadReceiptsEnabled = remember(context) {
        DirectMessageNotificationPrefs.isReadReceiptsEnabled(context)
    }

    // Live Walkie-Talkie PTT state (synced across both users via Firestore + LivePttSessionManager)
    val pttActiveThreadId by LivePttSessionManager.activeThreadId.collectAsStateWithLifecycle()
    val pttIsConnected by LivePttSessionManager.isConnected.collectAsStateWithLifecycle()
    val pttIsConnecting by LivePttSessionManager.isConnecting.collectAsStateWithLifecycle()
    val pttIsTransmitting by LivePttSessionManager.isTransmitting.collectAsStateWithLifecycle()
    val pttRemoteSpeaker by LivePttSessionManager.remoteSpeakingName.collectAsStateWithLifecycle()
    val isThisThreadLivePttConnected = pttActiveThreadId == viewModel.threadId && pttIsConnected
    val isThisThreadLivePttConnecting = pttActiveThreadId == viewModel.threadId && pttIsConnecting
    val isThisThreadTransmitting = pttActiveThreadId == viewModel.threadId && pttIsTransmitting
    val thisThreadRemoteSpeaker = if (pttActiveThreadId == viewModel.threadId) pttRemoteSpeaker else null

    var canDrawFloatingOverlay by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawFloatingOverlay =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Voice Note Recording & Playback State
    val voiceRecorder = remember { VoiceNoteRecorder(context) }
    val voicePlayer = remember { VoiceNotePlayer(context) }
    var isRecordingVoiceNote by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    val playingVoiceMessageId by voicePlayer.playingMessageId.collectAsStateWithLifecycle()
    val voicePlaybackProgress by voicePlayer.progressFraction.collectAsStateWithLifecycle()

    fun startVoiceNoteRecording() {
        voicePlayer.stop()
        if (voiceRecorder.startRecording()) {
            recordingSeconds = 0
            isRecordingVoiceNote = true
        } else {
            scope.launch {
                snackbarHostState.showTopMessage("Could not start voice note recording.")
            }
        }
    }

    fun cancelVoiceNoteRecording() {
        voiceRecorder.cancel()
        isRecordingVoiceNote = false
        recordingSeconds = 0
    }

    fun stopAndSendVoiceNote() {
        if (!isRecordingVoiceNote) return
        isRecordingVoiceNote = false
        recordingSeconds = 0
        scope.launch {
            val note = voiceRecorder.stopAndEncode()
            if (note != null) {
                val caption = composerText.trim()
                composerText = ""
                viewModel.sendMessage(
                    senderName = userProfile.displayName,
                    senderTier = myStaffTier,
                    text = caption,
                    audioBase64 = note.audioBase64,
                    audioDurationSec = note.durationSec
                )
            } else {
                snackbarHostState.showTopMessage("Recording was too short or could not be saved.")
            }
        }
    }

    LaunchedEffect(isRecordingVoiceNote) {
        if (isRecordingVoiceNote) {
            while (isRecordingVoiceNote && recordingSeconds < VoiceNoteRecorder.MAX_DURATION_SECONDS) {
                delay(1000L)
                if (isRecordingVoiceNote) {
                    recordingSeconds += 1
                }
            }
            if (isRecordingVoiceNote) {
                stopAndSendVoiceNote()
            }
        }
    }

    val cameraSnapLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            scope.launch {
                isEncodingSnap = true
                val encoded = encodeBitmapToBase64Jpeg(bitmap)
                isEncodingSnap = false
                if (!encoded.isNullOrBlank()) {
                    draftSnapBase64 = encoded
                } else {
                    snackbarHostState.showTopMessage("Could not prepare snapped photo.")
                }
            }
        }
    }

    val galleryPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isEncodingSnap = true
                val encoded = encodeImageUriToBase64Jpeg(
                    context = context,
                    uri = uri,
                    maxDimensionPx = 720,
                    squareCrop = false,
                    maxBase64Chars = 360_000
                )
                isEncodingSnap = false
                if (!encoded.isNullOrBlank()) {
                    draftSnapBase64 = encoded
                }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingMicAction ?: "VOICE_CALL"
        pendingMicAction = null
        if (granted) {
            when (action) {
                "VOICE_NOTE" -> startVoiceNoteRecording()
                "PTT" -> {
                    val activeThread = (threadState as? UiState.Success)?.data
                    if (activeThread != null && activeThread.isMutualPttActive(viewModel.currentUserId)) {
                        LivePttSessionManager.ensureConnected(
                            context = context,
                            threadId = activeThread.id,
                            peerName = activeThread.displayTitle(viewModel.currentUserId),
                            myUserId = viewModel.currentUserId,
                            myDisplayName = userProfile.displayName
                        )
                    }
                    scope.launch {
                        snackbarHostState.showTopMessage("Microphone ready! Hold Push to Talk to stream live audio.")
                    }
                }
                else -> viewModel.toggleCallMic(userProfile.displayName)
            }
        } else {
            scope.launch {
                snackbarHostState.showTopMessage(
                    when (action) {
                        "VOICE_NOTE" -> "Microphone permission is needed to send voice notes."
                        "PTT" -> "Microphone permission is needed for Push to Talk."
                        else -> "Microphone permission is needed for private voice calls."
                    }
                )
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingCameraAction
        pendingCameraAction = null
        if (granted) {
            when (action) {
                "VIDEO_CALL" -> viewModel.toggleCallVideo(userProfile.displayName)
                "SNAP_PHOTO" -> cameraSnapLauncher.launch(null)
            }
        } else {
            scope.launch { snackbarHostState.showTopMessage("Camera permission is required for video calls and snaps.") }
        }
    }

    fun requestMicCallToggle() {
        if (!myStaffTier.isAdmin() && userProfile.disableMic) {
            scope.launch { snackbarHostState.showTopMessage("Microphone access is disabled on your account.") }
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.toggleCallMic(userProfile.displayName)
        } else {
            pendingMicAction = "VOICE_CALL"
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun requestVoiceNoteStart() {
        if (!myStaffTier.isAdmin() && userProfile.disableMic) {
            scope.launch { snackbarHostState.showTopMessage("Microphone access is disabled on your account.") }
            return
        }
        if (isInCall && isMicActive) {
            scope.launch { snackbarHostState.showTopMessage("Mute your live call mic first to record a voice note.") }
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            startVoiceNoteRecording()
        } else {
            pendingMicAction = "VOICE_NOTE"
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun requestVideoCallToggle() {
        if (!myStaffTier.isAdmin() && userProfile.disableVideo) {
            scope.launch { snackbarHostState.showTopMessage("Video calling is disabled on your account.") }
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.toggleCallVideo(userProfile.displayName)
        } else {
            pendingCameraAction = "VIDEO_CALL"
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun requestCameraSnap() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            cameraSnapLauncher.launch(null)
        } else {
            pendingCameraAction = "SNAP_PHOTO"
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(viewModel.threadId) {
        VoxStageNotificationState.activeDmThreadId = viewModel.threadId
        DirectMessageNotificationHelper.cancelThreadNotification(context, viewModel.threadId)
        onDispose {
            voiceRecorder.cancel()
            voicePlayer.release()
            if (VoxStageNotificationState.activeDmThreadId == viewModel.threadId) {
                VoxStageNotificationState.activeDmThreadId = null
            }
        }
    }

    val thread = (threadState as? UiState.Success)?.data
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val hasDisappearingMessages = remember(messages) { messages.any { it.isDisappearing() } }
    var expiryClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(hasDisappearingMessages) {
        if (hasDisappearingMessages) {
            while (true) {
                expiryClockMs = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    LaunchedEffect(messages, expiryClockMs) {
        if (hasDisappearingMessages) {
            viewModel.purgeExpiredSentMessages(messages, expiryClockMs)
        }
    }

    val visibleMessages = remember(messages, expiryClockMs) {
        messages.filterNot { it.isExpired(expiryClockMs) }
    }

    val isMyChatReadReceiptsEnabled = globalReadReceiptsEnabled &&
        (thread?.isReadReceiptsEnabledFor(viewModel.currentUserId) != false)

    LaunchedEffect(thread?.id, thread?.unreadByUserIds, thread?.updatedAt, isMyChatReadReceiptsEnabled) {
        viewModel.markReadIfNeeded(thread, sendReadReceipt = isMyChatReadReceiptsEnabled)
    }

    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showTopMessage(msg)
        }
    }

    val isGroup = thread?.isGroup == true
    val otherName = thread?.displayTitle(viewModel.currentUserId) ?: "Private Chat"
    val otherColorHex = thread?.otherColorHex(viewModel.currentUserId) ?: "#0088FF"
    val otherTier = if (isGroup) StaffTier.USER else (thread?.otherTier(viewModel.currentUserId) ?: StaffTier.USER)
    val otherUid = thread?.otherUserId(viewModel.currentUserId).orEmpty()
    val nowMillis = rememberPresenceClockMillis()
    val otherDirectoryEntry = remember(directoryEntries, otherUid) {
        directoryEntries.firstOrNull { it.userId == otherUid }
    }
    val isOtherOnline = !isGroup && (otherDirectoryEntry?.isCurrentlyOnline(nowMillis) == true)
    val amIKicked = thread?.isUserKicked(viewModel.currentUserId) == true
    val iBlockedOther = !isGroup && (
        thread?.isBlockedBy(viewModel.currentUserId) == true ||
            (otherUid.isNotBlank() && userProfile.blockedUserIds.contains(otherUid))
        )
    val isBlockedThread = !isGroup && (thread?.hasAnyBlock() == true || iBlockedOther)
    val otherPaidPass = remember(paidDmMembersForMe, otherUid, isGroup) {
        if (!isGroup && otherUid.isNotBlank()) {
            paidDmMembersForMe.firstOrNull { it.senderId == otherUid && it.isCurrentlyAllowed() }
        } else null
    }
    val canKickOtherPaidMember = !isGroup && otherUid.isNotBlank() && !isBlockedThread &&
        (otherPaidPass != null || (userProfile.dmPolicyEnum() == DmPolicy.PAID && thread?.isUserKicked(otherUid) != true))
    val isMyPttEnabled = thread?.isPttEnabledFor(viewModel.currentUserId) == true
    val isOtherPttEnabled = thread?.isOtherPttEnabled(viewModel.currentUserId) == true
    val isMutualPttActive = !amIKicked && !isBlockedThread && (thread?.isMutualPttActive(viewModel.currentUserId) == true)
    val remoteVideoTrack = remoteVideoTracks.values.firstOrNull()
    val canSend = !amIKicked && !isBlockedThread && (composerText.isNotBlank() || draftSnapBase64.isNotBlank()) && !isSending && !isEncodingSnap
    val remainingPictureQuota = remember(userProfile.pictureUploadTimestamps) {
        userProfile.remainingPictureUploads()
    }

    LaunchedEffect(isMutualPttActive, thread?.id, otherName, userProfile.displayName) {
        val currentThread = thread
        if (isMutualPttActive && currentThread != null && !(!myStaffTier.isAdmin() && userProfile.disableMic)) {
            val micGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (micGranted) {
                LivePttSessionManager.ensureConnected(
                    context = context,
                    threadId = currentThread.id,
                    peerName = otherName,
                    myUserId = viewModel.currentUserId,
                    myDisplayName = userProfile.displayName
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("dm_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back to Chats",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = !isGroup && otherUid.isNotBlank()) {
                                        onOpenUserProfile(otherUid)
                                    }
                                    .testTag("dm_header_profile_link")
                            ) {
                                if (isGroup) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Groups,
                                            contentDescription = "Group chat",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                } else {
                                    SpeakingAvatarCircle(
                                        displayName = otherName,
                                        colorHex = otherColorHex,
                                        speakingLevel = 0f,
                                        micActive = isInCall && isMicActive,
                                        size = 40.dp,
                                        photoBase64 = otherPhotoBase64
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (!isGroup) {
                                            OnlinePresenceDot(
                                                isOnline = isOtherOnline,
                                                modifier = Modifier.testTag("dm_header_presence_dot")
                                            )
                                        }
                                        Text(
                                            text = otherName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isGroup) MaterialTheme.colorScheme.onSurface else usernameColor(otherTier),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (!isGroup) {
                                            StaffTierBadge(tier = otherTier)
                                        }
                                    }
                                    Text(
                                        text = when {
                                            isInCall -> if (isGroup) "Live Group Call Active" else "Live Call Active"
                                            isGroup -> "${thread?.participantIds?.size ?: 0} members · ${thread?.memberNamesSummary().orEmpty()}"
                                            isOtherOnline -> "Online · Tap profile"
                                            else -> "Offline · Tap profile"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            isInCall -> SafetyEmerald
                                            !isGroup && isOtherOnline -> PresenceOnlineGreen
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Disappearing Messages & Read Receipts Options
                            if (!amIKicked && !isBlockedThread && thread != null) {
                                val disappearingOn = thread.isDisappearingEnabled()
                                IconButton(
                                    onClick = { showChatPrivacyDialog = true },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("dm_privacy_options_button")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (disappearingOn) ModAmber else MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Timer,
                                                contentDescription = "Disappearing messages and read receipts",
                                                tint = if (disappearingOn) Color.White else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Add Person -> Creates Group Chat (or adds to Group Chat)
                            if (!amIKicked && !isBlockedThread && thread != null) {
                                IconButton(
                                    onClick = { showAddPersonSheet = true },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("dm_add_user_button")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.GroupAdd,
                                                contentDescription = if (isGroup) "Add member to group chat" else "Add user to create group chat",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Firestore-synced Live Push-to-Talk Toggle (mutual live audio stream when both select it)
                            if (!amIKicked && !isBlockedThread && thread != null) {
                                IconButton(
                                    onClick = {
                                        val next = !isMyPttEnabled
                                        if (next) {
                                            val micGranted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (!micGranted) {
                                                pendingMicAction = "PTT"
                                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                        viewModel.setPttEnabled(next, isAdmin = myStaffTier == StaffTier.ADMIN)
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("dm_toggle_ptt_button")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = when {
                                            isMutualPttActive -> SafetyEmerald
                                            isMyPttEnabled -> SnapBlue
                                            isOtherPttEnabled -> ModAmber.copy(alpha = 0.25f)
                                            else -> MaterialTheme.colorScheme.primaryContainer
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.RecordVoiceOver,
                                                contentDescription = if (isMyPttEnabled) "Disable Live Push to Talk" else "Enable Live Push to Talk",
                                                tint = when {
                                                    isMutualPttActive || isMyPttEnabled -> Color.White
                                                    isOtherPttEnabled -> ModAmber
                                                    else -> MaterialTheme.colorScheme.primary
                                                },
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Friend Attention Alert (Ring + Vibrate, friends list only, 1 per 5 min)
                            if (!isGroup && isAcceptedFriend && !amIKicked && !isBlockedThread && thread != null) {
                                IconButton(
                                    onClick = { onSendFriendAttentionAlert(thread) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("dm_send_attention_alert_button")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = ModAmber.copy(alpha = 0.20f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.NotificationsActive,
                                                contentDescription = "Send ring and vibrate attention alert to friend",
                                                tint = ModAmber,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Kick Paid Member button (for locked $10 Paid DM owners)
                            if (canKickOtherPaidMember) {
                                IconButton(
                                    onClick = { showKickPaidMemberDialog = true },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("dm_kick_paid_member_button")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = LiveCoral.copy(alpha = 0.16f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.PersonRemove,
                                                contentDescription = "Kick paid member from messaging you",
                                                tint = LiveCoral,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Moderation: Block Contact (removes from friends list & stops incoming messages)
                            if (!isGroup && otherUid.isNotBlank() && !iBlockedOther) {
                                IconButton(
                                    onClick = { showBlockContactDialog = true },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("dm_block_contact_button")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = LiveCoral.copy(alpha = 0.14f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Block,
                                                contentDescription = "Block $otherName",
                                                tint = LiveCoral,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Private Chat Voice Mic Call Button
                            if (!amIKicked && !isBlockedThread) {
                                IconButton(
                                    onClick = ::requestMicCallToggle,
                                    enabled = !isConnectingCall,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("dm_mic_call_button")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isMicActive) SafetyEmerald else MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isMicActive) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                                                contentDescription = if (isMicActive) "Mute private call mic" else "Start private voice mic",
                                                tint = if (isMicActive) Color.White else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }

                                // Private Chat Live Video Call Button
                                IconButton(
                                    onClick = ::requestVideoCallToggle,
                                    enabled = !isConnectingCall,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("dm_video_call_button")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isCamActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isCamActive) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                                                contentDescription = if (isCamActive) "Turn off private video" else "Start private video call",
                                                tint = if (isCamActive) Color.White else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (thread?.isDisappearingEnabled() == true) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ModAmber.copy(alpha = 0.14f))
                                    .clickable { showChatPrivacyDialog = true }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                    .testTag("dm_disappearing_active_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = "Disappearing messages active",
                                    tint = ModAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Disappearing messages ON (${thread.disappearingLabel()}) · New messages auto-vanish",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Edit",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        if (amIKicked) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LiveCoral.copy(alpha = 0.12f))
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("dm_kicked_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Block,
                                    contentDescription = "Blocked from messaging",
                                    tint = LiveCoral,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "You were removed from messaging in this private chat.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = LiveCoral
                                )
                            }
                        } else if (isBlockedThread) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LiveCoral.copy(alpha = 0.12f))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("dm_blocked_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Block,
                                    contentDescription = "Contact blocked",
                                    tint = LiveCoral,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (iBlockedOther) {
                                        "You blocked $otherName. Incoming messages are stopped."
                                    } else {
                                        "Messaging is unavailable in this chat."
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = LiveCoral,
                                    modifier = Modifier.weight(1f)
                                )
                                if (iBlockedOther && otherUid.isNotBlank()) {
                                    TextButton(
                                        onClick = { onUnblockContact(otherUid) },
                                        modifier = Modifier.testTag("dm_unblock_contact_button")
                                    ) {
                                        Text(
                                            text = "Unblock",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                        // Mutual Live Push-to-Talk Walkie-Talkie Strip (Live stream audio — never chat-log voice notes)
                        if (isOtherPttEnabled && !isMyPttEnabled && thread != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SnapBlue.copy(alpha = 0.12f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("dm_ptt_invite_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RecordVoiceOver,
                                    contentDescription = "Live Push to Talk invite",
                                    tint = SnapBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "$otherName turned on Live Push-to-Talk. Enable to stream live audio.",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        val micGranted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (!micGranted) {
                                            pendingMicAction = "PTT"
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                        viewModel.setPttEnabled(true, isAdmin = myStaffTier == StaffTier.ADMIN)
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SafetyEmerald),
                                    modifier = Modifier.testTag("dm_ptt_accept_button")
                                ) {
                                    Text(
                                        text = "Enable",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        } else if (isMyPttEnabled && thread != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isMutualPttActive) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = when {
                                            isThisThreadTransmitting -> LiveCoral
                                            !thisThreadRemoteSpeaker.isNullOrBlank() -> SnapBlue
                                            else -> SafetyEmerald
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .pointerInput(thread.id, otherName, userProfile.displayName, isInCall) {
                                                awaitEachGesture {
                                                    awaitFirstDown(requireUnconsumed = false)
                                                    if (!myStaffTier.isAdmin() && userProfile.disableMic) {
                                                        scope.launch {
                                                            snackbarHostState.showTopMessage("Microphone access is disabled on your account.")
                                                        }
                                                        waitForUpOrCancellation()
                                                        return@awaitEachGesture
                                                    }
                                                    val granted = ContextCompat.checkSelfPermission(
                                                        context,
                                                        Manifest.permission.RECORD_AUDIO
                                                    ) == PackageManager.PERMISSION_GRANTED
                                                    if (!granted) {
                                                        pendingMicAction = "PTT"
                                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                        waitForUpOrCancellation()
                                                        return@awaitEachGesture
                                                    }
                                                    if (isInCall) {
                                                        viewModel.setPttLiveMic(userProfile.displayName, true)
                                                        waitForUpOrCancellation()
                                                        viewModel.setPttLiveMic(userProfile.displayName, false)
                                                    } else {
                                                        LivePttSessionManager.setTransmitting(
                                                            context = context,
                                                            transmitting = true,
                                                            threadId = thread.id,
                                                            peerName = otherName,
                                                            myUserId = viewModel.currentUserId,
                                                            myDisplayName = userProfile.displayName
                                                        )
                                                        waitForUpOrCancellation()
                                                        LivePttSessionManager.setTransmitting(
                                                            context = context,
                                                            transmitting = false,
                                                            threadId = thread.id,
                                                            peerName = otherName,
                                                            myUserId = viewModel.currentUserId,
                                                            myDisplayName = userProfile.displayName
                                                        )
                                                    }
                                                }
                                            }
                                            .testTag("dm_ptt_button")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.RecordVoiceOver,
                                                contentDescription = "Live Push to Talk",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = when {
                                                    isThisThreadTransmitting || (isInCall && isMicActive) ->
                                                        "LIVE AUDIO STREAMING to $otherName…"
                                                    !thisThreadRemoteSpeaker.isNullOrBlank() ->
                                                        "$thisThreadRemoteSpeaker is streaming live audio…"
                                                    isThisThreadLivePttConnecting ->
                                                        "Connecting live Push-to-Talk channel…"
                                                    isThisThreadLivePttConnected ->
                                                        "HOLD TO TALK LIVE · $otherName"
                                                    else ->
                                                        "HOLD TO STREAM LIVE AUDIO · $otherName"
                                                },
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Floating Background PTT Button Permission / Status Row
                                    if (!canDrawFloatingOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    val intent = Intent(
                                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        Uri.parse("package:${context.packageName}")
                                                    )
                                                    context.startActivity(intent)
                                                }
                                                .testTag("dm_ptt_enable_floating_overlay_button")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.GraphicEq,
                                                    contentDescription = "Enable floating background button",
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Floating PTT is active in-app · Tap to also float over other apps",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(SafetyEmerald)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Live audio stream · Floating PTT stays active in-app & in background",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    // Only current user has PTT enabled so far — waiting on the other user
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, SnapBlue.copy(alpha = 0.45f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                scope.launch {
                                                    snackbarHostState.showTopMessage(
                                                        "Waiting for $otherName to also select Push-to-Talk for live audio streaming."
                                                    )
                                                }
                                            }
                                            .testTag("dm_ptt_waiting_banner")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.RecordVoiceOver,
                                                contentDescription = "Waiting for Push to Talk",
                                                tint = SnapBlue,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Live Push-to-Talk ON · Waiting for $otherName",
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Live audio streaming & floating PTT button activate once both users select PTT.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        }

                        // Draft Snap Picture Preview Strip
                        if (isEncodingSnap || draftSnapBase64.isNotBlank()) {
                            val draftBmp = rememberBase64ImageBitmap(draftSnapBase64)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isEncodingSnap) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Preparing snap picture…",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (draftBmp != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Image(
                                            bitmap = draftBmp,
                                            contentDescription = "Draft snap preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Snap ready to send",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Add a caption or tap Send · $remainingPictureQuota/10 pics left (10m)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { draftSnapBase64 = "" },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("dm_remove_draft_snap")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove snapped photo",
                                            tint = LiveCoral
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        }

                        if (isRecordingVoiceNote) {
                            // Live Voice Note Recording Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LiveCoral.copy(alpha = 0.12f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("dm_voice_recording_bar"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                IconButton(
                                    onClick = ::cancelVoiceNoteRecording,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("dm_voice_cancel_button")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Cancel voice note",
                                                tint = LiveCoral,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(LiveCoral)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Recording voice note…",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = LiveCoral
                                    )
                                    Text(
                                        text = "${formatVoiceDuration(recordingSeconds.toLong())} / ${formatVoiceDuration(VoiceNoteRecorder.MAX_DURATION_SECONDS.toLong())}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = LiveCoral,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    IconButton(
                                        onClick = ::stopAndSendVoiceNote,
                                        modifier = Modifier.testTag("dm_voice_send_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Send,
                                            contentDescription = "Send voice note",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Snap Picture with Camera
                            IconButton(
                                onClick = ::requestCameraSnap,
                                enabled = !isSending && !isEncodingSnap,
                                modifier = Modifier
                                    .size(46.dp)
                                    .testTag("dm_snap_camera_button")
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.PhotoCamera,
                                            contentDescription = "Snap picture with camera",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }

                            // Pick Photo from Gallery
                            IconButton(
                                onClick = {
                                    galleryPhotoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isSending && !isEncodingSnap,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("dm_snap_gallery_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AddPhotoAlternate,
                                    contentDescription = "Send photo from gallery",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            // Record Voice Note
                            IconButton(
                                onClick = ::requestVoiceNoteStart,
                                enabled = !isSending && !isEncodingSnap,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("dm_voice_note_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "Record voice note",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            OutlinedTextField(
                                value = composerText,
                                onValueChange = { composerText = it.take(500) },
                                placeholder = {
                                    Text(
                                        if (draftSnapBase64.isNotBlank()) "Add a caption…"
                                        else stringResource(R.string.hint_send_dm)
                                    )
                                },
                                singleLine = false,
                                maxLines = 3,
                                shape = RoundedCornerShape(999.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dm_message_input")
                            )

                            Surface(
                                shape = CircleShape,
                                color = if (canSend) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val toSend = composerText.trim()
                                        val snapToSend = draftSnapBase64
                                        if (toSend.isNotEmpty() || snapToSend.isNotBlank()) {
                                            composerText = ""
                                            draftSnapBase64 = ""
                                            viewModel.sendMessage(
                                                senderName = userProfile.displayName,
                                                senderTier = myStaffTier,
                                                text = toSend,
                                                imageBase64 = snapToSend
                                            )
                                        }
                                    },
                                    enabled = canSend,
                                    modifier = Modifier.testTag("dm_send_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "Send private message",
                                        tint = if (canSend) Color.White else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.surface),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // LIVE PRIVATE VOICE & VIDEO CALL STAGE
                if (isInCall || isConnectingCall) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF0C182E),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("dm_live_call_stage")
                    ) {
                        Column {
                            val hasAnyVideo = remoteVideoTrack != null || localVideoTrack != null
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (hasAnyVideo) 220.dp else 112.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    remoteVideoTrack != null -> {
                                        LiveKitVideoView(
                                            room = viewModel.liveKitManager.room,
                                            videoTrack = remoteVideoTrack,
                                            mirror = false,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (localVideoTrack != null) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(10.dp)
                                                    .size(width = 88.dp, height = 118.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color.Black)
                                            ) {
                                                LiveKitVideoView(
                                                    room = viewModel.liveKitManager.room,
                                                    videoTrack = localVideoTrack,
                                                    mirror = useFrontCamera,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }

                                    localVideoTrack != null -> {
                                        LiveKitVideoView(
                                            room = viewModel.liveKitManager.room,
                                            videoTrack = localVideoTrack,
                                            mirror = useFrontCamera,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = Color.Black.copy(alpha = 0.55f),
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(10.dp)
                                        ) {
                                            Text(
                                                text = "Your Camera · Waiting for $otherName",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    else -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            SpeakingAvatarCircle(
                                                displayName = otherName,
                                                colorHex = otherColorHex,
                                                speakingLevel = if (isMicActive) 0.7f else 0.1f,
                                                micActive = isMicActive,
                                                size = 54.dp,
                                                photoBase64 = otherPhotoBase64
                                            )
                                            Column {
                                                Text(
                                                    text = if (isConnectingCall) "Connecting private call…" else "Private Call with $otherName",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = if (isMicActive) "Microphone LIVE" else "Mic muted · Tap mic or camera below",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Call Controls Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF091224))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = ::requestMicCallToggle,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("dm_stage_mic_toggle")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isMicActive) SafetyEmerald else Color.White.copy(alpha = 0.16f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isMicActive) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                                                contentDescription = "Toggle mic",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = ::requestVideoCallToggle,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("dm_stage_cam_toggle")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isCamActive) SnapBlue else Color.White.copy(alpha = 0.16f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isCamActive) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                                                contentDescription = "Toggle video",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                if (isCamActive) {
                                    IconButton(
                                        onClick = { viewModel.flipCamera() },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("dm_stage_flip_cam")
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.16f),
                                            modifier = Modifier.size(40.dp)
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
                                }

                                IconButton(
                                    onClick = { viewModel.endCall() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("dm_stage_end_call")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = LiveCoral,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.CallEnd,
                                                contentDescription = "End private call",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // MESSAGES LIST
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when (messagesState) {
                        is UiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        is UiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (messagesState as UiState.Error).message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        is UiState.Success -> {
                            if (visibleMessages.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    SpeakingAvatarCircle(
                                        displayName = otherName,
                                        colorHex = otherColorHex,
                                        speakingLevel = 0f,
                                        micActive = false,
                                        size = 72.dp,
                                        photoBase64 = otherPhotoBase64
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = otherName,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = usernameColor(otherTier)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (thread?.isDisappearingEnabled() == true) {
                                            "Disappearing messages (${thread.disappearingLabel()}) are enabled in this chat."
                                        } else {
                                            "Send a message, voice note, snap a picture, or start a live mic/video call."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                val showReadReceipts = !isGroup &&
                                    otherUid.isNotBlank() &&
                                    globalReadReceiptsEnabled &&
                                    (thread?.areReadReceiptsMutual(viewModel.currentUserId, otherUid) == true)
                                val otherLastReadMs = if (otherUid.isNotBlank()) {
                                    thread?.lastReadMillisFor(otherUid) ?: 0L
                                } else {
                                    0L
                                }
                                val latestMyMessageId = remember(visibleMessages, viewModel.currentUserId) {
                                    visibleMessages.lastOrNull { it.senderId == viewModel.currentUserId }?.id
                                }
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .widthIn(max = 760.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(visibleMessages, key = { it.id }) { msg ->
                                        val isPlayingThis = playingVoiceMessageId == msg.id
                                        val isMine = msg.senderId == viewModel.currentUserId
                                        val msgCreatedMs = msg.createdAt?.toDate()?.time ?: 0L
                                        val isReadByOther = isMine && showReadReceipts &&
                                            msgCreatedMs > 0L && otherLastReadMs >= msgCreatedMs
                                        SnapChatMessageBlock(
                                            message = msg,
                                            isMe = isMine,
                                            nowMs = expiryClockMs,
                                            showReadReceipt = isMine && showReadReceipts,
                                            isReadByOther = isReadByOther,
                                            showReadTimeLabel = isMine && msg.id == latestMyMessageId && isReadByOther,
                                            otherReadMillis = otherLastReadMs,
                                            isPlayingVoice = isPlayingThis,
                                            voicePlaybackProgress = if (isPlayingThis) voicePlaybackProgress else 0f,
                                            onToggleVoicePlay = {
                                                voicePlayer.togglePlay(
                                                    messageId = msg.id,
                                                    audioBase64 = msg.audioBase64,
                                                    scope = scope,
                                                    onError = { errMsg ->
                                                        scope.launch { snackbarHostState.showTopMessage(errMsg) }
                                                    }
                                                )
                                            },
                                            onOpenPhoto = { photo -> fullScreenPhotoBase64 = photo }
                                        )
                                    }
                                }
                            }
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

    fullScreenPhotoBase64?.let { photo ->
        FullScreenPhotoDialog(
            photoBase64 = photo,
            contentDescription = "Snapped photo",
            onDismiss = { fullScreenPhotoBase64 = null }
        )
    }

    if (showKickPaidMemberDialog && otherUid.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showKickPaidMemberDialog = false },
            title = { Text("Kick $otherName?") },
            text = {
                Text(
                    "Remove $otherName from private messaging you? They will immediately be blocked from sending messages in this chat."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onKickPaidMember(otherUid, otherName)
                        showKickPaidMemberDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiveCoral,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("dm_confirm_kick_button")
                ) {
                    Text("Kick Member")
                }
            },
            dismissButton = {
                TextButton(onClick = { showKickPaidMemberDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBlockContactDialog && otherUid.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showBlockContactDialog = false },
            title = { Text("Block $otherName?") },
            text = {
                Text(
                    "Blocking $otherName will remove them from your friends list and stop all incoming private messages from them. You can unblock them later from this chat or Safety Center."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onBlockContact(otherUid, otherName)
                        showBlockContactDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiveCoral,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("dm_confirm_block_button")
                ) {
                    Text("Block Contact")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockContactDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddPersonSheet && thread != null) {
        val currentMemberIds = remember(thread.participantIds) { thread.participantIds.toSet() }
        val candidates = remember(directoryEntries, currentMemberIds) {
            directoryEntries.filter { it.userId !in currentMemberIds }
        }
        var pickerQuery by remember { mutableStateOf("") }
        val filteredCandidates = remember(candidates, pickerQuery) {
            val q = pickerQuery.trim()
            if (q.isEmpty()) candidates
            else candidates.filter {
                it.displayName.contains(q, ignoreCase = true) ||
                    (it.tierEnum() != StaffTier.ADMIN && it.handle.contains(q, ignoreCase = true))
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showAddPersonSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isGroup) "Add Person to Group Chat" else "Create Group Chat",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (isGroup) {
                        "Current members (${thread.participantIds.size}/15): ${thread.memberNamesSummary()}"
                    } else {
                        "Pick another user to add to your chat with $otherName — this creates a group chat automatically!"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pickerQuery,
                    onValueChange = { pickerQuery = it.take(40) },
                    placeholder = { Text("Search people to add…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dm_add_person_search")
                )

                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredCandidates.isEmpty()) {
                        item(key = "no_candidates") {
                            Text(
                                text = "No other users available to add right now.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 14.dp)
                            )
                        }
                    } else {
                        items(filteredCandidates, key = { "add_cand_${it.userId}" }) { candidate ->
                            val candTier = candidate.tierEnum()
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAddPersonSheet = false
                                        onAddUserToChat(thread, candidate)
                                    }
                                    .testTag("dm_add_candidate_${candidate.userId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SpeakingAvatarCircle(
                                        displayName = candidate.displayName,
                                        colorHex = candidate.avatarColorHex,
                                        speakingLevel = 0f,
                                        micActive = false,
                                        size = 42.dp,
                                        photoBase64 = candidate.photoBase64
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = candidate.displayName,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = usernameColor(candTier)
                                            )
                                            StaffTierBadge(tier = candTier)
                                        }
                                        if (candTier != StaffTier.ADMIN) {
                                            Text(
                                                text = "@${candidate.handle}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.GroupAdd,
                                        contentDescription = "Add ${candidate.displayName}",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChatPrivacyDialog && thread != null) {
        ChatPrivacyOptionsDialog(
            currentDurationSec = thread.disappearingDurationSec,
            isReadReceiptsEnabledForMe = thread.isReadReceiptsEnabledFor(viewModel.currentUserId),
            globalReadReceiptsEnabled = globalReadReceiptsEnabled,
            isOtherReadReceiptsEnabled = if (!isGroup && otherUid.isNotBlank()) {
                thread.isReadReceiptsEnabledFor(otherUid)
            } else {
                true
            },
            otherName = otherName,
            isGroup = isGroup,
            onSelectDisappearingDuration = { sec ->
                viewModel.setDisappearingDuration(sec)
            },
            onToggleReadReceipts = { enabled ->
                viewModel.setReadReceiptsEnabled(enabled)
            },
            onDismiss = { showChatPrivacyDialog = false }
        )
    }
}

@Composable
private fun ChatPrivacyOptionsDialog(
    currentDurationSec: Long,
    isReadReceiptsEnabledForMe: Boolean,
    globalReadReceiptsEnabled: Boolean,
    isOtherReadReceiptsEnabled: Boolean,
    otherName: String,
    isGroup: Boolean,
    onSelectDisappearingDuration: (Long) -> Unit,
    onToggleReadReceipts: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Chat Privacy Options",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Disappearing Messages",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "When enabled, new messages sent in this chat automatically disappear after the chosen timer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DirectThread.DISAPPEARING_OPTIONS_SEC.forEach { sec ->
                            val selected = currentDurationSec == sec
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectDisappearingDuration(sec) },
                                label = {
                                    Text(
                                        text = DirectThread.formatDisappearingDuration(sec),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (sec > 0L) ModAmber else MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("dm_disappearing_option_$sec")
                            )
                        }
                    }
                }

                if (!isGroup) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "Read Receipts",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when {
                                    !globalReadReceiptsEnabled ->
                                        "Turned off globally in Profile Settings."
                                    !isReadReceiptsEnabledForMe ->
                                        "You disabled read receipts for this chat — neither of you will see Read status."
                                    !isOtherReadReceiptsEnabled ->
                                        "$otherName has read receipts turned off for this chat."
                                    else ->
                                        "Show when you and $otherName have read each other's messages."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isReadReceiptsEnabledForMe && globalReadReceiptsEnabled,
                            enabled = globalReadReceiptsEnabled,
                            onCheckedChange = onToggleReadReceipts,
                            modifier = Modifier.testTag("dm_read_receipts_switch")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("dm_privacy_options_done")
            ) {
                Text("Done")
            }
        }
    )
}

/**
 * Snapchat-signature vertical bar chat block supporting text, voice notes, and snapped pictures.
 */
@Composable
private fun SnapChatMessageBlock(
    message: DirectMessage,
    isMe: Boolean,
    nowMs: Long,
    showReadReceipt: Boolean,
    isReadByOther: Boolean,
    showReadTimeLabel: Boolean,
    otherReadMillis: Long,
    isPlayingVoice: Boolean,
    voicePlaybackProgress: Float,
    onToggleVoicePlay: () -> Unit,
    onOpenPhoto: (String) -> Unit
) {
    val tier = message.senderTierEnum()
    val accentBarColor = if (isMe) SnapBlue else usernameColor(tier)
    val snapBitmap = rememberBase64ImageBitmap(message.imageBase64)
    val hasVoice = message.hasVoiceNote()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isMe) "ME" else message.senderName.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = accentBarColor
            )
            if (!isMe) {
                StaffTierBadge(tier = tier)
            }
            Text(
                text = formatTimestampShort(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (message.isDisappearing()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = ModAmber.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = "Disappearing countdown",
                            tint = ModAmber,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = message.remainingExpiryLabel(nowMs),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ModAmber
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(3.5.dp)
                    .height(if (snapBitmap != null) 56.dp else if (hasVoice) 44.dp else 26.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentBarColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isMe) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                },
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = if (snapBitmap != null) 8.dp else 12.dp,
                        vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (snapBitmap != null) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .heightIn(min = 120.dp, max = 280.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onOpenPhoto(message.imageBase64) }
                                .testTag("dm_snap_image_${message.id}")
                        ) {
                            Image(
                                bitmap = snapBitmap,
                                contentDescription = "Snapped photo from ${message.senderName}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (hasVoice) {
                        val barHeights = remember { listOf(10, 18, 26, 16, 28, 22, 14, 24, 30, 18, 12, 22, 16, 10) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .widthIn(min = 210.dp, max = 270.dp)
                                .testTag("dm_voice_bubble_${message.id}")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isPlayingVoice) LiveCoral else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                IconButton(
                                    onClick = onToggleVoicePlay,
                                    modifier = Modifier.testTag("dm_voice_play_${message.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingVoice) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlayingVoice) "Pause voice note" else "Play voice note",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    barHeights.forEachIndexed { index, h ->
                                        val fraction = (index + 1).toFloat() / barHeights.size.toFloat()
                                        val active = isPlayingVoice && fraction <= voicePlaybackProgress.coerceAtLeast(0.08f)
                                        Box(
                                            modifier = Modifier
                                                .width(3.5.dp)
                                                .height(h.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(
                                                    if (active) MaterialTheme.colorScheme.primary
                                                    else accentBarColor.copy(alpha = 0.42f)
                                                )
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (isPlayingVoice) {
                                            "Playing · ${formatVoiceDuration(message.audioDurationSec)}"
                                        } else {
                                            "Voice note · ${formatVoiceDuration(message.audioDurationSec)}"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = if (snapBitmap != null) Modifier.padding(horizontal = 4.dp) else Modifier
                        )
                    }
                }
            }
        }

        if (showReadReceipt) {
            Row(
                modifier = Modifier
                    .padding(start = 14.dp, top = 1.dp)
                    .testTag("dm_read_receipt_${message.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isReadByOther) Icons.Rounded.DoneAll else Icons.Rounded.Done,
                    contentDescription = if (isReadByOther) "Read" else "Delivered",
                    tint = if (isReadByOther) SnapBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                val receiptLabel = if (isReadByOther) {
                    if (showReadTimeLabel && otherReadMillis > 0L) {
                        val fmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                        "Read · ${fmt.format(java.util.Date(otherReadMillis))}"
                    } else {
                        "Read"
                    }
                } else {
                    "Delivered"
                }
                Text(
                    text = receiptLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isReadByOther) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isReadByOther) SnapBlue else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
