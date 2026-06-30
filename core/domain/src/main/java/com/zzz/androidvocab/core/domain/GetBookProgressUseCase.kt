package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.model.effectiveSelectedBookCodes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Retrieves learning progress for each selected book.
 *
 * This use case observes vocabulary data and computes progress metrics for
 * each book the user has selected. Results are sorted according to the user's
 * book selection order.
 *
 * @param vocabularyRepository Repository for accessing vocabulary data.
 * @param settingsRepository Repository for accessing user preferences.
 * @param clockProvider Provider for observing the current time.
 */
class GetBookProgressUseCase
    @Inject
    constructor(
        private val vocabularyRepository: VocabularyRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) {
        /**
         * Returns a Flow that emits a list of book progress entries.
         *
         * Each entry contains progress metrics for a specific book, sorted
         * by the user's selection order.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke() =
            settingsRepository.settings.flatMapLatest { settings ->
                clockProvider.observeNow().flatMapLatest { now ->
                    vocabularyRepository.observeBookProgress(now).map { progress ->
                        val bookOrder = settings.selectedBooks.effectiveSelectedBookCodes()
                        val bookRank = bookOrder.withIndex().associate { it.value to it.index }
                        progress
                            .filter { it.bookCode in bookRank }
                            .sortedBy { bookRank[it.bookCode] ?: UNKNOWN_BOOK_RANK }
                    }
                }
            }
    }

private const val UNKNOWN_BOOK_RANK = Int.MAX_VALUE
