package com.patslaurel.resibo.verification

import com.patslaurel.resibo.factcheck.FactCheckApiClient
import com.patslaurel.resibo.factcheck.FactCheckResult
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

class ClaimReviewTool
    @Inject
    constructor(
        private val factCheckApiClient: FactCheckApiClient,
    ) : VerificationTool {
        override val name: String = VerificationToolNames.CLAIM_REVIEW

        override suspend fun execute(call: VerificationToolCall): VerificationToolResult {
            val startedAt = System.currentTimeMillis()
            return runCatching {
                val results = factCheckApiClient.searchRawStrict(call.query, call.maxResults)
                mapResults(call, results, startedAt, System.currentTimeMillis())
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

        companion object {
            fun mapResults(
                call: VerificationToolCall,
                results: List<FactCheckResult>,
                startedAt: Long,
                finishedAt: Long,
            ): VerificationToolResult {
                val records =
                    results
                        .take(call.maxResults)
                        .map { result -> result.toEvidenceRecord(finishedAt) }

                return VerificationToolResult(
                    toolName = VerificationToolNames.CLAIM_REVIEW,
                    input = call.inputSummary(),
                    status = if (records.isEmpty()) ToolStatus.EMPTY else ToolStatus.SUCCESS,
                    records = records,
                    latencyMs = finishedAt - startedAt,
                )
            }

            internal fun FactCheckResult.toEvidenceRecord(fetchedAt: Long): EvidenceRecord {
                val trimmedUrl = reviewUrl.trim()
                return EvidenceRecord(
                    sourceName = publisherName.trim().ifBlank { "Fact check" },
                    sourceType = SourceType.FACT_CHECK,
                    url = trimmedUrl.ifBlank { null },
                    canonicalUrl = trimmedUrl.ifBlank { null },
                    title = reviewTitle.trim().ifBlank { claimText.trim().ifBlank { "Untitled fact check" } },
                    publishedAt = parseDateMillis(reviewDate),
                    fetchedAt = fetchedAt,
                    trustTier = TrustTier.VERIFIED_FACT_CHECK,
                    stance = stanceFromRating(rating),
                    snippet = snippet(),
                )
            }

            internal fun stanceFromRating(rating: String): EvidenceStance {
                val lower = rating.lowercase()
                return when {
                    "false" in lower || "fake" in lower || "misleading" in lower -> EvidenceStance.REFUTES
                    "true" in lower || "correct" in lower -> EvidenceStance.SUPPORTS
                    else -> EvidenceStance.UNCLEAR
                }
            }

            internal fun parseDateMillis(raw: String): Long? {
                val value = raw.trim()
                if (value.isBlank()) return null

                return runCatching { Instant.parse(value).toEpochMilli() }
                    .recoverCatching {
                        LocalDate
                            .parse(value.take(10))
                            .atStartOfDay(ZoneOffset.UTC)
                            .toInstant()
                            .toEpochMilli()
                    }.getOrNull()
            }

            private fun FactCheckResult.snippet(): String =
                buildString {
                    val checkedClaim = claimText.trim()
                    if (checkedClaim.isNotBlank()) append("Claim checked: ").append(checkedClaim)
                    val trimmedRating = rating.trim()
                    if (trimmedRating.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append("Rating: ").append(trimmedRating)
                    }
                    val claimantName = claimant.trim()
                    if (claimantName.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append("Claimant: ").append(claimantName)
                    }
                    if (isEmpty()) append("Fact-check result returned without claim text or rating.")
                }
        }
    }
