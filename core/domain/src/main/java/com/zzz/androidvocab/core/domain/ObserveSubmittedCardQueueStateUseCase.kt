package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class SubmittedCardQueueState(
    val submittedCardId: String? = null,
    val isStillInRawQueue: Boolean = false,
)

class ObserveSubmittedCardQueueStateUseCase
    @Inject
    constructor(
        private val reviewRepository: ReviewRepository,
        private val settingsRepository: SettingsRepository,
        private val reviewSessionCoordinator: ReviewSessionCoordinator,
        private val clockProvider: ClockProvider,
    ) {
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
