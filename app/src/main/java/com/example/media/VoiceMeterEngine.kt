package com.example.media

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import kotlin.math.sqrt

/**
 * Real-time microphone audio level analyzer backed by Android's AudioRecord API.
 * Computes normalized RMS speech amplitude (0f..1f) for live stage voice meters
 * and Firestore speaking-level telemetry without storing raw audio recordings.
 */
class VoiceMeterEngine {
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var captureJob: Job? = null

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (captureJob?.isActive == true) return
        captureJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16_000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
                Log.w(TAG, "AudioRecord unsupported buffer size: $minBuffer")
                return@launch
            }

            val bufferSize = (minBuffer * 2).coerceAtLeast(2048)
            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord failed to initialize")
                    return@launch
                }
                recorder.startRecording()
                val pcmBuffer = ShortArray(bufferSize / 2)

                while (isActive) {
                    val readCount = recorder.read(pcmBuffer, 0, pcmBuffer.size)
                    if (readCount > 0) {
                        var sumSquares = 0.0
                        for (i in 0 until readCount) {
                            val sample = pcmBuffer[i].toDouble() / Short.MAX_VALUE.toDouble()
                            sumSquares += sample * sample
                        }
                        val rms = sqrt(sumSquares / readCount).toFloat()
                        // Scale voice RMS gently into 0..1 visual meter range
                        val normalized = (rms * 5.2f).coerceIn(0f, 1f)
                        _amplitude.value = (_amplitude.value * 0.35f + normalized * 0.65f)
                    } else {
                        delay(40)
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "RECORD_AUDIO permission revoked during capture", e)
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord capture error: ${e.message}")
            } finally {
                _amplitude.value = 0f
                try {
                    if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        recorder.stop()
                    }
                } catch (_: Exception) {
                }
                try {
                    recorder?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        _amplitude.value = 0f
    }

    private companion object {
        const val TAG = "VoiceMeterEngine"
    }
}
