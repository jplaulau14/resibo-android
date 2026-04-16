package com.patslaurel.resibo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.patslaurel.resibo.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllOrdered(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
