package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class GetStreakUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke() =
            settingsRepository.settings.flatMapLatest { settings ->
                clockProvider.observeToday().flatMapLatest { today ->
                    statsRepository.observeStreakStats(today, settings.selectedBooks)
                }
            }
    }
