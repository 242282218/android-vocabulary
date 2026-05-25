package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

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
