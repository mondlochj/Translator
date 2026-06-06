package com.arosys.meetingassistant.impl.whisper

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.arosys.meetingassistant.accelerator.HardwareAcceleratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.arosys.meetingassistant.accelerator.OrtSessionFactory
import com.arosys.meetingassistant.audio.AudioChunker
import com.arosys.meetingassistant.audio.MelSpectrogramProcessor
import com.arosys.meetingassistant.core.interfaces.AudioSource
import com.arosys.meetingassistant.core.interfaces.SpeechRecognizer
import com.arosys.meetingassistant.core.interfaces.TranscriptSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.LongBuffer

private const val TAG = "WhisperOnnxRecognizer"

/**
 * ONNX Runtime–based Whisper speech recogniser.
 *
 * Model files expected in assets/:
 *   whisper_encoder.onnx  — takes input_features [1, 80, 3000]
 *   whisper_decoder.onnx  — takes input_ids + encoder_hidden_states
 *
 * Generate them with:  python3 scripts/download_whisper_onnx.py
 *
 * If either file is missing, [isReady] = false and [transcribeStream] emits
 * an error segment rather than crashing.
 *
 * Backend selection is handled automatically by [HardwareAcceleratorManager];
 * first call to [warmUp] triggers the per-model benchmark.
 */
class WhisperOnnxSpeechRecognizer(
    private val context: Context,
    private val chunker: AudioChunker = AudioChunker(),
) : SpeechRecognizer {

    override val modelName = "whisper-tiny-onnx"

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val mel = MelSpectrogramProcessor()
    private val tokenizer = WhisperTokenizer(context)

    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null

    override var isReady: Boolean = false
        private set

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    override suspend fun warmUp() {
        if (isReady) return
        val encoderBytes = loadAsset(WhisperConfig.ENCODER_MODEL_ASSET) ?: run {
            Log.w(TAG, "Encoder model not found — run scripts/download_whisper_onnx.py")
            return
        }
        val decoderBytes = loadAsset(WhisperConfig.DECODER_MODEL_ASSET) ?: run {
            Log.w(TAG, "Decoder model not found — run scripts/download_whisper_onnx.py")
            return
        }

        val backend = HardwareAcceleratorManager.instance.bestBackendFor(modelName)
        Log.i(TAG, "Loading Whisper encoder on ${backend.displayName}")
        val (enc, encBackend) = OrtSessionFactory.createWithFallback(encoderBytes, backend)
        val (dec, _) = OrtSessionFactory.createWithFallback(decoderBytes, backend)

        encoderSession = enc
        decoderSession = dec
        isReady = true
        Log.i(TAG, "Whisper ready — encoder on ${encBackend.displayName}")

        // Trigger async per-model benchmark so future launches are faster
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            HardwareAcceleratorManager.instance.benchmarkModel(modelName, encoderBytes)
        }
    }

    // -------------------------------------------------------------------------
    // Transcription
    // -------------------------------------------------------------------------

    override fun transcribeStream(audioSource: AudioSource): Flow<TranscriptSegment> = flow {
        if (!isReady) {
            emit(TranscriptSegment(
                text = "[Model not loaded — run scripts/download_whisper_onnx.py]",
                isFinal = true, startMs = 0L, endMs = 0L
            ))
            return@flow
        }

        chunker.chunkStream(audioSource).collect { chunk ->
            // Emit a non-final placeholder so the UI shows activity immediately
            emit(TranscriptSegment(
                text = "…",
                isFinal = false,
                startMs = chunk.startTimestampMs,
                endMs = chunk.startTimestampMs + chunk.durationMs,
            ))

            val text = transcribeChunk(chunk.samples)

            if (text.isNotBlank()) {
                emit(TranscriptSegment(
                    text = text,
                    isFinal = true,
                    startMs = chunk.startTimestampMs,
                    endMs = chunk.startTimestampMs + chunk.durationMs,
                ))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal inference
    // -------------------------------------------------------------------------

    private fun transcribeChunk(samples: FloatArray): String {
        val enc = encoderSession ?: return ""
        val dec = decoderSession ?: return ""

        // 1. Mel spectrogram → [1, 80, 3000]
        val melFlat = mel.computeFlat(samples, WhisperConfig.N_FRAMES)
        val melShape = longArrayOf(1, WhisperConfig.N_MELS.toLong(), WhisperConfig.N_FRAMES.toLong())
        val melTensor = OnnxTensor.createTensor(env, melFlat, melShape)

        // 2. Encoder
        val encoderResult = enc.run(mapOf(WhisperConfig.ENCODER_INPUT_FEATURES to melTensor))
        melTensor.close()

        val hiddenState = encoderResult.get(WhisperConfig.ENCODER_OUTPUT_HIDDEN)
            .get() as OnnxTensor
        val hiddenShape = hiddenState.info.shape  // [1, 1500, hidden_dim]

        // 3. Greedy decoder loop
        val tokenIds = WhisperConfig.FORCED_DECODER_IDS_ES_TRANSCRIBE.toMutableList()

        for (step in 0 until WhisperConfig.MAX_NEW_TOKENS) {
            val inputIds = LongBuffer.wrap(tokenIds.map { it.toLong() }.toLongArray())
            val inputIdsTensor = OnnxTensor.createTensor(
                env, inputIds, longArrayOf(1, tokenIds.size.toLong())
            )

            val decResult = dec.run(mapOf(
                WhisperConfig.DECODER_INPUT_IDS to inputIdsTensor,
                WhisperConfig.DECODER_ENCODER_HIDDEN to hiddenState,
            ))
            inputIdsTensor.close()

            val logits = decResult.get(WhisperConfig.DECODER_OUTPUT_LOGITS).get() as OnnxTensor
            // logits shape: [1, seq_len, vocab_size] — take the last position
            val logitData = logits.floatBuffer
            val vocabSize = logits.info.shape[2].toInt()
            val lastOffset = (tokenIds.size - 1) * vocabSize

            var maxLogit = Float.NEGATIVE_INFINITY
            var nextToken = WhisperConfig.TOKEN_EOT
            for (v in 0 until vocabSize) {
                val l = logitData.get(lastOffset + v)
                if (l > maxLogit) { maxLogit = l; nextToken = v }
            }
            logits.close()

            if (nextToken == WhisperConfig.TOKEN_EOT) break
            tokenIds.add(nextToken)
        }

        hiddenState.close()
        encoderResult.close()

        return tokenizer.decode(tokenIds.drop(WhisperConfig.FORCED_DECODER_IDS_ES_TRANSCRIBE.size))
    }

    private fun loadAsset(name: String): ByteArray? =
        try { context.assets.open(name).use { it.readBytes() } }
        catch (e: Exception) { null }

    override fun close() {
        encoderSession?.close()
        decoderSession?.close()
        isReady = false
    }
}
