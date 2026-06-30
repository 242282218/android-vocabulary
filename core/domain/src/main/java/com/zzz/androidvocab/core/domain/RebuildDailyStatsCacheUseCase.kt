package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import javax.inject.Inject

/**
 * Rebuilds the daily statistics cache from raw review data.
 *
 * This use case recomputes all daily statistics from the review log entries,
 * ensuring the cache is consistent with the actual data. It is typically called
 * during app initialization or when cache corruption is detected.
 *
 * @param statsRepository Repository for managing statistics data.
 * @param clockProvider Provider for the current timestamp.
 */
class RebuildDailyStatsCacheUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val clockProvider: ClockProvider,
    ) {
        /**
         * Executes the daily statistics cache rebuild process.
         *
         * @return The number of days rebuilt.
         */
        suspend operator fun invoke(): Int = statsRepository.rebuildDailyStatsCache(clockProvider.now())
    }
