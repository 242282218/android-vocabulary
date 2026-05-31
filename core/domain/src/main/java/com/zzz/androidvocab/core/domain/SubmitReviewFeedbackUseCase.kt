package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class SubmitReviewFeedbackUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) {
        suspend operator fun invoke(
            cardId: String,
            expectedLastReviewAt: Instant?,
            expectedReviewCount: Int,
            rating: ReviewRating,
            durationMs: Long,
        ) = settingsRepository.settings.map { it.targetRetention }.firstValue().let { targetRetention ->
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
    }
