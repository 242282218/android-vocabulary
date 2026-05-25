package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
import com.zzz.androidvocab.core.model.ReviewDataRepairResult
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewResult
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import com.zzz.androidvocab.core.model.ThemeMode
import com.zzz.androidvocab.core.model.TodayQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class UseCasesTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun todayQueueRefreshesNowWhileCollected() =
        runTest {
            val first = Instant.parse("2026-05-16T08:00:00Z")
            val second = Instant.parse("2026-05-16T08:01:00Z")
            val reviewRepository = RecordingReviewRepository()
            val useCase =
                GetTodayQueueUseCase(
                    reviewRepository = reviewRepository,
                    settingsRepository = FakeSettingsRepository(),
                    clockProvider = SequenceClockProvider(listOf(first, second)),
                )

            val values = mutableListOf<TodayQueue>()
            val job = launch { useCase().take(2).toList(values) }
            advanceTimeBy(60_000)
            job.join()

            assertEquals(listOf(first, second), reviewRepository.observedNow)
            assertEquals(2, values.size)
        }

    @Test
    fun estimateRemainingMinutesUsesAverageDuration() {
        assertEquals(2, estimateRemainingMinutes(remainingCount = 20, averageDurationMs = 3_500L))
    }

    @Test
    fun estimateRemainingMinutesUsesDefaultWhenHistoryIsMissing() {
        assertEquals(1, estimateRemainingMinutes(remainingCount = 2, averageDurationMs = null))
        assertEquals(0, estimateRemainingMinutes(remainingCount = 0, averageDurationMs = 8_000L))
    }
}

private class RecordingReviewRepository : ReviewRepository {
    val observedNow = mutableListOf<Instant>()

    override fun observeTodayQueue(
        now: Instant,
        selectedBooks: Set<BookCode>,
        dailyNewLimit: Int,
    ): Flow<TodayQueue> {
        observedNow += now
        return flowOf(TodayQueue(dueItems = emptyList(), newItems = emptyList()))
    }

    override suspend fun submitFeedback(command: SubmitFeedbackCommand): ReviewResult = unsupported()

    override suspend fun replayLogs(cardId: String): ReviewCard = unsupported()

    override suspend fun getQueueItem(cardId: String): ReviewQueueItem = unsupported()

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

private class FakeSettingsRepository : SettingsRepository {
    override val settings: Flow<AppSettings> =
        MutableStateFlow(AppSettings(selectedBooks = setOf(BookCode.CET4), dailyNewLimit = 20))

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

private class SequenceClockProvider(
    private val instants: List<Instant>,
) : ClockProvider {
    private var index = 0

    override fun now(): Instant {
        val value = instants[index.coerceAtMost(instants.lastIndex)]
        index += 1
        return value
    }

    override fun zoneId(): ZoneId = ZoneId.of("UTC")

    override fun today(): LocalDate = LocalDate.ofInstant(now(), zoneId())
}

private fun unsupported(): Nothing = error("Not used by this test")
