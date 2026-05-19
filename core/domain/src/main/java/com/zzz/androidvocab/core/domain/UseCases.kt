package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import com.zzz.androidvocab.core.model.WordStatusFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class ImportVocabularyUseCase
    @Inject
    constructor(
        private val vocabularyRepository: VocabularyRepository,
    ) {
        suspend operator fun invoke() = vocabularyRepository.importPublishSafeVocabulary()
    }

class GetTodayOverviewUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
        private val statsRepository: StatsRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke() =
            settingsRepository.settings.flatMapLatest { settings ->
                clockProvider.observeNow().flatMapLatest { now ->
                    reviewRepository
                        .observeTodayQueue(now, settings.selectedBooks, settings.dailyNewLimit)
                        .flatMapLatest { queue ->
                            val today = clockProvider.localDate(now)
                            combine(
                                statsRepository.observeTodayStats(today, now),
                                statsRepository.observeAverageReviewDurationMs(AVERAGE_DURATION_DAYS, today),
                            ) { stats, averageDurationMs ->
                                com.zzz.androidvocab.core.model.TodayOverview(
                                    queue = queue,
                                    stats =
                                        stats.copy(
                                            remainingCount = queue.totalCount,
                                            estimatedMinutes =
                                                estimateRemainingMinutes(
                                                    remainingCount = queue.totalCount,
                                                    averageDurationMs = averageDurationMs,
                                                ),
                                        ),
                                    selectedBooks = settings.selectedBooks.toList(),
                                )
                            }
                        }
                }
            }
    }

class GetTodayQueueUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke() =
            settingsRepository.settings.flatMapLatest { settings ->
                clockProvider.observeNow().flatMapLatest { now ->
                    reviewRepository.observeTodayQueue(
                        now = now,
                        selectedBooks = settings.selectedBooks,
                        dailyNewLimit = settings.dailyNewLimit,
                    )
                }
            }
    }

class SubmitReviewFeedbackUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) {
        suspend operator fun invoke(
            cardId: String,
            rating: ReviewRating,
            durationMs: Long,
        ) = settingsRepository.settings
            .map { it.targetRetention }
            .firstValue()
            .let { targetRetention ->
                reviewRepository.submitFeedback(
                    SubmitFeedbackCommand(
                        cardId = cardId,
                        rating = rating,
                        reviewedAt = clockProvider.now(),
                        durationMs = durationMs,
                        targetRetention = targetRetention,
                    ),
                )
            }
    }

class ReplayReviewLogsUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
    ) {
        suspend operator fun invoke(cardId: String) = reviewRepository.replayLogs(cardId)
    }

class InspectReviewDataIntegrityUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
    ) {
        suspend operator fun invoke() = reviewRepository.inspectReviewDataIntegrity()
    }

class RepairReviewDataCacheUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
    ) {
        suspend operator fun invoke() = reviewRepository.repairReviewDataCache()
    }

class GetBookProgressUseCase
    @Inject
    constructor(
        private val vocabularyRepository: VocabularyRepository,
        private val clockProvider: ClockProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke() =
            clockProvider.observeNow().flatMapLatest { vocabularyRepository.observeBookProgress(it) }
    }

class GetReviewLoadUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val clockProvider: ClockProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(days: Int) =
            clockProvider.observeNow().flatMapLatest { now -> statsRepository.observeReviewLoad(days, now) }
    }

class GetDailyActivityUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val clockProvider: ClockProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(days: Int) =
            clockProvider.observeToday().flatMapLatest { today -> statsRepository.observeDailyActivity(days, today) }
    }

class GetRetentionStatsUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val clockProvider: ClockProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(days: Int) =
            clockProvider.observeNow().flatMapLatest { now -> statsRepository.observeRetentionStats(days, now) }
    }

class GetStreakUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val clockProvider: ClockProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke() =
            clockProvider.observeToday().flatMapLatest { today -> statsRepository.observeStreakStats(today) }
    }

class GetDifficultWordsUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val clockProvider: ClockProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(days: Int) =
            clockProvider.observeNow().flatMapLatest { now -> statsRepository.observeDifficultWords(days, now) }
    }

class RebuildDailyStatsCacheUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val clockProvider: ClockProvider,
    ) {
        suspend operator fun invoke(): Int = statsRepository.rebuildDailyStatsCache(clockProvider.now())
    }

class ExportUserDataUseCase
    @Inject
    constructor(
        private val exportRepository: ExportRepository,
    ) {
        suspend operator fun invoke() = exportRepository.exportUserData()
    }

class SearchWordsUseCase
    @Inject
    constructor(
        private val vocabularyRepository: VocabularyRepository,
        private val clockProvider: ClockProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(
            query: String,
            bookCodes: Set<BookCode>,
            statusFilter: WordStatusFilter,
        ) = clockProvider.observeNow().flatMapLatest { now ->
            vocabularyRepository.searchWords(query, bookCodes, statusFilter, now)
        }
    }

class ObserveWordDetailUseCase
    @Inject
    constructor(
        private val vocabularyRepository: VocabularyRepository,
    ) {
        operator fun invoke(wordId: String) = vocabularyRepository.observeWordDetail(wordId)
    }

private const val TIME_REFRESH_INTERVAL_MS = 60_000L
private const val AVERAGE_DURATION_DAYS = 7
private const val DEFAULT_REVIEW_CARD_DURATION_MS = 8_000L
private const val MILLIS_PER_MINUTE = 60_000L

internal fun estimateRemainingMinutes(
    remainingCount: Int,
    averageDurationMs: Long?,
): Int {
    if (remainingCount <= 0) return 0
    val cardDurationMs = averageDurationMs ?: DEFAULT_REVIEW_CARD_DURATION_MS
    val totalDurationMs = remainingCount.toLong() * cardDurationMs.coerceAtLeast(1L)
    return ((totalDurationMs + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt()
}

private fun ClockProvider.observeNow(): Flow<Instant> =
    flow {
        while (currentCoroutineContext().isActive) {
            emit(now())
            delay(TIME_REFRESH_INTERVAL_MS)
        }
    }

private fun ClockProvider.observeToday(): Flow<LocalDate> =
    observeNow()
        .map { localDate(it) }
        .distinctUntilChanged()

private fun ClockProvider.localDate(now: Instant): LocalDate = LocalDate.ofInstant(now, zoneId())

class ObserveSettingsUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        operator fun invoke() = settingsRepository.settings
    }

class UpdateSettingsUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        suspend fun dailyNewLimit(value: Int) = settingsRepository.updateDailyNewLimit(value)

        suspend fun selectedBooks(bookCodes: Set<BookCode>) = settingsRepository.updateSelectedBooks(bookCodes)

        suspend fun toggleBook(bookCode: BookCode) = settingsRepository.toggleBook(bookCode)

        suspend fun targetRetention(value: Double) = settingsRepository.updateTargetRetention(value)

        suspend fun themeMode(themeMode: com.zzz.androidvocab.core.model.ThemeMode) =
            settingsRepository.updateThemeMode(themeMode)

        suspend fun reminder(
            enabled: Boolean,
            hour: Int,
            minute: Int,
        ) = settingsRepository.updateReminder(enabled, hour, minute)

        suspend fun reminderEnabled(enabled: Boolean) = settingsRepository.updateReminderEnabled(enabled)

        suspend fun reminderTime(
            hour: Int,
            minute: Int,
        ) = settingsRepository.updateReminderTime(hour, minute)
    }
