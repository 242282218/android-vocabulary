package com.zzz.androidvocab.core.scheduler

import com.zzz.androidvocab.core.common.daysUntil
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.ReviewState
import io.github.openspacedrepetition.Card
import io.github.openspacedrepetition.Rating
import io.github.openspacedrepetition.Scheduler
import io.github.openspacedrepetition.State
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import javax.inject.Inject

class FsrsKotlinReviewScheduler
    @Inject
    constructor() : ReviewScheduler {
        override val algorithm: SchedulerAlgorithm = SchedulerAlgorithm.Fsrs

        override fun schedule(input: ScheduleInput): ScheduleResult {
            val scheduler = scheduler(input.targetRetention)
            val previous = input.card.toFsrsCard()
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
                        input.durationMs.toInt().coerceAtLeast(0),
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
                algorithmVersion = "java-fsrs-1.0.0",
            )
        }

        override fun retrievability(
            card: ReviewCard,
            now: java.time.Instant,
        ): Double? {
            if (card.lastReviewAt == null || card.stability == null || card.difficulty == null) {
                return card.retrievability
            }
            return scheduler(targetRetention = 0.9).getCardRetrievability(
                card.toFsrsCard(),
                now,
            )
        }

        private fun scheduler(targetRetention: Double): Scheduler =
            Scheduler
                .builder()
                .desiredRetention(targetRetention.coerceIn(0.7, 0.98))
                .enableFuzzing(false)
                .build()

        private fun ReviewCard.toFsrsCard(): Card {
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
                .due(dueAt ?: updatedAt)
                .lastReview(lastReviewAt)
                .build()
        }

        private fun stableCardIntId(value: String): Int {
            val checksum = CRC32()
            checksum.update(value.toByteArray(StandardCharsets.UTF_8))
            return (checksum.value and Int.MAX_VALUE.toLong()).toInt()
        }

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
                    if (scheduledDays >= 21) ReviewState.Mastered else ReviewState.Review
                }
            }
    }
