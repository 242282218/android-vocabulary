package com.zzz.androidvocab.feature.today

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.domain.GetReviewLoadUseCase
import com.zzz.androidvocab.core.domain.GetTodayOverviewUseCase
import com.zzz.androidvocab.core.domain.ImportVocabularyUseCase
import com.zzz.androidvocab.core.domain.ObserveSubmittedCardQueueStateUseCase
import com.zzz.androidvocab.core.domain.ReviewRepository
import com.zzz.androidvocab.core.domain.ReviewSessionCoordinator
import com.zzz.androidvocab.core.domain.SettingsRepository
import com.zzz.androidvocab.core.domain.StatsRepository
import com.zzz.androidvocab.core.domain.VocabularyRepository
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.BookStats
import com.zzz.androidvocab.core.model.DailyActivity
import com.zzz.androidvocab.core.model.DailyReviewLoad
import com.zzz.androidvocab.core.model.DifficultWord
import com.zzz.androidvocab.core.model.ImportResult
import com.zzz.androidvocab.core.model.RetentionStats
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
import com.zzz.androidvocab.core.model.ReviewDataRepairResult
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewResult
import com.zzz.androidvocab.core.model.SourceInfo
import com.zzz.androidvocab.core.model.StreakStats
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import com.zzz.androidvocab.core.model.ThemeMode
import com.zzz.androidvocab.core.model.TodayQueue
import com.zzz.androidvocab.core.model.TodayStats
import com.zzz.androidvocab.core.model.WordDetail
import com.zzz.androidvocab.core.model.WordEntry
import com.zzz.androidvocab.core.model.WordStatusFilter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialImportKeepsTodayScreenInImportingStateUntilFinished() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val importAttempt = ImportAttempt()
            val vocabularyRepository = FakeVocabularyRepository(importAttempt)
            val viewModel = viewModel(vocabularyRepository)

            val importing =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { !it.isLoading && it.isImporting }
                }
            runCurrent()

            assertEquals(true, importing.await().isImporting)

            importAttempt.complete()
            val ready =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { !it.isLoading && !it.isImporting }
                }
            runCurrent()

            assertNull(ready.await().errorMessage)
        }

    @Test
    fun retryImportIgnoresDuplicateTriggerWhileRunning() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val failedInitialImport = ImportAttempt(failure = IllegalStateException("broken assets"))
            val retryImport = ImportAttempt()
            val vocabularyRepository = FakeVocabularyRepository(failedInitialImport, retryImport)
            val viewModel = viewModel(vocabularyRepository)

            val failed =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { !it.isLoading && !it.isImporting && it.errorMessage != null }
                }
            runCurrent()
            failedInitialImport.complete()
            runCurrent()

            assertEquals(true, failed.await().errorMessage != null)
            assertEquals(1, vocabularyRepository.callCount)

            viewModel.retryImport()
            viewModel.retryImport()
            runCurrent()

            assertEquals(true, viewModel.uiState.value.isImporting)
            assertEquals(2, vocabularyRepository.callCount)

            retryImport.complete()
            val ready =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { !it.isLoading && !it.isImporting }
                }
            runCurrent()

            assertNull(ready.await().errorMessage)
        }

    @Test
    fun submittedCardIsHiddenFromTodayWhileQueueStillContainsStaleEntry() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val completedImport = ImportAttempt().apply { complete() }
            val vocabularyRepository = FakeVocabularyRepository(completedImport)
            val reviewRepository = FakeReviewRepository()
            reviewRepository.queue.value =
                TodayQueue(
                    dueItems = listOf(reviewQueueItem(cardId = CARD_ID, wordId = WORD_ID)),
                    newItems = emptyList(),
                )
            val reviewSessionCoordinator = ReviewSessionCoordinator().also { it.markSubmittedCard(CARD_ID) }
            val viewModel =
                viewModel(
                    vocabularyRepository = vocabularyRepository,
                    reviewRepository = reviewRepository,
                    reviewSessionCoordinator = reviewSessionCoordinator,
                )

            val ready =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { !it.isLoading && !it.isImporting }
                }
            runCurrent()

            val state = ready.await()
            assertEquals(0, state.overview?.queue?.totalCount)
            assertEquals(0, state.overview?.stats?.remainingCount)
            assertEquals(true, state.isRefreshingReviewSession)
        }

    @Test
    fun todayShowsNextCardCountWhilePendingSubmittedCardIsStillInRawQueue() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val completedImport = ImportAttempt().apply { complete() }
            val vocabularyRepository = FakeVocabularyRepository(completedImport)
            val reviewRepository = FakeReviewRepository()
            reviewRepository.queue.value =
                TodayQueue(
                    dueItems =
                        listOf(
                            reviewQueueItem(cardId = CARD_ID, wordId = WORD_ID),
                            reviewQueueItem(cardId = "card-2", wordId = "word-2"),
                        ),
                    newItems = emptyList(),
                )
            val reviewSessionCoordinator = ReviewSessionCoordinator().also { it.markSubmittedCard(CARD_ID) }
            val viewModel =
                viewModel(
                    vocabularyRepository = vocabularyRepository,
                    reviewRepository = reviewRepository,
                    reviewSessionCoordinator = reviewSessionCoordinator,
                )

            val ready =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { !it.isLoading && !it.isImporting }
                }
            runCurrent()

            val state = ready.await()
            assertEquals(1, state.overview?.queue?.totalCount)
            assertEquals(1, state.overview?.stats?.remainingCount)
            assertEquals(false, state.isRefreshingReviewSession)
        }

    @Test
    fun todayClearsRefreshingStateWhenSubmittedCardActuallyLeavesQueue() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val completedImport = ImportAttempt().apply { complete() }
            val vocabularyRepository = FakeVocabularyRepository(completedImport)
            val reviewRepository = FakeReviewRepository()
            reviewRepository.queue.value =
                TodayQueue(
                    dueItems = listOf(reviewQueueItem(cardId = CARD_ID, wordId = WORD_ID)),
                    newItems = emptyList(),
                )
            val reviewSessionCoordinator = ReviewSessionCoordinator().also { it.markSubmittedCard(CARD_ID) }
            val viewModel =
                viewModel(
                    vocabularyRepository = vocabularyRepository,
                    reviewRepository = reviewRepository,
                    reviewSessionCoordinator = reviewSessionCoordinator,
                )

            val ready =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { !it.isLoading && !it.isImporting }
                }
            runCurrent()
            ready.await()

            reviewRepository.queue.value = TodayQueue(dueItems = emptyList(), newItems = emptyList())
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(false, state.isRefreshingReviewSession)
            assertEquals(0, state.overview?.queue?.totalCount)
            assertEquals(null, reviewSessionCoordinator.submittedCardId.value)
        }

    @Test
    fun keepsSubmittedCardPendingWhenOnlyFilteredQueueDropsIt() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val completedImport = ImportAttempt().apply { complete() }
            val vocabularyRepository = FakeVocabularyRepository(completedImport)
            val reviewRepository = FakeReviewRepository()
            reviewRepository.queue.value = TodayQueue(dueItems = emptyList(), newItems = emptyList())
            reviewRepository.allBooksQueue.value =
                TodayQueue(
                    dueItems = listOf(reviewQueueItem(cardId = CARD_ID, wordId = WORD_ID)),
                    newItems = emptyList(),
                )
            val reviewSessionCoordinator = ReviewSessionCoordinator().also { it.markSubmittedCard(CARD_ID) }
            val viewModel =
                viewModel(
                    vocabularyRepository = vocabularyRepository,
                    reviewRepository = reviewRepository,
                    reviewSessionCoordinator = reviewSessionCoordinator,
                )

            val ready =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { !it.isLoading && !it.isImporting }
                }
            runCurrent()

            val state = ready.await()
            assertEquals(false, state.isRefreshingReviewSession)
            assertEquals(0, state.overview?.queue?.totalCount)
            assertEquals(CARD_ID, reviewSessionCoordinator.submittedCardId.value)
        }

    private fun viewModel(
        vocabularyRepository: VocabularyRepository,
        reviewRepository: FakeReviewRepository = FakeReviewRepository(),
        reviewSessionCoordinator: ReviewSessionCoordinator = ReviewSessionCoordinator(),
    ): TodayViewModel {
        val settingsRepository = FakeSettingsRepository()
        val statsRepository = FakeStatsRepository()
        val clockProvider = FixedClockProvider()
        return TodayViewModel(
            importVocabularyUseCase = ImportVocabularyUseCase(vocabularyRepository),
            getTodayOverviewUseCase =
                GetTodayOverviewUseCase(
                    reviewRepository = reviewRepository,
                    statsRepository = statsRepository,
                    settingsRepository = settingsRepository,
                    clockProvider = clockProvider,
                ),
            getReviewLoadUseCase =
                GetReviewLoadUseCase(
                    statsRepository = statsRepository,
                    settingsRepository = settingsRepository,
                    clockProvider = clockProvider,
                ),
            observeSubmittedCardQueueStateUseCase =
                ObserveSubmittedCardQueueStateUseCase(
                    reviewRepository = reviewRepository,
                    settingsRepository = settingsRepository,
                    reviewSessionCoordinator = reviewSessionCoordinator,
                    clockProvider = clockProvider,
                ),
            reviewSessionCoordinator = reviewSessionCoordinator,
        )
    }
}

private class FakeVocabularyRepository(
    vararg attempts: ImportAttempt,
) : VocabularyRepository {
    private val attempts = ArrayDeque(attempts.toList())
    var callCount: Int = 0
        private set

    override fun observeBookProgress(now: Instant): Flow<List<BookProgress>> = flowOf(emptyList())

    override fun searchWords(
        query: String,
        bookCodes: Set<BookCode>,
        statusFilter: WordStatusFilter,
        now: Instant,
    ): Flow<List<WordEntry>> = flowOf(emptyList())

    override fun observeWordDetail(wordId: String): Flow<WordDetail?> = flowOf(null)

    override fun observeSourceInfo(): Flow<List<SourceInfo>> = flowOf(emptyList())

    override suspend fun importPublishSafeVocabulary(): ImportResult {
        callCount += 1
        val attempt = if (attempts.isEmpty()) error("Unexpected import attempt") else attempts.removeFirst()
        attempt.await()
        return ImportResult(
            importedWords = 0,
            memberships = 0,
            bookCounts = emptyMap(),
            generatedAt = "2026-05-16",
        )
    }
}

private class ImportAttempt(
    private val failure: Exception? = null,
) {
    private val completion = CompletableDeferred<Unit>()

    fun complete() {
        completion.complete(Unit)
    }

    suspend fun await() {
        completion.await()
        failure?.let { throw it }
    }
}

private class FakeReviewRepository : ReviewRepository {
    val queue = MutableStateFlow(TodayQueue(dueItems = emptyList(), newItems = emptyList()))
    val allBooksQueue = MutableStateFlow(TodayQueue(dueItems = emptyList(), newItems = emptyList()))

    override fun observeTodayQueue(
        now: Instant,
        selectedBooks: Set<BookCode>,
        dailyNewLimit: Int,
    ): Flow<TodayQueue> = if (selectedBooks.isEmpty()) allBooksQueue else queue

    override suspend fun submitFeedback(command: SubmitFeedbackCommand): ReviewResult = unused()

    override suspend fun replayLogs(cardId: String): ReviewCard = unused()

    override suspend fun getQueueItem(cardId: String): ReviewQueueItem = unused()

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

private class FakeStatsRepository : StatsRepository {
    override fun observeTodayStats(
        localDay: LocalDate,
        now: Instant,
        selectedBooks: Set<BookCode>,
    ): Flow<TodayStats> =
        flowOf(
            TodayStats(
                localDay = localDay.toString(),
                newCount = 0,
                reviewCount = 0,
                againCount = 0,
                hardCount = 0,
                goodCount = 0,
                easyCount = 0,
                completedCount = 0,
                remainingCount = 0,
                recallAccuracy = 0.0,
                passRate = 0.0,
                estimatedMinutes = 0,
            ),
        )

    override fun observeAverageReviewDurationMs(
        days: Int,
        today: LocalDate,
        selectedBooks: Set<BookCode>,
    ): Flow<Long?> = flowOf(null)

    override fun observeBookStats(now: Instant): Flow<List<BookStats>> = flowOf(emptyList())

    override fun observeReviewLoad(
        days: Int,
        now: Instant,
        selectedBooks: Set<BookCode>,
    ): Flow<List<DailyReviewLoad>> = flowOf(emptyList())

    override fun observeDailyActivity(
        days: Int,
        today: LocalDate,
        selectedBooks: Set<BookCode>,
    ): Flow<List<DailyActivity>> = flowOf(emptyList())

    override fun observeRetentionStats(
        days: Int,
        now: Instant,
        selectedBooks: Set<BookCode>,
    ): Flow<RetentionStats> = flowOf(RetentionStats(0.0, 0.0, 0.0))

    override fun observeStreakStats(
        today: LocalDate,
        selectedBooks: Set<BookCode>,
    ): Flow<StreakStats> = flowOf(StreakStats(0, 0, emptySet()))

    override fun observeDifficultWords(
        days: Int,
        now: Instant,
        selectedBooks: Set<BookCode>,
    ): Flow<List<DifficultWord>> = flowOf(emptyList())

    override suspend fun rebuildDailyStatsCache(updatedAt: Instant): Int = 0
}

private class FakeSettingsRepository : SettingsRepository {
    override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings(selectedBooks = setOf(BookCode.CET4)))

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

private class FixedClockProvider : ClockProvider {
    override fun now(): Instant = Instant.parse("2026-05-16T08:00:00Z")

    override fun zoneId(): ZoneId = ZoneId.of("Asia/Shanghai")

    override fun today(): LocalDate = LocalDate.ofInstant(now(), zoneId())
}

private fun unused(): Nothing = error("Not used by this test")

private fun reviewQueueItem(
    cardId: String,
    wordId: String,
): ReviewQueueItem =
    ReviewQueueItem(
        card =
            ReviewCard(
                id = cardId,
                wordId = wordId,
                bookCode = BookCode.CET4,
                state = com.zzz.androidvocab.core.model.ReviewState.Review,
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
                id = wordId,
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

private const val CARD_ID = "card|CET4|word-ability"
private const val WORD_ID = "word-ability"
