package com.example.media

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.DirectThread
import com.example.data.PttChirpStyle
import com.example.data.PttThemeStyle
import com.example.data.StaffTier
import com.example.notifications.DirectMessageNotificationPrefs
import com.example.widget.VoxPttWidgetProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * App-wide persistent Live Walkie-Talkie Push-to-Talk session manager.
 *
 * When BOTH users in a private chat have Push-to-Talk enabled (`pttEnabledUserIds`),
 * this manager keeps a real-time LiveKit audio channel (`dm_<threadId>`) open in
 * listening standby — even across screens or when the app is minimized to the background.
 *
 * Pressing and holding the Push-to-Talk button (in-app or via the floating background button)
 * immediately unmutes the live microphone stream directly to the other user without
 * creating recorded audio messages in the chat log.
 */
object LivePttSessionManager {

    private const val TAG = "LivePttSession"
    private const val WIDGET_PREFS = "vox_ptt_widget_session"
    private const val KEY_LAST_THREAD_ID = "last_thread_id"
    private const val KEY_LAST_PEER_NAME = "last_peer_name"
    private const val KEY_LAST_USER_ID = "last_user_id"
    private const val KEY_LAST_DISPLAY_NAME = "last_display_name"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var liveKitManager: LiveKitRoomManager? = null
    private var speakerCollectorJob: Job? = null
    private var connectedCollectorJob: Job? = null
    private var transmitLimitJob: Job? = null

    /** Thread ID currently in an active Private Voice/Video Call so PTT doesn't hijack the LiveKit identity. */
    private val _suppressedForCallThreadId = MutableStateFlow<String?>(null)
    val suppressedForCallThreadId: StateFlow<String?> = _suppressedForCallThreadId.asStateFlow()

    /** Optional hook notified when PTT transmission toggles (used by Group DM Admin priority talk-over). */
    @Volatile
    var onTransmittingStateChanged: ((threadId: String, transmitting: Boolean) -> Unit)? = null

    private val _activeThreadId = MutableStateFlow<String?>(null)
    val activeThreadId: StateFlow<String?> = _activeThreadId.asStateFlow()

    private val _activePeerName = MutableStateFlow("")
    val activePeerName: StateFlow<String> = _activePeerName.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    /** Max PTT clip duration: 5s for Free members, 60s for Pro members & Staff. */
    private val _maxPttSeconds = MutableStateFlow(5)
    val maxPttSeconds: StateFlow<Int> = _maxPttSeconds.asStateFlow()

    private val _transmittingElapsedSec = MutableStateFlow(0)
    val transmittingElapsedSec: StateFlow<Int> = _transmittingElapsedSec.asStateFlow()

    private val _limitReachedEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val limitReachedEvents: SharedFlow<String> = _limitReachedEvents.asSharedFlow()

    /** Emits threadId whenever the user completes a live PTT transmission clip. */
    private val _completedPttThreadEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val completedPttThreadEvents: SharedFlow<String> = _completedPttThreadEvents.asSharedFlow()

    private val _remoteSpeakingName = MutableStateFlow<String?>(null)
    val remoteSpeakingName: StateFlow<String?> = _remoteSpeakingName.asStateFlow()

    private val _isAppInBackground = MutableStateFlow(false)
    val isAppInBackground: StateFlow<Boolean> = _isAppInBackground.asStateFlow()

    private val _activePttTheme = MutableStateFlow(PttThemeStyle.CLASSIC)
    val activePttTheme: StateFlow<PttThemeStyle> = _activePttTheme.asStateFlow()

    private val _activePttChirp = MutableStateFlow(PttChirpStyle.STANDARD)
    val activePttChirp: StateFlow<PttChirpStyle> = _activePttChirp.asStateFlow()

    /** True = Loudspeaker (built-in speakerphone), False = Regular phone speaker (earpiece). */
    private val _isLoudspeakerEnabled = MutableStateFlow(true)
    val isLoudspeakerEnabled: StateFlow<Boolean> = _isLoudspeakerEnabled.asStateFlow()

    /** True = Incoming PTT voice audio is silenced; alerts and PTT beeps still come through. */
    private val _isPttSilentMode = MutableStateFlow(false)
    val isPttSilentMode: StateFlow<Boolean> = _isPttSilentMode.asStateFlow()

    /** True = Incoming PTT Beep noises are also silenced. */
    private val _isBeepSilenced = MutableStateFlow(false)
    val isBeepSilenced: StateFlow<Boolean> = _isBeepSilenced.asStateFlow()

    /** True = Floating PTT overlay / bubble is enabled (hides bottom Hold-to-Talk bar in DM). */
    private val _isFloatingPttEnabled = MutableStateFlow(true)
    val isFloatingPttEnabled: StateFlow<Boolean> = _isFloatingPttEnabled.asStateFlow()

    /** True = User enabled PTT Voice-to-Text so outgoing PTT also posts a voice-to-text message. */
    private val _isPttVoiceToTextEnabled = MutableStateFlow(false)
    val isPttVoiceToTextEnabled: StateFlow<Boolean> = _isPttVoiceToTextEnabled.asStateFlow()

    /** True = Lock-Screen Do Not Disturb is active: incoming PTT audio is muted & converted to voice-to-text. */
    private val _isLockScreenDndEnabled = MutableStateFlow(false)
    val isLockScreenDndEnabled: StateFlow<Boolean> = _isLockScreenDndEnabled.asStateFlow()

    /** True = Active DM thread requires auto voice-to-text (someone enabled VTT or DND in the thread). */
    private val _threadRequiresVoiceToText = MutableStateFlow(false)
    val threadRequiresVoiceToText: StateFlow<Boolean> = _threadRequiresVoiceToText.asStateFlow()

    /** True = Current user enabled Do Not Disturb inside the active Group DM thread. */
    private val _threadDndActiveForMe = MutableStateFlow(false)
    val threadDndActiveForMe: StateFlow<Boolean> = _threadDndActiveForMe.asStateFlow()

    /** True when every other participant in the active thread has Do Not Disturb turned on. */
    @Volatile
    private var allOtherRecipientsInDnd: Boolean = false

    /** Live partial transcript while speaking with PTT Voice-to-Text enabled. */
    private val _livePttTranscript = MutableStateFlow("")
    val livePttTranscript: StateFlow<String> = _livePttTranscript.asStateFlow()

    /** Latest confirmation or error feedback after a PTT Voice-to-Text message is posted. */
    private val _lastPttTranscriptFeedback = MutableStateFlow<String?>(null)
    val lastPttTranscriptFeedback: StateFlow<String?> = _lastPttTranscriptFeedback.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pttSpeechRecognizer: SpeechRecognizer? = null
    private var pttSpeechCapturing = false
    private var pttSpeechTargetThreadId: String = ""
    private var pttSpeechAppContext: Context? = null
    private var pttSpeechFallbackRunnable: Runnable? = null

    /** Tracks whether the current user is the Group DM Admin for the active PTT thread. */
    @Volatile
    private var groupAdminThreadId: String? = null

    /** Thread ID where the Group DM Admin is currently talking over non-admin members. */
    private val _blockedByGroupAdminThreadId = MutableStateFlow<String?>(null)

    private val _isBlockedByGroupAdminTalking = MutableStateFlow(false)
    val isBlockedByGroupAdminTalking: StateFlow<Boolean> = _isBlockedByGroupAdminTalking.asStateFlow()

    data class IncomingPttBeep(
        val threadId: String,
        val senderName: String,
        val channelName: String,
        val timestampMillis: Long
    )

    private val _lastIncomingBeep = MutableStateFlow<IncomingPttBeep?>(null)
    val lastIncomingBeep: StateFlow<IncomingPttBeep?> = _lastIncomingBeep.asStateFlow()

    fun setMaxDurationSeconds(seconds: Int) {
        _maxPttSeconds.value = seconds.coerceIn(5, 60)
    }

    fun setPttCustomization(theme: PttThemeStyle, chirp: PttChirpStyle) {
        _activePttTheme.value = theme
        _activePttChirp.value = chirp
    }

    private fun recomputeRemoteAudioMute() {
        val shouldMute = _isPttSilentMode.value || _isLockScreenDndEnabled.value || _threadDndActiveForMe.value
        liveKitManager?.setRemoteAudioMuted(shouldMute)
    }

    fun syncAudioPrefs(context: Context) {
        val appContext = context.applicationContext
        val speaker = DirectMessageNotificationPrefs.isPttLoudspeakerEnabled(appContext)
        val silent = DirectMessageNotificationPrefs.isPttSilentModeEnabled(appContext)
        val beepSilent = DirectMessageNotificationPrefs.isPttBeepSilenced(appContext)
        val floatingEnabled = DirectMessageNotificationPrefs.isFloatingPttEnabled(appContext)
        val vttEnabled = DirectMessageNotificationPrefs.isPttVoiceToTextEnabled(appContext)
        val lockDndEnabled = DirectMessageNotificationPrefs.isLockScreenDndEnabled(appContext)
        _isLoudspeakerEnabled.value = speaker
        _isPttSilentMode.value = silent
        _isBeepSilenced.value = beepSilent
        _isFloatingPttEnabled.value = floatingEnabled
        _isPttVoiceToTextEnabled.value = vttEnabled
        _isLockScreenDndEnabled.value = lockDndEnabled
        liveKitManager?.setAudioOutputLoudspeaker(speaker)
        recomputeRemoteAudioMute()
    }

    fun syncActiveThreadPttFlags(context: Context, thread: DirectThread, currentUserId: String) {
        val activeTid = _activeThreadId.value
        if (activeTid.isNullOrBlank() || thread.id != activeTid) return
        _threadRequiresVoiceToText.value = thread.shouldAutoTranscribePttFrom(currentUserId)
        _threadDndActiveForMe.value = thread.isPttDndEnabledFor(currentUserId)
        allOtherRecipientsInDnd = thread.areAllOtherParticipantsInDnd(currentUserId)
        recomputeRemoteAudioMute()
    }

    fun setPttVoiceToTextEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        _isPttVoiceToTextEnabled.value = enabled
        DirectMessageNotificationPrefs.setPttVoiceToTextEnabled(appContext, enabled)
        triggerPttHaptic(appContext)
        val tid = _activeThreadId.value
        if (!tid.isNullOrBlank()) {
            syncSelfThreadListFieldInFirestore(appContext, tid, "pttVoiceToTextUserIds", enabled)
        }
    }

    fun togglePttVoiceToText(context: Context) {
        setPttVoiceToTextEnabled(context, !_isPttVoiceToTextEnabled.value)
    }

    fun setLockScreenDndEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        _isLockScreenDndEnabled.value = enabled
        DirectMessageNotificationPrefs.setLockScreenDndEnabled(appContext, enabled)
        recomputeRemoteAudioMute()
        triggerPttHaptic(appContext)
        val tid = _activeThreadId.value
        if (!tid.isNullOrBlank()) {
            syncSelfThreadListFieldInFirestore(appContext, tid, "pttDndUserIds", enabled)
        }
    }

    fun toggleLockScreenDnd(context: Context) {
        setLockScreenDndEnabled(context, !_isLockScreenDndEnabled.value)
    }

    fun clearPttTranscriptFeedback() {
        _lastPttTranscriptFeedback.value = null
    }

    private fun syncSelfThreadListFieldInFirestore(
        appContext: Context,
        threadId: String,
        fieldName: String,
        enabled: Boolean
    ) {
        if (threadId.isBlank()) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val uid = Firebase.auth.currentUser?.uid
                    ?: cachedMyUserId.takeIf { it.isNotBlank() }
                    ?: return@runCatching
                val databaseId = appContext.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val ref = db.collection("directThreads").document(threadId)
                val snap = ref.get().await()
                val thread = snap.toObject(DirectThread::class.java) ?: return@runCatching
                if (thread.hasAnyBlock() || thread.isUserKicked(uid) || !thread.participantIds.contains(uid)) {
                    return@runCatching
                }
                val currentList = if (fieldName == "pttDndUserIds") {
                    thread.pttDndUserIds
                } else {
                    thread.pttVoiceToTextUserIds
                }
                val updatedList = if (enabled) {
                    if (currentList.contains(uid)) return@runCatching
                    currentList + uid
                } else {
                    if (!currentList.contains(uid)) return@runCatching
                    currentList.filter { it != uid }
                }
                ref.update(
                    mapOf(
                        fieldName to updatedList,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }.onFailure { err ->
                Log.w(TAG, "Failed syncing $fieldName=$enabled on $threadId", err)
            }
        }
    }

    fun cancelPttSpeechCapture() {
        mainHandler.post {
            pttSpeechFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
            pttSpeechFallbackRunnable = null
            pttSpeechCapturing = false
            pttSpeechTargetThreadId = ""
            _livePttTranscript.value = ""
            runCatching { pttSpeechRecognizer?.cancel() }
        }
    }

    private fun startPttSpeechCapture(appContext: Context, threadId: String) {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) return@post
            pttSpeechFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
            pttSpeechFallbackRunnable = null
            pttSpeechAppContext = appContext.applicationContext
            pttSpeechTargetThreadId = threadId
            _livePttTranscript.value = ""
            _lastPttTranscriptFeedback.value = null
            pttSpeechCapturing = true

            runCatching {
                val recognizer = ensurePttSpeechRecognizer(appContext.applicationContext)
                val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
                }
                recognizer.startListening(speechIntent)
            }.onFailure { err ->
                Log.w(TAG, "PTT SpeechRecognizer failed to start", err)
                pttSpeechCapturing = false
            }
        }
    }

    private fun stopPttSpeechCaptureAndSend(appContext: Context) {
        mainHandler.post {
            if (!pttSpeechCapturing) return@post
            pttSpeechAppContext = appContext.applicationContext
            runCatching { pttSpeechRecognizer?.stopListening() }
            pttSpeechFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
            val runnable = Runnable {
                if (pttSpeechCapturing) {
                    val targetTid = pttSpeechTargetThreadId
                    val captured = _livePttTranscript.value.trim()
                    pttSpeechCapturing = false
                    pttSpeechTargetThreadId = ""
                    _livePttTranscript.value = ""
                    if (targetTid.isNotBlank() && captured.isNotBlank()) {
                        sendTranscribedPttMessage(appContext.applicationContext, targetTid, captured)
                    }
                }
            }
            pttSpeechFallbackRunnable = runnable
            mainHandler.postDelayed(runnable, 2_000L)
        }
    }

    private fun ensurePttSpeechRecognizer(appContext: Context): SpeechRecognizer {
        pttSpeechRecognizer?.let { return it }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
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
                    _livePttTranscript.value = partial
                }
            }

            override fun onResults(results: Bundle?) {
                pttSpeechFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
                pttSpeechFallbackRunnable = null
                val targetTid = pttSpeechTargetThreadId
                val ctx = pttSpeechAppContext ?: appContext
                val finalText = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                    .ifBlank { _livePttTranscript.value.trim() }
                pttSpeechCapturing = false
                pttSpeechTargetThreadId = ""
                _livePttTranscript.value = ""
                if (targetTid.isNotBlank() && finalText.isNotBlank()) {
                    sendTranscribedPttMessage(ctx, targetTid, finalText)
                }
            }

            override fun onError(error: Int) {
                pttSpeechFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
                pttSpeechFallbackRunnable = null
                val targetTid = pttSpeechTargetThreadId
                val ctx = pttSpeechAppContext ?: appContext
                val partial = _livePttTranscript.value.trim()
                pttSpeechCapturing = false
                pttSpeechTargetThreadId = ""
                _livePttTranscript.value = ""
                if (targetTid.isNotBlank() && partial.isNotBlank()) {
                    sendTranscribedPttMessage(ctx, targetTid, partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        pttSpeechRecognizer = recognizer
        return recognizer
    }

    private fun sendTranscribedPttMessage(appContext: Context, threadId: String, spokenText: String) {
        val cleanText = spokenText.trim().take(480)
        if (cleanText.isBlank() || threadId.isBlank()) return
        val formattedText = "🎙️ $cleanText"

        scope.launch(Dispatchers.IO) {
            val outcome = runCatching {
                val uid = Firebase.auth.currentUser?.uid
                    ?: cachedMyUserId.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Not signed in")
                val databaseId = appContext.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val threadRef = db.collection("directThreads").document(threadId)
                val thread = threadRef.get().await().toObject(DirectThread::class.java)
                    ?: throw IllegalStateException("Thread unavailable")
                if (thread.voxOfficial || !thread.participantIds.contains(uid) ||
                    thread.isUserKicked(uid) || thread.hasAnyBlock()
                ) {
                    return@runCatching
                }
                val profileSnap = runCatching { db.collection("users").document(uid).get().await() }.getOrNull()
                if (profileSnap?.getBoolean("messagingDisabled") == true) return@runCatching
                val staffSnap = runCatching { db.collection("staffRoles").document(uid).get().await() }.getOrNull()
                val senderName = profileSnap?.getString("displayName")?.trim()?.takeIf { it.isNotBlank() }
                    ?: cachedMyDisplayName.takeIf { it.isNotBlank() && it != "Member" }
                    ?: Firebase.auth.currentUser?.displayName?.trim()?.takeIf { it.isNotBlank() }
                    ?: "Member"
                val senderTier = StaffTier.fromRaw(staffSnap?.getString("staffTier"))

                val msgRef = threadRef.collection("messages").document()
                val msgPayload = mapOf(
                    "threadId" to threadId,
                    "participantIds" to thread.participantIds,
                    "senderId" to uid,
                    "senderName" to senderName.take(40),
                    "senderTier" to senderTier.name,
                    "text" to formattedText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                val unreadRecipients = thread.participantIds.filter { it != uid }
                val batch = db.batch()
                batch.set(msgRef, msgPayload)
                batch.update(
                    threadRef,
                    mapOf(
                        "lastMessageText" to formattedText.take(300),
                        "lastMessageSenderId" to uid,
                        "unreadByUserIds" to unreadRecipients,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            }
            outcome.onSuccess {
                _lastPttTranscriptFeedback.value = "Voice-to-Text sent: \"$cleanText\""
            }.onFailure { err ->
                Log.w(TAG, "Failed posting PTT Voice-to-Text message", err)
            }
        }
    }

    fun setFloatingPttEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        _isFloatingPttEnabled.value = enabled
        DirectMessageNotificationPrefs.setFloatingPttEnabled(appContext, enabled)
        val tid = _activeThreadId.value
        val peer = _activePeerName.value
        if (!enabled) {
            PrivateCallForegroundService.hideFloatingPttOverlay(appContext)
        } else if (!tid.isNullOrBlank() && (_isConnected.value || _isConnecting.value)) {
            PrivateCallForegroundService.startPtt(
                context = appContext,
                threadId = tid,
                peerName = peer,
                isTransmitting = _isTransmitting.value,
                showFloatingOverlay = true
            )
        }
    }

    fun toggleFloatingPttEnabled(context: Context) {
        setFloatingPttEnabled(context, !_isFloatingPttEnabled.value)
    }

    fun setThreadGroupAdminState(
        threadId: String,
        isGroupAdmin: Boolean,
        blockedByAdmin: Boolean,
        context: Context
    ) {
        if (threadId.isBlank()) return
        if (isGroupAdmin) {
            groupAdminThreadId = threadId
        } else if (groupAdminThreadId == threadId) {
            groupAdminThreadId = null
        }

        if (blockedByAdmin && !isGroupAdmin) {
            _blockedByGroupAdminThreadId.value = threadId
            if (_activeThreadId.value == threadId) {
                _isBlockedByGroupAdminTalking.value = true
                if (_isTransmitting.value) {
                    setTransmitting(context.applicationContext, false, threadId = threadId)
                }
            }
        } else if (_blockedByGroupAdminThreadId.value == threadId) {
            _blockedByGroupAdminThreadId.value = null
            _isBlockedByGroupAdminTalking.value = false
        }
    }

    private fun updateGroupAdminTalkingInFirestore(appContext: Context, threadId: String, talking: Boolean) {
        if (threadId.isBlank() || groupAdminThreadId != threadId) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val databaseId = appContext.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                db.collection("directThreads").document(threadId)
                    .update(
                        mapOf(
                            "groupAdminTalking" to talking,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
            }.onFailure { err ->
                Log.w(TAG, "Failed to update groupAdminTalking=$talking for $threadId", err)
            }
        }
    }

    fun setLoudspeakerEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        _isLoudspeakerEnabled.value = enabled
        DirectMessageNotificationPrefs.setPttLoudspeakerEnabled(appContext, enabled)
        liveKitManager?.setAudioOutputLoudspeaker(enabled)
        triggerPttHaptic(appContext)
    }

    fun toggleLoudspeaker(context: Context) {
        setLoudspeakerEnabled(context, !_isLoudspeakerEnabled.value)
    }

    fun setPttSilentMode(context: Context, silent: Boolean) {
        val appContext = context.applicationContext
        _isPttSilentMode.value = silent
        DirectMessageNotificationPrefs.setPttSilentModeEnabled(appContext, silent)
        recomputeRemoteAudioMute()
        triggerPttHaptic(appContext)
    }

    fun togglePttSilentMode(context: Context) {
        setPttSilentMode(context, !_isPttSilentMode.value)
    }

    fun setBeepSilenced(context: Context, silenced: Boolean) {
        val appContext = context.applicationContext
        _isBeepSilenced.value = silenced
        DirectMessageNotificationPrefs.setPttBeepSilenced(appContext, silenced)
        triggerPttHaptic(appContext)
    }

    fun toggleBeepSilenced(context: Context) {
        setBeepSilenced(context, !_isBeepSilenced.value)
    }

    fun recordIncomingBeep(
        threadId: String,
        senderName: String,
        channelName: String,
        timestampMillis: Long
    ) {
        _lastIncomingBeep.value = IncomingPttBeep(
            threadId = threadId,
            senderName = senderName,
            channelName = channelName,
            timestampMillis = timestampMillis
        )
    }

    /**
     * Sends a real-time PTT "Beep" tone to the active PTT group or user via Firestore so
     * recipients hear the walkie-talkie beep immediately (even in PTT Silent Mode unless they silenced beeps).
     */
    fun sendPttBeep(
        context: Context,
        threadId: String? = _activeThreadId.value,
        onResult: ((Result<String>) -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val targetThreadId = threadId?.takeIf { it.isNotBlank() }
            ?: _activeThreadId.value?.takeIf { it.isNotBlank() }
            ?: prefs.getString(KEY_LAST_THREAD_ID, "").orEmpty()
        val targetPeerName = _activePeerName.value.ifBlank {
            prefs.getString(KEY_LAST_PEER_NAME, "").orEmpty().ifBlank { "PTT channel" }
        }
        if (targetThreadId.isBlank()) {
            onResult?.invoke(Result.failure(IllegalStateException("No active PTT channel to beep.")))
            return
        }

        triggerPttHaptic(appContext)
        if (!DirectMessageNotificationPrefs.isPttBeepSilenced(appContext)) {
            PttChirpSoundEngine.playPttAttentionBeep()
        }

        scope.launch(Dispatchers.IO) {
            val outcome = runCatching {
                val uid = Firebase.auth.currentUser?.uid
                    ?: cachedMyUserId.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Please sign in to VOX first.")
                val databaseId = appContext.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val senderName = cachedMyDisplayName.takeIf { it.isNotBlank() && it != "Member" }
                    ?: runCatching {
                        db.collection("users").document(uid).get().await()
                            .getString("displayName")?.trim()
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: Firebase.auth.currentUser?.displayName?.trim()?.takeIf { it.isNotBlank() }
                    ?: "Member"
                val nowMs = System.currentTimeMillis()
                db.collection("directThreads").document(targetThreadId)
                    .update(
                        mapOf(
                            "lastBeepAtMillis" to nowMs,
                            "lastBeepSenderId" to uid,
                            "lastBeepSenderName" to senderName.take(40),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
                "Beep sent to $targetPeerName!"
            }
            launch(Dispatchers.Main.immediate) {
                onResult?.invoke(outcome)
            }
        }
    }

    /**
     * Suppresses background Live PTT on [threadId] while a Private Voice/Video Call is active
     * so LiveKit never kicks the user's video call participant for duplicate identity.
     */
    fun suppressForPrivateCall(context: Context, threadId: String) {
        if (threadId.isBlank()) return
        _suppressedForCallThreadId.value = threadId
        if (_activeThreadId.value == threadId) {
            val appContext = context.applicationContext
            disconnectInternal(appContext, stopService = false)
        }
    }

    fun clearPrivateCallSuppression(threadId: String) {
        if (_suppressedForCallThreadId.value == threadId) {
            _suppressedForCallThreadId.value = null
        }
    }

    private fun saveLastSession(
        context: Context,
        threadId: String,
        peerName: String,
        myUserId: String,
        myDisplayName: String
    ) {
        if (threadId.isBlank() || myUserId.isBlank()) return
        runCatching {
            context.applicationContext
                .getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_THREAD_ID, threadId)
                .putString(KEY_LAST_PEER_NAME, peerName)
                .putString(KEY_LAST_USER_ID, myUserId)
                .putString(KEY_LAST_DISPLAY_NAME, myDisplayName)
                .apply()
        }
    }

    fun getSavedLastPeerName(context: Context): String = runCatching {
        context.applicationContext
            .getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_PEER_NAME, "")
            .orEmpty()
    }.getOrDefault("")

    /**
     * Called by the Home Screen PTT Widget on tap:
     * - If currently transmitting -> stops transmitting and dims widget.
     * - If idle -> starts transmitting (reconnecting the saved PTT channel in background if needed) and lights up widget.
     * Returns true if handled in background, or false if the user needs to open VOX first.
     */
    fun toggleFromWidget(context: Context): Boolean {
        val appContext = context.applicationContext
        val hasMic = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) return false

        if (_isTransmitting.value) {
            triggerPttHaptic(appContext)
            setTransmitting(appContext, false)
            return true
        }

        val activeTid = _activeThreadId.value
        val activePeer = _activePeerName.value
        if (!activeTid.isNullOrBlank()) {
            triggerPttHaptic(appContext)
            setTransmitting(appContext, true, threadId = activeTid, peerName = activePeer)
            return true
        }

        val prefs = appContext.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val savedTid = prefs.getString(KEY_LAST_THREAD_ID, "").orEmpty()
        val savedPeer = prefs.getString(KEY_LAST_PEER_NAME, "").orEmpty().ifBlank { "Private Chat" }
        val savedUid = prefs.getString(KEY_LAST_USER_ID, "").orEmpty()
        val savedName = prefs.getString(KEY_LAST_DISPLAY_NAME, "").orEmpty().ifBlank { "Member" }
        if (savedTid.isBlank() || savedUid.isBlank()) return false

        cachedMyUserId = savedUid
        cachedMyDisplayName = savedName
        triggerPttHaptic(appContext)
        setTransmitting(
            context = appContext,
            transmitting = true,
            threadId = savedTid,
            peerName = savedPeer,
            myUserId = savedUid,
            myDisplayName = savedName
        )
        return true
    }

    /**
     * Optional foreground hardware-key PTT target (e.g., an active Voice Room in Hold-to-Talk
     * mode, or a Private Call with mutual PTT enabled).
     */
    data class ForegroundPttTarget(
        val id: String,
        val canTransmit: () -> Boolean,
        val onSetTransmitting: (Boolean) -> Unit
    )

    @Volatile
    private var foregroundPttTarget: ForegroundPttTarget? = null

    @Volatile
    private var activeHardwareTargetId: String? = null

    private var cachedMyUserId: String = ""
    private var cachedMyDisplayName: String = "Member"

    fun registerForegroundPttTarget(target: ForegroundPttTarget) {
        foregroundPttTarget = target
    }

    fun clearForegroundPttTarget(targetId: String) {
        if (foregroundPttTarget?.id == targetId) {
            foregroundPttTarget = null
        }
        if (activeHardwareTargetId == targetId) {
            activeHardwareTargetId = null
        }
    }

    /**
     * Handles the hardware Volume Down key as a Push-to-Talk trigger while the app is open.
     *
     * Returns true if the event was consumed by an active PTT session (preventing volume change),
     * or false to let Android handle Volume Down normally.
     */
    fun handleHardwarePttKey(context: Context, pressed: Boolean): Boolean {
        val appContext = context.applicationContext
        if (pressed && !DirectMessageNotificationPrefs.isVolumeDownPttEnabled(appContext)) {
            return false
        }

        val target = foregroundPttTarget
        if (pressed) {
            if (target != null && target.canTransmit()) {
                activeHardwareTargetId = target.id
                target.onSetTransmitting(true)
                return true
            }
            if (!_activeThreadId.value.isNullOrBlank()) {
                val hasMic = ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasMic) return false
                activeHardwareTargetId = "live_dm_ptt"
                setTransmitting(appContext, true)
                return true
            }
            return false
        } else {
            val activeId = activeHardwareTargetId ?: return false
            activeHardwareTargetId = null
            if (activeId == "live_dm_ptt") {
                setTransmitting(appContext, false)
            } else if (target != null && target.id == activeId) {
                target.onSetTransmitting(false)
            } else {
                // Safety fallback in case the screen changed while the key was held
                setTransmitting(appContext, false)
            }
            return true
        }
    }

    fun triggerPttHaptic(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(24L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(24L)
                }
            }
        }
    }

    fun onAppForegrounded(context: Context) {
        _isAppInBackground.value = false
        val tid = _activeThreadId.value
        val peer = _activePeerName.value
        if (!tid.isNullOrBlank() && (_isConnected.value || _isConnecting.value)) {
            PrivateCallForegroundService.startPtt(
                context = context,
                threadId = tid,
                peerName = peer,
                isTransmitting = _isTransmitting.value,
                showFloatingOverlay = _isFloatingPttEnabled.value
            )
        }
    }

    fun onAppBackgrounded(context: Context) {
        _isAppInBackground.value = true
        val tid = _activeThreadId.value
        val peer = _activePeerName.value
        if (!tid.isNullOrBlank() && (_isConnected.value || _isConnecting.value)) {
            PrivateCallForegroundService.startPtt(
                context = context,
                threadId = tid,
                peerName = peer,
                isTransmitting = _isTransmitting.value,
                showFloatingOverlay = _isFloatingPttEnabled.value
            )
        }
    }

    /**
     * Ensures the live walkie-talkie audio channel is connected for [threadId].
     * Starts in silent listening standby (mic muted) until the user holds Push-to-Talk.
     */
    fun ensureConnected(
        context: Context,
        threadId: String,
        peerName: String,
        myUserId: String,
        myDisplayName: String
    ) {
        if (threadId.isBlank() || myUserId.isBlank()) return
        if (_suppressedForCallThreadId.value == threadId) {
            Log.d(TAG, "PTT connect suppressed for $threadId while private voice/video call is active")
            return
        }
        val appContext = context.applicationContext
        cachedMyUserId = myUserId
        cachedMyDisplayName = myDisplayName.ifBlank { "Member" }

        val cleanPeer = peerName.ifBlank { "Private Chat" }
        _activePeerName.value = cleanPeer
        saveLastSession(appContext, threadId, cleanPeer, myUserId, cachedMyDisplayName)

        if (_activeThreadId.value == threadId && (_isConnected.value || _isConnecting.value)) {
            PrivateCallForegroundService.startPtt(
                context = appContext,
                threadId = threadId,
                peerName = cleanPeer,
                isTransmitting = _isTransmitting.value,
                showFloatingOverlay = _isFloatingPttEnabled.value
            )
            VoxPttWidgetProvider.requestUpdate(appContext)
            return
        }

        val hasMicPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPermission) {
            Log.d(TAG, "Skipping auto-connect for PTT thread $threadId until RECORD_AUDIO is granted")
            return
        }

        scope.launch {
            connectInternal(appContext, threadId, cleanPeer, myUserId, cachedMyDisplayName)
        }
    }

    private suspend fun connectInternal(
        appContext: Context,
        threadId: String,
        peerName: String,
        myUserId: String,
        myDisplayName: String
    ): Boolean {
        // Disconnect any previous thread cleanly
        if (_activeThreadId.value != null && _activeThreadId.value != threadId) {
            disconnectInternal(appContext, stopService = false)
        }

        val existing = liveKitManager
        if (existing != null && _activeThreadId.value == threadId && existing.isConnected.value) {
            _isConnected.value = true
            _isConnecting.value = false
            return true
        }

        _activeThreadId.value = threadId
        _activePeerName.value = peerName
        _isConnecting.value = true

        syncAudioPrefs(appContext)
        val manager = LiveKitRoomManager(appContext, forceLoudspeaker = true)
        manager.setAudioOutputLoudspeaker(_isLoudspeakerEnabled.value)
        manager.setRemoteAudioMuted(_isPttSilentMode.value || _isLockScreenDndEnabled.value || _threadDndActiveForMe.value)
        liveKitManager = manager

        speakerCollectorJob?.cancel()
        speakerCollectorJob = scope.launch {
            manager.remoteActiveSpeakerName.collect { speaker ->
                _remoteSpeakingName.value = speaker
            }
        }
        connectedCollectorJob?.cancel()
        connectedCollectorJob = scope.launch {
            manager.isConnected.collect { connected ->
                _isConnected.value = connected
                if (!connected) {
                    _isTransmitting.value = false
                }
            }
        }

        val result = manager.connectToRoom(
            roomName = "dm_$threadId",
            participantIdentity = myUserId,
            participantName = myDisplayName
        )
        _isConnecting.value = false

        return result.fold(
            onSuccess = {
                _isConnected.value = true
                _isTransmitting.value = false
                manager.setAudioOutputLoudspeaker(_isLoudspeakerEnabled.value)
                recomputeRemoteAudioMute()
                manager.setMicEnabled(false) // Standby listen mode until PTT pressed
                if (_isLockScreenDndEnabled.value) {
                    syncSelfThreadListFieldInFirestore(appContext, threadId, "pttDndUserIds", true)
                }
                if (_isPttVoiceToTextEnabled.value) {
                    syncSelfThreadListFieldInFirestore(appContext, threadId, "pttVoiceToTextUserIds", true)
                }
                PrivateCallForegroundService.startPtt(
                    context = appContext,
                    threadId = threadId,
                    peerName = peerName,
                    isTransmitting = false,
                    showFloatingOverlay = _isFloatingPttEnabled.value
                )
                VoxPttWidgetProvider.requestUpdate(appContext)
                true
            },
            onFailure = { err ->
                Log.w(TAG, "Live PTT LiveKit connection failed for $threadId", err)
                _isConnected.value = false
                _isTransmitting.value = false
                VoxPttWidgetProvider.requestUpdate(appContext)
                false
            }
        )
    }

    /**
     * Streams live audio when [transmitting] is true, and immediately mutes when false.
     * Never writes an audio message into the chat history.
     */
    fun setTransmitting(
        context: Context,
        transmitting: Boolean,
        threadId: String? = _activeThreadId.value,
        peerName: String? = _activePeerName.value,
        myUserId: String = cachedMyUserId,
        myDisplayName: String = cachedMyDisplayName
    ) {
        val appContext = context.applicationContext
        val hasMicPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (transmitting && !hasMicPermission) {
            Log.w(TAG, "Cannot transmit live PTT audio without RECORD_AUDIO permission")
            return
        }

        scope.launch {
            val targetThreadId = threadId ?: _activeThreadId.value ?: return@launch
            val targetPeer = (peerName ?: _activePeerName.value).ifBlank { "Private Chat" }
            val uid = myUserId.ifBlank { cachedMyUserId }
            val name = myDisplayName.ifBlank { cachedMyDisplayName }

            if (transmitting) {
                // Non-admin members cannot transmit while the Group DM Admin holds priority mic
                val isGroupAdminForTarget = groupAdminThreadId == targetThreadId
                if (!isGroupAdminForTarget && _blockedByGroupAdminThreadId.value == targetThreadId) {
                    _limitReachedEvents.tryEmit("Group DM Admin has priority mic — please wait until they finish speaking.")
                    return@launch
                }
                val ready = if (liveKitManager?.isConnected?.value == true && _activeThreadId.value == targetThreadId) {
                    true
                } else if (uid.isNotBlank()) {
                    connectInternal(appContext, targetThreadId, targetPeer, uid, name)
                } else {
                    false
                }
                if (!ready) return@launch
                if (!_isTransmitting.value) {
                    PttChirpSoundEngine.playKeyDown(_activePttChirp.value)
                }
                _isTransmitting.value = true
                _transmittingElapsedSec.value = 0
                onTransmittingStateChanged?.invoke(targetThreadId, true)
                if (isGroupAdminForTarget) {
                    updateGroupAdminTalkingInFirestore(appContext, targetThreadId, true)
                }
                val shouldTranscribe = _isPttVoiceToTextEnabled.value || _threadRequiresVoiceToText.value
                if (shouldTranscribe && allOtherRecipientsInDnd) {
                    // All recipients have Do Not Disturb active: dedicate mic to Voice-to-Text
                    liveKitManager?.setMicEnabled(false)
                    startPttSpeechCapture(appContext, targetThreadId)
                } else {
                    liveKitManager?.setMicEnabled(true)
                    if (shouldTranscribe) {
                        startPttSpeechCapture(appContext, targetThreadId)
                    }
                }
                PrivateCallForegroundService.startPtt(
                    context = appContext,
                    threadId = targetThreadId,
                    peerName = targetPeer,
                    isTransmitting = true,
                    showFloatingOverlay = _isFloatingPttEnabled.value
                )
                VoxPttWidgetProvider.requestUpdate(appContext)
                transmitLimitJob?.cancel()
                val limitSec = if (isGroupAdminForTarget) 60 else _maxPttSeconds.value.coerceIn(5, 60)
                transmitLimitJob = scope.launch {
                    for (elapsed in 1..limitSec) {
                        delay(1_000L)
                        if (!isActive || !_isTransmitting.value) return@launch
                        _transmittingElapsedSec.value = elapsed
                    }
                    if (isActive && _isTransmitting.value) {
                        _isTransmitting.value = false
                        _transmittingElapsedSec.value = 0
                        onTransmittingStateChanged?.invoke(targetThreadId, false)
                        if (isGroupAdminForTarget) {
                            updateGroupAdminTalkingInFirestore(appContext, targetThreadId, false)
                        }
                        liveKitManager?.setMicEnabled(false)
                        if (pttSpeechCapturing) {
                            stopPttSpeechCaptureAndSend(appContext)
                        }
                        PttChirpSoundEngine.playKeyUp(_activePttChirp.value)
                        if (_isConnected.value) {
                            PrivateCallForegroundService.startPtt(
                                context = appContext,
                                threadId = targetThreadId,
                                peerName = targetPeer,
                                isTransmitting = false,
                                showFloatingOverlay = _isFloatingPttEnabled.value
                            )
                        }
                        VoxPttWidgetProvider.requestUpdate(appContext)
                        val msg = if (limitSec <= 5) {
                            "Free PTT 5-second limit reached. Upgrade to Pro (\$9.99/mo) for 1-minute PTT clips!"
                        } else {
                            "PTT 1-minute clip limit reached. Release and press again to continue."
                        }
                        _completedPttThreadEvents.tryEmit(targetThreadId)
                        _limitReachedEvents.tryEmit(msg)
                    }
                }
            } else {
                val wasTx = _isTransmitting.value
                transmitLimitJob?.cancel()
                transmitLimitJob = null
                _isTransmitting.value = false
                _transmittingElapsedSec.value = 0
                if (wasTx) {
                    onTransmittingStateChanged?.invoke(targetThreadId, false)
                    if (groupAdminThreadId == targetThreadId) {
                        updateGroupAdminTalkingInFirestore(appContext, targetThreadId, false)
                    }
                }
                liveKitManager?.setMicEnabled(false)
                if (pttSpeechCapturing) {
                    stopPttSpeechCaptureAndSend(appContext)
                }
                if (wasTx) {
                    PttChirpSoundEngine.playKeyUp(_activePttChirp.value)
                    _completedPttThreadEvents.tryEmit(targetThreadId)
                }
                if (_isConnected.value) {
                    PrivateCallForegroundService.startPtt(
                        context = appContext,
                        threadId = targetThreadId,
                        peerName = targetPeer,
                        isTransmitting = false,
                        showFloatingOverlay = _isFloatingPttEnabled.value
                    )
                }
                VoxPttWidgetProvider.requestUpdate(appContext)
            }
        }
    }

    fun toggleTransmitting(context: Context) {
        setTransmitting(context, !_isTransmitting.value)
    }

    fun disconnect(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            disconnectInternal(appContext, stopService = true)
        }
    }

    private fun disconnectInternal(appContext: Context, stopService: Boolean) {
        transmitLimitJob?.cancel()
        transmitLimitJob = null
        speakerCollectorJob?.cancel()
        speakerCollectorJob = null
        connectedCollectorJob?.cancel()
        connectedCollectorJob = null
        val manager = liveKitManager
        liveKitManager = null
        runCatching { manager?.disconnect() }
        val wasTx = _isTransmitting.value
        val prevThread = _activeThreadId.value
        _isTransmitting.value = false
        _transmittingElapsedSec.value = 0
        if (wasTx && !prevThread.isNullOrBlank()) {
            onTransmittingStateChanged?.invoke(prevThread, false)
            if (groupAdminThreadId == prevThread) {
                updateGroupAdminTalkingInFirestore(appContext, prevThread, false)
            }
        }
        cancelPttSpeechCapture()
        _isConnected.value = false
        _isConnecting.value = false
        _isBlockedByGroupAdminTalking.value = false
        _threadRequiresVoiceToText.value = false
        _threadDndActiveForMe.value = false
        allOtherRecipientsInDnd = false
        _remoteSpeakingName.value = null
        _activeThreadId.value = null
        _activePeerName.value = ""
        if (stopService && PrivateCallForegroundService.isPttMode) {
            PrivateCallForegroundService.stop(appContext)
        } else if (stopService && PrivateCallForegroundService.activeThreadId == prevThread) {
            PrivateCallForegroundService.stop(appContext)
        }
        VoxPttWidgetProvider.requestUpdate(appContext)
    }
}
