package com.patslaurel.resibo.agent

import android.util.Log
import com.patslaurel.resibo.factcheck.PerplexityClient
import com.patslaurel.resibo.factcheck.PerplexityResult
import com.patslaurel.resibo.llm.LlmTriageEngine
import com.patslaurel.resibo.llm.PromptLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AgentEvent {
    data class ToolRequested(
        val toolName: String,
        val input: String,
    ) : AgentEvent

    data class ToolCompleted(
        val toolName: String,
        val output: String,
    ) : AgentEvent

    data class TokenGenerated(
        val fullText: String,
    ) : AgentEvent

    data class Done(
        val finalNote: String,
        val toolResults: List<ToolResult>,
        val perplexityResult: PerplexityResult,
    ) : AgentEvent

    data class Error(
        val message: String,
    ) : AgentEvent
}

@Singleton
class AgentRunner
    @Inject
    constructor(
        private val engine: LlmTriageEngine,
        private val perplexity: PerplexityClient,
        private val promptLoader: PromptLoader,
    ) {
        fun run(
            claim: String,
            imagePath: String? = null,
        ): Flow<AgentEvent> =
            flow<AgentEvent> {
                val systemPrompt = promptLoader.load(AGENT_PROMPT)
                val toolResults = mutableListOf<ToolResult>()
                var perplexityResult = PerplexityResult.EMPTY

                val userMessage =
                    if (imagePath != null) {
                        "$claim\n\n[Image attached]"
                    } else {
                        claim
                    }

                val initialPrompt = "${systemPrompt.trim()}\n\n---\n\nUser's post:\n\n$userMessage"

                Log.i(TAG, "Agent: sending initial prompt (${initialPrompt.length} chars)")
                val firstResponse = engine.generate(initialPrompt)
                Log.i(TAG, "Agent: first response (${firstResponse.length} chars): ${firstResponse.take(100)}")

                val calls =
                    if (ToolCallParser.hasToolCalls(firstResponse)) {
                        ToolCallParser.parse(firstResponse)
                    } else {
                        Log.i(TAG, "Agent: no tool calls in response, auto-searching as fallback")
                        val query = engine.extractSearchKeywords(claim)
                        if (query.isNotBlank()) listOf(ToolCall.SearchWeb(query)) else emptyList()
                    }

                if (calls.isEmpty()) {
                    Log.i(TAG, "Agent: no tools to call, returning direct response")
                    emit(AgentEvent.TokenGenerated(firstResponse))
                    emit(AgentEvent.Done(firstResponse, toolResults, perplexityResult))
                    return@flow
                }

                Log.i(TAG, "Agent: ${calls.size} tool call(s) to execute")

                val toolOutputs = StringBuilder()
                for (call in calls.take(2)) {
                    when (call) {
                        is ToolCall.SearchWeb -> {
                            emit(AgentEvent.ToolRequested("search_web", call.query))
                            Log.i(TAG, "Agent: executing search_web('${call.query}')")

                            perplexityResult =
                                runCatching { perplexity.search(call.query) }
                                    .getOrDefault(PerplexityResult.EMPTY)

                            val output = perplexityResult.text.ifBlank { "No results found." }
                            toolResults.add(ToolResult("search_web", call.query, output.take(2000)))
                            emit(AgentEvent.ToolCompleted("search_web", "${perplexityResult.sources.size} sources found"))

                            toolOutputs.append("\n\n## search_web results for \"${call.query}\":\n$output")
                        }

                        is ToolCall.AnalyzeImage -> {
                            if (imagePath != null) {
                                emit(AgentEvent.ToolRequested("analyze_image", imagePath))
                                val description =
                                    runCatching {
                                        engine.generateWithImage(
                                            "Describe this image in detail. Extract any visible text.",
                                            imagePath,
                                        )
                                    }.getOrDefault("Could not analyze image.")
                                toolResults.add(ToolResult("analyze_image", imagePath, description.take(1000)))
                                emit(AgentEvent.ToolCompleted("analyze_image", "Image analyzed"))
                                toolOutputs.append("\n\n## analyze_image results:\n$description")
                            }
                        }
                    }
                }

                val followUpPrompt = "$initialPrompt\n\nYou called tools and got these results:$toolOutputs\n\nNow write your Note based on the evidence above."

                Log.i(TAG, "Agent: sending follow-up prompt (${followUpPrompt.length} chars)")

                var accumulated = ""
                engine.generateStreaming(followUpPrompt).collect { token ->
                    accumulated += token
                    emit(AgentEvent.TokenGenerated(accumulated))
                }

                Log.i(TAG, "Agent: done, final Note ${accumulated.length} chars")
                emit(AgentEvent.Done(accumulated, toolResults, perplexityResult))
            }.flowOn(Dispatchers.Default)

        companion object {
            private const val TAG = "AgentRunner"
            private const val AGENT_PROMPT = "agent_system.md"
        }
    }
