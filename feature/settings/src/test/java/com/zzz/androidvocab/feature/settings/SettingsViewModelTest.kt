package com.zzz.androidvocab.feature.settings

import com.zzz.androidvocab.core.domain.ExportRepository
import com.zzz.androidvocab.core.domain.ExportUserDataUseCase
import com.zzz.androidvocab.core.domain.InspectReviewDataIntegrityUseCase
import com.zzz.androidvocab.core.domain.ObserveSettingsUseCase
import com.zzz.androidvocab.core.domain.RepairReviewDataCacheUseCase
import com.zzz.androidvocab.core.domain.ReviewRepository
import com.zzz.androidvocab.core.domain.SettingsRepository
import com.zzz.androidvocab.core.domain.UpdateSettingsUseCase
import com.zzz.androidvocab.core.domain.VocabularyRepository
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.ExportResult
import com.zzz.androidvocab.core.model.ImportResult
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
import com.zzz.androidvocab.core.model.ReviewDataRepairResult
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewResult
import com.zzz.androidvocab.core.model.SourceInfo
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import com.zzz.androidvocab.core.model.ThemeMode
import com.zzz.androidvocab.core.model.TodayQueue
import com.zzz.androidvocab.core.model.WordDetail
import com.zzz.androidvocab.core.model.WordEntry
import com.zzz.androidvocab.core.model.WordStatusFilter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun exportDataTracksProgressAndResult() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val gate = CompletableDeferred<Unit>()
            val exportRepository = RecordingExportRepository(gate = gate)
            val viewModel = viewModel(exportRepository = exportRepository)
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }

            viewModel.exportData()
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.isExporting)
            assertEquals(1, exportRepository.callCount)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isExporting)
            assertEquals(
                "export.json",
                viewModel.uiState.value.exportResult
                    ?.fileName,
            )
            collector.cancel()
        }

    @Test
    fun exportDataIgnoresDuplicateTriggerWhileRunning() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val gate = CompletableDeferred<Unit>()
            val exportRepository = RecordingExportRepository(gate = gate)
            val viewModel = viewModel(exportRepository = exportRepository)
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }

            viewModel.exportData()
            viewModel.exportData()
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.isExporting)
            assertEquals(1, exportRepository.callCount)

            gate.complete(Unit)
            advanceUntilIdle()
            collector.cancel()
        }

    @Test
    fun inspectLearningDataUpdatesMaintenanceState() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val reviewRepository =
                MaintenanceReviewRepository(
                    report =
                        ReviewDataIntegrityReport(
                            cardsWithLogs = 3,
                            missingCacheCount = 1,
                            inconsistentCacheCount = 1,
                            legacyLogCardCount = 1,
                        ),
                )
            val viewModel = viewModel(reviewRepository)
            launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.first { it.dataMaintenance.report != null }
            }

            viewModel.inspectLearningData()
            advanceUntilIdle()

            val maintenance = viewModel.uiState.value.dataMaintenance
            assertEquals(3, maintenance.report?.cardsWithLogs)
            assertEquals(3, maintenance.report?.issueCount)
            assertEquals(2, maintenance.report?.repairableIssueCount)
            assertEquals(
                "已检查 3 张：可修复 2 项，需人工确认 1 项。缺失缓存 1，不一致 1，旧日志 1，孤儿日志 0。",
                maintenance.message,
            )
        }

    @Test
    fun repairLearningDataUpdatesMaintenanceState() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val reviewRepository =
                MaintenanceReviewRepository(
                    repairResult =
                        ReviewDataRepairResult(
                            before =
                                ReviewDataIntegrityReport(
                                    cardsWithLogs = 2,
                                    missingCacheCount = 1,
                                    inconsistentCacheCount = 1,
                                    legacyLogCardCount = 0,
                                ),
                            after =
                                ReviewDataIntegrityReport(
                                    cardsWithLogs = 2,
                                    missingCacheCount = 0,
                                    inconsistentCacheCount = 0,
                                    legacyLogCardCount = 0,
                                ),
                            repairedCount = 2,
                        ),
                )
            val viewModel = viewModel(reviewRepository)
            launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.first { it.dataMaintenance.report != null }
            }

            viewModel.repairLearningData()
            advanceUntilIdle()

            val maintenance = viewModel.uiState.value.dataMaintenance
            assertEquals(0, maintenance.report?.issueCount)
            assertEquals("已修复 2 项，剩余可修复 0 项，需人工确认 0 项。", maintenance.message)
        }

    private fun viewModel(
        reviewRepository: MaintenanceReviewRepository = MaintenanceReviewRepository(),
        exportRepository: ExportRepository = FakeExportRepository(),
    ): SettingsViewModel {
        val settingsRepository = FakeSettingsRepository()
        return SettingsViewModel(
            observeSettingsUseCase = ObserveSettingsUseCase(settingsRepository),
            vocabularyRepository = FakeVocabularyRepository(),
            updateSettingsUseCase = UpdateSettingsUseCase(settingsRepository),
            exportUserDataUseCase = ExportUserDataUseCase(exportRepository),
            inspectReviewDataIntegrityUseCase = InspectReviewDataIntegrityUseCase(reviewRepository),
            repairReviewDataCacheUseCase = RepairReviewDataCacheUseCase(reviewRepository),
        )
    }
}

private class MaintenanceReviewRepository(
    private val report: ReviewDataIntegrityReport = ReviewDataIntegrityReport(0, 0, 0, 0),
    private val repairResult: ReviewDataRepairResult =
        ReviewDataRepairResult(
            before = report,
            after = report,
            repairedCount = 0,
        ),
) : ReviewRepository {
    override fun observeTodayQueue(
        now: Instant,
        selectedBooks: Set<BookCode>,
        dailyNewLimit: Int,
    ): Flow<TodayQueue> = emptyFlow()

    override suspend fun submitFeedback(command: SubmitFeedbackCommand): ReviewResult = unsupported()

    override suspend fun replayLogs(cardId: String): ReviewCard = unsupported()

    override suspend fun getQueueItem(cardId: String): ReviewQueueItem = unsupported()

    override suspend fun inspectReviewDataIntegrity(): ReviewDataIntegrityReport = report

    override suspend fun repairReviewDataCache(): ReviewDataRepairResult = repairResult
}

private class FakeSettingsRepository : SettingsRepository {
    override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())

    override suspend fun updateDailyNewLimit(value: Int) = Unit

    override suspend fun updateSelectedBooks(bookCodes: Set<BookCode>) = Unit

    override suspend fun toggleBook(bookCode: BookCode) = Unit

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

private class FakeVocabularyRepository : VocabularyRepository {
    override fun observeBookProgress(now: Instant): Flow<List<BookProgress>> = emptyFlow()

    override fun searchWords(
        query: String,
        bookCodes: Set<BookCode>,
        statusFilter: WordStatusFilter,
        now: Instant,
    ): Flow<List<WordEntry>> = emptyFlow()

    override fun observeWordDetail(wordId: String): Flow<WordDetail?> = emptyFlow()

    override fun observeSourceInfo(): Flow<List<SourceInfo>> = MutableStateFlow(emptyList())

    override suspend fun importPublishSafeVocabulary(): ImportResult = unsupported()
}

private class FakeExportRepository : ExportRepository {
    override suspend fun exportUserData(): ExportResult = unsupported()
}

private class RecordingExportRepository(
    private val gate: CompletableDeferred<Unit>,
    private val result: ExportResult =
        ExportResult(
            fileName = "export.json",
            absolutePath = "D:\\exports\\export.json",
        ),
) : ExportRepository {
    var callCount: Int = 0
        private set

    override suspend fun exportUserData(): ExportResult {
        callCount += 1
        gate.await()
        return result
    }
}

private fun unsupported(): Nothing = error("unused")
