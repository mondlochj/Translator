package com.arosys.meetingassistant.impl.piper

import android.content.Context
import android.util.Log
import com.arosys.meetingassistant.core.interfaces.AudioOutputRoute
import com.arosys.meetingassistant.core.interfaces.TTSProvider
import com.arosys.meetingassistant.impl.tts.AndroidNativeTTSProvider

private const val TAG = "PiperTTSProvider"

// Voice model files expected in assets/ — generate with scripts/download_piper_voice.py
private const val PIPER_MODEL_ASSET  = "piper_en_us_lessac_medium.onnx"
private const val PIPER_CONFIG_ASSET = "piper_en_us_lessac_medium.onnx.json"

/**
 * High-quality offline TTS using Piper (https://github.com/rhasspy/piper).
 *
 * Falls back to [AndroidNativeTTSProvider] when:
 *   1. The Piper voice model is not present in assets/.
 *   2. The Piper JNI library is not yet integrated (current state — see TODO below).
 *
 * Prepare the voice model with:
 *   python3 scripts/download_piper_voice.py
 *
 * Full Piper JNI integration steps (future):
 *   1. Download pre-built `libpiper_android.so` from the Piper releases page.
 *   2. Place it in app/src/main/jniLibs/arm64-v8a/.
 *   3. Add a Kotlin/JNI bridge class PiperJni that calls piper_synthesize().
 *   4. Remove the `piperAvailable = false` override in warmUp() below.
 */
class PiperTTSProvider(private val context: Context) : TTSProvider {

    private val fallback = AndroidNativeTTSProvider(context)
    private var piperAvailable = false

    override val isReady: Boolean
        get() = if (piperAvailable) _piperReady else fallback.isReady
    private var _piperReady = false

    override val isSpeaking: Boolean
        get() = if (piperAvailable) _isSpeakingPiper else fallback.isSpeaking
    private var _isSpeakingPiper = false

    override suspend fun warmUp() {
        val hasModel  = assetExists(PIPER_MODEL_ASSET)
        val hasConfig = assetExists(PIPER_CONFIG_ASSET)

        if (hasModel && hasConfig) {
            Log.i(TAG, "Piper voice model found — JNI integration pending, using Android TTS fallback")
            // TODO: when libpiper_android.so is bundled, remove the line below and
            //       initialise PiperJni here with the model/config bytes.
            piperAvailable = false
        } else {
            Log.i(TAG, "Piper model not in assets/ — using Android TTS. Run scripts/download_piper_voice.py")
        }

        fallback.warmUp()
    }

    override suspend fun speak(text: String, outputRoute: AudioOutputRoute) {
        if (piperAvailable) {
            // TODO: PiperJni.synthesize(text) → PCM → AudioTrack(STREAM_VOICE_CALL)
            Log.d(TAG, "Piper synthesize (stub): $text")
        } else {
            fallback.speak(text, outputRoute)
        }
    }

    override fun stopSpeaking() {
        if (piperAvailable) {
            // TODO: PiperJni.stop()
        } else {
            fallback.stopSpeaking()
        }
    }

    override fun close() {
        fallback.close()
        _piperReady = false
    }

    private fun assetExists(name: String): Boolean =
        try { context.assets.open(name).use { true } } catch (e: Exception) { false }
}
