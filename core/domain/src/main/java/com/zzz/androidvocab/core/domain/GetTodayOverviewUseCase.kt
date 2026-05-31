package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.model.TodayOverview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

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
                                    selectedBooks = settings.selectedBooks.toList(),
                                )
                            }
                        }
                }
            }
    }
