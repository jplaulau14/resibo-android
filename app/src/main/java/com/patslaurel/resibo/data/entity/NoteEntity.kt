package com.patslaurel.resibo.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A generated fact-check Note — the core output of the Resibo agent.
 * One Note per shared post; linked to [SourceEntity] and [TraceStepEntity] via [id].
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val claim: String,
    val language: String,
    val checkWorthiness: String,
    val domain: String,
    val offlineAssessment: String,
    val verificationNeeded: String,
    val fullResponse: String,
    val modelVariant: String,
    val promptChars: Int,
    val outputChars: Int,
    val generationMs: Long,
    val perceptualHash: String? = null,
    val mimeType: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
