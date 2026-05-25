package com.zzz.androidvocab.feature.settings

import android.Manifest
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.zzz.androidvocab.core.model.SourceInfo
import com.zzz.androidvocab.core.model.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasNotificationPermission by remember { mutableStateOf(context.hasNotificationPermission()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasNotificationPermission = context.hasNotificationPermission()
    }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasNotificationPermission = granted
            if (granted) {
                viewModel.updateReminder(true)
            } else {
                viewModel.showReminderPermissionDenied()
            }
        }
    SettingsScreen(
        uiState = uiState,
        hasNotificationPermission = hasNotificationPermission,
        onDailyLimitChange = viewModel::updateDailyLimit,
        onTargetRetentionChange = viewModel::updateTargetRetention,
        onThemeChange = viewModel::updateTheme,
        onReminderChange = { enabled ->
            val permissionGranted = context.hasNotificationPermission()
            hasNotificationPermission = permissionGranted
            when {
                !enabled -> viewModel.updateReminder(false)
                permissionGranted -> viewModel.updateReminder(true)
                else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onReminderTimeChange = viewModel::updateReminderTime,
        onOpenNotificationSettings = { context.openNotificationSettings() },
        onExport = viewModel::exportData,
        onInspectLearningData = viewModel::inspectLearningData,
        onRepairLearningData = viewModel::repairLearningData,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    hasNotificationPermission: Boolean,
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
        VocabCard(elevated = false) {
            SectionTitle("学习设置")
            Text("每日新词上限 ${uiState.settings.dailyNewLimit}", style = MaterialTheme.typography.titleMedium)
            var dailyLimitSlider by remember(uiState.settings.dailyNewLimit) {
                mutableFloatStateOf(uiState.settings.dailyNewLimit.toFloat())
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
                "目标保持率 ${(uiState.settings.targetRetention * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
            )
            var retentionSlider by remember(uiState.settings.targetRetention) {
                mutableFloatStateOf(uiState.settings.targetRetention.toFloat())
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
        }
        VocabCard(elevated = false) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "每日 ${formatReminderTime(uiState.settings.reminderHour, uiState.settings.reminderMinute)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = uiState.settings.reminderEnabled, onCheckedChange = onReminderChange)
            }
            ReminderTimeStepper(
                hour = uiState.settings.reminderHour,
                minute = uiState.settings.reminderMinute,
                onTimeChange = onReminderTimeChange,
            )
            uiState.reminderErrorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (uiState.settings.reminderEnabled && !hasNotificationPermission) {
                Text(
                    "系统通知权限未开启，每日提醒不会显示。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onOpenNotificationSettings) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("打开通知设置")
                }
            }
        }
        VocabCard(elevated = false) {
            SectionTitle("外观")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.settings.themeMode == mode,
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
        }
        VocabCard(elevated = false) {
            SectionTitle("数据导出")
            PrimaryAction(
                text = if (uiState.isExporting) "导出中" else "导出 JSON",
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isExporting,
            )
            uiState.exportResult?.let {
                Text(
                    it.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            uiState.exportErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        VocabCard(
            elevated = false,
            containerColor = MaterialTheme.colorScheme.surface,
            borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.24f),
        ) {
            SectionTitle("数据维护")
            Text(
                "检查复习记录与学习缓存的一致性。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryAction(
                text = if (uiState.dataMaintenance.inProgress) "处理中" else "检查学习数据",
                onClick = onInspectLearningData,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.dataMaintenance.inProgress,
            )
            if ((uiState.dataMaintenance.report?.repairableIssueCount ?: 0) > 0) {
                OutlinedButton(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    enabled = !uiState.dataMaintenance.inProgress,
                    onClick = onRepairLearningData,
                ) {
                    Text("修复学习数据缓存")
                }
            }
            uiState.dataMaintenance.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            uiState.dataMaintenance.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        VocabCard(elevated = false) {
            SectionTitle("词库来源与许可证")
            uiState.sources.forEach { source -> SourceRow(source) }
        }
    }
}

@Composable
private fun SourceRow(source: SourceInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(source.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
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
        }
        Text(
            "${source.license} / ${source.notes}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (source.publishBlocking) {
            Text("发布阻断来源，仅保留许可证说明", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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

private fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

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

private fun Int.twoDigits(): String = coerceIn(0, 99).toString().padStart(2, '0')
