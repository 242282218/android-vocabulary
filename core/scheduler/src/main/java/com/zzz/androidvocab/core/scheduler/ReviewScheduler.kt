package com.zzz.androidvocab.core.scheduler

import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewRating
import java.time.Instant
import java.time.ZoneId

enum class SchedulerAlgorithm {
    Fsrs,
}

data class ScheduleInput(
    val card: ReviewCard,
    val rating: ReviewRating,
    val reviewedAt: Instant,
    val zoneId: ZoneId,
    val targetRetention: Double,
    val durationMs: Long,
    val enableFuzzing: Boolean = true,
)

data class ReviewLogPatch(
    val scheduledDaysBefore: Int?,
    val scheduledDaysAfter: Int?,
    val difficultyBefore: Double?,
    val difficultyAfter: Double?,
    val stabilityBefore: Double?,
    val stabilityAfter: Double?,
    val retrievabilityBefore: Double?,
    val retrievabilityAfter: Double?,
)

data class ScheduleResult(
    val nextCard: ReviewCard,
    val logPatch: ReviewLogPatch,
    val algorithmVersion: String,
)

interface ReviewScheduler {
    val algorithm: SchedulerAlgorithm

    fun schedule(input: ScheduleInput): ScheduleResult

    fun retrievability(
        card: ReviewCard,
        now: Instant,
        targetRetention: Double,
    ): Double?
}

const val FSRS_DEFAULT_RETENTION = 0.9
