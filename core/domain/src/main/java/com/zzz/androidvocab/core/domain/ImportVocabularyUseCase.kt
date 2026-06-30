package com.zzz.androidvocab.core.domain

import javax.inject.Inject

/**
 * Imports vocabulary data from bundled assets into the local database.
 *
 * This use case triggers a safe import process that populates the vocabulary
 * database with predefined word lists. It is typically called during app
 * initialization or when the user requests a data reset.
 *
 * @param vocabularyRepository Repository for managing vocabulary data.
 */
class ImportVocabularyUseCase
    @Inject
    constructor(
        private val vocabularyRepository: VocabularyRepository,
    ) {
        /**
         * Executes the vocabulary import process.
         *
         * @return Result indicating success or failure of the import operation.
         */
        suspend operator fun invoke() = vocabularyRepository.importPublishSafeVocabulary()
    }
