package com.patslaurel.resibo.verification

import javax.inject.Inject

class LocalEvidenceTool
    @Inject
    constructor(
        private val evidenceSearch: EvidenceSearch,
    ) : VerificationTool {
        override val name: String = VerificationToolNames.LOCAL_EVIDENCE

        override suspend fun execute(call: VerificationToolCall): VerificationToolResult {
            val startedAt = System.currentTimeMillis()
            return runCatching {
                val records =
                    evidenceSearch
                        .searchEvidence(call.query, call.maxResults)
                        .map { it.copy(sourceType = SourceType.LOCAL_CACHE) }

                VerificationToolResult(
                    toolName = name,
                    input = call.inputSummary(),
                    status = if (records.isEmpty()) ToolStatus.EMPTY else ToolStatus.SUCCESS,
                    records = records,
                    latencyMs = System.currentTimeMillis() - startedAt,
                )
            }.getOrElse { error ->
                VerificationToolResult(
                    toolName = name,
                    input = call.inputSummary(),
                    status = ToolStatus.ERROR,
                    latencyMs = System.currentTimeMillis() - startedAt,
                    error = error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }
