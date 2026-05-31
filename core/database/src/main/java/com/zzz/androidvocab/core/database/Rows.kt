package com.zzz.androidvocab.core.database

data class ReviewQueueRow(
    val cardId: String?,
    val wordId: String,
    val bookCode: String,
    val state: String?,
    val difficulty: Double?,
    val stability: Double?,
    val retrievability: Double?,
    val scheduledDays: Int?,
    val dueAt: java.time.Instant?,
    val lastReviewAt: java.time.Instant?,
    val reviewCount: Int?,
    val lapseCount: Int?,
    val firstReviewedAt: java.time.Instant?,
    val cardCreatedAt: java.time.Instant?,
    val cardUpdatedAt: java.time.Instant?,
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
    val wordCreatedAt: java.time.Instant,
    val wordUpdatedAt: java.time.Instant,
)

data class BookProgressRow(
    val bookCode: String,
    val totalCount: Int,
    val learnedCount: Int,
    val masteredCount: Int,
    val dueCount: Int,
)

data class BookCountRow(
    val bookCode: String,
    val count: Int,
)

data class BookStatsRow(
    val bookCode: String,
    val totalCount: Int,
    val learnedCount: Int,
    val masteredCount: Int,
    val dueCount: Int,
    val learningCount: Int,
    val familiarCount: Int,
)

data class DueCardRow(
    val dueAt: java.time.Instant?,
)

data class DailyActivityRow(
    val localDay: String,
    val reviewCount: Int,
)

data class DifficultWordRow(
    val wordId: String,
    val againCount: Int,
    val hardCount: Int,
)
