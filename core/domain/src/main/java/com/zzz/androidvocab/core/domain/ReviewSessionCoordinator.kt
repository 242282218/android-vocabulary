package com.zzz.androidvocab.core.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewSessionCoordinator
    @Inject
    constructor() {
        private val mutableSubmittedCardId = MutableStateFlow<String?>(null)

        val submittedCardId: StateFlow<String?> = mutableSubmittedCardId.asStateFlow()

        fun markSubmittedCard(cardId: String) {
            mutableSubmittedCardId.value = cardId
        }

        fun clearSubmittedCard(cardId: String? = null) {
            if (cardId == null || mutableSubmittedCardId.value == cardId) {
                mutableSubmittedCardId.value = null
            }
        }
    }
