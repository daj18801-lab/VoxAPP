package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.example.media.VoiceNotePlayer
import com.example.media.VoiceNoteRecorder
import com.example.media.formatVoiceDuration
import com.example.notifications.DirectMessageNotificationHelper
import com.example.notifications.VoxStageNotificationState
import com.example.ui.components.FullScreenPhotoDialog
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.TopSnackbarOverlay
import com.example.ui.components.encodeBitmapToBase64Jpeg
import com.example.ui.components.encodeImageUriToBase64Jpeg
import com.example.ui.components.formatTimestampShort
import com.example.ui.components.rememberBase64ImageBitmap
import com.example.ui.components.showTopMessage
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
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
    onAddUserToChat: (DirectThread, DirectoryEntry) -> Unit = { _, _ -> },
    onKickPaidMember: (senderId: String, senderName: String) -> Unit = { _, _ -> },
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
    var pendingMicAction by remember { mutableStateOf<String?>(null) } // "VOICE_CALL" or "VOICE_NOTE"
    var showAddPersonSheet by remember { mutableStateOf(false) }
    var showKickPaidMemberDialog by remember { mutableStateOf(false) }

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
                else -> viewModel.toggleCallMic(userProfile.displayName)
            }
        } else {
            scope.launch {
                snackbarHostState.showTopMessage(
                    if (action == "VOICE_NOTE") "Microphone permission is needed to send voice notes."
                    else "Microphone permission is needed for private voice calls."
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

    LaunchedEffect(thread?.id, thread?.unreadByUserIds) {
        viewModel.markReadIfNeeded(thread)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
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
    val amIKicked = thread?.isUserKicked(viewModel.currentUserId) == true
    val otherPaidPass = remember(paidDmMembersForMe, otherUid, isGroup) {
        if (!isGroup && otherUid.isNotBlank()) {
            paidDmMembersForMe.firstOrNull { it.senderId == otherUid && it.isCurrentlyAllowed() }
        } else null
    }
    val canKickOtherPaidMember = !isGroup && otherUid.isNotBlank() &&
        (otherPaidPass != null || (userProfile.dmPolicyEnum() == DmPolicy.PAID && thread?.isUserKicked(otherUid) != true))
    val remoteVideoTrack = remoteVideoTracks.values.firstOrNull()
    val canSend = !amIKicked && (composerText.isNotBlank() || draftSnapBase64.isNotBlank()) && !isSending && !isEncodingSnap

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
                                            else -> "Private Chat · Tap profile"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isInCall) SafetyEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Add Person -> Creates Group Chat (or adds to Group Chat)
                            if (!amIKicked && thread != null) {
                                IconButton(
                                    onClick = { showAddPersonSheet = true },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("dm_add_user_button")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.GroupAdd,
                                                contentDescription = if (isGroup) "Add member to group chat" else "Add user to create group chat",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
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
                                        .size(44.dp)
                                        .testTag("dm_kick_paid_member_button")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = LiveCoral.copy(alpha = 0.16f),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.PersonRemove,
                                                contentDescription = "Kick paid member from messaging you",
                                                tint = LiveCoral,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Private Chat Voice Mic Call Button
                            if (!amIKicked) {
                                IconButton(
                                    onClick = ::requestMicCallToggle,
                                    enabled = !isConnectingCall,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("dm_mic_call_button")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isMicActive) SafetyEmerald else MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isMicActive) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                                                contentDescription = if (isMicActive) "Mute private call mic" else "Start private voice mic",
                                                tint = if (isMicActive) Color.White else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Private Chat Live Video Call Button
                                IconButton(
                                    onClick = ::requestVideoCallToggle,
                                    enabled = !isConnectingCall,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("dm_video_call_button")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isCamActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isCamActive) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                                                contentDescription = if (isCamActive) "Turn off private video" else "Start private video call",
                                                tint = if (isCamActive) Color.White else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
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
                        } else {
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
                                            text = "Add a caption or tap Send",
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
                            if (messages.isEmpty()) {
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
                                        text = "Send a message, voice note, snap a picture, or start a live mic/video call.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .widthIn(max = 760.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(messages, key = { it.id }) { msg ->
                                        val isPlayingThis = playingVoiceMessageId == msg.id
                                        SnapChatMessageBlock(
                                            message = msg,
                                            isMe = msg.senderId == viewModel.currentUserId,
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
                    it.handle.contains(q, ignoreCase = true)
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
                                        Text(
                                            text = candidate.displayName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = usernameColor(candidate.tierEnum())
                                        )
                                        Text(
                                            text = "@${candidate.handle}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
}

/**
 * Snapchat-signature vertical bar chat block supporting text, voice notes, and snapped pictures.
 */
@Composable
private fun SnapChatMessageBlock(
    message: DirectMessage,
    isMe: Boolean,
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
    }
}
