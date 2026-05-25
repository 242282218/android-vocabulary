package com.zzz.androidvocab.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zzz.androidvocab.core.common.toUserMessage
import com.zzz.androidvocab.core.domain.ExportUserDataUseCase
import com.zzz.androidvocab.core.domain.InspectReviewDataIntegrityUseCase
import com.zzz.androidvocab.core.domain.ObserveSettingsUseCase
import com.zzz.androidvocab.core.domain.RepairReviewDataCacheUseCase
import com.zzz.androidvocab.core.domain.UpdateSettingsUseCase
import com.zzz.androidvocab.core.domain.VocabularyRepository
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.ExportResult
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
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
    val reminderErrorMessage: String? = null,
    val dataMaintenance: DataMaintenanceUiState = DataMaintenanceUiState(),
)

data class DataMaintenanceUiState(
    val report: ReviewDataIntegrityReport? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val inProgress: Boolean = false,
)

private data class ExportUiState(
    val result: ExportResult? = null,
    val errorMessage: String? = null,
    val inProgress: Boolean = false,
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
        private val reminderErrorMessage = MutableStateFlow<String?>(null)
        private val dataMaintenance = MutableStateFlow(DataMaintenanceUiState())

        val uiState =
            combine(exportResult, exportErrorMessage, isExporting) { result, errorMessage, inProgress ->
                ExportUiState(result = result, errorMessage = errorMessage, inProgress = inProgress)
            }.let { exportState ->
                combine(
                    observeSettingsUseCase(),
                    vocabularyRepository.observeSourceInfo(),
                    exportState,
                    reminderErrorMessage,
                ) { settings, sources, export, reminderError ->
                    SettingsUiState(
                        settings = settings,
                        sources = sources,
                        exportResult = export.result,
                        exportErrorMessage = export.errorMessage,
                        isExporting = export.inProgress,
                        reminderErrorMessage = reminderError,
                    )
                }
            }.let { baseState ->
                combine(baseState, dataMaintenance) { state, maintenance ->
                    state.copy(dataMaintenance = maintenance)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

        fun updateDailyLimit(value: Int) {
            viewModelScope.launch { updateSettingsUseCase.dailyNewLimit(value) }
        }

        fun updateTheme(themeMode: ThemeMode) {
            viewModelScope.launch { updateSettingsUseCase.themeMode(themeMode) }
        }

        fun updateTargetRetention(value: Double) {
            viewModelScope.launch { updateSettingsUseCase.targetRetention(value) }
        }

        fun updateReminder(enabled: Boolean) {
            viewModelScope.launch {
                updateSettingsUseCase.reminderEnabled(enabled)
                reminderErrorMessage.value = null
            }
        }

        fun updateReminderTime(
            hour: Int,
            minute: Int,
        ) {
            viewModelScope.launch {
                updateSettingsUseCase.reminderTime(hour, minute)
                reminderErrorMessage.value = null
            }
        }

        fun showReminderPermissionDenied() {
            reminderErrorMessage.value = "通知权限未开启，无法发送每日提醒。"
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
            viewModelScope.launch {
                dataMaintenance.value = dataMaintenance.value.copy(inProgress = true, errorMessage = null)
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
                        DataMaintenanceUiState(
                            errorMessage = e.toUserMessage("学习数据检查失败"),
                        )
                }
            }
        }

        fun repairLearningData() {
            viewModelScope.launch {
                dataMaintenance.value = dataMaintenance.value.copy(inProgress = true, errorMessage = null)
                try {
                    val result = repairReviewDataCacheUseCase()
                    dataMaintenance.value =
                        DataMaintenanceUiState(
                            report = result.after,
                            message =
                                "已修复 ${result.repairedCount} 项，剩余可修复 ${result.after.repairableIssueCount} 项，" +
                                    "需人工确认 ${result.after.manualReviewIssueCount} 项。",
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    dataMaintenance.value =
                        DataMaintenanceUiState(
                            errorMessage = e.toUserMessage("学习数据修复失败"),
                        )
                }
            }
        }
    }

private fun ReviewDataIntegrityReport.toUserMessage(): String =
    if (issueCount == 0) {
        "学习数据缓存一致。已检查 $cardsWithLogs 张有记录卡片。"
    } else {
        "已检查 $cardsWithLogs 张：可修复 $repairableIssueCount 项，需人工确认 $manualReviewIssueCount 项。" +
            "缺失缓存 $missingCacheCount，不一致 $inconsistentCacheCount，旧日志 $legacyLogCardCount，孤儿日志 $orphanLogCount。"
    }
