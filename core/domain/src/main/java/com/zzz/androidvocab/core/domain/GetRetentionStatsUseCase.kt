package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class GetRetentionStatsUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val clockProvider: ClockProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(days: Int) =
            clockProvider.observeNow().flatMapLatest { now -> statsRepository.observeRetentionStats(days, now) }
    }
