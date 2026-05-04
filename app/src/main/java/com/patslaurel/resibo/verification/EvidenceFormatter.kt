package com.patslaurel.resibo.verification

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object EvidenceFormatter {
    private val utcFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun format(report: VerificationReport): String {
        val evidence = report.allEvidence()

        return buildString {
            appendLine("## Verification evidence")
            appendLine("Evidence mode: ${report.evidenceMode.name}")
            appendLine("Freshest fetched at: ${report.freshestFetchedAt()?.toUtcString() ?: "unavailable"}")
            appendLine("Claim category: ${report.plan.claimCategory.name}")
            appendLine("Time sensitivity: ${report.plan.timeSensitivity.name}")
            appendLine()

            if (evidence.isEmpty()) {
                appendLine("No sufficient evidence was found.")
                appendLine("Abstention reason: ${report.plan.abstentionReasonIfNoEvidence.asEvidenceField()}")
                appendLine("Do not answer from model memory. Abstain unless sufficient grounded evidence is provided.")
                return@buildString
            }

            evidence.forEachIndexed { index, record ->
                appendLine("### Evidence ${index + 1}")
                appendLine("Source: ${record.sourceName.asEvidenceField()}")
                appendLine("Source type: ${record.sourceType.name}")
                appendLine("Trust tier: ${record.trustTier.name}")
                appendLine("Stance: ${record.stance.name}")
                appendLine("Title: ${record.title.asEvidenceField()}")
                record.url?.let { appendLine("URL: ${it.asEvidenceField()}") }
                record.publishedAt?.let { appendLine("Published at: ${it.toUtcString()}") }
                appendLine("Fetched at: ${record.fetchedAt.toUtcString()}")
                appendLine("Snippet: ${record.snippet.asEvidenceField()}")
                appendLine()
            }

            appendLine("Use only the evidence above. Cite the relevant sources and abstain when the evidence is insufficient.")
        }
    }

    private fun Long.toUtcString(): String =
        synchronized(utcFormat) {
            utcFormat.format(Date(this))
        }

    private fun String.asEvidenceField(): String =
        replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
}
