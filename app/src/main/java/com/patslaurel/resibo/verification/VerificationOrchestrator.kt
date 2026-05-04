package com.patslaurel.resibo.verification

import javax.inject.Inject

class VerificationOrchestrator
    @Inject
    constructor(
        tools: Set<@JvmSuppressWildcards VerificationTool>,
        private val evidenceStore: EvidenceStore,
    ) {
        private val toolsByName = tools.associateBy { it.name }

        constructor(
            tools: List<VerificationTool>,
            evidenceStore: EvidenceStore,
        ) : this(tools.toSet(), evidenceStore)

        suspend fun verify(plan: VerificationPlan): VerificationReport {
            val toolResults = plan.toolCalls.map { call -> executeToolCall(call) }
            val recordsToStore =
                toolResults
                    .flatMap { it.records }
                    .filterNot { it.sourceType == SourceType.LOCAL_CACHE }

            evidenceStore.saveEvidence(recordsToStore)

            return VerificationReport(
                plan = plan,
                toolResults = toolResults,
                evidenceMode = inferEvidenceMode(toolResults.flatMap { it.records }),
            )
        }

        private suspend fun executeToolCall(call: VerificationToolCall): VerificationToolResult {
            val tool = toolsByName[call.toolName] ?: return blockedResult(call)
            return runCatching { tool.execute(call) }
                .getOrElse { error ->
                    VerificationToolResult(
                        toolName = call.toolName,
                        input = call.inputSummary(),
                        status = ToolStatus.ERROR,
                        error = error.message ?: error.javaClass.simpleName,
                    )
                }
        }

        private fun blockedResult(call: VerificationToolCall): VerificationToolResult =
            VerificationToolResult(
                toolName = call.toolName,
                input = call.inputSummary(),
                status = ToolStatus.BLOCKED,
                error = "No verification tool registered for ${call.toolName}",
            )

        private fun inferEvidenceMode(records: List<EvidenceRecord>): EvidenceMode =
            when {
                records.isEmpty() -> EvidenceMode.INSUFFICIENT

                records.any { it.sourceType != SourceType.LOCAL_CACHE && it.sourceType != SourceType.USER_PROVIDED } ->
                    EvidenceMode.LIVE

                records.any { it.sourceType == SourceType.LOCAL_CACHE } -> EvidenceMode.LOCAL_ONLY

                else -> EvidenceMode.USER_PROVIDED
            }
    }
