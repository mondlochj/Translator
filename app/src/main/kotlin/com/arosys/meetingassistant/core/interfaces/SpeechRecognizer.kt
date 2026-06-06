package com.arosys.meetingassistant.core.interfaces

import kotlinx.coroutines.flow.Flow

/**
 * Streaming speech-to-text.  Implementations are swappable (Whisper ONNX,
 * whisper.cpp JNI, Android SpeechRecognizer) without changing call sites.
 */
interface SpeechRecognizer : AutoCloseable {

    /** Models that this recognizer can report. */
    val modelName: String

    /** True once the model is loaded and ready to accept audio. */
    val isReady: Boolean

    /**
     * Emits partial and final [TranscriptSegment]s as audio is processed.
     * Cancelling the collection coroutine stops recognition cleanly.
     */
    fun transcribeStream(audioSource: AudioSource): Flow<TranscriptSegment>

    /** Explicitly warm up the model (loads weights, runs a dummy inference). */
    suspend fun warmUp()
}

data class TranscriptSegment(
    val text: String,
    val isFinal: Boolean,
    val startMs: Long,
    val endMs: Long,
    val language: String = "es",
    val confidence: Float = 1f,
)

/** Where audio comes from — lets us swap mic, file, or test source. */
interface AudioSource {
    /** Sample rate in Hz. */
    val sampleRateHz: Int
    /** Mono or stereo channel count. */
    val channelCount: Int
    /** Produces raw PCM frames (float32 in [-1, 1]). */
    fun readFrames(buffer: FloatArray): Int
}
