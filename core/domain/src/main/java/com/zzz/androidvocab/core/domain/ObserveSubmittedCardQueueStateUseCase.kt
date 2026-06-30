package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Represents the state of a submitted card in the review queue.
 *
 * @property submittedCardId The ID of the card that was submitted, or null if no card is pending.
 * @property isStillInRawQueue Whether the submitted card is still present in the raw queue.
 */
data class SubmittedCardQueueState(
    val submittedCardId: String? = null,
    val isStillInRawQueue: Boolean = false,
)

/**
 * Observes the queue state of a recently submitted review card.
 *
 * This use case tracks whether a submitted card remains in the review queue,
 * which is useful for determining if the card should be shown again immediately
 * or if it has been successfully processed. It reacts to changes in the
 * submitted card ID, settings, time, and queue state.
 *
 * @param reviewRepository Repository for accessing review queue data.
 * @param settingsRepository Repository for accessing user preferences.
 * @param reviewSessionCoordinator Coordinator that tracks the submitted card ID.
 * @param clockProvider Provider for observing the current time.
 */
class ObserveSubmittedCardQueueStateUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
        private val settingsRepository: SettingsRepository,
        private val reviewSessionCoordinator: ReviewSessionCoordinator,
        private val clockProvider: ClockProvider,
    ) {
        /**
         * Returns a Flow that emits the submitted card's queue state.
         *
         * When no card is submitted, emits a default state with null cardId.
         * When a card is submitted, tracks whether it still appears in the queue.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke() =
            reviewSessionCoordinator.submittedCardId.flatMapLatest { submittedCardId ->
                if (submittedCardId == null) {
                    flowOf(SubmittedCardQueueState())
                } else {
                    settingsRepository.settings.flatMapLatest { settings ->
                        clockProvider.observeNow().flatMapLatest { now ->
                            reviewRepository
                                .observeTodayQueue(
                                    now = now,
                                    selectedBooks = emptySet(),
                                    dailyNewLimit = settings.dailyNewLimit,
                                ).map { queue ->
                                    SubmittedCardQueueState(
                                        submittedCardId = submittedCardId,
                                        isStillInRawQueue = queue.items.any { it.card.id == submittedCardId },
                                    )
                                }
                        }
                    }
                }
            }
    }
