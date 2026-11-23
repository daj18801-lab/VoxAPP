package com.example.media

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.AudioOptions
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.audio.AudioSwitchHandler
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.RemoteVideoTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LiveKitRoomManager(
    private val appContext: Context,
    private val forceLoudspeaker: Boolean = false
) {

    private val audioHandler = AudioSwitchHandler(appContext).apply {
        preferredDeviceList = if (forceLoudspeaker) {
            listOf(
                AudioDevice.Speakerphone::class.java,
                AudioDevice.WiredHeadset::class.java,
                AudioDevice.BluetoothHeadset::class.java,
                AudioDevice.Earpiece::class.java
            )
        } else {
            listOf(
                AudioDevice.BluetoothHeadset::class.java,
                AudioDevice.WiredHeadset::class.java,
                AudioDevice.Speakerphone::class.java,
                AudioDevice.Earpiece::class.java
            )
        }
    }

    val room: Room = LiveKit.create(
        appContext = appContext,
        overrides = LiveKitOverrides(
            audioOptions = AudioOptions(
                audioHandler = audioHandler
            )
        )
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var eventJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<LocalVideoTrack?>(null)
    val localVideoTrack: StateFlow<LocalVideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTracks = MutableStateFlow(emptyMap<String, RemoteVideoTrack>())
    val remoteVideoTracks = _remoteVideoTracks.asStateFlow()

    private val _remoteActiveSpeakerName = MutableStateFlow<String?>(null)
    val remoteActiveSpeakerName: StateFlow<String?> = _remoteActiveSpeakerName.asStateFlow()

    suspend fun connectToRoom(
        roomName: String,
        participantIdentity: String,
        participantName: String
    ): Result<Unit> = runCatching {
        if (_isConnected.value) {
            if (forceLoudspeaker) enforceLoudspeakerRoute()
            return@runCatching
        }
        val (serverUrl, token) = fetchConnectionDetails(roomName, participantIdentity, participantName)
        startObservingRoomEvents()
        room.connect(serverUrl, token, ConnectOptions(autoSubscribe = true))
        _isConnected.value = true
        if (forceLoudspeaker) {
            enforceLoudspeakerRoute()
            // Re-apply shortly after WebRTC audio engine finishes starting its communication mode
            scope.launch {
                delay(350L)
                if (_isConnected.value) enforceLoudspeakerRoute()
            }
        }
    }

    /**
     * Routes LiveKit/WebRTC audio output to the phone's built-in loudspeaker instead of the earpiece.
     * Essential for walkie-talkie Push-to-Talk sessions.
     */
    fun enforceLoudspeakerRoute() {
        runCatching {
            val speakerDevice = audioHandler.availableAudioDevices.firstOrNull {
                it is AudioDevice.Speakerphone
            }
            if (speakerDevice != null) {
                audioHandler.selectDevice(speakerDevice)
            }
        }.onFailure {
            Log.w(TAG, "AudioSwitchHandler speaker selection failed: ${it.message}")
        }

        runCatching {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return@runCatching
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val builtinSpeaker = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (builtinSpeaker != null) {
                    audioManager.setCommunicationDevice(builtinSpeaker)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
            }
        }.onFailure {
            Log.w(TAG, "AudioManager loudspeaker route failed: ${it.message}")
        }
    }

    private fun releaseLoudspeakerRoute() {
        runCatching {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return@runCatching
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        }.onFailure {
            Log.w(TAG, "AudioManager loudspeaker release failed: ${it.message}")
        }
    }

    private fun startObservingRoomEvents() {
        eventJob?.cancel()
        eventJob = scope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.TrackSubscribed -> {
                        if (forceLoudspeaker) {
                            enforceLoudspeakerRoute()
                        }
                        val videoTrack = event.track as? RemoteVideoTrack
                        val identity = event.participant.identity?.value
                        if (videoTrack != null && identity != null) {
                            _remoteVideoTracks.update { it + (identity to videoTrack) }
                        }
                    }
                    is RoomEvent.TrackUnsubscribed -> {
                        val identity = event.participant.identity?.value
                        if (event.track is RemoteVideoTrack && identity != null) {
                            _remoteVideoTracks.update { it - identity }
                        }
                    }
                    is RoomEvent.ParticipantDisconnected -> {
                        val identity = event.participant.identity?.value
                        if (identity != null) {
                            _remoteVideoTracks.update { it - identity }
                        }
                    }
                    is RoomEvent.ActiveSpeakersChanged -> {
                        val localId = room.localParticipant.identity?.value
                        val remoteSpeaker = event.speakers.firstOrNull { speaker ->
                            speaker.identity?.value != localId
                        }
                        _remoteActiveSpeakerName.value = remoteSpeaker?.let { sp ->
                            sp.name?.takeIf { it.isNotBlank() } ?: sp.identity?.value ?: "Partner"
                        }
                    }
                    is RoomEvent.Disconnected -> {
                        if (forceLoudspeaker) releaseLoudspeakerRoute()
                        _isConnected.value = false
                        _localVideoTrack.value = null
                        _remoteVideoTracks.value = emptyMap()
                        _remoteActiveSpeakerName.value = null
                    }
                    else -> Unit
                }
            }
        }
    }

    suspend fun setMicEnabled(enabled: Boolean) {
        runCatching {
            room.localParticipant.setMicrophoneEnabled(enabled)
            if (forceLoudspeaker) {
                enforceLoudspeakerRoute()
            }
        }.onFailure {
            Log.w(TAG, "Failed to toggle mic: ${it.message}")
        }
    }

    suspend fun setCameraEnabled(enabled: Boolean) {
        runCatching {
            room.localParticipant.setCameraEnabled(enabled)
            val pub = room.localParticipant.getTrackPublication(Track.Source.CAMERA)
            _localVideoTrack.value = if (enabled) pub?.track as? LocalVideoTrack else null
        }.onFailure {
            Log.w(TAG, "Failed to toggle camera: ${it.message}")
        }
    }

    fun disconnect() {
        eventJob?.cancel()
        eventJob = null
        runCatching { room.disconnect() }
        if (forceLoudspeaker) releaseLoudspeakerRoute()
        _isConnected.value = false
        _localVideoTrack.value = null
        _remoteVideoTracks.value = emptyMap()
        _remoteActiveSpeakerName.value = null
    }

    private suspend fun fetchConnectionDetails(
        roomName: String,
        identity: String,
        name: String
    ): Pair<String, String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val url = URL(TOKEN_ENDPOINT_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Sandbox-ID", SANDBOX_ID)
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        val payload = JSONObject().apply {
            put("roomName", roomName)
            put("participantName", name)
            put("participantIdentity", identity)
        }.toString()
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val errBody = conn.errorStream?.bufferedReader()?.readText().orEmpty()
            throw IllegalStateException("Token server returned HTTP $code: $errBody")
        }
        val body = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(body)
        val serverUrl = json.optString("serverUrl").ifBlank { LIVEKIT_URL }
        val token = if (json.has("participantToken")) {
            json.getString("participantToken")
        } else {
            json.getString("token")
        }
        serverUrl to token
    }

    companion object {
        private const val TAG = "LiveKitRoomManager"
        private const val LIVEKIT_URL = "wss://vox-r29t0v8w.livekit.cloud"
        private const val SANDBOX_ID = "vox-2qnude"
        private const val TOKEN_ENDPOINT_URL = "https://cloud-api.livekit.io/api/sandbox/connection-details"
    }
}
