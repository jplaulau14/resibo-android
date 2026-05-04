package com.patslaurel.resibo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.patslaurel.resibo.data.entity.EvidenceRecordEntity

@Dao
interface EvidenceRecordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<EvidenceRecordEntity>): List<Long>

    @Query(
        """
        SELECT * FROM evidence_records
        WHERE title LIKE '%' || :query || '%'
            OR snippet LIKE '%' || :query || '%'
            OR fullText LIKE '%' || :query || '%'
            OR sourceName LIKE '%' || :query || '%'
        ORDER BY fetchedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun search(
        query: String,
        limit: Int = 5,
    ): List<EvidenceRecordEntity>

    @Query("SELECT * FROM evidence_records WHERE contentHash = :contentHash LIMIT 1")
    suspend fun findByContentHash(contentHash: String): EvidenceRecordEntity?
}
