package com.zzz.androidvocab.feature.stats

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.domain.GetBookProgressUseCase
import com.zzz.androidvocab.core.domain.GetDailyActivityUseCase
import com.zzz.androidvocab.core.domain.GetDifficultWordsUseCase
import com.zzz.androidvocab.core.domain.GetRetentionStatsUseCase
import com.zzz.androidvocab.core.domain.GetReviewLoadUseCase
import com.zzz.androidvocab.core.domain.GetStreakUseCase
import com.zzz.androidvocab.core.domain.StatsRepository
import com.zzz.androidvocab.core.domain.VocabularyRepository
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.DailyActivity
import com.zzz.androidvocab.core.model.DailyReviewLoad
import com.zzz.androidvocab.core.model.DifficultWord
import com.zzz.androidvocab.core.model.RetentionStats
import com.zzz.androidvocab.core.model.SourceInfo
import com.zzz.androidvocab.core.model.StreakStats
import com.zzz.androidvocab.core.model.WordEntry
import com.zzz.androidvocab.core.model.WordStatusFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun combinesAllUseCaseDataIntoUiState() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val reviewLoad = listOf(DailyReviewLoad("2026-05-16", 5))
            val activity = listOf(DailyActivity("2026-05-16", 3))
            val retention = RetentionStats(0.85, 0.72, 0.88)
            val streak =
                StreakStats(currentStreak = 4, maxStreak = 12, activeDays = setOf("2026-05-16"))
            val progress = listOf(BookProgress(BookCode.CET4, 3846, 120, 40, 12))
            val difficultWords = listOf(difficultWord("abandon"))
            val statsRepo =
                FakeStatsRepository(
                    reviewLoad = reviewLoad,
                    activity = activity,
                    retentionValue = retention,
                    streak = streak,
                    difficultWords = difficultWords,
                )
            val vocabRepo = FakeVocabRepository(progress)
            val viewModel = createViewModel(statsRepo, vocabRepo)

            val state = viewModel.uiState.first { it.load.isNotEmpty() }

            assertEquals(reviewLoad, state.load)
            assertEquals(activity, state.activity)
            assertEquals(retention, state.retention)
            assertEquals(streak, state.streak)
            assertEquals(progress, state.progress)
            assertEquals(difficultWords, state.difficultWords)
        }

    @Test
    fun defaultsToEmptyStateWhenRepositoriesEmitNothing() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val viewModel = createViewModel(FakeStatsRepository(), FakeVocabRepository())

            val state = viewModel.uiState.first()

            assertEquals(emptyList<DailyReviewLoad>(), state.load)
            assertEquals(emptyList<DailyActivity>(), state.activity)
            assertEquals(RetentionStats(0.0, 0.0, 0.0), state.retention)
            assertEquals(StreakStats(0, 0, emptySet()), state.streak)
            assertEquals(emptyList<BookProgress>(), state.progress)
            assertEquals(emptyList<DifficultWord>(), state.difficultWords)
        }

    @Test
    fun reflectsUpdatedDataWhenRepositoriesChange() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val statsRepo =
                FakeStatsRepository(retentionValue = RetentionStats(0.5, 0.4, 0.6))
            val vocabRepo = FakeVocabRepository(emptyList())
            val viewModel = createViewModel(statsRepo, vocabRepo)

            val initial = viewModel.uiState.first { it.retention.averageRetrievability > 0.0 }
            assertEquals(0.5, initial.retention.averageRetrievability, 0.001)

            statsRepo.retentionFlow.value = RetentionStats(0.9, 0.8, 0.95)
            val updated = viewModel.uiState.first { it.retention.averageRetrievability > 0.8 }
            assertEquals(0.9, updated.retention.averageRetrievability, 0.001)
        }

    private fun createViewModel(
        statsRepo: FakeStatsRepository,
        vocabRepo: FakeVocabRepository,
    ): StatsViewModel {
        val clock = FixedClockProvider()
        return StatsViewModel(
            getReviewLoadUseCase = GetReviewLoadUseCase(statsRepo, clock),
            getDailyActivityUseCase = GetDailyActivityUseCase(statsRepo, clock),
            getRetentionStatsUseCase = GetRetentionStatsUseCase(statsRepo, clock),
            getStreakUseCase = GetStreakUseCase(statsRepo, clock),
            getBookProgressUseCase = GetBookProgressUseCase(vocabRepo, clock),
            getDifficultWordsUseCase = GetDifficultWordsUseCase(statsRepo, clock),
        )
    }
}

private class FakeStatsRepository(
    private val reviewLoad: List<DailyReviewLoad> = emptyList(),
    private val activity: List<DailyActivity> = emptyList(),
    retentionValue: RetentionStats = RetentionStats(0.0, 0.0, 0.0),
    private val streak: StreakStats = StreakStats(0, 0, emptySet()),
    private val difficultWords: List<DifficultWord> = emptyList(),
) : StatsRepository {
    internal val retentionFlow = MutableStateFlow(retentionValue)

    override fun observeTodayStats(
        localDay: LocalDate,
        now: Instant,
    ): Flow<com.zzz.androidvocab.core.model.TodayStats> =
        flowOf(
            com.zzz.androidvocab.core.model.TodayStats(
                localDay = localDay.toString(),
                newCount = 0,
                reviewCount = 0,
                againCount = 0,
                hardCount = 0,
                goodCount = 0,
                easyCount = 0,
                completedCount = 0,
                remainingCount = 0,
                recallAccuracy = 0.0,
                passRate = 0.0,
                estimatedMinutes = 0,
            ),
        )

    override fun observeAverageReviewDurationMs(
        days: Int,
        today: LocalDate,
    ): Flow<Long?> = flowOf(null)

    override fun observeBookStats(now: Instant): Flow<List<com.zzz.androidvocab.core.model.BookStats>> =
        flowOf(emptyList())

    override fun observeReviewLoad(
        days: Int,
        now: Instant,
    ): Flow<List<DailyReviewLoad>> = flowOf(reviewLoad)

    override fun observeDailyActivity(
        days: Int,
        today: LocalDate,
    ): Flow<List<DailyActivity>> = flowOf(activity)

    override fun observeRetentionStats(
        days: Int,
        now: Instant,
    ): Flow<RetentionStats> = retentionFlow

    override fun observeStreakStats(today: LocalDate): Flow<StreakStats> = flowOf(streak)

    override fun observeDifficultWords(
        days: Int,
        now: Instant,
    ): Flow<List<DifficultWord>> = flowOf(difficultWords)

    override suspend fun rebuildDailyStatsCache(updatedAt: Instant): Int = 0
}

private class FakeVocabRepository(
    private val progress: List<BookProgress> = emptyList(),
) : VocabularyRepository {
    override fun observeBookProgress(now: Instant): Flow<List<BookProgress>> = flowOf(progress)

    override fun searchWords(
        query: String,
        bookCodes: Set<BookCode>,
        statusFilter: WordStatusFilter,
        now: Instant,
    ): Flow<List<WordEntry>> = flowOf(emptyList())

    override fun observeWordDetail(wordId: String): Flow<com.zzz.androidvocab.core.model.WordDetail?> = flowOf(null)

    override fun observeSourceInfo(): Flow<List<SourceInfo>> = flowOf(emptyList())

    override suspend fun importPublishSafeVocabulary(): com.zzz.androidvocab.core.model.ImportResult =
        com.zzz.androidvocab.core.model.ImportResult(
            importedWords = 0,
            memberships = 0,
            bookCounts = emptyMap(),
            generatedAt = "2026-05-16",
        )
}

private class FixedClockProvider : ClockProvider {
    override fun now(): Instant = Instant.parse("2026-05-16T08:00:00Z")

    override fun zoneId(): ZoneId = ZoneId.of("Asia/Shanghai")

    override fun today(): LocalDate = LocalDate.ofInstant(now(), zoneId())
}

private fun difficultWord(word: String): DifficultWord =
    DifficultWord(
        word =
            WordEntry(
                id = "word-$word",
                word = word,
                meaning = "test meaning",
                phonetic = null,
                partOfSpeech = null,
                definition = null,
                cefrLevel = null,
                cefrRank = 0.0,
                frequency = 0.0,
                sourceFlags = emptyList(),
                coverageTier = null,
            ),
        againCount = 3,
        hardCount = 2,
        difficultyScore = 8.5,
    )
