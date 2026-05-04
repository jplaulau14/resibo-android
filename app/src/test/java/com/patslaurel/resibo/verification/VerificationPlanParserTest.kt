package com.patslaurel.resibo.verification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationPlanParserTest {
    @Test
    fun `parses strict planner json`() {
        val json =
            """
            {
              "claim": "Libre na ang MRT simula Mayo 2026",
              "language": "Taglish",
              "claim_category": "transport",
              "time_sensitivity": "current",
              "requires_live_evidence": true,
              "required_freshness_hours": 24,
              "preferred_sources": ["DOTr", "MRT-3"],
              "abstention_reason_if_no_evidence": "Needs fresh DOTr or MRT-3 confirmation.",
              "tool_calls": [
                {
                  "tool_name": "official_source",
                  "query": "MRT free ride May 2026 DOTr MRT-3",
                  "max_results": 3,
                  "preferred_domains": ["dotr.gov.ph", "mrt3.com"]
                },
                {
                  "tool_name": "claim_review",
                  "query": "MRT free ride May 2026 fact check Philippines"
                }
              ]
            }
            """.trimIndent()

        val plan = VerificationPlanParser.parse(json, fallbackClaim = "fallback")

        assertEquals("Libre na ang MRT simula Mayo 2026", plan.claim)
        assertEquals("Taglish", plan.language)
        assertEquals(ClaimCategory.TRANSPORT, plan.claimCategory)
        assertEquals(TimeSensitivity.CURRENT, plan.timeSensitivity)
        assertTrue(plan.requiresLiveEvidence)
        assertEquals(24, plan.requiredFreshnessHours)
        assertEquals(listOf("DOTr", "MRT-3"), plan.preferredSources)
        assertEquals(2, plan.toolCalls.size)
        assertEquals(VerificationToolNames.OFFICIAL_SOURCE, plan.toolCalls[0].toolName)
        assertEquals(listOf("dotr.gov.ph", "mrt3.com"), plan.toolCalls[0].preferredDomains)
    }

    @Test
    fun `extracts json object from markdown fenced output`() {
        val output =
            """
            Here is the plan:
            ```json
            {"claim":"PAGASA declared Signal No. 5 in Manila","claim_category":"disaster_weather","time_sensitivity":"breaking","tool_calls":[]}
            ```
            """.trimIndent()

        val plan = VerificationPlanParser.parse(output, fallbackClaim = "fallback")

        assertEquals("PAGASA declared Signal No. 5 in Manila", plan.claim)
        assertEquals(ClaimCategory.DISASTER_WEATHER, plan.claimCategory)
        assertEquals(TimeSensitivity.BREAKING, plan.timeSensitivity)
    }

    @Test
    fun `falls back to safe plan when json is invalid`() {
        val plan = VerificationPlanParser.parse("not json", fallbackClaim = "Totoo ba ito?")

        assertEquals("Totoo ba ito?", plan.claim)
        assertEquals(ClaimCategory.OTHER, plan.claimCategory)
        assertTrue(plan.requiresLiveEvidence)
        assertFalse(plan.toolCalls.isEmpty())
        assertEquals(VerificationToolNames.LOCAL_EVIDENCE, plan.toolCalls[0].toolName)
        assertEquals(VerificationToolNames.PERPLEXITY_DISCOVERY, plan.toolCalls[1].toolName)
    }
}
