package com.patslaurel.resibo.llm

/**
 * UI-level state for an in-flight or completed Gemma generation. Shared between
 * any screen that triggers [LlmTriageEngine]: ShareReceiverActivity, Playground, etc.
 */
sealed interface GenerationState {
    data object Idle : GenerationState

    data object Generating : GenerationState

    data class Result(
        val text: String,
        val elapsedMs: Long,
    ) : GenerationState

    data class Error(
        val message: String,
    ) : GenerationState
}
