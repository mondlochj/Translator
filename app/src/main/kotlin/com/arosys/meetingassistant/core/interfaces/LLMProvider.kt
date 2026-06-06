package com.arosys.meetingassistant.core.interfaces

import kotlinx.coroutines.flow.Flow

/**
 * Local LLM for meeting analysis, real-time filtering, and copilot queries.
 * Qwen3 4B is the primary implementation.
 */
interface LLMProvider : AutoCloseable {

    val modelName: String
    val isReady: Boolean

    /**
     * Generates a completion for [prompt].
     * Emits tokens as they are produced; final token has [LLMToken.isFinal] = true.
     */
    fun generateStream(
        prompt: String,
        params: GenerationParams = GenerationParams(),
    ): Flow<LLMToken>

    /** Convenience: collect entire generation as a single string. */
    suspend fun generate(
        prompt: String,
        params: GenerationParams = GenerationParams(),
    ): String

    suspend fun warmUp()
}

data class GenerationParams(
    val maxTokens: Int = 512,
    val temperature: Float = 0.3f,
    val topP: Float = 0.9f,
    val stopSequences: List<String> = listOf("</s>", "[END]"),
)

data class LLMToken(
    val text: String,
    val isFinal: Boolean,
)
