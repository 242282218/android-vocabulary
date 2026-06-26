package com.zzz.androidvocab.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zzz.androidvocab.core.model.DailyReviewLoad
import kotlin.math.roundToInt

val ScreenHorizontalPadding = 20.dp
val ScreenVerticalPadding = 16.dp

val VocabCardShape = RoundedCornerShape(10.dp)
val VocabControlShape = RoundedCornerShape(10.dp)
val VocabHeroShape = RoundedCornerShape(12.dp)
val VocabPillShape = RoundedCornerShape(999.dp)
val VocabTinyBarShape = RoundedCornerShape(6.dp)
val VocabTinyLegendDotShape = RoundedCornerShape(4.dp)

@Composable
fun VocabScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHorizontalPadding, vertical = ScreenVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
fun VocabCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = VocabCardShape,
        border = BorderStroke(0.6.dp, borderColor.copy(alpha = 0.6f)),
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (elevated) 0.5.dp else 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
fun VocabLoadingCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    elevated: Boolean = true,
) {
    VocabCard(
        modifier =
            modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            },
        elevated = elevated,
    ) {
        VocabLoadingState(title = title, body = body)
    }
}

@Composable
fun VocabErrorCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    VocabCard(
        modifier =
            modifier.semantics {
                liveRegion = LiveRegionMode.Assertive
            },
        elevated = elevated,
        containerColor = VocabThemeExtras.colors.errorCardBackground,
        borderColor = vocabErrorBorderColor(),
        content = content,
    )
}

@Composable
fun vocabErrorBorderColor(): Color = MaterialTheme.colorScheme.error.copy(alpha = VOCAB_ERROR_BORDER_ALPHA)

private const val VOCAB_ERROR_BORDER_ALPHA = 0.24f
private const val VOCAB_DISABLED_CONTENT_ALPHA = 0.38f

@Composable
private fun disabledContentColor(): Color =
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = VOCAB_DISABLED_CONTENT_ALPHA)

@Composable
fun VocabInlineError(
    text: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Column(
        modifier =
            modifier.semantics {
                liveRegion = LiveRegionMode.Assertive
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        supportingText?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun VocabLoadingState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        VocabEmptyState(title = title, body = body)
        CircularProgressIndicator()
    }
}

@Composable
fun WarmHeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = VocabThemeExtras.colors
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = VocabHeroShape,
        border = BorderStroke(0.8.dp, colors.heroBorder.copy(alpha = 0.7f)),
        colors =
            CardDefaults.cardColors(
                containerColor = colors.heroBackground,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
fun MetricBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun VocabPageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Row(content = trailing)
        }
    }
}

@Composable
fun SectionTitle(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val shouldStackDetail = detail != null && maxWidth < SectionTitleStackBreakpoint
        if (detail == null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (shouldStackDetail) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(SECTION_TITLE_TITLE_WEIGHT),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail,
                    modifier = Modifier.weight(SECTION_TITLE_DETAIL_WEIGHT),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val SECTION_TITLE_TITLE_WEIGHT = 1.4f
private const val SECTION_TITLE_DETAIL_WEIGHT = 1f
private val SectionTitleStackBreakpoint = 340.dp

@Composable
fun VocabPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(
        modifier = modifier.heightIn(min = 28.dp),
        color = color,
        contentColor = contentColor,
        shape = VocabPillShape,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun VocabFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .semantics {
                    stateDescription = if (selected) "已选择" else "未选择"
                },
        enabled = enabled,
        label = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        shape = VocabControlShape,
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledLabelColor = disabledContentColor(),
            ),
    )
}

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        onClick = onClick,
        shape = VocabControlShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = disabledContentColor(),
            ),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        onClick = onClick,
        shape = VocabControlShape,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContentColor = disabledContentColor(),
            ),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun VocabProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    semanticDescription: String? = null,
) {
    val coercedProgress = progress.coerceIn(0f, 1f)
    val progressDescription = semanticDescription ?: "进度 ${vocabWholePercent(coercedProgress)}%"
    LinearProgressIndicator(
        progress = { coercedProgress },
        modifier =
            modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(VocabPillShape)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(coercedProgress, 0f..1f)
                    contentDescription = progressDescription
                },
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        color = color,
    )
}

fun vocabWholePercent(value: Float): Int = (value.coerceIn(0f, 1f) * 100).roundToInt()

fun vocabWholePercent(value: Double): Int = (value.coerceIn(0.0, 1.0) * 100).roundToInt()

@Composable
fun MiniReviewLoadChart(
    load: List<DailyReviewLoad>,
    modifier: Modifier = Modifier,
    maxBars: Int = 14,
    height: Dp = 48.dp,
) {
    val visibleLoad = load.take(maxBars).map { it.copy(dueCount = it.dueCount.coerceAtLeast(0)) }
    val maxDueCount = visibleLoad.maxOfOrNull { it.dueCount } ?: 0
    val scaleMax = maxDueCount.coerceAtLeast(1)
    val totalDueCount = visibleLoad.sumOf { it.dueCount }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        if (visibleLoad.isEmpty()) {
                            "复习负载图，暂无到期词。"
                        } else {
                            "复习负载图，显示 ${visibleLoad.size} 天，总到期 $totalDueCount 个，最高单日 $maxDueCount 个。"
                        }
                },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        visibleLoad.forEach { item ->
            val fraction = item.dueCount.toFloat() / scaleMax.toFloat()
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(height),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp + (height - 8.dp) * fraction)
                            .clip(VocabTinyBarShape)
                            .background(
                                if (item.dueCount == 0) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                                },
                            ),
                )
            }
        }
    }
}

@Composable
fun TinyLegend(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(VocabTinyLegendDotShape)
                    .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun VocabSparkline(
    values: List<Int>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val gridColor = MaterialTheme.colorScheme.outline
    val normalizedValues = values.ifEmpty { listOf(0) }
    val total = values.sum()
    val max = values.maxOrNull() ?: 0
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(118.dp)
                .semantics {
                    contentDescription =
                        if (values.isEmpty()) {
                            "趋势图，暂无复习记录。"
                        } else {
                            "趋势图，显示 ${values.size} 天，总复习 $total 次，最高单日 $max 次。"
                        }
                },
    ) {
        val max = normalizedValues.maxOrNull()?.coerceAtLeast(1) ?: 1
        val min = normalizedValues.minOrNull()?.coerceAtLeast(0) ?: 0
        val range = (max - min).coerceAtLeast(1)
        val stepX =
            if (normalizedValues.size <= 1) {
                size.width
            } else {
                size.width / normalizedValues.lastIndex.toFloat()
            }
        repeat(3) { index ->
            val y = size.height * (index + 1) / 4f
            drawLine(
                color = gridColor.copy(alpha = 0.42f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        normalizedValues.zipWithNext().forEachIndexed { index, pair ->
            val startY = size.height - ((pair.first - min).toFloat() / range.toFloat()) * size.height
            val endY = size.height - ((pair.second - min).toFloat() / range.toFloat()) * size.height
            drawLine(
                color = color,
                start = Offset(index * stepX, startY.coerceIn(0f, size.height)),
                end = Offset((index + 1) * stepX, endY.coerceIn(0f, size.height)),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun VocabEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}
