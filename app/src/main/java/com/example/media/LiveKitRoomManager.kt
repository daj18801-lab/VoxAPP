package com.example.media

import android.content.Context
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.RemoteVideoTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LiveKitRoomManager(private val appContext: Context) {

    val room: Room = LiveKit.create(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var eventJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<LocalVideoTrack?>(null)
    val localVideoTrack: StateFlow<LocalVideoTrack?> = _localVideoTrack.asStateFlow()

        private var eventJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<LocalVideoTrack?>(null)
    val localVideoTrack: StateFlow<LocalVideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTracks = MutableStateFlow>(emptyMap())
    val remoteVideoTracks: StateFlow> = _remoteVideoTracks.asStateFlow()


    suspend fun connectToRoom(
        roomName: String,
        participantIdentity: String,
        participantName: String
    ): Result<Unit> = runCatching {
        if (_isConnected.value) return@runCatching
        val token = fetchTokenFromBackend(roomName, participantIdentity, participantName)
        startObservingRoomEvents()
        room.connect(LIVEKIT_URL, token)
        _isConnected.value = true
    }

    private fun startObservingRoomEvents() {
        eventJob?.cancel()
        eventJob = scope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.TrackSubscribed -> {
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
                    is RoomEvent.Disconnected -> {
                        _isConnected.value = false
                        _localVideoTrack.value = null
                        _remoteVideoTracks.value = emptyMap()
                    }
                    else -> Unit
                }
            }
        }
    }

    suspend fun setMicEnabled(enabled: Boolean) {
        runCatching {
            room.localParticipant.setMicrophoneEnabled(enabled)
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
        _isConnected.value = false
        _localVideoTrack.value = null
        _remoteVideoTracks.value = emptyMap()
    }

    private suspend fun fetchTokenFromBackend(
        roomName: String,
        identity: String,
        name: String
    ): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val url = URL(TOKEN_ENDPOINT_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        val payload = JSONObject().apply {
            put("roomName", roomName)
            put("participantIdentity", identity)
            put("participantName", name)
        }.toString()
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val errBody = conn.errorStream?.bufferedReader()?.readText().orEmpty()
            throw IllegalStateException("Token server returned HTTP $code: $errBody")
        }
        val body = conn.inputStream.bufferedReader().readText()
        JSONObject(body).getString("token")
    }

    companion object {
        private const val TAG = "LiveKitRoomManager"
        private const val LIVEKIT_URL = "wss://vox-r29t0v8w.livekit.cloud"
        private const val TOKEN_ENDPOINT_URL = "https://us-central1-gen-lang-client-0444374128.cloudfunctions.net/createLiveKitToken"
    }
}
