package com.zzz.androidvocab.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

const val VOCAB_DATABASE_VERSION = 5

@Database(
    entities = [
        WordEntryEntity::class,
        WordBookMembershipEntity::class,
        WordAliasEntity::class,
        ReviewCardEntity::class,
        ReviewLogEntity::class,
        DailyStatsEntity::class,
        AppSettingsSnapshotEntity::class,
        SourceManifestEntity::class,
        VocabularyImportRunEntity::class,
    ],
    views = [ValidReviewLogView::class, WordCardView::class],
    version = VOCAB_DATABASE_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VocabDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao

    abstract fun reviewDao(): ReviewDao

    abstract fun statsDao(): StatsDao

    abstract fun exportDao(): ExportDao
}
