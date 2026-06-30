package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.BookStats
import com.zzz.androidvocab.core.model.DailyActivity
import com.zzz.androidvocab.core.model.DailyReviewLoad
import com.zzz.androidvocab.core.model.DifficultWord
import com.zzz.androidvocab.core.model.RetentionStats
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
import com.zzz.androidvocab.core.model.ReviewDataRepairResult
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewResult
import com.zzz.androidvocab.core.model.ReviewState
import com.zzz.androidvocab.core.model.StreakStats
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import com.zzz.androidvocab.core.model.ThemeMode
import com.zzz.androidvocab.core.model.TodayOverview
import com.zzz.androidvocab.core.model.TodayQueue
import com.zzz.androidvocab.core.model.TodayStats
import com.zzz.androidvocab.core.model.WordEntry
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun todayQueueUsesDailyNewLimitFromSettings() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            val reviewRepository = RecordingReviewRepository()
            val useCase =
                GetTodayQueueUseCase(
                    reviewRepository = reviewRepository,
                    settingsRepository =
                        FakeSettingsRepository(
                            AppSettings(selectedBooks = setOf(BookCode.CET4), dailyNewLimit = 0),
                        ),
                    clockProvider = SequenceClockProvider(listOf(now)),
                )

            val values = mutableListOf<TodayQueue>()
            val job = launch { useCase().take(1).toList(values) }
            job.join()

            assertEquals(listOf(0), reviewRepository.observedDailyNewLimits)
            assertEquals(1, values.size)
        }

    @Test
    fun reviewLoadUsesSelectedBooksFromSettings() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            val statsRepository = RecordingStatsRepository()
            val useCase =
                GetReviewLoadUseCase(
                    statsRepository = statsRepository,
                    settingsRepository =
                        FakeSettingsRepository(
                            AppSettings(selectedBooks = setOf(BookCode.TOEFL)),
                        ),
                    clockProvider = SequenceClockProvider(listOf(now)),
                )

            val values = useCase(days = 30).take(1).toList()

            assertEquals(1, values.size)
            assertEquals(listOf(setOf(BookCode.TOEFL)), statsRepository.observedReviewLoadBooks)
        }

    @Test
    fun statsUseCasesUseSelectedBooksFromSettings() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            val statsRepository = RecordingStatsRepository()
            val settingsRepository =
                FakeSettingsRepository(
                    AppSettings(selectedBooks = setOf(BookCode.TOEFL)),
                )

            GetDailyActivityUseCase(
                statsRepository = statsRepository,
                settingsRepository = settingsRepository,
                clockProvider = SequenceClockProvider(listOf(now)),
            )(days = 35).take(1).toList()
            GetRetentionStatsUseCase(
                statsRepository = statsRepository,
                settingsRepository = settingsRepository,
                clockProvider = SequenceClockProvider(listOf(now)),
            )(days = 30).take(1).toList()
            GetStreakUseCase(
                statsRepository = statsRepository,
                settingsRepository = settingsRepository,
                clockProvider = SequenceClockProvider(listOf(now)),
            )().take(1).toList()
            GetDifficultWordsUseCase(
                statsRepository = statsRepository,
                settingsRepository = settingsRepository,
                clockProvider = SequenceClockProvider(listOf(now)),
            )(days = 30).take(1).toList()

            assertEquals(listOf(setOf(BookCode.TOEFL)), statsRepository.observedDailyActivityBooks)
            assertEquals(listOf(setOf(BookCode.TOEFL)), statsRepository.observedRetentionBooks)
            assertEquals(listOf(setOf(BookCode.TOEFL)), statsRepository.observedStreakBooks)
            assertEquals(listOf(setOf(BookCode.TOEFL)), statsRepository.observedDifficultWordBooks)
        }

    @Test
    fun bookProgressUsesSelectedBooksFromSettings() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            val useCase =
                GetBookProgressUseCase(
                    vocabularyRepository =
                        RecordingVocabularyRepository(
                            progress =
                                listOf(
                                    BookProgress(BookCode.CET4, 10, 1, 0, 1),
                                    BookProgress(BookCode.TOEFL, 20, 2, 1, 2),
                                ),
                        ),
                    settingsRepository =
                        FakeSettingsRepository(
                            AppSettings(selectedBooks = setOf(BookCode.TOEFL)),
                        ),
                    clockProvider = SequenceClockProvider(listOf(now)),
                )

            val progress = useCase().take(1).toList().single()

            assertEquals(listOf(BookCode.TOEFL), progress.map { it.bookCode })
        }

    @Test
    fun bookProgressUsesProductOrderWhenSelectionIsEmpty() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            val useCase =
                GetBookProgressUseCase(
                    vocabularyRepository =
                        RecordingVocabularyRepository(
                            progress =
                                listOf(
                                    BookProgress(BookCode.TOEFL, 20, 2, 1, 2),
                                    BookProgress(BookCode.CET4, 10, 1, 0, 1),
                                    BookProgress(BookCode.IELTS, 30, 3, 1, 0),
                                ),
                        ),
                    settingsRepository =
                        FakeSettingsRepository(
                            AppSettings(selectedBooks = emptySet()),
                        ),
                    clockProvider = SequenceClockProvider(listOf(now)),
                )

            val progress = useCase().take(1).toList().single()

            assertEquals(listOf(BookCode.CET4, BookCode.IELTS, BookCode.TOEFL), progress.map { it.bookCode })
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun todayOverviewRefreshesLocalDateWhileCollected() =
        runTest {
            val first = Instant.parse("2026-05-16T23:59:30Z")
            val second = Instant.parse("2026-05-17T00:00:30Z")
            val reviewRepository = RecordingReviewRepository()
            val statsRepository = RecordingStatsRepository()
            val useCase =
                GetTodayOverviewUseCase(
                    reviewRepository = reviewRepository,
                    statsRepository = statsRepository,
                    settingsRepository = FakeSettingsRepository(),
                    clockProvider = SequenceClockProvider(listOf(first, second)),
                )

            val values = mutableListOf<TodayOverview>()
            val job = launch { useCase().take(2).toList(values) }
            advanceTimeBy(60_000)
            job.join()

            assertEquals(listOf(first, second), reviewRepository.observedNow)
            assertEquals(
                listOf(LocalDate.parse("2026-05-16"), LocalDate.parse("2026-05-17")),
                statsRepository.observedDays,
            )
            assertEquals(2, values.size)
        }

    @Test
    fun todayOverviewDisplaysQueueRemainingCountAndAverageEstimate() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            val useCase =
                GetTodayOverviewUseCase(
                    reviewRepository =
                        RecordingReviewRepository(
                            queue =
                                TodayQueue(
                                    dueItems = listOf(reviewQueueItem("card-1"), reviewQueueItem("card-2")),
                                    newItems = listOf(reviewQueueItem("card-3")),
                                ),
                        ),
                    statsRepository =
                        RecordingStatsRepository(
                            todayStats =
                                emptyTodayStats(
                                    localDay = "2026-05-16",
                                    remainingCount = 99,
                                    estimatedMinutes = 99,
                                ),
                            averageDurationMs = 30_000L,
                        ),
                    settingsRepository = FakeSettingsRepository(),
                    clockProvider = SequenceClockProvider(listOf(now)),
                )

            val overview = useCase().take(1).toList().single()

            assertEquals(3, overview.queue.totalCount)
            assertEquals(3, overview.stats.remainingCount)
            assertEquals(2, overview.stats.estimatedMinutes)
        }

    @Test
    fun todayOverviewUsesSelectedBooksForStatsAndAverageDuration() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            val statsRepository = RecordingStatsRepository()
            val useCase =
                GetTodayOverviewUseCase(
                    reviewRepository = RecordingReviewRepository(),
                    statsRepository = statsRepository,
                    settingsRepository =
                        FakeSettingsRepository(
                            AppSettings(selectedBooks = setOf(BookCode.TOEFL)),
                        ),
                    clockProvider = SequenceClockProvider(listOf(now)),
                )

            useCase().take(1).toList()

            assertEquals(listOf(setOf(BookCode.TOEFL)), statsRepository.observedTodayStatsBooks)
            assertEquals(listOf(setOf(BookCode.TOEFL)), statsRepository.observedAverageDurationBooks)
        }

    @Test
    fun todayOverviewExposesSelectedBooksInBookOrder() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            val useCase =
                GetTodayOverviewUseCase(
                    reviewRepository = RecordingReviewRepository(),
                    statsRepository = RecordingStatsRepository(),
                    settingsRepository =
                        FakeSettingsRepository(
                            AppSettings(selectedBooks = linkedSetOf(BookCode.TOEFL, BookCode.CET4)),
                        ),
                    clockProvider = SequenceClockProvider(listOf(now)),
                )

            val overview = useCase().take(1).toList().single()

            assertEquals(listOf(BookCode.CET4, BookCode.TOEFL), overview.selectedBooks)
        }

    @Test
    fun todayOverviewExposesAllBooksWhenSelectionIsEmpty() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            val useCase =
                GetTodayOverviewUseCase(
                    reviewRepository = RecordingReviewRepository(),
                    statsRepository = RecordingStatsRepository(),
                    settingsRepository =
                        FakeSettingsRepository(
                            AppSettings(selectedBooks = emptySet()),
                        ),
                    clockProvider = SequenceClockProvider(listOf(now)),
                )

            val overview = useCase().take(1).toList().single()

            assertEquals(BookCode.entries, overview.selectedBooks)
        }

    @Test
    fun submittedCardQueueStateUsesAllBooksQueue() =
        runTest {
            val reviewRepository = SubmittedCardAwareReviewRepository()
            reviewRepository.filteredQueue.value = TodayQueue(dueItems = emptyList(), newItems = emptyList())
            reviewRepository.allBooksQueue.value =
                TodayQueue(
                    dueItems = listOf(reviewQueueItem("card-pending")),
                    newItems = emptyList(),
                )
            val coordinator = ReviewSessionCoordinator().also { it.markSubmittedCard("card-pending") }
            val useCase =
                ObserveSubmittedCardQueueStateUseCase(
                    reviewRepository = reviewRepository,
                    settingsRepository = FakeSettingsRepository(),
                    reviewSessionCoordinator = coordinator,
                    clockProvider = SequenceClockProvider(listOf(Instant.parse("2026-05-16T08:00:00Z"))),
                )

            val state = useCase().take(1).toList().single()

            assertEquals("card-pending", state.submittedCardId)
            assertEquals(true, state.isStillInRawQueue)
            assertEquals(listOf(emptySet<BookCode>()), reviewRepository.observedSelectedBooks)
        }

    @Test
    fun submittedCardQueueStateSkipsRepositoryWhenNothingPending() =
        runTest {
            val reviewRepository = SubmittedCardAwareReviewRepository()
            val useCase =
                ObserveSubmittedCardQueueStateUseCase(
                    reviewRepository = reviewRepository,
                    settingsRepository = FakeSettingsRepository(),
                    reviewSessionCoordinator = ReviewSessionCoordinator(),
                    clockProvider = SequenceClockProvider(listOf(Instant.parse("2026-05-16T08:00:00Z"))),
                )

            val state = useCase().take(1).toList().single()

            assertEquals(null, state.submittedCardId)
            assertEquals(false, state.isStillInRawQueue)
            assertEquals(emptyList<Set<BookCode>>(), reviewRepository.observedSelectedBooks)
        }
}

private class RecordingReviewRepository(
    private val queue: TodayQueue = TodayQueue(dueItems = emptyList(), newItems = emptyList()),
) : ReviewRepository {
    val observedNow = mutableListOf<Instant>()
    val observedDailyNewLimits = mutableListOf<Int>()

    override fun observeTodayQueue(
        now: Instant,
        selectedBooks: Set<BookCode>,
        dailyNewLimit: Int,
    ): Flow<TodayQueue> {
        observedNow += now
        observedDailyNewLimits += dailyNewLimit
        return flowOf(queue)
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

private class SubmittedCardAwareReviewRepository : ReviewRepository {
    val observedSelectedBooks = mutableListOf<Set<BookCode>>()
    val filteredQueue = MutableStateFlow(TodayQueue(dueItems = emptyList(), newItems = emptyList()))
    val allBooksQueue = MutableStateFlow(TodayQueue(dueItems = emptyList(), newItems = emptyList()))

    override fun observeTodayQueue(
        now: Instant,
        selectedBooks: Set<BookCode>,
        dailyNewLimit: Int,
    ): Flow<TodayQueue> {
        observedSelectedBooks += selectedBooks
        return if (selectedBooks.isEmpty()) allBooksQueue else filteredQueue
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

private class FakeSettingsRepository(
    initialSettings: AppSettings = AppSettings(selectedBooks = setOf(BookCode.CET4), dailyNewLimit = 20),
) : SettingsRepository {
    override val settings: Flow<AppSettings> =
        MutableStateFlow(initialSettings)

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

private class RecordingStatsRepository(
    private val todayStats: TodayStats = emptyTodayStats(localDay = "2026-05-16"),
    private val averageDurationMs: Long? = null,
) : StatsRepository {
    val observedDays = mutableListOf<LocalDate>()
    val observedTodayStatsBooks = mutableListOf<Set<BookCode>>()
    val observedAverageDurationBooks = mutableListOf<Set<BookCode>>()
    val observedReviewLoadBooks = mutableListOf<Set<BookCode>>()
    val observedDailyActivityBooks = mutableListOf<Set<BookCode>>()
    val observedRetentionBooks = mutableListOf<Set<BookCode>>()
    val observedStreakBooks = mutableListOf<Set<BookCode>>()
    val observedDifficultWordBooks = mutableListOf<Set<BookCode>>()

    override fun observeTodayStats(
        localDay: LocalDate,
        now: Instant,
        selectedBooks: Set<BookCode>,
    ): Flow<TodayStats> {
        observedDays += localDay
        observedTodayStatsBooks += selectedBooks
        return flowOf(todayStats.copy(localDay = localDay.toString()))
    }

    override fun observeAverageReviewDurationMs(
        days: Int,
        today: LocalDate,
        selectedBooks: Set<BookCode>,
    ): Flow<Long?> {
        observedAverageDurationBooks += selectedBooks
        return flowOf(averageDurationMs)
    }

    override fun observeBookStats(now: Instant): Flow<List<BookStats>> = flowOf(emptyList())

    override fun observeReviewLoad(
        days: Int,
        now: Instant,
        selectedBooks: Set<BookCode>,
    ): Flow<List<DailyReviewLoad>> {
        observedReviewLoadBooks += selectedBooks
        return flowOf(emptyList())
    }

    override fun observeDailyActivity(
        days: Int,
        today: LocalDate,
        selectedBooks: Set<BookCode>,
    ): Flow<List<DailyActivity>> {
        observedDailyActivityBooks += selectedBooks
        return flowOf(emptyList())
    }

    override fun observeRetentionStats(
        days: Int,
        now: Instant,
        selectedBooks: Set<BookCode>,
    ): Flow<RetentionStats> {
        observedRetentionBooks += selectedBooks
        return flowOf(RetentionStats(0.0, 0.0, 0.0))
    }

    override fun observeStreakStats(
        today: LocalDate,
        selectedBooks: Set<BookCode>,
    ): Flow<StreakStats> {
        observedStreakBooks += selectedBooks
        return flowOf(StreakStats(0, 0, emptySet()))
    }

    override fun observeDifficultWords(
        days: Int,
        now: Instant,
        selectedBooks: Set<BookCode>,
    ): Flow<List<DifficultWord>> {
        observedDifficultWordBooks += selectedBooks
        return flowOf(emptyList())
    }

    override suspend fun rebuildDailyStatsCache(updatedAt: Instant): Int = 0
}

private fun emptyTodayStats(
    localDay: String,
    remainingCount: Int = 0,
    estimatedMinutes: Int = 0,
) = TodayStats(
    localDay = localDay,
    newCount = 0,
    reviewCount = 0,
    againCount = 0,
    hardCount = 0,
    goodCount = 0,
    easyCount = 0,
    completedCount = 0,
    remainingCount = remainingCount,
    recallAccuracy = 0.0,
    passRate = 0.0,
    estimatedMinutes = estimatedMinutes,
)

private class RecordingVocabularyRepository(
    private val progress: List<com.zzz.androidvocab.core.model.BookProgress>,
) : VocabularyRepository {
    override fun observeBookProgress(now: Instant): Flow<List<com.zzz.androidvocab.core.model.BookProgress>> =
        flowOf(progress)

    override fun searchWords(
        query: String,
        bookCodes: Set<BookCode>,
        statusFilter: com.zzz.androidvocab.core.model.WordStatusFilter,
        now: Instant,
    ): Flow<List<WordEntry>> = flowOf(emptyList())

    override fun observeWordDetail(wordId: String): Flow<com.zzz.androidvocab.core.model.WordDetail?> = flowOf(null)

    override fun observeSourceInfo(): Flow<List<com.zzz.androidvocab.core.model.SourceInfo>> = flowOf(emptyList())

    override suspend fun importPublishSafeVocabulary(): com.zzz.androidvocab.core.model.ImportResult = unsupported()
}

private fun reviewQueueItem(cardId: String): ReviewQueueItem =
    ReviewQueueItem(
        card =
            ReviewCard(
                id = cardId,
                wordId = "word-$cardId",
                bookCode = BookCode.CET4,
                state = ReviewState.Review,
                difficulty = 5.0,
                stability = 10.0,
                retrievability = 0.9,
                scheduledDays = 10,
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
                id = "word-$cardId",
                word = cardId,
                meaning = cardId,
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
