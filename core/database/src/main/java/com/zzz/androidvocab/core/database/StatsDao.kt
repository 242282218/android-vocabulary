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

    @Query("SELECT * FROM daily_stats ORDER BY localDay ASC")
    suspend fun dailyStats(): List<DailyStatsEntity>

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
        SELECT
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
          :updatedAt
        FROM review_daily_stats
        """,
    )
    suspend fun insertDailyStatsFromLogs(updatedAt: Instant)

    @Query(
        """
        SELECT
          localDay,
          newCount,
          reviewCount,
          againCount,
          hardCount,
          goodCount,
          easyCount,
          completedCount,
          estimatedMinutes,
          recallAccuracy,
          passRate,
          :updatedAt AS updatedAt
        FROM review_daily_stats
        WHERE localDay = :localDay
        """,
    )
    suspend fun dailyStatsFromLogs(
        localDay: String,
        updatedAt: Instant,
    ): DailyStatsEntity?

    @Query(
        """
        SELECT * FROM review_daily_stats
        ORDER BY localDay ASC
        """,
    )
    suspend fun dailyStatsFromLogsRows(): List<ReviewDailyStatsView>

    @Query(
        """
        SELECT
          l.localDay AS localDay,
          SUM(CASE WHEN f.firstLogId = l.id THEN 1 ELSE 0 END) AS newCount,
          COUNT(*) - SUM(CASE WHEN f.firstLogId = l.id THEN 1 ELSE 0 END) AS reviewCount,
          SUM(CASE WHEN l.rating = 'again' THEN 1 ELSE 0 END) AS againCount,
          SUM(CASE WHEN l.rating = 'hard' THEN 1 ELSE 0 END) AS hardCount,
          SUM(CASE WHEN l.rating = 'good' THEN 1 ELSE 0 END) AS goodCount,
          SUM(CASE WHEN l.rating = 'easy' THEN 1 ELSE 0 END) AS easyCount,
          COUNT(*) AS completedCount,
          IFNULL(SUM(l.durationMs), 0) AS durationMs,
          AVG(CASE WHEN l.rating IN ('good', 'easy') THEN 1.0 ELSE 0.0 END) AS recallAccuracy,
          AVG(CASE WHEN l.rating != 'again' THEN 1.0 ELSE 0.0 END) AS passRate,
          CAST(0 AS INTEGER) AS estimatedMinutes
        FROM valid_review_logs l
        JOIN first_reviews f ON f.cardId = (l.bookCode || '|' || l.wordId)
        WHERE l.localDay = :localDay AND l.bookCode IN (:bookCodes)
        GROUP BY l.localDay
        """,
    )
    fun observeDailyStatsFromLogs(
        localDay: String,
        bookCodes: List<String>,
    ): Flow<ReviewDailyStatsView?>

    @Query(
        """
        SELECT AVG(l.durationMs)
        FROM valid_review_logs l
        WHERE l.localDay >= :startDay AND l.localDay <= :endDay AND l.durationMs > 0 AND l.bookCode IN (:bookCodes)
        """,
    )
    fun observeAverageDurationMs(
        startDay: String,
        endDay: String,
        bookCodes: List<String>,
    ): Flow<Double?>

    @Query(
        """
        SELECT c.dueAt
        FROM review_cards c
        JOIN wordbook_memberships m ON m.wordId = c.wordId AND m.bookCode = c.bookCode
        WHERE c.dueAt IS NOT NULL AND c.dueAt < :end AND m.bookCode IN (:bookCodes)
        """,
    )
    fun observeDueCards(
        end: Instant,
        bookCodes: List<String>,
    ): Flow<List<DueCardRow>>

    @Query(
        """
        SELECT COUNT(*)
        FROM review_cards c
        JOIN wordbook_memberships m ON m.wordId = c.wordId AND m.bookCode = c.bookCode
        WHERE c.dueAt IS NOT NULL AND c.dueAt <= :now AND m.bookCode IN (:bookCodes)
        """,
    )
    fun observeDueCount(
        now: Instant,
        bookCodes: List<String>,
    ): Flow<Int>

    @Query("SELECT bookCode, COUNT(*) AS count FROM wordbook_memberships GROUP BY bookCode")
    fun observeBookTotals(): Flow<List<BookCountRow>>

    @Query(
        """
        SELECT c.*
        FROM review_cards c
        WHERE EXISTS (
          SELECT 1 FROM wordbook_memberships m
          WHERE m.wordId = c.wordId AND m.bookCode = c.bookCode
        )
        """,
    )
    fun observeValidReviewCards(): Flow<List<ReviewCardEntity>>

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
          SUM(
            CASE
              WHEN c.state IN ('Review', 'Mastered')
                AND (c.dueAt IS NULL OR c.dueAt > :now)
                AND NOT (c.scheduledDays >= 21 AND IFNULL(c.retrievability, 0) >= 0.85)
              THEN 1 ELSE 0
            END
          ) AS familiarCount
        FROM wordbook_memberships m
        LEFT JOIN review_cards c ON c.wordId = m.wordId AND c.bookCode = m.bookCode
        GROUP BY m.bookCode
        """,
    )
    fun observeBookStats(now: Instant): Flow<List<BookStatsRow>>

    @Query(
        """
        SELECT c.*
        FROM review_cards c
        JOIN wordbook_memberships m ON m.wordId = c.wordId AND m.bookCode = c.bookCode
        WHERE c.lastReviewAt IS NOT NULL AND c.lastReviewAt >= :from AND m.bookCode IN (:bookCodes)
        """,
    )
    fun observeReviewedCards(
        from: Instant,
        bookCodes: List<String>,
    ): Flow<List<ReviewCardEntity>>

    @Query(
        """
        SELECT DISTINCT l.localDay
        FROM valid_review_logs l
        WHERE l.bookCode IN (:bookCodes)
        """,
    )
    fun observeActiveDays(bookCodes: List<String>): Flow<List<String>>

    @Query(
        """
        SELECT l.localDay, COUNT(*) AS reviewCount
        FROM valid_review_logs l
        WHERE l.localDay >= :startDay AND l.localDay <= :endDay AND l.bookCode IN (:bookCodes)
        GROUP BY l.localDay
        ORDER BY l.localDay ASC
        """,
    )
    fun observeDailyActivityRows(
        startDay: String,
        endDay: String,
        bookCodes: List<String>,
    ): Flow<List<DailyActivityRow>>

    @Query(
        """
        SELECT
          l.wordId AS wordId,
          SUM(CASE WHEN rating = 'again' THEN 1 ELSE 0 END) AS againCount,
          SUM(CASE WHEN rating = 'hard' THEN 1 ELSE 0 END) AS hardCount
        FROM valid_review_logs l
        WHERE l.reviewedAt >= :from AND l.bookCode IN (:bookCodes)
        GROUP BY l.wordId
        HAVING againCount > 0 OR hardCount > 0
        ORDER BY (againCount * 3.0 + hardCount * 1.5) DESC
        LIMIT 10
        """,
    )
    fun observeDifficultWordRows(
        from: Instant,
        bookCodes: List<String>,
    ): Flow<List<DifficultWordRow>>

    @Query("SELECT * FROM word_entries WHERE id IN (:wordIds)")
    suspend fun getWordsByIds(wordIds: List<String>): List<WordEntryEntity>
}
