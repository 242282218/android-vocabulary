package com.zzz.androidvocab.feature.review

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.domain.GetTodayQueueUseCase
import com.zzz.androidvocab.core.domain.ObserveSubmittedCardQueueStateUseCase
import com.zzz.androidvocab.core.domain.ReviewRepository
import com.zzz.androidvocab.core.domain.ReviewSessionCoordinator
import com.zzz.androidvocab.core.domain.SettingsRepository
import com.zzz.androidvocab.core.domain.SubmitReviewFeedbackUseCase
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
import com.zzz.androidvocab.core.model.ReviewDataRepairResult
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewResult
import com.zzz.androidvocab.core.model.ReviewState
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import com.zzz.androidvocab.core.model.ThemeMode
import com.zzz.androidvocab.core.model.TodayQueue
import com.zzz.androidvocab.core.model.WordEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
internal fun setTestDispatcher() {
    kotlinx.coroutines.Dispatchers.setMain(StandardTestDispatcherCompat())
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun resetTestDispatcher() {
    kotlinx.coroutines.Dispatchers.resetMain()
}

@OptIn(ExperimentalCoroutinesApi::class)
internal object StandardTestDispatcherCompat {
    operator fun invoke() = kotlinx.coroutines.test.UnconfinedTestDispatcher()
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun createTestViewModel(
    reviewRepository: TestReviewRepository,
    clock: TestClock = TestClock(Instant.parse("2026-05-16T08:00:00Z")),
): ReviewViewModel {
    val settingsRepository = TestSettingsRepository()
    val reviewSessionCoordinator = ReviewSessionCoordinator()
    return ReviewViewModel(
        getTodayQueueUseCase =
            GetTodayQueueUseCase(
                reviewRepository = reviewRepository,
                settingsRepository = settingsRepository,
                clockProvider = clock,
            ),
        observeSubmittedCardQueueStateUseCase =
            ObserveSubmittedCardQueueStateUseCase(
                reviewRepository = reviewRepository,
                settingsRepository = settingsRepository,
                reviewSessionCoordinator = reviewSessionCoordinator,
                clockProvider = clock,
            ),
        submitReviewFeedbackUseCase =
            SubmitReviewFeedbackUseCase(
                reviewRepository = reviewRepository,
                settingsRepository = settingsRepository,
                clockProvider = clock,
            ),
        clockProvider = clock,
        reviewSessionCoordinator = reviewSessionCoordinator,
    )
}

internal class TestClock(
    var instant: Instant,
) : ClockProvider {
    override fun now(): Instant = instant

    override fun zoneId(): ZoneId = ZoneId.of("UTC")

    override fun today(): LocalDate = LocalDate.ofInstant(instant, zoneId())
}

internal open class TestReviewRepository : ReviewRepository {
    val commands = mutableListOf<SubmitFeedbackCommand>()
    protected val defaultItem = TestFixtures.reviewQueueItem()
    val queue = MutableStateFlow(TodayQueue(dueItems = listOf(defaultItem), newItems = emptyList()))
    var nextSubmitError: Exception? = null
    var nextSubmitBlocker: CompletableDeferred<Unit>? = null

    override fun observeTodayQueue(
        now: Instant,
        selectedBooks: Set<BookCode>,
        dailyNewLimit: Int,
    ): Flow<TodayQueue> = queue

    override suspend fun submitFeedback(command: SubmitFeedbackCommand): ReviewResult {
        nextSubmitError?.let { error ->
            nextSubmitError = null
            throw error
        }
        commands += command
        nextSubmitBlocker?.await()
        nextSubmitBlocker = null
        return ReviewResult(defaultItem, defaultItem.card, TestFixtures.reviewLog(command))
    }

    override suspend fun replayLogs(cardId: String): ReviewCard = defaultItem.card

    override suspend fun getQueueItem(cardId: String): ReviewQueueItem = defaultItem

    override suspend fun inspectReviewDataIntegrity(): ReviewDataIntegrityReport = ReviewDataIntegrityReport(0, 0, 0, 0)

    override suspend fun repairReviewDataCache(): ReviewDataRepairResult =
        ReviewDataRepairResult(inspectReviewDataIntegrity(), inspectReviewDataIntegrity(), 0)
}

internal class TestSettingsRepository : SettingsRepository {
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

internal object TestFixtures {
    const val CARD_ID = "card|CET4|word-ability"
    const val WORD_ID = "word-ability"

    fun reviewQueueItem(
        cardId: String = CARD_ID,
        wordId: String = WORD_ID,
        isNew: Boolean = false,
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
            isNew = isNew,
        )

    fun reviewLog(command: SubmitFeedbackCommand) =
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
}
