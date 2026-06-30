package com.zzz.androidvocab.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zzz.androidvocab.core.designsystem.MetricBlock
import com.zzz.androidvocab.core.designsystem.MiniReviewLoadChart
import com.zzz.androidvocab.core.designsystem.PrimaryAction
import com.zzz.androidvocab.core.designsystem.SectionTitle
import com.zzz.androidvocab.core.designsystem.VocabCard
import com.zzz.androidvocab.core.designsystem.VocabEmptyState
import com.zzz.androidvocab.core.designsystem.VocabErrorCard
import com.zzz.androidvocab.core.designsystem.VocabLoadingCard
import com.zzz.androidvocab.core.designsystem.VocabPageHeader
import com.zzz.androidvocab.core.designsystem.VocabPill
import com.zzz.androidvocab.core.designsystem.VocabProgressBar
import com.zzz.androidvocab.core.designsystem.VocabScreen
import com.zzz.androidvocab.core.designsystem.WarmHeroCard
import com.zzz.androidvocab.core.designsystem.vocabWholePercent
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.TodayOverview

@Composable
fun TodayRoute(
    onStartReview: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(uiState = uiState, onStartReview = onStartReview, onRetryImport = viewModel::retryImport)
}

@Composable
fun TodayScreen(
    uiState: TodayUiState,
    onStartReview: () -> Unit,
    onRetryImport: () -> Unit,
) {
    VocabScreen {
        VocabPageHeader(
            title = "今日",
            subtitle =
                when {
                    uiState.isRefreshingReviewSession -> "学习记录已保存，正在同步今日队列。"
                    else -> "清掉今天的队列，未来复习压力会更平稳。"
                },
        )
        if (uiState.isLoading) {
            VocabLoadingCard(
                title = "正在加载今日队列",
                body = "正在读取本地学习状态和未来复习负载。",
            )
            return@VocabScreen
        }
        val overview = uiState.overview
        if (uiState.isImporting && overview?.hasLearningEvidence() != true) {
            TodayImportingCard()
            return@VocabScreen
        }
        if (uiState.errorMessage != null && overview?.hasLearningEvidence() != true) {
            TodayImportErrorCard(
                message = uiState.errorMessage,
                onRetryImport = onRetryImport,
            )
            return@VocabScreen
        }
        if (overview != null) {
            TodayOverviewContent(
                uiState = uiState,
                overview = overview,
                onStartReview = onStartReview,
                onRetryImport = onRetryImport,
            )
        } else {
            TodayMissingOverviewCard(onRetryImport = onRetryImport)
        }
    }
}

@Composable
private fun TodayOverviewContent(
    uiState: TodayUiState,
    overview: TodayOverview,
    onStartReview: () -> Unit,
    onRetryImport: () -> Unit,
) {
    TodayFocusCard(
        overview = overview,
        isRefreshingReviewSession = uiState.isRefreshingReviewSession,
        onStartReview = onStartReview,
    )
    if (uiState.isImporting) {
        TodayImportingCard()
    } else {
        uiState.errorMessage?.let {
            TodayImportErrorCard(
                message = it,
                onRetryImport = onRetryImport,
            )
        }
    }
    VocabCard {
        SectionTitle("完成进度", detail = "预计 ${overview.stats.estimatedMinutes} 分钟")
        TodayProgress(overview)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            MetricBlock("完成", overview.stats.completedCount.toString(), Modifier.weight(1f))
            MetricBlock("正确率", "${vocabWholePercent(overview.stats.recallAccuracy)}%", Modifier.weight(1f))
            MetricBlock("词书", todaySelectedBookCount(overview.selectedBooks).toString(), Modifier.weight(1f))
        }
    }
    VocabCard {
        SectionTitle("未来 14 天", detail = "复习压力")
        MiniReviewLoadChart(load = uiState.reviewLoad, height = 56.dp)
        Text(
            text = "柱越高表示当天到期词越多，提前完成可以让后续更平滑。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodayImportingCard() {
    VocabLoadingCard(
        title = "正在准备词库",
        body = "正在校验并导入 publish-safe 词库，完成后会生成今日学习队列。",
        elevated = false,
    )
}

@Composable
private fun TodayImportErrorCard(
    message: String,
    onRetryImport: () -> Unit,
) {
    VocabErrorCard {
        VocabEmptyState(title = "词库导入失败", body = message)
        PrimaryAction("重试导入", onRetryImport, Modifier.fillMaxWidth())
    }
}

@Composable
private fun TodayMissingOverviewCard(onRetryImport: () -> Unit) {
    VocabCard {
        VocabEmptyState(
            title = "暂无今日队列",
            body = "还没有从本地词库生成今日学习队列。可以重新导入词库，完成后会显示今天的新词和复习词。",
        )
        PrimaryAction("重试导入", onRetryImport, Modifier.fillMaxWidth())
    }
}

@Composable
private fun TodayFocusCard(
    overview: TodayOverview,
    isRefreshingReviewSession: Boolean,
    onStartReview: () -> Unit,
) {
    WarmHeroCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "今日队列",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                VocabPill(
                    text =
                        if (isRefreshingReviewSession) {
                            "同步中"
                        } else if (overview.queue.totalCount > 0) {
                            "剩余 ${overview.queue.totalCount} 个"
                        } else {
                            "已完成"
                        },
                    color =
                        if (overview.queue.totalCount > 0) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                    contentColor =
                        if (isRefreshingReviewSession || overview.queue.totalCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                )
            }
            Text(
                text = overview.queue.totalCount.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            TodayMetrics(overview)
        }
        PrimaryAction(
            text =
                when {
                    isRefreshingReviewSession -> "正在同步"
                    overview.queue.totalCount > 0 -> "开始学习"
                    else -> "今天已完成"
                },
            onClick = onStartReview,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRefreshingReviewSession && overview.queue.totalCount > 0,
        )
    }
}

@Composable
private fun RowScope.TodayMetrics(overview: TodayOverview) {
    MetricBlock(
        "新学",
        overview.queue.newItems.size
            .toString(),
        Modifier.weight(1f),
        valueColor = MaterialTheme.colorScheme.primary,
    )
    MetricBlock(
        "复习",
        overview.queue.dueItems.size
            .toString(),
        Modifier.weight(1f),
        valueColor = MaterialTheme.colorScheme.primary,
    )
    MetricBlock("已完成", overview.stats.completedCount.toString(), Modifier.weight(1f))
}

private fun TodayOverview.hasLearningEvidence(): Boolean =
    queue.totalCount > 0 ||
        stats.completedCount > 0 ||
        stats.newCount > 0 ||
        stats.reviewCount > 0

@Composable
private fun TodayProgress(overview: TodayOverview) {
    val total = overview.stats.completedCount + overview.queue.totalCount
    val fraction =
        if (total == 0) {
            0f
        } else {
            overview.stats.completedCount.toFloat() / total.toFloat()
        }
    val progressPercent = vocabWholePercent(fraction)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VocabProgressBar(
            progress = fraction,
            semanticDescription = "今日完成进度 $progressPercent%",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "进度 $progressPercent%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                todaySelectedBookLabel(overview.selectedBooks),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun todaySelectedBookCount(selectedBooks: List<BookCode>): Int =
    if (selectedBooks.isEmpty()) BookCode.entries.size else selectedBooks.size

private fun todaySelectedBookLabel(selectedBooks: List<BookCode>): String =
    if (selectedBooks.isEmpty()) {
        "全部词书"
    } else {
        selectedBooks.joinToString { it.displayName }
    }
