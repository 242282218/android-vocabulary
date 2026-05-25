package com.zzz.androidvocab.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zzz.androidvocab.core.common.toUserMessage
import com.zzz.androidvocab.core.domain.GetReviewLoadUseCase
import com.zzz.androidvocab.core.domain.GetTodayOverviewUseCase
import com.zzz.androidvocab.core.domain.ImportVocabularyUseCase
import com.zzz.androidvocab.core.model.DailyReviewLoad
import com.zzz.androidvocab.core.model.TodayOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodayUiState(
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val overview: TodayOverview? = null,
    val reviewLoad: List<DailyReviewLoad> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        private val importVocabularyUseCase: ImportVocabularyUseCase,
        getTodayOverviewUseCase: GetTodayOverviewUseCase,
        getReviewLoadUseCase: GetReviewLoadUseCase,
    ) : ViewModel() {
        private val isImporting = MutableStateFlow(false)
        private val importError = MutableStateFlow<String?>(null)

        val uiState =
            combine(
                getTodayOverviewUseCase(),
                getReviewLoadUseCase(14),
                isImporting,
                importError,
            ) { overview, reviewLoad, importing, error ->
                TodayUiState(
                    isLoading = false,
                    isImporting = importing,
                    overview = overview,
                    reviewLoad = reviewLoad,
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
