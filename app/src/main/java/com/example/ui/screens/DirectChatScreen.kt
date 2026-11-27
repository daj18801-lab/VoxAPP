package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeOff
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CompanySubAccount
import com.example.data.DirectMessage
import com.example.data.DirectThread
import com.example.data.DirectoryEntry
import com.example.data.DmAccessPass
import com.example.data.DmPolicy
import com.example.data.PresenceStatus
import com.example.data.ProTier
import com.example.data.ReportCategory
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
import com.example.ui.components.PresenceStatusDot
import com.example.ui.components.DockedCirclePttButton
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.TalkStreakBadge
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    onOpenBuyPoints: () -> Unit = {},
    onToggleDnd: (Boolean) -> Unit = {},
    onOpenWorkHub: () -> Unit = {},
    onOpenCompanyConsole: () -> Unit = {},
    myCompanySubAccount: CompanySubAccount? = null,
    initialInvisibleOwnerMode: Boolean = false,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val threadState by viewModel.threadState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val isInvisibleOwnerMode by viewModel.isInvisibleOwnerMode.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val isInCall by viewModel.isInCall.collectAsStateWithLifecycle()
    val isConnectingCall by viewModel.isConnectingCall.collectAsStateWithLifecycle()
    val isMicActive by viewModel.isMicActive.collectAsStateWithLifecycle()
    val isCamActive by viewModel.isCamActive.collectAsStateWithLifecycle()
    val useFrontCamera by viewModel.useFrontCamera.collectAsStateWithLifecycle()
    val localVideoTrack by viewModel.liveKitManager.localVideoTrack.collectAsStateWithLifecycle()
    val remoteVideoTracks by viewModel.liveKitManager.remoteVideoTracks.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.threadId, initialInvisibleOwnerMode) {
        viewModel.initOwnerMonitorMode(initialInvisibleOwnerMode)
    }

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
    var showDmBannerDialog by remember { mutableStateOf(false) }
    var showPriorityChirpDialog by remember { mutableStateOf(false) }
    var showGroupAdminSheet by remember { mutableStateOf(false) }
    var showSendGroupAlertDialog by remember { mutableStateOf(false) }
    var showTopActionsPanel by remember { mutableStateOf(false) }
    var showUserListDrawer by remember { mutableStateOf(false) }
    var workOrderTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showEmployeeRequestDialog by remember { mutableStateOf(false) }
    var showRequestStaffHelpDialog by remember { mutableStateOf(false) }
    var staffHelpReasonDraft by remember { mutableStateOf("") }
    var showReportUserPickerDialog by remember { mutableStateOf(false) }
    var reportTargetUser by remember { mutableStateOf<Pair<String, String>?>(null) }
    var hideMyNameInGroup by remember { mutableStateOf(false) }
    var actionTargetMessage by remember { mutableStateOf<DirectMessage?>(null) }
    var reportTargetMessage by remember { mutableStateOf<DirectMessage?>(null) }
    var lastScreenshotNotifiedMs by remember { mutableLongStateOf(0L) }
    val globalReadReceiptsEnabled = remember(context) {
        DirectMessageNotificationPrefs.isReadReceiptsEnabled(context)
    }

    BackHandler(enabled = showUserListDrawer) {
        showUserListDrawer = false
    }

    // Live Walkie-Talkie PTT state (synced across both users via Firestore + LivePttSessionManager)
    val pttActiveThreadId by LivePttSessionManager.activeThreadId.collectAsStateWithLifecycle()
    val pttIsConnected by LivePttSessionManager.isConnected.collectAsStateWithLifecycle()
    val pttIsConnecting by LivePttSessionManager.isConnecting.collectAsStateWithLifecycle()
    val pttIsTransmitting by LivePttSessionManager.isTransmitting.collectAsStateWithLifecycle()
    val pttRemoteSpeaker by LivePttSessionManager.remoteSpeakingName.collectAsStateWithLifecycle()
    val pttElapsedSec by LivePttSessionManager.transmittingElapsedSec.collectAsStateWithLifecycle()
    val pttMaxSec by LivePttSessionManager.maxPttSeconds.collectAsStateWithLifecycle()
    val isFloatingPttEnabled by LivePttSessionManager.isFloatingPttEnabled.collectAsStateWithLifecycle()
    val isLockScreenPttEnabled by LivePttSessionManager.isLockScreenPttEnabled.collectAsStateWithLifecycle()
    val isVolumeDownPttEnabled by LivePttSessionManager.isVolumeDownPttEnabled.collectAsStateWithLifecycle()
    val floatingDockedThreadId by LivePttSessionManager.floatingButtonDockedThreadId.collectAsStateWithLifecycle()
    val activePttTheme by LivePttSessionManager.activePttTheme.collectAsStateWithLifecycle()
    val isGlobalPttVttEnabled by LivePttSessionManager.isPttVoiceToTextEnabled.collectAsStateWithLifecycle()
    val livePttTranscript by LivePttSessionManager.livePttTranscript.collectAsStateWithLifecycle()
    val lastPttTranscriptFeedback by LivePttSessionManager.lastPttTranscriptFeedback.collectAsStateWithLifecycle()
    val remoteCallSpeakerName by viewModel.remoteCallSpeakerName.collectAsStateWithLifecycle()

    LaunchedEffect(lastPttTranscriptFeedback) {
        val feedback = lastPttTranscriptFeedback
        if (!feedback.isNullOrBlank()) {
            snackbarHostState.showTopMessage(feedback)
            LivePttSessionManager.clearPttTranscriptFeedback()
        }
    }
    val myEffectiveName = userProfile.effectiveDisplayName().ifBlank { "Member" }
    val myDisplayedStaffTier = userProfile.displayedStaffTier(myStaffTier)
    val myMaxPttSeconds = userProfile.pttMaxDurationSeconds(myStaffTier)
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
        if (voiceRecorder.startRecording(maxDurationSeconds = myMaxPttSeconds)) {
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
                    senderName = myEffectiveName,
                    senderTier = myStaffTier,
                    text = caption,
                    audioBase64 = note.audioBase64,
                    audioDurationSec = note.durationSec,
                    hideSenderName = hideMyNameInGroup && ((threadState as? UiState.Success)?.data?.let { it.isGroup && !it.anonymousModeDisabled } == true),
                    displayedSenderTier = myDisplayedStaffTier
                )
            } else {
                snackbarHostState.showTopMessage("Recording was too short or could not be saved.")
            }
        }
    }

    LaunchedEffect(isRecordingVoiceNote, myMaxPttSeconds) {
        if (isRecordingVoiceNote) {
            while (isRecordingVoiceNote && recordingSeconds < myMaxPttSeconds) {
                delay(1000L)
                if (isRecordingVoiceNote) {
                    recordingSeconds += 1
                }
            }
            if (isRecordingVoiceNote) {
                if (myMaxPttSeconds <= UserProfile.FREE_PTT_MAX_SECONDS) {
                    snackbarHostState.showTopMessage("Free members have 5s PTT clips. Upgrade to Pro ($9.99/mo) for 1-minute clips!")
                }
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
                            myDisplayName = myEffectiveName
                        )
                    }
                    scope.launch {
                        snackbarHostState.showTopMessage("Microphone ready! Hold Push to Talk to stream live audio.")
                    }
                }
                else -> viewModel.toggleCallMic(myEffectiveName)
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
                "VIDEO_CALL" -> viewModel.toggleCallVideo(myEffectiveName)
                "SNAP_PHOTO" -> cameraSnapLauncher.launch(null)
            }
        } else {
            scope.launch { snackbarHostState.showTopMessage("Camera permission is required for video calls and snaps.") }
        }
    }

    val subPttDisabled = userProfile.companySubAccount && myCompanySubAccount?.pttEnabled == false
    val subMsgDisabled = userProfile.companySubAccount && myCompanySubAccount?.messagingEnabled == false
    val subMicDisabled = userProfile.companySubAccount && myCompanySubAccount?.micEnabled == false
    val subVideoDisabled = userProfile.companySubAccount && myCompanySubAccount?.videoEnabled == false

    LaunchedEffect(subMicDisabled, subVideoDisabled, subPttDisabled) {
        viewModel.forceStopDisabledSubAccountMedia(
            micDisabled = subMicDisabled,
            videoDisabled = subVideoDisabled
        )
        if ((subPttDisabled || subMicDisabled) && LivePttSessionManager.activeThreadId.value == viewModel.threadId) {
            LivePttSessionManager.disconnect(context)
        }
    }

    fun requestMicCallToggle() {
        if (subMicDisabled) {
            scope.launch { snackbarHostState.showTopMessage("Your microphone has been disabled by your company owner.") }
            return
        }
        if (!myStaffTier.isAdmin() && userProfile.disableMic) {
            scope.launch { snackbarHostState.showTopMessage("Microphone access is disabled on your account.") }
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.toggleCallMic(myEffectiveName)
        } else {
            pendingMicAction = "VOICE_CALL"
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun requestVoiceNoteStart() {
        if (subPttDisabled) {
            scope.launch { snackbarHostState.showTopMessage("Push-to-Talk has been disabled on your sub-account by your company owner.") }
            return
        }
        if (subMicDisabled) {
            scope.launch { snackbarHostState.showTopMessage("Your microphone has been disabled by your company owner.") }
            return
        }
        if (subMsgDisabled) {
            scope.launch { snackbarHostState.showTopMessage("Messaging has been disabled on your sub-account by your company owner.") }
            return
        }
        val currentThread = (threadState as? UiState.Success)?.data
        if (currentThread != null && (currentThread.isGroup || currentThread.participantIds.size > 2) &&
            !currentThread.isPttAllowedInGroup(viewModel.currentUserId, myStaffTier == StaffTier.ADMIN)
        ) {
            scope.launch {
                snackbarHostState.showTopMessage(
                    "Push-to-Talk in groups over ${DirectThread.PTT_ADMIN_SELECTION_THRESHOLD} users is restricted to speakers selected by the Group DM Admin."
                )
            }
            return
        }
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
        if (subVideoDisabled) {
            scope.launch { snackbarHostState.showTopMessage("Video / camera access has been disabled on your sub-account by your company owner.") }
            return
        }
        if (!myStaffTier.isAdmin() && userProfile.disableVideo) {
            scope.launch { snackbarHostState.showTopMessage("Video calling is disabled on your account.") }
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.toggleCallVideo(myEffectiveName)
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
    val isVoxOfficial = thread?.voxOfficial == true
    val isCompanyOwnerMonitor = thread?.isCompanySubOneOnOne() == true &&
        thread.companyOwnerId == viewModel.currentUserId &&
        !thread.participantIds.contains(viewModel.currentUserId)
    val otherUid = if (isCompanyOwnerMonitor || isVoxOfficial) "" else thread?.otherUserId(viewModel.currentUserId).orEmpty()

    LaunchedEffect(
        thread?.id,
        isInvisibleOwnerMode,
        isCompanyOwnerMonitor,
        userProfile.effectiveDisplayName(),
        myStaffTier
    ) {
        viewModel.announceEnterIfNeeded(
            thread = thread,
            displayName = userProfile.effectiveDisplayName(),
            displayedTier = userProfile.displayedStaffTier(myStaffTier),
            isInvisible = isInvisibleOwnerMode || isCompanyOwnerMonitor || initialInvisibleOwnerMode
        )
    }
    val nowMillis = rememberPresenceClockMillis()
    val otherDirectoryEntry = remember(directoryEntries, otherUid) {
        directoryEntries.firstOrNull { it.userId == otherUid }
    }
    val otherName = when {
        isVoxOfficial -> "VOX"
        isCompanyOwnerMonitor && thread != null ->
            "${thread.userAName.ifBlank { "Employee 1" }} & ${thread.userBName.ifBlank { "Employee 2" }}"
        !isGroup && otherDirectoryEntry != null && otherDirectoryEntry.effectiveDisplayName().isNotBlank() ->
            otherDirectoryEntry.effectiveDisplayName()
        else -> thread?.displayTitle(viewModel.currentUserId) ?: "Private Chat"
    }
    val otherColorHex = thread?.otherColorHex(viewModel.currentUserId) ?: "#0088FF"
    val otherTier = when {
        isGroup || isCompanyOwnerMonitor || isVoxOfficial -> StaffTier.USER
        otherDirectoryEntry != null -> otherDirectoryEntry.tierEnum()
        else -> thread?.otherTier(viewModel.currentUserId) ?: StaffTier.USER
    }
    val otherDisplayedStatus = if (!isGroup) {
        otherDirectoryEntry?.displayedStatusFor(
            viewerIsStaff = myStaffTier.isStaff(),
            nowMillis = nowMillis
        ) ?: PresenceStatus.OFFLINE
    } else {
        PresenceStatus.OFFLINE
    }
    val otherHidingTrueOnline = !isGroup && myStaffTier.isStaff() &&
        (otherDirectoryEntry?.isHidingTrueOnlineStatus(nowMillis) == true)
    val amIKicked = thread?.isUserKicked(viewModel.currentUserId) == true
    val directoryByUid = remember(directoryEntries) { directoryEntries.associateBy { it.userId } }
    val isAppStaff = myStaffTier.isStaff()
    val isGroupAdmin = thread?.isGroupAdmin(viewModel.currentUserId) == true
    val isGroupSupervisor = isGroup && (thread?.isGroupSupervisor(viewModel.currentUserId) == true)
    val isGroupSupport = isGroup && (thread?.isGroupSupport(viewModel.currentUserId) == true)
    val isGroupAdminOrAppAdmin = isGroup && (isGroupAdmin || myStaffTier == StaffTier.ADMIN)
    val isGroupDisabledByStaff = isGroup && (thread?.groupDisabled == true)
    val isStaffModMonitor = isAppStaff &&
        (thread?.hasStaffAccessOpen() == true || (isGroup && thread?.participantIds?.contains(viewModel.currentUserId) == false))
    val canManageWorkOrders = isGroup && (thread?.canManageWorkOrders(viewModel.currentUserId, myStaffTier == StaffTier.ADMIN) == true)
    val canManageGroupDm = isGroup && (isGroupAdmin || isGroupSupport || isGroupSupervisor || isAppStaff)
    val requiresGroupPttSelection = isGroup && (thread?.requiresAdminPttSelection() == true)
    val canUseGroupPtt = !isGroup || thread == null ||
        thread.isPttAllowedInGroup(viewModel.currentUserId, myStaffTier == StaffTier.ADMIN)
    val amIBannedInGroup = isGroup && (thread?.isBannedInGroup(viewModel.currentUserId) == true)
    val amIBouncedInGroup = isGroup && (thread?.isBouncedInGroup(viewModel.currentUserId, nowMillis) == true)
    val amIMutedInGroup = isGroup && (thread?.isMutedInGroup(viewModel.currentUserId) == true)
    val isMessagingDisabledByStaff =
        userProfile.isFeatureRestricted(com.example.data.RestrictableFeature.DM, nowMillis)
    val isPttDisabledByStaff = myStaffTier != StaffTier.ADMIN &&
        userProfile.isFeatureRestricted(com.example.data.RestrictableFeature.PTT, nowMillis)
    val isGroupLockedOut = amIBannedInGroup || amIBouncedInGroup
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
    val isMyLiveAudioListenOn = thread?.isPttListenOnlyEnabledFor(viewModel.currentUserId) == true
    val hasOtherLiveListener = thread?.hasAnyLiveAudioListenerOtherThan(viewModel.currentUserId) == true
    val blockedByGroupAdminTalking = thread?.isBlockedByGroupAdminTalking(
        uid = viewModel.currentUserId,
        isAppAdmin = myStaffTier == StaffTier.ADMIN
    ) == true
    val canMyAccountUsePtt = !isVoxOfficial && !amIKicked && !isBlockedThread && !isGroupLockedOut && !amIMutedInGroup &&
        !subPttDisabled && !subMicDisabled && !isPttDisabledByStaff && !(isGroupDisabledByStaff && !isAppStaff) &&
        canUseGroupPtt
    val canUseListenOnlyLiveAudio = !isVoxOfficial && !amIKicked && !isBlockedThread && !isGroupLockedOut &&
        !(isGroupDisabledByStaff && !isAppStaff)
    val isMutualPttActive = canMyAccountUsePtt && isMyPttEnabled && (
        hasOtherLiveListener ||
            (isGroupAdmin && isMyPttEnabled)
        )
    val remoteVideoTrack = remoteVideoTracks.values.firstOrNull()
    val canSend = !isVoxOfficial && !amIKicked && !isBlockedThread && !isGroupLockedOut && !amIMutedInGroup &&
        !isMessagingDisabledByStaff && !subMsgDisabled && !(isGroupDisabledByStaff && !isAppStaff) &&
        (composerText.isNotBlank() || draftSnapBase64.isNotBlank()) && !isSending && !isEncodingSnap
    val remainingPictureQuota = remember(userProfile.pictureUploadTimestamps) {
        userProfile.remainingPictureUploads()
    }

    val isMyPttVttOn = thread?.isPttVoiceToTextEnabledFor(viewModel.currentUserId) == true
    val isMyGroupDndOn = isGroup && (thread?.isPttDndEnabledFor(viewModel.currentUserId) == true)
    val isPttSilentMode by LivePttSessionManager.isPttSilentMode.collectAsStateWithLifecycle()
    val isVttOnlyPttMode = isMyPttVttOn || isGlobalPttVttEnabled || isPttSilentMode || !hasOtherLiveListener
    val isAnyThreadVttActive = thread?.shouldAutoTranscribePttFrom(viewModel.currentUserId) == true || isGlobalPttVttEnabled || isPttSilentMode

    val suppressTypingBroadcast = isInvisibleOwnerMode ||
        (isGroup && (thread?.anonymousModeDisabled != true) && hideMyNameInGroup) ||
        isMessagingDisabledByStaff || subMsgDisabled
    LaunchedEffect(suppressTypingBroadcast) {
        if (suppressTypingBroadcast) {
            viewModel.clearTypingNow()
        }
    }
    DisposableEffect(viewModel.threadId) {
        onDispose {
            viewModel.clearTypingNow(useExternalScope = true)
        }
    }
    val hasRemoteTypingEntry = thread?.typingByUserId?.any { (uid, _) ->
        uid.isNotBlank() && uid != viewModel.currentUserId
    } == true
    var typingClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(hasRemoteTypingEntry, thread?.typingByUserId) {
        typingClockMs = System.currentTimeMillis()
        if (!hasRemoteTypingEntry) return@LaunchedEffect
        while (true) {
            delay(1000L)
            typingClockMs = System.currentTimeMillis()
        }
    }
    val typingIndicatorLabel = thread?.formatTypingLabel(
        myUid = viewModel.currentUserId,
        nowMs = typingClockMs,
        nameLookup = { uid -> directoryByUid[uid]?.effectiveDisplayName() }
    )

    // Sync Group DM Admin role, talk-over lock state, and PTT Voice-to-Text / DND / Listen-Only flags into LivePttSessionManager
    LaunchedEffect(
        thread?.id,
        isGroupAdmin,
        blockedByGroupAdminTalking,
        thread?.pttEnabledUserIds,
        thread?.pttListenOnlyUserIds,
        thread?.pttVoiceToTextUserIds,
        thread?.pttDndUserIds,
        pttActiveThreadId
    ) {
        val activeTid = thread?.id
        if (!activeTid.isNullOrBlank()) {
            LivePttSessionManager.setThreadGroupAdminState(
                threadId = activeTid,
                isGroupAdmin = isGroupAdmin,
                blockedByAdmin = blockedByGroupAdminTalking,
                context = context
            )
            LivePttSessionManager.syncActiveThreadPttFlags(
                context = context,
                thread = thread,
                currentUserId = viewModel.currentUserId
            )
        }
        if (blockedByGroupAdminTalking) {
            if (LivePttSessionManager.isTransmitting.value) {
                LivePttSessionManager.setTransmitting(context, false)
            }
            viewModel.forceMuteForGroupAdminOverride()
        }
    }

    // Sync Group DM Admin's Live PTT transmit state to Firestore so all members yield the mic
    DisposableEffect(isGroupAdmin, thread?.id) {
        val activeTid = thread?.id
        if (isGroupAdmin && !activeTid.isNullOrBlank()) {
            LivePttSessionManager.onTransmittingStateChanged = { tid, transmitting ->
                if (tid == activeTid) {
                    viewModel.setGroupAdminTalking(transmitting)
                }
            }
            onDispose {
                LivePttSessionManager.onTransmittingStateChanged = null
                if (thread?.groupAdminTalking == true) {
                    viewModel.setGroupAdminTalking(false)
                }
            }
        } else {
            onDispose { }
        }
    }

    // Android 14+ Screenshot Detection in Private Messages -> Notifies the other user
    DisposableEffect(context, thread?.id, amIKicked, isBlockedThread, myEffectiveName, myStaffTier, myDisplayedStaffTier) {
        val activeThreadId = thread?.id
        val activity = context.findActivity()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            activity != null &&
            !activeThreadId.isNullOrBlank() &&
            !amIKicked &&
            !isBlockedThread
        ) {
            val callback = Activity.ScreenCaptureCallback {
                val now = System.currentTimeMillis()
                if (now - lastScreenshotNotifiedMs >= 3_000L) {
                    lastScreenshotNotifiedMs = now
                    viewModel.sendScreenshotNotice(
                        userName = myEffectiveName,
                        senderTier = myStaffTier,
                        displayedSenderTier = myDisplayedStaffTier
                    )
                }
            }
            runCatching {
                activity.registerScreenCaptureCallback(activity.mainExecutor, callback)
            }
            onDispose {
                runCatching {
                    activity.unregisterScreenCaptureCallback(callback)
                }
            }
        } else {
            onDispose { }
        }
    }

    LaunchedEffect(
        isMutualPttActive,
        isMyLiveAudioListenOn,
        canUseListenOnlyLiveAudio,
        isVttOnlyPttMode,
        isInCall,
        thread?.id,
        otherName,
        myEffectiveName
    ) {
        val currentThread = thread ?: return@LaunchedEffect
        if (isMutualPttActive && !isVttOnlyPttMode && !isInCall && !(!myStaffTier.isAdmin() && userProfile.disableMic)) {
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
                    myDisplayName = myEffectiveName
                )
            }
        } else if (canUseListenOnlyLiveAudio && isMyLiveAudioListenOn && !isInCall) {
            LivePttSessionManager.ensureConnectedListenOnly(
                context = context,
                threadId = currentThread.id,
                peerName = otherName,
                myUserId = viewModel.currentUserId,
                myDisplayName = myEffectiveName
            )
        } else if (LivePttSessionManager.activeThreadId.value == currentThread.id &&
            LivePttSessionManager.isListenOnlyMode.value &&
            !isMyLiveAudioListenOn
        ) {
            LivePttSessionManager.disconnect(context)
        }
    }

    LaunchedEffect(thread?.anonymousModeDisabled) {
        if (thread?.anonymousModeDisabled == true && hideMyNameInGroup) {
            hideMyNameInGroup = false
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
                                    .clickable(enabled = !isVoxOfficial && (isGroup || otherUid.isNotBlank())) {
                                        if (isGroup) {
                                            showUserListDrawer = true
                                        } else if (otherUid.isNotBlank()) {
                                            onOpenUserProfile(otherUid)
                                        }
                                    }
                                    .testTag("dm_header_profile_link")
                            ) {
                                if (isVoxOfficial) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(SnapBlue),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Verified,
                                            contentDescription = "Official VOX Message",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                } else if (isGroup) {
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
                                        photoBase64 = otherPhotoBase64,
                                        userId = otherUid
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (!isGroup && !isVoxOfficial) {
                                            PresenceStatusDot(
                                                status = otherDisplayedStatus,
                                                modifier = Modifier.testTag("dm_header_presence_dot")
                                            )
                                        }
                                        Text(
                                            text = otherName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = when {
                                                isVoxOfficial -> SnapBlue
                                                isGroup -> MaterialTheme.colorScheme.onSurface
                                                else -> usernameColor(otherTier, userId = otherUid)
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isVoxOfficial) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = SnapBlue.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = "OFFICIAL",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                    color = SnapBlue,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else if (!isGroup) {
                                            StaffTierBadge(tier = otherTier)
                                            val activeStreak = thread?.activeTalkStreakCount() ?: 0
                                            if (activeStreak >= 3) {
                                                TalkStreakBadge(
                                                    streakDays = activeStreak,
                                                    expiringToday = thread?.isTalkStreakExpiringToday() == true,
                                                    compact = true,
                                                    modifier = Modifier.testTag("dm_header_talk_streak")
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = when {
                                            isVoxOfficial -> "Official VOX Updates & Alerts • Read-only"
                                            isInCall -> if (isGroup) "Live Group Call Active" else "Live Call Active"
                                            isMutualPttActive -> if (isGroupAdmin) "Admin Priority PTT Live" else "Live PTT Active"
                                            isGroup -> "${thread?.participantIds?.size ?: 0} members · Tap for user list"
                                            otherHidingTrueOnline -> "Online (Appearing ${otherDirectoryEntry?.appearanceStatusEnum()?.label}) · Tap profile"
                                            else -> "${otherDisplayedStatus.label} · Tap profile"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            isVoxOfficial -> SnapBlue
                                            isInCall || isMutualPttActive -> SafetyEmerald
                                            !isGroup && otherDisplayedStatus == PresenceStatus.ONLINE -> PresenceOnlineGreen
                                            !isGroup && otherDisplayedStatus == PresenceStatus.IDLE -> ModAmber
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Right-side User List Drawer trigger (matches Chat Room User List)
                            if (!isVoxOfficial && thread != null) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .heightIn(min = 40.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable { showUserListDrawer = true }
                                        .testTag("dm_open_user_list_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Groups,
                                            contentDescription = "Open user list",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "${thread.participantIds.distinct().size}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // Collapsible Top Actions Down-Arrow Toggle (frees up room for chat log)
                            if (!isVoxOfficial && !amIKicked && !isBlockedThread && thread != null) {
                                val hasLiveAudio = isInCall || isMicActive || isCamActive || isMutualPttActive || isMyPttEnabled
                                IconButton(
                                    onClick = { showTopActionsPanel = !showTopActionsPanel },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("dm_toggle_top_actions_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (showTopActionsPanel) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (showTopActionsPanel) {
                                                        Icons.Rounded.KeyboardArrowUp
                                                    } else {
                                                        Icons.Rounded.KeyboardArrowDown
                                                    },
                                                    contentDescription = if (showTopActionsPanel) {
                                                        "Hide chat controls"
                                                    } else {
                                                        "Show chat controls"
                                                    },
                                                    tint = if (showTopActionsPanel) Color.White else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        if (hasLiveAudio && !showTopActionsPanel) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(9.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isThisThreadTransmitting || isMicActive) LiveCoral else SafetyEmerald)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Expandable Top Controls Panel (revealed when pressing the down arrow)
                        AnimatedVisibility(
                            visible = showTopActionsPanel && !isVoxOfficial && !amIKicked && !isBlockedThread && thread != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            val activeThread = thread
                            if (activeThread != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("dm_top_actions_dropdown_panel"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Row 1: Primary Call, PTT, Privacy & Group Actions
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Voice Mic / Group Admin Priority Mic
                                        DmTopDropdownChip(
                                            icon = if (isMicActive) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                                            label = when {
                                                isGroupAdmin && isMicActive -> "Priority Mic LIVE"
                                                isGroupAdmin -> "Admin Mic"
                                                isMicActive -> "Mic LIVE"
                                                else -> "Voice Call"
                                            },
                                            containerColor = when {
                                                isMicActive && isGroupAdmin -> ModAmber
                                                isMicActive -> SafetyEmerald
                                                isGroupAdmin -> ModAmber.copy(alpha = 0.18f)
                                                else -> MaterialTheme.colorScheme.primaryContainer
                                            },
                                            contentColor = when {
                                                isMicActive -> Color.White
                                                isGroupAdmin -> ModAmber
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                            enabled = !isConnectingCall,
                                            testTag = "dm_mic_call_button",
                                            onClick = ::requestMicCallToggle
                                        )

                                        // Video Call
                                        DmTopDropdownChip(
                                            icon = if (isCamActive) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                                            label = if (isCamActive) "Video ON" else "Video Call",
                                            containerColor = if (isCamActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = if (isCamActive) Color.White else MaterialTheme.colorScheme.primary,
                                            enabled = !isConnectingCall,
                                            testTag = "dm_video_call_button",
                                            onClick = ::requestVideoCallToggle
                                        )

                                        // Live Push-to-Talk Toggle
                                        DmTopDropdownChip(
                                            icon = if (!canUseGroupPtt && !isMyPttEnabled) Icons.Rounded.MicOff else Icons.Rounded.RecordVoiceOver,
                                            label = when {
                                                isMutualPttActive && isGroupAdmin -> "Admin PTT ON"
                                                isMutualPttActive -> "PTT LIVE"
                                                isMyPttEnabled -> "PTT Waiting"
                                                isOtherPttEnabled -> "Join PTT"
                                                else -> "PTT OFF"
                                            },
                                            containerColor = when {
                                                !canUseGroupPtt && !isMyPttEnabled -> MaterialTheme.colorScheme.surfaceVariant
                                                isMutualPttActive && isGroupAdmin -> ModAmber
                                                isMutualPttActive -> SafetyEmerald
                                                isMyPttEnabled -> SnapBlue
                                                isOtherPttEnabled -> ModAmber.copy(alpha = 0.22f)
                                                else -> MaterialTheme.colorScheme.primaryContainer
                                            },
                                            contentColor = when {
                                                !canUseGroupPtt && !isMyPttEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                                                isMutualPttActive || isMyPttEnabled -> Color.White
                                                isOtherPttEnabled -> ModAmber
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                            testTag = "dm_toggle_ptt_button",
                                            onClick = {
                                                val next = !isMyPttEnabled
                                                if (next && isPttDisabledByStaff) {
                                                    scope.launch {
                                                        snackbarHostState.showTopMessage(
                                                            userProfile.featureRestrictionNotice(com.example.data.RestrictableFeature.PTT, nowMillis)
                                                                ?: "Push-to-Talk is currently restricted on your account."
                                                        )
                                                    }
                                                    return@DmTopDropdownChip
                                                }
                                                if (next && isGroupDisabledByStaff && !isAppStaff) {
                                                    scope.launch {
                                                        snackbarHostState.showTopMessage("This Group DM has been disabled by VOX Staff.")
                                                    }
                                                    return@DmTopDropdownChip
                                                }
                                                if (next && subPttDisabled) {
                                                    scope.launch {
                                                        snackbarHostState.showTopMessage("Push-to-Talk has been disabled on your sub-account by your company owner.")
                                                    }
                                                    return@DmTopDropdownChip
                                                }
                                                if (next && subMicDisabled) {
                                                    scope.launch {
                                                        snackbarHostState.showTopMessage("Your microphone has been disabled by your company owner.")
                                                    }
                                                    return@DmTopDropdownChip
                                                }
                                                if (next && !canUseGroupPtt) {
                                                    scope.launch {
                                                        snackbarHostState.showTopMessage(
                                                            "Only speakers selected by the Group DM Admin can use Push-to-Talk in groups over ${DirectThread.PTT_ADMIN_SELECTION_THRESHOLD} users."
                                                        )
                                                    }
                                                    return@DmTopDropdownChip
                                                }
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
                                            }
                                        )

                                        // Floating PTT Toggle (when enabled, hides the big bottom Hold-to-Talk button)
                                        DmTopDropdownChip(
                                            icon = Icons.Rounded.GraphicEq,
                                            label = if (isFloatingPttEnabled) "Floating PTT: ON" else "Floating PTT: OFF",
                                            containerColor = if (isFloatingPttEnabled) SnapBlue else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (isFloatingPttEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            testTag = "dm_toggle_floating_ptt_button",
                                            onClick = {
                                                val next = !isFloatingPttEnabled
                                                LivePttSessionManager.setFloatingPttEnabled(context, next)
                                                scope.launch {
                                                    snackbarHostState.showTopMessage(
                                                        if (next) "Floating PTT enabled — bottom Hold-to-Talk bar hidden."
                                                        else "Floating PTT disabled — bottom Hold-to-Talk button restored."
                                                    )
                                                }
                                            }
                                        )

                                        // Lock-Screen PTT ON/OFF Toggle (available in both Private DMs & Group DMs)
                                        DmTopDropdownChip(
                                            icon = if (isLockScreenPttEnabled) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                            label = if (isLockScreenPttEnabled) "Lock-Screen PTT: ON" else "Lock-Screen PTT: OFF",
                                            containerColor = if (isLockScreenPttEnabled) SnapBlue else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (isLockScreenPttEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            testTag = "dm_toggle_lock_screen_ptt_button",
                                            onClick = {
                                                val next = !isLockScreenPttEnabled
                                                LivePttSessionManager.setLockScreenPttEnabled(context, next)
                                                scope.launch {
                                                    snackbarHostState.showTopMessage(
                                                        if (next) "Lock-Screen PTT enabled — PTT controls can appear on your lock screen."
                                                        else "Lock-Screen PTT turned off — PTT will not pop up on your lock screen."
                                                    )
                                                }
                                            }
                                        )

                                        // Launch Lock-Screen PTT Mode (shown only when Lock-Screen PTT is ON)
                                        if (isLockScreenPttEnabled) {
                                            DmTopDropdownChip(
                                                icon = Icons.Rounded.Lock,
                                                label = "Open Lock-Screen PTT",
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                testTag = "dm_open_lock_screen_ptt_button",
                                                onClick = {
                                                    val lockIntent = Intent(
                                                        context,
                                                        com.example.media.LockScreenPttActivity::class.java
                                                    ).apply {
                                                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                                                    }
                                                    runCatching { context.startActivity(lockIntent) }
                                                }
                                            )
                                        }

                                        // Volume Down Key PTT ON/OFF Toggle (available in both Private DMs & Group DMs)
                                        DmTopDropdownChip(
                                            icon = if (isVolumeDownPttEnabled) Icons.AutoMirrored.Rounded.VolumeDown else Icons.Rounded.VolumeOff,
                                            label = if (isVolumeDownPttEnabled) "Vol Down PTT: ON" else "Vol Down PTT: OFF",
                                            containerColor = if (isVolumeDownPttEnabled) SnapBlue else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (isVolumeDownPttEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            testTag = "dm_toggle_volume_down_ptt_button",
                                            onClick = {
                                                val next = !isVolumeDownPttEnabled
                                                LivePttSessionManager.setVolumeDownPttEnabled(context, next)
                                                scope.launch {
                                                    snackbarHostState.showTopMessage(
                                                        if (next) "Volume Down key PTT enabled — hold Volume Down to talk."
                                                        else "Volume Down key PTT turned off — volume button adjusts sound normally."
                                                    )
                                                }
                                            }
                                        )

                                        // PTT Voice-to-Text Toggle (available in all Private & Group DMs)
                                        DmTopDropdownChip(
                                            icon = Icons.Rounded.Subtitles,
                                            label = if (isMyPttVttOn) "PTT Voice-to-Text: ON" else "PTT Voice-to-Text: OFF",
                                            containerColor = if (isMyPttVttOn) SafetyEmerald else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (isMyPttVttOn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            testTag = "dm_toggle_ptt_vtt_button",
                                            onClick = {
                                                viewModel.setPttVoiceToText(!isMyPttVttOn)
                                            }
                                        )

                                        // Live Audio (Listen-Only Broadcast) toggle — lets users hear live PTT audio even if PTT is OFF, restricted, or on a sub-account
                                        if (canUseListenOnlyLiveAudio) {
                                            DmTopDropdownChip(
                                                icon = Icons.Rounded.Podcasts,
                                                label = if (isMyLiveAudioListenOn) "Live Audio: ON" else "Live Audio",
                                                containerColor = if (isMyLiveAudioListenOn) SnapBlue else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (isMyLiveAudioListenOn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                testTag = "dm_toggle_live_audio_listen_button",
                                                onClick = {
                                                    viewModel.setPttListenOnly(!isMyLiveAudioListenOn)
                                                }
                                            )
                                        }

                                        // Group DM Do Not Disturb (incoming PTT audio converted to Voice-to-Text)
                                        if (isGroup) {
                                            DmTopDropdownChip(
                                                icon = Icons.Rounded.DoNotDisturbOn,
                                                label = if (isMyGroupDndOn) "Do Not Disturb: ON" else "Do Not Disturb: OFF",
                                                containerColor = if (isMyGroupDndOn) LiveCoral else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (isMyGroupDndOn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                testTag = "dm_toggle_group_dnd_button",
                                                onClick = {
                                                    viewModel.setGroupPttDnd(!isMyGroupDndOn)
                                                }
                                            )
                                        }

                                        // Float Over Other Apps permission shortcut when Floating PTT is enabled
                                        if (isFloatingPttEnabled && !canDrawFloatingOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            DmTopDropdownChip(
                                                icon = Icons.Rounded.GraphicEq,
                                                label = "Float Over Apps",
                                                containerColor = ModAmber.copy(alpha = 0.18f),
                                                contentColor = ModAmber,
                                                testTag = "dm_ptt_enable_floating_overlay_button",
                                                onClick = {
                                                    val intent = Intent(
                                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        Uri.parse("package:${context.packageName}")
                                                    )
                                                    context.startActivity(intent)
                                                }
                                            )
                                        }

                                        // Privacy / Disappearing Timer
                                        val disappearingOn = activeThread.isDisappearingEnabled()
                                        DmTopDropdownChip(
                                            icon = Icons.Rounded.Timer,
                                            label = if (disappearingOn) "Timer ON" else "Privacy",
                                            containerColor = if (disappearingOn) ModAmber else MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = if (disappearingOn) Color.White else MaterialTheme.colorScheme.primary,
                                            testTag = "dm_privacy_options_button",
                                            onClick = { showChatPrivacyDialog = true }
                                        )

                                        // Group DM Admin / Support Staff Controls
                                        if (canManageGroupDm) {
                                            DmTopDropdownChip(
                                                icon = if (isAppStaff || isGroupAdminOrAppAdmin) Icons.Rounded.AdminPanelSettings else Icons.Rounded.SupportAgent,
                                                label = when {
                                                    isAppStaff -> "Staff Controls"
                                                    isGroupAdminOrAppAdmin -> "Group Admin"
                                                    else -> "Group Support"
                                                },
                                                containerColor = if (isAppStaff) LiveCoral.copy(alpha = 0.2f) else if (isGroupAdminOrAppAdmin) ModAmber.copy(alpha = 0.22f) else SafetyEmerald.copy(alpha = 0.22f),
                                                contentColor = if (isAppStaff) LiveCoral else if (isGroupAdminOrAppAdmin) ModAmber else SafetyEmerald,
                                                testTag = "dm_group_admin_controls_button",
                                                onClick = { showGroupAdminSheet = true }
                                            )
                                        }

                                        // Request Admin / Support Help (available in ANY Group DM)
                                        if (isGroup && !isGroupLockedOut) {
                                            DmTopDropdownChip(
                                                icon = Icons.Rounded.SupportAgent,
                                                label = if (activeThread.staffHelpRequested) "End Staff Help" else "Request Staff Help",
                                                containerColor = if (activeThread.staffHelpRequested) SafetyEmerald.copy(alpha = 0.22f) else ModAmber.copy(alpha = 0.2f),
                                                contentColor = if (activeThread.staffHelpRequested) SafetyEmerald else ModAmber,
                                                testTag = "dm_request_group_staff_help_button",
                                                onClick = {
                                                    if (activeThread.staffHelpRequested) {
                                                        viewModel.endBusinessStaffHelp(userProfile.effectiveDisplayName())
                                                    } else {
                                                        staffHelpReasonDraft = ""
                                                        showRequestStaffHelpDialog = true
                                                    }
                                                }
                                            )
                                            DmTopDropdownChip(
                                                icon = Icons.Rounded.Flag,
                                                label = "Report User",
                                                containerColor = LiveCoral.copy(alpha = 0.16f),
                                                contentColor = LiveCoral,
                                                testTag = "dm_group_report_user_button",
                                                onClick = { showReportUserPickerDialog = true }
                                            )
                                        }

                                        // Add Person -> Creates Group Chat / Adds to Group
                                        if (!userProfile.companySubAccount && !isGroupLockedOut) {
                                            DmTopDropdownChip(
                                                icon = Icons.Rounded.GroupAdd,
                                                label = if (isGroup) "Add Member" else "Add to Group",
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.primary,
                                                testTag = "dm_add_user_button",
                                                onClick = { showAddPersonSheet = true }
                                            )
                                        }

                                        // Friend Attention Alert
                                        if (!isGroup && isAcceptedFriend) {
                                            DmTopDropdownChip(
                                                icon = Icons.Rounded.NotificationsActive,
                                                label = "Ring Friend",
                                                containerColor = ModAmber.copy(alpha = 0.20f),
                                                contentColor = ModAmber,
                                                testTag = "dm_send_attention_alert_button",
                                                onClick = { onSendFriendAttentionAlert(activeThread) }
                                            )
                                        }

                                        // Kick Paid Member
                                        if (canKickOtherPaidMember) {
                                            DmTopDropdownChip(
                                                icon = Icons.Rounded.PersonRemove,
                                                label = "Kick Paid",
                                                containerColor = LiveCoral.copy(alpha = 0.16f),
                                                contentColor = LiveCoral,
                                                testTag = "dm_kick_paid_member_button",
                                                onClick = { showKickPaidMemberDialog = true }
                                            )
                                        }

                                        // Block Contact
                                        if (!isGroup && otherUid.isNotBlank() && !iBlockedOther) {
                                            DmTopDropdownChip(
                                                icon = Icons.Rounded.Block,
                                                label = "Block",
                                                containerColor = LiveCoral.copy(alpha = 0.14f),
                                                contentColor = LiveCoral,
                                                testTag = "dm_block_contact_button",
                                                onClick = { showBlockContactDialog = true }
                                            )
                                        }
                                    }

                                    // Row 2: Banner, Priority Chirp & Company Actions
                                    val showBannerRow = !userProfile.companySubAccount
                                    val showCompanyRow = activeThread.isCompanyThread() || userProfile.companySubAccount
                                    if (showBannerRow || showCompanyRow) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (showBannerRow) {
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .clickable { showDmBannerDialog = true }
                                                        .testTag("dm_open_banner_dialog_button")
                                                ) {
                                                    Text(
                                                        text = if (activeThread.hasActiveBanner()) "★ Edit Banner (1 pt)" else "★ Scrolling Banner (1 pt)",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                                if (isGroup && (isGroupAdminOrAppAdmin || isAppStaff)) {
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = LiveCoral.copy(alpha = 0.16f),
                                                        border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.55f)),
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .clickable {
                                                                showTopActionsPanel = false
                                                                showSendGroupAlertDialog = true
                                                            }
                                                            .testTag("dm_open_group_urgent_alert_button")
                                                    ) {
                                                        Text(
                                                            text = "🚨 Urgent Alert (All Phones)",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                            color = LiveCoral,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                }
                                                if (!isGroup) {
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = LiveCoral.copy(alpha = 0.14f),
                                                        border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.45f)),
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .clickable { showPriorityChirpDialog = true }
                                                            .testTag("dm_open_priority_chirp_button")
                                                    ) {
                                                        Text(
                                                            text = "📡 Priority Chirp (1–2 pts)",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = LiveCoral,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            if (showCompanyRow) {
                                                if (userProfile.companySubAccount) {
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .clickable { showEmployeeRequestDialog = true }
                                                            .testTag("dm_company_submit_request_button")
                                                    ) {
                                                        Text(
                                                            text = "+ Request",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = ModAmber.copy(alpha = 0.16f),
                                                        border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.45f)),
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .clickable { onOpenWorkHub() }
                                                            .testTag("dm_company_open_work_hub_button")
                                                    ) {
                                                        Text(
                                                            text = "Work Orders",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = ModAmber,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = if (userProfile.doNotDisturb) LiveCoral.copy(alpha = 0.18f)
                                                        else MaterialTheme.colorScheme.surfaceVariant,
                                                        border = BorderStroke(
                                                            1.dp,
                                                            if (userProfile.doNotDisturb) LiveCoral.copy(alpha = 0.55f)
                                                            else MaterialTheme.colorScheme.outlineVariant
                                                        ),
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .clickable { onToggleDnd(!userProfile.doNotDisturb) }
                                                            .testTag("dm_company_toggle_dnd_button")
                                                    ) {
                                                        Text(
                                                            text = if (userProfile.doNotDisturb) "DND ON" else "DND",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = if (userProfile.doNotDisturb) LiveCoral
                                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                } else if (canManageWorkOrders) {
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .clickable { onOpenCompanyConsole() }
                                                            .testTag("dm_company_open_console_button")
                                                    ) {
                                                        Text(
                                                            text = "Company Console",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = ModAmber.copy(alpha = 0.16f),
                                                        border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.45f)),
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .clickable { showGroupAdminSheet = true }
                                                            .testTag("dm_company_work_orders_button")
                                                    ) {
                                                        Text(
                                                            text = "Work Orders",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = ModAmber,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                    if (isGroup && activeThread.companyOwnerId == viewModel.currentUserId) {
                                                        Surface(
                                                            shape = RoundedCornerShape(999.dp),
                                                            color = if (activeThread.staffHelpRequested) SafetyEmerald.copy(alpha = 0.2f)
                                                            else LiveCoral.copy(alpha = 0.16f),
                                                            border = BorderStroke(
                                                                1.dp,
                                                                if (activeThread.staffHelpRequested) SafetyEmerald else LiveCoral.copy(alpha = 0.55f)
                                                            ),
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(999.dp))
                                                                .clickable {
                                                                    if (activeThread.staffHelpRequested) {
                                                                        viewModel.endBusinessStaffHelp(userProfile.effectiveDisplayName())
                                                                    } else {
                                                                        staffHelpReasonDraft = ""
                                                                        showRequestStaffHelpDialog = true
                                                                    }
                                                                }
                                                                .testTag(
                                                                    if (activeThread.staffHelpRequested) "end_business_staff_help_button"
                                                                    else "request_business_staff_help_button"
                                                                )
                                                        ) {
                                                            Text(
                                                                text = if (activeThread.staffHelpRequested) "End Staff Help" else "Request Staff Help",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                color = if (activeThread.staffHelpRequested) SafetyEmerald else LiveCoral,
                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                        if (isGroup && thread != null && isGroupDisabledByStaff) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LiveCoral.copy(alpha = 0.18f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("group_dm_disabled_top_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Block,
                                    contentDescription = "Group DM Disabled",
                                    tint = LiveCoral,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "GROUP DM DISABLED FOR VIOLATIONS",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = LiveCoral
                                    )
                                    Text(
                                        text = thread.groupDisabledReason.ifBlank {
                                            "Disabled by ${thread.groupDisabledByName.ifBlank { "VOX Staff" }}. Messaging and PTT are locked."
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isAppStaff) {
                                    TextButton(
                                        onClick = {
                                            viewModel.staffSetGroupDisabled(false, "", userProfile.effectiveDisplayName())
                                        },
                                        modifier = Modifier.testTag("staff_banner_reenable_group_button")
                                    ) {
                                        Text("Re-enable", color = SafetyEmerald, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        if (thread != null && thread.hasStaffAccessOpen() && (isGroup || isAppStaff)) {
                            val isStaffHere = isAppStaff
                            val isInvisibleStaff = isStaffHere && isInvisibleOwnerMode
                            val isYouthSafetyPrivateDm = !isGroup && thread.reportStaffAccessOpen
                            val bannerBg = when {
                                isInvisibleStaff -> Color(0xFF1A1033)
                                isYouthSafetyPrivateDm -> LiveCoral.copy(alpha = 0.14f)
                                else -> SafetyEmerald.copy(alpha = 0.16f)
                            }
                            val bannerFg = if (isInvisibleStaff) Color.White else MaterialTheme.colorScheme.onSurface
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bannerBg)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("business_staff_help_active_banner"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SupportAgent,
                                        contentDescription = "VOX Staff Access Active",
                                        tint = when {
                                            isInvisibleStaff -> ModAmber
                                            isYouthSafetyPrivateDm -> LiveCoral
                                            else -> SafetyEmerald
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = when {
                                                isInvisibleStaff && isYouthSafetyPrivateDm -> "INVISIBLE YOUTH SAFETY MONITOR (Staff)"
                                                isInvisibleStaff -> "INVISIBLE STAFF MODE (Admin / Support)"
                                                isStaffHere && isYouthSafetyPrivateDm -> "YOUTH SAFETY MONITOR ACTIVE (Visible)"
                                                isStaffHere -> "VOX STAFF MODERATION ACTIVE (Visible)"
                                                thread.staffHelpRequested && thread.reportStaffAccessOpen -> "VOX Staff Help & Report Review Active"
                                                thread.reportStaffAccessOpen -> "Reported Group DM · Staff Review Open"
                                                else -> "VOX Staff Help Requested"
                                            },
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = when {
                                                isInvisibleStaff -> ModAmber
                                                isYouthSafetyPrivateDm -> LiveCoral
                                                else -> SafetyEmerald
                                            }
                                        )
                                        Text(
                                            text = when {
                                                isInvisibleStaff && isYouthSafetyPrivateDm ->
                                                    thread.lastReportSummary.ifBlank { "Participants cannot see you. Monitoring 18+ / under-18 private DM invisibly." }
                                                isInvisibleStaff -> "Members cannot see you. You can monitor or open Staff Controls."
                                                thread.staffHelpReason.isNotBlank() -> thread.staffHelpReason
                                                thread.lastReportSummary.isNotBlank() -> thread.lastReportSummary
                                                else -> "Support Staff or App Admin can enter this chat (visible or invisible) to monitor."
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = bannerFg,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isStaffHere || thread.isGroupAdmin(viewModel.currentUserId) || thread.companyOwnerId == viewModel.currentUserId) {
                                        TextButton(
                                            onClick = {
                                                viewModel.endBusinessStaffHelp(userProfile.effectiveDisplayName()) {
                                                    if (isStaffHere && !thread.participantIds.contains(viewModel.currentUserId)) {
                                                        onBack()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.testTag(
                                                if (isStaffHere) "staff_end_business_help_button"
                                                else "owner_banner_end_staff_help_button"
                                            )
                                        ) {
                                            Text(
                                                text = if (isStaffHere) "End" else "End Help",
                                                color = LiveCoral,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                if (isStaffHere) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.switchStaffVisibility(
                                                    invisible = !isInvisibleOwnerMode,
                                                    staffName = userProfile.effectiveDisplayName()
                                                )
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("staff_dm_invisible_toggle")
                                        ) {
                                            Text(
                                                text = if (isInvisibleOwnerMode) "Switch to Visible" else "Go Invisible",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isInvisibleOwnerMode) ModAmber else SafetyEmerald
                                            )
                                        }
                                        if (isGroup) {
                                            Button(
                                                onClick = { showGroupAdminSheet = true },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = LiveCoral,
                                                    contentColor = Color.White
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("staff_banner_controls_button")
                                            ) {
                                                Text(
                                                    text = "Staff Controls",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (isMyGroupDndOn) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LiveCoral.copy(alpha = 0.14f))
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                    .testTag("dm_group_dnd_active_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DoNotDisturbOn,
                                    contentDescription = "Group Do Not Disturb active",
                                    tint = LiveCoral,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Do Not Disturb ON · Incoming PTT arrives as Voice-to-Text",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = LiveCoral,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(
                                    onClick = { viewModel.setGroupPttDnd(false) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("dm_group_dnd_turn_off_button")
                                ) {
                                    Text(
                                        text = "Turn Off",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = LiveCoral
                                    )
                                }
                            }
                        } else if (isMyPttVttOn) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SafetyEmerald.copy(alpha = 0.14f))
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                    .testTag("dm_ptt_vtt_active_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Subtitles,
                                    contentDescription = "PTT Voice-to-Text active",
                                    tint = SafetyEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "PTT Voice-to-Text ON · Hold PTT below to send voice as text (no live audio)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = SafetyEmerald,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(
                                    onClick = { viewModel.setPttVoiceToText(false) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("dm_ptt_vtt_turn_off_button")
                                ) {
                                    Text(
                                        text = "Turn Off",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = SafetyEmerald
                                    )
                                }
                            }
                        }
                        if (subPttDisabled || subMsgDisabled || subMicDisabled || subVideoDisabled) {
                            val disabledList = buildList {
                                if (subPttDisabled) add("PTT")
                                if (subMsgDisabled) add("Messaging")
                                if (subMicDisabled) add("Mic")
                                if (subVideoDisabled) add("Video")
                            }.joinToString(", ")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LiveCoral.copy(alpha = 0.12f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("dm_sub_capabilities_restricted_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Block,
                                    contentDescription = "Sub-account permissions restricted",
                                    tint = LiveCoral,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Company Owner disabled on your account: $disabledList",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = LiveCoral
                                )
                            }
                        }
                        if (thread?.hasActiveBanner() == true) {
                            com.example.ui.components.DmScrollingBannerBar(
                                thread = thread,
                                onTapEdit = {
                                    if (!amIKicked && !isBlockedThread && !isGroupLockedOut) {
                                        showDmBannerDialog = true
                                    }
                                }
                            )
                        } else if (isGroup && thread != null && !amIKicked && !isBlockedThread && !isGroupLockedOut && !userProfile.companySubAccount) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                                    .clickable { showDmBannerDialog = true }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                                    .testTag("group_dm_buy_banner_bar"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Campaign,
                                    contentDescription = "Buy scrolling banner for Group DM",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Pin a 24h Scrolling Banner at top of this Group DM",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = if (myStaffTier.isStaff()) "FREE" else "1 pt",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
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
                        if (thread?.hasPinnedMessage() == true) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                    .clickable {
                                        val idx = visibleMessages.indexOfFirst { it.id == thread.pinnedMessageId }
                                        scope.launch {
                                            if (idx >= 0) {
                                                listState.animateScrollToItem(idx)
                                            } else {
                                                val pinner = thread.pinnedByName.ifBlank { "a participant" }
                                                snackbarHostState.showTopMessage("Pinned by $pinner")
                                            }
                                        }
                                    }
                                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                    .testTag("dm_pinned_message_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = "Pinned message",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Pinned · ${thread.pinnedMessageSenderName.ifBlank { "Message" }}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = thread.pinnedMessageText.ifBlank { "Pinned message" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (!amIKicked && !isBlockedThread) {
                                    IconButton(
                                        onClick = { viewModel.unpinMessage() },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("dm_unpin_message_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Unpin message",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (isCompanyOwnerMonitor) {
                            val bannerBg = if (isInvisibleOwnerMode) Color(0xFF1A1033) else MaterialTheme.colorScheme.primaryContainer
                            val bannerFg = if (isInvisibleOwnerMode) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bannerBg)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag(if (isInvisibleOwnerMode) "owner_monitor_invisible_banner" else "owner_monitor_visible_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isInvisibleOwnerMode) "INVISIBLE OWNER MODE" else "VISIBLE OWNER MODE",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = if (isInvisibleOwnerMode) ModAmber else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (isInvisibleOwnerMode) {
                                            "Sub-accounts cannot see you. Read-only monitoring."
                                        } else {
                                            "You are visible as Company Owner and can reply in this DM."
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = bannerFg.copy(alpha = 0.9f)
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.setInvisibleOwnerMode(!isInvisibleOwnerMode) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag(
                                        if (isInvisibleOwnerMode) "toggle_owner_visible_button" else "toggle_owner_invisible_button"
                                    )
                                ) {
                                    Text(
                                        text = if (isInvisibleOwnerMode) "Switch to Visible" else "Go Invisible",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isInvisibleOwnerMode) ModAmber else MaterialTheme.colorScheme.primary
                                    )
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
                        AnimatedVisibility(
                            visible = !typingIndicatorLabel.isNullOrBlank(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .testTag("dm_typing_indicator"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TypingIndicatorDots(color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = typingIndicatorLabel.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        if ((isCompanyOwnerMonitor || isStaffModMonitor) && isInvisibleOwnerMode) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1A1033))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("owner_invisible_composer_hidden_bar"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isStaffModMonitor) {
                                        "Invisible Staff Mode active — switch to Visible to send messages."
                                    } else {
                                        "Invisible Mode active — switch to Visible to send a message."
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        if (isStaffModMonitor) {
                                            viewModel.switchStaffVisibility(false, userProfile.effectiveDisplayName())
                                        } else {
                                            viewModel.setInvisibleOwnerMode(false)
                                        }
                                    },
                                    modifier = Modifier.testTag("owner_bottom_switch_visible_button")
                                ) {
                                    Text("Go Visible", color = ModAmber, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else if (isGroupDisabledByStaff && !isAppStaff) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LiveCoral.copy(alpha = 0.16f))
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("group_dm_disabled_composer_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Block,
                                    contentDescription = "Group DM Disabled",
                                    tint = LiveCoral,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "This Group DM has been disabled by VOX Staff due to violations.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = LiveCoral
                                )
                            }
                        } else if (amIBannedInGroup) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LiveCoral.copy(alpha = 0.14f))
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("dm_group_banned_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Block,
                                    contentDescription = "Banned from Group DM",
                                    tint = LiveCoral,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "You have been banned from this Group DM by the Group Admin or VOX Staff.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = LiveCoral
                                )
                            }
                        } else if (amIBouncedInGroup) {
                            val remLabel = thread?.remainingGroupBounceLabel(viewModel.currentUserId, nowMillis).orEmpty()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ModAmber.copy(alpha = 0.16f))
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("dm_group_bounced_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.TimerOff,
                                    contentDescription = "Bounced from Group DM",
                                    tint = ModAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "You were bounced from this Group DM for 24h ($remLabel).",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = ModAmber
                                )
                            }
                        } else if (isMessagingDisabledByStaff) {
                            val dmNotice = userProfile.featureRestrictionNotice(
                                com.example.data.RestrictableFeature.DM,
                                nowMillis
                            ) ?: "Your messaging has been disabled by Staff. You can read messages but cannot send until re-enabled."
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LiveCoral.copy(alpha = 0.14f))
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("dm_staff_messaging_disabled_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Block,
                                    contentDescription = "Messaging disabled by Staff",
                                    tint = LiveCoral,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = dmNotice,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = LiveCoral
                                )
                            }
                        } else if (subMsgDisabled) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LiveCoral.copy(alpha = 0.14f))
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("dm_sub_messaging_disabled_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Block,
                                    contentDescription = "Messaging disabled by company owner",
                                    tint = LiveCoral,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Messaging has been disabled on your sub-account by your company owner. You can read messages but cannot send.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = LiveCoral
                                )
                            }
                        } else if (amIMutedInGroup) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ModAmber.copy(alpha = 0.14f))
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("dm_group_muted_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VolumeOff,
                                    contentDescription = "Muted in Group DM",
                                    tint = ModAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "You are muted in this Group DM by the Group Admin. You can read messages but cannot send.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = ModAmber
                                )
                            }
                        } else if (isVoxOfficial) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SnapBlue.copy(alpha = 0.12f))
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("dm_vox_official_readonly_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Verified,
                                    contentDescription = "Official VOX announcement channel",
                                    tint = SnapBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Official VOX Updates & Alerts • Replies are disabled.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = SnapBlue
                                )
                            }
                        } else if (amIKicked) {
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
                        // Group DM >15 Members PTT Speaker Selection Notice
                        if (requiresGroupPttSelection && thread != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (canUseGroupPtt) SafetyEmerald.copy(alpha = 0.12f)
                                        else ModAmber.copy(alpha = 0.12f)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("dm_group_ptt_speaker_status_bar"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (canUseGroupPtt) Icons.Rounded.RecordVoiceOver else Icons.Rounded.MicOff,
                                    contentDescription = "Group PTT speaker mode",
                                    tint = if (canUseGroupPtt) SafetyEmerald else ModAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = when {
                                        isGroupAdminOrAppAdmin ->
                                            "Large Group PTT (${thread.participantIds.size} users): ${thread.pttAllowedUserIds.size} speaker(s) selected."
                                        canUseGroupPtt ->
                                            "Large Group PTT (${thread.participantIds.size} users): You are an approved PTT speaker."
                                        else ->
                                            "Large Group PTT (${thread.participantIds.size} users): Listen-only. Ask the Group Admin for PTT access."
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isGroupAdminOrAppAdmin) {
                                    TextButton(
                                        onClick = { showGroupAdminSheet = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("dm_group_select_ptt_speakers_button")
                                    ) {
                                        Text(
                                            text = "Speakers",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }

                        // Live Push-to-Talk & Voice-to-Text / Listen-Only Live Audio Strip
                        if (isOtherPttEnabled && !isMyPttEnabled && thread != null) {
                            if (canMyAccountUsePtt && !isMyLiveAudioListenOn) {
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
                                        text = "$otherName turned on Live Push-to-Talk. Enable to stream live audio (or toggle Live Audio to just listen).",
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
                            } else if (canUseListenOnlyLiveAudio) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isMyLiveAudioListenOn) SnapBlue.copy(alpha = 0.14f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("dm_live_audio_listen_banner"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Podcasts,
                                        contentDescription = "Live Audio broadcast listen",
                                        tint = if (isMyLiveAudioListenOn) SnapBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = when {
                                            isMyLiveAudioListenOn && !thisThreadRemoteSpeaker.isNullOrBlank() ->
                                                "LIVE AUDIO · $thisThreadRemoteSpeaker is speaking…"
                                            isMyLiveAudioListenOn ->
                                                "Live Audio Listening ON · Hearing $otherName's live PTT broadcasts"
                                            else ->
                                                "$otherName has PTT ON. Tap Live Audio to hear broadcasts (otherwise arrives as Voice-to-Text)."
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = { viewModel.setPttListenOnly(!isMyLiveAudioListenOn) },
                                        shape = RoundedCornerShape(999.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isMyLiveAudioListenOn) MaterialTheme.colorScheme.surfaceVariant else SnapBlue
                                        ),
                                        modifier = Modifier.testTag("dm_live_audio_listen_banner_button")
                                    ) {
                                        Text(
                                            text = if (isMyLiveAudioListenOn) "Turn Off" else "Live Audio",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isMyLiveAudioListenOn) MaterialTheme.colorScheme.onSurface else Color.White
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            }
                        } else if (!isMyPttEnabled && isMyLiveAudioListenOn && canUseListenOnlyLiveAudio && thread != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SnapBlue.copy(alpha = 0.12f))
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                                    .testTag("dm_live_audio_listening_strip"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Podcasts,
                                    contentDescription = "Live Audio active",
                                    tint = SnapBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (!thisThreadRemoteSpeaker.isNullOrBlank()) {
                                        "LIVE AUDIO · $thisThreadRemoteSpeaker is speaking…"
                                    } else {
                                        "Live Audio ON · Ready to hear incoming PTT audio (listen-only)"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = SnapBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        }
                        val hasMyPttOrVttEnabled = isMyPttEnabled || isMyPttVttOn || isGlobalPttVttEnabled
                        if (hasMyPttOrVttEnabled && canMyAccountUsePtt && thread != null) {
                            val hasActiveFloatingOrDockedPtt = isFloatingPttEnabled &&
                                isMyPttEnabled &&
                                !isVttOnlyPttMode &&
                                (isThisThreadLivePttConnected || isThisThreadLivePttConnecting)
                            val showBottomBigPttButton = !hasActiveFloatingOrDockedPtt
                            val showLiveVttTranscript = isThisThreadTransmitting &&
                                (livePttTranscript.isNotBlank() || isVttOnlyPttMode)
                            val showBottomPttSection =
                                blockedByGroupAdminTalking || showBottomBigPttButton || showLiveVttTranscript || isVttOnlyPttMode
                            if (showBottomPttSection) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (showLiveVttTranscript) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = SnapBlue.copy(alpha = 0.14f),
                                            border = BorderStroke(1.dp, SnapBlue.copy(alpha = 0.45f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("dm_ptt_live_transcript_bar")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Subtitles,
                                                    contentDescription = "Transcribing PTT",
                                                    tint = SnapBlue,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = if (livePttTranscript.isNotBlank()) {
                                                        "Voice-to-Text: \"$livePttTranscript\""
                                                    } else {
                                                        "Listening for Voice-to-Text… speak clearly, release to send"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = SnapBlue,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    if (blockedByGroupAdminTalking) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = LiveCoral.copy(alpha = 0.14f),
                                            border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.5f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("group_admin_talking_banner")
                                        ) {
                                            Text(
                                                text = "Group DM Admin is speaking (Priority Mic) — your mic is muted until they finish.",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = LiveCoral,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                            )
                                        }
                                    }
                                    // Big Hold-to-Talk button is ONLY shown when PTT is ON and Floating PTT is disabled
                                    if (showBottomBigPttButton) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = when {
                                                blockedByGroupAdminTalking -> MaterialTheme.colorScheme.surfaceVariant
                                                isThisThreadTransmitting -> LiveCoral
                                                isVttOnlyPttMode -> SnapBlue
                                                !thisThreadRemoteSpeaker.isNullOrBlank() -> SnapBlue
                                                isGroupAdmin -> ModAmber
                                                else -> SafetyEmerald
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .pointerInput(
                                                    thread.id,
                                                    otherName,
                                                    myEffectiveName,
                                                    isInCall,
                                                    blockedByGroupAdminTalking,
                                                    isVttOnlyPttMode
                                                ) {
                                                    awaitEachGesture {
                                                        awaitFirstDown(requireUnconsumed = false)
                                                        if (blockedByGroupAdminTalking) {
                                                            scope.launch {
                                                                snackbarHostState.showTopMessage("Group DM Admin has priority mic right now.")
                                                            }
                                                            waitForUpOrCancellation()
                                                            return@awaitEachGesture
                                                        }
                                                        if (subPttDisabled || subMicDisabled) {
                                                            scope.launch {
                                                                snackbarHostState.showTopMessage(
                                                                    if (subPttDisabled) "Push-to-Talk has been disabled by your company owner."
                                                                    else "Your microphone has been disabled by your company owner."
                                                                )
                                                            }
                                                            waitForUpOrCancellation()
                                                            return@awaitEachGesture
                                                        }
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
                                                        if (isInCall && !isVttOnlyPttMode) {
                                                            viewModel.setPttLiveMic(myEffectiveName, true)
                                                            waitForUpOrCancellation()
                                                            viewModel.setPttLiveMic(myEffectiveName, false)
                                                        } else {
                                                            LivePttSessionManager.setTransmitting(
                                                                context = context,
                                                                transmitting = true,
                                                                threadId = thread.id,
                                                                peerName = otherName,
                                                                myUserId = viewModel.currentUserId,
                                                                myDisplayName = myEffectiveName,
                                                                forceVoiceToTextOnly = isVttOnlyPttMode
                                                            )
                                                            waitForUpOrCancellation()
                                                            LivePttSessionManager.setTransmitting(
                                                                context = context,
                                                                transmitting = false,
                                                                threadId = thread.id,
                                                                peerName = otherName,
                                                                myUserId = viewModel.currentUserId,
                                                                myDisplayName = myEffectiveName,
                                                                forceVoiceToTextOnly = isVttOnlyPttMode
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
                                                    imageVector = if (isVttOnlyPttMode) Icons.Rounded.Subtitles else Icons.Rounded.RecordVoiceOver,
                                                    contentDescription = if (isVttOnlyPttMode) "Hold for Voice to Text" else "Live Push to Talk",
                                                    tint = if (blockedByGroupAdminTalking) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = when {
                                                        blockedByGroupAdminTalking ->
                                                            "ADMIN SPEAKING · MIC YIELDED"
                                                        isThisThreadTransmitting && isVttOnlyPttMode ->
                                                            "VOICE-TO-TEXT (${pttElapsedSec}s) · NO AUDIO SENT"
                                                        isThisThreadTransmitting && isGroupAdmin ->
                                                            "ADMIN PRIORITY MIC LIVE (${pttElapsedSec}s) · TALKING OVER GROUP"
                                                        isThisThreadTransmitting ->
                                                            "LIVE (${pttElapsedSec}s/${pttMaxSec}s) · $otherName…"
                                                        isMyPttVttOn ->
                                                            "HOLD TO TALK · VOICE-TO-TEXT ONLY"
                                                        !hasOtherLiveListener ->
                                                            "HOLD TO TALK · VOICE-TO-TEXT ($otherName PTT OFF)"
                                                        isInCall && isMicActive ->
                                                            "LIVE AUDIO STREAMING to $otherName…"
                                                        isGroupAdmin ->
                                                            "HOLD TO TALK OVER GROUP (ADMIN PRIORITY MIC)"
                                                        !thisThreadRemoteSpeaker.isNullOrBlank() ->
                                                            "$thisThreadRemoteSpeaker is streaming live audio…"
                                                        isThisThreadLivePttConnecting ->
                                                            "Connecting live Push-to-Talk channel…"
                                                        isThisThreadLivePttConnected ->
                                                            if (isAnyThreadVttActive) {
                                                                "HOLD TO TALK + TEXT (${myMaxPttSeconds}s) · $otherName"
                                                            } else {
                                                                "HOLD TO TALK (${myMaxPttSeconds}s ${if (myMaxPttSeconds >= 60) "PRO" else "FREE"}) · $otherName"
                                                            }
                                                        else ->
                                                            "HOLD TO STREAM (${myMaxPttSeconds}s ${if (myMaxPttSeconds >= 60) "PRO" else "FREE"}) · $otherName"
                                                    },
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = if (blockedByGroupAdminTalking) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    } else if (isVttOnlyPttMode) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            border = BorderStroke(1.dp, SnapBlue.copy(alpha = 0.45f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("dm_ptt_vtt_only_status_banner")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Subtitles,
                                                    contentDescription = "Voice-to-Text mode",
                                                    tint = SnapBlue,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = if (isMyPttVttOn) {
                                                        "Voice-to-Text ON · PTT sends text to DM (no live audio broadcast)"
                                                    } else {
                                                        "$otherName has PTT OFF · PTT will send as Voice-to-Text until they enable PTT or Live Audio"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            }
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
                                        text = "${formatVoiceDuration(recordingSeconds.toLong())} / ${formatVoiceDuration(myMaxPttSeconds.toLong())} · ${if (myMaxPttSeconds >= 60) "Pro 1m" else "Free 5s max"}",
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
                        if (isGroup) {
                            val anonDisabled = thread?.anonymousModeDisabled == true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (anonDisabled) {
                                    Icon(
                                        imageVector = Icons.Rounded.Visibility,
                                        contentDescription = "Anonymous Mode disabled",
                                        tint = ModAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Anonymous Mode (Hide Name) disabled by Group Admin",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ModAmber,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("dm_anonymous_disabled_notice")
                                    )
                                } else {
                                    FilterChip(
                                        selected = hideMyNameInGroup,
                                        onClick = { hideMyNameInGroup = !hideMyNameInGroup },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.VisibilityOff,
                                                contentDescription = "Hide my name",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = if (hideMyNameInGroup) "Hide Name: ON" else "Hide Name",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        modifier = Modifier.testTag("dm_hide_name_toggle")
                                    )
                                    Text(
                                        text = if (hideMyNameInGroup) {
                                            "Others won't see your name (still reportable)"
                                        } else {
                                            "Tap Hide Name to send anonymously"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
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

                            // Record Voice Note / PTT Clip
                            IconButton(
                                onClick = ::requestVoiceNoteStart,
                                enabled = !isSending && !isEncodingSnap,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("dm_voice_note_button")
                            ) {
                                Icon(
                                    imageVector = if (canUseGroupPtt) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                                    contentDescription = if (canUseGroupPtt) "Record voice note" else "PTT restricted to selected speakers",
                                    tint = if (canUseGroupPtt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            OutlinedTextField(
                                value = composerText,
                                onValueChange = { next ->
                                    val clipped = next.take(500)
                                    composerText = clipped
                                    viewModel.onDraftTextChanged(
                                        text = clipped,
                                        suppressed = suppressTypingBroadcast
                                    )
                                },
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

                            if (canSend) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
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
                                                    senderName = myEffectiveName,
                                                    senderTier = myStaffTier,
                                                    text = toSend,
                                                    imageBase64 = snapToSend,
                                                    hideSenderName = isGroup && (thread?.anonymousModeDisabled != true) && hideMyNameInGroup,
                                                    displayedSenderTier = myDisplayedStaffTier
                                                )
                                            }
                                        },
                                        enabled = canSend,
                                        modifier = Modifier.testTag("dm_send_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Send,
                                            contentDescription = "Send private message",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            } else {
                                // 1-Tap Thumbs Up 👍 Quick Send Button
                                val canSendThumbsUp = !amIKicked && !isBlockedThread && !isGroupLockedOut && !amIMutedInGroup && !isMessagingDisabledByStaff && !isSending && !isEncodingSnap
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            viewModel.sendMessage(
                                                senderName = myEffectiveName,
                                                senderTier = myStaffTier,
                                                text = "👍",
                                                hideSenderName = isGroup && (thread?.anonymousModeDisabled != true) && hideMyNameInGroup,
                                                displayedSenderTier = myDisplayedStaffTier
                                            )
                                        },
                                        enabled = canSendThumbsUp,
                                        modifier = Modifier.testTag("dm_thumbs_up_button")
                                    ) {
                                        Text(
                                            text = "👍",
                                            fontSize = 22.sp
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
                            val hasAnyVideo = remoteVideoTrack != null || localVideoTrack != null || isCamActive
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

                                    isCamActive -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF0A0E17)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Starting your camera…",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color.White.copy(alpha = 0.85f)
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

                // MESSAGES LIST (or Group DM Bounce / Ban Lockout)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (isGroupLockedOut) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp)
                                .testTag("dm_group_lockout_card"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (amIBannedInGroup) Icons.Rounded.Block else Icons.Rounded.TimerOff,
                                contentDescription = if (amIBannedInGroup) "Banned from Group DM" else "Bounced from Group DM",
                                tint = if (amIBannedInGroup) LiveCoral else ModAmber,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (amIBannedInGroup) "Banned from Group DM" else "Bounced for 24 Hours",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (amIBannedInGroup) LiveCoral else ModAmber,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (amIBannedInGroup) {
                                    "The Group DM Administrator has banned you from participating in this group chat."
                                } else {
                                    "You were bounced by the Group DM Administrator. Remaining time: ${thread?.remainingGroupBounceLabel(viewModel.currentUserId, nowMillis).orEmpty()}."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onBack) {
                                Text("Back to Chats")
                            }
                        }
                    } else when (messagesState) {
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
                                        color = usernameColor(otherTier, userId = otherUid)
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
                                val isDockedCirclePttShown = isMyPttEnabled &&
                                    canMyAccountUsePtt &&
                                    thread != null &&
                                    isFloatingPttEnabled &&
                                    floatingDockedThreadId == thread.id
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .widthIn(max = 760.dp),
                                    contentPadding = PaddingValues(
                                        start = 14.dp,
                                        end = 14.dp,
                                        top = 12.dp,
                                        bottom = if (isDockedCirclePttShown) 92.dp else 12.dp
                                    ),
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
                                            currentUserId = viewModel.currentUserId,
                                            isMe = isMine,
                                            isPinned = thread?.pinnedMessageId == msg.id,
                                            nowMs = expiryClockMs,
                                            showReadReceipt = isMine && showReadReceipts && !msg.isSystemNotice(),
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
                                            onOpenPhoto = { photo -> fullScreenPhotoBase64 = photo },
                                            onLongPressMessage = {
                                                if (!msg.isSystemNotice()) {
                                                    actionTargetMessage = msg
                                                }
                                            },
                                            onToggleReaction = { emoji ->
                                                viewModel.toggleMessageReaction(msg, emoji)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Docked 68.dp Circle PTT Button (bottom-right, directly above the 👍 thumb button)
                    // Shown when user closed the floating PTT button while choosing to keep PTT stream open.
                    // Hold to talk; Double-tap to reopen the floating PTT button.
                    val dockedThread = thread
                    if (dockedThread != null &&
                        isMyPttEnabled &&
                        canMyAccountUsePtt &&
                        isFloatingPttEnabled &&
                        floatingDockedThreadId == dockedThread.id &&
                        !isGroupLockedOut &&
                        !amIKicked &&
                        !isBlockedThread
                    ) {
                        DockedCirclePttButton(
                            peerName = otherName,
                            isTransmitting = isThisThreadTransmitting,
                            isVttOnlyMode = isVttOnlyPttMode,
                            remoteSpeakerName = thisThreadRemoteSpeaker,
                            blockedByGroupAdmin = blockedByGroupAdminTalking,
                            pttTheme = activePttTheme,
                            onSetTransmitting = { startTx ->
                                if (startTx) {
                                    if (blockedByGroupAdminTalking) {
                                        scope.launch {
                                            snackbarHostState.showTopMessage("Group DM Admin has priority mic right now.")
                                        }
                                        return@DockedCirclePttButton
                                    }
                                    if (subPttDisabled || subMicDisabled) {
                                        scope.launch {
                                            snackbarHostState.showTopMessage(
                                                if (subPttDisabled) "Push-to-Talk has been disabled by your company owner."
                                                else "Your microphone has been disabled by your company owner."
                                            )
                                        }
                                        return@DockedCirclePttButton
                                    }
                                    if (!myStaffTier.isAdmin() && userProfile.disableMic) {
                                        scope.launch {
                                            snackbarHostState.showTopMessage("Microphone access is disabled on your account.")
                                        }
                                        return@DockedCirclePttButton
                                    }
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        pendingMicAction = "PTT"
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        return@DockedCirclePttButton
                                    }
                                    if (isInCall && !isVttOnlyPttMode) {
                                        viewModel.setPttLiveMic(myEffectiveName, true)
                                    } else {
                                        LivePttSessionManager.setTransmitting(
                                            context = context,
                                            transmitting = true,
                                            threadId = dockedThread.id,
                                            peerName = otherName,
                                            myUserId = viewModel.currentUserId,
                                            myDisplayName = myEffectiveName,
                                            forceVoiceToTextOnly = isVttOnlyPttMode
                                        )
                                    }
                                } else {
                                    if (isInCall && !isVttOnlyPttMode) {
                                        viewModel.setPttLiveMic(myEffectiveName, false)
                                    } else {
                                        LivePttSessionManager.setTransmitting(
                                            context = context,
                                            transmitting = false,
                                            threadId = dockedThread.id,
                                            peerName = otherName,
                                            myUserId = viewModel.currentUserId,
                                            myDisplayName = myEffectiveName,
                                            forceVoiceToTextOnly = isVttOnlyPttMode
                                        )
                                    }
                                }
                            },
                            onDoubleTapReopenFloating = {
                                LivePttSessionManager.reopenFloatingPttFromDock(context)
                                scope.launch {
                                    snackbarHostState.showTopMessage("Floating PTT button reopened.")
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 6.dp, bottom = 6.dp)
                        )
                    }
                }
            }
        }

        // Right-side slide-over Private / Group DM User List Drawer (styled like Chat Room User List)
        AnimatedVisibility(
            visible = showUserListDrawer && thread != null,
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
                        onClick = { showUserListDrawer = false }
                    )
                    .testTag("dm_user_list_scrim")
            )
        }

        AnimatedVisibility(
            visible = showUserListDrawer && thread != null,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        ) {
            val activeDrawerThread = thread
            if (activeDrawerThread != null) {
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
                        .fillMaxWidth(0.60f)
                        .widthIn(min = 210.dp, max = 250.dp)
                        .testTag("dm_user_list_right_drawer")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        DmUserListDrawerContent(
                            thread = activeDrawerThread,
                            currentUserId = viewModel.currentUserId,
                            myDisplayName = userProfile.effectiveDisplayName(),
                            myColorHex = userProfile.avatarColorHex,
                            myPhotoBase64 = userProfile.photoBase64,
                            myStaffTier = myStaffTier,
                            directoryByUid = directoryByUid,
                            isLocalOnMic = isMicActive || isThisThreadTransmitting,
                            remoteSpeakingName = thisThreadRemoteSpeaker ?: remoteCallSpeakerName,
                            canManageGroup = canManageGroupDm,
                            nowMillis = nowMillis,
                            onSelectUser = { uid ->
                                onOpenUserProfile(uid)
                            },
                            onOpenGroupControls = {
                                showGroupAdminSheet = true
                            },
                            onReportUser = if (activeDrawerThread.isGroup) {
                                { uid, name -> reportTargetUser = uid to name }
                            } else null,
                            onClose = { showUserListDrawer = false }
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

    fullScreenPhotoBase64?.let { photo ->
        FullScreenPhotoDialog(
            photoBase64 = photo,
            contentDescription = "Snapped photo",
            onDismiss = { fullScreenPhotoBase64 = null }
        )
    }

    if (showDmBannerDialog && thread != null) {
        com.example.ui.components.PurchaseDmBannerDialog(
            pointsBalance = userProfile.pointsBalance,
            isSending = isSending,
            existingBannerText = if (thread.hasActiveBanner()) thread.bannerText else "",
            isStaffFreePerks = myStaffTier.isStaff(),
            isGroup = isGroup,
            onBuyBanner = { bannerText, colorHex ->
                viewModel.purchaseScrollingBanner(
                    bannerText = bannerText,
                    colorHex = colorHex,
                    buyerName = myEffectiveName,
                    staffTier = myStaffTier,
                    onInsufficientPoints = onOpenBuyPoints,
                    onSuccess = { showDmBannerDialog = false }
                )
            },
            onGoBuyPoints = onOpenBuyPoints,
            onDismiss = { showDmBannerDialog = false }
        )
    }

    if (showPriorityChirpDialog && thread != null && !isGroup) {
        val recipientDirEntry = directoryEntries.firstOrNull { it.userId == otherUid }
        val recipientOptedIn = thread.isPriorityChirpOptedIn(otherUid) ||
            (recipientDirEntry?.priorityChirpOptIn == true)
        com.example.ui.components.PriorityBreakInChirpDialog(
            recipientName = otherName,
            recipientOptedIn = recipientOptedIn,
            myThreadOptIn = thread.isPriorityChirpOptedIn(viewModel.currentUserId),
            myGlobalOptIn = userProfile.priorityChirpOptIn,
            pointsBalance = userProfile.pointsBalance,
            isSending = isSending,
            isStaffFreePerks = myStaffTier.isStaff(),
            onToggleMyThreadOptIn = { optIn -> viewModel.setPriorityChirpOptIn(optIn) },
            onSendPriorityChirp = { msg, cost ->
                viewModel.sendPriorityBreakInChirp(
                    senderName = myEffectiveName,
                    senderTier = myStaffTier,
                    messageText = msg,
                    pointsCost = cost,
                    displayedSenderTier = myDisplayedStaffTier,
                    onInsufficientPoints = onOpenBuyPoints
                )
            },
            onGoBuyPoints = onOpenBuyPoints,
            onDismiss = { showPriorityChirpDialog = false }
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
        val viewerIsAdult18Plus = userProfile.isAdultViewer18Plus()
        val candidates = remember(directoryEntries, currentMemberIds, viewerIsAdult18Plus, isAppStaff) {
            directoryEntries.filter {
                it.userId !in currentMemberIds &&
                    it.isVisibleToViewer(
                        viewerIsAdult18Plus = viewerIsAdult18Plus,
                        viewerIsStaff = isAppStaff
                    )
            }
        }
        val groupOwnerUid = if (isGroup) thread.effectiveGroupAdminId() else viewModel.currentUserId
        val ownerTier = remember(groupOwnerUid, userProfile, myStaffTier, directoryEntries) {
            if (groupOwnerUid == viewModel.currentUserId) {
                userProfile.effectiveProTier(myStaffTier)
            } else {
                directoryEntries.firstOrNull { it.userId == groupOwnerUid }?.effectiveProTier() ?: ProTier.FREE
            }
        }
        val maxGroupMembers = ownerTier.maxGroupDmMembers
        val isAtMemberCap = thread.participantIds.size >= maxGroupMembers
        val upgradeMessage = when {
            thread.participantIds.size >= DirectThread.MAX_GROUP_MEMBERS ->
                "This Group DM has reached the maximum limit of ${DirectThread.MAX_GROUP_MEMBERS} users."
            ownerTier == ProTier.FREE ->
                "Free Group DMs max out at ${DirectThread.FREE_MAX_GROUP_MEMBERS} users. Upgrade to VOX Pro (\$9.99/mo) for up to ${DirectThread.PRO_MAX_GROUP_MEMBERS} users."
            ownerTier == ProTier.PRO ->
                "VOX Pro Group DMs max out at ${DirectThread.PRO_MAX_GROUP_MEMBERS} users. Upgrade to VIP Pro (\$29.99/mo) for up to ${DirectThread.VIP_PRO_MAX_GROUP_MEMBERS} users."
            ownerTier == ProTier.VIP_PRO ->
                "VIP Pro Group DMs max out at ${DirectThread.VIP_PRO_MAX_GROUP_MEMBERS} users. Upgrade to a Business Account (\$99.99/mo) for up to ${DirectThread.BUSINESS_MAX_GROUP_MEMBERS} users."
            else ->
                "This Group DM has reached the maximum limit of ${DirectThread.MAX_GROUP_MEMBERS} users."
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
                        "Members (${thread.participantIds.size}/$maxGroupMembers · ${ownerTier.label}): ${thread.memberNamesSummary()}"
                    } else {
                        "Pick another user to start a Group DM (your ${ownerTier.label} plan supports up to $maxGroupMembers users; Free max 5, Pro 15, VIP Pro 25, Business 50)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isAtMemberCap) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ModAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dm_group_capacity_limit_banner")
                    ) {
                        Text(
                            text = upgradeMessage,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

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
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = if (isAtMemberCap) 0.25f else 0.5f
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isAtMemberCap) {
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
                                                color = usernameColor(candTier, isPro = candidate.isProActive(), userId = candidate.userId)
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

    actionTargetMessage?.let { targetMsg ->
        val myActiveReaction = targetMsg.myReaction(viewModel.currentUserId)
        AlertDialog(
            onDismissRequest = { actionTargetMessage = null },
            title = {
                Text(
                    text = "Message Options",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val previewText = when {
                        targetMsg.text.isNotBlank() -> targetMsg.text.take(100)
                        targetMsg.hasVoiceNote() -> "Voice note (${formatVoiceDuration(targetMsg.audioDurationSec)})"
                        targetMsg.imageBase64.isNotBlank() -> "Photo message"
                        else -> "Private message"
                    }
                    Text(
                        text = "${targetMsg.senderName}: \"$previewText\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap an emoji to react",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DirectMessage.ALLOWED_REACTIONS.forEachIndexed { idx, emoji ->
                            val isSelected = myActiveReaction == emoji
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = if (isSelected) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else null,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        viewModel.toggleMessageReaction(targetMsg, emoji)
                                        actionTargetMessage = null
                                    }
                                    .testTag("dm_react_option_$idx")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (!amIKicked && !isBlockedThread) {
                        val isCurrentlyPinned = thread?.pinnedMessageId == targetMsg.id
                        Button(
                            onClick = {
                                actionTargetMessage = null
                                if (isCurrentlyPinned) {
                                    viewModel.unpinMessage()
                                } else {
                                    viewModel.pinMessage(targetMsg, myEffectiveName)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("dm_pin_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = if (isCurrentlyPinned) "Unpin message" else "Pin message",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCurrentlyPinned) "Unpin Message" else "Pin Message",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            actionTargetMessage = null
                            reportTargetMessage = targetMsg
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LiveCoral.copy(alpha = 0.14f),
                            contentColor = LiveCoral
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dm_report_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Flag,
                            contentDescription = "Report message",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Report Message",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTargetMessage = null }) {
                    Text("Close")
                }
            }
        )
    }

    reportTargetMessage?.let { reportingMsg ->
        var selectedCategory by remember(reportingMsg.id) { mutableStateOf(ReportCategory.HARASSMENT) }
        var reportDetails by remember(reportingMsg.id) { mutableStateOf("") }
        var isSubmittingReport by remember(reportingMsg.id) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmittingReport) reportTargetMessage = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = LiveCoral
                    )
                    Text(
                        text = "Report Private Message",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (reportingMsg.isNameHiddenFor(viewModel.currentUserId)) {
                            "Report this hidden-name group message to Admins & Support for review."
                        } else {
                            "Report this message from ${reportingMsg.senderName} to Admins & Support for review."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReportCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = {
                                    Text(
                                        text = category.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                },
                                modifier = Modifier.testTag("dm_report_category_${category.name}")
                            )
                        }
                    }
                    OutlinedTextField(
                        value = reportDetails,
                        onValueChange = { reportDetails = it.take(300) },
                        label = { Text("Additional details (optional)") },
                        placeholder = { Text("Describe why this message violates safety guidelines…") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dm_report_details_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSubmittingReport = true
                        viewModel.reportMessage(
                            reporterName = myEffectiveName,
                            message = reportingMsg,
                            category = selectedCategory,
                            details = reportDetails,
                            onReported = {
                                isSubmittingReport = false
                                reportTargetMessage = null
                            }
                        )
                    },
                    enabled = !isSubmittingReport,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiveCoral,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("dm_submit_report_button")
                ) {
                    Text(if (isSubmittingReport) "Submitting…" else "Submit Report")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { reportTargetMessage = null },
                    enabled = !isSubmittingReport
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showGroupAdminSheet && thread != null && canManageGroupDm) {
        com.example.ui.components.GroupDmAdminControlsSheet(
            thread = thread,
            currentUserId = viewModel.currentUserId,
            isGroupAdminOrAppAdmin = isGroupAdminOrAppAdmin,
            isAppStaff = isAppStaff,
            directoryByUid = directoryByUid,
            canManageWorkOrders = canManageWorkOrders,
            onDismiss = { showGroupAdminSheet = false },
            onToggleMute = { uid, name, mute ->
                viewModel.setGroupMemberMuted(uid, name, mute)
            },
            onBounce24h = { uid, name ->
                viewModel.bounceGroupMember(uid, name)
            },
            onRemoveBounce = { uid, name ->
                viewModel.removeGroupBounce(uid, name)
            },
            onBanUser = { uid, name ->
                viewModel.banGroupMember(uid, name, alsoRemoveFromParticipants = isAppStaff)
            },
            onUnbanUser = { uid, name ->
                viewModel.unbanGroupMember(uid, name)
            },
            onEditGroupName = { newName ->
                viewModel.staffEditGroupName(newName, userProfile.effectiveDisplayName())
            },
            onRemoveMember = { uid, name ->
                viewModel.staffRemoveGroupMember(uid, name, userProfile.effectiveDisplayName())
            },
            onSetGroupDisabled = { disabled, reason ->
                viewModel.staffSetGroupDisabled(disabled, reason, userProfile.effectiveDisplayName())
            },
            onToggleSupportStaff = { uid, name, promote ->
                viewModel.setGroupSupportStaff(uid, name, promote)
            },
            onToggleSupervisor = { uid, name, makeSupervisor ->
                viewModel.setGroupSupervisor(uid, name, makeSupervisor)
            },
            onSendWorkOrderClick = { uid, name ->
                showGroupAdminSheet = false
                workOrderTarget = uid to name
            },
            onTogglePttAllowed = { uid, name, allowed ->
                viewModel.setGroupPttAllowed(uid, name, allowed)
            },
            onToggleAnonymousModeDisabled = { disableAnon ->
                viewModel.setGroupAnonymousModeDisabled(disableAnon, userProfile.effectiveDisplayName())
            },
            onOpenSendGroupAlert = {
                showSendGroupAlertDialog = true
            }
        )
    }

    if (showSendGroupAlertDialog && thread != null && isGroup && (isGroupAdminOrAppAdmin || isAppStaff)) {
        val alertRecipientCount = (thread.participantIds.size - 1).coerceAtLeast(0)
        com.example.ui.components.SendGroupUrgentAlertDialog(
            groupTitle = thread.displayTitle(viewModel.currentUserId),
            recipientCount = alertRecipientCount,
            isSending = isSending,
            onSendAlert = { alertTitle, alertMessage ->
                viewModel.sendGroupUrgentAlert(
                    senderName = myEffectiveName,
                    senderTier = myStaffTier,
                    title = alertTitle,
                    message = alertMessage,
                    displayedSenderTier = myDisplayedStaffTier,
                    onSuccess = { showSendGroupAlertDialog = false }
                )
            },
            onDismiss = { showSendGroupAlertDialog = false }
        )
    }

    workOrderTarget?.let { (targetUid, targetName) ->
        if (thread != null) {
            SendWorkOrderDialog(
                targetName = targetName,
                onDismiss = { workOrderTarget = null },
                onSend = { orderType, priority, title, body ->
                    workOrderTarget = null
                    val senderRole = when {
                        myStaffTier == StaffTier.ADMIN -> "App Admin"
                        thread.isGroupAdmin(viewModel.currentUserId) -> "Owner"
                        else -> "Supervisor"
                    }
                    viewModel.sendWorkOrderFromChat(
                        senderName = userProfile.effectiveDisplayName(),
                        senderRole = senderRole,
                        targetUserId = targetUid,
                        targetName = targetName,
                        orderType = orderType,
                        priority = priority,
                        title = title,
                        body = body
                    )
                }
            )
        }
    }

    if (showEmployeeRequestDialog && thread != null) {
        SubmitEmployeeRequestDialog(
            onDismiss = { showEmployeeRequestDialog = false },
            onSubmit = { category, subject, details, urgent ->
                showEmployeeRequestDialog = false
                viewModel.submitEmployeeRequestFromChat(
                    requesterName = userProfile.effectiveDisplayName(),
                    category = category,
                    subject = subject,
                    details = details,
                    urgent = urgent
                )
            }
        )
    }

    if (showRequestStaffHelpDialog && thread != null) {
        AlertDialog(
            onDismissRequest = { showRequestStaffHelpDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.SupportAgent,
                    contentDescription = "Request VOX Staff Help",
                    tint = SafetyEmerald
                )
            },
            title = { Text("Request Admin or Support Help") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Need help with a problem in this Group DM? Requesting help lets VOX Support Staff or an App Admin enter this group (visibly or invisibly) to assist and moderate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = staffHelpReasonDraft,
                        onValueChange = { staffHelpReasonDraft = it.take(240) },
                        label = { Text("Describe the issue (Optional)") },
                        placeholder = { Text("e.g., Harassment, spam, or need help moderating this group…") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("business_staff_help_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reason = staffHelpReasonDraft
                        showRequestStaffHelpDialog = false
                        viewModel.requestBusinessStaffHelp(reason)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyEmerald, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_request_business_staff_help_button")
                ) {
                    Text("Request Help Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRequestStaffHelpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showReportUserPickerDialog && thread != null && isGroup) {
        val otherMembers = remember(thread.participantIds, thread.participantNamesById, directoryByUid) {
            thread.participantIds
                .filter { it.isNotBlank() && it != viewModel.currentUserId }
                .distinct()
                .map { uid ->
                    val display = directoryByUid[uid]?.effectiveDisplayName()?.takeIf { it.isNotBlank() }
                        ?: thread.participantNamesById[uid]?.takeIf { it.isNotBlank() }
                        ?: "Member"
                    uid to display
                }
        }
        AlertDialog(
            onDismissRequest = { showReportUserPickerDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Flag,
                    contentDescription = "Report Group Member",
                    tint = LiveCoral
                )
            },
            title = { Text("Report a Member in Group DM") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Select a member to report. Once reported, VOX Support Staff and Admins can enter this Group DM (visible or invisible) to review and take action.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (otherMembers.isEmpty()) {
                        Text(
                            text = "No other members in this group.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        otherMembers.forEach { (uid, name) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showReportUserPickerDialog = false
                                        reportTargetUser = uid to name
                                    }
                                    .testTag("pick_report_group_member_$uid")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Flag,
                                        contentDescription = "Report $name",
                                        tint = LiveCoral,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReportUserPickerDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    reportTargetUser?.let { (targetUid, targetName) ->
        var selectedCategory by remember(targetUid) { mutableStateOf(ReportCategory.HARASSMENT) }
        var reportDetails by remember(targetUid) { mutableStateOf("") }
        var isSubmittingUserReport by remember(targetUid) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmittingUserReport) reportTargetUser = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Flag,
                    contentDescription = "Report $targetName",
                    tint = LiveCoral
                )
            },
            title = { Text("Report $targetName") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Reporting $targetName notifies Admin & Support and allows VOX Staff to enter this Group DM (visible or invisible) to review.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReportCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category.label) },
                                modifier = Modifier.testTag("dm_report_user_category_${category.name}")
                            )
                        }
                    }
                    OutlinedTextField(
                        value = reportDetails,
                        onValueChange = { reportDetails = it.take(300) },
                        label = { Text("Additional details (optional)") },
                        placeholder = { Text("Explain what happened in this Group DM…") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dm_report_user_details_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSubmittingUserReport = true
                        viewModel.reportGroupMember(
                            reporterName = userProfile.effectiveDisplayName(),
                            targetUserId = targetUid,
                            targetName = targetName,
                            category = selectedCategory,
                            details = reportDetails,
                            onReported = {
                                isSubmittingUserReport = false
                                reportTargetUser = null
                            }
                        )
                    },
                    enabled = !isSubmittingUserReport,
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCoral, contentColor = Color.White),
                    modifier = Modifier.testTag("dm_submit_user_report_button")
                ) {
                    Text(if (isSubmittingUserReport) "Submitting…" else "Submit Report")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { reportTargetUser = null },
                    enabled = !isSubmittingUserReport
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SnapChatMessageBlock(
    message: DirectMessage,
    currentUserId: String,
    isMe: Boolean,
    isPinned: Boolean = false,
    nowMs: Long,
    showReadReceipt: Boolean,
    isReadByOther: Boolean,
    showReadTimeLabel: Boolean,
    otherReadMillis: Long,
    isPlayingVoice: Boolean,
    voicePlaybackProgress: Float,
    onToggleVoicePlay: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onLongPressMessage: () -> Unit,
    onToggleReaction: (String) -> Unit
) {
    if (message.isPriorityChirp()) {
        com.example.ui.components.PriorityChirpMessageCard(message = message)
        return
    }

    if (message.isGroupUrgentAlert()) {
        com.example.ui.components.GroupUrgentAlertMessageCard(message = message)
        return
    }

    // Centered system banners for Screenshot alerts, PTT disconnect, Enter/Exit presence, Group mod & Support handoffs
    if (message.isSystemNotice()) {
        val isScreenshot = message.isScreenshotNotice()
        val lowerText = message.text.lowercase()
        val isPttDisconnect = lowerText.contains("disconnected from push-to-talk")
        val isEntered = lowerText.endsWith("entered the chat")
        val isExited = lowerText.endsWith("exited the chat")
        val isModNotice = lowerText.contains("was bounced") ||
            lowerText.contains("was banned") ||
            lowerText.contains("was unbanned") ||
            lowerText.contains("was muted") ||
            lowerText.contains("was unmuted") ||
            lowerText.contains("bounce was removed")
        val accent = when {
            isScreenshot -> LiveCoral
            isPttDisconnect -> ModAmber
            isEntered -> SafetyEmerald
            isExited -> MaterialTheme.colorScheme.onSurfaceVariant
            isModNotice -> if (lowerText.contains("banned") || lowerText.contains("bounced")) LiveCoral else ModAmber
            else -> MaterialTheme.colorScheme.primary
        }
        val noticeIcon = when {
            isScreenshot -> Icons.Rounded.Screenshot
            isPttDisconnect -> Icons.Rounded.RecordVoiceOver
            isEntered -> Icons.AutoMirrored.Rounded.Login
            isExited -> Icons.AutoMirrored.Rounded.Logout
            isModNotice -> Icons.Rounded.Gavel
            else -> Icons.Rounded.SupportAgent
        }
        val noticeDesc = when {
            isScreenshot -> "Screenshot taken"
            isPttDisconnect -> "Push-to-Talk disconnected"
            isEntered -> "Entered chat"
            isExited -> "Exited chat"
            isModNotice -> "Chat moderation notice"
            else -> "Support handoff"
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                modifier = Modifier.testTag(
                    when {
                        isScreenshot -> "dm_screenshot_notice_${message.id}"
                        isPttDisconnect -> "dm_ptt_disconnect_notice_${message.id}"
                        isEntered || isExited -> "dm_presence_notice_${message.id}"
                        isModNotice -> "dm_mod_notice_${message.id}"
                        else -> "dm_support_handoff_notice_${message.id}"
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = noticeIcon,
                        contentDescription = noticeDesc,
                        tint = accent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${message.text} · ${formatTimestampShort(message.createdAt)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accent,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    val isHiddenForViewer = message.isNameHiddenFor(currentUserId)
    val tier = message.senderTierEnum()
    val accentBarColor = if (isHiddenForViewer) {
        MaterialTheme.colorScheme.outline
    } else {
        usernameColor(tier, userId = message.senderId)
    }
    val snapBitmap = rememberBase64ImageBitmap(message.imageBase64)
    val hasVoice = message.hasVoiceNote()
    val isStandaloneThumbsUp = message.isStandaloneThumbsUp()
    val groupedReactions = remember(message.reactionsByUserId) { message.groupedReactions() }
    val myReaction = message.myReaction(currentUserId)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = when {
                    isMe && message.hideSenderName -> "ME · NAME HIDDEN"
                    isMe -> "ME"
                    isHiddenForViewer -> "HIDDEN MEMBER"
                    else -> message.senderName.uppercase()
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = accentBarColor
            )
            if (!isMe && !isHiddenForViewer) {
                StaffTierBadge(tier = tier)
            }
            Text(
                text = formatTimestampShort(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isPinned) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pinned message badge",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Pinned",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
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
                    .height(if (snapBitmap != null) 56.dp else if (hasVoice) 44.dp else if (isStandaloneThumbsUp) 38.dp else 26.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentBarColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isStandaloneThumbsUp) {
                    Color.Transparent
                } else if (isMe) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                },
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(14.dp))
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPressMessage
                    )
                    .testTag("dm_message_bubble_${message.id}")
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = if (snapBitmap != null) 8.dp else if (isStandaloneThumbsUp) 4.dp else 12.dp,
                        vertical = if (isStandaloneThumbsUp) 2.dp else 8.dp
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
                                .combinedClickable(
                                    onClick = { onOpenPhoto(message.imageBase64) },
                                    onLongClick = onLongPressMessage
                                )
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
                    if (isStandaloneThumbsUp) {
                        Text(
                            text = "👍",
                            fontSize = 36.sp
                        )
                    } else if (message.text.isNotBlank()) {
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

        if (groupedReactions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(start = 14.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groupedReactions.forEach { (emoji, count) ->
                    val isMineReaction = myReaction == emoji
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isMineReaction) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isMineReaction) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onToggleReaction(emoji) }
                            .testTag("dm_reaction_chip_${message.id}_$emoji")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = emoji, fontSize = 13.sp)
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isMineReaction) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
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

@Composable
private fun TypingIndicatorDots(
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "dm_typing_dots")
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(3) { index ->
            val dotAlpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 480),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 160)
                ),
                label = "typing_dot_$index"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = dotAlpha))
            )
        }
    }
}

