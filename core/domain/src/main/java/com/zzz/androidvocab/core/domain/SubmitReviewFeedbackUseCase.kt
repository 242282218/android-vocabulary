package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import kotlinx.coroutines.flow.map
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
            rating: ReviewRating,
            durationMs: Long,
        ) = settingsRepository.settings.map { it.targetRetention }.firstValue().let { targetRetention ->
            reviewRepository.submitFeedback(
                SubmitFeedbackCommand(
                    cardId = cardId,
                    rating = rating,
                    reviewedAt = clockProvider.now(),
                    durationMs = durationMs,
                    targetRetention = targetRetention,
                ),
            )
        }
    }
