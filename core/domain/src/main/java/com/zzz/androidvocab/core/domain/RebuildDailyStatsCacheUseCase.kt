package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import javax.inject.Inject

class RebuildDailyStatsCacheUseCase
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val clockProvider: ClockProvider,
    ) {
        suspend operator fun invoke(): Int = statsRepository.rebuildDailyStatsCache(clockProvider.now())
    }
