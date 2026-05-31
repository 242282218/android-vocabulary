package com.zzz.androidvocab.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zzz.androidvocab.core.common.toUserMessage
import com.zzz.androidvocab.core.domain.GetReviewLoadUseCase
import com.zzz.androidvocab.core.domain.GetTodayOverviewUseCase
import com.zzz.androidvocab.core.domain.ImportVocabularyUseCase
import com.zzz.androidvocab.core.domain.ObserveSubmittedCardQueueStateUseCase
import com.zzz.androidvocab.core.domain.ReviewSessionCoordinator
import com.zzz.androidvocab.core.model.DailyReviewLoad
import com.zzz.androidvocab.core.model.TodayOverview
import com.zzz.androidvocab.core.model.TodayQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class TodayUiState(
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val overview: TodayOverview? = null,
    val reviewLoad: List<DailyReviewLoad> = emptyList(),
    val isRefreshingReviewSession: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        private val importVocabularyUseCase: ImportVocabularyUseCase,
        getTodayOverviewUseCase: GetTodayOverviewUseCase,
        getReviewLoadUseCase: GetReviewLoadUseCase,
        observeSubmittedCardQueueStateUseCase: ObserveSubmittedCardQueueStateUseCase,
        private val reviewSessionCoordinator: ReviewSessionCoordinator,
    ) : ViewModel() {
        private val isImporting = MutableStateFlow(false)
        private val importError = MutableStateFlow<String?>(null)

        val uiState =
            combine(
                getTodayOverviewUseCase(),
                getReviewLoadUseCase(14),
                isImporting,
                importError,
                observeSubmittedCardQueueStateUseCase(),
            ) { overview, reviewLoad, importing, error, submittedCardQueueState ->
                val submittedCardId = submittedCardQueueState.submittedCardId
                val adjustedOverview = overview.hideSubmittedCard(submittedCardId)
                if (submittedCardId != null && !submittedCardQueueState.isStillInRawQueue) {
                    reviewSessionCoordinator.clearSubmittedCard(submittedCardId)
                }
                TodayUiState(
                    isLoading = false,
                    isImporting = importing,
                    overview = adjustedOverview,
                    reviewLoad = reviewLoad,
                    isRefreshingReviewSession =
                        submittedCardId != null &&
                            overview.queue.containsCard(submittedCardId) &&
                            adjustedOverview.queue.totalCount == 0,
                    errorMessage = error,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

        init {
            startImport()
        }

        fun retryImport() {
            startImport()
        }

        private fun startImport() {
            if (isImporting.value) return
            isImporting.value = true
            importError.value = null
            viewModelScope.launch {
                importVocabulary()
            }
        }

        private suspend fun importVocabulary() {
            try {
                importVocabularyUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                importError.value = e.toUserMessage("词库导入失败")
            } finally {
                isImporting.value = false
            }
        }
    }

private fun TodayOverview.hideSubmittedCard(submittedCardId: String?): TodayOverview {
    if (submittedCardId == null || !queue.containsCard(submittedCardId)) return this
    val filteredQueue =
        TodayQueue(
            dueItems = queue.dueItems.filterNot { it.card.id == submittedCardId },
            newItems = queue.newItems.filterNot { it.card.id == submittedCardId },
        )
    if (filteredQueue.totalCount == queue.totalCount) return this
    return copy(
        queue = filteredQueue,
        stats =
            stats.copy(
                remainingCount = filteredQueue.totalCount,
                estimatedMinutes =
                    adjustedEstimatedMinutes(
                        filteredQueue.totalCount,
                        queue.totalCount,
                        stats.estimatedMinutes,
                    ),
            ),
    )
}

private fun TodayQueue.containsCard(cardId: String): Boolean = items.any { it.card.id == cardId }

private fun adjustedEstimatedMinutes(
    filteredCount: Int,
    originalCount: Int,
    originalEstimatedMinutes: Int,
): Int =
    when {
        filteredCount <= 0 || originalCount <= 0 -> 0
        filteredCount >= originalCount -> originalEstimatedMinutes
        originalEstimatedMinutes <= 0 -> 0
        else -> maxOf(1, (originalEstimatedMinutes.toDouble() * filteredCount / originalCount).roundToInt())
    }
