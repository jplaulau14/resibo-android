package com.patslaurel.resibo.verification

object VerificationToolNames {
    const val PERPLEXITY_DISCOVERY = "perplexity_discovery"
    const val CLAIM_REVIEW = "claim_review"
    const val OFFICIAL_SOURCE = "official_source"
    const val LOCAL_EVIDENCE = "local_evidence"
}

enum class ClaimCategory {
    PUBLIC_POLICY,
    DISASTER_WEATHER,
    ELECTION_GOVERNMENT,
    TRANSPORT,
    SCAM,
    HEALTH,
    OTHER,
}

enum class TimeSensitivity {
    EVERGREEN,
    RECENT,
    CURRENT,
    BREAKING,
}

enum class EvidenceMode {
    LIVE,
    LAST_SYNCED,
    LOCAL_ONLY,
    USER_PROVIDED,
    INSUFFICIENT,
}

enum class SourceType {
    OFFICIAL,
    FACT_CHECK,
    NEWS,
    AGGREGATOR,
    USER_PROVIDED,
    LOCAL_CACHE,
}

enum class TrustTier {
    OFFICIAL,
    VERIFIED_FACT_CHECK,
    REPUTABLE_NEWS,
    DISCOVERY_ONLY,
    USER_PROVIDED,
}

enum class EvidenceStance {
    SUPPORTS,
    REFUTES,
    UNCLEAR,
    BACKGROUND,
}

enum class ToolStatus {
    SUCCESS,
    PARTIAL,
    EMPTY,
    BLOCKED,
    ERROR,
}

data class VerificationToolCall(
    val toolName: String,
    val query: String = "",
    val url: String = "",
    val maxResults: Int = 5,
    val preferredDomains: List<String> = emptyList(),
) {
    fun inputSummary(): String = query.ifBlank { url }
}

data class VerificationPlan(
    val claim: String,
    val language: String = "unknown",
    val claimCategory: ClaimCategory = ClaimCategory.OTHER,
    val timeSensitivity: TimeSensitivity = TimeSensitivity.RECENT,
    val requiresLiveEvidence: Boolean = true,
    val requiredFreshnessHours: Int? = 48,
    val toolCalls: List<VerificationToolCall> = emptyList(),
    val preferredSources: List<String> = emptyList(),
    val abstentionReasonIfNoEvidence: String =
        "I could not find enough fresh evidence from the allowed sources to verify this claim.",
)

data class EvidenceRecord(
    val id: Long = 0,
    val sourceName: String,
    val sourceType: SourceType,
    val url: String? = null,
    val canonicalUrl: String? = url,
    val title: String,
    val publishedAt: Long? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
    val trustTier: TrustTier,
    val stance: EvidenceStance = EvidenceStance.UNCLEAR,
    val snippet: String,
    val fullText: String? = null,
    val contentHash: String = "${canonicalUrl ?: url ?: title}|${snippet.take(120)}".hashCode().toString(),
)

data class VerificationToolResult(
    val toolName: String,
    val input: String,
    val status: ToolStatus,
    val records: List<EvidenceRecord> = emptyList(),
    val rawSummary: String = "",
    val latencyMs: Long = 0,
    val error: String? = null,
)

data class VerificationReport(
    val plan: VerificationPlan,
    val toolResults: List<VerificationToolResult> = emptyList(),
    val evidenceMode: EvidenceMode,
) {
    fun allEvidence(): List<EvidenceRecord> = toolResults.flatMap { it.records }

    fun freshestFetchedAt(): Long? = allEvidence().maxOfOrNull { it.fetchedAt }
}
