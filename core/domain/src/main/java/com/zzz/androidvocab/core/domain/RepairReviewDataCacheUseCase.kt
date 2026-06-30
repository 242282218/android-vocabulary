package com.zzz.androidvocab.core.domain

import javax.inject.Inject

/**
 * Repairs the review data cache to ensure consistency.
 *
 * This use case triggers a cache repair process that fixes any inconsistencies
 * or corruption in the cached review data. It is typically called when cache
 * integrity issues are detected.
 *
 * @param reviewRepository Repository for managing review data.
 */
class RepairReviewDataCacheUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
    ) {
        /**
         * Executes the review data cache repair process.
         *
         * @return Result indicating success or failure of the repair operation.
         */
        suspend operator fun invoke() = reviewRepository.repairReviewDataCache()
    }
