package com.zzz.androidvocab.core.stats

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.database.StatsDao
import com.zzz.androidvocab.core.database.toModel
import com.zzz.androidvocab.core.domain.SettingsRepository
import com.zzz.androidvocab.core.domain.StatsRepository
import com.zzz.androidvocab.core.domain.calculateBookStats
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookStats
import com.zzz.androidvocab.core.model.DailyActivity
import com.zzz.androidvocab.core.model.DailyReviewLoad
import com.zzz.androidvocab.core.model.DifficultWord
import com.zzz.androidvocab.core.model.RetentionStats
import com.zzz.androidvocab.core.model.StreakStats
import com.zzz.androidvocab.core.model.TodayStats
import com.zzz.androidvocab.core.scheduler.ReviewScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

class OfflineStatsRepository
    @Inject
    constructor(
        private val statsDao: StatsDao,
        private val clockProvider: ClockProvider,
        private val scheduler: ReviewScheduler,
        private val settingsRepository: SettingsRepository,
    ) : StatsRepository {
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeTodayStats(
            localDay: LocalDate,
            now: Instant,
        ): Flow<TodayStats> {
            val baseFlow =
                combine(
                    statsDao.observeDailyRatingCounts(localDay.toString()),
                    statsDao.observeDailyCompleted(localDay.toString()),
                    statsDao.observeDailyNewCount(localDay.toString()),
                    statsDao.observeDailyDurationMs(localDay.toString()),
                ) { ratings, completed, newCount, durationMs ->
                    val counts = ratings.associate { it.rating to it.count }
                    TodayStatsBase(
                        againCount = counts["again"] ?: 0,
                        hardCount = counts["hard"] ?: 0,
                        goodCount = counts["good"] ?: 0,
                        easyCount = counts["easy"] ?: 0,
                        completedCount = completed.coerceAtLeast(0),
                        newCount = newCount,
                        durationMs = durationMs,
                    )
                }
            val dueCountAndSettingsFlow =
                settingsRepository.settings.flatMapLatest { settings ->
                    val bookCodes =
                        settings.selectedBooks.ifEmpty { BookCode.entries.toSet() }.map { it.name }
                    statsDao.observeDueCount(now, bookCodes).map { dueCount ->
                        dueCount to settings
                    }
                }
            return combine(baseFlow, dueCountAndSettingsFlow) { base, (dueCount, settings) ->
                val remainingNew = (settings.dailyNewLimit - base.newCount).coerceAtLeast(0)
                val remainingCount = dueCount + remainingNew
                val avgMs =
                    if (base.completedCount > 0) {
                        base.durationMs / base.completedCount
                    } else {
                        DEFAULT_REVIEW_DURATION_MS
                    }
                val estimatedMinutes =
                    ((remainingCount.toLong() * avgMs + 59999L) / 60000L).toInt()
                val recallAccuracy =
                    if (base.completedCount == 0) {
                        0.0
                    } else {
                        (base.goodCount + base.easyCount).toDouble() / base.completedCount
                    }
                val passRate =
                    if (base.completedCount == 0) {
                        0.0
                    } else {
                        (base.hardCount + base.goodCount + base.easyCount).toDouble() /
                            base.completedCount
                    }
                TodayStats(
                    localDay = localDay.toString(),
                    newCount = base.newCount,
                    reviewCount = (base.completedCount - base.newCount).coerceAtLeast(0),
                    againCount = base.againCount,
                    hardCount = base.hardCount,
                    goodCount = base.goodCount,
                    easyCount = base.easyCount,
                    completedCount = base.completedCount,
                    remainingCount = remainingCount,
                    recallAccuracy = recallAccuracy,
                    passRate = passRate,
                    estimatedMinutes = estimatedMinutes,
                )
            }
        }

        override fun observeAverageReviewDurationMs(
            days: Int,
            today: LocalDate,
        ): Flow<Long?> {
            val safeDays = days.coerceAtLeast(1)
            val startDay = today.minusDays((safeDays - 1).toLong())
            return statsDao
                .observeAverageDurationMs(startDay.toString(), today.toString())
                .map { average -> average?.roundToLong()?.takeIf { it > 0L } }
        }

        override fun observeBookStats(now: Instant): Flow<List<BookStats>> =
            combine(
                statsDao.observeBookTotals(),
                statsDao.observeValidReviewCards(),
                settingsRepository.settings,
            ) { totals, cards, settings ->
                val totalCounts =
                    totals
                        .mapNotNull { total ->
                            total.bookCode.toBookCodeOrNull()?.let { book -> book to total.count }
                        }.toMap()
                calculateBookStats(
                    totals = totalCounts,
                    cards = cards.map { it.toModel() },
                    now = now,
                    retrievability = { card -> scheduler.retrievability(card, now, settings.targetRetention) },
                )
            }

        override fun observeReviewLoad(
            days: Int,
            now: Instant,
        ): Flow<List<DailyReviewLoad>> {
            val zoneId = clockProvider.zoneId()
            val startDay = LocalDate.ofInstant(now, zoneId)
            val end = startDay.plusDays(days.toLong()).atStartOfDay(zoneId).toInstant()
            return statsDao.observeDueCards(end).map { rows ->
                val grouped =
                    rows
                        .mapNotNull { row ->
                            row.dueAt?.let { dueAt ->
                                val day = LocalDate.ofInstant(dueAt, zoneId)
                                if (day.isBefore(startDay)) startDay.toString() else day.toString()
                            }
                        }.groupingBy { it }
                        .eachCount()
                (0 until days).map { offset ->
                    val day = startDay.plusDays(offset.toLong()).toString()
                    DailyReviewLoad(day, grouped[day] ?: 0)
                }
            }
        }

        override fun observeDailyActivity(
            days: Int,
            today: LocalDate,
        ): Flow<List<DailyActivity>> {
            val startDay = today.minusDays((days - 1).coerceAtLeast(0).toLong())
            return statsDao.observeDailyActivityRows(startDay.toString(), today.toString()).map { rows ->
                val grouped = rows.associate { it.localDay to it.reviewCount }
                (0 until days).map { offset ->
                    val day = startDay.plusDays(offset.toLong()).toString()
                    DailyActivity(day, grouped[day] ?: 0)
                }
            }
        }

        override fun observeRetentionStats(
            days: Int,
            now: Instant,
        ): Flow<RetentionStats> {
            val from = now.minusSeconds(days.toLong() * 24L * 60L * 60L)
            return combine(
                statsDao.observeReviewedCards(from),
                settingsRepository.settings,
            ) { cards, settings ->
                val values =
                    cards.mapNotNull { entity ->
                        val card = entity.toModel()
                        scheduler.retrievability(card, now, settings.targetRetention) ?: card.retrievability
                    }
                val sorted = values.sorted()
                RetentionStats(
                    averageRetrievability = sorted.averageOrZero(),
                    p25Retrievability = sorted.percentile(0.25),
                    p50Retrievability = sorted.percentile(0.50),
                )
            }
        }

        override fun observeStreakStats(today: LocalDate): Flow<StreakStats> =
            statsDao.observeActiveDays().map { days ->
                val active = days.toSet()
                StreakStats(
                    currentStreak = currentStreak(active, today),
                    maxStreak = maxStreak(active),
                    activeDays = active,
                )
            }

        override fun observeDifficultWords(
            days: Int,
            now: Instant,
        ): Flow<List<DifficultWord>> {
            val from = now.minusSeconds(days.toLong() * 24L * 60L * 60L)
            return statsDao.observeDifficultWordRows(from).map { rows ->
                val words = statsDao.getWordsByIds(rows.map { it.wordId }).associateBy { it.id }
                rows.mapNotNull { row ->
                    words[row.wordId]?.toModel()?.let { word ->
                        DifficultWord(
                            word = word,
                            againCount = row.againCount,
                            hardCount = row.hardCount,
                            difficultyScore = row.againCount * 3.0 + row.hardCount * 1.5,
                        )
                    }
                }
            }
        }

        override suspend fun rebuildDailyStatsCache(updatedAt: Instant): Int =
            statsDao.rebuildDailyStatsCache(updatedAt)
    }

private const val DEFAULT_REVIEW_DURATION_MS = 30_000L

private data class TodayStatsBase(
    val againCount: Int,
    val hardCount: Int,
    val goodCount: Int,
    val easyCount: Int,
    val completedCount: Int,
    val newCount: Int,
    val durationMs: Long,
)

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

private fun String.toBookCodeOrNull(): BookCode? = BookCode.entries.firstOrNull { it.name == this }

private fun List<Double>.percentile(p: Double): Double {
    if (isEmpty()) return 0.0
    val index = ((size - 1) * p).toInt().coerceIn(0, size - 1)
    return this[index]
}

private fun currentStreak(
    active: Set<String>,
    today: LocalDate,
): Int {
    var day = today
    if (!active.contains(day.toString())) {
        day = day.minusDays(1)
    }
    var count = 0
    while (active.contains(day.toString())) {
        count += 1
        day = day.minusDays(1)
    }
    return count
}

private fun maxStreak(active: Set<String>): Int {
    val days = active.map(LocalDate::parse).sorted()
    var best = 0
    var current = 0
    var previous: LocalDate? = null
    days.forEach { day ->
        current = if (previous?.plusDays(1) == day) current + 1 else 1
        best = maxOf(best, current)
        previous = day
    }
    return best
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StatsModule {
    @Binds
    @Singleton
    abstract fun bindStatsRepository(repository: OfflineStatsRepository): StatsRepository
}
