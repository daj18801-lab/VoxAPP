package com.example.media

import android.content.Context
import android.util.Log
import io.livekit.android.LiveKit
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

    private val _remoteVideoTracks = MutableStateFlow(emptyMap<String, RemoteVideoTrack>())
    val remoteVideoTracks = _remoteVideoTracks.asStateFlow()

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
