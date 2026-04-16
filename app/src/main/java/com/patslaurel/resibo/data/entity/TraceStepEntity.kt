package com.patslaurel.resibo.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One step in the agent's reasoning trace. Ordered by [stepIndex].
 * Populated during the agent loop (E05) to surface transparency for the user.
 */
@Entity(
    tableName = "trace_steps",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class TraceStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val stepIndex: Int,
    val tool: String,
    val input: String,
    val output: String,
    val durationMs: Long = 0,
)
