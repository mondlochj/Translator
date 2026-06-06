package com.arosys.meetingassistant.impl.nllb

object NLLBConfig {

    const val ENCODER_MODEL_ASSET = "nllb_encoder.onnx"
    const val DECODER_MODEL_ASSET = "nllb_decoder.onnx"
    const val VOCAB_ASSET         = "nllb_vocab.json"

    // -------------------------------------------------------------------------
    // Special token IDs (identical across all NLLB-200 model sizes)
    // -------------------------------------------------------------------------
    const val TOKEN_PAD = 1
    const val TOKEN_BOS = 0     // <s>
    const val TOKEN_EOS = 2     // </s>
    const val TOKEN_UNK = 3

    // Forced language BOS tokens
    const val TOKEN_LANG_SPA = 256057   // __spa_Latn__
    const val TOKEN_LANG_ENG = 256047   // __eng_Latn__

    // -------------------------------------------------------------------------
    // Model I/O tensor names (optimum ONNX export)
    // -------------------------------------------------------------------------
    const val ENC_INPUT_IDS       = "input_ids"
    const val ENC_ATTENTION_MASK  = "attention_mask"
    const val ENC_OUTPUT_HIDDEN   = "last_hidden_state"

    const val DEC_INPUT_IDS       = "input_ids"
    const val DEC_ENC_HIDDEN      = "encoder_hidden_states"
    const val DEC_ENC_ATTN_MASK   = "encoder_attention_mask"
    const val DEC_OUTPUT_LOGITS   = "logits"

    // -------------------------------------------------------------------------
    // Decoding parameters
    // -------------------------------------------------------------------------
    const val MAX_NEW_TOKENS = 128
    const val VOCAB_SIZE     = 256_206

    /** Language code strings used in the SP tokenizer vocab. */
    const val LANG_CODE_SPA = "__spa_Latn__"
    const val LANG_CODE_ENG = "__eng_Latn__"

    /** SentencePiece word-boundary marker (U+2581 LOWER ONE EIGHTH BLOCK). */
    const val SP_BOUNDARY = '▁'
}
