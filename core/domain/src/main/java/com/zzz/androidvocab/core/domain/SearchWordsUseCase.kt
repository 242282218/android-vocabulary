package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.WordStatusFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class SearchWordsUseCase
    @Inject
    constructor(
        private val vocabularyRepository: VocabularyRepository,
        private val clockProvider: ClockProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(
            query: String,
            bookCodes: Set<BookCode>,
            statusFilter: WordStatusFilter,
        ) = clockProvider.observeNow().flatMapLatest { now ->
            vocabularyRepository.searchWords(query, bookCodes, statusFilter, now)
        }
    }
