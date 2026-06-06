package com.arosys.meetingassistant.testing.fakes

import com.arosys.meetingassistant.core.interfaces.GenerationParams
import com.arosys.meetingassistant.core.interfaces.LLMProvider
import com.arosys.meetingassistant.core.interfaces.LLMToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Scripted [LLMProvider] for unit tests.
 *
 * Map prompt substrings to canned responses using [responses].  If no key
 * matches, [defaultResponse] is returned.
 *
 * ```
 * val fake = FakeLLMProvider(responses = mapOf(
 *     "summarize" to "Supplier delay. Delivery next Tuesday.",
 *     "action items" to "John to review proposal by Friday.",
 * ))
 * ```
 */
class FakeLLMProvider(
    override val modelName: String = "fake-qwen3",
    private val responses: Map<String, String> = emptyMap(),
    private val defaultResponse: String = "[LLM response]",
    private val streamTokens: Boolean = false,
) : LLMProvider {

    override var isReady: Boolean = true

    data class GenerateCall(val prompt: String, val params: GenerationParams)

    /** All calls to [generate] or [generateStream], in order. */
    val generateCalls = mutableListOf<GenerateCall>()

    val warmUpCount get() = _warmUpCount
    private var _warmUpCount = 0

    override fun generateStream(prompt: String, params: GenerationParams): Flow<LLMToken> = flow {
        generateCalls += GenerateCall(prompt, params)
        val text = resolve(prompt)
        if (streamTokens) {
            text.split(" ").forEach { word -> emit(LLMToken("$word ", isFinal = false)) }
        }
        emit(LLMToken(text, isFinal = true))
    }

    override suspend fun generate(prompt: String, params: GenerationParams): String {
        generateCalls += GenerateCall(prompt, params)
        return resolve(prompt)
    }

    override suspend fun warmUp() {
        _warmUpCount++
    }

    override fun close() {}

    private fun resolve(prompt: String): String =
        responses.entries.firstOrNull { (key, _) -> prompt.contains(key, ignoreCase = true) }?.value
            ?: defaultResponse
}
