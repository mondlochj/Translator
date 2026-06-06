package com.arosys.meetingassistant.testing.fakes

import com.arosys.meetingassistant.core.interfaces.AudioOutputRoute
import com.arosys.meetingassistant.core.interfaces.TTSProvider

/**
 * Recording [TTSProvider] for unit tests.
 *
 * Captures every [speak] call in [spokenUtterances] without producing
 * any audio, so tests can assert what was spoken and in what order.
 */
class FakeTTSProvider : TTSProvider {

    override var isReady: Boolean = true
    override var isSpeaking: Boolean = false

    data class Utterance(val text: String, val route: AudioOutputRoute)

    /** All utterances passed to [speak], in order. */
    val spokenUtterances = mutableListOf<Utterance>()

    val warmUpCount get() = _warmUpCount
    private var _warmUpCount = 0

    override suspend fun speak(text: String, outputRoute: AudioOutputRoute) {
        isSpeaking = true
        spokenUtterances += Utterance(text, outputRoute)
        isSpeaking = false
    }

    override fun stopSpeaking() {
        isSpeaking = false
    }

    override suspend fun warmUp() {
        _warmUpCount++
    }

    override fun close() {}
}
