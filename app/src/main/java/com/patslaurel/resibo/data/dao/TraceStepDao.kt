package com.patslaurel.resibo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.patslaurel.resibo.data.entity.TraceStepEntity

@Dao
interface TraceStepDao {
    @Insert
    suspend fun insertAll(steps: List<TraceStepEntity>)

    @Query("SELECT * FROM trace_steps WHERE noteId = :noteId ORDER BY stepIndex ASC")
    suspend fun getForNote(noteId: Long): List<TraceStepEntity>
}
