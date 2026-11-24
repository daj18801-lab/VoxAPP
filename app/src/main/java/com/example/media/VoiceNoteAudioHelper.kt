package com.example.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class RecordedVoiceNote(
    val audioBase64: String,
    val durationSec: Long
)

/**
 * Lightweight AAC/M4A voice note recorder for Private Messages.
 * Targets compact mono 16kHz / 24kbps (~3 KB/sec) so voice notes up to 30 seconds
 * stay well under Firestore document size limits without requiring external file storage.
 */
class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startMillis: Long = 0L
    private var activeMaxDurationSeconds: Int = FREE_MAX_DURATION_SECONDS

    val isRecording: Boolean
        get() = recorder != null

    fun startRecording(maxDurationSeconds: Int = FREE_MAX_DURATION_SECONDS): Boolean {
        cancel()
        val boundedMax = maxDurationSeconds.coerceIn(1, PRO_MAX_DURATION_SECONDS)
        return try {
            val file = File(context.cacheDir, "vn_rec_${System.currentTimeMillis()}.m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(16_000)
                setMaxDuration(boundedMax * 1000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
            activeMaxDurationSeconds = boundedMax
            startMillis = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start voice note recording", e)
            cancel()
            false
        }
    }

    suspend fun stopAndEncode(): RecordedVoiceNote? = withContext(Dispatchers.IO) {
        val activeRecorder = recorder ?: return@withContext null
        val file = outputFile
        val maxSec = activeMaxDurationSeconds.toLong()
        val elapsedSec = (((System.currentTimeMillis() - startMillis) + 500L) / 1000L)
            .coerceIn(1L, maxSec)
        recorder = null
        outputFile = null
        startMillis = 0L

        try {
            activeRecorder.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Voice note stop error (recording may be too short)", e)
        } finally {
            runCatching { activeRecorder.release() }
        }

        if (file == null || !file.exists() || file.length() <= 0L) {
            file?.delete()
            return@withContext null
        }

        return@withContext try {
            val bytes = file.readBytes()
            if (bytes.size > MAX_RAW_BYTES) {
                Log.w(TAG, "Voice note too large: ${bytes.size} bytes")
                null
            } else {
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                if (base64.length > MAX_BASE64_CHARS) null
                else RecordedVoiceNote(audioBase64 = base64, durationSec = elapsedSec)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to encode voice note", e)
            null
        } finally {
            runCatching { file.delete() }
        }
    }

    fun cancel() {
        val activeRecorder = recorder
        recorder = null
        startMillis = 0L
        if (activeRecorder != null) {
            runCatching { activeRecorder.stop() }
            runCatching { activeRecorder.release() }
        }
        outputFile?.let { runCatching { it.delete() } }
        outputFile = null
    }

    fun release() = cancel()

    companion object {
        private const val TAG = "VoiceNoteRecorder"
        const val FREE_MAX_DURATION_SECONDS = 5
        const val PRO_MAX_DURATION_SECONDS = 60
        const val MAX_DURATION_SECONDS = PRO_MAX_DURATION_SECONDS
        private const val MAX_RAW_BYTES = 260_000
        private const val MAX_BASE64_CHARS = 350_000
    }
}

/**
 * Plays Base64-encoded M4A voice notes in Private Messages with live playback progress.
 */
class VoiceNotePlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null
    private var progressJob: Job? = null

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    private val _progressFraction = MutableStateFlow(0f)
    val progressFraction: StateFlow<Float> = _progressFraction.asStateFlow()

    fun togglePlay(
        messageId: String,
        audioBase64: String,
        scope: CoroutineScope,
        onError: (String) -> Unit = {}
    ) {
        if (_playingMessageId.value == messageId) {
            stop()
            return
        }
        stop()
        if (audioBase64.isBlank()) return

        scope.launch {
            val decodedFile = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
                    val file = File(context.cacheDir, "vn_play_${messageId.take(24)}_${System.currentTimeMillis()}.m4a")
                    file.writeBytes(bytes)
                    file
                }.getOrNull()
            }

            if (decodedFile == null || !decodedFile.exists()) {
                onError("Could not decode voice note audio.")
                return@launch
            }

            try {
                tempFile = decodedFile
                val player = MediaPlayer().apply {
                    setDataSource(decodedFile.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        stop()
                    }
                    setOnErrorListener { _, _, _ ->
                        stop()
                        true
                    }
                    start()
                }
                mediaPlayer = player
                _playingMessageId.value = messageId
                _progressFraction.value = 0f

                progressJob = scope.launch {
                    while (isActive && mediaPlayer === player) {
                        val running = runCatching { player.isPlaying }.getOrDefault(false)
                        if (!running) break
                        val dur = runCatching { player.duration }.getOrDefault(0).coerceAtLeast(1)
                        val pos = runCatching { player.currentPosition }.getOrDefault(0).coerceIn(0, dur)
                        _progressFraction.value = pos.toFloat() / dur.toFloat()
                        delay(120L)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Voice note playback failed", e)
                stop()
                onError("Could not play voice note.")
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
            }
            runCatching { player.release() }
        }
        mediaPlayer = null
        tempFile?.let { runCatching { it.delete() } }
        tempFile = null
        _playingMessageId.value = null
        _progressFraction.value = 0f
    }

    fun release() = stop()

    companion object {
        private const val TAG = "VoiceNotePlayer"
    }
}

fun formatVoiceDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val mins = safe / 60L
    val secs = safe % 60L
    return "$mins:${secs.toString().padStart(2, '0')}"
}
