package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.AppError
import com.zzz.androidvocab.core.common.AppException
import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import javax.inject.Inject

/**
 * Submits user feedback for a reviewed word card.
 *
 * This use case coordinates reading the target retention setting and persisting
 * the review result. It enforces a timeout when reading settings to avoid
 * blocking indefinitely on database access.
 *
 * @param reviewRepository Repository for persisting review feedback.
 * @param settingsRepository Repository for reading user preferences.
 * @param clockProvider Provider for the current timestamp.
 */
class SubmitReviewFeedbackUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) {
        /**
         * Submits feedback for a word card review.
         *
         * @param cardId Identifier of the reviewed card.
         * @param expectedLastReviewAt The last review timestamp the client observed,
         *   used for optimistic concurrency control.
         * @param expectedReviewCount The review count the client observed,
         *   used for optimistic concurrency control.
         * @param rating User's rating of the review (Again/Hard/Good/Easy).
         * @param durationMs Time spent on this review in milliseconds.
         * @throws AppException if reading settings times out.
         */
        suspend operator fun invoke(
            cardId: String,
            expectedLastReviewAt: Instant?,
            expectedReviewCount: Int,
            rating: ReviewRating,
            durationMs: Long,
        ) {
            val targetRetention =
                withTimeoutOrNull(SETTINGS_READ_TIMEOUT_MS) {
                    settingsRepository.settings.first().targetRetention
                } ?: throw AppException(
                    AppError.DatabaseReadFailed("Timed out reading settings for targetRetention"),
                )
            reviewRepository.submitFeedback(
                SubmitFeedbackCommand(
                    cardId = cardId,
                    rating = rating,
                    reviewedAt = clockProvider.now(),
                    expectedLastReviewAt = expectedLastReviewAt,
                    expectedReviewCount = expectedReviewCount,
                    durationMs = durationMs,
                    targetRetention = targetRetention,
                ),
            )
        }

        companion object {
            private const val SETTINGS_READ_TIMEOUT_MS = 3000L
        }
    }
