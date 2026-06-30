package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/**
 * Retrieves the today's review queue as a reactive stream.
 *
 * This use case combines user settings (selected books, daily new limit) with
 * the current time to produce a live-updating queue of words to review.
 * It reacts to changes in settings or time automatically.
 *
 * @param reviewRepository Repository for accessing review data.
 * @param settingsRepository Repository for accessing user preferences.
 * @param clockProvider Provider for observing the current time.
 */
class GetTodayQueueUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) {
        /**
         * Returns a Flow that emits the current review queue.
         *
         * The queue is recomputed whenever:
         * - User settings change (selected books or daily new limit)
         * - The current time changes (affects which words are due)
         */
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
