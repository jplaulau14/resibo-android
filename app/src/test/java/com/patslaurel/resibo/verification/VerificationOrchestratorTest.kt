package com.patslaurel.resibo.verification

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationOrchestratorTest {
    @Test
    fun `executes approved tools in order and returns live when online evidence exists`() =
        runBlocking {
            val onlineRecord = record(SourceType.FACT_CHECK, "Online evidence")
            val localRecord = record(SourceType.LOCAL_CACHE, "Cached evidence")
            val store = FakeEvidenceStore()
            val firstTool = FakeTool("online", records = listOf(onlineRecord))
            val secondTool = FakeTool("local", records = listOf(localRecord))
            val orchestrator = VerificationOrchestrator(listOf(secondTool, firstTool), store)
            val plan =
                VerificationPlan(
                    claim = "MRT rides are free today",
                    toolCalls =
                        listOf(
                            VerificationToolCall(toolName = "online", query = "online query"),
                            VerificationToolCall(toolName = "local", query = "local query"),
                        ),
                )

            val report = orchestrator.verify(plan)

            assertEquals(EvidenceMode.LIVE, report.evidenceMode)
            assertEquals(listOf("online", "local"), report.toolResults.map { it.toolName })
            assertEquals(listOf("online query"), firstTool.executedInputs)
            assertEquals(listOf("local query"), secondTool.executedInputs)
            assertEquals(listOf(onlineRecord), store.savedRecords)
        }

    @Test
    fun `returns insufficient when approved tools produce no records`() =
        runBlocking {
            val orchestrator =
                VerificationOrchestrator(
                    listOf(FakeTool("empty", records = emptyList())),
                    FakeEvidenceStore(),
                )
            val plan =
                VerificationPlan(
                    claim = "No evidence claim",
                    toolCalls = listOf(VerificationToolCall(toolName = "empty", query = "missing")),
                )

            val report = orchestrator.verify(plan)

            assertEquals(EvidenceMode.INSUFFICIENT, report.evidenceMode)
            assertTrue(report.allEvidence().isEmpty())
        }

    @Test
    fun `missing tool returns blocked result without preventing later tools`() =
        runBlocking {
            val onlineRecord = record(SourceType.OFFICIAL, "Official evidence")
            val availableTool = FakeTool("available", records = listOf(onlineRecord))
            val orchestrator = VerificationOrchestrator(listOf(availableTool), FakeEvidenceStore())
            val plan =
                VerificationPlan(
                    claim = "Tool unavailable claim",
                    toolCalls =
                        listOf(
                            VerificationToolCall(toolName = "missing", query = "missing query"),
                            VerificationToolCall(toolName = "available", query = "available query"),
                        ),
                )

            val report = orchestrator.verify(plan)

            assertEquals(listOf("missing", "available"), report.toolResults.map { it.toolName })
            assertEquals(ToolStatus.BLOCKED, report.toolResults[0].status)
            assertEquals("missing query", report.toolResults[0].input)
            assertEquals("No verification tool registered for missing", report.toolResults[0].error)
            assertEquals(ToolStatus.SUCCESS, report.toolResults[1].status)
            assertEquals(EvidenceMode.LIVE, report.evidenceMode)
        }

    @Test
    fun `does not store local cache evidence`() =
        runBlocking {
            val localRecord = record(SourceType.LOCAL_CACHE, "Cached evidence")
            val userRecord = record(SourceType.USER_PROVIDED, "User evidence")
            val store = FakeEvidenceStore()
            val orchestrator =
                VerificationOrchestrator(
                    listOf(FakeTool("mixed", records = listOf(localRecord, userRecord))),
                    store,
                )
            val plan =
                VerificationPlan(
                    claim = "Local evidence only",
                    toolCalls = listOf(VerificationToolCall(toolName = "mixed", query = "cache query")),
                )

            val report = orchestrator.verify(plan)

            assertEquals(EvidenceMode.LOCAL_ONLY, report.evidenceMode)
            assertEquals(listOf(userRecord), store.savedRecords)
        }

    @Test
    fun `store failure still returns report with tool results and evidence mode`() =
        runBlocking {
            val onlineRecord = record(SourceType.FACT_CHECK, "Online evidence")
            val tool = FakeTool("online", records = listOf(onlineRecord))
            val orchestrator =
                VerificationOrchestrator(
                    listOf(tool),
                    FailingEvidenceStore(),
                )
            val plan =
                VerificationPlan(
                    claim = "Persistence failure claim",
                    toolCalls = listOf(VerificationToolCall(toolName = "online", query = "online query")),
                )

            val report = orchestrator.verify(plan)

            assertEquals(EvidenceMode.LIVE, report.evidenceMode)
            assertEquals(listOf("online"), report.toolResults.map { it.toolName })
            assertEquals(ToolStatus.SUCCESS, report.toolResults[0].status)
            assertEquals(listOf(onlineRecord), report.toolResults[0].records)
            assertEquals(listOf("online query"), tool.executedInputs)
        }

    private class FakeEvidenceStore : EvidenceStore {
        val savedRecords = mutableListOf<EvidenceRecord>()

        override suspend fun saveEvidence(records: List<EvidenceRecord>) {
            savedRecords += records
        }
    }

    private class FailingEvidenceStore : EvidenceStore {
        override suspend fun saveEvidence(records: List<EvidenceRecord>) =
            throw IllegalStateException("database unavailable")
    }

    private class FakeTool(
        override val name: String,
        private val records: List<EvidenceRecord>,
    ) : VerificationTool {
        val executedInputs = mutableListOf<String>()

        override suspend fun execute(call: VerificationToolCall): VerificationToolResult {
            executedInputs += call.inputSummary()
            return VerificationToolResult(
                toolName = name,
                input = call.inputSummary(),
                status = if (records.isEmpty()) ToolStatus.EMPTY else ToolStatus.SUCCESS,
                records = records,
            )
        }
    }

    private fun record(
        sourceType: SourceType,
        title: String,
    ): EvidenceRecord =
        EvidenceRecord(
            sourceName = "Source",
            sourceType = sourceType,
            title = title,
            trustTier =
                when (sourceType) {
                    SourceType.OFFICIAL -> TrustTier.OFFICIAL
                    SourceType.FACT_CHECK -> TrustTier.VERIFIED_FACT_CHECK
                    SourceType.USER_PROVIDED -> TrustTier.USER_PROVIDED
                    SourceType.LOCAL_CACHE -> TrustTier.REPUTABLE_NEWS
                    else -> TrustTier.DISCOVERY_ONLY
                },
            snippet = "Snippet for $title",
        )
}
