package com.zzz.androidvocab.core.stats

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.database.BookCountRow
import com.zzz.androidvocab.core.database.BookStatsRow
import com.zzz.androidvocab.core.database.DailyActivityRow
import com.zzz.androidvocab.core.database.DailyStatsEntity
import com.zzz.androidvocab.core.database.DifficultWordRow
import com.zzz.androidvocab.core.database.DueCardRow
import com.zzz.androidvocab.core.database.ReviewCardEntity
import com.zzz.androidvocab.core.database.ReviewDailyStatsView
import com.zzz.androidvocab.core.database.StatsDao
import com.zzz.androidvocab.core.database.WordEntryEntity
import com.zzz.androidvocab.core.domain.SettingsRepository
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.DifficultWord
import com.zzz.androidvocab.core.model.RetentionStats
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

            val stats =
                repository
                    .observeRetentionStats(days = 30, now = now, selectedBooks = setOf(BookCode.CET4))
                    .first()

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

            val load =
                repository
                    .observeReviewLoad(days = 3, now = now, selectedBooks = setOf(BookCode.CET4))
                    .first()

            assertEquals(listOf(1, 1, 0), load.map { it.dueCount })
            assertEquals(listOf("2026-05-16", "2026-05-17", "2026-05-18"), load.map { it.localDay })
        }

    @Test
    fun reviewLoadUsesSelectedBooks() =
        runTest {
            val statsDao = FakeStatsDao(dueCards = listOf(DueCardRow(now)))
            val repository =
                OfflineStatsRepository(
                    statsDao = statsDao,
                    clockProvider = FixedClock(now),
                    scheduler = FakeScheduler(),
                    settingsRepository = FakeSettingsRepository(defaultSettings),
                )

            repository
                .observeReviewLoad(
                    days = 3,
                    now = now,
                    selectedBooks = setOf(BookCode.CET4, BookCode.TOEFL),
                ).first()

            assertEquals(
                listOf(listOf(BookCode.CET4.name, BookCode.TOEFL.name)),
                statsDao.observedDueCardBookCodes,
            )
        }

    @Test
    fun reviewLoadSkipsDaoWhenWindowIsEmpty() =
        runTest {
            val statsDao = FakeStatsDao(dueCards = listOf(DueCardRow(now)))
            val repository =
                OfflineStatsRepository(
                    statsDao = statsDao,
                    clockProvider = FixedClock(now),
                    scheduler = FakeScheduler(),
                    settingsRepository = FakeSettingsRepository(defaultSettings),
                )

            val load =
                repository
                    .observeReviewLoad(days = 0, now = now, selectedBooks = setOf(BookCode.CET4))
                    .first()

            assertEquals(0, load.size)
            assertEquals(0, statsDao.observeDueCardsCalls)
        }

    @Test
    fun dailyActivitySkipsDaoWhenWindowIsEmpty() =
        runTest {
            val statsDao = FakeStatsDao()
            val repository =
                OfflineStatsRepository(
                    statsDao = statsDao,
                    clockProvider = FixedClock(now),
                    scheduler = FakeScheduler(),
                    settingsRepository = FakeSettingsRepository(defaultSettings),
                )

            val activity =
                repository
                    .observeDailyActivity(
                        days = -1,
                        today = LocalDate.parse("2026-05-16"),
                        selectedBooks = setOf(BookCode.CET4),
                    ).first()

            assertEquals(0, activity.size)
            assertEquals(0, statsDao.observeDailyActivityRowsCalls)
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
                            dailyStats =
                                ReviewDailyStatsView(
                                    localDay = "2026-05-16",
                                    newCount = 1,
                                    reviewCount = 1,
                                    againCount = 0,
                                    hardCount = 0,
                                    goodCount = 2,
                                    easyCount = 0,
                                    completedCount = 2,
                                    durationMs = 61_000L,
                                    recallAccuracy = 1.0,
                                    passRate = 1.0,
                                    estimatedMinutes = 2,
                                ),
                            dueCount = 5,
                        ),
                    clockProvider = FixedClock(now),
                    scheduler = FakeScheduler(),
                    settingsRepository = FakeSettingsRepository(settings),
                )

            val stats =
                repository
                    .observeTodayStats(
                        localDay = LocalDate.parse("2026-05-16"),
                        now = now,
                        selectedBooks = setOf(BookCode.CET4),
                    ).first()

            assertEquals(5 + 9, stats.remainingCount)
            assertEquals(1, stats.reviewCount)
            assertEquals(1.0, stats.recallAccuracy, 0.0)
        }

    @Test
    fun todayStatsAndAverageDurationUseSelectedBooks() =
        runTest {
            val statsDao =
                FakeStatsDao(
                    dailyStatsByBookCodes =
                        mapOf(
                            listOf(BookCode.CET4.name) to
                                ReviewDailyStatsView(
                                    localDay = "2026-05-16",
                                    newCount = 2,
                                    reviewCount = 3,
                                    againCount = 1,
                                    hardCount = 1,
                                    goodCount = 2,
                                    easyCount = 1,
                                    completedCount = 5,
                                    durationMs = 70_000L,
                                    recallAccuracy = 0.6,
                                    passRate = 0.8,
                                    estimatedMinutes = 0,
                                ),
                        ),
                    averageDurationByBookCodes = mapOf(listOf(BookCode.CET4.name) to 4_500.0),
                )
            val repository =
                OfflineStatsRepository(
                    statsDao = statsDao,
                    clockProvider = FixedClock(now),
                    scheduler = FakeScheduler(),
                    settingsRepository = FakeSettingsRepository(defaultSettings),
                )

            val stats =
                repository
                    .observeTodayStats(
                        localDay = LocalDate.parse("2026-05-16"),
                        now = now,
                        selectedBooks = setOf(BookCode.CET4),
                    ).first()
            val average =
                repository
                    .observeAverageReviewDurationMs(
                        days = 7,
                        today = LocalDate.parse("2026-05-16"),
                        selectedBooks = setOf(BookCode.CET4),
                    ).first()

            assertEquals(2, stats.newCount)
            assertEquals(3, stats.reviewCount)
            assertEquals(4_500L, average)
            assertEquals(listOf(listOf(BookCode.CET4.name)), statsDao.observedTodayStatsBookCodes)
            assertEquals(listOf(listOf(BookCode.CET4.name)), statsDao.observedAverageDurationBookCodes)
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
                    .observeAverageReviewDurationMs(
                        days = 7,
                        today = LocalDate.parse("2026-05-16"),
                        selectedBooks = setOf(BookCode.CET4),
                    ).first()

            assertEquals(3_500L, average)
        }

    @Test
    fun retentionAndDifficultWordsUseLocalCalendarLookbackWindow() =
        runTest {
            val boundaryNow = Instant.parse("2026-05-16T16:30:00Z")
            val statsDao = FakeStatsDao()
            val repository =
                OfflineStatsRepository(
                    statsDao = statsDao,
                    clockProvider = FixedClock(boundaryNow, ZoneId.of("Asia/Shanghai")),
                    scheduler = FakeScheduler(),
                    settingsRepository = FakeSettingsRepository(defaultSettings),
                )

            repository.observeRetentionStats(days = 2, now = boundaryNow, selectedBooks = setOf(BookCode.CET4)).first()
            repository.observeDifficultWords(days = 2, now = boundaryNow, selectedBooks = setOf(BookCode.CET4)).first()

            val expected = Instant.parse("2026-05-15T16:00:00Z")
            assertEquals(listOf(expected), statsDao.observedReviewedCardsFrom)
            assertEquals(listOf(expected), statsDao.observedDifficultWordRowsFrom)
        }

    @Test
    fun scopedStatsQueriesUseSelectedBooks() =
        runTest {
            val statsDao =
                FakeStatsDao(
                    reviewedCardsByBookCodes =
                        mapOf(
                            listOf(BookCode.CET4.name) to listOf(reviewCard(retrievability = 0.99)),
                        ),
                    activeDaysByBookCodes =
                        mapOf(
                            listOf(BookCode.CET4.name) to listOf("2026-05-16"),
                        ),
                    dailyActivityRowsByBookCodes =
                        mapOf(
                            listOf(BookCode.CET4.name) to listOf(DailyActivityRow("2026-05-16", 3)),
                        ),
                    difficultWordRowsByBookCodes =
                        mapOf(
                            listOf(BookCode.CET4.name) to listOf(DifficultWordRow("word-1", 2, 1)),
                        ),
                    wordsByIds =
                        listOf(
                            WordEntryEntity(
                                id = "word-1",
                                word = "abandon",
                                meaning = "放弃",
                                phonetic = null,
                                partOfSpeech = null,
                                definition = null,
                                cefrLevel = null,
                                cefrRank = 0.0,
                                frequency = 0.0,
                                sourceFlagsJson = "[]",
                                coverageTier = null,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )
            val repository =
                OfflineStatsRepository(
                    statsDao = statsDao,
                    clockProvider = FixedClock(now),
                    scheduler = FakeScheduler(currentRetrievability = mapOf("card-1" to 0.42)),
                    settingsRepository = FakeSettingsRepository(defaultSettings),
                )

            val activity =
                repository
                    .observeDailyActivity(
                        days = 7,
                        today = LocalDate.parse("2026-05-16"),
                        selectedBooks = setOf(BookCode.CET4),
                    ).first()
            val retention =
                repository
                    .observeRetentionStats(days = 30, now = now, selectedBooks = setOf(BookCode.CET4))
                    .first()
            val streak =
                repository
                    .observeStreakStats(LocalDate.parse("2026-05-16"), setOf(BookCode.CET4))
                    .first()
            val difficultWords =
                repository
                    .observeDifficultWords(days = 30, now = now, selectedBooks = setOf(BookCode.CET4))
                    .first()

            assertEquals(3, activity.last().reviewCount)
            assertEquals(0.42, retention.averageRetrievability, 0.0)
            assertEquals(1, streak.currentStreak)
            assertEquals(listOf("abandon"), difficultWords.map { it.word.word })
            assertEquals(listOf(listOf(BookCode.CET4.name)), statsDao.observedDailyActivityBookCodes)
            assertEquals(listOf(listOf(BookCode.CET4.name)), statsDao.observedReviewedCardsBookCodes)
            assertEquals(listOf(listOf(BookCode.CET4.name)), statsDao.observedActiveDaysBookCodes)
            assertEquals(listOf(listOf(BookCode.CET4.name)), statsDao.observedDifficultWordBookCodes)
        }

    @Test
    fun streakStatsAreNotTruncatedToRecent365Days() =
        runTest {
            val activeDays =
                (0L..399L).map { offset ->
                    LocalDate.parse("2026-05-16").minusDays(399L - offset).toString()
                }
            val repository =
                OfflineStatsRepository(
                    statsDao =
                        FakeStatsDao(
                            activeDaysByBookCodes =
                                mapOf(
                                    listOf(BookCode.CET4.name) to activeDays,
                                ),
                        ),
                    clockProvider = FixedClock(now),
                    scheduler = FakeScheduler(),
                    settingsRepository = FakeSettingsRepository(defaultSettings),
                )

            val streak =
                repository
                    .observeStreakStats(LocalDate.parse("2026-05-16"), setOf(BookCode.CET4))
                    .first()

            assertEquals(400, streak.currentStreak)
            assertEquals(400, streak.maxStreak)
        }

    @Test
    fun retentionAndDifficultWordsSkipDaoWhenWindowIsEmpty() =
        runTest {
            val statsDao = FakeStatsDao(reviewedCards = listOf(reviewCard(retrievability = 0.99)))
            val repository =
                OfflineStatsRepository(
                    statsDao = statsDao,
                    clockProvider = FixedClock(now),
                    scheduler = FakeScheduler(),
                    settingsRepository = FakeSettingsRepository(defaultSettings),
                )

            val retention =
                repository
                    .observeRetentionStats(days = 0, now = now, selectedBooks = setOf(BookCode.CET4))
                    .first()
            val difficultWords =
                repository
                    .observeDifficultWords(days = 0, now = now, selectedBooks = setOf(BookCode.CET4))
                    .first()

            assertEquals(RetentionStats(0.0, 0.0, 0.0), retention)
            assertEquals(emptyList<DifficultWord>(), difficultWords)
            assertEquals(emptyList<Instant>(), statsDao.observedReviewedCardsFrom)
            assertEquals(emptyList<Instant>(), statsDao.observedDifficultWordRowsFrom)
        }

    @Test
    fun remainingTodayCountClampsNegativeInputsAndOverflow() {
        assertEquals(14, remainingTodayCount(dueCount = 5, dailyNewLimit = 10, learnedNewCount = 1))
        assertEquals(0, remainingTodayCount(dueCount = -1, dailyNewLimit = 0, learnedNewCount = 3))
        assertEquals(
            Int.MAX_VALUE,
            remainingTodayCount(dueCount = Int.MAX_VALUE, dailyNewLimit = 100, learnedNewCount = 0),
        )
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
    private val dailyStats: ReviewDailyStatsView? = null,
    private val dailyStatsByBookCodes: Map<List<String>, ReviewDailyStatsView?> = emptyMap(),
    private val averageDurationMs: Double? = null,
    private val averageDurationByBookCodes: Map<List<String>, Double?> = emptyMap(),
    private val dueCards: List<DueCardRow> = emptyList(),
    private val dueCount: Int = 0,
    private val bookTotals: List<BookCountRow> = emptyList(),
    private val reviewedCards: List<ReviewCardEntity> = emptyList(),
    private val validReviewCards: List<ReviewCardEntity> = emptyList(),
    private val reviewedCardsByBookCodes: Map<List<String>, List<ReviewCardEntity>> = emptyMap(),
    private val activeDaysByBookCodes: Map<List<String>, List<String>> = emptyMap(),
    private val dailyActivityRowsByBookCodes: Map<List<String>, List<DailyActivityRow>> = emptyMap(),
    private val difficultWordRowsByBookCodes: Map<List<String>, List<DifficultWordRow>> = emptyMap(),
    private val wordsByIds: List<WordEntryEntity> = emptyList(),
) : StatsDao {
    var observeDueCardsCalls = 0
        private set
    var observeDailyActivityRowsCalls = 0
        private set
    val observedReviewedCardsFrom = mutableListOf<Instant>()
    val observedDifficultWordRowsFrom = mutableListOf<Instant>()
    val observedDueCardBookCodes = mutableListOf<List<String>>()
    val observedTodayStatsBookCodes = mutableListOf<List<String>>()
    val observedAverageDurationBookCodes = mutableListOf<List<String>>()
    val observedReviewedCardsBookCodes = mutableListOf<List<String>>()
    val observedActiveDaysBookCodes = mutableListOf<List<String>>()
    val observedDailyActivityBookCodes = mutableListOf<List<String>>()
    val observedDifficultWordBookCodes = mutableListOf<List<String>>()

    override suspend fun upsertDailyStats(stats: DailyStatsEntity) = Unit

    override suspend fun clearDailyStats() = Unit

    override suspend fun dailyStatsCount(): Int = 0

    override suspend fun dailyStats(): List<DailyStatsEntity> = emptyList()

    override suspend fun insertDailyStatsFromLogs(updatedAt: Instant) = Unit

    override suspend fun dailyStatsFromLogs(
        localDay: String,
        updatedAt: Instant,
    ): DailyStatsEntity? = null

    override suspend fun dailyStatsFromLogsRows(): List<ReviewDailyStatsView> = emptyList()

    override fun observeDailyStatsFromLogs(
        localDay: String,
        bookCodes: List<String>,
    ): Flow<ReviewDailyStatsView?> {
        observedTodayStatsBookCodes += bookCodes
        val scopedStats = dailyStatsByBookCodes[bookCodes]
        return flowOf(scopedStats?.takeIf { it.localDay == localDay } ?: dailyStats?.takeIf { it.localDay == localDay })
    }

    override fun observeAverageDurationMs(
        startDay: String,
        endDay: String,
        bookCodes: List<String>,
    ): Flow<Double?> {
        observedAverageDurationBookCodes += bookCodes
        return flowOf(averageDurationByBookCodes[bookCodes] ?: averageDurationMs)
    }

    override fun observeDueCards(
        end: Instant,
        bookCodes: List<String>,
    ): Flow<List<DueCardRow>> {
        observeDueCardsCalls += 1
        observedDueCardBookCodes += bookCodes
        return flowOf(dueCards.filter { row -> row.dueAt?.let { dueAt -> dueAt < end } == true })
    }

    override fun observeDueCount(
        now: Instant,
        bookCodes: List<String>,
    ): Flow<Int> = flowOf(dueCount)

    override fun observeBookTotals(): Flow<List<BookCountRow>> = flowOf(bookTotals)

    override fun observeValidReviewCards(): Flow<List<ReviewCardEntity>> = flowOf(validReviewCards)

    override fun observeBookStats(now: Instant): Flow<List<BookStatsRow>> = flowOf(emptyList())

    override fun observeReviewedCards(
        from: Instant,
        bookCodes: List<String>,
    ): Flow<List<ReviewCardEntity>> {
        observedReviewedCardsFrom += from
        observedReviewedCardsBookCodes += bookCodes
        return flowOf(reviewedCardsByBookCodes[bookCodes] ?: reviewedCards)
    }

    override fun observeActiveDays(bookCodes: List<String>): Flow<List<String>> {
        observedActiveDaysBookCodes += bookCodes
        return flowOf(activeDaysByBookCodes[bookCodes] ?: emptyList())
    }

    override fun observeDailyActivityRows(
        startDay: String,
        endDay: String,
        bookCodes: List<String>,
    ): Flow<List<DailyActivityRow>> {
        observeDailyActivityRowsCalls += 1
        observedDailyActivityBookCodes += bookCodes
        return flowOf(dailyActivityRowsByBookCodes[bookCodes] ?: emptyList())
    }

    override fun observeDifficultWordRows(
        from: Instant,
        bookCodes: List<String>,
    ): Flow<List<DifficultWordRow>> {
        observedDifficultWordRowsFrom += from
        observedDifficultWordBookCodes += bookCodes
        return flowOf(difficultWordRowsByBookCodes[bookCodes] ?: emptyList())
    }

    override suspend fun getWordsByIds(wordIds: List<String>): List<WordEntryEntity> =
        wordsByIds.filter { it.id in wordIds }
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
    private val zoneId: ZoneId = ZoneId.of("UTC"),
) : ClockProvider {
    override fun now(): Instant = now

    override fun zoneId(): ZoneId = zoneId

    override fun today(): LocalDate = LocalDate.ofInstant(now, zoneId())
}
