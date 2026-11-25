package com.example.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.PttChirpStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lightweight PCM synthesizer for Walkie-Talkie / Push-to-Talk radio chirps and roger beeps.
 * Generates crisp, zero-asset audio tones on [Dispatchers.IO] without blocking the main thread.
 */
object PttChirpSoundEngine {
    private const val SAMPLE_RATE = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class ToneSegment(
        val startFreqHz: Double,
        val endFreqHz: Double = startFreqHz,
        val durationMs: Int,
        val volume: Double = 0.32,
        val noiseMix: Double = 0.0
    )

    fun playKeyDown(chirp: PttChirpStyle) {
        val segments = when (chirp) {
            PttChirpStyle.STANDARD -> listOf(
                ToneSegment(startFreqHz = 880.0, durationMs = 45, volume = 0.22)
            )
            PttChirpStyle.ROGER_BEEP -> listOf(
                ToneSegment(startFreqHz = 1046.5, durationMs = 55, volume = 0.30),
                ToneSegment(startFreqHz = 0.0, durationMs = 20, volume = 0.0),
                ToneSegment(startFreqHz = 1318.5, durationMs = 65, volume = 0.32)
            )
            PttChirpStyle.TACTICAL_SQUAWK -> listOf(
                ToneSegment(startFreqHz = 620.0, endFreqHz = 960.0, durationMs = 40, volume = 0.26, noiseMix = 0.45),
                ToneSegment(startFreqHz = 1480.0, durationMs = 55, volume = 0.30, noiseMix = 0.12)
            )
            PttChirpStyle.RETRO_POLICE -> listOf(
                ToneSegment(startFreqHz = 1200.0, durationMs = 42, volume = 0.30),
                ToneSegment(startFreqHz = 1600.0, durationMs = 42, volume = 0.30),
                ToneSegment(startFreqHz = 2000.0, durationMs = 55, volume = 0.32)
            )
            PttChirpStyle.SCIFI_PULSE -> listOf(
                ToneSegment(startFreqHz = 520.0, endFreqHz = 1760.0, durationMs = 110, volume = 0.30),
                ToneSegment(startFreqHz = 1760.0, endFreqHz = 1320.0, durationMs = 55, volume = 0.24)
            )
        }
        playSegmentsAsync(segments)
    }

    fun playKeyUp(chirp: PttChirpStyle) {
        val segments = when (chirp) {
            PttChirpStyle.STANDARD -> listOf(
                ToneSegment(startFreqHz = 660.0, durationMs = 45, volume = 0.20)
            )
            PttChirpStyle.ROGER_BEEP -> listOf(
                ToneSegment(startFreqHz = 1318.5, durationMs = 55, volume = 0.30),
                ToneSegment(startFreqHz = 0.0, durationMs = 20, volume = 0.0),
                ToneSegment(startFreqHz = 987.8, durationMs = 80, volume = 0.30)
            )
            PttChirpStyle.TACTICAL_SQUAWK -> listOf(
                ToneSegment(startFreqHz = 1100.0, endFreqHz = 580.0, durationMs = 60, volume = 0.26, noiseMix = 0.35),
                ToneSegment(startFreqHz = 420.0, durationMs = 45, volume = 0.20, noiseMix = 0.55)
            )
            PttChirpStyle.RETRO_POLICE -> listOf(
                ToneSegment(startFreqHz = 1800.0, durationMs = 45, volume = 0.28),
                ToneSegment(startFreqHz = 0.0, durationMs = 18, volume = 0.0),
                ToneSegment(startFreqHz = 1200.0, durationMs = 70, volume = 0.30)
            )
            PttChirpStyle.SCIFI_PULSE -> listOf(
                ToneSegment(startFreqHz = 1560.0, endFreqHz = 460.0, durationMs = 125, volume = 0.28)
            )
        }
        playSegmentsAsync(segments)
    }

    fun playPreview(chirp: PttChirpStyle) {
        val keyDown = when (chirp) {
            PttChirpStyle.STANDARD -> listOf(
                ToneSegment(startFreqHz = 880.0, durationMs = 55, volume = 0.26),
                ToneSegment(startFreqHz = 0.0, durationMs = 90, volume = 0.0),
                ToneSegment(startFreqHz = 660.0, durationMs = 55, volume = 0.24)
            )
            PttChirpStyle.ROGER_BEEP -> listOf(
                ToneSegment(startFreqHz = 1046.5, durationMs = 55, volume = 0.32),
                ToneSegment(startFreqHz = 0.0, durationMs = 22, volume = 0.0),
                ToneSegment(startFreqHz = 1318.5, durationMs = 65, volume = 0.34),
                ToneSegment(startFreqHz = 0.0, durationMs = 110, volume = 0.0),
                ToneSegment(startFreqHz = 1318.5, durationMs = 55, volume = 0.32),
                ToneSegment(startFreqHz = 0.0, durationMs = 22, volume = 0.0),
                ToneSegment(startFreqHz = 987.8, durationMs = 85, volume = 0.32)
            )
            PttChirpStyle.TACTICAL_SQUAWK -> listOf(
                ToneSegment(startFreqHz = 620.0, endFreqHz = 960.0, durationMs = 45, volume = 0.28, noiseMix = 0.45),
                ToneSegment(startFreqHz = 1480.0, durationMs = 55, volume = 0.32, noiseMix = 0.12),
                ToneSegment(startFreqHz = 0.0, durationMs = 100, volume = 0.0),
                ToneSegment(startFreqHz = 1100.0, endFreqHz = 580.0, durationMs = 60, volume = 0.28, noiseMix = 0.35),
                ToneSegment(startFreqHz = 420.0, durationMs = 50, volume = 0.22, noiseMix = 0.55)
            )
            PttChirpStyle.RETRO_POLICE -> listOf(
                ToneSegment(startFreqHz = 1200.0, durationMs = 45, volume = 0.32),
                ToneSegment(startFreqHz = 1600.0, durationMs = 45, volume = 0.32),
                ToneSegment(startFreqHz = 2000.0, durationMs = 60, volume = 0.34),
                ToneSegment(startFreqHz = 0.0, durationMs = 100, volume = 0.0),
                ToneSegment(startFreqHz = 1800.0, durationMs = 45, volume = 0.30),
                ToneSegment(startFreqHz = 0.0, durationMs = 20, volume = 0.0),
                ToneSegment(startFreqHz = 1200.0, durationMs = 75, volume = 0.32)
            )
            PttChirpStyle.SCIFI_PULSE -> listOf(
                ToneSegment(startFreqHz = 520.0, endFreqHz = 1760.0, durationMs = 115, volume = 0.32),
                ToneSegment(startFreqHz = 1760.0, endFreqHz = 1320.0, durationMs = 55, volume = 0.26),
                ToneSegment(startFreqHz = 0.0, durationMs = 95, volume = 0.0),
                ToneSegment(startFreqHz = 1560.0, endFreqHz = 460.0, durationMs = 130, volume = 0.30)
            )
        }
        playSegmentsAsync(keyDown)
    }

    /**
     * Distinct two-tone emergency/priority radio break-in chirp.
     * Standard (1 pt): crisp alternating two-tone alert; Urgent (2 pts): double-burst two-tone break-in.
     */
    fun playPriorityBreakInChirp(urgent: Boolean = false) {
        val burst = listOf(
            ToneSegment(startFreqHz = 1320.0, endFreqHz = 1320.0, durationMs = 85, volume = 0.42),
            ToneSegment(startFreqHz = 988.0, endFreqHz = 988.0, durationMs = 85, volume = 0.42),
            ToneSegment(startFreqHz = 0.0, endFreqHz = 0.0, durationMs = 35, volume = 0.0),
            ToneSegment(startFreqHz = 1480.0, endFreqHz = 1760.0, durationMs = 95, volume = 0.44, noiseMix = 0.05)
        )
        val segments = if (urgent) {
            burst + listOf(ToneSegment(startFreqHz = 0.0, endFreqHz = 0.0, durationMs = 80, volume = 0.0)) + burst
        } else {
            burst
        }
        playSegmentsAsync(segments)
    }

    private fun playSegmentsAsync(segments: List<ToneSegment>) {
        scope.launch {
            runCatching {
                val totalSamples = segments.sumOf { (SAMPLE_RATE * it.durationMs) / 1000 }
                if (totalSamples <= 0) return@runCatching
                val pcm = ShortArray(totalSamples)
                var offset = 0
                var phase = 0.0
                val rng = Random(System.nanoTime())

                for (seg in segments) {
                    val count = (SAMPLE_RATE * seg.durationMs) / 1000
                    val fadeSamples = minOf(80, count / 4).coerceAtLeast(1)
                    for (i in 0 until count) {
                        val idx = offset + i
                        if (idx >= pcm.size) break
                        if (seg.volume <= 0.0 || (seg.startFreqHz <= 0.0 && seg.noiseMix <= 0.0)) {
                            pcm[idx] = 0
                            continue
                        }
                        val progress = i.toDouble() / count.coerceAtLeast(1)
                        val freq = seg.startFreqHz + (seg.endFreqHz - seg.startFreqHz) * progress
                        phase += 2.0 * PI * freq / SAMPLE_RATE
                        val sineSample = sin(phase)
                        val noiseSample = (rng.nextDouble() * 2.0 - 1.0)
                        val mixed = sineSample * (1.0 - seg.noiseMix) + noiseSample * seg.noiseMix

                        val env = when {
                            i < fadeSamples -> i.toDouble() / fadeSamples
                            i > count - fadeSamples -> (count - i).toDouble() / fadeSamples
                            else -> 1.0
                        }.coerceIn(0.0, 1.0)

                        val sampleVal = (mixed * seg.volume * env * Short.MAX_VALUE)
                            .toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        pcm[idx] = sampleVal.toShort()
                    }
                    offset += count
                }

                val bufferSizeBytes = pcm.size * 2
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(bufferSizeBytes)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                val sleepMs = (totalSamples * 1000L) / SAMPLE_RATE + 60L
                Thread.sleep(sleepMs)
                track.stop()
                track.release()
            }
        }
    }
}
