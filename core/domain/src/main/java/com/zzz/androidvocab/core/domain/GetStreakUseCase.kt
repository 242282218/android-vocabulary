package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/**
 * Retrieves the user's current and longest learning streak.
 *
 * This use case computes consecutive days of learning activity based on
 * review history. It reacts to changes in settings and the current date.
 *
 * @param statsRepository Repository for accessing learning statistics.
 * @param settingsRepository Repository for accessing user preferences.
 * @param clockProvider Provider for observing the current date.
 */
class GetStreakUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) {
        /**
         * Returns a Flow that emits streak statistics.
         *
         * Includes current streak (consecutive days up to today) and
         * longest streak (maximum consecutive days in history).
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke() =
            settingsRepository.settings.flatMapLatest { settings ->
                clockProvider.observeToday().flatMapLatest { today ->
                    statsRepository.observeStreakStats(today, settings.selectedBooks)
                }
            }
    }
