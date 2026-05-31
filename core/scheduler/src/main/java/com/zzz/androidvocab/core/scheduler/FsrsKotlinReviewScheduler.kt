package com.zzz.androidvocab.core.scheduler

import com.zzz.androidvocab.core.common.daysUntil
import com.zzz.androidvocab.core.model.MASTERED_SCHEDULED_DAYS
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.ReviewState
import io.github.openspacedrepetition.Card
import io.github.openspacedrepetition.Rating
import io.github.openspacedrepetition.Scheduler
import io.github.openspacedrepetition.State
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class FsrsKotlinReviewScheduler
    @Inject
    constructor() : ReviewScheduler {
        override val algorithm: SchedulerAlgorithm = SchedulerAlgorithm.Fsrs

        private val schedulerCache = ConcurrentHashMap<SchedulerKey, Scheduler>()

        override fun schedule(input: ScheduleInput): ScheduleResult {
            val scheduler = scheduler(input.targetRetention, input.enableFuzzing)
            val previous = input.card.toFsrsCard(input.reviewedAt)
            val beforeRetrievability =
                input.card.lastReviewAt?.let {
                    scheduler.getCardRetrievability(previous, input.reviewedAt)
                }
            val reviewed =
                scheduler
                    .reviewCard(
                        previous,
                        input.rating.toFsrsRating(),
                        input.reviewedAt,
                        input.durationMs.toFsrsDurationMs(),
                    ).card()
            val scheduledDaysAfter = daysUntil(input.reviewedAt, reviewed.due, input.zoneId)
            val nextCard =
                input.card.copy(
                    state = reviewed.state.toReviewState(scheduledDaysAfter),
                    difficulty = reviewed.difficulty,
                    stability = reviewed.stability,
                    retrievability = scheduler.getCardRetrievability(reviewed, input.reviewedAt),
                    scheduledDays = scheduledDaysAfter,
                    dueAt = reviewed.due,
                    lastReviewAt = input.reviewedAt,
                    reviewCount = input.card.reviewCount + 1,
                    lapseCount = input.card.lapseCount + if (input.rating == ReviewRating.Again) 1 else 0,
                    firstReviewedAt = input.card.firstReviewedAt ?: input.reviewedAt,
                    updatedAt = input.reviewedAt,
                )
            return ScheduleResult(
                nextCard = nextCard,
                logPatch =
                    ReviewLogPatch(
                        scheduledDaysBefore = input.card.scheduledDays,
                        scheduledDaysAfter = scheduledDaysAfter,
                        difficultyBefore = input.card.difficulty,
                        difficultyAfter = reviewed.difficulty,
                        stabilityBefore = input.card.stability,
                        stabilityAfter = reviewed.stability,
                        retrievabilityBefore = beforeRetrievability ?: input.card.retrievability,
                        retrievabilityAfter = nextCard.retrievability,
                    ),
                algorithmVersion = FSRS_ALGORITHM_VERSION,
            )
        }

        override fun retrievability(
            card: ReviewCard,
            now: java.time.Instant,
            targetRetention: Double,
        ): Double? {
            if (card.lastReviewAt == null || card.stability == null || card.difficulty == null) {
                return card.retrievability
            }
            return scheduler(targetRetention = targetRetention).getCardRetrievability(
                card.toFsrsCard(now),
                now,
            )
        }

        private fun scheduler(
            targetRetention: Double,
            enableFuzzing: Boolean = true,
        ): Scheduler {
            val key = SchedulerKey(targetRetention.coerceIn(0.7, 0.98), enableFuzzing)
            return schedulerCache.computeIfAbsent(key) {
                Scheduler
                    .builder()
                    .desiredRetention(it.targetRetention)
                    .enableFuzzing(it.enableFuzzing)
                    .build()
            }
        }

        private data class SchedulerKey(
            val targetRetention: Double,
            val enableFuzzing: Boolean,
        )

        private fun ReviewCard.toFsrsCard(now: Instant): Card {
            val state =
                when (state) {
                    ReviewState.New,
                    ReviewState.Learning,
                    -> State.LEARNING
                    ReviewState.Review,
                    ReviewState.Mastered,
                    -> State.REVIEW
                    ReviewState.Relearning -> State.RELEARNING
                }
            return Card
                .builder()
                .cardId(stableCardIntId(id))
                .state(state)
                .stability(stability)
                .difficulty(difficulty)
                .due(dueAt ?: now)
                .lastReview(lastReviewAt)
                .build()
        }

        private fun stableCardIntId(value: String): Int = value.hashCode() and Int.MAX_VALUE

        private fun ReviewRating.toFsrsRating(): Rating =
            when (this) {
                ReviewRating.Again -> Rating.AGAIN
                ReviewRating.Hard -> Rating.HARD
                ReviewRating.Good -> Rating.GOOD
                ReviewRating.Easy -> Rating.EASY
            }

        private fun State.toReviewState(scheduledDays: Int): ReviewState =
            when (this) {
                State.LEARNING -> ReviewState.Learning
                State.RELEARNING -> ReviewState.Relearning
                State.REVIEW -> {
                    if (scheduledDays >= MASTERED_SCHEDULED_DAYS) ReviewState.Mastered else ReviewState.Review
                }
            }
    }

private const val FSRS_ALGORITHM_VERSION = "java-fsrs-1.0.0"
private const val MIN_DURATION_MS = 0
private const val MAX_DURATION_MS = Int.MAX_VALUE

internal fun Long.toFsrsDurationMs(): Int = coerceIn(MIN_DURATION_MS.toLong(), MAX_DURATION_MS.toLong()).toInt()
