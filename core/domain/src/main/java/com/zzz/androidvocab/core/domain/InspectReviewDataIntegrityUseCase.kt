package com.zzz.androidvocab.core.domain

import javax.inject.Inject

/**
 * Inspects the integrity of review data to detect inconsistencies.
 *
 * This use case performs validation checks on the review data to identify
 * any corruption, missing entries, or logical inconsistencies. It is typically
 * used for debugging and maintenance purposes.
 *
 * @param reviewRepository Repository for accessing review data.
 */
class InspectReviewDataIntegrityUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
    ) {
        /**
         * Executes the review data integrity inspection.
         *
         * @return A report detailing any integrity issues found.
         */
        suspend operator fun invoke() = reviewRepository.inspectReviewDataIntegrity()
    }
