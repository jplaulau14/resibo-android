package com.patslaurel.resibo.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "evidence_records",
    indices = [
        Index("canonicalUrl"),
        Index(value = ["contentHash"], unique = true),
        Index("sourceName"),
        Index("fetchedAt"),
    ],
)
data class EvidenceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceName: String,
    val sourceType: String,
    val url: String? = null,
    val canonicalUrl: String? = url,
    val title: String,
    val publishedAt: Long? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
    val trustTier: String,
    val stance: String,
    val snippet: String,
    val fullText: String? = null,
    val contentHash: String,
)
