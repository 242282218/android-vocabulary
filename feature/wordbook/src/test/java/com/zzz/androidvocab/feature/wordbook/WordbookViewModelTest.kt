package com.zzz.androidvocab.feature.wordbook

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.domain.GetBookProgressUseCase
import com.zzz.androidvocab.core.domain.ObserveSettingsUseCase
import com.zzz.androidvocab.core.domain.ObserveWordDetailUseCase
import com.zzz.androidvocab.core.domain.SearchWordsUseCase
import com.zzz.androidvocab.core.domain.SettingsRepository
import com.zzz.androidvocab.core.domain.UpdateSettingsUseCase
import com.zzz.androidvocab.core.domain.VocabularyRepository
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.ImportResult
import com.zzz.androidvocab.core.model.SourceInfo
import com.zzz.androidvocab.core.model.ThemeMode
import com.zzz.androidvocab.core.model.WordDetail
import com.zzz.androidvocab.core.model.WordEntry
import com.zzz.androidvocab.core.model.WordStatusFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class WordbookViewModelTest {
    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun initialStateShowsLoadingUntilWordbookDataEmits() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val vocabularyRepository = FakeVocabularyRepository()
            val settingsRepository = FakeSettingsRepository()
            val viewModel = viewModel(vocabularyRepository, settingsRepository)

            assertEquals(true, viewModel.uiState.value.isLoading)

            val collectionJob =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }
            advanceTimeBy(300)
            runCurrent()

            val loadedState = viewModel.uiState.first { !it.isLoading }
            assertEquals(false, loadedState.isLoading)
            assertEquals(listOf(word()), loadedState.words)
            collectionJob.cancel()
        }

    @Test
    fun filterChangesClearSelectedWordDetail() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val vocabularyRepository = FakeVocabularyRepository()
            val settingsRepository = FakeSettingsRepository()
            val viewModel = viewModel(vocabularyRepository, settingsRepository)
            val collectionJob =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }
            advanceTimeBy(300)
            runCurrent()

            viewModel.selectWord(WORD_ID)
            runCurrent()
            val selectedState =
                viewModel.uiState.first { it.selectedWordDetail?.entry?.id == WORD_ID }
            assertEquals(
                WORD_ID,
                selectedState.selectedWordDetail
                    ?.entry
                    ?.id,
            )

            viewModel.updateQuery("ab")
            runCurrent()
            val queryState = viewModel.uiState.first { it.query == "ab" }
            assertNull(queryState.selectedWordDetail)

            viewModel.selectWord(WORD_ID)
            runCurrent()
            viewModel.uiState.first { it.selectedWordDetail?.entry?.id == WORD_ID }
            viewModel.updateStatusFilter(WordStatusFilter.Mastered)
            runCurrent()
            val filterState = viewModel.uiState.first { it.statusFilter == WordStatusFilter.Mastered }
            assertNull(filterState.selectedWordDetail)

            viewModel.selectWord(WORD_ID)
            runCurrent()
            viewModel.uiState.first { it.selectedWordDetail?.entry?.id == WORD_ID }
            viewModel.toggleBook(BookCode.CET6)
            runCurrent()
            val booksState = viewModel.uiState.first { BookCode.CET6 in it.settings.selectedBooks }
            assertNull(booksState.selectedWordDetail)
            collectionJob.cancel()
        }

    @Test
    fun searchUsesDebouncedLatestQuery() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val vocabularyRepository = FakeVocabularyRepository()
            val settingsRepository = FakeSettingsRepository()
            val viewModel = viewModel(vocabularyRepository, settingsRepository)
            val collectionJob =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }
            advanceTimeBy(300)
            runCurrent()
            vocabularyRepository.searchQueries.clear()

            viewModel.updateQuery("a")
            advanceTimeBy(100)
            viewModel.updateQuery("ab")
            advanceTimeBy(299)
            runCurrent()
            assertEquals(emptyList<String>(), vocabularyRepository.searchQueries)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf("ab"), vocabularyRepository.searchQueries)
            collectionJob.cancel()
        }

    @Test
    fun selectedBookChangesRefreshSearchImmediately() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val vocabularyRepository = FakeVocabularyRepository()
            val settingsRepository = FakeSettingsRepository()
            val viewModel = viewModel(vocabularyRepository, settingsRepository)
            val collectionJob =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }
            advanceTimeBy(300)
            runCurrent()

            viewModel.updateQuery("ab")
            advanceTimeBy(300)
            runCurrent()
            vocabularyRepository.searchBookCodes.clear()

            viewModel.toggleBook(BookCode.CET6)
            runCurrent()

            assertEquals(listOf(setOf(BookCode.CET4, BookCode.CET6)), vocabularyRepository.searchBookCodes)
            collectionJob.cancel()
        }

    @Test
    fun selectedBookChangeUsesPendingLatestQueryImmediately() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val vocabularyRepository = FakeVocabularyRepository()
            val settingsRepository = FakeSettingsRepository()
            val viewModel = viewModel(vocabularyRepository, settingsRepository)
            val collectionJob =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }
            advanceTimeBy(300)
            runCurrent()
            vocabularyRepository.searchQueries.clear()
            vocabularyRepository.searchBookCodes.clear()

            viewModel.updateQuery("ab")
            advanceTimeBy(100)
            viewModel.toggleBook(BookCode.CET6)
            runCurrent()

            assertEquals(listOf("ab"), vocabularyRepository.searchQueries)
            assertEquals(listOf(setOf(BookCode.CET4, BookCode.CET6)), vocabularyRepository.searchBookCodes)

            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf("ab"), vocabularyRepository.searchQueries)
            collectionJob.cancel()
        }

    @Test
    fun statusFilterChangesRefreshSearchImmediately() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val vocabularyRepository = FakeVocabularyRepository()
            val settingsRepository = FakeSettingsRepository()
            val viewModel = viewModel(vocabularyRepository, settingsRepository)
            val collectionJob =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }
            advanceTimeBy(300)
            runCurrent()

            viewModel.updateQuery("ab")
            advanceTimeBy(300)
            runCurrent()
            vocabularyRepository.searchStatusFilters.clear()

            viewModel.updateStatusFilter(WordStatusFilter.Mastered)
            runCurrent()

            assertEquals(listOf(WordStatusFilter.Mastered), vocabularyRepository.searchStatusFilters)
            collectionJob.cancel()
        }

    @Test
    fun statusFilterChangeUsesPendingLatestQueryImmediately() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val vocabularyRepository = FakeVocabularyRepository()
            val settingsRepository = FakeSettingsRepository()
            val viewModel = viewModel(vocabularyRepository, settingsRepository)
            val collectionJob =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }
            advanceTimeBy(300)
            runCurrent()
            vocabularyRepository.searchQueries.clear()
            vocabularyRepository.searchStatusFilters.clear()

            viewModel.updateQuery("ab")
            advanceTimeBy(100)
            viewModel.updateStatusFilter(WordStatusFilter.Mastered)
            runCurrent()

            assertEquals(listOf("ab"), vocabularyRepository.searchQueries)
            assertEquals(listOf(WordStatusFilter.Mastered), vocabularyRepository.searchStatusFilters)

            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf("ab"), vocabularyRepository.searchQueries)
            collectionJob.cancel()
        }

    @Test
    fun clearingQueryAndStatusFilterSearchesEmptyQueryImmediately() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val vocabularyRepository = FakeVocabularyRepository()
            val settingsRepository = FakeSettingsRepository()
            val viewModel = viewModel(vocabularyRepository, settingsRepository)
            val collectionJob =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }
            advanceTimeBy(300)
            runCurrent()
            viewModel.updateQuery("ab")
            advanceTimeBy(300)
            runCurrent()
            viewModel.updateStatusFilter(WordStatusFilter.Mastered)
            runCurrent()
            vocabularyRepository.searchQueries.clear()
            vocabularyRepository.searchStatusFilters.clear()

            viewModel.updateQuery("")
            viewModel.updateStatusFilter(WordStatusFilter.All)
            runCurrent()

            assertEquals(listOf(""), vocabularyRepository.searchQueries)
            assertEquals(listOf(WordStatusFilter.All), vocabularyRepository.searchStatusFilters)

            advanceTimeBy(300)
            runCurrent()
            assertEquals(listOf(""), vocabularyRepository.searchQueries)
            collectionJob.cancel()
        }

    @Test
    fun toggleLastSelectedBookKeepsOneBookSelected() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val vocabularyRepository = FakeVocabularyRepository()
            val settingsRepository = FakeSettingsRepository()
            val viewModel = viewModel(vocabularyRepository, settingsRepository)
            val collectionJob =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }
            advanceTimeBy(300)
            runCurrent()

            viewModel.toggleBook(BookCode.CET4)
            runCurrent()

            val state = viewModel.uiState.first { !it.isLoading }
            assertEquals(setOf(BookCode.CET4), state.settings.selectedBooks)
            collectionJob.cancel()
        }

    private fun viewModel(
        vocabularyRepository: VocabularyRepository,
        settingsRepository: SettingsRepository,
    ): WordbookViewModel {
        val clock = FixedClock()
        return WordbookViewModel(
            getBookProgressUseCase = GetBookProgressUseCase(vocabularyRepository, settingsRepository, clock),
            observeSettingsUseCase = ObserveSettingsUseCase(settingsRepository),
            observeWordDetailUseCase = ObserveWordDetailUseCase(vocabularyRepository),
            updateSettingsUseCase = UpdateSettingsUseCase(settingsRepository),
            searchWordsUseCase = SearchWordsUseCase(vocabularyRepository, clock),
        )
    }
}

private class FakeVocabularyRepository : VocabularyRepository {
    val searchQueries = mutableListOf<String>()
    val searchBookCodes = mutableListOf<Set<BookCode>>()
    val searchStatusFilters = mutableListOf<WordStatusFilter>()

    override fun observeBookProgress(now: Instant): Flow<List<BookProgress>> = flowOf(emptyList())

    override fun searchWords(
        query: String,
        bookCodes: Set<BookCode>,
        statusFilter: WordStatusFilter,
        now: Instant,
    ): Flow<List<WordEntry>> {
        searchQueries += query
        searchBookCodes += bookCodes
        searchStatusFilters += statusFilter
        return flowOf(listOf(word()))
    }

    override fun observeWordDetail(wordId: String): Flow<WordDetail?> =
        flowOf(WordDetail(entry = word(wordId), memberships = emptyList(), aliases = emptyList()))

    override fun observeSourceInfo(): Flow<List<SourceInfo>> = flowOf(emptyList())

    override suspend fun importPublishSafeVocabulary(): ImportResult = error("Not used by this test")
}

private class FakeSettingsRepository : SettingsRepository {
    override val settings: MutableStateFlow<AppSettings> =
        MutableStateFlow(AppSettings(selectedBooks = setOf(BookCode.CET4)))

    override suspend fun updateDailyNewLimit(value: Int) = Unit

    override suspend fun updateSelectedBooks(bookCodes: Set<BookCode>) {
        settings.value = settings.value.copy(selectedBooks = bookCodes)
    }

    override suspend fun toggleBook(bookCode: BookCode) {
        val selected = settings.value.selectedBooks
        settings.value =
            settings.value.copy(
                selectedBooks =
                    if (bookCode in selected && selected.size > 1) {
                        selected - bookCode
                    } else if (bookCode in selected) {
                        selected
                    } else {
                        selected + bookCode
                    },
            )
    }

    override suspend fun updateTargetRetention(value: Double) = Unit

    override suspend fun updateReminder(
        enabled: Boolean,
        hour: Int,
        minute: Int,
    ) = Unit

    override suspend fun updateReminderEnabled(enabled: Boolean) = Unit

    override suspend fun updateReminderTime(
        hour: Int,
        minute: Int,
    ) = Unit

    override suspend fun updateThemeMode(themeMode: ThemeMode) = Unit
}

private class FixedClock : ClockProvider {
    private val now = Instant.parse("2026-05-16T08:00:00Z")

    override fun now(): Instant = now

    override fun zoneId(): ZoneId = ZoneId.of("UTC")

    override fun today(): LocalDate = LocalDate.parse("2026-05-16")
}

private fun word(id: String = WORD_ID): WordEntry =
    WordEntry(
        id = id,
        word = "ability",
        meaning = "能力",
        phonetic = null,
        partOfSpeech = null,
        definition = null,
        cefrLevel = null,
        cefrRank = 0.0,
        frequency = 0.0,
        sourceFlags = emptyList(),
        coverageTier = null,
    )

private const val WORD_ID = "word-ability"
