package com.patslaurel.resibo.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.patslaurel.resibo.data.dao.EvidenceRecordDao
import com.patslaurel.resibo.data.dao.NoteDao
import com.patslaurel.resibo.data.dao.SeenPostDao
import com.patslaurel.resibo.data.dao.SourceDao
import com.patslaurel.resibo.data.dao.TraceStepDao
import com.patslaurel.resibo.data.entity.EvidenceRecordEntity
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
        EvidenceRecordEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ResiboDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun sourceDao(): SourceDao

    abstract fun traceStepDao(): TraceStepDao

    abstract fun seenPostDao(): SeenPostDao

    abstract fun evidenceRecordDao(): EvidenceRecordDao

    companion object {
        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `evidence_records` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `sourceName` TEXT NOT NULL,
                            `sourceType` TEXT NOT NULL,
                            `url` TEXT,
                            `canonicalUrl` TEXT,
                            `title` TEXT NOT NULL,
                            `publishedAt` INTEGER,
                            `fetchedAt` INTEGER NOT NULL,
                            `trustTier` TEXT NOT NULL,
                            `stance` TEXT NOT NULL,
                            `snippet` TEXT NOT NULL,
                            `fullText` TEXT,
                            `contentHash` TEXT NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_evidence_records_canonicalUrl` " +
                            "ON `evidence_records` (`canonicalUrl`)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_evidence_records_contentHash` " +
                            "ON `evidence_records` (`contentHash`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_evidence_records_sourceName` " +
                            "ON `evidence_records` (`sourceName`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_evidence_records_fetchedAt` " +
                            "ON `evidence_records` (`fetchedAt`)",
                    )
                }
            }

        val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `notes` ADD COLUMN `evidenceMode` TEXT")
                    db.execSQL("ALTER TABLE `notes` ADD COLUMN `evidenceFetchedAt` INTEGER")
                }
            }
    }
}
