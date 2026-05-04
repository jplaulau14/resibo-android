package com.patslaurel.resibo.verification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationPolicyTest {
    @Test
    fun `adds local evidence as first call`() {
        val plan =
            VerificationPlan(
                claim = "Libre MRT",
                toolCalls =
                    listOf(
                        VerificationToolCall(
                            toolName = VerificationToolNames.PERPLEXITY_DISCOVERY,
                            query = "Libre MRT",
                        ),
                    ),
            )

        val approved = VerificationPolicy.approve(plan, networkAvailable = true)

        assertEquals(VerificationToolNames.LOCAL_EVIDENCE, approved.toolCalls.first().toolName)
        assertEquals(VerificationToolNames.PERPLEXITY_DISCOVERY, approved.toolCalls[1].toolName)
    }

    @Test
    fun `transport current claim receives official source call`() {
        val plan =
            VerificationPlan(
                claim = "Libre na ang MRT bukas",
                claimCategory = ClaimCategory.TRANSPORT,
                timeSensitivity = TimeSensitivity.CURRENT,
                toolCalls = emptyList(),
            )

        val approved = VerificationPolicy.approve(plan, networkAvailable = true)

        assertTrue(approved.toolCalls.any { it.toolName == VerificationToolNames.OFFICIAL_SOURCE })
        val official = approved.toolCalls.first { it.toolName == VerificationToolNames.OFFICIAL_SOURCE }
        assertTrue(official.preferredDomains.contains("dotr.gov.ph"))
        assertTrue(official.preferredDomains.contains("mrt3.com"))
    }

    @Test
    fun `offline mode removes online tools and marks live evidence unavailable`() {
        val plan =
            VerificationPlan(
                claim = "PAGASA warning",
                requiresLiveEvidence = true,
                toolCalls =
                    listOf(
                        VerificationToolCall(toolName = VerificationToolNames.PERPLEXITY_DISCOVERY, query = "PAGASA warning"),
                        VerificationToolCall(toolName = VerificationToolNames.CLAIM_REVIEW, query = "PAGASA warning"),
                    ),
            )

        val approved = VerificationPolicy.approve(plan, networkAvailable = false)

        assertEquals(1, approved.toolCalls.size)
        assertEquals(VerificationToolNames.LOCAL_EVIDENCE, approved.toolCalls[0].toolName)
        assertTrue(approved.abstentionReasonIfNoEvidence.contains("fresh online evidence"))
    }

    @Test
    fun `offline mode removes policy generated official source calls`() {
        val plan =
            VerificationPlan(
                claim = "Libre na ang MRT bukas",
                claimCategory = ClaimCategory.TRANSPORT,
                timeSensitivity = TimeSensitivity.CURRENT,
                requiresLiveEvidence = true,
                toolCalls = emptyList(),
            )

        val approved = VerificationPolicy.approve(plan, networkAvailable = false)

        assertEquals(1, approved.toolCalls.size)
        assertEquals(VerificationToolNames.LOCAL_EVIDENCE, approved.toolCalls[0].toolName)
        assertTrue(approved.abstentionReasonIfNoEvidence.contains("fresh online evidence"))
    }

    @Test
    fun `drops unknown tool calls and limits call count`() {
        val plan =
            VerificationPlan(
                claim = "test",
                toolCalls =
                    listOf(
                        VerificationToolCall("bad_tool", query = "bad"),
                        VerificationToolCall(VerificationToolNames.PERPLEXITY_DISCOVERY, query = "one"),
                        VerificationToolCall(VerificationToolNames.CLAIM_REVIEW, query = "two"),
                        VerificationToolCall(VerificationToolNames.OFFICIAL_SOURCE, query = "three"),
                        VerificationToolCall(VerificationToolNames.PERPLEXITY_DISCOVERY, query = "four"),
                    ),
            )

        val approved = VerificationPolicy.approve(plan, networkAvailable = true)

        assertFalse(approved.toolCalls.any { it.toolName == "bad_tool" })
        assertTrue(approved.toolCalls.size <= 4)
    }
}
