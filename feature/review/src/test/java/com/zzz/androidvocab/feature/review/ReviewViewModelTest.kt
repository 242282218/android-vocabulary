package com.zzz.androidvocab.feature.review

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.domain.GetTodayQueueUseCase
import com.zzz.androidvocab.core.domain.ReviewRepository
import com.zzz.androidvocab.core.domain.SettingsRepository
import com.zzz.androidvocab.core.domain.SubmitReviewFeedbackUseCase
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
import com.zzz.androidvocab.core.model.ReviewDataRepairResult
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.ReviewResult
import com.zzz.androidvocab.core.model.ReviewState
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import com.zzz.androidvocab.core.model.ThemeMode
import com.zzz.androidvocab.core.model.TodayQueue
import com.zzz.androidvocab.core.model.WordEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {
    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun submitIgnoresDuplicateClicksWhileFirstSubmitIsRunning() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val reviewRepository = RecordingReviewRepository()
            val viewModel = viewModel(reviewRepository = reviewRepository)
            launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.first { it.item != null }
            }.join()
            advanceUntilIdle()

            viewModel.submit(ReviewRating.Good)
            viewModel.submit(ReviewRating.Again)
            advanceUntilIdle()

            assertEquals(listOf(ReviewRating.Good), reviewRepository.commands.map { it.rating })
        }

    @Test
    fun submitUsesElapsedTimeAfterAnswerIsShown() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val clock = MutableClock(Instant.parse("2026-05-16T08:00:00Z"))
            val reviewRepository = RecordingReviewRepository()
            val viewModel = viewModel(reviewRepository = reviewRepository, clock = clock)
            launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.first { it.item != null }
            }.join()
            advanceUntilIdle()

            viewModel.showBack()
            clock.instant = Instant.parse("2026-05-16T08:00:03.500Z")
            viewModel.submit(ReviewRating.Easy)
            advanceUntilIdle()

            assertEquals(3_500L, reviewRepository.commands.single().durationMs)
        }

    @Test
    fun backIsHiddenWhenCurrentCardChanges() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val reviewRepository = RecordingReviewRepository()
            val viewModel = viewModel(reviewRepository = reviewRepository)
            viewModel.uiState.first { it.item != null }
            advanceUntilIdle()

            viewModel.showBack()
            assertEquals(true, viewModel.uiState.first { it.isBackVisible }.isBackVisible)

            reviewRepository.queue.value =
                TodayQueue(
                    dueItems = listOf(reviewQueueItem(cardId = "card-2", wordId = "word-2")),
                    newItems = emptyList(),
                )

            val stateAfterCardChange = viewModel.uiState.first { it.item?.card?.id == "card-2" }
            assertEquals(false, stateAfterCardChange.isBackVisible)
        }

    private fun viewModel(
        reviewRepository: RecordingReviewRepository,
        clock: MutableClock = MutableClock(Instant.parse("2026-05-16T08:00:00Z")),
    ): ReviewViewModel {
        val settingsRepository = FakeSettingsRepository()
        return ReviewViewModel(
            getTodayQueueUseCase =
                GetTodayQueueUseCase(
                    reviewRepository = reviewRepository,
                    settingsRepository = settingsRepository,
                    clockProvider = clock,
                ),
            submitReviewFeedbackUseCase =
                SubmitReviewFeedbackUseCase(
                    reviewRepository = reviewRepository,
                    settingsRepository = settingsRepository,
                    clockProvider = clock,
                ),
            clockProvider = clock,
        )
    }
}

private class RecordingReviewRepository : ReviewRepository {
    val commands = mutableListOf<SubmitFeedbackCommand>()
    private val item = reviewQueueItem()
    val queue = MutableStateFlow(TodayQueue(dueItems = listOf(item), newItems = emptyList()))

    override fun observeTodayQueue(
        now: Instant,
        selectedBooks: Set<BookCode>,
        dailyNewLimit: Int,
    ): Flow<TodayQueue> = queue

    override suspend fun submitFeedback(command: SubmitFeedbackCommand): ReviewResult {
        commands += command
        return ReviewResult(item, item.card, reviewLog(command))
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

private class MutableClock(
    var instant: Instant,
) : ClockProvider {
    override fun now(): Instant = instant

    override fun zoneId(): ZoneId = ZoneId.of("UTC")

    override fun today(): LocalDate = LocalDate.ofInstant(instant, zoneId())
}

private fun reviewQueueItem(
    cardId: String = CARD_ID,
    wordId: String = WORD_ID,
): ReviewQueueItem =
    ReviewQueueItem(
        card =
            ReviewCard(
                id = cardId,
                wordId = wordId,
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

private fun reviewLog(command: SubmitFeedbackCommand) =
    com.zzz.androidvocab.core.model.ReviewLog(
        id = "log-1",
        cardId = command.cardId,
        wordId = WORD_ID,
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

private const val CARD_ID = "card|CET4|word-ability"
private const val WORD_ID = "word-ability"
