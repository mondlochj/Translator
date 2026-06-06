package com.arosys.meetingassistant.testing.fakes

import com.arosys.meetingassistant.core.interfaces.TranslationEngine
import com.arosys.meetingassistant.core.interfaces.TranslationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Deterministic [TranslationEngine] for unit tests.
 *
 * By default, prepends "[EN] " to the source text so tests can verify
 * translated text without real model output.  Provide [translations] to
 * return specific strings for specific inputs.
 *
 * Records every translation call in [translationHistory] for assertion.
 */
class FakeTranslationEngine(
    override val modelName: String = "fake-nllb",
    private val translations: Map<String, String> = emptyMap(),
    private val defaultBehaviour: (String) -> String = { "[EN] $it" },
    private val latencyMs: Long = 10L,
) : TranslationEngine {

    override var isReady: Boolean = true

    /** List of (sourceText, translatedText) pairs in call order. */
    val translationHistory = mutableListOf<Pair<String, String>>()

    val warmUpCount get() = _warmUpCount
    private var _warmUpCount = 0

    override fun translateStream(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): Flow<TranslationResult> {
        val out = resolve(text)
        translationHistory += text to out
        return flowOf(TranslationResult(text, out, isFinal = true, latencyMs = latencyMs))
    }

    override suspend fun translate(text: String, sourceLang: String, targetLang: String): String {
        val out = resolve(text)
        translationHistory += text to out
        return out
    }

    override suspend fun warmUp() {
        _warmUpCount++
    }

    override fun close() {}

    private fun resolve(text: String) = translations[text] ?: defaultBehaviour(text)
}
