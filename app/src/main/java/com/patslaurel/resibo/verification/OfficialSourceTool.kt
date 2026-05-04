package com.patslaurel.resibo.verification

import com.patslaurel.resibo.factcheck.FactCheckResult
import com.patslaurel.resibo.factcheck.PerplexityClient
import com.patslaurel.resibo.factcheck.PerplexityResult
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class OfficialSourceTool
    @Inject
    constructor(
        private val perplexityClient: PerplexityClient,
    ) : VerificationTool {
        override val name: String = VerificationToolNames.OFFICIAL_SOURCE

        override suspend fun execute(call: VerificationToolCall): VerificationToolResult {
            val startedAt = System.currentTimeMillis()
            val query = officialQuery(call)
            return runCatching {
                val result = perplexityClient.searchStrict(query)
                mapPerplexityResult(call.copy(query = query), result, startedAt, System.currentTimeMillis())
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                VerificationToolResult(
                    toolName = name,
                    input = call.inputSummary(),
                    status = ToolStatus.ERROR,
                    latencyMs = System.currentTimeMillis() - startedAt,
                    error = error.message ?: error.javaClass.simpleName,
                )
            }
        }

        private fun officialQuery(call: VerificationToolCall): String {
            val domainHints =
                call.preferredDomains
                    .mapNotNull { normalizePreferredDomain(it) }
                    .distinct()
                    .joinToString(separator = " OR ") { "site:$it" }

            return listOf(call.query, domainHints)
                .filter { it.isNotBlank() }
                .joinToString(separator = " ")
        }

        companion object {
            fun mapPerplexityResult(
                call: VerificationToolCall,
                result: PerplexityResult,
                startedAt: Long,
                finishedAt: Long,
            ): VerificationToolResult {
                val preferredDomains =
                    call.preferredDomains
                        .mapNotNull { normalizePreferredDomain(it) }
                        .distinct()
                val snippet =
                    with(PerplexityDiscoveryTool) {
                        result.discoverySnippet(call)
                    }
                val records =
                    result.sources
                        .take(call.maxResults)
                        .map { source ->
                            source.toOfficialOrDiscoveryEvidence(
                                preferredDomains = preferredDomains,
                                fetchedAt = finishedAt,
                                snippet = snippet,
                            )
                        }

                return VerificationToolResult(
                    toolName = VerificationToolNames.OFFICIAL_SOURCE,
                    input = call.inputSummary(),
                    status = if (records.isEmpty()) ToolStatus.EMPTY else ToolStatus.SUCCESS,
                    records = records,
                    rawSummary = result.text,
                    latencyMs = finishedAt - startedAt,
                )
            }

            private fun FactCheckResult.toOfficialOrDiscoveryEvidence(
                preferredDomains: List<String>,
                fetchedAt: Long,
                snippet: String,
            ): EvidenceRecord {
                val sourceDomain =
                    PerplexityDiscoveryTool
                        .normalizedDomain(reviewUrl)
                        .ifBlank { PerplexityDiscoveryTool.normalizedDomain(publisherSite) }
                val official = preferredDomains.any { preferred -> sourceDomain.matchesPreferred(preferred) }
                val record =
                    with(PerplexityDiscoveryTool) {
                        toDiscoveryEvidence(
                            fetchedAt = fetchedAt,
                            snippet = snippet,
                        )
                    }

                return record.copy(
                    sourceType = if (official) SourceType.OFFICIAL else SourceType.AGGREGATOR,
                    trustTier = if (official) TrustTier.OFFICIAL else TrustTier.DISCOVERY_ONLY,
                )
            }

            internal fun normalizePreferredDomain(raw: String): String? =
                PerplexityDiscoveryTool
                    .normalizedDomain(raw)
                    .ifBlank { raw.trim().lowercase().removePrefix("www.") }
                    .takeIf { it.isNotBlank() && "." in it && "/" !in it && " " !in it }

            private fun String.matchesPreferred(preferredDomain: String): Boolean =
                this == preferredDomain || endsWith(".$preferredDomain")
        }
    }
