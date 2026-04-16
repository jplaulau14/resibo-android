package com.patslaurel.resibo.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A source citation attached to a Note. Populated by RAG retrieval (E04)
 * or from the model's inline citations.
 */
@Entity(
    tableName = "sources",
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
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val title: String,
    val url: String? = null,
    val snippet: String? = null,
    val domain: String? = null,
    val retrievedAt: Long = System.currentTimeMillis(),
)
