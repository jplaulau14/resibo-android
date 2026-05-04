package com.patslaurel.resibo.data

import com.patslaurel.resibo.data.dao.EvidenceRecordDao
import com.patslaurel.resibo.data.dao.NoteDao
import com.patslaurel.resibo.data.dao.SeenPostDao
import com.patslaurel.resibo.data.dao.SourceDao
import com.patslaurel.resibo.data.dao.TraceStepDao
import com.patslaurel.resibo.data.entity.NoteEntity
import com.patslaurel.resibo.data.entity.SeenPostEntity
import com.patslaurel.resibo.data.entity.SourceEntity
import com.patslaurel.resibo.data.entity.TraceStepEntity
import com.patslaurel.resibo.hash.PerceptualHash
import com.patslaurel.resibo.verification.EvidenceRecord
import com.patslaurel.resibo.verification.EvidenceSearch
import com.patslaurel.resibo.verification.EvidenceStore
import com.patslaurel.resibo.verification.toEntity
import com.patslaurel.resibo.verification.toEvidenceRecord
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for all Note persistence operations. Coroutine-based —
 * all functions are `suspend` or return `Flow` and must be called from a
 * coroutine scope (ViewModel / lifecycleScope).
 */
@Singleton
class NoteRepository
    @Inject
    constructor(
        private val noteDao: NoteDao,
        private val sourceDao: SourceDao,
        private val traceStepDao: TraceStepDao,
        private val seenPostDao: SeenPostDao,
        private val evidenceRecordDao: EvidenceRecordDao,
    ) : EvidenceSearch,
        EvidenceStore {
        /** Insert a Note with its sources and trace steps in one transaction. */
        suspend fun saveNote(
            note: NoteEntity,
            sources: List<SourceEntity> = emptyList(),
            traceSteps: List<TraceStepEntity> = emptyList(),
        ): Long {
            val noteId = noteDao.insert(note)

            if (sources.isNotEmpty()) {
                sourceDao.insertAll(sources.map { it.copy(noteId = noteId) })
            }
            if (traceSteps.isNotEmpty()) {
                traceStepDao.insertAll(traceSteps.map { it.copy(noteId = noteId) })
            }

            note.perceptualHash?.let { hash ->
                seenPostDao.upsert(
                    SeenPostEntity(perceptualHash = hash, noteId = noteId),
                )
            }

            return noteId
        }

        /** All Notes, newest first. Observed as a Flow for live UI updates. */
        fun observeAll(): Flow<List<NoteEntity>> = noteDao.getAllOrdered()

        /** Single Note by ID with its sources and trace steps. */
        suspend fun getNoteWithDetails(noteId: Long): NoteWithDetails? {
            val note = noteDao.getById(noteId) ?: return null
            return NoteWithDetails(
                note = note,
                sources = sourceDao.getForNote(noteId),
                traceSteps = traceStepDao.getForNote(noteId),
            )
        }

        /** Recent Notes for the history screen. */
        suspend fun getRecent(limit: Int = 20): List<NoteEntity> = noteDao.getRecent(limit)

        /** Total Note count (for stats / settings). */
        suspend fun count(): Int = noteDao.count()

        /**
         * Look up whether we've seen this image before (Community Notes cache-hit).
         * Searches by exact hash first, then falls back to Hamming distance ≤ [threshold]
         * across all stored hashes (brute-force on small sets; add an index or VP-tree
         * if the table grows past ~10k rows).
         *
         * @return the matching [SeenPostEntity] and its associated Note ID, or null.
         */
        suspend fun findNearDuplicate(
            hash: Long,
            threshold: Int = 5,
        ): SeenPostEntity? {
            val hex = PerceptualHash.toHex(hash)

            seenPostDao.findByExactHash(hex)?.let { return it }

            return seenPostDao.getAll().firstOrNull { stored ->
                val storedHash = PerceptualHash.fromHex(stored.perceptualHash)
                PerceptualHash.hammingDistance(hash, storedHash) <= threshold
            }
        }

        suspend fun incrementSeenCount(hash: String) {
            seenPostDao.incrementSeenCount(hash)
        }

        suspend fun deleteNote(noteId: Long) {
            noteDao.deleteById(noteId)
        }

        override suspend fun saveEvidence(records: List<EvidenceRecord>) {
            if (records.isEmpty()) return

            evidenceRecordDao.insertAll(records.map { it.toEntity() })
        }

        override suspend fun searchEvidence(
            query: String,
            limit: Int,
        ): List<EvidenceRecord> {
            if (query.isBlank()) return emptyList()

            val safeLimit = limit.coerceIn(MIN_EVIDENCE_RESULTS, MAX_EVIDENCE_RESULTS)
            return evidenceRecordDao.search(query, safeLimit).map { it.toEvidenceRecord() }
        }

        suspend fun searchEvidence(query: String): List<EvidenceRecord> = searchEvidence(query, limit = 5)

        private companion object {
            const val MIN_EVIDENCE_RESULTS = 1
            const val MAX_EVIDENCE_RESULTS = 10
        }
    }

/** Aggregate of a Note and its related entities. */
data class NoteWithDetails(
    val note: NoteEntity,
    val sources: List<SourceEntity>,
    val traceSteps: List<TraceStepEntity>,
)
