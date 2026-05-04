package com.patslaurel.resibo.verification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationModelsTest {
    @Test
    fun `tool call input summary prefers query then url then empty`() {
        assertEquals(
            "MRT free ride Philippines 2026",
            VerificationToolCall(
                toolName = VerificationToolNames.PERPLEXITY_DISCOVERY,
                query = "MRT free ride Philippines 2026",
            ).inputSummary(),
        )
        assertEquals(
            "https://dotr.gov.ph",
            VerificationToolCall(
                toolName = VerificationToolNames.OFFICIAL_SOURCE,
                url = "https://dotr.gov.ph",
            ).inputSummary(),
        )
        assertEquals(
            "",
            VerificationToolCall(toolName = VerificationToolNames.LOCAL_EVIDENCE).inputSummary(),
        )
    }

    @Test
    fun `verification report computes freshest fetched timestamp`() {
        val old =
            EvidenceRecord(
                sourceName = "MRT-3",
                sourceType = SourceType.OFFICIAL,
                url = "https://dotr.gov.ph/a",
                title = "Old",
                fetchedAt = 100,
                trustTier = TrustTier.OFFICIAL,
                stance = EvidenceStance.BACKGROUND,
                snippet = "Old source",
            )
        val fresh = old.copy(title = "Fresh", fetchedAt = 300)

        val report =
            VerificationReport(
                plan = VerificationPlan(claim = "test"),
                toolResults =
                    listOf(
                        VerificationToolResult(
                            toolName = VerificationToolNames.LOCAL_EVIDENCE,
                            input = "test",
                            status = ToolStatus.SUCCESS,
                            records = listOf(old, fresh),
                        ),
                    ),
                evidenceMode = EvidenceMode.LIVE,
            )

        assertEquals(300, report.freshestFetchedAt())
        assertTrue(report.allEvidence().contains(fresh))
    }
}
