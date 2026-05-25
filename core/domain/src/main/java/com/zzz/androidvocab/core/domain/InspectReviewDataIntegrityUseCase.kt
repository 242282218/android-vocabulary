package com.zzz.androidvocab.core.domain

import javax.inject.Inject

class InspectReviewDataIntegrityUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
    ) {
        suspend operator fun invoke() = reviewRepository.inspectReviewDataIntegrity()
    }
