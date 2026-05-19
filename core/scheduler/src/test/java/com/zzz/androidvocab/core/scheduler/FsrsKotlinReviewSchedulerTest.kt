package com.zzz.androidvocab.core.scheduler

import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.ReviewState
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class FsrsKotlinReviewSchedulerTest {
    private val scheduler = FsrsKotlinReviewScheduler()
    private val now = Instant.parse("2026-05-16T08:00:00Z")
    private val utc = ZoneId.of("UTC")

    @Test
    fun againCreatesShortInterval() {
        val result = scheduler.schedule(input(ReviewRating.Again))

        assertTrue(result.nextCard.scheduledDays <= 1)
        assertTrue(result.nextCard.lapseCount == 1)
    }

    @Test
    fun goodThenEasyGrowInterval() {
        val good = scheduler.schedule(input(ReviewRating.Good)).nextCard
        val later =
            scheduler
                .schedule(
                    ScheduleInput(good, ReviewRating.Easy, good.dueAt ?: now, utc, 0.9, 800),
                ).nextCard

        assertTrue(later.scheduledDays >= good.scheduledDays)
        assertTrue(later.reviewCount == 2)
    }

    @Test
    fun retrievabilityUsesCurrentTime() {
        val reviewed = scheduler.schedule(input(ReviewRating.Good)).nextCard
        val later = scheduler.retrievability(reviewed, (reviewed.dueAt ?: now).plusSeconds(86_400))

        assertTrue(later != null)
        assertTrue((later ?: 1.0) < (reviewed.retrievability ?: 1.0))
    }

    @Test
    fun scheduledDaysUseInputZone() {
        val reviewedAt = Instant.parse("2026-05-16T23:59:30Z")
        val utcResult = scheduler.schedule(input(ReviewRating.Again, reviewedAt, ZoneId.of("UTC"))).nextCard
        val shanghaiResult =
            scheduler
                .schedule(
                    input(ReviewRating.Again, reviewedAt, ZoneId.of("Asia/Shanghai")),
                ).nextCard

        assertTrue(utcResult.scheduledDays > shanghaiResult.scheduledDays)
    }

    private fun input(
        rating: ReviewRating,
        reviewedAt: Instant = now,
        zoneId: ZoneId = utc,
    ) = ScheduleInput(
        card =
            ReviewCard(
                id = "card-1",
                wordId = "word-1",
                bookCode = BookCode.CET4,
                state = ReviewState.New,
                difficulty = null,
                stability = null,
                retrievability = null,
                scheduledDays = 0,
                dueAt = now,
                lastReviewAt = null,
                reviewCount = 0,
                lapseCount = 0,
                firstReviewedAt = null,
                createdAt = reviewedAt,
                updatedAt = reviewedAt,
            ),
        rating = rating,
        reviewedAt = reviewedAt,
        zoneId = zoneId,
        targetRetention = 0.9,
        durationMs = 500,
    )
}
