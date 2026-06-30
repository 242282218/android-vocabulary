package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/**
 * Retrieves words that the user finds difficult to remember.
 *
 * This use case identifies words with low retention rates or frequent review
 * failures over a specified time range. It reacts to changes in settings and
 * the current time.
 *
 * @param statsRepository Repository for accessing learning statistics.
 * @param settingsRepository Repository for accessing user preferences.
 * @param clockProvider Provider for observing the current time.
 */
class GetDifficultWordsUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) {
        /**
         * Returns a Flow that emits a list of difficult words.
         *
         * @param days Number of days of history to consider when identifying difficult words.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(days: Int) =
            settingsRepository.settings.flatMapLatest { settings ->
                clockProvider.observeNow().flatMapLatest { now ->
                    statsRepository.observeDifficultWords(days, now, settings.selectedBooks)
                }
            }
    }
