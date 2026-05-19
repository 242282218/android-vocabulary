package com.zzz.androidvocab.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettingsSnapshot(snapshot: AppSettingsSnapshotEntity)

    @Query("SELECT * FROM review_cards ORDER BY updatedAt ASC")
    suspend fun reviewCards(): List<ReviewCardEntity>

    @Query("SELECT * FROM review_logs ORDER BY reviewedAt ASC")
    suspend fun reviewLogs(): List<ReviewLogEntity>

    @Query("SELECT * FROM daily_stats ORDER BY localDay ASC")
    suspend fun dailyStats(): List<DailyStatsEntity>

    @Query(
        """
        WITH first_reviews AS (
          SELECT cardId, MIN(reviewedAt) AS firstReviewedAt
          FROM review_logs
          GROUP BY cardId
        )
        SELECT
          l.localDay AS localDay,
          SUM(CASE WHEN f.firstReviewedAt = l.reviewedAt THEN 1 ELSE 0 END) AS newCount,
          SUM(CASE WHEN l.rating = 'again' THEN 1 ELSE 0 END) AS againCount,
          SUM(CASE WHEN l.rating = 'hard' THEN 1 ELSE 0 END) AS hardCount,
          SUM(CASE WHEN l.rating = 'good' THEN 1 ELSE 0 END) AS goodCount,
          SUM(CASE WHEN l.rating = 'easy' THEN 1 ELSE 0 END) AS easyCount,
          COUNT(*) AS completedCount,
          IFNULL(SUM(l.durationMs), 0) AS durationMs
        FROM review_logs l
        JOIN first_reviews f ON f.cardId = l.cardId
        GROUP BY l.localDay
        ORDER BY l.localDay ASC
        """,
    )
    suspend fun dailyStatsFromLogs(): List<ExportDailyStatsRow>

    @Query("SELECT * FROM app_settings_snapshot WHERE id = 'current'")
    suspend fun settingsSnapshot(): AppSettingsSnapshotEntity?

    @Query("SELECT * FROM source_manifest WHERE id = 'publish-safe'")
    suspend fun sourceManifest(): SourceManifestEntity?

    @Query("SELECT * FROM vocabulary_import_runs ORDER BY importedAt DESC LIMIT 1")
    suspend fun latestImportRun(): VocabularyImportRunEntity?
}
