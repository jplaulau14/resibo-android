package com.patslaurel.resibo.verification

object VerificationPolicy {
    private val allowedTools =
        setOf(
            VerificationToolNames.LOCAL_EVIDENCE,
            VerificationToolNames.PERPLEXITY_DISCOVERY,
            VerificationToolNames.CLAIM_REVIEW,
            VerificationToolNames.OFFICIAL_SOURCE,
        )

    fun approve(
        plan: VerificationPlan,
        networkAvailable: Boolean,
    ): VerificationPlan {
        val calls = mutableListOf<VerificationToolCall>()

        calls.add(
            VerificationToolCall(
                toolName = VerificationToolNames.LOCAL_EVIDENCE,
                query = plan.claim,
                maxResults = 5,
            ),
        )

        calls.addAll(requiredOfficialCalls(plan))

        plan.toolCalls
            .filter { it.toolName in allowedTools }
            .filter { networkAvailable || it.toolName == VerificationToolNames.LOCAL_EVIDENCE }
            .forEach { calls.add(it.withPolicyDefaults(plan)) }

        if (networkAvailable && calls.none { it.toolName != VerificationToolNames.LOCAL_EVIDENCE }) {
            calls.add(
                VerificationToolCall(
                    toolName = VerificationToolNames.PERPLEXITY_DISCOVERY,
                    query = plan.claim,
                    maxResults = 5,
                ),
            )
        }

        val distinctCalls =
            calls
                .distinctBy { "${it.toolName}|${it.query}|${it.url}|${it.preferredDomains.joinToString(",")}" }
                .take(4)

        return plan.copy(
            toolCalls = distinctCalls,
            abstentionReasonIfNoEvidence =
                if (!networkAvailable && plan.requiresLiveEvidence) {
                    "This claim needs fresh online evidence, but only local evidence is available right now."
                } else {
                    plan.abstentionReasonIfNoEvidence
                },
        )
    }

    private fun requiredOfficialCalls(plan: VerificationPlan): List<VerificationToolCall> {
        val domains =
            when (plan.claimCategory) {
                ClaimCategory.TRANSPORT -> listOf("dotr.gov.ph", "mrt3.com", "lrmc.ph")
                ClaimCategory.DISASTER_WEATHER -> listOf("pagasa.dost.gov.ph", "ndrrmc.gov.ph")
                ClaimCategory.ELECTION_GOVERNMENT -> listOf("comelec.gov.ph", "officialgazette.gov.ph")
                ClaimCategory.PUBLIC_POLICY -> listOf("officialgazette.gov.ph", "gov.ph")
                ClaimCategory.HEALTH -> listOf("doh.gov.ph", "who.int")
                ClaimCategory.SCAM -> listOf("sec.gov.ph", "bsp.gov.ph", "dict.gov.ph")
                ClaimCategory.OTHER -> emptyList()
            }

        val categoryRequiresOfficial =
            plan.claimCategory in
                setOf(
                    ClaimCategory.TRANSPORT,
                    ClaimCategory.DISASTER_WEATHER,
                    ClaimCategory.ELECTION_GOVERNMENT,
                    ClaimCategory.PUBLIC_POLICY,
                )
        val requiresOfficial =
            plan.timeSensitivity == TimeSensitivity.CURRENT ||
                plan.timeSensitivity == TimeSensitivity.BREAKING ||
                categoryRequiresOfficial

        return if (requiresOfficial && domains.isNotEmpty()) {
            listOf(
                VerificationToolCall(
                    toolName = VerificationToolNames.OFFICIAL_SOURCE,
                    query = plan.claim,
                    maxResults = 5,
                    preferredDomains = domains,
                ),
            )
        } else {
            emptyList()
        }
    }

    private fun VerificationToolCall.withPolicyDefaults(plan: VerificationPlan): VerificationToolCall =
        copy(
            query = query.ifBlank { plan.claim },
            maxResults = maxResults.coerceIn(1, 10),
        )
}
