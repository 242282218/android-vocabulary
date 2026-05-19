package com.zzz.androidvocab.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zzz.androidvocab.core.designsystem.MetricBlock
import com.zzz.androidvocab.core.designsystem.MiniReviewLoadChart
import com.zzz.androidvocab.core.designsystem.SectionTitle
import com.zzz.androidvocab.core.designsystem.TinyLegend
import com.zzz.androidvocab.core.designsystem.VocabCard
import com.zzz.androidvocab.core.designsystem.VocabColors
import com.zzz.androidvocab.core.designsystem.VocabEmptyState
import com.zzz.androidvocab.core.designsystem.VocabPageHeader
import com.zzz.androidvocab.core.designsystem.VocabScreen
import com.zzz.androidvocab.core.designsystem.VocabSparkline
import com.zzz.androidvocab.core.designsystem.WarmHeroCard
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.DailyActivity
import com.zzz.androidvocab.core.model.DailyReviewLoad

@Composable
fun StatsRoute(viewModel: StatsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreen(uiState)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(uiState: StatsUiState) {
    VocabScreen {
        VocabPageHeader(
            title = "统计",
            subtitle = "复盘学习节奏，提前看见未来的复习压力。",
        )
        StatsSnapshotCard(uiState)
        VocabCard {
            SectionTitle("近 7 天复习趋势")
            ActivityTrend(uiState.activity.takeLast(7))
        }
        VocabCard {
            SectionTitle("月历热力图")
            ActivityHeatmap(uiState.activity)
        }
        VocabCard {
            SectionTitle("未来 30 天复习负载")
            ReviewLoadChart(uiState.load)
        }
        VocabCard {
            SectionTitle("掌握分布")
            MasteryDistribution(uiState.progress)
        }
        VocabCard {
            SectionTitle("词书进度")
            uiState.progress.forEach { BookProgressRow(it) }
        }
        VocabCard {
            SectionTitle("困难词 Top 10")
            if (uiState.difficultWords.isEmpty()) {
                VocabEmptyState(
                    title = "暂无困难词",
                    body = "继续复习后，这里会按 again / hard 频率显示最需要巩固的词。",
                )
            }
            uiState.difficultWords.forEach {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(it.word.word, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            it.word.meaning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "${it.difficultyScore.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsSnapshotCard(uiState: StatsUiState) {
    WarmHeroCard {
        SectionTitle("连续学习", detail = "最近 30 天")
        Text(
            "当前连续 ${uiState.streak.currentStreak} 天，平均记忆保持 ${(uiState.retention.averageRetrievability * 100).toInt()}%。",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
            MetricBlock(
                "最长",
                uiState.streak.maxStreak.toString(),
                Modifier.weight(1f),
                valueColor = MaterialTheme.colorScheme.primary,
            )
            MetricBlock(
                "活跃",
                uiState.streak.activeDays.size
                    .toString(),
                Modifier.weight(1f),
            )
            MetricBlock(
                "P50",
                "${(uiState.retention.p50Retrievability * 100).toInt()}%",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActivityTrend(activity: List<DailyActivity>) {
    val values = activity.map { it.reviewCount }
    if (values.isEmpty()) {
        VocabEmptyState(title = "暂无趋势", body = "完成一次复习后会出现近 7 天趋势。")
        return
    }
    VocabSparkline(values = values)
    Text(
        "按本地日期统计复习次数，线条越平稳，后续负载越可控。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReviewLoadChart(load: List<DailyReviewLoad>) {
    if (load.isEmpty()) {
        VocabEmptyState(title = "暂无负载", body = "复习卡片产生到期时间后，会显示未来 30 天负载。")
        return
    }
    MiniReviewLoadChart(load = load, maxBars = 30, height = 118.dp)
    Text(
        "未来到期词数，今天是最左侧。高峰前提前复习能削平压力。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityHeatmap(activity: List<DailyActivity>) {
    val max = activity.maxOfOrNull { it.reviewCount }?.coerceAtLeast(1) ?: 1
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        maxItemsInEachRow = 7,
    ) {
        activity.forEach { item ->
            val level = ((item.reviewCount.toFloat() / max.toFloat()) * 4).toInt().coerceIn(0, 4)
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(activityColor(level)),
            )
        }
    }
}

@Composable
private fun MasteryDistribution(progress: List<BookProgress>) {
    val total = progress.sumOf { it.totalCount }
    val learned = progress.sumOf { it.learnedCount }
    val mastered = progress.sumOf { it.masteredCount }
    val unlearned = (total - learned).coerceAtLeast(0)
    val learning = (learned - mastered).coerceAtLeast(0)
    if (total == 0) {
        Text("暂无词书数据", style = MaterialTheme.typography.bodySmall)
        return
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DistributionSegment(unlearned, MaterialTheme.colorScheme.surfaceVariant)
        DistributionSegment(learning, MaterialTheme.colorScheme.primary)
        DistributionSegment(mastered, VocabColors.Success)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TinyLegend("未学 $unlearned", MaterialTheme.colorScheme.surfaceVariant)
        TinyLegend("学习中 $learning", MaterialTheme.colorScheme.primary)
        TinyLegend("掌握 $mastered", VocabColors.Success)
    }
}

@Composable
private fun BookProgressRow(progress: BookProgress) {
    val fraction =
        if (progress.totalCount == 0) {
            0f
        } else {
            progress.learnedCount.toFloat() / progress.totalCount.toFloat()
        }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(progress.bookCode.displayName, fontWeight = FontWeight.SemiBold)
            Text(
                "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DistributionSegment(progress.learnedCount, MaterialTheme.colorScheme.primary)
            DistributionSegment(
                (progress.totalCount - progress.learnedCount).coerceAtLeast(0),
                MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        Text(
            "${progress.learnedCount}/${progress.totalCount}  待复习 ${progress.dueCount}  掌握 ${progress.masteredCount}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RowScope.DistributionSegment(
    count: Int,
    color: Color,
) {
    if (count <= 0) return
    Box(
        modifier =
            Modifier
                .weight(count.toFloat())
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color),
    )
}

@Composable
private fun activityColor(level: Int): Color =
    when (level) {
        0 -> MaterialTheme.colorScheme.surfaceVariant
        1 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)
        2 -> MaterialTheme.colorScheme.secondaryContainer
        3 -> VocabColors.Success.copy(alpha = 0.72f)
        else -> MaterialTheme.colorScheme.primary
    }
