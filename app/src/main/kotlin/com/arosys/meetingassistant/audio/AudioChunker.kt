package com.arosys.meetingassistant.audio

import android.util.Log
import com.arosys.meetingassistant.core.interfaces.AudioSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val TAG = "AudioChunker"

/**
 * Segments a continuous audio stream into speech chunks for Whisper inference.
 *
 * Algorithm (energy-based VAD):
 *  - Track RMS energy in 10 ms analysis frames.
 *  - A frame is "speech" when energy > [speechThreshold].
 *  - Emit a chunk when:
 *      (a) a silence gap > [silencePadMs] ms follows a speech segment, OR
 *      (b) the accumulated speech exceeds [maxChunkMs] ms.
 *
 * The emitted [FloatArray] includes [preSpeechPadMs] ms of audio before the
 * speech onset and [silencePadMs] ms after the speech end, matching common
 * Whisper streaming patterns.
 */
class AudioChunker(
    private val sampleRate: Int = 16_000,
    /** RMS energy threshold below which a frame is considered silence. */
    private val speechThreshold: Float = 0.01f,
    /** Silence gap that triggers a chunk emit (ms). */
    private val silencePadMs: Int = 400,
    /** Maximum chunk duration before forced emit (ms). */
    private val maxChunkMs: Int = 15_000,
    /** Audio buffered before speech onset to capture leading consonants (ms). */
    private val preSpeechPadMs: Int = 200,
) {
    private val analysisFrameSamples = sampleRate / 100   // 10 ms
    private val silencePadFrames = silencePadMs / 10
    private val maxChunkSamples = sampleRate * maxChunkMs / 1_000
    private val preSpeechSamples = sampleRate * preSpeechPadMs / 1_000

    /**
     * Reads from [source] and emits speech [AudioChunk]s.
     * Terminates when [source.readFrames] returns ≤ 0.
     */
    fun chunkStream(source: AudioSource): Flow<AudioChunk> = flow {
        val readBuf = FloatArray(analysisFrameSamples)
        val ringBuf = FloatArray(preSpeechSamples + maxChunkSamples + analysisFrameSamples)
        var ringWrite = 0

        // Circular pre-speech buffer
        val preBuf = FloatArray(preSpeechSamples)
        var preBufHead = 0

        var speechBuffer = mutableListOf<Float>()
        var inSpeech = false
        var silenceFrameCount = 0
        var startTimestampMs = 0L
        var wallMs = 0L

        fun rms(buf: FloatArray, len: Int): Float {
            var sum = 0.0
            for (i in 0 until len) sum += buf[i] * buf[i]
            return kotlin.math.sqrt(sum / len).toFloat()
        }

        while (true) {
            val samplesRead = source.readFrames(readBuf)
            if (samplesRead <= 0) {
                // Flush any remaining speech
                if (speechBuffer.size > analysisFrameSamples) {
                    emit(AudioChunk(speechBuffer.toFloatArray(), startTimestampMs))
                }
                break
            }

            val energy = rms(readBuf, samplesRead)
            wallMs += samplesRead * 1_000L / sampleRate

            // Update rolling pre-speech buffer
            for (i in 0 until samplesRead) {
                preBuf[preBufHead % preSpeechSamples] = readBuf[i]
                preBufHead++
            }

            if (energy > speechThreshold) {
                if (!inSpeech) {
                    // Speech onset — prepend pre-speech buffer
                    inSpeech = true
                    silenceFrameCount = 0
                    startTimestampMs = wallMs - preSpeechPadMs
                    val preCount = minOf(preBufHead, preSpeechSamples)
                    val startIdx = (preBufHead - preCount + preSpeechSamples) % preSpeechSamples
                    for (i in 0 until preCount) {
                        speechBuffer.add(preBuf[(startIdx + i) % preSpeechSamples])
                    }
                }
                silenceFrameCount = 0
                for (i in 0 until samplesRead) speechBuffer.add(readBuf[i])

                // Hard cap — emit immediately
                if (speechBuffer.size >= maxChunkSamples) {
                    Log.d(TAG, "Max chunk reached: ${speechBuffer.size} samples")
                    emit(AudioChunk(speechBuffer.toFloatArray(), startTimestampMs))
                    speechBuffer = mutableListOf()
                    inSpeech = false
                }
            } else if (inSpeech) {
                silenceFrameCount++
                for (i in 0 until samplesRead) speechBuffer.add(readBuf[i])

                if (silenceFrameCount >= silencePadFrames) {
                    Log.d(TAG, "Silence boundary: emitting ${speechBuffer.size} samples")
                    emit(AudioChunk(speechBuffer.toFloatArray(), startTimestampMs))
                    speechBuffer = mutableListOf()
                    inSpeech = false
                    silenceFrameCount = 0
                }
            }
        }
    }
}

data class AudioChunk(
    val samples: FloatArray,
    val startTimestampMs: Long,
) {
    val durationMs: Long get() = samples.size * 1_000L / 16_000
}
