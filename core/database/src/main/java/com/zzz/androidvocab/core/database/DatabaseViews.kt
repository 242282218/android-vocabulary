package com.zzz.androidvocab.core.database

import androidx.room.DatabaseView
import java.time.Instant

internal const val REVIEW_DAILY_STATS_VIEW_NAME = "review_daily_stats"

internal const val FIRST_REVIEWS_VIEW_NAME = "first_reviews"

internal const val LOGICAL_REVIEW_CARD_ID_SQL = "l.bookCode || '|' || l.wordId"
internal const val CANDIDATE_LOGICAL_REVIEW_CARD_MATCH_SQL =
    "candidate.wordId = l.wordId AND candidate.bookCode = l.bookCode"

internal const val FIRST_REVIEWS_VIEW_SQL =
    "SELECT $LOGICAL_REVIEW_CARD_ID_SQL AS cardId, l.id AS firstLogId, l.reviewedAt AS firstReviewedAt " +
        "FROM valid_review_logs l " +
        "WHERE l.id = (" +
        "SELECT candidate.id FROM valid_review_logs candidate " +
        "WHERE $CANDIDATE_LOGICAL_REVIEW_CARD_MATCH_SQL " +
        "ORDER BY candidate.reviewedAt ASC, candidate.id ASC " +
        "LIMIT 1" +
        ")"

internal const val REVIEW_DAILY_STATS_VIEW_SQL =
    "SELECT " +
        "localDay, " +
        "newCount, " +
        "(completedCount - newCount) AS reviewCount, " +
        "againCount, " +
        "hardCount, " +
        "goodCount, " +
        "easyCount, " +
        "completedCount, " +
        "durationMs, " +
        "CASE " +
        "WHEN completedCount = 0 THEN 0.0 " +
        "ELSE CAST(goodCount + easyCount AS REAL) / completedCount " +
        "END AS recallAccuracy, " +
        "CASE " +
        "WHEN completedCount = 0 THEN 0.0 " +
        "ELSE CAST(hardCount + goodCount + easyCount AS REAL) / completedCount " +
        "END AS passRate, " +
        "CASE " +
        "WHEN durationMs <= 0 THEN 0 " +
        "ELSE CAST((durationMs + 59999) / 60000 AS INTEGER) " +
        "END AS estimatedMinutes " +
        "FROM (" +
        "SELECT " +
        "l.localDay AS localDay, " +
        "SUM(CASE WHEN f.firstLogId = l.id THEN 1 ELSE 0 END) AS newCount, " +
        "SUM(CASE WHEN l.rating = 'again' THEN 1 ELSE 0 END) AS againCount, " +
        "SUM(CASE WHEN l.rating = 'hard' THEN 1 ELSE 0 END) AS hardCount, " +
        "SUM(CASE WHEN l.rating = 'good' THEN 1 ELSE 0 END) AS goodCount, " +
        "SUM(CASE WHEN l.rating = 'easy' THEN 1 ELSE 0 END) AS easyCount, " +
        "COUNT(*) AS completedCount, " +
        "IFNULL(SUM(l.durationMs), 0) AS durationMs " +
        "FROM valid_review_logs l " +
        "JOIN $FIRST_REVIEWS_VIEW_NAME f ON f.cardId = ($LOGICAL_REVIEW_CARD_ID_SQL) " +
        "GROUP BY l.localDay" +
        ") daily"

@DatabaseView(
    viewName = "valid_review_logs",
    value =
        "SELECT l.* FROM review_logs l " +
            "JOIN wordbook_memberships m ON m.wordId = l.wordId AND m.bookCode = l.bookCode",
)
data class ValidReviewLogView(
    val id: String,
    val cardId: String,
    val wordId: String,
    val bookCode: String,
    val rating: String,
    val reviewedAt: Instant,
    val localDay: String,
    val elapsedDays: Int?,
    val scheduledDaysBefore: Int?,
    val scheduledDaysAfter: Int?,
    val difficultyBefore: Double?,
    val difficultyAfter: Double?,
    val stabilityBefore: Double?,
    val stabilityAfter: Double?,
    val retrievabilityBefore: Double?,
    val retrievabilityAfter: Double?,
    val durationMs: Long,
    val targetRetention: Double,
    val algorithm: String,
    val algorithmVersion: String,
    val stateAfter: String?,
    val dueAtAfter: Instant?,
)

@DatabaseView(
    viewName = FIRST_REVIEWS_VIEW_NAME,
    value = FIRST_REVIEWS_VIEW_SQL,
)
data class FirstReviewView(
    val cardId: String,
    val firstLogId: String,
    val firstReviewedAt: Instant,
)

@DatabaseView(
    viewName = REVIEW_DAILY_STATS_VIEW_NAME,
    value = REVIEW_DAILY_STATS_VIEW_SQL,
)
data class ReviewDailyStatsView(
    val localDay: String,
    val newCount: Int,
    val reviewCount: Int,
    val againCount: Int,
    val hardCount: Int,
    val goodCount: Int,
    val easyCount: Int,
    val completedCount: Int,
    val durationMs: Long,
    val recallAccuracy: Double,
    val passRate: Double,
    val estimatedMinutes: Int,
)

@DatabaseView(
    viewName = "word_card_view",
    value =
        "SELECT c.id AS cardId, m.wordId, m.bookCode, " +
            "c.state, c.difficulty, c.stability, c.retrievability, " +
            "c.scheduledDays, c.dueAt, c.lastReviewAt, " +
            "c.reviewCount, c.lapseCount, c.firstReviewedAt, " +
            "c.createdAt AS cardCreatedAt, c.updatedAt AS cardUpdatedAt, " +
            "w.word, w.meaning, w.phonetic, w.partOfSpeech, w.definition, " +
            "w.cefrLevel, w.cefrRank, w.frequency, w.sourceFlagsJson, w.coverageTier, " +
            "w.createdAt AS wordCreatedAt, w.updatedAt AS wordUpdatedAt, " +
            "m.orderIndex " +
            "FROM wordbook_memberships m " +
            "JOIN word_entries w ON w.id = m.wordId " +
            "LEFT JOIN review_cards c ON c.wordId = m.wordId AND c.bookCode = m.bookCode",
)
data class WordCardView(
    val cardId: String?,
    val wordId: String,
    val bookCode: String,
    val state: String?,
    val difficulty: Double?,
    val stability: Double?,
    val retrievability: Double?,
    val scheduledDays: Int?,
    val dueAt: Instant?,
    val lastReviewAt: Instant?,
    val reviewCount: Int?,
    val lapseCount: Int?,
    val firstReviewedAt: Instant?,
    val cardCreatedAt: Instant?,
    val cardUpdatedAt: Instant?,
    val word: String,
    val meaning: String,
    val phonetic: String?,
    val partOfSpeech: String?,
    val definition: String?,
    val cefrLevel: String?,
    val cefrRank: Double,
    val frequency: Double,
    val sourceFlagsJson: String,
    val coverageTier: String?,
    val wordCreatedAt: Instant,
    val wordUpdatedAt: Instant,
    val orderIndex: Int,
)
