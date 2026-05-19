package com.zzz.androidvocab.core.model

import java.time.Instant

enum class ReviewRating(
    val wireName: String,
) {
    Again("again"),
    Hard("hard"),
    Good("good"),
    Easy("easy"),
}

enum class ReviewState {
    New,
    Learning,
    Review,
    Relearning,
    Mastered,
}

data class ReviewCard(
    val id: String,
    val wordId: String,
    val bookCode: BookCode,
    val state: ReviewState,
    val difficulty: Double?,
    val stability: Double?,
    val retrievability: Double?,
    val scheduledDays: Int,
    val dueAt: Instant?,
    val lastReviewAt: Instant?,
    val reviewCount: Int,
    val lapseCount: Int,
    val firstReviewedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun isMastered(now: Instant): Boolean =
        scheduledDays >= 21 && (retrievability ?: 0.0) >= 0.85 && (dueAt == null || dueAt > now)
}

data class ReviewLog(
    val id: String,
    val cardId: String,
    val wordId: String,
    val bookCode: BookCode,
    val rating: ReviewRating,
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
    val stateAfter: ReviewState? = null,
    val dueAtAfter: Instant? = null,
)

data class ReviewQueueItem(
    val card: ReviewCard,
    val word: WordEntry,
    val isNew: Boolean,
)

data class TodayQueue(
    val dueItems: List<ReviewQueueItem>,
    val newItems: List<ReviewQueueItem>,
) {
    val items: List<ReviewQueueItem> = dueItems + newItems
    val totalCount: Int = items.size
}

data class SubmitFeedbackCommand(
    val cardId: String,
    val rating: ReviewRating,
    val reviewedAt: Instant,
    val durationMs: Long,
    val targetRetention: Double,
)

data class ReviewResult(
    val reviewedItem: ReviewQueueItem,
    val nextCard: ReviewCard,
    val log: ReviewLog,
)

data class ReviewDataIntegrityReport(
    val cardsWithLogs: Int,
    val missingCacheCount: Int,
    val inconsistentCacheCount: Int,
    val legacyLogCardCount: Int,
) {
    val issueCount: Int = missingCacheCount + inconsistentCacheCount
}

data class ReviewDataRepairResult(
    val before: ReviewDataIntegrityReport,
    val after: ReviewDataIntegrityReport,
    val repairedCount: Int,
)
