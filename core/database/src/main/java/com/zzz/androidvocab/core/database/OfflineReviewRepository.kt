package com.zzz.androidvocab.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.zzz.androidvocab.core.common.AppError
import com.zzz.androidvocab.core.common.AppException
import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.common.daysBetween
import com.zzz.androidvocab.core.common.logId
import com.zzz.androidvocab.core.domain.ReviewRepository
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
import com.zzz.androidvocab.core.model.ReviewDataRepairResult
import com.zzz.androidvocab.core.model.ReviewLog
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewResult
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import com.zzz.androidvocab.core.model.TodayQueue
import com.zzz.androidvocab.core.scheduler.ReviewScheduler
import com.zzz.androidvocab.core.scheduler.ScheduleInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class OfflineReviewRepository
    @Inject
    constructor(
        private val database: VocabDatabase,
        private val reviewDao: ReviewDao,
        private val statsDao: StatsDao,
        private val scheduler: ReviewScheduler,
        private val clockProvider: ClockProvider,
    ) : ReviewRepository {
        private val cardsRepairDone = AtomicBoolean(false)

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeTodayQueue(
            now: Instant,
            selectedBooks: Set<BookCode>,
            dailyNewLimit: Int,
        ): Flow<TodayQueue> {
            val bookCodes = selectedBooks.ifEmpty { BookCode.entries.toSet() }.map { it.name }
            val dueFlow =
                reviewDao.observeDueQueue(
                    now = now,
                    bookCodes = bookCodes,
                    difficultySince = now.minusSeconds(DIFFICULTY_PRIORITY_WINDOW_SECONDS),
                )
            val newFlow =
                statsDao.observeDailyNewCount(clockProvider.localDay(now)).flatMapLatest { learnedToday ->
                    val remaining = (dailyNewLimit - learnedToday).coerceAtLeast(0)
                    if (remaining == 0) flowOf(emptyList()) else reviewDao.observeNewQueue(bookCodes, remaining)
                }
            return combine(dueFlow, newFlow) { dueRows, newRows ->
                TodayQueue(
                    dueItems = dueRows.map { it.toQueueItem(now) },
                    newItems = newRows.map { it.toQueueItem(now) },
                )
            }.onStart { repairMissingCardsOnce(now) }
        }

        override suspend fun submitFeedback(command: SubmitFeedbackCommand): ReviewResult =
            try {
                database.withTransaction {
                    val currentItem = readQueueItem(command.cardId, command.reviewedAt)
                    val scheduleResult =
                        scheduleOrThrow(
                            ScheduleInput(
                                card = currentItem.card,
                                rating = command.rating,
                                reviewedAt = command.reviewedAt,
                                zoneId = clockProvider.zoneId(),
                                targetRetention = command.targetRetention,
                                durationMs = command.durationMs,
                            ),
                        )
                    val log =
                        ReviewLog(
                            id = logId(command.cardId, command.reviewedAt.toString()),
                            cardId = command.cardId,
                            wordId = currentItem.card.wordId,
                            bookCode = currentItem.card.bookCode,
                            rating = command.rating,
                            reviewedAt = command.reviewedAt,
                            localDay = clockProvider.localDay(command.reviewedAt),
                            elapsedDays =
                                daysBetween(
                                    currentItem.card.lastReviewAt,
                                    command.reviewedAt,
                                    clockProvider.zoneId(),
                                ),
                            scheduledDaysBefore = scheduleResult.logPatch.scheduledDaysBefore,
                            scheduledDaysAfter = scheduleResult.logPatch.scheduledDaysAfter,
                            difficultyBefore = scheduleResult.logPatch.difficultyBefore,
                            difficultyAfter = scheduleResult.logPatch.difficultyAfter,
                            stabilityBefore = scheduleResult.logPatch.stabilityBefore,
                            stabilityAfter = scheduleResult.logPatch.stabilityAfter,
                            retrievabilityBefore = scheduleResult.logPatch.retrievabilityBefore,
                            retrievabilityAfter = scheduleResult.logPatch.retrievabilityAfter,
                            durationMs = command.durationMs,
                            targetRetention = command.targetRetention,
                            algorithm = scheduler.algorithm.name.lowercase(),
                            algorithmVersion = scheduleResult.algorithmVersion,
                            stateAfter = scheduleResult.nextCard.state,
                            dueAtAfter = scheduleResult.nextCard.dueAt,
                        )
                    reviewDao.insertLog(log.toEntity())
                    reviewDao.upsertCard(scheduleResult.nextCard.toEntity())
                    refreshDailyStats(log.localDay, command.reviewedAt)
                    ReviewResult(currentItem, scheduleResult.nextCard, log)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: AppException) {
                throw error
            } catch (error: SQLiteConstraintException) {
                throw AppException(AppError.DatabaseWriteFailed("本次反馈已保存，请勿重复提交"), error)
            } catch (error: Exception) {
                throw AppException(
                    AppError.DatabaseWriteFailed(error.message ?: "Unable to save review feedback"),
                    error,
                )
            }

        override suspend fun replayLogs(cardId: String) =
            database.withTransaction {
                replayLogsToCard(
                    cardId = cardId,
                    logs = reviewDao.getLogs(cardId).map { it.toModel() },
                    now = clockProvider.now(),
                )
            }

        override suspend fun getQueueItem(cardId: String): ReviewQueueItem = readQueueItem(cardId, clockProvider.now())

        override suspend fun inspectReviewDataIntegrity(): ReviewDataIntegrityReport =
            database.withTransaction {
                inspectReviewDataIntegrityInTransaction(clockProvider.now())
            }

        override suspend fun repairReviewDataCache(): ReviewDataRepairResult =
            database.withTransaction {
                val before = inspectReviewDataIntegrityInTransaction(clockProvider.now())
                val repaired =
                    repairReviewDataCacheInTransaction(
                        cardIds = reviewDao.getCardIdsWithLogs(),
                        now = clockProvider.now(),
                        snapshotOnly = true,
                    )
                val after = inspectReviewDataIntegrityInTransaction(clockProvider.now())
                ReviewDataRepairResult(before = before, after = after, repairedCount = repaired)
            }

        private suspend fun readQueueItem(
            cardId: String,
            now: Instant,
        ): ReviewQueueItem {
            reviewDao.getQueueItem(cardId)?.let { return it.toQueueItem(now) }
            val logs = reviewDao.getLogs(cardId).map { it.toModel() }
            if (logs.isNotEmpty()) {
                val repairedCard = replayLogsToCard(cardId, logs, now)
                reviewDao.upsertCard(repairedCard.toEntity())
                return reviewDao.getQueueItem(cardId)?.toQueueItem(now)
                    ?: readNewQueueItem(cardId, now).copy(card = repairedCard, isNew = false)
            }
            return readNewQueueItem(cardId, now)
        }

        private suspend fun readNewQueueItem(
            cardId: String,
            now: Instant,
        ): ReviewQueueItem {
            val parts = cardId.split("|")
            if (parts.size != 3 || parts[0] != "card") {
                throw AppException(AppError.DatabaseWriteFailed("Unknown card id: $cardId"))
            }
            val bookCode = parts[1]
            val wordId = parts[2]
            return reviewDao.getNewQueueItem(wordId, bookCode)?.toQueueItem(now)
                ?: throw AppException(AppError.DatabaseWriteFailed("Card not found: $cardId"))
        }

        private suspend fun replayLogsToCard(
            cardId: String,
            logs: List<ReviewLog>,
            now: Instant,
        ): ReviewCard {
            val baseItem = readNewQueueItem(cardId, now)
            val firstReviewedAt = logs.firstOrNull()?.reviewedAt
            val baseCard =
                baseItem.card.copy(
                    state = com.zzz.androidvocab.core.model.ReviewState.New,
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

        private suspend fun repairMissingCardsOnce(now: Instant) {
            if (!cardsRepairDone.getAndSet(true)) {
                repairMissingCardsFromLogs(now)
            }
        }

        private suspend fun repairMissingCardsFromLogs(now: Instant): Int {
            val cardIds = reviewDao.getCardIdsMissingCacheFromLogs()
            cardIds.forEach { cardId ->
                val logs = reviewDao.getLogs(cardId).map { it.toModel() }
                if (logs.isNotEmpty()) {
                    reviewDao.upsertCard(replayLogsToCard(cardId, logs, now).toEntity())
                }
            }
            return cardIds.size
        }

        private suspend fun inspectReviewDataIntegrityInTransaction(now: Instant): ReviewDataIntegrityReport {
            var missingCacheCount = 0
            var inconsistentCacheCount = 0
            var legacyLogCardCount = 0
            var repairableIssueCount = 0
            val cardIds = reviewDao.getCardIdsWithLogs()
            cardIds.forEach { cardId ->
                val logs = reviewDao.getLogs(cardId).map { it.toModel() }
                val hasLegacyLogs = logs.any { !it.hasCardSnapshot() }
                if (hasLegacyLogs) {
                    legacyLogCardCount += 1
                }
                val current = reviewDao.getCard(cardId)?.toModel()
                if (current == null) {
                    missingCacheCount += 1
                    if (!hasLegacyLogs) {
                        repairableIssueCount += 1
                    }
                } else if (!hasLegacyLogs) {
                    val replayed = replayLogsToCard(cardId, logs, now)
                    if (current.differsFrom(replayed)) {
                        inconsistentCacheCount += 1
                        repairableIssueCount += 1
                    }
                }
            }
            return ReviewDataIntegrityReport(
                cardsWithLogs = cardIds.size,
                missingCacheCount = missingCacheCount,
                inconsistentCacheCount = inconsistentCacheCount,
                legacyLogCardCount = legacyLogCardCount,
                orphanLogCount = reviewDao.countOrphanLogs(),
                repairableIssueCount = repairableIssueCount,
            )
        }

        private suspend fun repairReviewDataCacheInTransaction(
            cardIds: List<String>,
            now: Instant,
            snapshotOnly: Boolean,
        ): Int {
            var repaired = 0
            cardIds.forEach { cardId ->
                val logs = reviewDao.getLogs(cardId).map { it.toModel() }
                if (logs.isEmpty() || (snapshotOnly && logs.any { !it.hasCardSnapshot() })) {
                    return@forEach
                }
                val replayed = replayLogsToCard(cardId, logs, now)
                val current = reviewDao.getCard(cardId)?.toModel()
                if (current == null || current.differsFrom(replayed)) {
                    reviewDao.upsertCard(replayed.toEntity())
                    repaired += 1
                }
            }
            return repaired
        }

        private suspend fun refreshDailyStats(
            localDay: String,
            updatedAt: Instant,
        ) {
            val counts = statsDao.dailyRatingCounts(localDay).associate { it.rating to it.count }
            val completed = statsDao.dailyCompleted(localDay)
            val newCount = statsDao.dailyNewCount(localDay)
            val durationMs = statsDao.dailyDurationMs(localDay)
            val again = counts["again"] ?: 0
            val hard = counts["hard"] ?: 0
            val good = counts["good"] ?: 0
            val easy = counts["easy"] ?: 0
            statsDao.upsertDailyStats(
                DailyStatsEntity(
                    localDay = localDay,
                    newCount = newCount,
                    reviewCount = (completed - newCount).coerceAtLeast(0),
                    againCount = again,
                    hardCount = hard,
                    goodCount = good,
                    easyCount = easy,
                    completedCount = completed,
                    recallAccuracy = if (completed == 0) 0.0 else (good + easy).toDouble() / completed,
                    passRate = if (completed == 0) 0.0 else (hard + good + easy).toDouble() / completed,
                    estimatedMinutes = durationMs.toCompletedMinutes(),
                    updatedAt = updatedAt,
                ),
            )
        }

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

private fun Long.toCompletedMinutes(): Int {
    if (this <= 0L) return 0
    return ((this + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt()
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val DIFFICULTY_PRIORITY_WINDOW_SECONDS = 30L * 24L * 60L * 60L

private fun ReviewLog.toEntity() =
    ReviewLogEntity(
        id = id,
        cardId = cardId,
        wordId = wordId,
        bookCode = bookCode.name,
        rating = rating.wireName,
        reviewedAt = reviewedAt,
        localDay = localDay,
        elapsedDays = elapsedDays,
        scheduledDaysBefore = scheduledDaysBefore,
        scheduledDaysAfter = scheduledDaysAfter,
        difficultyBefore = difficultyBefore,
        difficultyAfter = difficultyAfter,
        stabilityBefore = stabilityBefore,
        stabilityAfter = stabilityAfter,
        retrievabilityBefore = retrievabilityBefore,
        retrievabilityAfter = retrievabilityAfter,
        durationMs = durationMs,
        targetRetention = targetRetention,
        algorithm = algorithm,
        algorithmVersion = algorithmVersion,
        stateAfter = stateAfter?.name,
        dueAtAfter = dueAtAfter,
    )

private fun ReviewLog.hasCardSnapshot(): Boolean =
    stateAfter != null &&
        dueAtAfter != null &&
        scheduledDaysAfter != null &&
        difficultyAfter != null &&
        stabilityAfter != null &&
        retrievabilityAfter != null

private fun com.zzz.androidvocab.core.model.ReviewCard.applySnapshot(log: ReviewLog) =
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
