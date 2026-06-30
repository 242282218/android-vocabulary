package com.zzz.androidvocab.feature.settings

import com.zzz.androidvocab.core.common.AppError
import com.zzz.androidvocab.core.common.AppException
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
import kotlinx.coroutines.async
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
@Suppress("LargeClass")
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
    fun reminderPermissionDeniedStoresRecoverableError() =
        runTest {
            kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val viewModel = viewModel()

            viewModel.showReminderPermissionDenied()
            val state = viewModel.uiState.first { it.reminderErrorMessage != null }

            assertEquals("通知权限未开启，无法发送每日提醒。", state.reminderErrorMessage)
        }

    @Test
    fun updateReminderStoresFailureInsteadOfCrashing() =
        runTest {
            kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val settingsRepository =
                FakeSettingsRepository().apply {
                    updateReminderEnabledException = AppException(AppError.DatabaseWriteFailed("disk full"))
                }
            val viewModel = viewModel(settingsRepository = settingsRepository)
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }

            viewModel.updateReminder(true)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(false, state.settings.reminderEnabled)
            assertEquals("提醒设置保存失败：disk full", state.reminderErrorMessage)
            collector.cancel()
        }

    @Test
    fun updateReminderTimeStoresFailureInsteadOfCrashing() =
        runTest {
            kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val settingsRepository =
                FakeSettingsRepository().apply {
                    updateReminderTimeException = AppException(AppError.DatabaseWriteFailed("disk full"))
                }
            val viewModel = viewModel(settingsRepository = settingsRepository)
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }

            viewModel.updateReminderTime(hour = 7, minute = 30)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(20, state.settings.reminderHour)
            assertEquals(0, state.settings.reminderMinute)
            assertEquals("提醒设置保存失败：disk full", state.reminderErrorMessage)
            collector.cancel()
        }

    @Test
    fun updateDailyLimitStoresFailureInsteadOfCrashing() =
        runTest {
            kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val settingsRepository =
                FakeSettingsRepository().apply {
                    updateDailyNewLimitException = AppException(AppError.DatabaseWriteFailed("disk full"))
                }
            val viewModel = viewModel(settingsRepository = settingsRepository)
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }

            viewModel.updateDailyLimit(30)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(20, state.settings.dailyNewLimit)
            assertEquals("学习设置保存失败：disk full", state.learningSettingsErrorMessage)
            collector.cancel()
        }

    @Test
    fun updateTargetRetentionStoresFailureInsteadOfCrashing() =
        runTest {
            kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val settingsRepository =
                FakeSettingsRepository().apply {
                    updateTargetRetentionException = AppException(AppError.DatabaseWriteFailed("disk full"))
                }
            val viewModel = viewModel(settingsRepository = settingsRepository)
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }

            viewModel.updateTargetRetention(0.95)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(0.9, state.settings.targetRetention, 0.0)
            assertEquals("学习设置保存失败：disk full", state.learningSettingsErrorMessage)
            collector.cancel()
        }

    @Test
    fun updateThemeStoresFailureInsteadOfCrashing() =
        runTest {
            kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val settingsRepository =
                FakeSettingsRepository().apply {
                    updateThemeModeException = AppException(AppError.DatabaseWriteFailed("disk full"))
                }
            val viewModel = viewModel(settingsRepository = settingsRepository)
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }

            viewModel.updateTheme(ThemeMode.Dark)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(ThemeMode.System, state.settings.themeMode)
            assertEquals("外观设置保存失败：disk full", state.themeErrorMessage)
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
    fun exportDataStoresFailureAndStopsProgress() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val exportRepository = FailingExportRepository(AppException(AppError.ExportFailed("disk full")))
            val viewModel = viewModel(exportRepository = exportRepository)
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }

            viewModel.exportData()
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isExporting)
            assertEquals(null, viewModel.uiState.value.exportResult)
            assertEquals("数据导出失败：disk full", viewModel.uiState.value.exportErrorMessage)
            collector.cancel()
        }

    @Test
    fun exportDataClearsPreviousSuccessWhenLaterFailure() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val exportRepository = MutableExportRepository()
            val viewModel = viewModel(exportRepository = exportRepository)
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }

            viewModel.exportData()
            advanceUntilIdle()

            assertEquals(
                "export.json",
                viewModel.uiState.value.exportResult
                    ?.fileName,
            )
            assertEquals(null, viewModel.uiState.value.exportErrorMessage)

            exportRepository.error = AppException(AppError.ExportFailed("disk full"))
            viewModel.exportData()
            advanceUntilIdle()

            assertEquals(2, exportRepository.callCount)
            assertEquals(false, viewModel.uiState.value.isExporting)
            assertEquals(null, viewModel.uiState.value.exportResult)
            assertEquals("数据导出失败：disk full", viewModel.uiState.value.exportErrorMessage)
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
    fun inspectLearningDataKeepsManualOnlyIssuesNonRepairable() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val reviewRepository =
                MaintenanceReviewRepository(
                    report =
                        ReviewDataIntegrityReport(
                            cardsWithLogs = 1,
                            missingCacheCount = 0,
                            inconsistentCacheCount = 0,
                            legacyLogCardCount = 1,
                            orphanLogCount = 1,
                        ),
                )
            val viewModel = viewModel(reviewRepository)
            launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.first { it.dataMaintenance.report != null }
            }

            viewModel.inspectLearningData()
            advanceUntilIdle()

            val maintenance = viewModel.uiState.value.dataMaintenance
            assertEquals(0, maintenance.report?.repairableIssueCount)
            assertEquals(2, maintenance.report?.manualReviewIssueCount)
            assertEquals(false, maintenance.hasRepairableIssues)
            assertEquals(
                "已检查 1 张：可修复 0 项，需人工确认 2 项。缺失缓存 0，不一致 0，旧日志 1，孤儿日志 1。",
                maintenance.message,
            )
        }

    @Test
    fun inspectLearningDataIncludesDailyStatsCacheIssues() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val reviewRepository =
                MaintenanceReviewRepository(
                    report =
                        ReviewDataIntegrityReport(
                            cardsWithLogs = 0,
                            missingCacheCount = 0,
                            inconsistentCacheCount = 0,
                            legacyLogCardCount = 0,
                            dailyStatsDays = 1,
                            missingDailyStatsCount = 1,
                        ),
                )
            val viewModel = viewModel(reviewRepository)
            launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.first { it.dataMaintenance.report != null }
            }

            viewModel.inspectLearningData()
            advanceUntilIdle()

            val maintenance = viewModel.uiState.value.dataMaintenance
            assertEquals(1, maintenance.report?.repairableIssueCount)
            assertEquals(
                "已检查 0 张卡，日统计 1 天：缺失 1，不一致 0：可修复 1 项，需人工确认 0 项。缺失缓存 0，不一致 0，旧日志 0，孤儿日志 0。",
                maintenance.message,
            )
        }

    @Test
    fun inspectLearningDataIncludesTimelineConflictIssues() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val reviewRepository =
                MaintenanceReviewRepository(
                    report =
                        ReviewDataIntegrityReport(
                            cardsWithLogs = 2,
                            missingCacheCount = 0,
                            inconsistentCacheCount = 0,
                            legacyLogCardCount = 0,
                            timelineConflictCardCount = 1,
                        ),
                )
            val viewModel = viewModel(reviewRepository)
            launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.first { it.dataMaintenance.report != null }
            }

            viewModel.inspectLearningData()
            advanceUntilIdle()

            val maintenance = viewModel.uiState.value.dataMaintenance
            assertEquals(1, maintenance.report?.manualReviewIssueCount)
            assertEquals(false, maintenance.hasRepairableIssues)
            assertEquals(
                "已检查 2 张：可修复 0 项，需人工确认 1 项。缺失缓存 0，不一致 0，旧日志 0，孤儿日志 0，时间线异常 1。",
                maintenance.message,
            )
        }

    @Test
    fun inspectLearningDataIgnoresDuplicateTriggerWhileRunning() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val reviewRepository = MaintenanceReviewRepository()
            reviewRepository.inspectGate = CompletableDeferred()
            val viewModel = viewModel(reviewRepository)
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }

            viewModel.inspectLearningData()
            viewModel.inspectLearningData()
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.dataMaintenance.inProgress)
            assertEquals(1, reviewRepository.inspectCallCount)

            reviewRepository.inspectGate!!.complete(Unit)
            advanceUntilIdle()
            collector.cancel()
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

    @Test
    fun repairLearningDataExplainsTimelineConflictsRemainManual() =
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
                                    inconsistentCacheCount = 0,
                                    legacyLogCardCount = 0,
                                    timelineConflictCardCount = 1,
                                ),
                            after =
                                ReviewDataIntegrityReport(
                                    cardsWithLogs = 2,
                                    missingCacheCount = 0,
                                    inconsistentCacheCount = 0,
                                    legacyLogCardCount = 0,
                                    timelineConflictCardCount = 1,
                                ),
                            repairedCount = 1,
                            timelineConflictCacheRebuiltCount = 1,
                        ),
                )
            val viewModel = viewModel(reviewRepository)
            launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.first { it.dataMaintenance.report != null }
            }

            viewModel.repairLearningData()
            advanceUntilIdle()

            val maintenance = viewModel.uiState.value.dataMaintenance
            assertEquals(
                "已修复 1 项，剩余可修复 0 项，需人工确认 1 项。其中时间线异常 1 项仅重建缓存，仍需人工确认；本次不修改复习日志。",
                maintenance.message,
            )
        }

    @Test
    fun repairLearningDataDoesNotInferTimelineRepairMessageFromAfterReportAlone() =
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
                                    missingCacheCount = 0,
                                    inconsistentCacheCount = 0,
                                    legacyLogCardCount = 0,
                                    timelineConflictCardCount = 1,
                                ),
                            after =
                                ReviewDataIntegrityReport(
                                    cardsWithLogs = 2,
                                    missingCacheCount = 0,
                                    inconsistentCacheCount = 0,
                                    legacyLogCardCount = 0,
                                    timelineConflictCardCount = 1,
                                ),
                            repairedCount = 0,
                            timelineConflictCacheRebuiltCount = 0,
                        ),
                )
            val viewModel = viewModel(reviewRepository)
            launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.first { it.dataMaintenance.report != null }
            }

            viewModel.repairLearningData()
            advanceUntilIdle()

            val maintenance = viewModel.uiState.value.dataMaintenance
            assertEquals(
                "已修复 0 项，剩余可修复 0 项，需人工确认 1 项。",
                maintenance.message,
            )
        }

    @Test
    fun repairLearningDataIgnoresDuplicateTriggerWhileRunning() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val reviewRepository = MaintenanceReviewRepository()
            reviewRepository.repairGate = CompletableDeferred()
            val viewModel = viewModel(reviewRepository)
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }

            viewModel.repairLearningData()
            viewModel.repairLearningData()
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.dataMaintenance.inProgress)
            assertEquals(1, reviewRepository.repairCallCount)

            reviewRepository.repairGate!!.complete(Unit)
            advanceUntilIdle()
            collector.cancel()
        }

    @Test
    fun repairLearningDataStoresFailureAndStopsProgress() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val reviewRepository = MaintenanceReviewRepository()
            reviewRepository.repairException = AppException(AppError.DatabaseWriteFailed("disk full"))
            val viewModel = viewModel(reviewRepository)
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }

            viewModel.repairLearningData()
            advanceUntilIdle()

            val maintenance = viewModel.uiState.value.dataMaintenance
            assertEquals(false, maintenance.inProgress)
            assertEquals(null, maintenance.message)
            assertEquals("学习数据保存失败：disk full", maintenance.errorMessage)
            collector.cancel()
        }

    @Test
    fun repairLearningDataFailureKeepsPreviousReportAndMessage() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val report =
                ReviewDataIntegrityReport(
                    cardsWithLogs = 2,
                    missingCacheCount = 1,
                    inconsistentCacheCount = 0,
                    legacyLogCardCount = 0,
                )
            val reviewRepository = MaintenanceReviewRepository(report = report)
            reviewRepository.repairException = AppException(AppError.DatabaseWriteFailed("disk full"))
            val viewModel = viewModel(reviewRepository)
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }

            viewModel.inspectLearningData()
            advanceUntilIdle()
            viewModel.repairLearningData()
            advanceUntilIdle()

            val maintenance = viewModel.uiState.value.dataMaintenance
            assertEquals(false, maintenance.inProgress)
            assertEquals(report, maintenance.report)
            assertEquals(
                "已检查 2 张：可修复 1 项，需人工确认 0 项。缺失缓存 1，不一致 0，旧日志 0，孤儿日志 0。",
                maintenance.message,
            )
            assertEquals("学习数据保存失败：disk full", maintenance.errorMessage)
            assertEquals(true, maintenance.hasRepairableIssues)
            collector.cancel()
        }

    @Test
    fun repairLearningDataClearsPreviousErrorBeforeCompletion() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val reviewRepository = MaintenanceReviewRepository()
            reviewRepository.repairException = AppException(AppError.DatabaseWriteFailed("disk full"))
            val viewModel = viewModel(reviewRepository)
            launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.first { it.dataMaintenance.errorMessage != null }
            }

            viewModel.repairLearningData()
            advanceUntilIdle()

            val failedMaintenance = viewModel.uiState.value.dataMaintenance
            assertEquals("学习数据保存失败：disk full", failedMaintenance.errorMessage)
            assertEquals(false, failedMaintenance.inProgress)

            reviewRepository.repairException = null
            reviewRepository.repairGate = CompletableDeferred()
            val pending =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { it.dataMaintenance.inProgress && it.dataMaintenance.errorMessage == null }
                }

            viewModel.repairLearningData()
            advanceUntilIdle()

            val pendingState = pending.await().dataMaintenance
            assertEquals(null, pendingState.errorMessage)
            assertEquals(true, pendingState.inProgress)

            val ready =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { !it.dataMaintenance.inProgress && it.dataMaintenance.message != null }
                }
            reviewRepository.repairGate!!.complete(Unit)
            advanceUntilIdle()

            val readyMaintenance = ready.await().dataMaintenance
            assertEquals("已修复 0 项，剩余可修复 0 项，需人工确认 0 项。", readyMaintenance.message)
            assertEquals(false, readyMaintenance.inProgress)
            assertEquals(null, readyMaintenance.errorMessage)
        }

    @Test
    fun settingsStateIncludesVocabularySources() =
        runTest {
            kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val source =
                SourceInfo(
                    name = "Long Source Name",
                    url = "https://example.test/source",
                    license = "CC BY-SA 4.0",
                    licenseStatus = "publish-safe",
                    redistributable = true,
                    publishBlocking = false,
                    notes = "Reusable with attribution.",
                )
            val viewModel = viewModel(vocabularyRepository = FakeVocabularyRepository(listOf(source)))

            val state = viewModel.uiState.first { it.sources.isNotEmpty() }

            assertEquals(source, state.sources.single())
        }

    @Test
    fun inspectLearningDataClearsPreviousErrorBeforeCompletion() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val reviewRepository = MaintenanceReviewRepository()
            reviewRepository.inspectException = AppException(AppError.DatabaseWriteFailed("disk full"))
            val viewModel = viewModel(reviewRepository)
            launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.first { it.dataMaintenance.errorMessage != null }
            }

            viewModel.inspectLearningData()
            advanceUntilIdle()

            val failedMaintenance = viewModel.uiState.value.dataMaintenance
            assertEquals("学习数据保存失败：disk full", failedMaintenance.errorMessage)
            assertEquals(false, failedMaintenance.inProgress)

            reviewRepository.inspectException = null
            reviewRepository.inspectGate = CompletableDeferred()
            val pending =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { it.dataMaintenance.inProgress && it.dataMaintenance.errorMessage == null }
                }

            viewModel.inspectLearningData()
            advanceUntilIdle()

            val pendingState = pending.await().dataMaintenance
            assertEquals(null, pendingState.errorMessage)
            assertEquals(true, pendingState.inProgress)

            val ready =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { !it.dataMaintenance.inProgress && it.dataMaintenance.report != null }
                }
            reviewRepository.inspectGate!!.complete(Unit)
            advanceUntilIdle()

            val readyMaintenance = ready.await().dataMaintenance
            assertEquals(0, readyMaintenance.report?.issueCount)
            assertEquals(false, readyMaintenance.inProgress)
            assertEquals(null, readyMaintenance.errorMessage)
        }

    @Test
    fun inspectLearningDataFailureKeepsPreviousReport() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val report =
                ReviewDataIntegrityReport(
                    cardsWithLogs = 1,
                    missingCacheCount = 0,
                    inconsistentCacheCount = 1,
                    legacyLogCardCount = 0,
                )
            val reviewRepository = MaintenanceReviewRepository(report = report)
            val viewModel = viewModel(reviewRepository)
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect()
                }

            viewModel.inspectLearningData()
            advanceUntilIdle()

            reviewRepository.inspectException = AppException(AppError.DatabaseWriteFailed("disk full"))
            viewModel.inspectLearningData()
            advanceUntilIdle()

            val maintenance = viewModel.uiState.value.dataMaintenance
            assertEquals(report, maintenance.report)
            assertEquals(
                "已检查 1 张：可修复 1 项，需人工确认 0 项。缺失缓存 0，不一致 1，旧日志 0，孤儿日志 0。",
                maintenance.message,
            )
            assertEquals("学习数据保存失败：disk full", maintenance.errorMessage)
            assertEquals(false, maintenance.inProgress)
            collector.cancel()
        }

    private fun viewModel(
        reviewRepository: MaintenanceReviewRepository = MaintenanceReviewRepository(),
        exportRepository: ExportRepository = FakeExportRepository(),
        vocabularyRepository: VocabularyRepository = FakeVocabularyRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
    ): SettingsViewModel =
        SettingsViewModel(
            observeSettingsUseCase = ObserveSettingsUseCase(settingsRepository),
            vocabularyRepository = vocabularyRepository,
            updateSettingsUseCase = UpdateSettingsUseCase(settingsRepository),
            exportUserDataUseCase = ExportUserDataUseCase(exportRepository),
            inspectReviewDataIntegrityUseCase = InspectReviewDataIntegrityUseCase(reviewRepository),
            repairReviewDataCacheUseCase = RepairReviewDataCacheUseCase(reviewRepository),
        )
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
    var inspectException: Exception? = null
    var inspectGate: CompletableDeferred<Unit>? = null
    var repairException: Exception? = null
    var repairGate: CompletableDeferred<Unit>? = null
    var inspectCallCount: Int = 0
    var repairCallCount: Int = 0

    override fun observeTodayQueue(
        now: Instant,
        selectedBooks: Set<BookCode>,
        dailyNewLimit: Int,
    ): Flow<TodayQueue> = emptyFlow()

    override suspend fun submitFeedback(command: SubmitFeedbackCommand): ReviewResult = unsupported()

    override suspend fun replayLogs(cardId: String): ReviewCard = unsupported()

    override suspend fun getQueueItem(cardId: String): ReviewQueueItem = unsupported()

    override suspend fun inspectReviewDataIntegrity(): ReviewDataIntegrityReport {
        inspectCallCount += 1
        inspectException?.let { throw it }
        inspectGate?.await()
        inspectGate = null
        return report
    }

    override suspend fun repairReviewDataCache(): ReviewDataRepairResult {
        repairCallCount += 1
        repairException?.let { throw it }
        repairGate?.await()
        repairGate = null
        return repairResult
    }
}

private class FakeSettingsRepository : SettingsRepository {
    private val mutableSettings = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = mutableSettings

    var updateDailyNewLimitException: Exception? = null
    var updateTargetRetentionException: Exception? = null
    var updateReminderEnabledException: Exception? = null
    var updateReminderTimeException: Exception? = null
    var updateThemeModeException: Exception? = null

    override suspend fun updateDailyNewLimit(value: Int) {
        updateDailyNewLimitException?.let { throw it }
        mutableSettings.value = mutableSettings.value.copy(dailyNewLimit = value)
    }

    override suspend fun updateSelectedBooks(bookCodes: Set<BookCode>) {
        mutableSettings.value = mutableSettings.value.copy(selectedBooks = bookCodes)
    }

    override suspend fun toggleBook(bookCode: BookCode) = Unit

    override suspend fun updateTargetRetention(value: Double) {
        updateTargetRetentionException?.let { throw it }
        mutableSettings.value = mutableSettings.value.copy(targetRetention = value)
    }

    override suspend fun updateReminder(
        enabled: Boolean,
        hour: Int,
        minute: Int,
    ) {
        mutableSettings.value =
            mutableSettings.value.copy(
                reminderEnabled = enabled,
                reminderHour = hour,
                reminderMinute = minute,
            )
    }

    override suspend fun updateReminderEnabled(enabled: Boolean) {
        updateReminderEnabledException?.let { throw it }
        mutableSettings.value = mutableSettings.value.copy(reminderEnabled = enabled)
    }

    override suspend fun updateReminderTime(
        hour: Int,
        minute: Int,
    ) {
        updateReminderTimeException?.let { throw it }
        mutableSettings.value =
            mutableSettings.value.copy(
                reminderHour = hour,
                reminderMinute = minute,
            )
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        updateThemeModeException?.let { throw it }
        mutableSettings.value = mutableSettings.value.copy(themeMode = themeMode)
    }
}

private class FakeVocabularyRepository(
    private val sources: List<SourceInfo> = emptyList(),
) : VocabularyRepository {
    override fun observeBookProgress(now: Instant): Flow<List<BookProgress>> = emptyFlow()

    override fun searchWords(
        query: String,
        bookCodes: Set<BookCode>,
        statusFilter: WordStatusFilter,
        now: Instant,
    ): Flow<List<WordEntry>> = emptyFlow()

    override fun observeWordDetail(wordId: String): Flow<WordDetail?> = emptyFlow()

    override fun observeSourceInfo(): Flow<List<SourceInfo>> = MutableStateFlow(sources)

    override suspend fun importPublishSafeVocabulary(): ImportResult = unsupported()
}

private class FakeExportRepository : ExportRepository {
    override suspend fun exportUserData(): ExportResult = unsupported()
}

private class MutableExportRepository(
    private val result: ExportResult =
        ExportResult(
            fileName = "export.json",
            absolutePath = "D:\\exports\\export.json",
        ),
) : ExportRepository {
    var error: Exception? = null
    var callCount: Int = 0
        private set

    override suspend fun exportUserData(): ExportResult {
        callCount += 1
        error?.let { throw it }
        return result
    }
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

private class FailingExportRepository(
    private val error: Exception,
) : ExportRepository {
    override suspend fun exportUserData(): ExportResult = throw error
}

private fun unsupported(): Nothing = error("unused")
