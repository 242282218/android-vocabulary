package com.zzz.androidvocab.core.database

import androidx.room.DatabaseView
import java.time.Instant

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
