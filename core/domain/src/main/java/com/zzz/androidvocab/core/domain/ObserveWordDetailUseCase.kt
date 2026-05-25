package com.zzz.androidvocab.core.domain

import javax.inject.Inject

class ObserveWordDetailUseCase
    @Inject
    constructor(
        private val vocabularyRepository: VocabularyRepository,
    ) {
        operator fun invoke(wordId: String) = vocabularyRepository.observeWordDetail(wordId)
    }
