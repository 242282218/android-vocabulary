package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.WordStatusFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/**
 * Searches for words in the vocabulary based on query and filters.
 *
 * This use case provides word search functionality with support for filtering
 * by book and word status. It reacts to changes in the current time, which
 * affects word status calculations.
 *
 * @param vocabularyRepository Repository for accessing vocabulary data.
 * @param clockProvider Provider for observing the current time.
 */
class SearchWordsUseCase
    @Inject
    constructor(
        private val vocabularyRepository: VocabularyRepository,
        private val clockProvider: ClockProvider,
    ) {
        /**
         * Returns a Flow that emits search results.
         *
         * @param query The search query string.
         * @param bookCodes Set of book codes to search within.
         * @param statusFilter Filter for word learning status.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(
            query: String,
            bookCodes: Set<BookCode>,
            statusFilter: WordStatusFilter,
        ) = clockProvider.observeNow().flatMapLatest { now ->
            vocabularyRepository.searchWords(query, bookCodes, statusFilter, now)
        }
    }
