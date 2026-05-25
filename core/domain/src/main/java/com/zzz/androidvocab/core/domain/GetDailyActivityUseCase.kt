package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class GetDailyActivityUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val clockProvider: ClockProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(days: Int) =
            clockProvider.observeToday().flatMapLatest { today -> statsRepository.observeDailyActivity(days, today) }
    }
