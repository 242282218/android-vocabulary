package com.zzz.androidvocab.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
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
import com.zzz.androidvocab.core.designsystem.VocabLoadingCard
import com.zzz.androidvocab.core.designsystem.VocabPageHeader
import com.zzz.androidvocab.core.designsystem.VocabScreen
import com.zzz.androidvocab.core.designsystem.VocabSparkline
import com.zzz.androidvocab.core.designsystem.VocabThemeExtras
import com.zzz.androidvocab.core.designsystem.WarmHeroCard
import com.zzz.androidvocab.core.designsystem.vocabWholePercent
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.DailyActivity
import com.zzz.androidvocab.core.model.DailyReviewLoad
import com.zzz.androidvocab.core.model.DifficultWord
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun StatsRoute(viewModel: StatsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreen(uiState)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(uiState: StatsUiState) {
    VocabScreen {
        val scopeLabel = uiState.selectedBooks.scopeLabel()
        VocabPageHeader(
            title = "统计",
            subtitle = "复盘学习节奏，提前看见未来的复习压力。",
        )
        if (uiState.isLoading) {
            StatsLoadingCard()
            return@VocabScreen
        }
        StatsSnapshotCard(uiState)
        VocabCard {
            SectionTitle("复习趋势", detail = windowScopeLabel("7 天", scopeLabel))
            ActivityTrend(uiState.activity.takeLast(7))
        }
        VocabCard {
            SectionTitle("月历热力图", detail = windowScopeLabel("35 天", scopeLabel))
            ActivityHeatmap(uiState.activity)
        }
        VocabCard {
            SectionTitle("未来负载", detail = windowScopeLabel("30 天", scopeLabel))
            ReviewLoadChart(uiState.load, scopeLabel)
        }
        VocabCard {
            SectionTitle("掌握分布", detail = scopeLabel)
            MasteryDistribution(uiState.progress)
        }
        BookProgressListCard(progress = uiState.progress, scopeLabel = scopeLabel)
        DifficultWordsCard(uiState.difficultWords, windowScopeLabel("30 天", scopeLabel))
    }
}

@Composable
private fun StatsLoadingCard() {
    VocabLoadingCard(
        title = "正在加载统计",
        body = "正在从本地复习记录汇总趋势、负载和困难词。",
    )
}

@Composable
private fun StatsSnapshotCard(uiState: StatsUiState) {
    val activeDaysLabel = "${uiState.streak.activeDays.size}"
    WarmHeroCard {
        SectionTitle("连续学习", detail = uiState.selectedBooks.scopeLabel())
        Text(
            "当前连续 ${uiState.streak.currentStreak} 天，" +
                "近 30 天平均记忆保持 ${vocabWholePercent(uiState.retention.averageRetrievability)}%。",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 380.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricBlock(
                        "最长连续",
                        uiState.streak.maxStreak.toString(),
                        modifier = Modifier.fillMaxWidth(),
                        valueColor = MaterialTheme.colorScheme.primary,
                    )
                    MetricBlock(
                        "累计活跃",
                        activeDaysLabel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MetricBlock(
                        "30天 P50",
                        "${vocabWholePercent(uiState.retention.p50Retrievability)}%",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricBlock(
                        "最长连续",
                        uiState.streak.maxStreak.toString(),
                        Modifier.weight(1f),
                        valueColor = MaterialTheme.colorScheme.primary,
                    )
                    MetricBlock(
                        "累计活跃",
                        activeDaysLabel,
                        Modifier.weight(1f),
                    )
                    MetricBlock(
                        "30天 P50",
                        "${vocabWholePercent(uiState.retention.p50Retrievability)}%",
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DifficultWordsCard(
    words: List<DifficultWord>,
    scopeLabel: String,
) {
    VocabCard {
        SectionTitle("困难词", detail = scopeLabel)
        if (words.isEmpty()) {
            VocabEmptyState(
                title = "暂无困难词",
                body = "继续复习后，这里会按 again / hard 频率显示最需要巩固的词。",
            )
        }
        words.forEach { DifficultWordRow(it) }
    }
}

@Composable
private fun DifficultWordRow(item: DifficultWord) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = difficultWordDescription(item)
                },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.word.word, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                item.word.meaning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatDifficultyScore(item.difficultyScore),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun difficultWordDescription(item: DifficultWord): String =
    "困难词 ${item.word.word}，释义 ${item.word.meaning}，" +
        "again ${item.againCount} 次，hard ${item.hardCount} 次，" +
        "困难度 ${formatDifficultyScore(item.difficultyScore)}。"

private fun formatDifficultyScore(score: Double): String {
    val tenths = (score * 10).roundToInt()
    return if (tenths % 10 == 0) {
        (tenths / 10).toString()
    } else {
        "${tenths / 10}.${tenths % 10}"
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
private fun ReviewLoadChart(
    load: List<DailyReviewLoad>,
    scopeLabel: String,
) {
    if (load.isEmpty()) {
        VocabEmptyState(title = "暂无负载", body = "当前词书：$scopeLabel。暂无未来 30 天到期词。")
        return
    }
    MiniReviewLoadChart(load = load, maxBars = 30, height = 118.dp)
    Text(
        "当前词书：$scopeLabel。未来到期词数，今天是最左侧。高峰前提前复习能削平压力。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun List<BookCode>.scopeLabel(): String =
    when (val orderedBooks = sortedBy { BookCode.entries.indexOf(it) }) {
        emptyList<BookCode>() -> ALL_BOOKS_SCOPE_LABEL
        else ->
            when {
                orderedBooks.size == 1 -> orderedBooks.single().displayName
                orderedBooks.size == 2 -> orderedBooks.joinToString("、") { it.displayName }
                else -> "${orderedBooks[0].displayName}、${orderedBooks[1].displayName}等${orderedBooks.size}本"
            }
    }

private const val ALL_BOOKS_SCOPE_LABEL = "全部词书"

private fun windowScopeLabel(
    window: String,
    scopeLabel: String,
): String = "$window · $scopeLabel"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityHeatmap(activity: List<DailyActivity>) {
    if (activity.isEmpty()) {
        VocabEmptyState(
            title = "暂无月历热力图",
            body = "完成复习后，这里会按本地日期显示最近 35 天的学习密度。",
        )
        return
    }
    val max = activity.maxOfOrNull { it.reviewCount }?.coerceAtLeast(1) ?: 1
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = activityHeatmapDescription(activity)
                },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        maxItemsInEachRow = 7,
    ) {
        activity.forEach { item ->
            val level = activityHeatmapLevel(item.reviewCount, max)
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

internal fun activityHeatmapLevel(
    reviewCount: Int,
    maxReviewCount: Int,
): Int {
    if (reviewCount <= 0 || maxReviewCount <= 0) return 0
    return ceil(reviewCount.toDouble() / maxReviewCount.toDouble() * HEATMAP_LEVEL_COUNT)
        .toInt()
        .coerceIn(1, HEATMAP_LEVEL_COUNT)
}

private const val HEATMAP_LEVEL_COUNT = 4

private fun activityHeatmapDescription(activity: List<DailyActivity>): String {
    if (activity.isEmpty()) {
        return "月历热力图，暂无复习记录。"
    }
    val totalReviewCount = activity.sumOf { it.reviewCount }
    val activeDays = activity.count { it.reviewCount > 0 }
    val maxReviewCount = activity.maxOf { it.reviewCount }
    return "月历热力图，显示 ${activity.size} 天，活跃 $activeDays 天，总复习 $totalReviewCount 次，最高单日 $maxReviewCount 次。"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MasteryDistribution(progress: List<BookProgress>) {
    val total = progress.sumOf { it.totalCount }
    val learned = progress.sumOf { it.learnedCount }
    val mastered = progress.sumOf { it.masteredCount }
    val unlearned = (total - learned).coerceAtLeast(0)
    val learning = (learned - mastered).coerceAtLeast(0)
    if (total == 0) {
        VocabEmptyState(
            title = "暂无掌握分布",
            body = "当前词书暂无可统计词条。完成词库导入或选择词书后，会显示未学、学习中和掌握占比。",
        )
        return
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = masteryDistributionDescription(unlearned, learning, mastered, total)
                },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DistributionSegment(unlearned, MaterialTheme.colorScheme.surfaceVariant)
        DistributionSegment(learning, MaterialTheme.colorScheme.primary)
        DistributionSegment(mastered, VocabColors.Success)
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TinyLegend("未学 $unlearned", MaterialTheme.colorScheme.surfaceVariant)
        TinyLegend("学习中 $learning", MaterialTheme.colorScheme.primary)
        TinyLegend("掌握 $mastered", VocabColors.Success)
    }
}

private fun masteryDistributionDescription(
    unlearned: Int,
    learning: Int,
    mastered: Int,
    total: Int,
): String = "掌握分布图，未学 $unlearned 个，学习中 $learning 个，掌握 $mastered 个，总计 $total 个。"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookProgressListCard(
    progress: List<BookProgress>,
    scopeLabel: String,
) {
    VocabCard {
        SectionTitle("词书进度", detail = scopeLabel)
        if (progress.isEmpty()) {
            VocabEmptyState(
                title = "暂无词书进度",
                body = "当前学习范围暂无词书数据。完成词库导入或重新选择词书后，会显示各词书进度。",
            )
            return@VocabCard
        }
        progress.forEach { BookProgressRow(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookProgressRow(progress: BookProgress) {
    val fraction =
        if (progress.totalCount == 0) {
            0f
        } else {
            progress.learnedCount.toFloat() / progress.totalCount.toFloat()
        }.coerceIn(0f, 1f)
    val progressPercent = vocabWholePercent(fraction)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(progress.bookCode.displayName, fontWeight = FontWeight.SemiBold)
            Text(
                "$progressPercent%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                        contentDescription = "${progress.bookCode.displayName} 词书学习进度 $progressPercent%"
                    },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DistributionSegment(progress.learnedCount, MaterialTheme.colorScheme.primary)
            DistributionSegment(
                (progress.totalCount - progress.learnedCount).coerceAtLeast(0),
                MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TinyLegend(
                label = "已学 ${progress.learnedCount}/${progress.totalCount}",
                color = MaterialTheme.colorScheme.primary,
            )
            TinyLegend(
                label = "待复习 ${progress.dueCount}",
                color = MaterialTheme.colorScheme.surfaceVariant,
            )
            TinyLegend(
                label = "掌握 ${progress.masteredCount}",
                color = VocabColors.Success,
            )
        }
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
private fun activityColor(level: Int): Color {
    val colors = VocabThemeExtras.colors
    return when (level) {
        0 -> MaterialTheme.colorScheme.surfaceVariant
        1 -> colors.heatmapLevel1
        2 -> colors.heatmapLevel2
        3 -> colors.heatmapLevel3
        else -> colors.heatmapLevel4
    }
}
