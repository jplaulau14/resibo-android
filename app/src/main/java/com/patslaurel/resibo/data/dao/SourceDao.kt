package com.patslaurel.resibo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.patslaurel.resibo.data.entity.SourceEntity

@Dao
interface SourceDao {
    @Insert
    suspend fun insertAll(sources: List<SourceEntity>)

    @Query("SELECT * FROM sources WHERE noteId = :noteId")
    suspend fun getForNote(noteId: Long): List<SourceEntity>
}
