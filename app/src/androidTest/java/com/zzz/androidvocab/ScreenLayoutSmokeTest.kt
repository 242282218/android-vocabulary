package com.zzz.androidvocab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.zzz.androidvocab.core.designsystem.SectionTitle
import com.zzz.androidvocab.core.designsystem.VocabCard
import com.zzz.androidvocab.core.designsystem.VocabTheme
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.DailyActivity
import com.zzz.androidvocab.core.model.DailyReviewLoad
import com.zzz.androidvocab.core.model.DifficultWord
import com.zzz.androidvocab.core.model.RetentionStats
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewState
import com.zzz.androidvocab.core.model.SourceInfo
import com.zzz.androidvocab.core.model.StreakStats
import com.zzz.androidvocab.core.model.ThemeMode
import com.zzz.androidvocab.core.model.TodayOverview
import com.zzz.androidvocab.core.model.TodayQueue
import com.zzz.androidvocab.core.model.TodayStats
import com.zzz.androidvocab.core.model.WordAlias
import com.zzz.androidvocab.core.model.WordBookMembership
import com.zzz.androidvocab.core.model.WordDetail
import com.zzz.androidvocab.core.model.WordEntry
import com.zzz.androidvocab.core.model.WordStatusFilter
import com.zzz.androidvocab.feature.review.ReviewScreen
import com.zzz.androidvocab.feature.review.ReviewUiState
import com.zzz.androidvocab.feature.settings.DataMaintenanceUiState
import com.zzz.androidvocab.feature.settings.ReminderNotificationState
import com.zzz.androidvocab.feature.settings.SettingsScreen
import com.zzz.androidvocab.feature.settings.SettingsUiState
import com.zzz.androidvocab.feature.stats.StatsScreen
import com.zzz.androidvocab.feature.stats.StatsUiState
import com.zzz.androidvocab.feature.today.TodayScreen
import com.zzz.androidvocab.feature.today.TodayUiState
import com.zzz.androidvocab.feature.wordbook.WordbookScreen
import com.zzz.androidvocab.feature.wordbook.WordbookUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant
import kotlin.math.min

@RunWith(Parameterized::class)
class ScreenLayoutSmokeTest(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun todayScreenRendersInViewport() {
        renderInViewport {
            TodayScreen(
                uiState = fakeTodayUiState(),
                onStartReview = {},
                onRetryImport = {},
            )
        }

        assertTextExists("今日")
        assertTextExists("开始学习")
    }

    @Test
    fun todayScreenShowsImportErrorWithoutBlockingUsableQueue() {
        renderInViewport {
            TodayScreen(
                uiState = fakeTodayUiState().copy(errorMessage = "assets mismatch"),
                onStartReview = {},
                onRetryImport = {},
            )
        }

        assertTextDisplayed("开始学习")
        assertTextDisplayed("词库导入失败")
        assertTextDisplayed("重试导入")
    }

    @Test
    fun todayScreenShowsImportProgressWithoutBlockingUsableQueue() {
        renderInViewport {
            TodayScreen(
                uiState = fakeTodayUiState().copy(isImporting = true),
                onStartReview = {},
                onRetryImport = {},
            )
        }

        assertTextDisplayed("开始学习")
        assertTextDisplayed("正在准备词库")
    }

    @Test
    fun reviewScreenRendersInViewport() {
        renderInViewport {
            ReviewScreen(
                uiState = fakeReviewUiState(),
                onShowBack = {},
                onSpeak = {},
                onSubmit = {},
            )
        }

        assertTextExists("复习")
        assertTextExists("compartmentalization")
        assertTextExists("忘了")
        assertTextExists("熟悉")
    }

    @Test
    fun reviewScreenKeepsFeedbackVisibleWithLongDictionaryText() {
        renderInViewport {
            ReviewScreen(
                uiState = fakeReviewUiState(item = fakeLongReviewQueueItem()),
                onShowBack = {},
                onSpeak = {},
                onSubmit = {},
            )
        }

        assertTextDisplayed("忘了")
        assertTextDisplayed("熟悉")
    }

    @Test
    fun wordbookScreenRendersInViewport() {
        renderInViewport {
            WordbookScreen(
                uiState = fakeWordbookUiState(),
                onQueryChange = {},
                onStatusFilterChange = {},
                onToggleBook = {},
                onSelectWord = {},
                onClearSelectedWord = {},
            )
        }

        assertTextExists("至少保留一本词书；再次点选最后一本时会保持当前选择。")
        assertTextExists("搜索单词或释义")
        assertTextExists("ability")
    }

    @Test
    fun wordbookScreenKeepsLongSelectedDetailControllable() {
        renderInViewport {
            WordbookScreen(
                uiState = fakeWordbookUiState(selectedWordDetail = fakeLongWordDetail()),
                onQueryChange = {},
                onStatusFilterChange = {},
                onToggleBook = {},
                onSelectWord = {},
                onClearSelectedWord = {},
            )
        }

        assertTextDisplayed("搜索单词或释义")
        composeRule.onNodeWithContentDescription("关闭词条详情").assertIsDisplayed()
    }

    @Test
    fun wordbookScreenShowsSelectedAndExtraBooksInDetail() {
        val word = fakeWordEntry("shared")
        renderInViewport {
            WordbookScreen(
                uiState =
                    fakeWordbookUiState(
                        settings = AppSettings(selectedBooks = setOf(BookCode.CET4)),
                        selectedWordDetail =
                            fakeWordDetail(
                                word = word,
                                memberships =
                                    listOf(
                                        fakeWordMembership(word.id, BookCode.CET4),
                                        fakeWordMembership(word.id, BookCode.CET6),
                                    ),
                            ),
                    ),
                onQueryChange = {},
                onStatusFilterChange = {},
                onToggleBook = {},
                onSelectWord = {},
                onClearSelectedWord = {},
            )
        }

        assertTextExists("当前范围 CET4 · 另收录 CET6")
    }

    @Test
    fun wordbookScreenAllowsTappingOnlySelectedBookChip() {
        var toggledBook: BookCode? = null
        renderInViewport {
            WordbookScreen(
                uiState =
                    fakeWordbookUiState(
                        settings = AppSettings(selectedBooks = setOf(BookCode.CET4)),
                        showSelectedWordDetail = false,
                    ),
                onQueryChange = {},
                onStatusFilterChange = {},
                onToggleBook = { toggledBook = it },
                onSelectWord = {},
                onClearSelectedWord = {},
            )
        }

        clickNodeWithText(BookCode.CET4.displayName)
        assertEquals(BookCode.CET4, toggledBook)
    }

    @Test
    fun wordbookScreenKeepsLongWordRowSeparateFromCefr() {
        val longWord =
            fakeWordEntry(
                word = "counterrevolutionaries",
                meaning = "一个很长的中文释义，用来验证搜索结果行在窄屏下仍然和等级标签保持间距。",
            )
        renderInViewport {
            WordbookScreen(
                uiState =
                    fakeWordbookUiState(
                        showSelectedWordDetail = false,
                        words = listOf(longWord),
                    ),
                onQueryChange = {},
                onStatusFilterChange = {},
                onToggleBook = {},
                onSelectWord = {},
                onClearSelectedWord = {},
            )
        }

        val wordNode = findTextNode(longWord.word)
        val cefrNode = findTextNode("B2")
        assertTextExists(longWord.word)
        assertTextExists("B2")
        val wordBounds = wordNode.getUnclippedBoundsInRoot()
        val cefrBounds = cefrNode.getUnclippedBoundsInRoot()
        assertTrue(
            "Word row text and CEFR label should keep spacing at ${widthDp}x$heightDp",
            wordBounds.right + 8.dp <= cefrBounds.left,
        )
    }

    @Test
    fun wordbookScreenExplainsPreviewLimit() {
        renderInViewport {
            WordbookScreen(
                uiState =
                    fakeWordbookUiState(
                        showSelectedWordDetail = false,
                        words = fakeWordList(35),
                    ),
                onQueryChange = {},
                onStatusFilterChange = {},
                onToggleBook = {},
                onSelectWord = {},
                onClearSelectedWord = {},
            )
        }

        assertTextExists("仅显示前 30 条，共 35 条；继续输入可缩小范围。")
    }

    @Test
    fun statsScreenRendersInViewport() {
        renderInViewport {
            StatsScreen(fakeStatsUiState())
        }

        assertTextExists("统计")
        assertTextExists("连续学习")
        assertTextExists("复习趋势")
        assertTextExists("7 天 · 全部词书")
        assertTextExists("未来负载")
        assertTextExists("30 天 · 全部词书")
    }

    @Test
    fun statsScreenKeepsCompactMetricsVisibleOnNarrowViewport() {
        renderInViewport {
            StatsScreen(
                fakeStatsUiState().copy(
                    selectedBooks =
                        listOf(
                            BookCode.CET4,
                            BookCode.CET6,
                            BookCode.KAOYAN,
                            BookCode.IELTS,
                            BookCode.TOEFL,
                        ),
                ),
            )
        }

        assertTextExists("30 天 · CET4、CET6等5本")
        assertTextDisplayed("年内最长")
        assertTextDisplayed("年内活跃")
        assertTextDisplayed("30天 P50")
        assertTextExists("已学 620/3846")
        assertTextExists("待复习 30")
        assertTextExists("掌握 240")
    }

    @Test
    fun settingsScreenRendersInViewport() {
        renderInViewport {
            SettingsScreen(
                uiState = fakeSettingsUiState(),
                reminderNotificationState = ReminderNotificationState.MissingRuntimePermission,
                onDailyLimitChange = {},
                onTargetRetentionChange = {},
                onThemeChange = {},
                onReminderChange = {},
                onReminderTimeChange = { _, _ -> },
                onOpenNotificationSettings = {},
                onExport = {},
                onInspectLearningData = {},
                onRepairLearningData = {},
            )
        }

        assertTextExists("设置")
        assertTextExists("导出 JSON")
        assertTextExists("Open English Word Lists")
        assertTextExists("未检查")
        assertTextExists("需授权")
        assertTextExists("系统通知权限未开启，每日提醒不会显示。")
        assertTextExists("打开通知设置")
    }

    @Test
    fun settingsScreenExplainsPausedReminderWhenSystemNotificationsAreDisabled() {
        renderInViewport {
            SettingsScreen(
                uiState = fakeSettingsUiState(),
                reminderNotificationState = ReminderNotificationState.DisabledInSystem,
                onDailyLimitChange = {},
                onTargetRetentionChange = {},
                onThemeChange = {},
                onReminderChange = {},
                onReminderTimeChange = { _, _ -> },
                onOpenNotificationSettings = {},
                onExport = {},
                onInspectLearningData = {},
                onRepairLearningData = {},
            )
        }

        assertTextExists("已暂停")
        assertTextExists("系统通知已关闭，每日提醒已暂停；重新开启后会自动恢复。")
        assertTextExists("打开通知设置")
    }

    @Test
    fun settingsScreenKeepsReminderOffStateCompact() {
        renderInViewport {
            SettingsScreen(
                uiState =
                    fakeSettingsUiState().copy(
                        settings =
                            fakeSettingsUiState().settings.copy(
                                reminderEnabled = false,
                                reminderHour = 7,
                                reminderMinute = 30,
                            ),
                    ),
                reminderNotificationState = ReminderNotificationState.MissingRuntimePermission,
                onDailyLimitChange = {},
                onTargetRetentionChange = {},
                onThemeChange = {},
                onReminderChange = {},
                onReminderTimeChange = { _, _ -> },
                onOpenNotificationSettings = {},
                onExport = {},
                onInspectLearningData = {},
                onRepairLearningData = {},
            )
        }

        assertTextExists("已关闭")
        assertTextExists("开启后按 07:30 提醒")
        composeRule.onAllNodesWithContentDescription("小时 减少").assertCountEquals(0)
        composeRule.onAllNodesWithText("打开通知设置", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun settingsScreenSeparatesPreviousMaintenanceResultFromCurrentFailure() {
        renderInViewport {
            SettingsScreen(
                uiState =
                    fakeSettingsUiState(
                        dataMaintenance =
                            DataMaintenanceUiState(
                                report =
                                    ReviewDataIntegrityReport(
                                        cardsWithLogs = 2,
                                        missingCacheCount = 1,
                                        inconsistentCacheCount = 0,
                                        legacyLogCardCount = 0,
                                    ),
                                message = "已检查 2 张：可修复 1 项，需人工确认 0 项。",
                                errorMessage = "学习数据修复失败：disk full",
                            ),
                    ),
                reminderNotificationState = ReminderNotificationState.MissingRuntimePermission,
                onDailyLimitChange = {},
                onTargetRetentionChange = {},
                onThemeChange = {},
                onReminderChange = {},
                onReminderTimeChange = { _, _ -> },
                onOpenNotificationSettings = {},
                onExport = {},
                onInspectLearningData = {},
                onRepairLearningData = {},
            )
        }

        assertTextExists("上次检查结果")
        assertTextExists("结果已保留")
        assertTextExists("本次操作失败")
        assertTextExists("学习数据修复失败：disk full")
    }

    @Test
    fun sectionTitleKeepsLongDetailSeparateFromTitle() {
        val title = "未来 30 天复习负载"
        val detail = "30 天 · 四级、六级、考研、雅思、托福"
        renderInViewport {
            VocabCard {
                SectionTitle(title = title, detail = detail)
            }
        }

        val titleNode = findTextNode(title)
        val detailNode = findTextNode(detail)
        assertTextExists(title)
        assertTextExists(detail)
        val titleBounds = titleNode.getUnclippedBoundsInRoot()
        val detailBounds = detailNode.getUnclippedBoundsInRoot()
        assertTrue(
            "SectionTitle text bounds should not overlap at ${widthDp}x$heightDp",
            titleBounds.right <= detailBounds.left || detailBounds.top >= titleBounds.bottom,
        )
    }

    private fun renderInViewport(content: @Composable () -> Unit) {
        composeRule.setContent {
            VocabTheme(themeMode = ThemeMode.Light) {
                val actualDensity = LocalDensity.current
                val configuration = LocalConfiguration.current
                val screenWidthPx =
                    with(actualDensity) {
                        configuration.screenWidthDp.dp.toPx()
                    }
                val screenHeightPx =
                    with(actualDensity) {
                        configuration.screenHeightDp.dp.toPx()
                    }
                val simulatedDensity = min(screenWidthPx / widthDp, screenHeightPx / heightDp)
                CompositionLocalProvider(
                    LocalDensity provides Density(simulatedDensity, actualDensity.fontScale),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Box(
                            modifier =
                                Modifier.requiredSize(
                                    width = widthDp.dp,
                                    height = heightDp.dp,
                                ),
                        ) {
                            content()
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage()
        assertTrue("Captured bitmap width must be positive, got ${bitmap.width}", bitmap.width > 0)
        assertTrue("Captured bitmap height must be positive, got ${bitmap.height}", bitmap.height > 0)
    }

    private fun assertTextExists(text: String) {
        composeRule.onAllNodesWithText(text, useUnmergedTree = true)[0].assertExists()
    }

    private fun assertTextDisplayed(text: String) {
        findTextNode(text).assertIsDisplayed()
    }

    private fun findTextNode(text: String) = composeRule.onNodeWithText(text, useUnmergedTree = true)

    private fun clickNodeWithText(text: String) {
        composeRule.onAllNodesWithText(text)[0].performClick()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}x{1}")
        fun viewports(): Collection<Array<Int>> =
            listOf(
                arrayOf(360, 800),
                arrayOf(390, 844),
                arrayOf(412, 915),
                arrayOf(768, 1024),
            )
    }
}

private fun fakeTodayUiState(): TodayUiState {
    val item = fakeReviewQueueItem()
    val queue = TodayQueue(dueItems = listOf(item), newItems = listOf(item.copy(isNew = true)))
    return TodayUiState(
        isLoading = false,
        overview =
            TodayOverview(
                queue = queue,
                stats =
                    TodayStats(
                        localDay = "2026-05-16",
                        newCount = 1,
                        reviewCount = 1,
                        againCount = 0,
                        hardCount = 1,
                        goodCount = 3,
                        easyCount = 1,
                        completedCount = 5,
                        remainingCount = 2,
                        recallAccuracy = 0.8,
                        passRate = 1.0,
                        estimatedMinutes = 3,
                    ),
                selectedBooks = listOf(BookCode.CET4, BookCode.CET6),
            ),
        reviewLoad = fakeReviewLoad(14),
    )
}

private fun fakeReviewUiState(item: ReviewQueueItem = fakeReviewQueueItem()): ReviewUiState =
    ReviewUiState(
        isLoading = false,
        item = item,
        remainingCount = 4,
        isBackVisible = true,
        isSubmitting = false,
    )

private fun fakeWordbookUiState(
    settings: AppSettings = AppSettings(selectedBooks = setOf(BookCode.CET4, BookCode.CET6)),
    selectedWordDetail: WordDetail? = null,
    words: List<WordEntry>? = null,
    showSelectedWordDetail: Boolean = true,
): WordbookUiState {
    val word = fakeWordEntry("abandon")
    return WordbookUiState(
        isLoading = false,
        settings = settings,
        progress =
            listOf(
                BookProgress(BookCode.CET4, totalCount = 3846, learnedCount = 120, masteredCount = 40, dueCount = 12),
                BookProgress(BookCode.CET6, totalCount = 5406, learnedCount = 64, masteredCount = 18, dueCount = 7),
            ),
        query = "aban",
        statusFilter = WordStatusFilter.All,
        words = words ?: listOf(word, fakeWordEntry("ability")),
        selectedWordDetail =
            if (showSelectedWordDetail) {
                selectedWordDetail ?: fakeWordDetail(word)
            } else {
                null
            },
    )
}

private fun fakeWordList(count: Int): List<WordEntry> =
    (1..count).map { index ->
        fakeWordEntry("ability${index.toString().padStart(2, '0')}")
    }

private fun fakeWordDetail(
    word: WordEntry,
    memberships: List<WordBookMembership> = listOf(fakeWordMembership(word.id, BookCode.CET4)),
): WordDetail =
    WordDetail(
        entry = word,
        memberships = memberships,
        aliases = listOf(WordAlias(word.id, "abandoned").value),
    )

private fun fakeWordMembership(
    wordId: String,
    bookCode: BookCode,
): WordBookMembership =
    WordBookMembership(
        wordId = wordId,
        bookCode = bookCode,
        orderIndex = 1,
        examFrequencyScore = 0.6,
        examPriorityScore = 0.7,
        isPhraseBacked = true,
        phraseCount = 2,
    )

private fun fakeLongWordDetail(): WordDetail =
    fakeWordDetail(
        fakeWordEntry(
            word = "counterrevolutionaries",
            meaning =
                "一个非常长的中文释义，用来验证词条详情在小屏幕上不会把搜索入口或关闭按钮挤出可见区域，" +
                    "同时仍然保留足够的上下文给学习者判断当前词条。",
            definition =
                "A deliberately long definition that should stay inside the selected word detail card " +
                    "without expanding the first viewport beyond the search and close controls.",
        ),
    )

private fun fakeStatsUiState(): StatsUiState =
    StatsUiState(
        isLoading = false,
        load = fakeReviewLoad(30),
        activity =
            (0 until 35).map { index ->
                DailyActivity(
                    localDay = "2026-05-${(index % 28 + 1).toString().padStart(2, '0')}",
                    reviewCount =
                        index % 9,
                )
            },
        retention = RetentionStats(0.88, 0.79, 0.9),
        streak = StreakStats(currentStreak = 5, maxStreak = 18, activeDays = setOf("2026-05-16")),
        progress =
            listOf(
                BookProgress(BookCode.CET4, totalCount = 3846, learnedCount = 620, masteredCount = 240, dueCount = 30),
                BookProgress(BookCode.TOEFL, totalCount = 6970, learnedCount = 240, masteredCount = 80, dueCount = 42),
            ),
        difficultWords =
            listOf(
                DifficultWord(fakeWordEntry("fragile"), againCount = 2, hardCount = 3, difficultyScore = 10.5),
            ),
    )

private fun fakeSettingsUiState(dataMaintenance: DataMaintenanceUiState = DataMaintenanceUiState()): SettingsUiState =
    SettingsUiState(
        settings =
            AppSettings(
                dailyNewLimit = 25,
                targetRetention = 0.9,
                reminderEnabled = true,
                reminderHour = 7,
                reminderMinute = 30,
                themeMode = ThemeMode.Light,
            ),
        sources =
            listOf(
                SourceInfo(
                    name = "Open English Word Lists",
                    url = "https://example.test/vocab",
                    license = "CC BY 4.0",
                    licenseStatus = "redistributable",
                    redistributable = true,
                    publishBlocking = false,
                    notes = "publish-safe",
                ),
            ),
        dataMaintenance = dataMaintenance,
    )

private fun fakeReviewLoad(days: Int): List<DailyReviewLoad> =
    (0 until days).map { index ->
        DailyReviewLoad(
            localDay = "2026-05-${(index % 28 + 1).toString().padStart(2, '0')}",
            dueCount = (index * 3) % 11,
        )
    }

private fun fakeReviewQueueItem(word: WordEntry = fakeWordEntry("compartmentalization")): ReviewQueueItem {
    val wordSlug = word.id.removePrefix("word-")
    return ReviewQueueItem(
        card =
            ReviewCard(
                id = "card-$wordSlug",
                wordId = word.id,
                bookCode = BookCode.CET6,
                state = ReviewState.Review,
                difficulty = 5.2,
                stability = 8.4,
                retrievability = 0.82,
                scheduledDays = 8,
                dueAt = Instant.parse("2026-05-16T00:00:00Z"),
                lastReviewAt = Instant.parse("2026-05-12T00:00:00Z"),
                reviewCount = 4,
                lapseCount = 1,
                firstReviewedAt = Instant.parse("2026-05-01T00:00:00Z"),
                createdAt = Instant.parse("2026-05-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-05-12T00:00:00Z"),
            ),
        word = word,
        isNew = false,
    )
}

private fun fakeLongReviewQueueItem(): ReviewQueueItem =
    fakeReviewQueueItem(
        word =
            fakeWordEntry(
                word = "psycholinguistics",
                meaning = "总联机程序和信息控制系统（Total On-line Program and Information Control System）",
                definition =
                    "n. 日落, 同伙, 组合, 集合, 装置\n" +
                        "vt. 放, 安置, 放置, 设定, 使凝结, 点燃, 确定, 点缀, 使就位, 树立, 分配, 调整\n" +
                        "vi. 日落, 凝固, 定型, 搁住, 结果, 适合\n" +
                        "a. 决心的, 规定的, 故意的, 持久的, 固定的, 老套的, 准备好的",
            ),
    )

private fun fakeWordEntry(
    word: String,
    meaning: String = "放弃；离弃；停止支持某事",
    definition: String =
        "A deliberately long English definition used to verify narrow screens keep text inside the card.",
): WordEntry =
    WordEntry(
        id = "word-$word",
        word = word,
        meaning = meaning,
        phonetic = "/$word/",
        partOfSpeech = "v.",
        definition = definition,
        cefrLevel = "B2",
        cefrRank = 3200.0,
        frequency = 0.42,
        sourceFlags = listOf("publish-safe"),
        coverageTier = "core",
    )
