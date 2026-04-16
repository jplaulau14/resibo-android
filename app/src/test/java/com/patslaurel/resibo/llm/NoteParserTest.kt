package com.patslaurel.resibo.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteParserTest {
    private val sampleResponse =
        """
        **Claim**: Ang bakuna sa COVID ay nagiging sanhi ng autism.

        **Language**: Tagalog.

        **Check-worthiness**: high.

        **Domain**: health.

        **What I can say offline**: Ito ay isang claim na paulit-ulit nang na-debunk ng mga health authorities tulad ng WHO at CDC. Walang siyentipikong ebidensya na nag-uugnay ng bakuna sa autism. Ang pag-aaral ni Andrew Wakefield na nagpasimula ng ganitong claim ay na-retract na.

        **What would need verification**:
        - Specific DOH (Department of Health) statement on COVID vaccines and autism
        - Latest peer-reviewed studies from Philippine medical journals
        - Current WHO position paper on COVID-19 vaccine safety
        """.trimIndent()

    @Test
    fun `parses claim correctly`() {
        val parsed = NoteParser.parse(sampleResponse)
        assertEquals("Ang bakuna sa COVID ay nagiging sanhi ng autism.", parsed.claim)
    }

    @Test
    fun `normalizes language`() {
        val parsed = NoteParser.parse(sampleResponse)
        assertEquals("Tagalog", parsed.language)
    }

    @Test
    fun `normalizes check-worthiness`() {
        val parsed = NoteParser.parse(sampleResponse)
        assertEquals("high", parsed.checkWorthiness)
    }

    @Test
    fun `normalizes domain`() {
        val parsed = NoteParser.parse(sampleResponse)
        assertEquals("health", parsed.domain)
    }

    @Test
    fun `extracts multi-line offline assessment`() {
        val parsed = NoteParser.parse(sampleResponse)
        assert(parsed.offlineAssessment.contains("WHO")) { "Should contain WHO reference" }
        assert(parsed.offlineAssessment.contains("Wakefield")) { "Should contain Wakefield reference" }
    }

    @Test
    fun `extracts verification needed`() {
        val parsed = NoteParser.parse(sampleResponse)
        assert(parsed.verificationNeeded.contains("DOH")) { "Should mention DOH" }
        assert(parsed.verificationNeeded.contains("WHO")) { "Should mention WHO" }
    }

    @Test
    fun `handles Taglish language detection`() {
        val response = "**Claim**: Test\n**Language**: Taglish.\n**Check-worthiness**: low.\n**Domain**: other."
        val parsed = NoteParser.parse(response)
        assertEquals("Taglish", parsed.language)
    }

    @Test
    fun `handles missing fields gracefully`() {
        val parsed = NoteParser.parse("Some random text without any structure")
        assertEquals("", parsed.claim)
        assertEquals("unknown", parsed.language)
        assertEquals("unknown", parsed.checkWorthiness)
        assertEquals("unknown", parsed.domain)
    }

    @Test
    fun `handles Cebuano language`() {
        val response = "**Claim**: Test\n**Language**: Cebuano.\n**Check-worthiness**: medium.\n**Domain**: political."
        val parsed = NoteParser.parse(response)
        assertEquals("Cebuano", parsed.language)
        assertEquals("medium", parsed.checkWorthiness)
        assertEquals("political", parsed.domain)
    }
}
