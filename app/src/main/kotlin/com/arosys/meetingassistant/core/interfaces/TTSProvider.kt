package com.arosys.meetingassistant.core.interfaces

/**
 * Text-to-speech synthesis.  Piper is the primary implementation;
 * Android TTS is the fallback.
 */
interface TTSProvider : AutoCloseable {

    val isReady: Boolean

    /** Synthesize [text] and route audio to [outputRoute]. */
    suspend fun speak(text: String, outputRoute: AudioOutputRoute = AudioOutputRoute.BLUETOOTH)

    /** Stop any in-progress speech immediately. */
    fun stopSpeaking()

    /** True if synthesis or playback is currently active. */
    val isSpeaking: Boolean

    suspend fun warmUp()
}

enum class AudioOutputRoute {
    /** Route to connected Bluetooth SCO device (earbud). */
    BLUETOOTH,
    /** Route to phone speaker. */
    SPEAKER,
    /** Route to wired headset if present, Bluetooth otherwise. */
    HEADSET_OR_BLUETOOTH,
}
