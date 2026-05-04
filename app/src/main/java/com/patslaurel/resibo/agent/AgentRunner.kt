package com.patslaurel.resibo.agent

import android.util.Log
import com.patslaurel.resibo.factcheck.PerplexityClient
import com.patslaurel.resibo.factcheck.PerplexityResult
import com.patslaurel.resibo.llm.LlmTriageEngine
import com.patslaurel.resibo.llm.PromptLoader
import com.patslaurel.resibo.verification.EvidenceFormatter
import com.patslaurel.resibo.verification.VerificationOrchestrator
import com.patslaurel.resibo.verification.VerificationPlanParser
import com.patslaurel.resibo.verification.VerificationPolicy
import com.patslaurel.resibo.verification.VerificationReport
import com.patslaurel.resibo.verification.VerificationToolCall
import com.patslaurel.resibo.verification.VerificationToolResult
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
        val toolResults: List<ToolResult> = emptyList(),
        val perplexityResult: PerplexityResult = PerplexityResult.EMPTY,
        val verificationReport: VerificationReport? = null,
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
        private val verificationOrchestrator: VerificationOrchestrator,
    ) {
        fun run(
            claim: String,
            imagePath: String? = null,
        ): Flow<AgentEvent> =
            flow<AgentEvent> {
                val systemPrompt = promptLoader.load(AGENT_PROMPT)
                val toolResults = mutableListOf<ToolResult>()
                val perplexityResult = PerplexityResult.EMPTY

                val imageAnalysis =
                    if (imagePath != null) {
                        emit(AgentEvent.ToolRequested("analyze_image", imagePath))
                        Log.i(TAG, "Agent: analyzing image at $imagePath")

                        val output =
                            runCatching {
                                engine
                                    .generateWithImage(IMAGE_ANALYSIS_PROMPT, imagePath)
                                    .trim()
                                    .ifBlank { "Image analysis returned no text." }
                            }.getOrElse { error ->
                                val message = error.message ?: error.javaClass.simpleName
                                Log.w(TAG, "Agent: image analysis failed: $message", error)
                                "Could not analyze image: $message"
                            }

                        val promptOutput = output.take(MAX_IMAGE_ANALYSIS_CHARS)
                        toolResults.add(ToolResult("analyze_image", imagePath, promptOutput))
                        emit(AgentEvent.ToolCompleted("analyze_image", output.take(120)))
                        promptOutput
                    } else {
                        ""
                    }

                val userMessage =
                    buildImageAwareClaim(
                        claim = claim,
                        imageAnalysis = imageAnalysis,
                    )

                val plannerJson =
                    runCatching {
                        engine.generatePlannerJson(userMessage)
                    }.getOrElse { error ->
                        val message = error.message ?: error.javaClass.simpleName
                        Log.w(TAG, "Agent: verification planner generation failed: $message", error)
                        ""
                    }
                Log.i(TAG, "Agent: planner output ${plannerJson.length} chars")

                val proposedPlan = VerificationPlanParser.parse(plannerJson, fallbackClaim = claim)
                val approvedPlan = VerificationPolicy.approve(proposedPlan, networkAvailable = true)

                emit(AgentEvent.ToolRequested("verification_plan", userMessage.take(MAX_TOOL_EVENT_INPUT_CHARS)))
                emit(
                    AgentEvent.ToolCompleted(
                        "verification_plan",
                        "Approved ${approvedPlan.toolCalls.size} verification tool call(s)",
                    ),
                )

                approvedPlan.toolCalls.forEach { call ->
                    emit(AgentEvent.ToolRequested(call.toolName, call.toEventInput()))
                }

                Log.i(TAG, "Agent: executing ${approvedPlan.toolCalls.size} verification tool call(s)")
                val verificationReport = verificationOrchestrator.verify(approvedPlan)

                verificationReport.toolResults.forEach { result ->
                    toolResults.add(result.toLegacyToolResult())
                    emit(AgentEvent.ToolCompleted(result.toolName, result.toEventOutput()))
                }

                val evidenceContext = EvidenceFormatter.format(verificationReport)
                val followUpPrompt =
                    """
                    ${systemPrompt.trim()}

                    ---

                    User's post:

                    $userMessage

                    $evidenceContext

                    Write the final Resibo Note using the verification evidence above. Use only grounded evidence
                    from the report. If the evidence is insufficient, stale, or does not address the claim, say that
                    clearly instead of relying on model memory.
                    """.trimIndent()

                Log.i(TAG, "Agent: sending follow-up prompt (${followUpPrompt.length} chars)")

                var accumulated = ""
                engine.generateStreaming(followUpPrompt).collect { token ->
                    accumulated += token
                    emit(AgentEvent.TokenGenerated(accumulated))
                }

                Log.i(TAG, "Agent: done, final Note ${accumulated.length} chars")
                emit(
                    AgentEvent.Done(
                        finalNote = accumulated,
                        toolResults = toolResults,
                        perplexityResult = perplexityResult,
                        verificationReport = verificationReport,
                    ),
                )
            }.flowOn(Dispatchers.Default)

        companion object {
            private const val TAG = "AgentRunner"
            private const val AGENT_PROMPT = "agent_system.md"
            private const val IMAGE_ANALYSIS_PROMPT =
                "Describe this image for fact-checking. Extract all visible text exactly when possible, " +
                    "identify the main claim or implication, name visible people/places/logos only if clear, " +
                    "and note if it appears to be a meme, screenshot, document, chart, or edited image."
            private const val MAX_IMAGE_ANALYSIS_CHARS = 2500
        }
    }

internal fun buildImageAwareClaim(
    claim: String,
    imageAnalysis: String,
): String {
    val trimmedClaim = claim.trim()
    val trimmedImageAnalysis = imageAnalysis.trim()
    if (trimmedImageAnalysis.isBlank()) return trimmedClaim

    return buildString {
        append(trimmedClaim)
        append("\n\nAttached image analysis:\n")
        append(trimmedImageAnalysis)
    }
}

private fun VerificationToolCall.toEventInput(): String =
    inputSummary()
        .ifBlank { preferredDomains.joinToString(",") }
        .take(MAX_TOOL_EVENT_INPUT_CHARS)

private fun VerificationToolResult.toEventOutput(): String =
    buildString {
        append(status.name)
        append(": ")
        append(records.size)
        append(" evidence record(s)")
        error?.let {
            append(" (")
            append(it.take(120))
            append(")")
        }
    }

private fun VerificationToolResult.toLegacyToolResult(): ToolResult =
    ToolResult(
        toolName = toolName,
        input = input,
        output = toLegacySummary().take(MAX_TOOL_RESULT_CHARS),
    )

private fun VerificationToolResult.toLegacySummary(): String =
    buildString {
        appendLine("Status: ${status.name}")
        if (rawSummary.isNotBlank()) {
            appendLine("Summary: $rawSummary")
        }
        error?.let { appendLine("Error: $it") }
        records.forEachIndexed { index, record ->
            appendLine("Evidence ${index + 1}: ${record.title}")
            record.url?.let { appendLine("URL: $it") }
            appendLine("Snippet: ${record.snippet}")
        }
    }.trim()

private const val MAX_TOOL_EVENT_INPUT_CHARS = 500
private const val MAX_TOOL_RESULT_CHARS = 2000
