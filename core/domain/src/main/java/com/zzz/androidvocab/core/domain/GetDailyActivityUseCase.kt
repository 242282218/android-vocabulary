package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/**
 * Retrieves daily activity statistics over a specified time range.
 *
 * This use case provides a time-series view of the user's learning activity,
 * showing daily review counts and other metrics. It reacts to changes in
 * settings and the current date automatically.
 *
 * @param statsRepository Repository for accessing learning statistics.
 * @param settingsRepository Repository for accessing user preferences.
 * @param clockProvider Provider for observing the current date.
 */
class GetDailyActivityUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) {
        /**
         * Returns a Flow that emits daily activity data.
         *
         * @param days Number of days of history to include in the activity data.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(days: Int) =
            settingsRepository.settings.flatMapLatest { settings ->
                clockProvider.observeToday().flatMapLatest { today ->
                    statsRepository.observeDailyActivity(days, today, settings.selectedBooks)
                }
            }
    }
