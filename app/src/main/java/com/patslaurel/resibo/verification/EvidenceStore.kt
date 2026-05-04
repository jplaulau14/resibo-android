package com.patslaurel.resibo.verification

interface EvidenceStore {
    suspend fun saveEvidence(records: List<EvidenceRecord>)
}
