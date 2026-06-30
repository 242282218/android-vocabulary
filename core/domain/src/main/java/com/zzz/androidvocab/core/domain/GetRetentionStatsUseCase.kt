package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/**
 * Retrieves memory retention statistics over a specified time range.
 *
 * This use case computes how well the user is retaining learned words over
 * time, based on spaced repetition algorithm outputs. It reacts to changes
 * in settings and the current time.
 *
 * @param statsRepository Repository for accessing learning statistics.
 * @param settingsRepository Repository for accessing user preferences.
 * @param clockProvider Provider for observing the current time.
 */
class GetRetentionStatsUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) {
        /**
         * Returns a Flow that emits retention statistics.
         *
         * @param days Number of days of history to include in the retention analysis.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(days: Int) =
            settingsRepository.settings.flatMapLatest { settings ->
                clockProvider.observeNow().flatMapLatest { now ->
                    statsRepository.observeRetentionStats(days, now, settings.selectedBooks)
                }
            }
    }
