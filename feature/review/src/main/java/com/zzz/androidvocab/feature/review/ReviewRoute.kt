package com.zzz.androidvocab.feature.review

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zzz.androidvocab.core.designsystem.PrimaryAction
import com.zzz.androidvocab.core.designsystem.SectionTitle
import com.zzz.androidvocab.core.designsystem.VocabCard
import com.zzz.androidvocab.core.designsystem.VocabColors
import com.zzz.androidvocab.core.designsystem.VocabControlShape
import com.zzz.androidvocab.core.designsystem.VocabEmptyState
import com.zzz.androidvocab.core.designsystem.VocabPageHeader
import com.zzz.androidvocab.core.designsystem.VocabPill
import com.zzz.androidvocab.core.designsystem.VocabScreen
import com.zzz.androidvocab.core.designsystem.WarmHeroCard
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewRating
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ReviewRoute(viewModel: ReviewViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val speakWord = rememberWordSpeaker()
    ReviewScreen(
        uiState = uiState,
        onShowBack = viewModel::showBack,
        onSpeak = speakWord,
        onSubmit = viewModel::submit,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    uiState: ReviewUiState,
    onShowBack: () -> Unit,
    onSpeak: (String) -> Unit,
    onSubmit: (ReviewRating) -> Unit,
) {
    VocabScreen {
        VocabPageHeader(
            title = "复习",
            subtitle = if (uiState.remainingCount > 0) "先回想，再看答案。剩余 ${uiState.remainingCount} 个" else "今天已完成",
            trailing = {
                VocabPill(
                    text = "${uiState.remainingCount}",
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            },
        )
        val item = uiState.item
        if (item == null) {
            VocabCard {
                VocabEmptyState(
                    title = "今天已完成",
                    body = "今日队列已经清空，可以去统计页看看未来几周的复习负载。",
                )
            }
            return@VocabScreen
        }
        ReviewWordCard(
            item = item,
            isBackVisible = uiState.isBackVisible,
            onShowBack = onShowBack,
            onSpeak = onSpeak,
        )
        if (uiState.isBackVisible) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 2,
            ) {
                FeedbackButton(
                    label = "忘了",
                    description = "重新来",
                    rating = ReviewRating.Again,
                    isSubmitting = uiState.isSubmitting,
                    onSubmit = onSubmit,
                    modifier = Modifier.weight(1f),
                )
                FeedbackButton(
                    label = "模糊",
                    description = "有点卡",
                    rating = ReviewRating.Hard,
                    isSubmitting = uiState.isSubmitting,
                    onSubmit = onSubmit,
                    modifier = Modifier.weight(1f),
                )
                FeedbackButton(
                    label = "记得",
                    description = "正常想起",
                    rating = ReviewRating.Good,
                    isSubmitting = uiState.isSubmitting,
                    onSubmit = onSubmit,
                    modifier = Modifier.weight(1f),
                )
                FeedbackButton(
                    label = "熟悉",
                    description = "太简单",
                    rating = ReviewRating.Easy,
                    isSubmitting = uiState.isSubmitting,
                    onSubmit = onSubmit,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        uiState.errorMessage?.let {
            VocabCard(
                elevated = false,
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.24f),
                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.24f),
            ) {
                Text("反馈没有保存，当前卡片已保留。", style = MaterialTheme.typography.titleMedium)
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReviewWordCard(
    item: ReviewQueueItem,
    isBackVisible: Boolean,
    onShowBack: () -> Unit,
    onSpeak: (String) -> Unit,
) {
    WarmHeroCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 328.dp)
                .clickable { onShowBack() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VocabPill(text = if (item.isNew) "新词" else item.card.bookCode.displayName)
            IconButton(onClick = { onSpeak(item.word.word) }) {
                Icon(
                    Icons.AutoMirrored.Outlined.VolumeUp,
                    contentDescription = "播放发音",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = item.word.word,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style =
                MaterialTheme.typography.displaySmall.copy(
                    fontSize =
                        if (item.word.word.length > 14) {
                            30.sp
                        } else {
                            40.sp
                        },
                ),
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.word.phonetic ?: " ",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isBackVisible) {
            AnswerBlock(item)
        } else {
            Text(
                text = "先在心里说出释义，再点击查看答案。",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryAction("显示释义", onShowBack, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AnswerBlock(item: ReviewQueueItem) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("释义")
        Text(
            item.word.meaning,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        item.word.partOfSpeech?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item.word.definition?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun rememberWordSpeaker(): (String) -> Unit {
    val context = LocalContext.current
    val ttsState = remember { mutableStateOf<TextToSpeech?>(null) }
    val isReady = remember { AtomicBoolean(false) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine =
            TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val languageResult = engine?.setLanguage(Locale.US)
                    isReady.set(
                        languageResult != TextToSpeech.LANG_MISSING_DATA &&
                            languageResult != TextToSpeech.LANG_NOT_SUPPORTED,
                    )
                }
            }
        ttsState.value = engine
        onDispose {
            isReady.set(false)
            engine?.stop()
            engine?.shutdown()
            ttsState.value = null
        }
    }
    return { word ->
        if (isReady.get()) {
            ttsState.value?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "review-word-$word")
        }
    }
}

@Composable
private fun FeedbackButton(
    label: String,
    description: String,
    rating: ReviewRating,
    isSubmitting: Boolean,
    onSubmit: (ReviewRating) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color =
        when (rating) {
            ReviewRating.Again -> VocabColors.Danger
            ReviewRating.Hard -> VocabColors.Warning
            ReviewRating.Good -> MaterialTheme.colorScheme.primary
            ReviewRating.Easy -> VocabColors.Success
        }
    OutlinedButton(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
        enabled = !isSubmitting,
        onClick = { onSubmit(rating) },
        shape = VocabControlShape,
        border = BorderStroke(0.75.dp, color.copy(alpha = 0.34f)),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = color,
                containerColor = color.copy(alpha = 0.08f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
