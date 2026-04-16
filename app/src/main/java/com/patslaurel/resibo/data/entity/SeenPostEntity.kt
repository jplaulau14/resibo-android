package com.patslaurel.resibo.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Perceptual-hash index of previously-seen viral posts. Powers the Community Notes
 * cache-hit feature: when a new share's dHash matches a [SeenPostEntity], we surface
 * the previously-generated Note(s) immediately instead of re-running inference.
 *
 * [perceptualHash] is a 16-char hex dHash (see [com.patslaurel.resibo.hash.PerceptualHash]).
 * Lookups use Hamming distance ≤ 5 to catch near-duplicates.
 */
@Entity(
    tableName = "seen_posts",
    indices = [Index("perceptualHash")],
)
data class SeenPostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val perceptualHash: String,
    val noteId: Long,
    val seenCount: Int = 1,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
)
