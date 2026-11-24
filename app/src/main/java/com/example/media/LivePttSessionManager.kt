package com.example.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.PttChirpStyle
import com.example.data.PttThemeStyle
import com.example.notifications.DirectMessageNotificationPrefs
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var liveKitManager: LiveKitRoomManager? = null
    private var speakerCollectorJob: Job? = null
    private var connectedCollectorJob: Job? = null
    private var transmitLimitJob: Job? = null

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

    fun setMaxDurationSeconds(seconds: Int) {
        _maxPttSeconds.value = seconds.coerceIn(5, 60)
    }

    fun setPttCustomization(theme: PttThemeStyle, chirp: PttChirpStyle) {
        _activePttTheme.value = theme
        _activePttChirp.value = chirp
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
                showFloatingOverlay = true
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
                showFloatingOverlay = true
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
        val appContext = context.applicationContext
        cachedMyUserId = myUserId
        cachedMyDisplayName = myDisplayName.ifBlank { "Member" }

        val cleanPeer = peerName.ifBlank { "Private Chat" }
        _activePeerName.value = cleanPeer

        if (_activeThreadId.value == threadId && (_isConnected.value || _isConnecting.value)) {
            PrivateCallForegroundService.startPtt(
                context = appContext,
                threadId = threadId,
                peerName = cleanPeer,
                isTransmitting = _isTransmitting.value,
                showFloatingOverlay = true
            )
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

        val manager = LiveKitRoomManager(appContext, forceLoudspeaker = true)
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
                manager.setMicEnabled(false) // Standby listen mode until PTT pressed
                PrivateCallForegroundService.startPtt(
                    context = appContext,
                    threadId = threadId,
                    peerName = peerName,
                    isTransmitting = false,
                    showFloatingOverlay = true
                )
                true
            },
            onFailure = { err ->
                Log.w(TAG, "Live PTT LiveKit connection failed for $threadId", err)
                _isConnected.value = false
                _isTransmitting.value = false
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
                liveKitManager?.setMicEnabled(true)
                PrivateCallForegroundService.startPtt(
                    context = appContext,
                    threadId = targetThreadId,
                    peerName = targetPeer,
                    isTransmitting = true,
                    showFloatingOverlay = true
                )
                transmitLimitJob?.cancel()
                val limitSec = _maxPttSeconds.value.coerceIn(5, 60)
                transmitLimitJob = scope.launch {
                    for (elapsed in 1..limitSec) {
                        delay(1_000L)
                        if (!isActive || !_isTransmitting.value) return@launch
                        _transmittingElapsedSec.value = elapsed
                    }
                    if (isActive && _isTransmitting.value) {
                        _isTransmitting.value = false
                        _transmittingElapsedSec.value = 0
                        liveKitManager?.setMicEnabled(false)
                        PttChirpSoundEngine.playKeyUp(_activePttChirp.value)
                        if (_isConnected.value) {
                            PrivateCallForegroundService.startPtt(
                                context = appContext,
                                threadId = targetThreadId,
                                peerName = targetPeer,
                                isTransmitting = false,
                                showFloatingOverlay = true
                            )
                        }
                        val msg = if (limitSec <= 5) {
                            "Free PTT 5-second limit reached. Upgrade to Pro (\$9.99/mo) for 1-minute PTT clips!"
                        } else {
                            "Pro PTT 1-minute clip limit reached. Release and press again to continue."
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
                liveKitManager?.setMicEnabled(false)
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
                        showFloatingOverlay = true
                    )
                }
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
        _isTransmitting.value = false
        _transmittingElapsedSec.value = 0
        _isConnected.value = false
        _isConnecting.value = false
        _remoteSpeakingName.value = null
        val prevThread = _activeThreadId.value
        _activeThreadId.value = null
        _activePeerName.value = ""
        if (stopService && PrivateCallForegroundService.isPttMode) {
            PrivateCallForegroundService.stop(appContext)
        } else if (stopService && PrivateCallForegroundService.activeThreadId == prevThread) {
            PrivateCallForegroundService.stop(appContext)
        }
    }
}
