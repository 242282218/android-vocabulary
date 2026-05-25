package com.zzz.androidvocab.feature.wordbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zzz.androidvocab.core.domain.GetBookProgressUseCase
import com.zzz.androidvocab.core.domain.ObserveSettingsUseCase
import com.zzz.androidvocab.core.domain.ObserveWordDetailUseCase
import com.zzz.androidvocab.core.domain.SearchWordsUseCase
import com.zzz.androidvocab.core.domain.UpdateSettingsUseCase
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.WordDetail
import com.zzz.androidvocab.core.model.WordEntry
import com.zzz.androidvocab.core.model.WordStatusFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WordbookUiState(
    val settings: AppSettings = AppSettings(),
    val progress: List<BookProgress> = emptyList(),
    val query: String = "",
    val statusFilter: WordStatusFilter = WordStatusFilter.All,
    val words: List<WordEntry> = emptyList(),
    val selectedWordDetail: WordDetail? = null,
)

@HiltViewModel
class WordbookViewModel
    @Inject
    constructor(
        getBookProgressUseCase: GetBookProgressUseCase,
        observeSettingsUseCase: ObserveSettingsUseCase,
        observeWordDetailUseCase: ObserveWordDetailUseCase,
        private val updateSettingsUseCase: UpdateSettingsUseCase,
        private val searchWordsUseCase: SearchWordsUseCase,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val statusFilter = MutableStateFlow(WordStatusFilter.All)
        private val selectedWordId = MutableStateFlow<String?>(null)

        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        val uiState =
            run {
                val settingsFlow = observeSettingsUseCase()
                val filterFlow = combine(query, statusFilter) { rawQuery, filter -> rawQuery to filter }
                val wordsFlow =
                    combine(settingsFlow, query.debounce(300), statusFilter) { settings, rawQuery, filter ->
                        SearchRequest(
                            query = rawQuery,
                            bookCodes = settings.selectedBooks,
                            statusFilter = filter,
                        )
                    }.flatMapLatest { request ->
                        searchWordsUseCase(request.query, request.bookCodes, request.statusFilter)
                    }
                val detailFlow =
                    selectedWordId.flatMapLatest { wordId ->
                        wordId?.let(observeWordDetailUseCase::invoke) ?: flowOf(null)
                    }
                combine(
                    settingsFlow,
                    getBookProgressUseCase(),
                    filterFlow,
                    wordsFlow,
                    detailFlow,
                ) { settings, progress, filterState, words, detail ->
                    WordbookUiState(
                        settings = settings,
                        progress = progress,
                        query = filterState.first,
                        statusFilter = filterState.second,
                        words = words,
                        selectedWordDetail = detail,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordbookUiState())

        fun updateQuery(value: String) {
            query.value = value
            selectedWordId.value = null
        }

        fun updateStatusFilter(value: WordStatusFilter) {
            statusFilter.value = value
            selectedWordId.value = null
        }

        fun selectWord(wordId: String) {
            selectedWordId.value = wordId
        }

        fun clearSelectedWord() {
            selectedWordId.value = null
        }

        fun toggleBook(bookCode: BookCode) {
            viewModelScope.launch {
                selectedWordId.value = null
                updateSettingsUseCase.toggleBook(bookCode)
            }
        }

        private data class SearchRequest(
            val query: String,
            val bookCodes: Set<BookCode>,
            val statusFilter: WordStatusFilter,
        )
    }
