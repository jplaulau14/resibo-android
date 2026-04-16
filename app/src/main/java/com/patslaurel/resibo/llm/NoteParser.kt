package com.patslaurel.resibo.llm

/**
 * Extracts structured fields from the Markdown Note that Gemma generates
 * per the `triage_system.md` prompt template.
 *
 * Expected response format (Markdown bold headings):
 *   **Claim**: ...
 *   **Language**: ...
 *   **Check-worthiness**: ...
 *   **Domain**: ...
 *   **What I can say offline**: ...
 *   **What would need verification**: ...
 *
 * Parser is intentionally lenient — if a field is missing or the model deviates
 * from the format, the corresponding parsed field falls back to a default.
 */
object NoteParser {
    fun parse(response: String): ParsedNote {
        val sections = extractSections(response)
        return ParsedNote(
            claim = sections["claim"] ?: "",
            language = normalizeLanguage(sections["language"] ?: "unknown"),
            checkWorthiness = normalizeCheckWorthiness(sections["check-worthiness"] ?: "unknown"),
            domain = normalizeDomain(sections["domain"] ?: "unknown"),
            offlineAssessment =
                sections["what i can say offline"]
                    ?: sections["what i can say from training alone"]
                    ?: "",
            verificationNeeded = sections["what would need verification"] ?: "",
        )
    }

    /**
     * Pull sections by scanning for `**Heading**:` patterns.
     * Returns a map of lowercase-heading → content-after-colon.
     */
    private fun extractSections(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val pattern = Regex("""\*\*([^*]+)\*\*\s*:\s*(.*)""")

        val lines = text.lines()
        var currentKey: String? = null
        val currentValue = StringBuilder()

        for (line in lines) {
            val match = pattern.find(line)
            if (match != null) {
                if (currentKey != null) {
                    result[currentKey] = currentValue.toString().trim()
                }
                currentKey = match.groupValues[1].trim().lowercase()
                currentValue.clear()
                currentValue.append(match.groupValues[2].trim())
            } else if (currentKey != null && line.isNotBlank()) {
                currentValue.append("\n").append(line.trim())
            }
        }
        if (currentKey != null) {
            result[currentKey] = currentValue.toString().trim()
        }
        return result
    }

    private fun normalizeLanguage(raw: String): String {
        val lower = raw.lowercase().trim().removeSuffix(".")
        return when {
            "tagalog" in lower && "english" in lower -> "Taglish"
            "taglish" in lower -> "Taglish"
            "tagalog" in lower || "filipino" in lower -> "Tagalog"
            "cebuano" in lower || "bisaya" in lower -> "Cebuano"
            "english" in lower -> "English"
            else -> raw.trim().removeSuffix(".")
        }
    }

    private fun normalizeCheckWorthiness(raw: String): String {
        val lower = raw.lowercase().trim().removeSuffix(".")
        return when {
            "high" in lower -> "high"
            "medium" in lower -> "medium"
            "low" in lower -> "low"
            else -> raw.trim().removeSuffix(".")
        }
    }

    private fun normalizeDomain(raw: String): String {
        val lower = raw.lowercase().trim().removeSuffix(".")
        return when {
            "politi" in lower -> "political"
            "health" in lower || "medical" in lower -> "health"
            "econom" in lower || "financ" in lower -> "economic"
            "cultur" in lower -> "cultural"
            "diaspora" in lower || "ofw" in lower -> "diaspora"
            else -> raw.trim().removeSuffix(".")
        }
    }
}

data class ParsedNote(
    val claim: String,
    val language: String,
    val checkWorthiness: String,
    val domain: String,
    val offlineAssessment: String,
    val verificationNeeded: String,
)
