package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.BookStats
import com.zzz.androidvocab.core.model.DailyActivity
import com.zzz.androidvocab.core.model.DailyReviewLoad
import com.zzz.androidvocab.core.model.DifficultWord
import com.zzz.androidvocab.core.model.ExportResult
import com.zzz.androidvocab.core.model.ImportResult
import com.zzz.androidvocab.core.model.RetentionStats
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
import com.zzz.androidvocab.core.model.ReviewDataRepairResult
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewResult
import com.zzz.androidvocab.core.model.SourceInfo
import com.zzz.androidvocab.core.model.StreakStats
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import com.zzz.androidvocab.core.model.TodayQueue
import com.zzz.androidvocab.core.model.TodayStats
import com.zzz.androidvocab.core.model.WordDetail
import com.zzz.androidvocab.core.model.WordEntry
import com.zzz.androidvocab.core.model.WordStatusFilter
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface VocabularyRepository {
    fun observeBookProgress(now: Instant): Flow<List<BookProgress>>

    fun searchWords(
        query: String,
        bookCodes: Set<BookCode>,
        statusFilter: WordStatusFilter,
        now: Instant,
    ): Flow<List<WordEntry>>

    fun observeWordDetail(wordId: String): Flow<WordDetail?>

    fun observeSourceInfo(): Flow<List<SourceInfo>>

    suspend fun importPublishSafeVocabulary(): ImportResult
}

interface ReviewRepository {
    fun observeTodayQueue(
        now: Instant,
        selectedBooks: Set<BookCode>,
        dailyNewLimit: Int,
    ): Flow<TodayQueue>

    suspend fun submitFeedback(command: SubmitFeedbackCommand): ReviewResult

    suspend fun replayLogs(cardId: String): ReviewCard

    suspend fun getQueueItem(cardId: String): ReviewQueueItem

    suspend fun inspectReviewDataIntegrity(): ReviewDataIntegrityReport

    suspend fun repairReviewDataCache(): ReviewDataRepairResult
}

interface StatsRepository {
    fun observeTodayStats(
        localDay: LocalDate,
        now: Instant,
    ): Flow<TodayStats>

    fun observeAverageReviewDurationMs(
        days: Int,
        today: LocalDate,
    ): Flow<Long?>

    fun observeBookStats(now: Instant): Flow<List<BookStats>>

    fun observeReviewLoad(
        days: Int,
        now: Instant,
    ): Flow<List<DailyReviewLoad>>

    fun observeDailyActivity(
        days: Int,
        today: LocalDate,
    ): Flow<List<DailyActivity>>

    fun observeRetentionStats(
        days: Int,
        now: Instant,
    ): Flow<RetentionStats>

    fun observeStreakStats(today: LocalDate): Flow<StreakStats>

    fun observeDifficultWords(
        days: Int,
        now: Instant,
    ): Flow<List<DifficultWord>>

    suspend fun rebuildDailyStatsCache(updatedAt: Instant): Int
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun updateDailyNewLimit(value: Int)

    suspend fun updateSelectedBooks(bookCodes: Set<BookCode>)

    suspend fun toggleBook(bookCode: BookCode)

    suspend fun updateTargetRetention(value: Double)

    suspend fun updateReminder(
        enabled: Boolean,
        hour: Int,
        minute: Int,
    )

    suspend fun updateReminderEnabled(enabled: Boolean)

    suspend fun updateReminderTime(
        hour: Int,
        minute: Int,
    )

    suspend fun updateThemeMode(themeMode: com.zzz.androidvocab.core.model.ThemeMode)
}

interface ExportRepository {
    suspend fun exportUserData(): ExportResult
}
