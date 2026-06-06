package com.arosys.meetingassistant.impl.nllb

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.arosys.meetingassistant.accelerator.HardwareAcceleratorManager
import com.arosys.meetingassistant.accelerator.OrtSessionFactory
import com.arosys.meetingassistant.core.interfaces.TranslationEngine
import com.arosys.meetingassistant.core.interfaces.TranslationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

private const val TAG = "NLLBTranslationEngine"

/**
 * ONNX Runtime–based NLLB-200-distilled-600M translation engine.
 *
 * Model files expected in assets/:
 *   nllb_encoder.onnx  — encoder (input_ids + attention_mask → last_hidden_state)
 *   nllb_decoder.onnx  — decoder (input_ids + encoder_hidden_states → logits)
 *   nllb_vocab.json    — SentencePiece vocabulary
 *
 * Generate with:  python3 scripts/download_nllb_onnx.py
 *
 * Streaming: the decoder loop emits a [TranslationResult] after every new
 * token, so translated words appear word-by-word in the UI.  The final
 * emission has [TranslationResult.isFinal] = true.
 */
class NLLBTranslationEngine(private val context: Context) : TranslationEngine {

    override val modelName = "nllb-200-distilled-600m"

    private val env = OrtEnvironment.getEnvironment()
    private val tokenizer = SentencePieceTokenizer(context)

    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null

    override var isReady: Boolean = false
        private set

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    override suspend fun warmUp() {
        if (isReady) return
        val encBytes = loadAsset(NLLBConfig.ENCODER_MODEL_ASSET) ?: run {
            Log.w(TAG, "Encoder not found — run scripts/download_nllb_onnx.py")
            return
        }
        val decBytes = loadAsset(NLLBConfig.DECODER_MODEL_ASSET) ?: run {
            Log.w(TAG, "Decoder not found — run scripts/download_nllb_onnx.py")
            return
        }
        if (!tokenizer.isLoaded) {
            Log.w(TAG, "Vocab not found — run scripts/download_nllb_onnx.py")
            return
        }

        val backend = HardwareAcceleratorManager.instance.bestBackendFor(modelName)
        Log.i(TAG, "Loading NLLB encoder on ${backend.displayName}")
        val (enc, encBackend) = OrtSessionFactory.createWithFallback(encBytes, backend)
        val (dec, _)          = OrtSessionFactory.createWithFallback(decBytes, backend)
        encoderSession = enc
        decoderSession = dec
        isReady = true
        Log.i(TAG, "NLLB ready — backend=${encBackend.displayName}")

        // Async per-model benchmark
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            HardwareAcceleratorManager.instance.benchmarkModel(modelName, encBytes)
        }
    }

    // -------------------------------------------------------------------------
    // Translation — streaming
    // -------------------------------------------------------------------------

    override fun translateStream(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): Flow<TranslationResult> = flow {
        if (!isReady) {
            emit(TranslationResult(text, "[Translation model not loaded]", isFinal = true))
            return@flow
        }
        if (text.isBlank()) {
            emit(TranslationResult(text, "", isFinal = true))
            return@flow
        }

        val latencyMs = measureTimeMillis {
            val srcLangId = langTokenId(sourceLang)
            val tgtLangId = langTokenId(targetLang)

            // Encode source text
            val inputIds = tokenizer.encode(text, srcLangId)
            val seqLen = inputIds.size.toLong()
            val inputIdsTensor  = OnnxTensor.createTensor(env, inputIds, longArrayOf(1, seqLen))
            val attentionMask   = OnnxTensor.createTensor(env, LongArray(seqLen.toInt()) { 1L }, longArrayOf(1, seqLen))

            // Encoder forward pass
            val encoderOut = encoderSession!!.run(mapOf(
                NLLBConfig.ENC_INPUT_IDS      to inputIdsTensor,
                NLLBConfig.ENC_ATTENTION_MASK to attentionMask,
            ))
            inputIdsTensor.close()
            val hiddenState = encoderOut.get(NLLBConfig.ENC_OUTPUT_HIDDEN).get() as OnnxTensor

            // Greedy decoder loop — emit a token at a time for streaming
            val decoderIds = mutableListOf(tgtLangId.toLong())

            for (step in 0 until NLLBConfig.MAX_NEW_TOKENS) {
                val decInputIds = OnnxTensor.createTensor(
                    env,
                    decoderIds.toLongArray(),
                    longArrayOf(1, decoderIds.size.toLong()),
                )
                val decResult = decoderSession!!.run(mapOf(
                    NLLBConfig.DEC_INPUT_IDS      to decInputIds,
                    NLLBConfig.DEC_ENC_HIDDEN     to hiddenState,
                    NLLBConfig.DEC_ENC_ATTN_MASK  to attentionMask,
                ))
                decInputIds.close()

                val logits = decResult.get(NLLBConfig.DEC_OUTPUT_LOGITS).get() as OnnxTensor
                val vocabSize   = logits.info.shape[2].toInt()
                val lastOffset  = (decoderIds.size - 1) * vocabSize
                val logitBuffer = logits.floatBuffer

                var maxLogit = Float.NEGATIVE_INFINITY
                var nextToken = NLLBConfig.TOKEN_EOS
                for (v in 0 until vocabSize) {
                    val l = logitBuffer.get(lastOffset + v)
                    if (l > maxLogit) { maxLogit = l; nextToken = v }
                }
                logits.close()

                if (nextToken == NLLBConfig.TOKEN_EOS) break
                decoderIds.add(nextToken.toLong())

                // Emit partial translation after each new token
                val partial = tokenizer.decode(decoderIds.drop(1).map { it.toInt() })
                emit(TranslationResult(text, partial, isFinal = false))
            }

            hiddenState.close()
            attentionMask.close()
            encoderOut.close()

            val finalText = tokenizer.decode(decoderIds.drop(1).map { it.toInt() })
            emit(TranslationResult(text, finalText, isFinal = true))
        }
        Log.d(TAG, "Translated in ${latencyMs}ms: \"${text.take(40)}\"")
    }

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): String {
        var result = ""
        translateStream(text, sourceLang, targetLang).collect { r ->
            if (r.isFinal) result = r.translatedText
        }
        return result
    }

    override fun close() {
        encoderSession?.close()
        decoderSession?.close()
        isReady = false
    }

    // -------------------------------------------------------------------------

    private fun langTokenId(langCode: String): Int = when (langCode) {
        "spa_Latn" -> NLLBConfig.TOKEN_LANG_SPA
        "eng_Latn" -> NLLBConfig.TOKEN_LANG_ENG
        else        -> NLLBConfig.TOKEN_LANG_ENG
    }

    private fun loadAsset(name: String): ByteArray? =
        try { context.assets.open(name).use { it.readBytes() } }
        catch (e: Exception) { null }
}
