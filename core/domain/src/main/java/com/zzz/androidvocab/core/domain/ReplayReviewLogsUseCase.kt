package com.zzz.androidvocab.core.domain

import javax.inject.Inject

class ReplayReviewLogsUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
    ) {
        suspend operator fun invoke(cardId: String) = reviewRepository.replayLogs(cardId)
    }
