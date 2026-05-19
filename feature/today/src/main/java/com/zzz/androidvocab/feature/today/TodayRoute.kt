package com.zzz.androidvocab.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import com.zzz.androidvocab.core.designsystem.VocabPageHeader
import com.zzz.androidvocab.core.designsystem.VocabPill
import com.zzz.androidvocab.core.designsystem.VocabProgressBar
import com.zzz.androidvocab.core.designsystem.VocabScreen
import com.zzz.androidvocab.core.designsystem.WarmHeroCard
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
            subtitle = "清掉今天的队列，未来复习压力会更平稳。",
        )
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            return@VocabScreen
        }
        if (uiState.isImporting) {
            VocabCard {
                VocabEmptyState(
                    title = "正在准备词库",
                    body = "正在校验并导入 publish-safe 词库，完成后会生成今日学习队列。",
                )
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            return@VocabScreen
        }
        uiState.errorMessage?.let {
            VocabCard {
                VocabEmptyState(title = "词库导入失败", body = it)
                PrimaryAction("重试导入", onRetryImport, Modifier.fillMaxWidth())
            }
        }
        val overview = uiState.overview
        if (overview != null) {
            TodayFocusCard(overview = overview, onStartReview = onStartReview)
            VocabCard {
                SectionTitle("完成进度", detail = "预计 ${overview.stats.estimatedMinutes} 分钟")
                TodayProgress(overview)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricBlock("完成", overview.stats.completedCount.toString(), Modifier.weight(1f))
                    MetricBlock("正确率", "${(overview.stats.recallAccuracy * 100).toInt()}%", Modifier.weight(1f))
                    MetricBlock("词书", overview.selectedBooks.size.toString(), Modifier.weight(1f))
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
    }
}

@Composable
private fun TodayFocusCard(
    overview: TodayOverview,
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
                        if (overview.queue.totalCount > 0) {
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
                        if (overview.queue.totalCount > 0) {
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
        PrimaryAction(
            text = if (overview.queue.totalCount > 0) "开始学习" else "今天已完成",
            onClick = onStartReview,
            modifier = Modifier.fillMaxWidth(),
            enabled = overview.queue.totalCount > 0,
        )
    }
}

@Composable
private fun TodayProgress(overview: TodayOverview) {
    val total = overview.stats.completedCount + overview.queue.totalCount
    val fraction =
        if (total == 0) {
            1f
        } else {
            overview.stats.completedCount.toFloat() / total.toFloat()
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VocabProgressBar(progress = fraction)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "进度 ${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                overview.selectedBooks.joinToString { it.displayName },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
