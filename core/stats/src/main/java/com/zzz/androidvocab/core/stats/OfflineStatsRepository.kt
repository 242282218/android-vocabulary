package com.zzz.androidvocab.core.stats

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.database.StatsDao
import com.zzz.androidvocab.core.database.toModel
import com.zzz.androidvocab.core.domain.StatsRepository
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    ) : StatsRepository {
        override fun observeTodayStats(
            localDay: LocalDate,
            now: Instant,
        ): Flow<TodayStats> =
            combine(
                statsDao.observeDailyRatingCounts(localDay.toString()),
                statsDao.observeDailyCompleted(localDay.toString()),
                statsDao.observeDailyNewCount(localDay.toString()),
                statsDao.observeDailyDurationMs(localDay.toString()),
            ) { ratings, completed, newCount, _ ->
                val counts = ratings.associate { it.rating to it.count }
                val again = counts["again"] ?: 0
                val hard = counts["hard"] ?: 0
                val good = counts["good"] ?: 0
                val easy = counts["easy"] ?: 0
                val total = completed.coerceAtLeast(0)
                TodayStats(
                    localDay = localDay.toString(),
                    newCount = newCount,
                    reviewCount = (total - newCount).coerceAtLeast(0),
                    againCount = again,
                    hardCount = hard,
                    goodCount = good,
                    easyCount = easy,
                    completedCount = total,
                    remainingCount = 0,
                    recallAccuracy = if (total == 0) 0.0 else (good + easy).toDouble() / total,
                    passRate = if (total == 0) 0.0 else (hard + good + easy).toDouble() / total,
                    estimatedMinutes = 0,
                )
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
            statsDao.observeBookStats(now).map { rows -> rows.map { it.toModel() } }

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
            return statsDao.observeReviewedCards(from).map { cards ->
                val values =
                    cards.mapNotNull { entity ->
                        val card = entity.toModel()
                        scheduler.retrievability(card, now) ?: card.retrievability
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

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

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
