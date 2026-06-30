package com.zzz.androidvocab.core.database

import androidx.room.withTransaction
import com.zzz.androidvocab.core.common.AppError
import com.zzz.androidvocab.core.common.AppException
import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.common.daysBetween
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
import com.zzz.androidvocab.core.model.ReviewDataRepairResult
import com.zzz.androidvocab.core.model.ReviewLog
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewState
import com.zzz.androidvocab.core.scheduler.ReviewScheduler
import com.zzz.androidvocab.core.scheduler.ScheduleInput
import kotlinx.coroutines.CancellationException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewDataIntegrityService
    @Inject
    constructor(
        private val database: VocabDatabase,
        private val reviewDao: ReviewDao,
        private val statsDao: StatsDao,
        private val scheduler: ReviewScheduler,
        private val clockProvider: ClockProvider,
    ) {
        suspend fun inspect(): ReviewDataIntegrityReport =
            database.withTransaction {
                inspectInTransaction(clockProvider.now())
            }

        suspend fun repair(): ReviewDataRepairResult =
            database.withTransaction {
                repairInTransaction(clockProvider.now())
            }

        private suspend fun repairInTransaction(now: Instant): ReviewDataRepairResult {
            val before = inspectInTransaction(now)
            val logicalLogGroups = loadLogicalLogGroups()
            val repaired =
                repairInTransaction(
                    logicalLogGroups = logicalLogGroups,
                    dailyStatsIssueCount = before.missingDailyStatsCount + before.inconsistentDailyStatsCount,
                    now = now,
                    snapshotOnly = true,
                )
            val after = inspectInTransaction(now)
            return ReviewDataRepairResult(
                before = before,
                after = after,
                repairedCount = repaired.repairedCount,
                timelineConflictCacheRebuiltCount = repaired.timelineConflictCacheRebuiltCount,
            )
        }

        private suspend fun inspectInTransaction(now: Instant): ReviewDataIntegrityReport {
            var missingCacheCount = 0
            var inconsistentCacheCount = 0
            var legacyLogCardCount = 0
            var malformedLogCardCount = 0
            var timelineConflictCardCount = 0
            var cardRepairableIssueCount = 0
            val logicalLogGroups = loadLogicalLogGroups()
            logicalLogGroups.forEach { (key, logs) ->
                val current = reviewDao.getCardByWordAndBook(key.wordId, key.bookCode.name)?.toModel()
                val targetCardId = canonicalCardId(key)
                val hasLegacyLogs = logs.any { !it.hasCardSnapshot() }
                val hasMalformedLogCardId = logs.any { it.cardId != canonicalCardId(key) }
                val hasMalformedCurrentCardId = current?.id?.let { it != targetCardId } ?: false
                if (hasLegacyLogs) {
                    legacyLogCardCount += 1
                } else if (hasSnapshotTimelineConflict(targetCardId, logs, now)) {
                    timelineConflictCardCount += 1
                }
                if (hasMalformedLogCardId) {
                    malformedLogCardCount += 1
                }
                if (current == null) {
                    missingCacheCount += 1
                    if (!hasLegacyLogs) {
                        cardRepairableIssueCount += 1
                    }
                } else {
                    val hasReplayedStateMismatch =
                        if (hasLegacyLogs) {
                            false
                        } else {
                            current.differsFrom(replayLogsToCard(targetCardId, logs, now))
                        }
                    if (hasMalformedCurrentCardId || hasReplayedStateMismatch) {
                        inconsistentCacheCount += 1
                        cardRepairableIssueCount += 1
                    }
                }
            }
            val dailyStatsFromLogs = statsDao.dailyStatsFromLogsRows().associateBy { it.localDay }
            val cachedDailyStats = statsDao.dailyStats().associateBy { it.localDay }
            val dailyStatsDays = dailyStatsFromLogs.size
            val missingDailyStatsCount = dailyStatsFromLogs.keys.count { it !in cachedDailyStats }
            val inconsistentDailyStatsCount =
                dailyStatsFromLogs.entries.count { (localDay, derived) ->
                    val cached = cachedDailyStats[localDay] ?: return@count false
                    cached.differsFrom(derived)
                } + cachedDailyStats.keys.count { it !in dailyStatsFromLogs }
            return ReviewDataIntegrityReport(
                cardsWithLogs = logicalLogGroups.size,
                missingCacheCount = missingCacheCount,
                inconsistentCacheCount = inconsistentCacheCount,
                legacyLogCardCount = legacyLogCardCount,
                orphanLogCount = reviewDao.countOrphanLogs(),
                malformedLogCardCount = malformedLogCardCount,
                dailyStatsDays = dailyStatsDays,
                missingDailyStatsCount = missingDailyStatsCount,
                inconsistentDailyStatsCount = inconsistentDailyStatsCount,
                timelineConflictCardCount = timelineConflictCardCount,
                repairableIssueCount =
                    cardRepairableIssueCount + missingDailyStatsCount + inconsistentDailyStatsCount,
            )
        }

        private suspend fun repairInTransaction(
            logicalLogGroups: Map<IntegrityLogicalCardKey, List<ReviewLog>>,
            dailyStatsIssueCount: Int,
            now: Instant,
            snapshotOnly: Boolean,
        ): RepairCacheResult {
            var repaired = 0
            var timelineConflictCacheRebuiltCount = 0
            logicalLogGroups.forEach { (key, logs) ->
                var current = reviewDao.getCardByWordAndBook(key.wordId, key.bookCode.name)?.toModel()
                val targetCardId = canonicalCardId(key)
                if (current != null && current.id != targetCardId) {
                    reviewDao.upsertCard(current.copy(id = targetCardId).toEntity())
                    reviewDao.deleteCard(current.id)
                    current = current.copy(id = targetCardId)
                    repaired += 1
                }
                if (snapshotOnly && logs.any { !it.hasCardSnapshot() }) {
                    return@forEach
                }
                val hasTimelineConflict = hasSnapshotTimelineConflict(targetCardId, logs, now)
                val replayed = replayLogsToCard(targetCardId, logs, now)
                if (current == null || current.differsFrom(replayed)) {
                    reviewDao.upsertCard(replayed.toEntity())
                    repaired += 1
                    if (hasTimelineConflict) {
                        timelineConflictCacheRebuiltCount += 1
                    }
                }
            }
            if (dailyStatsIssueCount > 0) {
                statsDao.rebuildDailyStatsCache(now)
                repaired += dailyStatsIssueCount
            }
            return RepairCacheResult(
                repairedCount = repaired,
                timelineConflictCacheRebuiltCount = timelineConflictCacheRebuiltCount,
            )
        }

        private suspend fun hasSnapshotTimelineConflict(
            cardId: String,
            logs: List<ReviewLog>,
            now: Instant,
        ): Boolean {
            if (logs.isEmpty() || logs.any { !it.hasCardSnapshot() }) {
                return false
            }
            var card = baseCardForReplay(cardId, logs, now)
            logs.forEach { log ->
                if (!log.matchesCardBeforeSnapshot(card, clockProvider.zoneId())) {
                    return true
                }
                card = card.applySnapshot(log)
            }
            return false
        }

        private suspend fun replayLogsToCard(
            cardId: String,
            logs: List<ReviewLog>,
            now: Instant,
        ): ReviewCard {
            val baseCard = baseCardForReplay(cardId, logs, now)
            return if (logs.all { it.hasCardSnapshot() }) {
                logs.fold(baseCard) { card, log -> card.applySnapshot(log) }
            } else {
                logs.fold(baseCard) { card, log ->
                    scheduleOrThrow(
                        ScheduleInput(
                            card = card,
                            rating = log.rating,
                            reviewedAt = log.reviewedAt,
                            zoneId = clockProvider.zoneId(),
                            targetRetention = log.targetRetention,
                            durationMs = log.durationMs,
                            enableFuzzing = false,
                        ),
                    ).nextCard
                }
            }
        }

        private suspend fun baseCardForReplay(
            cardId: String,
            logs: List<ReviewLog>,
            now: Instant,
        ): ReviewCard {
            val firstLog =
                logs.firstOrNull()
                    ?: throw AppException(
                        AppError.DatabaseWriteFailed("No review logs found for card: $cardId"),
                    )
            val baseItem =
                readNewQueueItem(
                    wordId = firstLog.wordId,
                    bookCode = firstLog.bookCode.name,
                    now = now,
                    cardIdOverride = cardId,
                )
            val firstReviewedAt = firstLog.reviewedAt
            return baseItem.card.copy(
                state = ReviewState.New,
                difficulty = null,
                stability = null,
                retrievability = null,
                scheduledDays = 0,
                dueAt = firstReviewedAt,
                lastReviewAt = null,
                reviewCount = 0,
                lapseCount = 0,
                firstReviewedAt = null,
                createdAt = firstReviewedAt ?: baseItem.card.createdAt,
                updatedAt = firstReviewedAt ?: baseItem.card.createdAt,
            )
        }

        private suspend fun readNewQueueItem(
            wordId: String,
            bookCode: String,
            now: Instant,
            cardIdOverride: String? = null,
        ): ReviewQueueItem {
            val notFoundCardId = cardIdOverride ?: "card|$bookCode|$wordId"
            val queueItem =
                reviewDao.getNewQueueItem(wordId, bookCode)?.toQueueItem(now)
                    ?: throw AppException(
                        AppError.DatabaseWriteFailed("Card not found: $notFoundCardId"),
                    )
            return cardIdOverride?.let { overrideId ->
                queueItem.copy(card = queueItem.card.copy(id = overrideId))
            } ?: queueItem
        }

        private suspend fun loadLogicalLogGroups(): Map<IntegrityLogicalCardKey, List<ReviewLog>> =
            reviewDao
                .getValidLogs()
                .map { it.toModel() }
                .groupBy { log -> IntegrityLogicalCardKey(wordId = log.wordId, bookCode = log.bookCode) }

        private fun scheduleOrThrow(input: ScheduleInput) =
            try {
                scheduler.schedule(input)
            } catch (error: AppException) {
                throw error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw AppException(
                    AppError.SchedulerFailed(error.message ?: "Unable to calculate next review"),
                    error,
                )
            }
    }

private data class IntegrityLogicalCardKey(
    val wordId: String,
    val bookCode: BookCode,
)

private data class RepairCacheResult(
    val repairedCount: Int,
    val timelineConflictCacheRebuiltCount: Int,
)

private fun canonicalCardId(key: IntegrityLogicalCardKey): String =
    com.zzz.androidvocab.core.common
        .cardId(key.wordId, key.bookCode.name)

private fun ReviewLog.hasCardSnapshot(): Boolean =
    stateAfter != null &&
        dueAtAfter != null &&
        scheduledDaysAfter != null &&
        difficultyAfter != null &&
        stabilityAfter != null &&
        retrievabilityAfter != null

private fun ReviewLog.matchesCardBeforeSnapshot(
    card: ReviewCard,
    zoneId: java.time.ZoneId,
): Boolean =
    scheduledDaysBefore == card.scheduledDays &&
        difficultyBefore == card.difficulty &&
        stabilityBefore == card.stability &&
        retrievabilityBefore == card.retrievability &&
        elapsedDays == daysBetween(card.lastReviewAt, reviewedAt, zoneId)

private fun ReviewCard.applySnapshot(log: ReviewLog) =
    copy(
        state = requireNotNull(log.stateAfter),
        difficulty = requireNotNull(log.difficultyAfter),
        stability = requireNotNull(log.stabilityAfter),
        retrievability = requireNotNull(log.retrievabilityAfter),
        scheduledDays = requireNotNull(log.scheduledDaysAfter),
        dueAt = requireNotNull(log.dueAtAfter),
        lastReviewAt = log.reviewedAt,
        reviewCount = reviewCount + 1,
        lapseCount = lapseCount + if (log.rating == com.zzz.androidvocab.core.model.ReviewRating.Again) 1 else 0,
        firstReviewedAt = firstReviewedAt ?: log.reviewedAt,
        updatedAt = log.reviewedAt,
    )

private fun ReviewCard.differsFrom(other: ReviewCard): Boolean =
    state != other.state ||
        difficulty != other.difficulty ||
        stability != other.stability ||
        retrievability != other.retrievability ||
        scheduledDays != other.scheduledDays ||
        dueAt != other.dueAt ||
        lastReviewAt != other.lastReviewAt ||
        reviewCount != other.reviewCount ||
        lapseCount != other.lapseCount ||
        firstReviewedAt != other.firstReviewedAt ||
        updatedAt != other.updatedAt

private fun DailyStatsEntity.differsFrom(other: ReviewDailyStatsView): Boolean =
    newCount != other.newCount ||
        reviewCount != other.reviewCount ||
        againCount != other.againCount ||
        hardCount != other.hardCount ||
        goodCount != other.goodCount ||
        easyCount != other.easyCount ||
        completedCount != other.completedCount ||
        recallAccuracy != other.recallAccuracy ||
        passRate != other.passRate ||
        estimatedMinutes != other.estimatedMinutes
