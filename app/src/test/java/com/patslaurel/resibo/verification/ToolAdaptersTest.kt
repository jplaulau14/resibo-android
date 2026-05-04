package com.patslaurel.resibo.verification

import com.patslaurel.resibo.factcheck.FactCheckResult
import com.patslaurel.resibo.factcheck.PerplexityResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolAdaptersTest {
    @Test
    fun `perplexity result maps citations as discovery evidence`() {
        val result =
            PerplexityDiscoveryTool.mapResult(
                call =
                    VerificationToolCall(
                        toolName = VerificationToolNames.PERPLEXITY_DISCOVERY,
                        query = "MRT free ride",
                    ),
                result =
                    PerplexityResult(
                        text = "Search summary",
                        sources =
                            listOf(
                                FactCheckResult(
                                    claimText = "",
                                    claimant = "",
                                    rating = "",
                                    reviewUrl = "https://www.rappler.com/example",
                                    reviewTitle = "rappler.com",
                                    publisherName = "rappler.com",
                                    publisherSite = "https://www.rappler.com/example",
                                    reviewDate = "",
                                ),
                            ),
                    ),
                startedAt = 100,
                finishedAt = 250,
            )

        assertEquals(ToolStatus.SUCCESS, result.status)
        assertEquals(150, result.latencyMs)
        assertEquals(1, result.records.size)
        assertEquals(SourceType.AGGREGATOR, result.records[0].sourceType)
        assertEquals(TrustTier.DISCOVERY_ONLY, result.records[0].trustTier)
        assertEquals(EvidenceStance.BACKGROUND, result.records[0].stance)
        assertTrue(result.records[0].snippet.contains("Search summary"))
    }

    @Test
    fun `claim review maps as verified fact check evidence and false rating refutes`() {
        val result =
            ClaimReviewTool.mapResults(
                call =
                    VerificationToolCall(
                        toolName = VerificationToolNames.CLAIM_REVIEW,
                        query = "Marcos gold",
                    ),
                results =
                    listOf(
                        FactCheckResult(
                            claimText = "Marcos gold exists in Vatican",
                            claimant = "",
                            rating = "False",
                            reviewUrl = "https://verafiles.org/example",
                            reviewTitle = "Fact check title",
                            publisherName = "Vera Files",
                            publisherSite = "verafiles.org",
                            reviewDate = "2024-01-02T00:00:00Z",
                        ),
                    ),
                startedAt = 50,
                finishedAt = 70,
            )

        assertEquals(ToolStatus.SUCCESS, result.status)
        assertEquals(SourceType.FACT_CHECK, result.records[0].sourceType)
        assertEquals(TrustTier.VERIFIED_FACT_CHECK, result.records[0].trustTier)
        assertEquals(EvidenceStance.REFUTES, result.records[0].stance)
        assertEquals(1704153600000L, result.records[0].publishedAt)
        assertTrue(result.records[0].snippet.contains("Rating: False"))
    }

    @Test
    fun `official source keeps only allowlisted domains as official`() {
        val result =
            OfficialSourceTool.mapPerplexityResult(
                call =
                    VerificationToolCall(
                        toolName = VerificationToolNames.OFFICIAL_SOURCE,
                        query = "MRT advisory",
                        preferredDomains = listOf("dotr.gov.ph"),
                    ),
                result =
                    PerplexityResult(
                        text = "Official search",
                        sources =
                            listOf(
                                FactCheckResult(
                                    claimText = "",
                                    claimant = "",
                                    rating = "",
                                    reviewUrl = "https://news.dotr.gov.ph/advisory",
                                    reviewTitle = "DOTr advisory",
                                    publisherName = "DOTr",
                                    publisherSite = "dotr.gov.ph",
                                    reviewDate = "",
                                ),
                                FactCheckResult(
                                    claimText = "",
                                    claimant = "",
                                    rating = "",
                                    reviewUrl = "https://randomblog.example/news",
                                    reviewTitle = "Random blog",
                                    publisherName = "Random blog",
                                    publisherSite = "randomblog.example",
                                    reviewDate = "",
                                ),
                                FactCheckResult(
                                    claimText = "",
                                    claimant = "",
                                    rating = "",
                                    reviewUrl = "https://fakedotr.gov.ph/news",
                                    reviewTitle = "Lookalike domain",
                                    publisherName = "Lookalike",
                                    publisherSite = "fakedotr.gov.ph",
                                    reviewDate = "",
                                ),
                            ),
                    ),
                startedAt = 10,
                finishedAt = 20,
            )

        assertEquals(3, result.records.size)
        assertEquals(SourceType.OFFICIAL, result.records[0].sourceType)
        assertEquals(TrustTier.OFFICIAL, result.records[0].trustTier)
        assertEquals(SourceType.AGGREGATOR, result.records[1].sourceType)
        assertEquals(TrustTier.DISCOVERY_ONLY, result.records[1].trustTier)
        assertEquals(SourceType.AGGREGATOR, result.records[2].sourceType)
        assertEquals(TrustTier.DISCOVERY_ONLY, result.records[2].trustTier)
    }
}
