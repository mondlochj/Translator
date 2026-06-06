package com.arosys.meetingassistant.impl.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.arosys.meetingassistant.core.interfaces.AudioOutputRoute
import com.arosys.meetingassistant.core.interfaces.TTSProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

private const val TAG = "AndroidNativeTTS"

/**
 * TTS via Android's built-in [TextToSpeech] engine.
 *
 * Audio attributes are set to [AudioAttributes.USAGE_VOICE_COMMUNICATION] so
 * that, once [BluetoothAudioRouter] has established an SCO connection, the
 * system routes audio through the SCO path to the earbud — even with the
 * screen off.
 */
class AndroidNativeTTSProvider(private val context: Context) : TTSProvider {

    private var tts: TextToSpeech? = null

    override var isReady: Boolean = false
        private set

    override var isSpeaking: Boolean = false
        private set

    override suspend fun warmUp() {
        if (isReady) return
        suspendCancellableCoroutine { cont ->
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.apply {
                        language = Locale.US
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                    }
                    isReady = true
                    Log.i(TAG, "Android TTS ready")
                } else {
                    Log.e(TAG, "Android TTS init failed with status $status")
                }
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation { /* TTS init is not cancellable */ }
        }
    }

    override suspend fun speak(text: String, outputRoute: AudioOutputRoute) {
        if (!isReady || text.isBlank()) return
        val utteranceId = UUID.randomUUID().toString()
        isSpeaking = true
        suspendCancellableCoroutine<Unit> { cont ->
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) {
                    isSpeaking = false
                    if (cont.isActive) cont.resume(Unit)
                }
                @Deprecated("Deprecated in API 21")
                override fun onError(id: String?) {
                    isSpeaking = false
                    if (cont.isActive) cont.resume(Unit)
                }
            })
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
            cont.invokeOnCancellation { tts?.stop() }
        }
    }

    override fun stopSpeaking() {
        tts?.stop()
        isSpeaking = false
    }

    override fun close() {
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
