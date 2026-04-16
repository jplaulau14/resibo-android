package com.patslaurel.resibo.factcheck

/**
 * Formats fact-check API results into a context block that gets prepended
 * to the Gemma prompt, giving the model grounded evidence to cite.
 */
object EvidenceInjector {
    /**
     * Build a context block from fact-check results. Returns empty string
     * if no results available (model runs without evidence context).
     */
    fun buildContext(results: List<FactCheckResult>): String {
        if (results.isEmpty()) return ""

        val entries =
            results.joinToString("\n\n") { r ->
                buildString {
                    append("Source: ${r.publisherName} (${r.publisherSite})")
                    if (r.reviewDate.isNotBlank()) append(" — ${r.reviewDate.take(10)}")
                    append("\n")
                    append("Claim checked: ${r.claimText}")
                    if (r.claimant.isNotBlank()) append(" (claimed by: ${r.claimant})")
                    append("\n")
                    append("Rating: **${r.rating}**")
                    append("\n")
                    append("Full article: ${r.reviewUrl}")
                }
            }

        return buildString {
            append("## Fact-check evidence from verified sources\n\n")
            append("The following fact-checks were found from IFCN-certified organizations. ")
            append("Use these as primary evidence in your Note. Cite them by name and date.\n\n")
            append(entries)
            append("\n\n---\n\n")
        }
    }
}
