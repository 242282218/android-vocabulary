package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/**
 * Retrieves review load statistics over a specified time range.
 *
 * This use case computes the distribution of review ratings (Again/Hard/Good/Easy)
 * over time, helping users understand their review patterns. It reacts to changes
 * in settings and the current time.
 *
 * @param statsRepository Repository for accessing learning statistics.
 * @param settingsRepository Repository for accessing user preferences.
 * @param clockProvider Provider for observing the current time.
 */
class GetReviewLoadUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) {
        /**
         * Returns a Flow that emits review load statistics.
         *
         * @param days Number of days of history to include in the review load analysis.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(days: Int) =
            settingsRepository.settings.flatMapLatest { settings ->
                clockProvider.observeNow().flatMapLatest { now ->
                    statsRepository.observeReviewLoad(days, now, settings.selectedBooks)
                }
            }
    }
