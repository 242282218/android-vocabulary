package com.zzz.androidvocab.core.stats

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.database.BookCountRow
import com.zzz.androidvocab.core.database.BookStatsRow
import com.zzz.androidvocab.core.database.DailyActivityRow
import com.zzz.androidvocab.core.database.DailyRatingCountRow
import com.zzz.androidvocab.core.database.DailyStatsEntity
import com.zzz.androidvocab.core.database.DifficultWordRow
import com.zzz.androidvocab.core.database.DueCardRow
import com.zzz.androidvocab.core.database.ReviewCardEntity
import com.zzz.androidvocab.core.database.StatsDao
import com.zzz.androidvocab.core.database.WordEntryEntity
import com.zzz.androidvocab.core.domain.SettingsRepository
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewState
import com.zzz.androidvocab.core.scheduler.ReviewScheduler
import com.zzz.androidvocab.core.scheduler.ScheduleInput
import com.zzz.androidvocab.core.scheduler.ScheduleResult
import com.zzz.androidvocab.core.scheduler.SchedulerAlgorithm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class OfflineStatsRepositoryTest {
    private val now = Instant.parse("2026-05-16T08:00:00Z")
    private val defaultSettings = AppSettings()

    @Test
    fun retentionStatsUseCurrentSchedulerRetrievability() =
        runTest {
            val repository =
                OfflineStatsRepository(
                    statsDao = FakeStatsDao(reviewedCards = listOf(reviewCard(retrievability = 0.99))),
                    clockProvider = FixedClock(now),
                    scheduler = FakeScheduler(currentRetrievability = mapOf("card-1" to 0.42)),
                    settingsRepository = FakeSettingsRepository(defaultSettings),
                )

            val stats = repository.observeRetentionStats(days = 30, now = now).first()

            assertEquals(0.42, stats.averageRetrievability, 0.0)
            assertEquals(0.42, stats.p25Retrievability, 0.0)
            assertEquals(0.42, stats.p50Retrievability, 0.0)
        }

    @Test
    fun reviewLoadBucketsOverdueCardsIntoToday() =
        runTest {
            val repository =
                OfflineStatsRepository(
                    statsDao =
                        FakeStatsDao(
                            dueCards =
                                listOf(
                                    DueCardRow(Instant.parse("2026-05-15T12:00:00Z")),
                                    DueCardRow(Instant.parse("2026-05-17T03:00:00Z")),
                                ),
                        ),
                    clockProvider = FixedClock(now),
                    scheduler = FakeScheduler(),
                    settingsRepository = FakeSettingsRepository(defaultSettings),
                )

            val load = repository.observeReviewLoad(days = 3, now = now).first()

            assertEquals(listOf(1, 1, 0), load.map { it.dueCount })
            assertEquals(listOf("2026-05-16", "2026-05-17", "2026-05-18"), load.map { it.localDay })
        }

    @Test
    fun bookStatsDelegateToStatsDao() =
        runTest {
            val repository =
                OfflineStatsRepository(
                    statsDao =
                        FakeStatsDao(
                            bookTotals = listOf(BookCountRow(BookCode.CET4.name, 2)),
                            validReviewCards = listOf(reviewCard(retrievability = 0.90)),
                        ),
                    clockProvider = FixedClock(now),
                    scheduler = FakeScheduler(currentRetrievability = mapOf("card-1" to 0.40)),
                    settingsRepository = FakeSettingsRepository(defaultSettings),
                )

            val stats = repository.observeBookStats(now).first().single()

            assertEquals(2, stats.progress.totalCount)
            assertEquals(1, stats.progress.learnedCount)
            assertEquals(0, stats.progress.masteredCount)
            assertEquals(1, stats.unlearnedCount)
            assertEquals(1, stats.familiarCount)
        }

    @Test
    fun todayStatsComputesRemainingAndEstimate() =
        runTest {
            val settings = defaultSettings.copy(dailyNewLimit = 10)
            val repository =
                OfflineStatsRepository(
                    statsDao =
                        FakeStatsDao(
                            dailyRatings = listOf(DailyRatingCountRow("good", 2)),
                            dailyCompleted = 2,
                            dailyNewCount = 1,
                            dailyDurationMs = 61_000L,
                            dueCount = 5,
                        ),
                    clockProvider = FixedClock(now),
                    scheduler = FakeScheduler(),
                    settingsRepository = FakeSettingsRepository(settings),
                )

            val stats = repository.observeTodayStats(LocalDate.parse("2026-05-16"), now).first()

            assertEquals(5 + 9, stats.remainingCount)
            assertEquals(1, stats.reviewCount)
            assertEquals(1.0, stats.recallAccuracy, 0.0)
        }

    @Test
    fun averageReviewDurationIgnoresZeroDurations() =
        runTest {
            val repository =
                OfflineStatsRepository(
                    statsDao = FakeStatsDao(averageDurationMs = 3_500.4),
                    clockProvider = FixedClock(now),
                    scheduler = FakeScheduler(),
                    settingsRepository = FakeSettingsRepository(defaultSettings),
                )

            val average =
                repository
                    .observeAverageReviewDurationMs(days = 7, today = LocalDate.parse("2026-05-16"))
                    .first()

            assertEquals(3_500L, average)
        }

    private fun reviewCard(
        retrievability: Double,
        scheduledDays: Int = 21,
    ): ReviewCardEntity =
        ReviewCardEntity(
            id = "card-1",
            wordId = "word-1",
            bookCode = BookCode.CET4.name,
            state = ReviewState.Review.name,
            difficulty = 5.0,
            stability = 20.0,
            retrievability = retrievability,
            scheduledDays = scheduledDays,
            dueAt = now.plusSeconds(86_400),
            lastReviewAt = now.minusSeconds(86_400),
            reviewCount = 3,
            lapseCount = 0,
            firstReviewedAt = now.minusSeconds(172_800),
            createdAt = now.minusSeconds(172_800),
            updatedAt = now.minusSeconds(86_400),
        )
}

private class FakeStatsDao(
    private val dailyRatings: List<DailyRatingCountRow> = emptyList(),
    private val dailyCompleted: Int = 0,
    private val dailyNewCount: Int = 0,
    private val dailyDurationMs: Long = 0L,
    private val averageDurationMs: Double? = null,
    private val dueCards: List<DueCardRow> = emptyList(),
    private val dueCount: Int = 0,
    private val bookTotals: List<BookCountRow> = emptyList(),
    private val reviewedCards: List<ReviewCardEntity> = emptyList(),
    private val validReviewCards: List<ReviewCardEntity> = emptyList(),
) : StatsDao {
    override fun observeDailyRatingCounts(localDay: String): Flow<List<DailyRatingCountRow>> = flowOf(dailyRatings)

    override suspend fun upsertDailyStats(stats: DailyStatsEntity) = Unit

    override suspend fun clearDailyStats() = Unit

    override suspend fun dailyStatsCount(): Int = 0

    override suspend fun insertDailyStatsFromLogs(updatedAt: Instant) = Unit

    override suspend fun dailyRatingCounts(localDay: String): List<DailyRatingCountRow> = dailyRatings

    override fun observeDailyCompleted(localDay: String): Flow<Int> = flowOf(dailyCompleted)

    override suspend fun dailyCompleted(localDay: String): Int = dailyCompleted

    override fun observeDailyDurationMs(localDay: String): Flow<Long> = flowOf(dailyDurationMs)

    override suspend fun dailyDurationMs(localDay: String): Long = dailyDurationMs

    override fun observeAverageDurationMs(
        startDay: String,
        endDay: String,
    ): Flow<Double?> = flowOf(averageDurationMs)

    override fun observeDailyNewCount(localDay: String): Flow<Int> = flowOf(dailyNewCount)

    override suspend fun dailyNewCount(localDay: String): Int = dailyNewCount

    override fun observeDueCards(end: Instant): Flow<List<DueCardRow>> =
        flowOf(dueCards.filter { row -> row.dueAt?.let { dueAt -> dueAt < end } == true })

    override fun observeDueCount(
        now: Instant,
        bookCodes: List<String>,
    ): Flow<Int> = flowOf(dueCount)

    override fun observeBookTotals(): Flow<List<BookCountRow>> = flowOf(bookTotals)

    override fun observeValidReviewCards(): Flow<List<ReviewCardEntity>> = flowOf(validReviewCards)

    override fun observeBookStats(now: Instant): Flow<List<BookStatsRow>> = flowOf(emptyList())

    override fun observeReviewedCards(from: Instant): Flow<List<ReviewCardEntity>> = flowOf(reviewedCards)

    override fun observeActiveDays(): Flow<List<String>> = flowOf(emptyList())

    override fun observeDailyActivityRows(
        startDay: String,
        endDay: String,
    ): Flow<List<DailyActivityRow>> = flowOf(emptyList())

    override fun observeDifficultWordRows(from: Instant): Flow<List<DifficultWordRow>> = flowOf(emptyList())

    override suspend fun getWordsByIds(wordIds: List<String>): List<WordEntryEntity> = emptyList()
}

private class FakeScheduler(
    private val currentRetrievability: Map<String, Double> = emptyMap(),
) : ReviewScheduler {
    override val algorithm: SchedulerAlgorithm = SchedulerAlgorithm.Fsrs

    override fun schedule(input: ScheduleInput): ScheduleResult = error("Not used by this test")

    override fun retrievability(
        card: ReviewCard,
        now: Instant,
        targetRetention: Double,
    ): Double? = currentRetrievability[card.id] ?: card.retrievability
}

private class FakeSettingsRepository(
    appSettings: AppSettings,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = flowOf(appSettings)

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

    override suspend fun updateThemeMode(themeMode: com.zzz.androidvocab.core.model.ThemeMode) = Unit
}

private class FixedClock(
    private val now: Instant,
) : ClockProvider {
    override fun now(): Instant = now

    override fun zoneId(): ZoneId = ZoneId.of("UTC")

    override fun today(): LocalDate = LocalDate.ofInstant(now, zoneId())
}
