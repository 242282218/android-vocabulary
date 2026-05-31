package com.zzz.androidvocab.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettingsSnapshot(snapshot: AppSettingsSnapshotEntity)

    @Query(
        """
        SELECT c.*
        FROM review_cards c
        JOIN wordbook_memberships m ON m.wordId = c.wordId AND m.bookCode = c.bookCode
        ORDER BY c.updatedAt ASC
        """,
    )
    suspend fun reviewCards(): List<ReviewCardEntity>

    @Query(
        """
        SELECT l.*
        FROM valid_review_logs l
        ORDER BY l.reviewedAt ASC, l.id ASC
        """,
    )
    suspend fun reviewLogs(): List<ReviewLogEntity>

    @Query("SELECT * FROM daily_stats ORDER BY localDay ASC")
    suspend fun dailyStats(): List<DailyStatsEntity>

    @Query("SELECT * FROM review_daily_stats ORDER BY localDay ASC")
    suspend fun dailyStatsFromLogs(): List<ReviewDailyStatsView>

    @Query("SELECT * FROM app_settings_snapshot WHERE id = 'current'")
    suspend fun settingsSnapshot(): AppSettingsSnapshotEntity?

    @Query("SELECT * FROM source_manifest WHERE id = 'publish-safe'")
    suspend fun sourceManifest(): SourceManifestEntity?

    @Query("SELECT * FROM vocabulary_import_runs ORDER BY importedAt DESC LIMIT 1")
    suspend fun latestImportRun(): VocabularyImportRunEntity?
}
