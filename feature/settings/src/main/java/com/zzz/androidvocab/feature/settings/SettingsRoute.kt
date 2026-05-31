package com.zzz.androidvocab.feature.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zzz.androidvocab.core.designsystem.PrimaryAction
import com.zzz.androidvocab.core.designsystem.SectionTitle
import com.zzz.androidvocab.core.designsystem.VocabCard
import com.zzz.androidvocab.core.designsystem.VocabControlShape
import com.zzz.androidvocab.core.designsystem.VocabPageHeader
import com.zzz.androidvocab.core.designsystem.VocabPill
import com.zzz.androidvocab.core.designsystem.VocabScreen
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.ExportResult
import com.zzz.androidvocab.core.model.SourceInfo
import com.zzz.androidvocab.core.model.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var reminderNotificationState by remember { mutableStateOf(context.readReminderNotificationState()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        reminderNotificationState = context.readReminderNotificationState()
    }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            reminderNotificationState = context.readReminderNotificationState()
            if (granted) {
                viewModel.updateReminder(true)
            } else {
                viewModel.showReminderPermissionDenied()
            }
        }
    SettingsScreen(
        uiState = uiState,
        reminderNotificationState = reminderNotificationState,
        onDailyLimitChange = viewModel::updateDailyLimit,
        onTargetRetentionChange = viewModel::updateTargetRetention,
        onThemeChange = viewModel::updateTheme,
        onReminderChange = { enabled ->
            val notificationState = context.readReminderNotificationState()
            reminderNotificationState = notificationState
            when {
                !enabled -> viewModel.updateReminder(false)
                notificationState == ReminderNotificationState.MissingRuntimePermission ->
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> viewModel.updateReminder(true)
            }
        },
        onReminderTimeChange = viewModel::updateReminderTime,
        onOpenNotificationSettings = { context.openNotificationSettings() },
        onExport = viewModel::exportData,
        onInspectLearningData = viewModel::inspectLearningData,
        onRepairLearningData = viewModel::repairLearningData,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    reminderNotificationState: ReminderNotificationState,
    onDailyLimitChange: (Int) -> Unit,
    onTargetRetentionChange: (Double) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onReminderTimeChange: (Int, Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onExport: () -> Unit,
    onInspectLearningData: () -> Unit,
    onRepairLearningData: () -> Unit,
) {
    VocabScreen {
        VocabPageHeader(
            title = "设置",
            subtitle = "调整学习节奏、提醒和本地数据。",
        )
        LearningSettingsCard(
            settings = uiState.settings,
            errorMessage = uiState.learningSettingsErrorMessage,
            onDailyLimitChange = onDailyLimitChange,
            onTargetRetentionChange = onTargetRetentionChange,
        )
        ReminderCard(
            settings = uiState.settings,
            reminderNotificationState = reminderNotificationState,
            reminderErrorMessage = uiState.reminderErrorMessage,
            onReminderChange = onReminderChange,
            onReminderTimeChange = onReminderTimeChange,
            onOpenNotificationSettings = onOpenNotificationSettings,
        )
        ThemeCard(
            currentTheme = uiState.settings.themeMode,
            errorMessage = uiState.themeErrorMessage,
            onThemeChange = onThemeChange,
        )
        DataExportCard(
            isExporting = uiState.isExporting,
            exportResult = uiState.exportResult,
            exportErrorMessage = uiState.exportErrorMessage,
            onExport = onExport,
        )
        DataMaintenanceCard(
            dataMaintenance = uiState.dataMaintenance,
            onInspect = onInspectLearningData,
            onRepair = onRepairLearningData,
        )
        VocabCard(elevated = false) {
            SectionTitle("词库来源与许可证")
            uiState.sources.forEach { source -> SourceRow(source) }
        }
    }
}

@Composable
private fun LearningSettingsCard(
    settings: AppSettings,
    errorMessage: String?,
    onDailyLimitChange: (Int) -> Unit,
    onTargetRetentionChange: (Double) -> Unit,
) {
    VocabCard(elevated = false) {
        SectionTitle("学习设置")
        Text("每日新词上限 ${settings.dailyNewLimit}", style = MaterialTheme.typography.titleMedium)
        var dailyLimitSlider by remember {
            mutableFloatStateOf(settings.dailyNewLimit.toFloat())
        }
        LaunchedEffect(settings.dailyNewLimit, errorMessage) {
            dailyLimitSlider = settings.dailyNewLimit.toFloat()
        }
        Slider(
            value = dailyLimitSlider,
            onValueChange = { dailyLimitSlider = it },
            onValueChangeFinished = { onDailyLimitChange(dailyLimitSlider.roundToInt()) },
            valueRange = 0f..100f,
            steps = 99,
        )
        Text(
            "0 表示只复习旧词；20-30 更适合长期坚持。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "目标保持率 ${(settings.targetRetention * 100).roundToInt()}%",
            style = MaterialTheme.typography.titleMedium,
        )
        var retentionSlider by remember {
            mutableFloatStateOf(settings.targetRetention.toFloat())
        }
        LaunchedEffect(settings.targetRetention, errorMessage) {
            retentionSlider = settings.targetRetention.toFloat()
        }
        Slider(
            value = retentionSlider,
            onValueChange = { retentionSlider = it },
            onValueChangeFinished = {
                onTargetRetentionChange((retentionSlider * 100).roundToInt() / 100.0)
            },
            valueRange = 0.7f..0.98f,
            steps = 27,
        )
        Text(
            "保持率越高，复习会更密；90% 是第一版推荐值。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        errorMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReminderCard(
    settings: AppSettings,
    reminderNotificationState: ReminderNotificationState,
    reminderErrorMessage: String?,
    onReminderChange: (Boolean) -> Unit,
    onReminderTimeChange: (Int, Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    VocabCard(
        elevated = false,
        borderColor =
            if (reminderNotificationState.hasVisibleRisk(settings.reminderEnabled)) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
            } else {
                MaterialTheme.colorScheme.outline
            },
    ) {
        val showPermissionRecovery =
            reminderNotificationState == ReminderNotificationState.MissingRuntimePermission &&
                (settings.reminderEnabled || reminderErrorMessage != null)
        val showSystemNotificationRecovery =
            reminderNotificationState == ReminderNotificationState.DisabledInSystem && settings.reminderEnabled
        SectionTitle(
            title = "提醒",
            detail = reminderNotificationState.statusSummary(settings.reminderEnabled),
        )
        val reminderTimeText =
            formatReminderSummary(
                settings.reminderEnabled,
                settings.reminderHour,
                settings.reminderMinute,
            )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                reminderTimeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(checked = settings.reminderEnabled, onCheckedChange = onReminderChange)
        }
        if (settings.reminderEnabled) {
            ReminderTimeStepper(
                hour = settings.reminderHour,
                minute = settings.reminderMinute,
                onTimeChange = onReminderTimeChange,
            )
        }
        reminderErrorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        ReminderRecovery(
            showPermissionRecovery = showPermissionRecovery,
            showSystemNotificationRecovery = showSystemNotificationRecovery,
            onOpenNotificationSettings = onOpenNotificationSettings,
        )
    }
}

@Composable
private fun ReminderRecovery(
    showPermissionRecovery: Boolean,
    showSystemNotificationRecovery: Boolean,
    onOpenNotificationSettings: () -> Unit,
) {
    when {
        showPermissionRecovery ->
            ReminderRecoveryMessage(
                text = "系统通知权限未开启，每日提醒不会显示。",
                onOpenNotificationSettings = onOpenNotificationSettings,
            )
        showSystemNotificationRecovery ->
            ReminderRecoveryMessage(
                text = "系统通知已关闭，每日提醒已暂停；重新开启后会自动恢复。",
                onOpenNotificationSettings = onOpenNotificationSettings,
            )
    }
}

@Composable
private fun ReminderRecoveryMessage(
    text: String,
    onOpenNotificationSettings: () -> Unit,
) {
    Text(
        text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = onOpenNotificationSettings) {
        Icon(Icons.Outlined.Notifications, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("打开通知设置")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeCard(
    currentTheme: ThemeMode,
    errorMessage: String?,
    onThemeChange: (ThemeMode) -> Unit,
) {
    VocabCard(elevated = false) {
        SectionTitle("外观")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = currentTheme == mode,
                    onClick = { onThemeChange(mode) },
                    label = { Text(mode.displayName()) },
                    shape = VocabControlShape,
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )
            }
        }
        errorMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DataExportCard(
    isExporting: Boolean,
    exportResult: ExportResult?,
    exportErrorMessage: String?,
    onExport: () -> Unit,
) {
    VocabCard(elevated = false) {
        SectionTitle("数据导出")
        PrimaryAction(
            text = if (isExporting) "导出中" else "导出 JSON",
            onClick = onExport,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isExporting,
        )
        exportResult?.let {
            Column(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                VocabPill(
                    text = "导出完成",
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    it.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "保存路径",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    it.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        exportErrorMessage?.let {
            Column(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "导出失败",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DataMaintenanceCard(
    dataMaintenance: DataMaintenanceUiState,
    onInspect: () -> Unit,
    onRepair: () -> Unit,
) {
    VocabCard(
        elevated = false,
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = dataMaintenance.borderColor(),
    ) {
        SectionTitle(
            title = "数据维护",
            detail = dataMaintenance.statusSummary(),
        )
        Text(
            "检查复习记录、卡片缓存和日统计缓存的一致性。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DataMaintenanceActions(dataMaintenance, onInspect, onRepair)
        DataMaintenanceFeedback(dataMaintenance)
    }
}

@Composable
private fun DataMaintenanceActions(
    dataMaintenance: DataMaintenanceUiState,
    onInspect: () -> Unit,
    onRepair: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryAction(
            text = if (dataMaintenance.inProgress) "处理中" else "检查学习数据",
            onClick = onInspect,
            modifier = Modifier.fillMaxWidth(),
            enabled = !dataMaintenance.inProgress,
        )
        if (dataMaintenance.hasRepairableIssues) {
            OutlinedButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                enabled = !dataMaintenance.inProgress,
                onClick = onRepair,
            ) {
                Text("修复学习数据缓存")
            }
        }
    }
}

@Composable
private fun DataMaintenanceFeedback(dataMaintenance: DataMaintenanceUiState) {
    if (dataMaintenance.message == null && dataMaintenance.errorMessage == null) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        dataMaintenance.message?.let {
            DataMaintenanceMessage(
                dataMaintenance = dataMaintenance,
                message = it,
                isStale = dataMaintenance.errorMessage != null,
            )
        }
        if (dataMaintenance.message != null && dataMaintenance.errorMessage != null) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                thickness = 1.dp,
            )
        }
        dataMaintenance.errorMessage?.let {
            DataMaintenanceError(
                errorMessage = it,
                hasPreviousResult = dataMaintenance.message != null,
            )
        }
    }
}

@Composable
private fun DataMaintenanceMessage(
    dataMaintenance: DataMaintenanceUiState,
    message: String,
    isStale: Boolean,
) {
    val hasIssues = (dataMaintenance.report?.issueCount ?: 0) > 0
    Column(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isStale) {
            Text(
                "上次检查结果",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VocabPill(
            text = if (hasIssues) "需要处理" else "状态正常",
            color =
                if (hasIssues) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
            contentColor =
                if (hasIssues) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DataMaintenanceError(
    errorMessage: String,
    hasPreviousResult: Boolean,
) {
    Column(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (hasPreviousResult) "本次操作失败" else "处理失败",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DataMaintenanceUiState.borderColor() =
    if (errorMessage != null || (report?.issueCount ?: 0) > 0) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.outline
    }

private fun DataMaintenanceUiState.statusSummary(): String =
    when {
        inProgress -> "处理中"
        errorMessage != null && message != null -> "结果已保留"
        errorMessage != null -> "处理失败"
        report == null -> "未检查"
        report.issueCount > 0 -> "需要处理"
        else -> "状态正常"
    }

@Composable
private fun SourceRow(source: SourceInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            source.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        VocabPill(
            text = source.licenseStatus,
            color =
                if (source.publishBlocking) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
            contentColor =
                if (source.publishBlocking) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
        )
        Text(
            "许可证：${source.license}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            source.notes,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (source.publishBlocking) {
            Text(
                "发布阻断：仅保留许可证说明，不进入发布词库。",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReminderTimeStepper(
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StepperRow(
            label = "小时",
            value = hour.twoDigits(),
            onDecrease = { onTimeChange((hour + 23) % 24, minute) },
            onIncrease = { onTimeChange((hour + 1) % 24, minute) },
        )
        StepperRow(
            label = "分钟",
            value = minute.twoDigits(),
            onDecrease = { onTimeChange(hour, (minute + 55) % 60) },
            onIncrease = { onTimeChange(hour, (minute + 5) % 60) },
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IconButton(modifier = Modifier.heightIn(min = 44.dp), onClick = onDecrease) {
            Icon(Icons.Outlined.Remove, contentDescription = "$label 减少")
        }
        Text(
            text = value,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(modifier = Modifier.heightIn(min = 44.dp), onClick = onIncrease) {
            Icon(Icons.Outlined.Add, contentDescription = "$label 增加")
        }
    }
}

private fun Context.readReminderNotificationState(): ReminderNotificationState {
    val hasRuntimePermission =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val notificationsEnabled =
        getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    return resolveReminderNotificationState(
        sdkInt = Build.VERSION.SDK_INT,
        hasRuntimePermission = hasRuntimePermission,
        notificationsEnabled = notificationsEnabled,
    )
}

private fun Context.openNotificationSettings() {
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    startActivity(intent)
}

private fun formatReminderTime(
    hour: Int,
    minute: Int,
): String = "${hour.twoDigits()}:${minute.twoDigits()}"

private fun formatReminderSummary(
    reminderEnabled: Boolean,
    hour: Int,
    minute: Int,
): String =
    if (reminderEnabled) {
        "每日 ${formatReminderTime(hour, minute)}"
    } else {
        "开启后按 ${formatReminderTime(hour, minute)} 提醒"
    }

private fun Int.twoDigits(): String = coerceIn(0, 99).toString().padStart(2, '0')
