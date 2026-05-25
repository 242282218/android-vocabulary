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
        FROM valid_review_logs l
        ORDER BY l.cardId ASC
        """,
    )
    suspend fun getCardIdsWithLogs(): List<String>

    @Query(
        """
        SELECT COUNT(*)
        FROM review_logs l
        WHERE NOT EXISTS (
          SELECT 1 FROM wordbook_memberships m
          WHERE m.wordId = l.wordId AND m.bookCode = l.bookCode
        )
        """,
    )
    suspend fun countOrphanLogs(): Int

    @Query(
        """
        SELECT DISTINCT l.cardId
        FROM valid_review_logs l
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
        SELECT v.cardId, v.wordId, v.bookCode, v.state, v.difficulty,
          v.stability, v.retrievability, v.scheduledDays, v.dueAt, v.lastReviewAt,
          v.reviewCount, v.lapseCount, v.firstReviewedAt,
          v.cardCreatedAt, v.cardUpdatedAt,
          v.word, v.meaning, v.phonetic, v.partOfSpeech, v.definition,
          v.cefrLevel, v.cefrRank, v.frequency, v.sourceFlagsJson, v.coverageTier,
          v.wordCreatedAt, v.wordUpdatedAt
        FROM word_card_view v
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
          FROM valid_review_logs
          WHERE reviewedAt >= :difficultySince
          GROUP BY cardId
        ) d ON d.cardId = v.cardId
        WHERE v.cardId IS NOT NULL AND v.dueAt IS NOT NULL AND v.dueAt <= :now AND v.bookCode IN (:bookCodes)
        ORDER BY v.dueAt ASC, IFNULL(d.difficultyScore, 0) DESC, v.lapseCount DESC
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
        SELECT v.cardId, v.wordId, v.bookCode, v.state, v.difficulty,
          v.stability, v.retrievability, v.scheduledDays, v.dueAt, v.lastReviewAt,
          v.reviewCount, v.lapseCount, v.firstReviewedAt,
          v.cardCreatedAt, v.cardUpdatedAt,
          v.word, v.meaning, v.phonetic, v.partOfSpeech, v.definition,
          v.cefrLevel, v.cefrRank, v.frequency, v.sourceFlagsJson, v.coverageTier,
          v.wordCreatedAt, v.wordUpdatedAt
        FROM word_card_view v
        WHERE v.cardId IS NULL
          AND NOT EXISTS (
            SELECT 1 FROM valid_review_logs l
            WHERE l.wordId = v.wordId AND l.bookCode = v.bookCode
          )
          AND v.bookCode IN (:bookCodes)
        ORDER BY v.bookCode ASC, v.orderIndex ASC
        LIMIT :limit
        """,
    )
    fun observeNewQueue(
        bookCodes: List<String>,
        limit: Int,
    ): Flow<List<ReviewQueueRow>>

    @Query(
        """
        SELECT v.cardId, v.wordId, v.bookCode, v.state, v.difficulty,
          v.stability, v.retrievability, v.scheduledDays, v.dueAt, v.lastReviewAt,
          v.reviewCount, v.lapseCount, v.firstReviewedAt,
          v.cardCreatedAt, v.cardUpdatedAt,
          v.word, v.meaning, v.phonetic, v.partOfSpeech, v.definition,
          v.cefrLevel, v.cefrRank, v.frequency, v.sourceFlagsJson, v.coverageTier,
          v.wordCreatedAt, v.wordUpdatedAt
        FROM word_card_view v
        WHERE v.cardId = :cardId
        """,
    )
    suspend fun getQueueItem(cardId: String): ReviewQueueRow?

    @Query(
        """
        SELECT v.cardId, v.wordId, v.bookCode, v.state, v.difficulty,
          v.stability, v.retrievability, v.scheduledDays, v.dueAt, v.lastReviewAt,
          v.reviewCount, v.lapseCount, v.firstReviewedAt,
          v.cardCreatedAt, v.cardUpdatedAt,
          v.word, v.meaning, v.phonetic, v.partOfSpeech, v.definition,
          v.cefrLevel, v.cefrRank, v.frequency, v.sourceFlagsJson, v.coverageTier,
          v.wordCreatedAt, v.wordUpdatedAt
        FROM word_card_view v
        WHERE v.wordId = :wordId AND v.bookCode = :bookCode
        LIMIT 1
        """,
    )
    suspend fun getNewQueueItem(
        wordId: String,
        bookCode: String,
    ): ReviewQueueRow?
}
