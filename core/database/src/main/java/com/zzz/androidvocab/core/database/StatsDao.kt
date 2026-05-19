package com.zzz.androidvocab.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface StatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyStats(stats: DailyStatsEntity)

    @Transaction
    suspend fun rebuildDailyStatsCache(updatedAt: Instant): Int {
        clearDailyStats()
        insertDailyStatsFromLogs(updatedAt)
        return dailyStatsCount()
    }

    @Query("DELETE FROM daily_stats")
    suspend fun clearDailyStats()

    @Query("SELECT COUNT(*) FROM daily_stats")
    suspend fun dailyStatsCount(): Int

    @Query(
        """
        INSERT OR REPLACE INTO daily_stats (
          localDay,
          newCount,
          reviewCount,
          againCount,
          hardCount,
          goodCount,
          easyCount,
          completedCount,
          recallAccuracy,
          passRate,
          estimatedMinutes,
          updatedAt
        )
        WITH first_reviews AS (
          SELECT cardId, MIN(reviewedAt) AS firstReviewedAt
          FROM review_logs
          GROUP BY cardId
        ),
        daily AS (
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
        )
        SELECT
          localDay,
          newCount,
          completedCount - newCount,
          againCount,
          hardCount,
          goodCount,
          easyCount,
          completedCount,
          CASE
            WHEN completedCount = 0 THEN 0.0
            ELSE CAST(goodCount + easyCount AS REAL) / completedCount
          END,
          CASE
            WHEN completedCount = 0 THEN 0.0
            ELSE CAST(hardCount + goodCount + easyCount AS REAL) / completedCount
          END,
          CASE
            WHEN durationMs <= 0 THEN 0
            ELSE CAST((durationMs + 59999) / 60000 AS INTEGER)
          END,
          :updatedAt
        FROM daily
        """,
    )
    suspend fun insertDailyStatsFromLogs(updatedAt: Instant)

    @Query("SELECT rating, COUNT(*) AS count FROM review_logs WHERE localDay = :localDay GROUP BY rating")
    fun observeDailyRatingCounts(localDay: String): Flow<List<DailyRatingCountRow>>

    @Query("SELECT rating, COUNT(*) AS count FROM review_logs WHERE localDay = :localDay GROUP BY rating")
    suspend fun dailyRatingCounts(localDay: String): List<DailyRatingCountRow>

    @Query("SELECT COUNT(*) FROM review_logs WHERE localDay = :localDay")
    fun observeDailyCompleted(localDay: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM review_logs WHERE localDay = :localDay")
    suspend fun dailyCompleted(localDay: String): Int

    @Query("SELECT IFNULL(SUM(durationMs), 0) FROM review_logs WHERE localDay = :localDay")
    fun observeDailyDurationMs(localDay: String): Flow<Long>

    @Query("SELECT IFNULL(SUM(durationMs), 0) FROM review_logs WHERE localDay = :localDay")
    suspend fun dailyDurationMs(localDay: String): Long

    @Query(
        """
        SELECT AVG(durationMs)
        FROM review_logs
        WHERE localDay >= :startDay AND localDay <= :endDay AND durationMs > 0
        """,
    )
    fun observeAverageDurationMs(
        startDay: String,
        endDay: String,
    ): Flow<Double?>

    @Query(
        """
        WITH first_reviews AS (
          SELECT cardId, MIN(reviewedAt) AS firstReviewedAt
          FROM review_logs
          GROUP BY cardId
        )
        SELECT COUNT(*) FROM review_logs l
        INNER JOIN first_reviews c ON c.cardId = l.cardId
        WHERE l.localDay = :localDay
        AND c.firstReviewedAt = l.reviewedAt
        """,
    )
    fun observeDailyNewCount(localDay: String): Flow<Int>

    @Query(
        """
        WITH first_reviews AS (
          SELECT cardId, MIN(reviewedAt) AS firstReviewedAt
          FROM review_logs
          GROUP BY cardId
        )
        SELECT COUNT(*) FROM review_logs l
        INNER JOIN first_reviews c ON c.cardId = l.cardId
        WHERE l.localDay = :localDay
        AND c.firstReviewedAt = l.reviewedAt
        """,
    )
    suspend fun dailyNewCount(localDay: String): Int

    @Query("SELECT dueAt FROM review_cards WHERE dueAt IS NOT NULL AND dueAt < :end")
    fun observeDueCards(end: Instant): Flow<List<DueCardRow>>

    @Query("SELECT bookCode, COUNT(*) AS count FROM wordbook_memberships GROUP BY bookCode")
    fun observeBookTotals(): Flow<List<BookCountRow>>

    @Query(
        """
        SELECT
          m.bookCode AS bookCode,
          COUNT(*) AS totalCount,
          SUM(CASE WHEN c.id IS NOT NULL THEN 1 ELSE 0 END) AS learnedCount,
          SUM(
            CASE
              WHEN c.scheduledDays >= 21
                AND IFNULL(c.retrievability, 0) >= 0.85
                AND (c.dueAt IS NULL OR c.dueAt > :now)
              THEN 1 ELSE 0
            END
          ) AS masteredCount,
          SUM(CASE WHEN c.dueAt IS NOT NULL AND c.dueAt <= :now THEN 1 ELSE 0 END) AS dueCount,
          SUM(CASE WHEN c.state IN ('New', 'Learning', 'Relearning') THEN 1 ELSE 0 END) AS learningCount,
          SUM(CASE WHEN c.state IN ('Review', 'Mastered') AND (c.dueAt IS NULL OR c.dueAt > :now) THEN 1 ELSE 0 END) AS familiarCount
        FROM wordbook_memberships m
        LEFT JOIN review_cards c ON c.wordId = m.wordId AND c.bookCode = m.bookCode
        GROUP BY m.bookCode
        """,
    )
    fun observeBookStats(now: Instant): Flow<List<BookStatsRow>>

    @Query("SELECT * FROM review_cards WHERE lastReviewAt IS NOT NULL AND lastReviewAt >= :from")
    fun observeReviewedCards(from: Instant): Flow<List<ReviewCardEntity>>

    @Query("SELECT DISTINCT localDay FROM review_logs")
    fun observeActiveDays(): Flow<List<String>>

    @Query(
        """
        SELECT localDay, COUNT(*) AS reviewCount
        FROM review_logs
        WHERE localDay >= :startDay AND localDay <= :endDay
        GROUP BY localDay
        ORDER BY localDay ASC
        """,
    )
    fun observeDailyActivityRows(
        startDay: String,
        endDay: String,
    ): Flow<List<DailyActivityRow>>

    @Query(
        """
        SELECT
          wordId,
          SUM(CASE WHEN rating = 'again' THEN 1 ELSE 0 END) AS againCount,
          SUM(CASE WHEN rating = 'hard' THEN 1 ELSE 0 END) AS hardCount
        FROM review_logs
        WHERE reviewedAt >= :from
        GROUP BY wordId
        HAVING againCount > 0 OR hardCount > 0
        ORDER BY (againCount * 3.0 + hardCount * 1.5) DESC
        LIMIT 10
        """,
    )
    fun observeDifficultWordRows(from: Instant): Flow<List<DifficultWordRow>>

    @Query("SELECT * FROM word_entries WHERE id IN (:wordIds)")
    suspend fun getWordsByIds(wordIds: List<String>): List<WordEntryEntity>
}
