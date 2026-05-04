package com.patslaurel.resibo.verification

import org.junit.Assert.assertEquals
import org.junit.Test

class EvidenceRecordMapperTest {
    @Test
    fun roundTripPreservesPopulatedEvidenceRecord() {
        val record =
            EvidenceRecord(
                id = 42L,
                sourceName = "PAGASA",
                sourceType = SourceType.OFFICIAL,
                url = "https://bagong.pagasa.dost.gov.ph/weather/weather-advisory",
                canonicalUrl = "https://pagasa.dost.gov.ph/weather-advisory",
                title = "Weather Advisory No. 1",
                publishedAt = 1_714_553_600_000L,
                fetchedAt = 1_714_557_200_000L,
                trustTier = TrustTier.OFFICIAL,
                stance = EvidenceStance.SUPPORTS,
                snippet = "PAGASA issued a weather advisory for affected regions.",
                fullText = "Full advisory text from PAGASA.",
                contentHash = "pagasa-weather-advisory-1",
            )

        val roundTripped = record.toEntity().toEvidenceRecord()

        assertEquals(record, roundTripped)
    }
}
