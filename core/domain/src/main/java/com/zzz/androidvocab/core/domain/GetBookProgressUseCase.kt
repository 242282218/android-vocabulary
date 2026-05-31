package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetBookProgressUseCase
    @Inject
    constructor(
        private val vocabularyRepository: VocabularyRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke() =
            settingsRepository.settings.flatMapLatest { settings ->
                clockProvider.observeNow().flatMapLatest { now ->
                    vocabularyRepository.observeBookProgress(now).map { progress ->
                        if (settings.selectedBooks.isEmpty()) {
                            progress
                        } else {
                            progress.filter { it.bookCode in settings.selectedBooks }
                        }
                    }
                }
            }
    }
