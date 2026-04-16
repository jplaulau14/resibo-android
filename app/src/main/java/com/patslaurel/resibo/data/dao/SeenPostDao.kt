package com.patslaurel.resibo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.patslaurel.resibo.data.entity.SeenPostEntity

@Dao
interface SeenPostDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(seenPost: SeenPostEntity)

    @Query("SELECT * FROM seen_posts WHERE perceptualHash = :hash LIMIT 1")
    suspend fun findByExactHash(hash: String): SeenPostEntity?

    /** Return all seen posts for Hamming-distance comparison in Kotlin. */
    @Query("SELECT * FROM seen_posts")
    suspend fun getAll(): List<SeenPostEntity>

    @Query(
        "UPDATE seen_posts SET seenCount = seenCount + 1, lastSeenAt = :now WHERE perceptualHash = :hash",
    )
    suspend fun incrementSeenCount(
        hash: String,
        now: Long = System.currentTimeMillis(),
    )
}
