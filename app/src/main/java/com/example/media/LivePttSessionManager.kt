package com.example.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _remoteSpeakingName = MutableStateFlow<String?>(null)
    val remoteSpeakingName: StateFlow<String?> = _remoteSpeakingName.asStateFlow()

    private val _isAppInBackground = MutableStateFlow(false)
    val isAppInBackground: StateFlow<Boolean> = _isAppInBackground.asStateFlow()

    private var cachedMyUserId: String = ""
    private var cachedMyDisplayName: String = "Member"

    fun onAppForegrounded(context: Context) {
        _isAppInBackground.value = false
        PrivateCallForegroundService.hideFloatingPttOverlay(context)
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
                showFloatingOverlay = _isAppInBackground.value
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

        val manager = LiveKitRoomManager(appContext)
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
                    showFloatingOverlay = _isAppInBackground.value
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
                _isTransmitting.value = true
                liveKitManager?.setMicEnabled(true)
                PrivateCallForegroundService.startPtt(
                    context = appContext,
                    threadId = targetThreadId,
                    peerName = targetPeer,
                    isTransmitting = true,
                    showFloatingOverlay = _isAppInBackground.value
                )
            } else {
                _isTransmitting.value = false
                liveKitManager?.setMicEnabled(false)
                if (_isConnected.value) {
                    PrivateCallForegroundService.startPtt(
                        context = appContext,
                        threadId = targetThreadId,
                        peerName = targetPeer,
                        isTransmitting = false,
                        showFloatingOverlay = _isAppInBackground.value
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
        speakerCollectorJob?.cancel()
        speakerCollectorJob = null
        connectedCollectorJob?.cancel()
        connectedCollectorJob = null
        val manager = liveKitManager
        liveKitManager = null
        runCatching { manager?.disconnect() }
        _isTransmitting.value = false
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
