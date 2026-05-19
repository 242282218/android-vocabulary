package com.zzz.androidvocab.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: ReviewCardEntity)

    @Update
    suspend fun updateCard(card: ReviewCardEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLog(log: ReviewLogEntity)

    @Query("SELECT * FROM review_cards WHERE id = :cardId")
    suspend fun getCard(cardId: String): ReviewCardEntity?

    @Query("DELETE FROM review_cards WHERE id = :cardId")
    suspend fun deleteCard(cardId: String)

    @Query("SELECT * FROM review_logs WHERE cardId = :cardId ORDER BY reviewedAt ASC")
    suspend fun getLogs(cardId: String): List<ReviewLogEntity>

    @Query(
        """
        SELECT DISTINCT l.cardId
        FROM review_logs l
        JOIN wordbook_memberships m ON m.wordId = l.wordId AND m.bookCode = l.bookCode
        ORDER BY l.cardId ASC
        """,
    )
    suspend fun getCardIdsWithLogs(): List<String>

    @Query(
        """
        SELECT DISTINCT l.cardId
        FROM review_logs l
        JOIN wordbook_memberships m ON m.wordId = l.wordId AND m.bookCode = l.bookCode
        LEFT JOIN review_cards c ON c.id = l.cardId
        WHERE c.id IS NULL
        ORDER BY l.cardId ASC
        """,
    )
    suspend fun getCardIdsMissingCacheFromLogs(): List<String>

    @Query(
        """
        DELETE FROM review_cards
        WHERE NOT EXISTS (
          SELECT 1 FROM wordbook_memberships m
          WHERE m.wordId = review_cards.wordId AND m.bookCode = review_cards.bookCode
        )
        """,
    )
    suspend fun deleteCardsWithoutMembership()

    @Query(
        """
        SELECT
          c.id AS cardId,
          w.id AS wordId,
          c.bookCode AS bookCode,
          c.state AS state,
          c.difficulty AS difficulty,
          c.stability AS stability,
          c.retrievability AS retrievability,
          c.scheduledDays AS scheduledDays,
          c.dueAt AS dueAt,
          c.lastReviewAt AS lastReviewAt,
          c.reviewCount AS reviewCount,
          c.lapseCount AS lapseCount,
          c.firstReviewedAt AS firstReviewedAt,
          c.createdAt AS cardCreatedAt,
          c.updatedAt AS cardUpdatedAt,
          w.word AS word,
          w.meaning AS meaning,
          w.phonetic AS phonetic,
          w.partOfSpeech AS partOfSpeech,
          w.definition AS definition,
          w.cefrLevel AS cefrLevel,
          w.cefrRank AS cefrRank,
          w.frequency AS frequency,
          w.sourceFlagsJson AS sourceFlagsJson,
          w.coverageTier AS coverageTier,
          w.createdAt AS wordCreatedAt,
          w.updatedAt AS wordUpdatedAt
        FROM review_cards c
        JOIN word_entries w ON w.id = c.wordId
        LEFT JOIN (
          SELECT
            cardId,
            SUM(
              CASE
                WHEN rating = 'again' THEN 3.0
                WHEN rating = 'hard' THEN 1.5
                ELSE 0
              END
            ) AS difficultyScore
          FROM review_logs
          WHERE reviewedAt >= :difficultySince
          GROUP BY cardId
        ) d ON d.cardId = c.id
        WHERE c.dueAt IS NOT NULL AND c.dueAt <= :now AND c.bookCode IN (:bookCodes)
        ORDER BY c.dueAt ASC, IFNULL(d.difficultyScore, 0) DESC, c.lapseCount DESC
        LIMIT 200
        """,
    )
    fun observeDueQueue(
        now: Instant,
        bookCodes: List<String>,
        difficultySince: Instant,
    ): Flow<List<ReviewQueueRow>>

    @Query(
        """
        SELECT
          NULL AS cardId,
          w.id AS wordId,
          m.bookCode AS bookCode,
          NULL AS state,
          NULL AS difficulty,
          NULL AS stability,
          NULL AS retrievability,
          NULL AS scheduledDays,
          NULL AS dueAt,
          NULL AS lastReviewAt,
          NULL AS reviewCount,
          NULL AS lapseCount,
          NULL AS firstReviewedAt,
          NULL AS cardCreatedAt,
          NULL AS cardUpdatedAt,
          w.word AS word,
          w.meaning AS meaning,
          w.phonetic AS phonetic,
          w.partOfSpeech AS partOfSpeech,
          w.definition AS definition,
          w.cefrLevel AS cefrLevel,
          w.cefrRank AS cefrRank,
          w.frequency AS frequency,
          w.sourceFlagsJson AS sourceFlagsJson,
          w.coverageTier AS coverageTier,
          w.createdAt AS wordCreatedAt,
          w.updatedAt AS wordUpdatedAt
        FROM wordbook_memberships m
        JOIN word_entries w ON w.id = m.wordId
        LEFT JOIN review_cards c ON c.wordId = m.wordId AND c.bookCode = m.bookCode
        LEFT JOIN review_logs l ON l.wordId = m.wordId AND l.bookCode = m.bookCode
        WHERE c.id IS NULL AND l.id IS NULL AND m.bookCode IN (:bookCodes)
        ORDER BY m.bookCode ASC, m.orderIndex ASC
        LIMIT :limit
        """,
    )
    fun observeNewQueue(
        bookCodes: List<String>,
        limit: Int,
    ): Flow<List<ReviewQueueRow>>

    @Query(
        """
        SELECT
          c.id AS cardId,
          w.id AS wordId,
          c.bookCode AS bookCode,
          c.state AS state,
          c.difficulty AS difficulty,
          c.stability AS stability,
          c.retrievability AS retrievability,
          c.scheduledDays AS scheduledDays,
          c.dueAt AS dueAt,
          c.lastReviewAt AS lastReviewAt,
          c.reviewCount AS reviewCount,
          c.lapseCount AS lapseCount,
          c.firstReviewedAt AS firstReviewedAt,
          c.createdAt AS cardCreatedAt,
          c.updatedAt AS cardUpdatedAt,
          w.word AS word,
          w.meaning AS meaning,
          w.phonetic AS phonetic,
          w.partOfSpeech AS partOfSpeech,
          w.definition AS definition,
          w.cefrLevel AS cefrLevel,
          w.cefrRank AS cefrRank,
          w.frequency AS frequency,
          w.sourceFlagsJson AS sourceFlagsJson,
          w.coverageTier AS coverageTier,
          w.createdAt AS wordCreatedAt,
          w.updatedAt AS wordUpdatedAt
        FROM review_cards c
        JOIN word_entries w ON w.id = c.wordId
        WHERE c.id = :cardId
        """,
    )
    suspend fun getQueueItem(cardId: String): ReviewQueueRow?

    @Query(
        """
        SELECT
          NULL AS cardId,
          w.id AS wordId,
          m.bookCode AS bookCode,
          NULL AS state,
          NULL AS difficulty,
          NULL AS stability,
          NULL AS retrievability,
          NULL AS scheduledDays,
          NULL AS dueAt,
          NULL AS lastReviewAt,
          NULL AS reviewCount,
          NULL AS lapseCount,
          NULL AS firstReviewedAt,
          NULL AS cardCreatedAt,
          NULL AS cardUpdatedAt,
          w.word AS word,
          w.meaning AS meaning,
          w.phonetic AS phonetic,
          w.partOfSpeech AS partOfSpeech,
          w.definition AS definition,
          w.cefrLevel AS cefrLevel,
          w.cefrRank AS cefrRank,
          w.frequency AS frequency,
          w.sourceFlagsJson AS sourceFlagsJson,
          w.coverageTier AS coverageTier,
          w.createdAt AS wordCreatedAt,
          w.updatedAt AS wordUpdatedAt
        FROM wordbook_memberships m
        JOIN word_entries w ON w.id = m.wordId
        WHERE m.wordId = :wordId AND m.bookCode = :bookCode
        LIMIT 1
        """,
    )
    suspend fun getNewQueueItem(
        wordId: String,
        bookCode: String,
    ): ReviewQueueRow?
}
