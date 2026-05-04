package com.patslaurel.resibo.verification

interface EvidenceSearch {
    suspend fun searchEvidence(
        query: String,
        limit: Int = 5,
    ): List<EvidenceRecord>
}
