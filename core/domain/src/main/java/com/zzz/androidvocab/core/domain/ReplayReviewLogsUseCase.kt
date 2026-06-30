package com.zzz.androidvocab.core.domain

import javax.inject.Inject

/**
 * Replays review logs for a specific card to reconstruct its review history.
 *
 * This use case is used for debugging and analysis purposes, allowing inspection
 * of how a card's state evolved over time based on its review log entries.
 *
 * @param reviewRepository Repository for accessing review data.
 */
class ReplayReviewLogsUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
    ) {
        /**
         * Replays the review logs for the specified card.
         *
         * @param cardId The ID of the card whose logs should be replayed.
         * @return The replayed review log entries.
         */
        suspend operator fun invoke(cardId: String) = reviewRepository.replayLogs(cardId)
    }
