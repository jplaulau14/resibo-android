package com.patslaurel.resibo.verification

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalEvidenceToolTest {
    @Test
    fun `cached evidence returns success and maps records as local cache`() =
        runBlocking {
            val fake =
                FakeEvidenceSearch(
                    listOf(
                        cachedRecord(
                            sourceName = "Vera Files",
                            sourceType = SourceType.FACT_CHECK,
                        ),
                    ),
                )
            val tool = LocalEvidenceTool(fake)

            val result =
                tool.execute(
                    VerificationToolCall(
                        toolName = VerificationToolNames.LOCAL_EVIDENCE,
                        query = "MRT free ride",
                        maxResults = 3,
                    ),
                )

            assertEquals(VerificationToolNames.LOCAL_EVIDENCE, result.toolName)
            assertEquals("MRT free ride", result.input)
            assertEquals(ToolStatus.SUCCESS, result.status)
            assertEquals(1, result.records.size)
            assertEquals(SourceType.LOCAL_CACHE, result.records[0].sourceType)
            assertEquals("Vera Files", result.records[0].sourceName)
            assertEquals("MRT free ride", fake.lastQuery)
            assertEquals(3, fake.lastLimit)
            assertTrue(result.latencyMs >= 0)
        }

    @Test
    fun `no cached evidence returns empty and zero records`() =
        runBlocking {
            val tool = LocalEvidenceTool(FakeEvidenceSearch(emptyList()))

            val result =
                tool.execute(
                    VerificationToolCall(
                        toolName = VerificationToolNames.LOCAL_EVIDENCE,
                        query = "MRT free ride",
                        maxResults = 3,
                    ),
                )

            assertEquals(ToolStatus.EMPTY, result.status)
            assertTrue(result.records.isEmpty())
        }

    @Test
    fun `search failure returns error result`() =
        runBlocking {
            val tool =
                LocalEvidenceTool(
                    FakeEvidenceSearch(error = IllegalStateException("cache unavailable")),
                )

            val result =
                tool.execute(
                    VerificationToolCall(
                        toolName = VerificationToolNames.LOCAL_EVIDENCE,
                        query = "MRT free ride",
                        maxResults = 3,
                    ),
                )

            assertEquals(ToolStatus.ERROR, result.status)
            assertTrue(result.records.isEmpty())
            assertEquals("MRT free ride", result.input)
            assertNotNull(result.error)
            assertTrue(result.latencyMs >= 0)
        }

    @Test
    fun `unsafe max results is clamped before searching local evidence`() =
        runBlocking {
            val fake =
                FakeEvidenceSearch(
                    listOf(
                        cachedRecord(title = "Cached title 1"),
                        cachedRecord(title = "Cached title 2"),
                        cachedRecord(title = "Cached title 3"),
                    ),
                )
            val tool = LocalEvidenceTool(fake)

            val result =
                tool.execute(
                    VerificationToolCall(
                        toolName = VerificationToolNames.LOCAL_EVIDENCE,
                        query = "MRT free ride",
                        maxResults = -1,
                    ),
                )

            assertEquals(1, fake.lastLimit)
            assertEquals(1, result.records.size)
            assertEquals("Cached title 1", result.records[0].title)
        }

    private class FakeEvidenceSearch(
        private val records: List<EvidenceRecord> = emptyList(),
        private val error: Throwable? = null,
    ) : EvidenceSearch {
        var lastQuery: String? = null
        var lastLimit: Int? = null

        override suspend fun searchEvidence(
            query: String,
            limit: Int,
        ): List<EvidenceRecord> {
            lastQuery = query
            lastLimit = limit
            error?.let { throw it }
            return records.take(limit)
        }
    }

    private fun cachedRecord(
        sourceName: String = "Local source",
        sourceType: SourceType = SourceType.NEWS,
        title: String = "Cached title",
    ): EvidenceRecord =
        EvidenceRecord(
            sourceName = sourceName,
            sourceType = sourceType,
            title = title,
            trustTier = TrustTier.REPUTABLE_NEWS,
            snippet = "Cached snippet",
        )
}
