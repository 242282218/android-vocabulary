package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
import com.zzz.androidvocab.core.model.ReviewDataRepairResult
import com.zzz.androidvocab.core.model.ReviewLog
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.ReviewResult
import com.zzz.androidvocab.core.model.ReviewState
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import com.zzz.androidvocab.core.model.ThemeMode
import com.zzz.androidvocab.core.model.TodayQueue
import com.zzz.androidvocab.core.model.WordEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SubmitReviewFeedbackUseCaseTest {
    @Test
    fun submitUsesTargetRetentionFromSettings() =
        runTest {
            val reviewedAt = Instant.parse("2026-05-16T08:00:00Z")
            val expectedLastReviewAt = Instant.parse("2026-05-15T08:00:00Z")
            val expectedReviewCount = 3
            val reviewRepository = RecordingSubmitReviewRepository()
            val useCase =
                SubmitReviewFeedbackUseCase(
                    reviewRepository = reviewRepository,
                    settingsRepository =
                        FakeSubmitSettingsRepository(
                            AppSettings(
                                selectedBooks = setOf(BookCode.CET4),
                                targetRetention = 0.83,
                            ),
                        ),
                    clockProvider = FixedSubmitClock(reviewedAt),
                )

            useCase(SUBMIT_CARD_ID, expectedLastReviewAt, expectedReviewCount, ReviewRating.Hard, durationMs = 2_400L)

            val command = reviewRepository.commands.single()
            assertEquals(SUBMIT_CARD_ID, command.cardId)
            assertEquals(ReviewRating.Hard, command.rating)
            assertEquals(reviewedAt, command.reviewedAt)
            assertEquals(expectedLastReviewAt, command.expectedLastReviewAt)
            assertEquals(expectedReviewCount, command.expectedReviewCount)
            assertEquals(2_400L, command.durationMs)
            assertEquals(0.83, command.targetRetention, 0.0)
        }
}

private class RecordingSubmitReviewRepository : ReviewRepository {
    val commands = mutableListOf<SubmitFeedbackCommand>()
    private val item = submitReviewQueueItem()

    override fun observeTodayQueue(
        now: Instant,
        selectedBooks: Set<BookCode>,
        dailyNewLimit: Int,
    ): Flow<TodayQueue> = flowOf(TodayQueue(dueItems = listOf(item), newItems = emptyList()))

    override suspend fun submitFeedback(command: SubmitFeedbackCommand): ReviewResult {
        commands += command
        return ReviewResult(item, item.card, submitReviewLog(command))
    }

    override suspend fun replayLogs(cardId: String): ReviewCard = item.card

    override suspend fun getQueueItem(cardId: String): ReviewQueueItem = item

    override suspend fun inspectReviewDataIntegrity(): ReviewDataIntegrityReport =
        ReviewDataIntegrityReport(
            cardsWithLogs = 0,
            missingCacheCount = 0,
            inconsistentCacheCount = 0,
            legacyLogCardCount = 0,
        )

    override suspend fun repairReviewDataCache(): ReviewDataRepairResult =
        ReviewDataRepairResult(
            before = inspectReviewDataIntegrity(),
            after = inspectReviewDataIntegrity(),
            repairedCount = 0,
        )
}

private class FakeSubmitSettingsRepository(
    initialSettings: AppSettings,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = MutableStateFlow(initialSettings)

    override suspend fun updateDailyNewLimit(value: Int) = Unit

    override suspend fun updateSelectedBooks(bookCodes: Set<BookCode>) = Unit

    override suspend fun toggleBook(bookCode: BookCode) = Unit

    override suspend fun updateTargetRetention(value: Double) = Unit

    override suspend fun updateReminder(
        enabled: Boolean,
        hour: Int,
        minute: Int,
    ) = Unit

    override suspend fun updateReminderEnabled(enabled: Boolean) = Unit

    override suspend fun updateReminderTime(
        hour: Int,
        minute: Int,
    ) = Unit

    override suspend fun updateThemeMode(themeMode: ThemeMode) = Unit
}

private class FixedSubmitClock(
    private val now: Instant,
) : ClockProvider {
    override fun now(): Instant = now

    override fun zoneId(): ZoneId = ZoneId.of("UTC")

    override fun today(): LocalDate = LocalDate.ofInstant(now, zoneId())
}

private fun submitReviewQueueItem(): ReviewQueueItem =
    ReviewQueueItem(
        card =
            ReviewCard(
                id = SUBMIT_CARD_ID,
                wordId = SUBMIT_WORD_ID,
                bookCode = BookCode.CET4,
                state = ReviewState.Review,
                difficulty = 5.0,
                stability = 8.0,
                retrievability = 0.9,
                scheduledDays = 8,
                dueAt = Instant.parse("2026-05-16T08:00:00Z"),
                lastReviewAt = Instant.parse("2026-05-15T08:00:00Z"),
                reviewCount = 1,
                lapseCount = 0,
                firstReviewedAt = Instant.parse("2026-05-15T08:00:00Z"),
                createdAt = Instant.parse("2026-05-15T08:00:00Z"),
                updatedAt = Instant.parse("2026-05-15T08:00:00Z"),
            ),
        word =
            WordEntry(
                id = SUBMIT_WORD_ID,
                word = "ability",
                meaning = "能力",
                phonetic = null,
                partOfSpeech = null,
                definition = null,
                cefrLevel = null,
                cefrRank = 0.0,
                frequency = 0.0,
                sourceFlags = emptyList(),
                coverageTier = null,
            ),
        isNew = false,
    )

private fun submitReviewLog(command: SubmitFeedbackCommand) =
    ReviewLog(
        id = "log-1",
        cardId = command.cardId,
        wordId = SUBMIT_WORD_ID,
        bookCode = BookCode.CET4,
        rating = command.rating,
        reviewedAt = command.reviewedAt,
        localDay = "2026-05-16",
        elapsedDays = 1,
        scheduledDaysBefore = 8,
        scheduledDaysAfter = 9,
        difficultyBefore = 5.0,
        difficultyAfter = 5.0,
        stabilityBefore = 8.0,
        stabilityAfter = 9.0,
        retrievabilityBefore = 0.9,
        retrievabilityAfter = 0.91,
        durationMs = command.durationMs,
        targetRetention = command.targetRetention,
        algorithm = "fsrs",
        algorithmVersion = "test",
    )

private const val SUBMIT_CARD_ID = "card|CET4|word-ability"
private const val SUBMIT_WORD_ID = "word-ability"
