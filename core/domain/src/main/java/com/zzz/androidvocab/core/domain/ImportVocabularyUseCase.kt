package com.zzz.androidvocab.core.domain

import javax.inject.Inject

class ImportVocabularyUseCase
    @Inject
    constructor(
        private val vocabularyRepository: VocabularyRepository,
    ) {
        suspend operator fun invoke() = vocabularyRepository.importPublishSafeVocabulary()
    }
