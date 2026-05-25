package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class GetBookProgressUseCase
    @Inject
    constructor(
        private val vocabularyRepository: VocabularyRepository,
        private val clockProvider: ClockProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke() =
            clockProvider.observeNow().flatMapLatest { vocabularyRepository.observeBookProgress(it) }
    }
