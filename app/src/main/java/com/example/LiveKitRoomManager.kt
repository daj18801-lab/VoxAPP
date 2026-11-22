package com.example.media

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LiveKitRoomManager(context: Context) {
    companion object {
        const val LIVEKIT_URL = "wss://vox-87701qju.livekit.cloud"
        const val TOKEN_SERVER_ID = "vox-2qnude"
        const val TOKEN_SERVER_URL = "https://vox-2qnude.sandbox.livekit.io/api/connection-details"
    }

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val room: Room = LiveKit.create(context.applicationContext)

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _remoteVideoTracks = MutableStateFlow>(emptyMap())
    val remoteVideoTracks: StateFlow> = _remoteVideoTracks.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    init {
        managerScope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.TrackSubscribed -> {
                        val videoTrack = event.track as? VideoTrack
                        val identity = event.participant.identity?.value
                        if (videoTrack != null && identity != null) {
                            _remoteVideoTracks.value = _remoteVideoTracks.value + (identity to videoTrack)
                        }
                    }
                    is RoomEvent.TrackUnsubscribed -> {
                        val identity = event.participant.identity?.value
                        if (event.track is VideoTrack && identity != null) {
                            _remoteVideoTracks.value = _remoteVideoTracks.value - identity
                        }
                    }
                    is RoomEvent.LocalTrackPublished -> {
                        val videoTrack = event.publication.track as? VideoTrack
                        if (videoTrack != null) {
                            _localVideoTrack.value = videoTrack
                        }
                    }
                    is RoomEvent.LocalTrackUnpublished -> {
                        if (event.publication.track is VideoTrack) {
                            _localVideoTrack.value = null
                        }
                    }
                    is RoomEvent.Disconnected -> {
                        _remoteVideoTracks.value = emptyMap()
                        _localVideoTrack.value = null
                        _connectionStatus.value = "Disconnected"
                    }
                    else -> Unit
                }
            }
        }
    }

    suspend fun connectToRoom(
        roomName: String,
        participantIdentity: String,
        participantName: String
    ): Result<Unit> = runCatching {
        _connectionStatus.value = "Connecting..."
        val token = fetchDevToken(roomName, participantIdentity, participantName)
        room.connect(url = LIVEKIT_URL, token = token)
        _connectionStatus.value = "Connected"
    }.onFailure { err ->
        _connectionStatus.value = "Error: ${err.message}"
    }

    suspend fun setMicEnabled(enabled: Boolean) {
        runCatching { room.localParticipant.setMicrophoneEnabled(enabled) }
    }

    suspend fun setCameraEnabled(enabled: Boolean) {
        runCatching { room.localParticipant.setCameraEnabled(enabled) }
    }

    fun disconnect() {
        runCatching { room.disconnect() }
        _remoteVideoTracks.value = emptyMap()
        _localVideoTrack.value = null
        _connectionStatus.value = "Disconnected"
    }

    private suspend fun fetchDevToken(
        roomName: String,
        identity: String,
        name: String
    ): String = withContext(Dispatchers.IO) {
        val conn = (URL(TOKEN_SERVER_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Sandbox-ID", TOKEN_SERVER_ID)
            doOutput = true
        }
        val body = JSONObject().apply {
            put("room_name", roomName)
            put("participant_identity", identity)
            put("participant_name", name)
        }.toString()
        conn.outputStream.use { it.write(body.toByteArray()) }

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        JSONObject(responseText).getString("participantToken")
    }
}
