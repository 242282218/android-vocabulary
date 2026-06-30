package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.model.TodayOverview
import com.zzz.androidvocab.core.model.effectiveSelectedBookCodes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/**
 * Retrieves a comprehensive overview of today's learning progress.
 *
 * This use case aggregates multiple data sources to provide a complete picture
 * of the user's current learning state, including the review queue, statistics,
 * and estimated time remaining. It reacts to changes in settings, time, and
 * review data automatically.
 *
 * @param reviewRepository Repository for accessing review queue data.
 * @param statsRepository Repository for accessing learning statistics.
 * @param settingsRepository Repository for accessing user preferences.
 * @param clockProvider Provider for observing the current time.
 */
class GetTodayOverviewUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
        private val statsRepository: StatsRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) {
        /**
         * Returns a Flow that emits the today's overview.
         *
         * The overview includes:
         * - Current review queue with remaining items
         * - Today's statistics (reviews completed, accuracy, etc.)
         * - Estimated time to complete remaining reviews
         * - Selected book codes
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke() =
            settingsRepository.settings.flatMapLatest { settings ->
                clockProvider.observeNow().flatMapLatest { now ->
                    val today = clockProvider.localDate(now)
                    reviewRepository
                        .observeTodayQueue(now, settings.selectedBooks, settings.dailyNewLimit)
                        .flatMapLatest { queue ->
                            combine(
                                statsRepository.observeTodayStats(
                                    localDay = today,
                                    now = now,
                                    selectedBooks = settings.selectedBooks,
                                ),
                                statsRepository.observeAverageReviewDurationMs(
                                    days = AVERAGE_DURATION_DAYS,
                                    today = today,
                                    selectedBooks = settings.selectedBooks,
                                ),
                            ) { stats, averageDurationMs ->
                                TodayOverview(
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
                                    selectedBooks = settings.selectedBooks.effectiveSelectedBookCodes(),
                                )
                            }
                        }
                }
            }
    }
