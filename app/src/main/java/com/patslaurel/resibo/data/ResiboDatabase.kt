package com.patslaurel.resibo.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.patslaurel.resibo.data.dao.NoteDao
import com.patslaurel.resibo.data.dao.SeenPostDao
import com.patslaurel.resibo.data.dao.SourceDao
import com.patslaurel.resibo.data.dao.TraceStepDao
import com.patslaurel.resibo.data.entity.NoteEntity
import com.patslaurel.resibo.data.entity.SeenPostEntity
import com.patslaurel.resibo.data.entity.SourceEntity
import com.patslaurel.resibo.data.entity.TraceStepEntity

@Database(
    entities = [
        NoteEntity::class,
        SourceEntity::class,
        TraceStepEntity::class,
        SeenPostEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ResiboDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun sourceDao(): SourceDao

    abstract fun traceStepDao(): TraceStepDao

    abstract fun seenPostDao(): SeenPostDao
}
