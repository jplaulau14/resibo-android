package com.patslaurel.resibo.verification

import com.patslaurel.resibo.factcheck.FactCheckResult
import com.patslaurel.resibo.factcheck.PerplexityClient
import com.patslaurel.resibo.factcheck.PerplexityResult
import java.net.URI
import java.util.Locale
import javax.inject.Inject

class PerplexityDiscoveryTool
    @Inject
    constructor(
        private val perplexityClient: PerplexityClient,
    ) : VerificationTool {
        override val name: String = VerificationToolNames.PERPLEXITY_DISCOVERY

        override suspend fun execute(call: VerificationToolCall): VerificationToolResult {
            val startedAt = System.currentTimeMillis()
            return runCatching {
                val result = perplexityClient.searchStrict(call.query)
                mapResult(call, result, startedAt, System.currentTimeMillis())
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

        companion object {
            fun mapResult(
                call: VerificationToolCall,
                result: PerplexityResult,
                startedAt: Long,
                finishedAt: Long,
            ): VerificationToolResult {
                val records =
                    result.sources
                        .take(call.maxResults)
                        .map { source ->
                            source.toDiscoveryEvidence(
                                fetchedAt = finishedAt,
                                snippet = result.discoverySnippet(call),
                            )
                        }

                return VerificationToolResult(
                    toolName = VerificationToolNames.PERPLEXITY_DISCOVERY,
                    input = call.inputSummary(),
                    status = if (records.isEmpty()) ToolStatus.EMPTY else ToolStatus.SUCCESS,
                    records = records,
                    rawSummary = result.text,
                    latencyMs = finishedAt - startedAt,
                )
            }

            internal fun FactCheckResult.toDiscoveryEvidence(
                fetchedAt: Long,
                snippet: String,
            ): EvidenceRecord {
                val domain = normalizedDomain(reviewUrl).ifBlank { publisherSite.trim() }
                val sourceName = publisherName.trim().ifBlank { domain.ifBlank { "Unknown source" } }
                val title = reviewTitle.trim().ifBlank { domain.ifBlank { "Untitled source" } }
                val trimmedUrl = reviewUrl.trim()
                return EvidenceRecord(
                    sourceName = sourceName,
                    sourceType = SourceType.AGGREGATOR,
                    url = trimmedUrl.ifBlank { null },
                    canonicalUrl = trimmedUrl.ifBlank { null },
                    title = title,
                    fetchedAt = fetchedAt,
                    trustTier = TrustTier.DISCOVERY_ONLY,
                    stance = EvidenceStance.BACKGROUND,
                    snippet = snippet,
                )
            }

            internal fun PerplexityResult.discoverySnippet(call: VerificationToolCall): String =
                text.trim().ifBlank {
                    "Perplexity found this source for the query: ${call.query.ifBlank { call.inputSummary() }}"
                }

            internal fun normalizedDomain(raw: String): String {
                val value = raw.trim()
                if (value.isBlank()) return ""
                val uriText = if ("://" in value) value else "https://$value"
                return runCatching {
                    URI(uriText)
                        .host
                        .orEmpty()
                        .lowercase(Locale.US)
                        .removePrefix("www.")
                }.getOrDefault("")
            }
        }
    }
