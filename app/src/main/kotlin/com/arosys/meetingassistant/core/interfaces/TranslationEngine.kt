package com.arosys.meetingassistant.core.interfaces

import kotlinx.coroutines.flow.Flow

/**
 * Bilingual translation engine.  Designed for streaming partial output so
 * translated words appear as they are generated rather than waiting for a
 * complete sentence.
 */
interface TranslationEngine : AutoCloseable {

    val modelName: String
    val isReady: Boolean

    /**
     * Translates [text] from [sourceLang] to [targetLang].
     * Emits [TranslationResult]s incrementally.  The final emission has
     * [TranslationResult.isFinal] = true.
     */
    fun translateStream(
        text: String,
        sourceLang: String = "spa_Latn",
        targetLang: String = "eng_Latn",
    ): Flow<TranslationResult>

    /** Convenience: await the final translation of a complete sentence. */
    suspend fun translate(
        text: String,
        sourceLang: String = "spa_Latn",
        targetLang: String = "eng_Latn",
    ): String

    suspend fun warmUp()
}

data class TranslationResult(
    val sourceText: String,
    val translatedText: String,
    val isFinal: Boolean,
    val latencyMs: Long = -1,
)
