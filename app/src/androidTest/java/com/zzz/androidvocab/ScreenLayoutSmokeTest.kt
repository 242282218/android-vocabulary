package com.zzz.androidvocab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.zzz.androidvocab.core.designsystem.VocabTheme
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.DailyActivity
import com.zzz.androidvocab.core.model.DailyReviewLoad
import com.zzz.androidvocab.core.model.DifficultWord
import com.zzz.androidvocab.core.model.RetentionStats
import com.zzz.androidvocab.core.model.ReviewCard
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
import com.zzz.androidvocab.feature.settings.SettingsScreen
import com.zzz.androidvocab.feature.settings.SettingsUiState
import com.zzz.androidvocab.feature.stats.StatsScreen
import com.zzz.androidvocab.feature.stats.StatsUiState
import com.zzz.androidvocab.feature.today.TodayScreen
import com.zzz.androidvocab.feature.today.TodayUiState
import com.zzz.androidvocab.feature.wordbook.WordbookScreen
import com.zzz.androidvocab.feature.wordbook.WordbookUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant

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

        composeRule.onNodeWithText("今日").assertExists()
        composeRule.onNodeWithText("开始学习").assertExists()
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

        composeRule.onNodeWithText("复习").assertExists()
        composeRule.onNodeWithText("compartmentalization").assertExists()
        composeRule.onNodeWithText("忘了").assertExists()
        composeRule.onNodeWithText("熟悉").assertExists()
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

        composeRule.onNodeWithText("词书").assertExists()
        composeRule.onNodeWithText("搜索单词或释义").assertExists()
        composeRule.onNodeWithText("ability").assertExists()
    }

    @Test
    fun statsScreenRendersInViewport() {
        renderInViewport {
            StatsScreen(fakeStatsUiState())
        }

        composeRule.onNodeWithText("统计").assertExists()
        composeRule.onNodeWithText("连续学习").assertExists()
    }

    @Test
    fun settingsScreenRendersInViewport() {
        renderInViewport {
            SettingsScreen(
                uiState = fakeSettingsUiState(),
                hasNotificationPermission = false,
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

        composeRule.onNodeWithText("设置").assertExists()
        composeRule.onNodeWithText("导出 JSON").assertExists()
        composeRule.onNodeWithText("Open English Word Lists").assertExists()
        composeRule.onNodeWithText("系统通知权限未开启，每日提醒不会显示。").assertExists()
        composeRule.onNodeWithText("打开通知设置").assertExists()
    }

    private fun renderInViewport(content: @Composable () -> Unit) {
        composeRule.setContent {
            VocabTheme(themeMode = ThemeMode.Light) {
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
        composeRule.waitForIdle()
        composeRule.onRoot().captureToImage()
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

private fun fakeReviewUiState(): ReviewUiState =
    ReviewUiState(
        item = fakeReviewQueueItem(),
        remainingCount = 4,
        isBackVisible = true,
        isSubmitting = false,
    )

private fun fakeWordbookUiState(): WordbookUiState {
    val word = fakeWordEntry("abandon")
    return WordbookUiState(
        settings = AppSettings(selectedBooks = setOf(BookCode.CET4, BookCode.CET6)),
        progress =
            listOf(
                BookProgress(BookCode.CET4, totalCount = 3846, learnedCount = 120, masteredCount = 40, dueCount = 12),
                BookProgress(BookCode.CET6, totalCount = 5406, learnedCount = 64, masteredCount = 18, dueCount = 7),
            ),
        query = "aban",
        statusFilter = WordStatusFilter.All,
        words = listOf(word, fakeWordEntry("ability")),
        selectedWordDetail =
            WordDetail(
                entry = word,
                memberships =
                    listOf(
                        WordBookMembership(
                            wordId = word.id,
                            bookCode = BookCode.CET4,
                            orderIndex = 1,
                            examFrequencyScore = 0.6,
                            examPriorityScore = 0.7,
                            isPhraseBacked = true,
                            phraseCount = 2,
                        ),
                    ),
                aliases = listOf(WordAlias(word.id, "abandoned").value),
            ),
    )
}

private fun fakeStatsUiState(): StatsUiState =
    StatsUiState(
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

private fun fakeSettingsUiState(): SettingsUiState =
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
    )

private fun fakeReviewLoad(days: Int): List<DailyReviewLoad> =
    (0 until days).map { index ->
        DailyReviewLoad(
            localDay = "2026-05-${(index % 28 + 1).toString().padStart(2, '0')}",
            dueCount = (index * 3) % 11,
        )
    }

private fun fakeReviewQueueItem(): ReviewQueueItem =
    ReviewQueueItem(
        card =
            ReviewCard(
                id = "card-compartmentalization",
                wordId = "word-compartmentalization",
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
        word = fakeWordEntry("compartmentalization"),
        isNew = false,
    )

private fun fakeWordEntry(word: String): WordEntry =
    WordEntry(
        id = "word-$word",
        word = word,
        meaning = "放弃；离弃；停止支持某事",
        phonetic = "/$word/",
        partOfSpeech = "v.",
        definition =
            "A deliberately long English definition used to verify narrow screens keep text inside the card.",
        cefrLevel = "B2",
        cefrRank = 3200.0,
        frequency = 0.42,
        sourceFlags = listOf("publish-safe"),
        coverageTier = "core",
    )
