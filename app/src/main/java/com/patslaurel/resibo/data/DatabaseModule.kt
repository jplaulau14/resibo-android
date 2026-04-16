package com.patslaurel.resibo.data

import android.content.Context
import androidx.room.Room
import com.patslaurel.resibo.data.crypto.KeystoreKeyProvider
import com.patslaurel.resibo.data.dao.NoteDao
import com.patslaurel.resibo.data.dao.SeenPostDao
import com.patslaurel.resibo.data.dao.SourceDao
import com.patslaurel.resibo.data.dao.TraceStepDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keystoreKeyProvider: KeystoreKeyProvider,
    ): ResiboDatabase {
        val passphrase = keystoreKeyProvider.getOrCreatePassphrase()
        val factory = SupportFactory(passphrase)

        return Room
            .databaseBuilder(context, ResiboDatabase::class.java, "resibo.db")
            .openHelperFactory(factory)
            .build()
    }

    @Provides
    fun provideNoteDao(db: ResiboDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideSourceDao(db: ResiboDatabase): SourceDao = db.sourceDao()

    @Provides
    fun provideTraceStepDao(db: ResiboDatabase): TraceStepDao = db.traceStepDao()

    @Provides
    fun provideSeenPostDao(db: ResiboDatabase): SeenPostDao = db.seenPostDao()
}
