package com.zzz.androidvocab.core.model

data class TodayStats(
    val localDay: String,
    val newCount: Int,
    val reviewCount: Int,
    val againCount: Int,
    val hardCount: Int,
    val goodCount: Int,
    val easyCount: Int,
    val completedCount: Int,
    val remainingCount: Int,
    val recallAccuracy: Double,
    val passRate: Double,
    val estimatedMinutes: Int,
)

data class TodayOverview(
    val queue: TodayQueue,
    val stats: TodayStats,
    val selectedBooks: List<BookCode>,
)

data class BookProgress(
    val bookCode: BookCode,
    val totalCount: Int,
    val learnedCount: Int,
    val masteredCount: Int,
    val dueCount: Int,
)

data class BookStats(
    val progress: BookProgress,
    val unlearnedCount: Int,
    val learningCount: Int,
    val familiarCount: Int,
)

data class DailyReviewLoad(
    val localDay: String,
    val dueCount: Int,
)

data class DailyActivity(
    val localDay: String,
    val reviewCount: Int,
)

data class RetentionStats(
    val averageRetrievability: Double,
    val p25Retrievability: Double,
    val p50Retrievability: Double,
)

data class StreakStats(
    val currentStreak: Int,
    val maxStreak: Int,
    val activeDays: Set<String>,
)

data class DifficultWord(
    val word: WordEntry,
    val againCount: Int,
    val hardCount: Int,
    val difficultyScore: Double,
)
