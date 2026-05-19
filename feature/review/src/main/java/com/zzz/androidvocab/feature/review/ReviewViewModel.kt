package com.zzz.androidvocab.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.common.toUserMessage
import com.zzz.androidvocab.core.domain.GetTodayQueueUseCase
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
import javax.inject.Inject

data class ReviewUiState(
    val item: ReviewQueueItem? = null,
    val remainingCount: Int = 0,
    val isBackVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ReviewViewModel
    @Inject
    constructor(
        getTodayQueueUseCase: GetTodayQueueUseCase,
        private val submitReviewFeedbackUseCase: SubmitReviewFeedbackUseCase,
        private val clockProvider: ClockProvider,
    ) : ViewModel() {
        private val isBackVisible = MutableStateFlow(false)
        private val isSubmitting = MutableStateFlow(false)
        private val errorMessage = MutableStateFlow<String?>(null)

        @Volatile
        private var answerShownAtMs: Long? = null

        val uiState =
            combine(
                getTodayQueueUseCase(),
                isBackVisible,
                isSubmitting,
                errorMessage,
            ) { queue, backVisible, submitting, error ->
                ReviewUiState(
                    item = queue.items.firstOrNull(),
                    remainingCount = queue.totalCount,
                    isBackVisible = backVisible,
                    isSubmitting = submitting,
                    errorMessage = error,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

        fun showBack() {
            if (!isBackVisible.value) {
                answerShownAtMs = clockProvider.now().toEpochMilli()
            }
            isBackVisible.value = true
        }

        fun submit(rating: ReviewRating) {
            if (isSubmitting.value) return
            val cardId =
                uiState.value.item
                    ?.card
                    ?.id ?: return
            val durationMs = reviewDurationMs()
            isSubmitting.value = true
            viewModelScope.launch {
                errorMessage.value = null
                try {
                    submitReviewFeedbackUseCase(cardId, rating, durationMs)
                    answerShownAtMs = null
                    isBackVisible.value = false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorMessage.value = e.toUserMessage("反馈保存失败")
                }
                isSubmitting.value = false
            }
        }

        private fun reviewDurationMs(): Long {
            val startedAt = answerShownAtMs ?: return 0L
            return (clockProvider.now().toEpochMilli() - startedAt).coerceAtLeast(0L)
        }
    }
