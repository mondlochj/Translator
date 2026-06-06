package com.arosys.meetingassistant.testing.fakes

import com.arosys.meetingassistant.core.interfaces.AudioSource
import com.arosys.meetingassistant.core.interfaces.SpeechRecognizer
import com.arosys.meetingassistant.core.interfaces.TranscriptSegment
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Controllable [SpeechRecognizer] for unit tests.
 *
 * Emit segments from the test:
 * ```
 * fake.emit("Necesitamos revisar el cronograma.", isFinal = true)
 * ```
 * Close the stream when done:
 * ```
 * fake.close()
 * ```
 */
class FakeSpeechRecognizer(
    override val modelName: String = "fake-whisper",
) : SpeechRecognizer {

    private val channel = Channel<TranscriptSegment>(capacity = Channel.UNLIMITED)

    override var isReady: Boolean = true

    val warmUpCount get() = _warmUpCount
    private var _warmUpCount = 0

    val transcribeCallCount get() = _transcribeCallCount
    private var _transcribeCallCount = 0

    override fun transcribeStream(audioSource: AudioSource): Flow<TranscriptSegment> {
        _transcribeCallCount++
        return channel.receiveAsFlow()
    }

    override suspend fun warmUp() {
        _warmUpCount++
    }

    /** Push a segment to any active collector. */
    fun emit(
        text: String,
        isFinal: Boolean = true,
        startMs: Long = 0L,
        endMs: Long = 500L,
        language: String = "es",
        confidence: Float = 0.95f,
    ) {
        channel.trySend(
            TranscriptSegment(text, isFinal, startMs, endMs, language, confidence)
        )
    }

    /** Signal end of stream. */
    override fun close() = channel.close()
}
