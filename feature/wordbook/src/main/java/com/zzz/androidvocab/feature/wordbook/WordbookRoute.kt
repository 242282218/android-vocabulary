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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zzz.androidvocab.core.designsystem.SecondaryAction
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
        if (uiState.isLoading) {
            WordbookLoadingCard()
            return@VocabScreen
        }
        BookSelectorCard(
            selectedBooks = uiState.settings.selectedBooks,
            onToggleBook = onToggleBook,
        )
        StatusFilterCard(
            statusFilter = uiState.statusFilter,
            query = uiState.query,
            onStatusFilterChange = onStatusFilterChange,
            onQueryChange = onQueryChange,
        )
        uiState.selectedWordDetail?.let {
            WordDetailCard(
                detail = it,
                selectedBooks = uiState.settings.selectedBooks,
                onClose = onClearSelectedWord,
            )
        }
        if (uiState.progress.isNotEmpty()) {
            BookProgressListCard(uiState.progress)
        }
        WordsCard(
            words = uiState.words,
            query = uiState.query,
            statusFilter = uiState.statusFilter,
            onQueryChange = onQueryChange,
            onStatusFilterChange = onStatusFilterChange,
            onSelectWord = onSelectWord,
        )
    }
}

@Composable
private fun WordbookLoadingCard() {
    VocabCard {
        VocabEmptyState(
            title = "正在加载词书",
            body = "正在读取本地词库、学习范围和词条状态。",
        )
        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookSelectorCard(
    selectedBooks: Set<BookCode>,
    onToggleBook: (BookCode) -> Unit,
) {
    VocabCard(elevated = false) {
        SectionTitle("词书")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BookCode.entries.forEach { book ->
                FilterChip(
                    selected = book in selectedBooks,
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusFilterCard(
    statusFilter: WordStatusFilter,
    query: String,
    onStatusFilterChange: (WordStatusFilter) -> Unit,
    onQueryChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    VocabCard(elevated = false) {
        SectionTitle("筛选")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WordStatusFilter.entries.forEach { filter ->
                FilterChip(
                    selected = statusFilter == filter,
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
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = VocabControlShape,
            label = { Text("搜索单词或释义") },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null)
            },
            trailingIcon =
                if (query.isNotBlank()) {
                    {
                        IconButton(
                            onClick = {
                                onQueryChange("")
                                focusManager.clearFocus()
                            },
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "清空搜索")
                        }
                    }
                } else {
                    null
                },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                    },
                ),
        )
    }
}

@Composable
private fun WordsCard(
    words: List<WordEntry>,
    query: String,
    statusFilter: WordStatusFilter,
    onQueryChange: (String) -> Unit,
    onStatusFilterChange: (WordStatusFilter) -> Unit,
    onSelectWord: (String) -> Unit,
) {
    VocabCard(elevated = false) {
        val shownWords = words.take(WORD_RESULT_PREVIEW_LIMIT)
        val hasActiveFilter = query.isNotBlank() || statusFilter != WordStatusFilter.All
        SectionTitle("词条", detail = "${shownWords.size}/${words.size} 个结果")
        resultLimitHint(visibleCount = shownWords.size, totalCount = words.size)?.let { hint ->
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (shownWords.isEmpty()) {
            VocabEmptyState(
                title = if (hasActiveFilter) "没有匹配词条" else "暂无词条",
                body =
                    if (hasActiveFilter) {
                        "当前搜索或筛选没有结果，可以清空条件后重新浏览。"
                    } else {
                        "词库导入完成后，这里会显示可学习词条。"
                    },
                action =
                    if (hasActiveFilter) {
                        {
                            SecondaryAction(
                                text = "清空筛选",
                                onClick = {
                                    onQueryChange("")
                                    onStatusFilterChange(WordStatusFilter.All)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        null
                    },
            )
        } else {
            shownWords.forEach { word ->
                key(word.id) {
                    WordRow(word = word, onClick = { onSelectWord(word.id) })
                }
            }
        }
    }
}

private fun resultLimitHint(
    visibleCount: Int,
    totalCount: Int,
): String? =
    if (totalCount > visibleCount) {
        "仅显示前 $visibleCount 条，共 $totalCount 条；继续输入可缩小范围。"
    } else {
        null
    }

private const val WORD_RESULT_PREVIEW_LIMIT = 30

@Composable
private fun BookProgressListCard(progressItems: List<BookProgress>) {
    VocabCard(elevated = false) {
        SectionTitle("词书进度", detail = "${progressItems.size} 本")
        progressItems.forEach { progress ->
            BookProgressRow(progress)
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    progress.bookCode.displayName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${progress.learnedCount}/${progress.totalCount} 已学习",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VocabPill("待复习 ${progress.dueCount}")
        }
        VocabProgressBar(progress = fraction)
        Text(
            "掌握 ${progress.masteredCount} · 进度 ${(fraction * 100).toInt()}%",
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
    selectedBooks: Set<BookCode>,
    onClose: () -> Unit,
) {
    WarmHeroCard {
        WordDetailHeader(detail = detail, onClose = onClose)
        Text(
            detail.entry.meaning,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
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
        wordbookScopeText(detail, selectedBooks)?.let { scopeText ->
            Text(
                scopeText,
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

private fun wordbookScopeText(
    detail: WordDetail,
    selectedBooks: Set<BookCode>,
): String? {
    val allBooks = detail.memberships.map { it.bookCode }.distinct()
    if (allBooks.isEmpty()) return null
    val effectiveSelectedBooks = selectedBooks.ifEmpty { BookCode.entries.toSet() }
    val inScope = allBooks.filter { it in effectiveSelectedBooks }
    val outOfScope = allBooks.filterNot { it in effectiveSelectedBooks }
    return listOfNotNull(
        inScope.takeIf { it.isNotEmpty() }?.joinToStringText(prefix = "当前范围"),
        outOfScope.takeIf { it.isNotEmpty() }?.joinToStringText(prefix = "另收录"),
    ).joinToString(" · ")
}

private fun List<BookCode>.joinToStringText(prefix: String): String =
    joinToString(
        separator = " / ",
        prefix = "$prefix ",
    ) { it.displayName }

@Composable
private fun WordDetailHeader(
    detail: WordDetail,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                detail.entry.word,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            detail.entry.phonetic?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            detail.entry.cefrLevel?.let { VocabPill(it) }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭词条详情")
            }
        }
    }
}
