package com.zzz.androidvocab.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zzz.androidvocab.core.common.AppError
import com.zzz.androidvocab.core.common.AppException
import com.zzz.androidvocab.core.common.toUserMessage
import com.zzz.androidvocab.core.common.userMessage
import com.zzz.androidvocab.core.domain.ExportUserDataUseCase
import com.zzz.androidvocab.core.domain.InspectReviewDataIntegrityUseCase
import com.zzz.androidvocab.core.domain.ObserveSettingsUseCase
import com.zzz.androidvocab.core.domain.RepairReviewDataCacheUseCase
import com.zzz.androidvocab.core.domain.UpdateSettingsUseCase
import com.zzz.androidvocab.core.domain.VocabularyRepository
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.ExportResult
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
import com.zzz.androidvocab.core.model.ReviewDataRepairResult
import com.zzz.androidvocab.core.model.SourceInfo
import com.zzz.androidvocab.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val sources: List<SourceInfo> = emptyList(),
    val exportResult: ExportResult? = null,
    val exportErrorMessage: String? = null,
    val isExporting: Boolean = false,
    val learningSettingsErrorMessage: String? = null,
    val themeErrorMessage: String? = null,
    val reminderErrorMessage: String? = null,
    val dataMaintenance: DataMaintenanceUiState = DataMaintenanceUiState(),
)

data class DataMaintenanceUiState(
    val report: ReviewDataIntegrityReport? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val inProgress: Boolean = false,
) {
    val hasRepairableIssues: Boolean = (report?.repairableIssueCount ?: 0) > 0
}

private data class ExportUiState(
    val result: ExportResult? = null,
    val errorMessage: String? = null,
    val inProgress: Boolean = false,
)

private data class SettingsErrorUiState(
    val learningSettingsErrorMessage: String? = null,
    val themeErrorMessage: String? = null,
    val reminderErrorMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        observeSettingsUseCase: ObserveSettingsUseCase,
        vocabularyRepository: VocabularyRepository,
        private val updateSettingsUseCase: UpdateSettingsUseCase,
        private val exportUserDataUseCase: ExportUserDataUseCase,
        private val inspectReviewDataIntegrityUseCase: InspectReviewDataIntegrityUseCase,
        private val repairReviewDataCacheUseCase: RepairReviewDataCacheUseCase,
    ) : ViewModel() {
        private val exportResult = MutableStateFlow<ExportResult?>(null)
        private val exportErrorMessage = MutableStateFlow<String?>(null)
        private val isExporting = MutableStateFlow(false)
        private val learningSettingsErrorMessage = MutableStateFlow<String?>(null)
        private val themeErrorMessage = MutableStateFlow<String?>(null)
        private val reminderErrorMessage = MutableStateFlow<String?>(null)
        private val dataMaintenance = MutableStateFlow(DataMaintenanceUiState())

        val uiState =
            combine(
                observeSettingsUseCase(),
                vocabularyRepository.observeSourceInfo(),
                combine(exportResult, exportErrorMessage, isExporting) { result, errorMessage, inProgress ->
                    ExportUiState(result = result, errorMessage = errorMessage, inProgress = inProgress)
                },
                combine(learningSettingsErrorMessage, themeErrorMessage, reminderErrorMessage) {
                    learningSettingsError,
                    themeError,
                    reminderError,
                    ->
                    SettingsErrorUiState(
                        learningSettingsErrorMessage = learningSettingsError,
                        themeErrorMessage = themeError,
                        reminderErrorMessage = reminderError,
                    )
                },
                dataMaintenance,
            ) { settings, sources, export, errors, maintenance ->
                SettingsUiState(
                    settings = settings,
                    sources = sources,
                    exportResult = export.result,
                    exportErrorMessage = export.errorMessage,
                    isExporting = export.inProgress,
                    learningSettingsErrorMessage = errors.learningSettingsErrorMessage,
                    themeErrorMessage = errors.themeErrorMessage,
                    reminderErrorMessage = errors.reminderErrorMessage,
                    dataMaintenance = maintenance,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

        fun updateDailyLimit(value: Int) =
            runSettingsAction(
                errorFlow = learningSettingsErrorMessage,
                onError = Throwable::toLearningSettingsMessage,
            ) { updateSettingsUseCase.dailyNewLimit(value) }

        fun updateTheme(themeMode: ThemeMode) =
            runSettingsAction(
                errorFlow = themeErrorMessage,
                onError = Throwable::toThemeSettingsMessage,
            ) { updateSettingsUseCase.themeMode(themeMode) }

        fun updateTargetRetention(value: Double) =
            runSettingsAction(
                errorFlow = learningSettingsErrorMessage,
                onError = Throwable::toLearningSettingsMessage,
            ) { updateSettingsUseCase.targetRetention(value) }

        fun updateReminder(enabled: Boolean) =
            runSettingsAction(
                errorFlow = reminderErrorMessage,
                onError = Throwable::toReminderSettingsMessage,
            ) { updateSettingsUseCase.reminderEnabled(enabled) }

        fun updateReminderTime(
            hour: Int,
            minute: Int,
        ) = runSettingsAction(
            errorFlow = reminderErrorMessage,
            onError = Throwable::toReminderSettingsMessage,
        ) { updateSettingsUseCase.reminderTime(hour, minute) }

        fun showReminderPermissionDenied() {
            reminderErrorMessage.value = "通知权限未开启，无法发送每日提醒。"
        }

        private fun runSettingsAction(
            errorFlow: MutableStateFlow<String?>,
            onError: (Throwable) -> String,
            action: suspend () -> Unit,
        ) {
            viewModelScope.launch {
                errorFlow.value = null
                try {
                    action()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorFlow.value = onError(e)
                }
            }
        }

        fun exportData() {
            if (isExporting.value) return
            isExporting.value = true
            exportResult.value = null
            exportErrorMessage.value = null
            viewModelScope.launch {
                try {
                    exportResult.value = exportUserDataUseCase()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    exportErrorMessage.value = e.toUserMessage("数据导出失败")
                } finally {
                    isExporting.value = false
                }
            }
        }

        fun inspectLearningData() {
            if (dataMaintenance.value.inProgress) return
            dataMaintenance.value =
                dataMaintenance.value.copy(
                    inProgress = true,
                    errorMessage = null,
                )
            viewModelScope.launch {
                try {
                    val report = inspectReviewDataIntegrityUseCase()
                    dataMaintenance.value =
                        DataMaintenanceUiState(
                            report = report,
                            message = report.toUserMessage(),
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    dataMaintenance.value =
                        dataMaintenance.value.copy(
                            inProgress = false,
                            errorMessage = e.toUserMessage("学习数据检查失败"),
                        )
                }
            }
        }

        fun repairLearningData() {
            if (dataMaintenance.value.inProgress) return
            dataMaintenance.value =
                dataMaintenance.value.copy(
                    inProgress = true,
                    errorMessage = null,
                )
            viewModelScope.launch {
                try {
                    val result = repairReviewDataCacheUseCase()
                    dataMaintenance.value =
                        DataMaintenanceUiState(
                            report = result.after,
                            message = result.toUserMessage(),
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    dataMaintenance.value =
                        dataMaintenance.value.copy(
                            inProgress = false,
                            errorMessage = e.toUserMessage("学习数据修复失败"),
                        )
                }
            }
        }
    }

private fun ReviewDataRepairResult.toUserMessage(): String {
    val baseMessage =
        "已修复 $repairedCount 项，剩余可修复 ${after.repairableIssueCount} 项，" +
            "需人工确认 ${after.manualReviewIssueCount} 项。"
    return if (timelineConflictCacheRebuiltCount > 0) {
        baseMessage +
            "其中时间线异常 $timelineConflictCacheRebuiltCount 项仅重建缓存，" +
            "仍需人工确认；本次不修改复习日志。"
    } else {
        baseMessage
    }
}

private fun Throwable.toSettingsErrorMessage(prefix: String): String =
    when (this) {
        is AppException ->
            when (error) {
                is AppError.DatabaseWriteFailed ->
                    "${prefix}保存失败：${(error as AppError.DatabaseWriteFailed).reason}"
                else -> error.userMessage
            }
        else -> message ?: "${prefix}保存失败"
    }

private fun Throwable.toReminderSettingsMessage(): String = toSettingsErrorMessage("提醒设置")

private fun Throwable.toLearningSettingsMessage(): String = toSettingsErrorMessage("学习设置")

private fun Throwable.toThemeSettingsMessage(): String = toSettingsErrorMessage("外观设置")

private fun ReviewDataIntegrityReport.toUserMessage(): String =
    if (issueCount == 0) {
        "学习数据缓存一致。已检查 $cardsWithLogs 张有记录卡片。"
    } else {
        val statsSummary =
            if (missingDailyStatsCount > 0 || inconsistentDailyStatsCount > 0) {
                "，日统计 $dailyStatsDays 天：缺失 $missingDailyStatsCount，不一致 $inconsistentDailyStatsCount"
            } else {
                ""
            }
        val timelineSummary =
            if (timelineConflictCardCount > 0) {
                "，时间线异常 $timelineConflictCardCount"
            } else {
                ""
            }
        if (statsSummary.isEmpty()) {
            "已检查 $cardsWithLogs 张：可修复 $repairableIssueCount 项，需人工确认 $manualReviewIssueCount 项。" +
                "缺失缓存 $missingCacheCount，不一致 $inconsistentCacheCount，" +
                "旧日志 $legacyLogCardCount，孤儿日志 $orphanLogCount$timelineSummary。"
        } else {
            "已检查 $cardsWithLogs 张卡$statsSummary：可修复 $repairableIssueCount 项，需人工确认 $manualReviewIssueCount 项。" +
                "缺失缓存 $missingCacheCount，不一致 $inconsistentCacheCount，" +
                "旧日志 $legacyLogCardCount，孤儿日志 $orphanLogCount$timelineSummary。"
        }
    }
