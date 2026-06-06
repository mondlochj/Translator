package com.arosys.meetingassistant.impl.whisper

/**
 * Whisper-specific constants.  Token IDs are identical across all model sizes.
 *
 * To force Spanish transcription (no translation), the decoder is initialised
 * with [FORCED_DECODER_IDS_ES_TRANSCRIBE] as the prompt prefix.
 */
object WhisperConfig {

    const val ENCODER_MODEL_ASSET = "whisper_encoder.onnx"
    const val DECODER_MODEL_ASSET = "whisper_decoder.onnx"
    const val VOCAB_ASSET = "whisper_vocab.json"

    // -------------------------------------------------------------------------
    // Special tokens
    // -------------------------------------------------------------------------
    const val TOKEN_EOT = 50256          // <|endoftext|>
    const val TOKEN_SOT = 50258          // <|startoftranscript|>
    const val TOKEN_TRANSLATE = 50358    // <|translate|>
    const val TOKEN_TRANSCRIBE = 50359   // <|transcribe|>
    const val TOKEN_NO_TIMESTAMPS = 50363

    // Language tokens (Whisper tiny / base / small / medium / large)
    const val TOKEN_LANG_ES = 50262      // <|es|>
    const val TOKEN_LANG_EN = 50259      // <|en|>

    /** Initial decoder token sequence to force Spanish transcription. */
    val FORCED_DECODER_IDS_ES_TRANSCRIBE = intArrayOf(
        TOKEN_SOT, TOKEN_LANG_ES, TOKEN_TRANSCRIBE, TOKEN_NO_TIMESTAMPS
    )

    // -------------------------------------------------------------------------
    // Model I/O names (optimum ONNX export)
    // -------------------------------------------------------------------------
    const val ENCODER_INPUT_FEATURES = "input_features"
    const val ENCODER_OUTPUT_HIDDEN = "last_hidden_state"

    const val DECODER_INPUT_IDS = "input_ids"
    const val DECODER_ENCODER_HIDDEN = "encoder_hidden_states"
    const val DECODER_OUTPUT_LOGITS = "logits"

    // -------------------------------------------------------------------------
    // Decoding limits
    // -------------------------------------------------------------------------
    const val MAX_NEW_TOKENS = 224       // Whisper's own limit for base model
    const val SAMPLE_RATE = 16_000
    const val N_MELS = 80
    const val N_FRAMES = 3_000          // 30 seconds
}
