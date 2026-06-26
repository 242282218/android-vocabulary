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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        private val submitMutex = Mutex()
        private val errorMessage = MutableStateFlow<String?>(null)

        private val answerShownAtMs = AtomicLong(0L)
        private val failedSubmitDurationMs = AtomicLong(NO_FAILED_SUBMIT_DURATION_MS)
        private val failedSubmitCardId = AtomicReference<String?>(null)

        // Split complex combine into smaller, focused flows
        private val queueFlow = getTodayQueueUseCase()
        private val submittedCardStateFlow = observeSubmittedCardQueueStateUseCase()

        private val visibleQueueFlow =
            combine(queueFlow, submittedCardStateFlow) { queue, submittedState ->
                val submittedCardId = submittedState.submittedCardId
                if (submittedCardId == null) {
                    queue.items to false
                } else {
                    val filtered = queue.items.filterNot { it.card.id == submittedCardId }
                    val stillInQueue = queue.items.any { it.card.id == submittedCardId }
                    filtered to stillInQueue
                }
            }

        private val uiContentFlow =
            combine(visibleQueueFlow, visibleBackCardId) { (visibleItems, stillInQueue), backCardId ->
                val item = visibleItems.firstOrNull()
                ReviewUiContent(
                    item = item,
                    remainingCount = visibleItems.size,
                    isBackVisible = item?.card?.id?.let { it == backCardId } ?: false,
                    isAdvancingPossible = stillInQueue,
                )
            }

        val uiState =
            combine(
                uiContentFlow,
                isSubmitting,
                errorMessage,
                submittedCardStateFlow,
            ) { content, submitting, error, submittedState ->
                val submittedCardId = submittedState.submittedCardId
                if (submittedCardId != null && !submittedState.isStillInRawQueue) {
                    reviewSessionCoordinator.clearSubmittedCard(submittedCardId)
                }
                if (content.item?.card?.id != failedSubmitCardId.get()) {
                    clearFailedSubmitDuration()
                }
                val isAdvancing =
                    submittedCardId != null &&
                        content.isAdvancingPossible &&
                        content.item == null
                ReviewUiState(
                    isLoading = false,
                    item = content.item,
                    remainingCount = content.remainingCount,
                    isAdvancingToNextCard = isAdvancing,
                    isBackVisible = content.isBackVisible,
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
            val card = uiState.value.item?.card ?: return
            val cardId = card.id
            if (reviewSessionCoordinator.submittedCardId.value == cardId) return
            viewModelScope.launch {
                submitMutex.withLock {
                    if (isSubmitting.value) return@withLock
                    val durationMs = reviewDurationMs(cardId)
                    errorMessage.value = null
                    isSubmitting.value = true
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

private data class ReviewUiContent(
    val item: ReviewQueueItem?,
    val remainingCount: Int,
    val isBackVisible: Boolean,
    val isAdvancingPossible: Boolean,
)
