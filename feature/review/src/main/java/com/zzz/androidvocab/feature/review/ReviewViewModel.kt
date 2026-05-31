package com.zzz.androidvocab.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.common.toUserMessage
import com.zzz.androidvocab.core.domain.GetTodayQueueUseCase
import com.zzz.androidvocab.core.domain.ObserveSubmittedCardQueueStateUseCase
import com.zzz.androidvocab.core.domain.ReviewSessionCoordinator
import com.zzz.androidvocab.core.domain.SubmitReviewFeedbackUseCase
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewRating
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

data class ReviewUiState(
    val isLoading: Boolean = true,
    val item: ReviewQueueItem? = null,
    val remainingCount: Int = 0,
    val isAdvancingToNextCard: Boolean = false,
    val isBackVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ReviewViewModel
    @Inject
    constructor(
        getTodayQueueUseCase: GetTodayQueueUseCase,
        observeSubmittedCardQueueStateUseCase: ObserveSubmittedCardQueueStateUseCase,
        private val submitReviewFeedbackUseCase: SubmitReviewFeedbackUseCase,
        private val clockProvider: ClockProvider,
        private val reviewSessionCoordinator: ReviewSessionCoordinator,
    ) : ViewModel() {
        private val visibleBackCardId = MutableStateFlow<String?>(null)
        private val isSubmitting = MutableStateFlow(false)
        private val errorMessage = MutableStateFlow<String?>(null)

        private val answerShownAtMs = AtomicLong(0L)
        private val failedSubmitDurationMs = AtomicLong(NO_FAILED_SUBMIT_DURATION_MS)
        private val failedSubmitCardId = AtomicReference<String?>(null)

        val uiState =
            combine(
                getTodayQueueUseCase(),
                visibleBackCardId,
                observeSubmittedCardQueueStateUseCase(),
                isSubmitting,
                errorMessage,
            ) { queue, backCardId, submittedCardQueueState, submitting, error ->
                val currentSubmittedCardId = submittedCardQueueState.submittedCardId
                val submittedCardStillInQueue =
                    currentSubmittedCardId != null && queue.items.any { it.card.id == currentSubmittedCardId }
                val visibleItems =
                    if (currentSubmittedCardId == null) {
                        queue.items
                    } else {
                        queue.items.filterNot { it.card.id == currentSubmittedCardId }
                    }
                if (currentSubmittedCardId != null && !submittedCardQueueState.isStillInRawQueue) {
                    reviewSessionCoordinator.clearSubmittedCard(currentSubmittedCardId)
                }
                val item = visibleItems.firstOrNull()
                val isAdvancingToNextCard =
                    currentSubmittedCardId != null &&
                        submittedCardStillInQueue &&
                        item == null
                if (item?.card?.id != failedSubmitCardId.get()) {
                    clearFailedSubmitDuration()
                }
                ReviewUiState(
                    isLoading = false,
                    item = item,
                    remainingCount = visibleItems.size,
                    isAdvancingToNextCard = isAdvancingToNextCard,
                    isBackVisible = item?.card?.id?.let { it == backCardId } ?: false,
                    isSubmitting = submitting,
                    errorMessage = error,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

        fun showBack() {
            val cardId =
                uiState.value.item
                    ?.card
                    ?.id ?: return
            if (reviewSessionCoordinator.submittedCardId.value == cardId) return
            if (visibleBackCardId.value != cardId) {
                answerShownAtMs.set(clockProvider.now().toEpochMilli())
                clearFailedSubmitDuration()
            }
            visibleBackCardId.value = cardId
        }

        fun submit(rating: ReviewRating) {
            if (isSubmitting.value) return
            val card = uiState.value.item?.card ?: return
            val cardId = card.id
            if (reviewSessionCoordinator.submittedCardId.value == cardId) return
            val durationMs = reviewDurationMs(cardId)
            errorMessage.value = null
            isSubmitting.value = true
            viewModelScope.launch {
                try {
                    submitReviewFeedbackUseCase(cardId, card.lastReviewAt, card.reviewCount, rating, durationMs)
                    reviewSessionCoordinator.markSubmittedCard(cardId)
                    answerShownAtMs.set(0L)
                    clearFailedSubmitDuration()
                    visibleBackCardId.value = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (failedSubmitDurationMs.compareAndSet(NO_FAILED_SUBMIT_DURATION_MS, durationMs)) {
                        failedSubmitCardId.set(cardId)
                    }
                    errorMessage.value = e.toUserMessage("反馈保存失败")
                } finally {
                    isSubmitting.value = false
                }
            }
        }

        private fun reviewDurationMs(cardId: String): Long {
            val failedDurationMs = failedSubmitDurationMs.get()
            if (failedDurationMs != NO_FAILED_SUBMIT_DURATION_MS && failedSubmitCardId.get() == cardId) {
                return failedDurationMs
            }
            val startedAt = answerShownAtMs.get()
            if (startedAt == 0L) return 0L
            return (clockProvider.now().toEpochMilli() - startedAt).coerceAtLeast(0L)
        }

        private fun clearFailedSubmitDuration() {
            failedSubmitDurationMs.set(NO_FAILED_SUBMIT_DURATION_MS)
            failedSubmitCardId.set(null)
        }
    }

private const val NO_FAILED_SUBMIT_DURATION_MS = -1L
