package com.zzz.androidvocab.feature.wordbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.zzz.androidvocab.core.designsystem.SectionTitle
import com.zzz.androidvocab.core.designsystem.VocabCard
import com.zzz.androidvocab.core.designsystem.VocabControlShape
import com.zzz.androidvocab.core.designsystem.VocabEmptyState
import com.zzz.androidvocab.core.designsystem.VocabPageHeader
import com.zzz.androidvocab.core.designsystem.VocabPill
import com.zzz.androidvocab.core.designsystem.VocabProgressBar
import com.zzz.androidvocab.core.designsystem.VocabScreen
import com.zzz.androidvocab.core.designsystem.WarmHeroCard
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.WordDetail
import com.zzz.androidvocab.core.model.WordEntry
import com.zzz.androidvocab.core.model.WordStatusFilter

@Composable
fun WordbookRoute(viewModel: WordbookViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WordbookScreen(
        uiState = uiState,
        onQueryChange = viewModel::updateQuery,
        onStatusFilterChange = viewModel::updateStatusFilter,
        onToggleBook = viewModel::toggleBook,
        onSelectWord = viewModel::selectWord,
        onClearSelectedWord = viewModel::clearSelectedWord,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordbookScreen(
    uiState: WordbookUiState,
    onQueryChange: (String) -> Unit,
    onStatusFilterChange: (WordStatusFilter) -> Unit,
    onToggleBook: (BookCode) -> Unit,
    onSelectWord: (String) -> Unit,
    onClearSelectedWord: () -> Unit,
) {
    VocabScreen {
        VocabPageHeader(
            title = "词书",
            subtitle = "选择学习范围，快速定位词条。",
        )
        VocabCard(elevated = false) {
            SectionTitle("词书")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BookCode.entries.forEach { book ->
                    FilterChip(
                        selected = book in uiState.settings.selectedBooks,
                        onClick = { onToggleBook(book) },
                        label = { Text(book.displayName) },
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
            Text(
                "至少保留一本词书；再次点选最后一本时会保持当前选择。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VocabCard(elevated = false) {
            SectionTitle("筛选")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WordStatusFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = uiState.statusFilter == filter,
                        onClick = { onStatusFilterChange(filter) },
                        label = { Text(filter.displayName) },
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
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = VocabControlShape,
                label = { Text("搜索单词或释义") },
            )
        }
        uiState.selectedWordDetail?.let { WordDetailCard(it, onClearSelectedWord) }
        uiState.progress.forEach { progress ->
            BookProgressCard(progress)
        }
        VocabCard(elevated = false) {
            val shownWords = uiState.words.take(30)
            SectionTitle("词条", detail = "${shownWords.size}/${uiState.words.size} 个结果")
            if (shownWords.isEmpty()) {
                VocabEmptyState(
                    title = "没有匹配词条",
                    body = "换个关键词，或切换词书与学习状态筛选。",
                )
            } else {
                shownWords.forEach { word ->
                    WordRow(word = word, onClick = { onSelectWord(word.id) })
                }
            }
        }
    }
}

@Composable
private fun BookProgressCard(progress: BookProgress) {
    val fraction =
        if (progress.totalCount == 0) {
            0f
        } else {
            progress.learnedCount.toFloat() / progress.totalCount.toFloat()
        }
    VocabCard(elevated = false) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(progress.bookCode.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    "${progress.learnedCount}/${progress.totalCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VocabPill("待复习 ${progress.dueCount}")
        }
        VocabProgressBar(progress = fraction)
        Text(
            "掌握 ${progress.masteredCount}  学习进度 ${(fraction * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WordRow(
    word: WordEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 52.dp)
                .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(word.word, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                word.meaning,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        word.cefrLevel?.let {
            VocabPill(
                text = it,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WordDetailCard(
    detail: WordDetail,
    onClose: () -> Unit,
) {
    WarmHeroCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(detail.entry.word, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                detail.entry.phonetic?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                detail.entry.cefrLevel?.let { VocabPill(it) }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭词条详情")
                }
            }
        }
        Text(detail.entry.meaning, style = MaterialTheme.typography.titleMedium)
        detail.entry.partOfSpeech?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        detail.entry.definition?.let {
            Text(
                it,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val books = detail.memberships.joinToString(" / ") { it.bookCode.displayName }
        if (books.isNotBlank()) {
            Text(
                "词书 $books",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (detail.aliases.isNotEmpty()) {
            Text(
                detail.aliases.take(6).joinToString(" / "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
