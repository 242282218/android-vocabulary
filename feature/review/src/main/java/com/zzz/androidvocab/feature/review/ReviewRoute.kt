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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.zzz.androidvocab.core.designsystem.VocabErrorCard
import com.zzz.androidvocab.core.designsystem.VocabLoadingCard
import com.zzz.androidvocab.core.designsystem.VocabPageHeader
import com.zzz.androidvocab.core.designsystem.VocabPill
import com.zzz.androidvocab.core.designsystem.VocabScreen
import com.zzz.androidvocab.core.designsystem.WarmHeroCard
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewRating
import java.util.Locale

@Composable
fun ReviewRoute(viewModel: ReviewViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val wordSpeaker = rememberWordSpeaker()
    ReviewScreen(
        uiState = uiState,
        onShowBack = viewModel::showBack,
        onSpeak = wordSpeaker.speak,
        isSpeechReady = wordSpeaker.isReady,
        onSubmit = viewModel::submit,
    )
}

@Composable
fun ReviewScreen(
    uiState: ReviewUiState,
    onShowBack: () -> Unit,
    onSpeak: (String) -> Unit,
    isSpeechReady: Boolean = true,
    onSubmit: (ReviewRating) -> Unit,
) {
    VocabScreen {
        ReviewHeader(uiState)
        if (uiState.isLoading) {
            VocabLoadingCard(
                title = "正在加载队列",
                body = "正在读取本地复习状态，稍后会显示下一张卡片。",
            )
            return@VocabScreen
        }
        if (uiState.isAdvancingToNextCard) {
            VocabLoadingCard(
                title = "正在准备下一张",
                body = "学习记录已经保存，正在刷新本地队列。",
            )
            return@VocabScreen
        }
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
            isSpeechReady = isSpeechReady,
        )
        if (uiState.isBackVisible) {
            FeedbackButtons(
                isSubmitting = uiState.isSubmitting,
                onSubmit = onSubmit,
            )
        }
        uiState.errorMessage?.let {
            VocabErrorCard {
                Text("反馈没有保存，当前卡片已保留。", style = MaterialTheme.typography.titleMedium)
                Text(
                    "可以再次选择下方反馈重试。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReviewHeader(uiState: ReviewUiState) {
    VocabPageHeader(
        title = "复习",
        subtitle =
            when {
                uiState.isLoading -> "正在读取今日队列"
                uiState.isAdvancingToNextCard -> "学习记录已保存，正在准备下一张"
                uiState.remainingCount > 0 -> "先回想，再看答案。剩余 ${uiState.remainingCount} 个"
                else -> "今天已完成"
            },
        trailing =
            if (uiState.isLoading) {
                null
            } else {
                {
                    VocabPill(
                        text = "${uiState.remainingCount}",
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                }
            },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackButtons(
    isSubmitting: Boolean,
    onSubmit: (ReviewRating) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isSubmitting) {
            Text(
                text = "正在保存反馈，按钮暂时不可用。",
                modifier =
                    Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
                isSubmitting = isSubmitting,
                onSubmit = onSubmit,
                modifier = Modifier.weight(1f),
            )
            FeedbackButton(
                label = "模糊",
                description = "有点卡",
                rating = ReviewRating.Hard,
                isSubmitting = isSubmitting,
                onSubmit = onSubmit,
                modifier = Modifier.weight(1f),
            )
            FeedbackButton(
                label = "记得",
                description = "正常想起",
                rating = ReviewRating.Good,
                isSubmitting = isSubmitting,
                onSubmit = onSubmit,
                modifier = Modifier.weight(1f),
            )
            FeedbackButton(
                label = "熟悉",
                description = "太简单",
                rating = ReviewRating.Easy,
                isSubmitting = isSubmitting,
                onSubmit = onSubmit,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReviewWordCard(
    item: ReviewQueueItem,
    isBackVisible: Boolean,
    onShowBack: () -> Unit,
    onSpeak: (String) -> Unit,
    isSpeechReady: Boolean,
) {
    WarmHeroCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp)
                .revealAnswerClickTarget(isBackVisible = isBackVisible, onShowBack = onShowBack),
    ) {
        ReviewWordHeader(item = item, isSpeechReady = isSpeechReady, onSpeak = onSpeak)
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
private fun ReviewWordHeader(
    item: ReviewQueueItem,
    isSpeechReady: Boolean,
    onSpeak: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VocabPill(text = if (item.isNew) "新词" else item.card.bookCode.displayName)
        IconButton(
            enabled = isSpeechReady,
            onClick = { onSpeak(item.word.word) },
        ) {
            val iconTint =
                if (isSpeechReady) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                }
            Icon(
                Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = if (isSpeechReady) "播放发音" else "发音暂不可用",
                tint = iconTint,
            )
        }
    }
}

private fun Modifier.revealAnswerClickTarget(
    isBackVisible: Boolean,
    onShowBack: () -> Unit,
): Modifier =
    if (isBackVisible) {
        this
    } else {
        clickable(
            onClickLabel = "显示释义",
            role = Role.Button,
            onClick = onShowBack,
        )
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

private data class WordSpeaker(
    val isReady: Boolean,
    val speak: (String) -> Unit,
)

@Composable
private fun rememberWordSpeaker(): WordSpeaker {
    val context = LocalContext.current
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    val isReady = remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine =
            TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val languageResult = engine?.setLanguage(Locale.US)
                    isReady.value =
                        languageResult != null &&
                        languageResult != TextToSpeech.LANG_MISSING_DATA &&
                        languageResult != TextToSpeech.LANG_NOT_SUPPORTED
                }
            }
        ttsRef.value = engine
        onDispose {
            isReady.value = false
            engine?.stop()
            engine?.shutdown()
            ttsRef.value = null
        }
    }
    return WordSpeaker(
        isReady = isReady.value,
        speak = { word ->
            if (isReady.value) {
                ttsRef.value?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "review-word-$word")
            }
        },
    )
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
