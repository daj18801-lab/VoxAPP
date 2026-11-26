package com.example.media

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.DirectThread
import com.example.data.StaffTier
import com.example.notifications.DirectMessageNotificationHelper
import com.example.notifications.DirectMessageNotificationPrefs
import com.example.notifications.LockScreenRecentDm
import com.example.ui.theme.VoxStageTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lock-Screen Push-to-Talk & Recent DM Voice-to-Text Activity rendered over the Android lock screen
 * (`showWhenLocked="true"`) so users can transmit live PTT or select the most recent DM and reply
 * via Push-to-Talk voice-to-text without unlocking their phone.
 *
 * Uses zero sensitive permissions (no USE_FULL_SCREEN_INTENT, no DISABLE_KEYGUARD) and supports
 * both direct touch hold-to-talk and hardware Volume Down Push-to-Talk.
 */
class LockScreenPttActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var dictationTargetDm: LockScreenRecentDm? = null
    private var fallbackCommitRunnable: Runnable? = null

    private var isPreviewLaunch by mutableStateOf(false)
    private var showRecentDm by mutableStateOf(true)
    private var isDmReplySelected by mutableStateOf(false)
    private var isDictating by mutableStateOf(false)
    private var isSendingReply by mutableStateOf(false)
    private var liveTranscript by mutableStateOf("")
    private var replyFeedbackMessage by mutableStateOf<String?>(null)
    private var replyFeedbackIsError by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        enableEdgeToEdge()

        isPreviewLaunch = intent?.getBooleanExtra(EXTRA_PREVIEW_MODE, false) == true
        showRecentDm = DirectMessageNotificationPrefs.isLockScreenRecentDmEnabled(this)
        DirectMessageNotificationHelper.ensureRecentDmLoaded(this)
        LivePttSessionManager.syncAudioPrefs(this)

        setContent {
            VoxStageTheme(darkTheme = true) {
                LockScreenPttContent(
                    isPreviewLaunch = isPreviewLaunch,
                    showRecentDm = showRecentDm,
                    isDmReplySelected = isDmReplySelected,
                    isDictating = isDictating,
                    isSendingReply = isSendingReply,
                    liveTranscript = liveTranscript,
                    replyFeedbackMessage = replyFeedbackMessage,
                    replyFeedbackIsError = replyFeedbackIsError,
                    onToggleShowRecentDm = {
                        val next = !showRecentDm
                        showRecentDm = next
                        DirectMessageNotificationPrefs.setLockScreenRecentDmEnabled(this, next)
                        if (!next) {
                            cancelDictation()
                            isDmReplySelected = false
                        }
                    },
                    onToggleSelectRecentDm = { dm ->
                        if (dm?.voxOfficial == true) {
                            isDmReplySelected = false
                            replyFeedbackIsError = false
                            replyFeedbackMessage = "Official VOX update messages are read-only."
                            return@LockScreenPttContent
                        }
                        if (isDictating) cancelDictation()
                        replyFeedbackMessage = null
                        replyFeedbackIsError = false
                        liveTranscript = ""
                        isDmReplySelected = !isDmReplySelected
                        if (isDmReplySelected && dm != null) {
                            LivePttSessionManager.triggerPttHaptic(this)
                        }
                    },
                    onStartVoiceToTextReply = { dm ->
                        if (dm.voxOfficial) {
                            replyFeedbackIsError = false
                            replyFeedbackMessage = "Official VOX update messages are read-only."
                        } else {
                            startVoiceToTextDictation(dm)
                        }
                    },
                    onStopVoiceToTextReply = {
                        stopVoiceToTextDictation()
                    },
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        isPreviewLaunch = intent.getBooleanExtra(EXTRA_PREVIEW_MODE, false) == true
        showRecentDm = DirectMessageNotificationPrefs.isLockScreenRecentDmEnabled(this)
        DirectMessageNotificationHelper.ensureRecentDmLoaded(this)
        LivePttSessionManager.syncAudioPrefs(this)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN &&
            DirectMessageNotificationPrefs.isVolumeDownPttEnabled(this)
        ) {
            val activeDm = DirectMessageNotificationHelper.recentDmForLockScreen.value
            val useVoiceToTextMode = showRecentDm && isDmReplySelected && activeDm != null && !activeDm.voxOfficial
            if (useVoiceToTextMode && activeDm != null) {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (event.repeatCount == 0) {
                            LivePttSessionManager.triggerPttHaptic(this)
                            startVoiceToTextDictation(activeDm)
                        }
                        return true
                    }
                    KeyEvent.ACTION_UP -> {
                        stopVoiceToTextDictation()
                        return true
                    }
                }
            } else {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (event.repeatCount == 0) {
                            if (LivePttSessionManager.handleHardwarePttKey(this, pressed = true)) {
                                LivePttSessionManager.triggerPttHaptic(this)
                                return true
                            }
                        } else if (!LivePttSessionManager.activeThreadId.value.isNullOrBlank()) {
                            return true
                        }
                    }
                    KeyEvent.ACTION_UP -> {
                        if (LivePttSessionManager.handleHardwarePttKey(this, pressed = false)) {
                            return true
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        // Release transmission and dictation safely if user leaves the lock-screen overlay
        if (LivePttSessionManager.isTransmitting.value) {
            LivePttSessionManager.setTransmitting(this, false)
        }
        cancelDictation()
    }

    override fun onDestroy() {
        cancelFallbackCommit()
        destroySpeechRecognizer()
        activityScope.cancel()
        super.onDestroy()
    }

    private fun startVoiceToTextDictation(targetDm: LockScreenRecentDm) {
        if (isSendingReply || isDictating) return
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            replyFeedbackIsError = true
            replyFeedbackMessage = "Microphone permission is required in VOX to send voice-to-text replies."
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            replyFeedbackIsError = true
            replyFeedbackMessage = "On-device voice-to-text recognition is unavailable on this device."
            return
        }

        // Pause walkie audio transmit & PTT speech capture so the mic is dedicated to DM reply dictation
        LivePttSessionManager.cancelPttSpeechCapture()
        if (LivePttSessionManager.isTransmitting.value) {
            LivePttSessionManager.setTransmitting(this, false)
        }

        cancelFallbackCommit()
        dictationTargetDm = targetDm
        liveTranscript = ""
        replyFeedbackMessage = null
        replyFeedbackIsError = false
        isDictating = true

        try {
            val recognizer = ensureSpeechRecognizer()
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            recognizer.startListening(speechIntent)
        } catch (err: Exception) {
            Log.w(TAG, "Failed to start lock-screen SpeechRecognizer", err)
            isDictating = false
            dictationTargetDm = null
            replyFeedbackIsError = true
            replyFeedbackMessage = "Unable to start voice-to-text. Hold PTT to try again."
        }
    }

    private fun stopVoiceToTextDictation() {
        if (!isDictating) return
        try {
            speechRecognizer?.stopListening()
        } catch (err: Exception) {
            Log.w(TAG, "Error stopping SpeechRecognizer", err)
        }
        // Safety fallback: if the speech service takes too long after release, commit any captured partial text
        cancelFallbackCommit()
        val runnable = Runnable {
            if (isDictating) {
                val target = dictationTargetDm
                val captured = liveTranscript.trim()
                isDictating = false
                dictationTargetDm = null
                if (target != null && captured.isNotBlank()) {
                    sendVoiceToTextDmReply(target, captured)
                } else {
                    replyFeedbackIsError = true
                    replyFeedbackMessage = "Didn't catch any speech — hold PTT and speak clearly."
                }
            }
        }
        fallbackCommitRunnable = runnable
        mainHandler.postDelayed(runnable, 2_500L)
    }

    private fun cancelDictation() {
        cancelFallbackCommit()
        isDictating = false
        dictationTargetDm = null
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
            // Ignored
        }
    }

    private fun cancelFallbackCommit() {
        fallbackCommitRunnable?.let { mainHandler.removeCallbacks(it) }
        fallbackCommitRunnable = null
    }

    private fun destroySpeechRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
            // Ignored
        }
        speechRecognizer = null
    }

    private fun ensureSpeechRecognizer(): SpeechRecognizer {
        speechRecognizer?.let { return it }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotBlank()) {
                    liveTranscript = partial
                }
            }

            override fun onResults(results: Bundle?) {
                cancelFallbackCommit()
                val target = dictationTargetDm
                val spoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                    .ifBlank { liveTranscript.trim() }
                isDictating = false
                dictationTargetDm = null
                if (target != null && spoken.isNotBlank()) {
                    liveTranscript = spoken
                    sendVoiceToTextDmReply(target, spoken)
                } else {
                    replyFeedbackIsError = true
                    replyFeedbackMessage = "Didn't catch any speech — hold PTT and speak clearly."
                }
            }

            override fun onError(error: Int) {
                cancelFallbackCommit()
                val target = dictationTargetDm
                val partial = liveTranscript.trim()
                isDictating = false
                dictationTargetDm = null
                // If user released button after speaking and we already have partial text, send it!
                if (target != null && partial.isNotBlank() &&
                    (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                ) {
                    sendVoiceToTextDmReply(target, partial)
                    return
                }
                replyFeedbackIsError = true
                replyFeedbackMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error — check microphone."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during voice-to-text."
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that — hold PTT and speak clearly."
                    else -> "Voice-to-text interrupted. Hold PTT to try again."
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer = recognizer
        return recognizer
    }

    private fun sendVoiceToTextDmReply(targetDm: LockScreenRecentDm, spokenText: String) {
        val cleanText = spokenText.trim().take(500)
        if (cleanText.isBlank()) return

        isSendingReply = true
        replyFeedbackIsError = false
        replyFeedbackMessage = "Sending voice-to-text reply to ${targetDm.senderName}…"

        val appContext = applicationContext
        activityScope.launch(Dispatchers.IO) {
            val outcome = runCatching {
                val uid = Firebase.auth.currentUser?.uid
                    ?: throw IllegalStateException("Please sign in to VOX first.")
                val databaseId = appContext.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val threadRef = db.collection("directThreads").document(targetDm.threadId)
                val threadSnap = threadRef.get().await()
                val thread = threadSnap.toObject(DirectThread::class.java)
                    ?: throw IllegalStateException("Conversation is no longer available.")
                if (thread.voxOfficial) {
                    throw IllegalStateException("Official VOX update messages are read-only.")
                }
                if (!thread.participantIds.contains(uid) || thread.isUserKicked(uid) || thread.hasAnyBlock()) {
                    throw IllegalStateException("You can no longer message this conversation.")
                }

                val profileSnap = runCatching { db.collection("users").document(uid).get().await() }.getOrNull()
                if (profileSnap?.getBoolean("messagingDisabled") == true) {
                    throw IllegalStateException("Messaging is currently disabled on your account.")
                }
                val staffSnap = runCatching { db.collection("staffRoles").document(uid).get().await() }.getOrNull()
                val senderName = profileSnap?.getString("displayName")?.trim()
                    ?.ifBlank { null }
                    ?: Firebase.auth.currentUser?.displayName?.trim()?.ifBlank { null }
                    ?: "Member"
                val senderTier = StaffTier.fromRaw(staffSnap?.getString("staffTier"))

                val msgRef = threadRef.collection("messages").document()
                val msgPayload = mapOf(
                    "threadId" to targetDm.threadId,
                    "participantIds" to thread.participantIds,
                    "senderId" to uid,
                    "senderName" to senderName.take(40),
                    "senderTier" to senderTier.name,
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                val unreadRecipients = thread.participantIds.filter { it != uid }
                val batch = db.batch()
                batch.set(msgRef, msgPayload)
                batch.update(
                    threadRef,
                    mapOf(
                        "lastMessageText" to cleanText.take(300),
                        "lastMessageSenderId" to uid,
                        "unreadByUserIds" to unreadRecipients,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
                DirectMessageNotificationHelper.cancelThreadNotification(appContext, targetDm.threadId)
            }

            launch(Dispatchers.Main.immediate) {
                isSendingReply = false
                outcome.onSuccess {
                    LivePttSessionManager.triggerPttHaptic(this@LockScreenPttActivity)
                    replyFeedbackIsError = false
                    replyFeedbackMessage = "Sent to ${targetDm.senderName}: \"$cleanText\""
                    liveTranscript = ""
                }.onFailure { err ->
                    Log.w(TAG, "Failed to send lock-screen voice-to-text DM reply", err)
                    replyFeedbackIsError = true
                    replyFeedbackMessage = err.message ?: "Unable to send reply right now."
                }
            }
        }
    }

    companion object {
        private const val TAG = "LockScreenPttActivity"
        const val EXTRA_PREVIEW_MODE = "extra_lock_screen_preview_mode"

        fun createPreviewIntent(context: Context): Intent =
            Intent(context, LockScreenPttActivity::class.java).apply {
                putExtra(EXTRA_PREVIEW_MODE, true)
            }
    }
}

@Composable
private fun LockScreenPttContent(
    isPreviewLaunch: Boolean,
    showRecentDm: Boolean,
    isDmReplySelected: Boolean,
    isDictating: Boolean,
    isSendingReply: Boolean,
    liveTranscript: String,
    replyFeedbackMessage: String?,
    replyFeedbackIsError: Boolean,
    onToggleShowRecentDm: () -> Unit,
    onToggleSelectRecentDm: (LockScreenRecentDm?) -> Unit,
    onStartVoiceToTextReply: (LockScreenRecentDm) -> Unit,
    onStopVoiceToTextReply: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activeThreadId by LivePttSessionManager.activeThreadId.collectAsStateWithLifecycle()
    val peerName by LivePttSessionManager.activePeerName.collectAsStateWithLifecycle()
    val isConnected by LivePttSessionManager.isConnected.collectAsStateWithLifecycle()
    val isTransmitting by LivePttSessionManager.isTransmitting.collectAsStateWithLifecycle()
    val remoteSpeakingName by LivePttSessionManager.remoteSpeakingName.collectAsStateWithLifecycle()
    val elapsedSec by LivePttSessionManager.transmittingElapsedSec.collectAsStateWithLifecycle()
    val maxSec by LivePttSessionManager.maxPttSeconds.collectAsStateWithLifecycle()
    val activeTheme by LivePttSessionManager.activePttTheme.collectAsStateWithLifecycle()
    val isLoudspeakerEnabled by LivePttSessionManager.isLoudspeakerEnabled.collectAsStateWithLifecycle()
    val isPttSilentMode by LivePttSessionManager.isPttSilentMode.collectAsStateWithLifecycle()
    val isBeepSilenced by LivePttSessionManager.isBeepSilenced.collectAsStateWithLifecycle()
    val isPttVoiceToTextEnabled by LivePttSessionManager.isPttVoiceToTextEnabled.collectAsStateWithLifecycle()
    val isLockScreenDndEnabled by LivePttSessionManager.isLockScreenDndEnabled.collectAsStateWithLifecycle()
    val threadRequiresVoiceToText by LivePttSessionManager.threadRequiresVoiceToText.collectAsStateWithLifecycle()
    val threadDndActiveForMe by LivePttSessionManager.threadDndActiveForMe.collectAsStateWithLifecycle()
    val livePttTranscript by LivePttSessionManager.livePttTranscript.collectAsStateWithLifecycle()
    val lastPttTranscriptFeedback by LivePttSessionManager.lastPttTranscriptFeedback.collectAsStateWithLifecycle()
    val lastIncomingBeep by LivePttSessionManager.lastIncomingBeep.collectAsStateWithLifecycle()
    val recentDm by DirectMessageNotificationHelper.recentDmForLockScreen.collectAsStateWithLifecycle()

    val isDndEffectivelyOn = isLockScreenDndEnabled || threadDndActiveForMe
    val isVttEffectivelyOn = isPttVoiceToTextEnabled || threadRequiresVoiceToText

    var beepFeedbackText by remember { mutableStateOf<String?>(null) }
    var beepFeedbackIsError by remember { mutableStateOf(false) }

    LaunchedEffect(beepFeedbackText) {
        if (!beepFeedbackText.isNullOrBlank()) {
            delay(3_200L)
            beepFeedbackText = null
        }
    }

    LaunchedEffect(lastPttTranscriptFeedback) {
        if (!lastPttTranscriptFeedback.isNullOrBlank()) {
            delay(4_000L)
            LivePttSessionManager.clearPttTranscriptFeedback()
        }
    }

    LaunchedEffect(lastIncomingBeep?.timestampMillis) {
        val beep = lastIncomingBeep ?: return@LaunchedEffect
        val ageMs = System.currentTimeMillis() - beep.timestampMillis
        if (ageMs <= 20_000L) {
            beepFeedbackIsError = false
            beepFeedbackText = if (isBeepSilenced) {
                "Silent beep from ${beep.senderName} (${beep.channelName})"
            } else {
                "Beep from ${beep.senderName} (${beep.channelName})!"
            }
        }
    }

    LaunchedEffect(activeThreadId, isPreviewLaunch, isDmReplySelected) {
        if (activeThreadId.isNullOrBlank() && !isPreviewLaunch && !isDmReplySelected) {
            onClose()
        }
    }

    val voiceReplyModeActive = showRecentDm && isDmReplySelected && recentDm != null && recentDm?.voxOfficial != true
    val standbyColor = if (voiceReplyModeActive) {
        Color(0xFF0EA5E9)
    } else {
        parseHexOrFallback(activeTheme.standbyColorHex, Color(0xFF10B981))
    }
    val liveColor = if (voiceReplyModeActive) {
        Color(0xFF6366F1)
    } else {
        parseHexOrFallback(activeTheme.liveColorHex, Color(0xFFEF4444))
    }
    val ringColor = if (voiceReplyModeActive) {
        Color(0xFF38BDF8)
    } else {
        parseHexOrFallback(activeTheme.ringGlowHex, Color(0xFF34D399))
    }
    val isActiveAction = if (voiceReplyModeActive) isDictating else isTransmitting
    val activeCoreColor = if (isActiveAction) liveColor else standbyColor

    val infiniteTransition = rememberInfiniteTransition(label = "lock_ptt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isActiveAction || remoteSpeakingName != null) 1.18f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActiveAction) 550 else 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lock_ptt_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060B18),
                        Color(0xFF0B162C),
                        Color(0xFF050A14)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header with Privacy Toggle (Show/Hide Recent DM) & Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.10f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Lock screen PTT",
                        tint = ringColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "VOX • LOCK SCREEN",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onToggleShowRecentDm,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("lock_screen_toggle_recent_dm"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (showRecentDm) {
                            Color(0xFF0EA5E9).copy(alpha = 0.22f)
                        } else {
                            Color.White.copy(alpha = 0.10f)
                        },
                        contentColor = if (showRecentDm) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.72f)
                    )
                ) {
                    Icon(
                        imageVector = if (showRecentDm) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (showRecentDm) {
                            "Hide recent DM on lock screen"
                        } else {
                            "Show recent DM on lock screen"
                        }
                    )
                }

                FilledTonalIconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("lock_screen_ptt_close_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close lock-screen PTT"
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Recent DM Preview & Voice-to-Text Reply Selector Area
        if (showRecentDm) {
            LockScreenRecentDmCard(
                recentDm = recentDm,
                isSelected = isDmReplySelected && recentDm != null,
                isDictating = isDictating,
                isSendingReply = isSendingReply,
                liveTranscript = liveTranscript,
                replyFeedbackMessage = replyFeedbackMessage,
                replyFeedbackIsError = replyFeedbackIsError,
                onToggleSelect = { onToggleSelectRecentDm(recentDm) }
            )
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleShowRecentDm)
                    .testTag("lock_screen_recent_dm_hidden_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Recent DM preview hidden • Tap to show on lock screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // Active Channel / Reply Target Header
        Text(
            text = if (voiceReplyModeActive && recentDm != null) {
                "Reply to ${recentDm!!.senderName}"
            } else {
                peerName.ifBlank { "Private Walkie Channel" }
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))

        val statusText = when {
            voiceReplyModeActive && isSendingReply -> "SENDING VOICE-TO-TEXT…"
            voiceReplyModeActive && isDictating -> "LISTENING • Release PTT to send"
            voiceReplyModeActive -> "VOICE-TO-TEXT READY • Hold PTT to reply"
            isTransmitting && isVttEffectivelyOn -> "ON AIR + VOICE-TO-TEXT • ${elapsedSec}s / ${maxSec}s"
            isTransmitting -> "ON AIR • ${elapsedSec}s / ${maxSec}s"
            isDndEffectivelyOn -> "DO NOT DISTURB • Incoming PTT → Text"
            isPttSilentMode -> if (isBeepSilenced) {
                "PTT SILENT • Alerts still active"
            } else {
                "PTT SILENT • Alerts & Beeps active"
            }
            !remoteSpeakingName.isNullOrBlank() -> "$remoteSpeakingName is talking…"
            isConnected && isVttEffectivelyOn -> "LIVE + VOICE-TO-TEXT • ${if (isLoudspeakerEnabled) "Loudspeaker" else "Phone Speaker"}"
            isConnected -> "LIVE STANDBY • ${if (isLoudspeakerEnabled) "Loudspeaker" else "Phone Speaker"}"
            !activeThreadId.isNullOrBlank() -> "Connecting walkie channel…"
            else -> "Select Recent DM above to reply by voice"
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = when {
                voiceReplyModeActive && isDictating -> Color(0xFF6366F1).copy(alpha = 0.30f)
                voiceReplyModeActive -> Color(0xFF0EA5E9).copy(alpha = 0.24f)
                isTransmitting -> liveColor.copy(alpha = 0.25f)
                isDndEffectivelyOn -> Color(0xFFE11D48).copy(alpha = 0.28f)
                !remoteSpeakingName.isNullOrBlank() -> Color(0xFF0EA5E9).copy(alpha = 0.25f)
                else -> Color.White.copy(alpha = 0.10f)
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSendingReply) {
                    CircularProgressIndicator(
                        color = Color(0xFF38BDF8),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = when {
                            voiceReplyModeActive -> Icons.Filled.RecordVoiceOver
                            !remoteSpeakingName.isNullOrBlank() -> Icons.AutoMirrored.Filled.VolumeUp
                            else -> Icons.Filled.Radio
                        },
                        contentDescription = null,
                        tint = if (isActiveAction) liveColor else ringColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Giant Lock-Screen Hold-to-Talk / Voice-to-Text Reply Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(224.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                (if (isActiveAction) liveColor else ringColor).copy(alpha = 0.44f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(178.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                activeCoreColor,
                                activeCoreColor.copy(alpha = 0.78f)
                            )
                        )
                    )
                    .border(
                        width = 4.dp,
                        color = if (isActiveAction) Color.White else ringColor,
                        shape = CircleShape
                    )
                    .testTag("lock_screen_ptt_hold_button")
                    .pointerInput(activeThreadId, voiceReplyModeActive, recentDm?.threadId) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            LivePttSessionManager.triggerPttHaptic(context)
                            val targetDm = recentDm
                            if (voiceReplyModeActive && targetDm != null) {
                                onStartVoiceToTextReply(targetDm)
                                waitForUpOrCancellation()
                                onStopVoiceToTextReply()
                            } else if (!activeThreadId.isNullOrBlank()) {
                                LivePttSessionManager.setTransmitting(context, true)
                                waitForUpOrCancellation()
                                LivePttSessionManager.setTransmitting(context, false)
                            } else {
                                waitForUpOrCancellation()
                            }
                        }
                    }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (voiceReplyModeActive) Icons.Filled.RecordVoiceOver else Icons.Filled.Mic,
                        contentDescription = if (voiceReplyModeActive) {
                            "Hold PTT to send voice-to-text DM reply"
                        } else {
                            "Hold to talk on lock screen"
                        },
                        tint = Color.White,
                        modifier = Modifier.size(58.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            voiceReplyModeActive && isSendingReply -> "SENDING…"
                            voiceReplyModeActive && isDictating -> "LISTENING"
                            voiceReplyModeActive -> "HOLD TO REPLY"
                            isTransmitting -> "TALKING"
                            else -> "HOLD TO TALK"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Live PTT Voice-to-Text transcript & sent confirmation pill
        AnimatedVisibility(
            visible = !voiceReplyModeActive &&
                (livePttTranscript.isNotBlank() || !lastPttTranscriptFeedback.isNullOrBlank())
        ) {
            val showingLive = livePttTranscript.isNotBlank()
            val vttColor = if (showingLive) Color(0xFF38BDF8) else Color(0xFF10B981)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = vttColor.copy(alpha = 0.20f),
                border = BorderStroke(1.dp, vttColor.copy(alpha = 0.60f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("lock_screen_ptt_vtt_live_transcript")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Subtitles,
                        contentDescription = null,
                        tint = vttColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (showingLive) {
                            "Transcribing: \"$livePttTranscript\""
                        } else {
                            lastPttTranscriptFeedback.orEmpty()
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Feedback pill for outgoing / incoming PTT Beep tones
        AnimatedVisibility(visible = !beepFeedbackText.isNullOrBlank()) {
            val pillColor = if (beepFeedbackIsError) Color(0xFFEF4444) else Color(0xFFF59E0B)
            Surface(
                shape = RoundedCornerShape(50),
                color = pillColor.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, pillColor.copy(alpha = 0.65f)),
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .testTag("lock_screen_beep_feedback")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = pillColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = beepFeedbackText.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Lock-Screen Quick Controls: Beep, Speaker Toggle, PTT Silent Mode, Silence Beep
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LockScreenQuickControlButton(
                    title = "Beep",
                    subtitle = "Ping group / peer",
                    icon = Icons.Filled.NotificationsActive,
                    isActive = false,
                    accentColor = Color(0xFFF59E0B),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lock_screen_ptt_beep_button"),
                    onClick = {
                        LivePttSessionManager.sendPttBeep(
                            context = context,
                            threadId = activeThreadId
                        ) { result ->
                            result.onSuccess { msg ->
                                beepFeedbackIsError = false
                                beepFeedbackText = msg
                            }.onFailure { err ->
                                beepFeedbackIsError = true
                                beepFeedbackText = err.message ?: "Unable to send beep right now."
                            }
                        }
                    }
                )
                LockScreenQuickControlButton(
                    title = if (isLoudspeakerEnabled) "Loudspeaker" else "Phone Speaker",
                    subtitle = if (isLoudspeakerEnabled) "Tap for earpiece" else "Tap for loud",
                    icon = if (isLoudspeakerEnabled) {
                        Icons.AutoMirrored.Filled.VolumeUp
                    } else {
                        Icons.Filled.PhoneInTalk
                    },
                    isActive = isLoudspeakerEnabled,
                    accentColor = Color(0xFF38BDF8),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lock_screen_ptt_speaker_toggle"),
                    onClick = { LivePttSessionManager.toggleLoudspeaker(context) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LockScreenQuickControlButton(
                    title = if (isPttSilentMode) "PTT Silent ON" else "PTT Silent",
                    subtitle = if (isPttSilentMode) {
                        if (isBeepSilenced) "Alerts still come in" else "Alerts & beeps on"
                    } else {
                        "Mute PTT voice"
                    },
                    icon = if (isPttSilentMode) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.Filled.Hearing
                    },
                    isActive = isPttSilentMode,
                    accentColor = Color(0xFFF97316),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lock_screen_ptt_silent_toggle"),
                    onClick = { LivePttSessionManager.togglePttSilentMode(context) }
                )
                LockScreenQuickControlButton(
                    title = if (isBeepSilenced) "Beeps Silenced" else "Beep Sound ON",
                    subtitle = if (isBeepSilenced) "Tap to allow beeps" else "Tap to silence beep",
                    icon = if (isBeepSilenced) {
                        Icons.Filled.NotificationsOff
                    } else {
                        Icons.Filled.Notifications
                    },
                    isActive = isBeepSilenced,
                    accentColor = Color(0xFFA855F7),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lock_screen_ptt_silence_beep_toggle"),
                    onClick = { LivePttSessionManager.toggleBeepSilenced(context) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LockScreenQuickControlButton(
                    title = if (isPttVoiceToTextEnabled) "Voice-to-Text ON" else "Voice-to-Text",
                    subtitle = if (isPttVoiceToTextEnabled) {
                        "PTT also sends text"
                    } else {
                        "Send PTT as text too"
                    },
                    icon = Icons.Filled.Subtitles,
                    isActive = isPttVoiceToTextEnabled,
                    accentColor = Color(0xFF10B981),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lock_screen_vtt_toggle_button"),
                    onClick = { LivePttSessionManager.togglePttVoiceToText(context) }
                )
                LockScreenQuickControlButton(
                    title = if (isDndEffectivelyOn) "Do Not Disturb ON" else "Do Not Disturb",
                    subtitle = if (isDndEffectivelyOn) {
                        "Incoming PTT → Text"
                    } else {
                        "Incoming PTT as text"
                    },
                    icon = Icons.Filled.DoNotDisturbOn,
                    isActive = isDndEffectivelyOn,
                    accentColor = Color(0xFFE11D48),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lock_screen_dnd_toggle_button"),
                    onClick = { LivePttSessionManager.toggleLockScreenDnd(context) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = if (voiceReplyModeActive) {
                "Hold the button above — or hold Volume Down — to dictate your reply. Release to send as text without unlocking."
            } else {
                "Turn on Voice-to-Text so your PTT also posts as text, or enable Do Not Disturb so all incoming PTT arrives as voice-to-text."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun LockScreenQuickControlButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) accentColor.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.09f),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) accentColor else Color.White.copy(alpha = 0.16f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) accentColor.copy(alpha = 0.30f) else accentColor.copy(alpha = 0.16f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isActive) Color.White else accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LockScreenRecentDmCard(
    recentDm: LockScreenRecentDm?,
    isSelected: Boolean,
    isDictating: Boolean,
    isSendingReply: Boolean,
    liveTranscript: String,
    replyFeedbackMessage: String?,
    replyFeedbackIsError: Boolean,
    onToggleSelect: () -> Unit
) {
    val accentBlue = Color(0xFF38BDF8)
    val borderColor = when {
        isSelected -> accentBlue
        recentDm != null -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.10f)
    }
    val containerColor = when {
        isSelected -> Color(0xFF0EA5E9).copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (recentDm != null) {
                    Modifier.clickable(onClick = onToggleSelect)
                } else {
                    Modifier
                }
            )
            .testTag("lock_screen_recent_dm_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MarkChatUnread,
                        contentDescription = null,
                        tint = if (isSelected) accentBlue else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isSelected) "VOICE-TO-TEXT REPLY SELECTED" else "LAST RECENT DM",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) accentBlue else Color.White.copy(alpha = 0.78f),
                        letterSpacing = 0.8.sp
                    )
                }
                if (recentDm != null && recentDm.timestampMillis > 0L) {
                    Text(
                        text = formatLockScreenTime(recentDm.timestampMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.62f)
                    )
                }
            }

            if (recentDm == null) {
                Text(
                    text = "No recent DM messages yet. When someone messages you, it will appear here so you can reply without unlocking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.68f)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (recentDm.isGroup && recentDm.threadTitle.isNotBlank() && recentDm.threadTitle != recentDm.senderName) {
                            "${recentDm.senderName} • ${recentDm.threadTitle}"
                        } else {
                            recentDm.senderName
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = when {
                            recentDm.voxOfficial -> Color(0xFFF59E0B).copy(alpha = 0.24f)
                            isSelected -> accentBlue.copy(alpha = 0.25f)
                            else -> Color.White.copy(alpha = 0.12f)
                        }
                    ) {
                        Text(
                            text = when {
                                recentDm.voxOfficial -> "Official • Read-only"
                                isSelected -> "Selected ✓"
                                else -> "Tap to reply"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                recentDm.voxOfficial -> Color(0xFFFBBF24)
                                isSelected -> accentBlue
                                else -> Color.White.copy(alpha = 0.88f)
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = recentDm.messageText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                // Live voice-to-text transcript preview while speaking
                AnimatedVisibility(visible = isDictating || (isSendingReply && liveTranscript.isNotBlank())) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF6366F1).copy(alpha = 0.24f),
                        border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = null,
                                tint = Color(0xFFA5B4FC),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = liveTranscript.ifBlank { "Listening… speak your message now" },
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = if (liveTranscript.isBlank()) FontStyle.Italic else FontStyle.Normal,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Confirmation or error banner after voice-to-text reply
                AnimatedVisibility(visible = !replyFeedbackMessage.isNullOrBlank()) {
                    val feedbackColor = if (replyFeedbackIsError) Color(0xFFFCA5A5) else Color(0xFF6EE7B7)
                    val feedbackBg = if (replyFeedbackIsError) {
                        Color(0xFFEF4444).copy(alpha = 0.20f)
                    } else {
                        Color(0xFF10B981).copy(alpha = 0.20f)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = feedbackBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lock_screen_dm_reply_feedback")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = feedbackColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = replyFeedbackMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = feedbackColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatLockScreenTime(timestampMillis: Long): String {
    val diffMs = System.currentTimeMillis() - timestampMillis
    return when {
        diffMs < 60_000L -> "Just now"
        diffMs < 60 * 60_000L -> "${diffMs / 60_000L}m ago"
        else -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestampMillis))
    }
}

private fun parseHexOrFallback(hex: String, fallback: Color): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(hex))
    }.getOrElse { fallback }
}
