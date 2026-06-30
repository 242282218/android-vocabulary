package com.zzz.androidvocab.core.domain

import javax.inject.Inject

/**
 * Observes detailed information about a specific word.
 *
 * This use case provides a reactive stream of word details, including its
 * current learning state, review history, and statistics. It reacts to changes
 * in the word's data automatically.
 *
 * @param vocabularyRepository Repository for accessing vocabulary data.
 */
class ObserveWordDetailUseCase
    @Inject
    constructor(
        private val vocabularyRepository: VocabularyRepository,
    ) {
        /**
         * Returns a Flow that emits detailed word information.
         *
         * @param wordId The ID of the word to observe.
         */
        operator fun invoke(wordId: String) = vocabularyRepository.observeWordDetail(wordId)
    }
