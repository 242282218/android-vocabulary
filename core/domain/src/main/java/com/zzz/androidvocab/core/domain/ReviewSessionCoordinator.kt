package com.zzz.androidvocab.core.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates review session state across UI components.
 *
 * This singleton tracks the ID of a recently submitted review card, enabling
 * different parts of the UI to react to card submission events. It provides
 * a simple mechanism to mark a card as submitted and clear that state when
 * the submission has been fully processed.
 */
@Singleton
class ReviewSessionCoordinator
    @Inject
    constructor() {
        private val mutableSubmittedCardId = MutableStateFlow<String?>(null)

        val submittedCardId: StateFlow<String?> = mutableSubmittedCardId.asStateFlow()

        fun markSubmittedCard(cardId: String) {
            mutableSubmittedCardId.update { cardId }
        }

        fun clearSubmittedCard(cardId: String? = null) {
            mutableSubmittedCardId.update { current ->
                if (cardId == null || current == cardId) null else current
            }
        }
    }
