package com.zzz.androidvocab.feature.wordbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zzz.androidvocab.core.common.AppError
import com.zzz.androidvocab.core.common.AppException
import com.zzz.androidvocab.core.common.userMessage
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WordbookUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val progress: List<BookProgress> = emptyList(),
    val query: String = "",
    val statusFilter: WordStatusFilter = WordStatusFilter.All,
    val words: List<WordEntry> = emptyList(),
    val displayedWords: List<WordEntry> = emptyList(),
    val hasMoreWords: Boolean = false,
    val selectedWordDetail: WordDetail? = null,
    val bookSelectionErrorMessage: String? = null,
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
        private val bookSelectionErrorMessage = MutableStateFlow<String?>(null)
        private val displayedCount = MutableStateFlow(INITIAL_DISPLAY_COUNT)

        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        val uiState =
            run {
                val settingsFlow = observeSettingsUseCase()
                val filterFlow = combine(query, statusFilter) { rawQuery, filter -> rawQuery to filter }
                val wordsFlow =
                    combine(
                        settingsFlow,
                        query.debounce(QUERY_DEBOUNCE_MS),
                        statusFilter,
                    ) { settings, debouncedQuery, filter ->
                        SearchRequest(
                            query = debouncedQuery,
                            bookCodes = settings.selectedBooks,
                            statusFilter = filter,
                        )
                    }.distinctUntilChanged()
                        .flatMapLatest { request ->
                            searchWordsUseCase(request.query, request.bookCodes, request.statusFilter)
                        }
                val detailFlow =
                    selectedWordId.flatMapLatest { wordId ->
                        wordId?.let(observeWordDetailUseCase::invoke) ?: flowOf(null)
                    }
                val contentFlow =
                    combine(
                        filterFlow,
                        wordsFlow,
                        detailFlow,
                        bookSelectionErrorMessage,
                        displayedCount,
                    ) { filterState, words, detail, bookSelectionError, count ->
                        capDisplayedCount(words.size)
                        val displayed = words.take(count)
                        WordbookContentState(
                            filterState = filterState,
                            words = words,
                            displayedWords = displayed,
                            hasMoreWords = words.size > count,
                            detail = detail,
                            bookSelectionErrorMessage = bookSelectionError,
                        )
                    }
                combine(
                    settingsFlow,
                    getBookProgressUseCase(),
                    contentFlow,
                ) { settings, progress, content ->
                    WordbookUiState(
                        isLoading = false,
                        settings = settings,
                        progress = progress,
                        query = content.filterState.first,
                        statusFilter = content.filterState.second,
                        words = content.words,
                        displayedWords = content.displayedWords,
                        hasMoreWords = content.hasMoreWords,
                        selectedWordDetail = content.detail,
                        bookSelectionErrorMessage = content.bookSelectionErrorMessage,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordbookUiState())

        fun updateQuery(value: String) {
            query.value = value
            resetSelectionAndPagination()
        }

        fun updateStatusFilter(value: WordStatusFilter) {
            statusFilter.value = value
            resetSelectionAndPagination()
        }

        fun selectWord(wordId: String) {
            selectedWordId.value = wordId
        }

        fun clearSelectedWord() {
            selectedWordId.value = null
        }

        fun loadMore() {
            displayedCount.value += PAGE_SIZE
        }

        /**
         * Called internally after each combine emission to cap pagination
         * so [displayedCount] never exceeds the actual word list size.
         */
        private fun capDisplayedCount(wordsSize: Int) {
            if (displayedCount.value > wordsSize) {
                displayedCount.value = wordsSize
            }
        }

        private fun resetSelectionAndPagination() {
            selectedWordId.value = null
            displayedCount.value = INITIAL_DISPLAY_COUNT
        }

        fun toggleBook(bookCode: BookCode) {
            viewModelScope.launch {
                bookSelectionErrorMessage.value = null
                selectedWordId.value = null
                try {
                    updateSettingsUseCase.toggleBook(bookCode)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    bookSelectionErrorMessage.value = e.toBookSelectionMessage()
                }
            }
        }

        private data class WordbookContentState(
            val filterState: Pair<String, WordStatusFilter>,
            val words: List<WordEntry>,
            val displayedWords: List<WordEntry>,
            val hasMoreWords: Boolean,
            val detail: WordDetail?,
            val bookSelectionErrorMessage: String?,
        )

        private data class SearchRequest(
            val query: String,
            val bookCodes: Set<BookCode>,
            val statusFilter: WordStatusFilter,
        )
    }

private const val INITIAL_DISPLAY_COUNT = 50
private const val PAGE_SIZE = 50
private const val QUERY_DEBOUNCE_MS = 300L

private fun Throwable.toBookSelectionMessage(): String {
    val reason =
        when (this) {
            is AppException ->
                when (error) {
                    is AppError.DatabaseWriteFailed ->
                        "词书选择保存失败：${(error as AppError.DatabaseWriteFailed).reason}"
                    else -> error.userMessage
                }
            else -> message ?: "词书选择保存失败"
        }
    return "$reason。当前选择已保留，可以再次点选词书重试。"
}
