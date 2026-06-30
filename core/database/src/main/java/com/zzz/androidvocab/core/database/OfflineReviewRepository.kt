package com.zzz.androidvocab.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.zzz.androidvocab.core.common.AppError
import com.zzz.androidvocab.core.common.AppException
import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.common.cardId
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
import com.zzz.androidvocab.core.model.effectiveSelectedBookCodeNames
import com.zzz.androidvocab.core.scheduler.ReviewScheduler
import com.zzz.androidvocab.core.scheduler.ScheduleInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.Instant
import javax.inject.Inject

@Suppress("TooManyFunctions")
class OfflineReviewRepository
    @Inject
    constructor(
        private val database: VocabDatabase,
        private val reviewDao: ReviewDao,
        private val statsDao: StatsDao,
        private val scheduler: ReviewScheduler,
        private val clockProvider: ClockProvider,
        private val integrityService: ReviewDataIntegrityService,
    ) : ReviewRepository {
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeTodayQueue(
            now: Instant,
            selectedBooks: Set<BookCode>,
            dailyNewLimit: Int,
        ): Flow<TodayQueue> {
            val bookCodes = selectedBooks.effectiveSelectedBookCodeNames()
            val dueFlow =
                reviewDao.observeDueQueue(
                    now = now,
                    bookCodes = bookCodes,
                    difficultySince = now.minusSeconds(DIFFICULTY_PRIORITY_WINDOW_SECONDS),
                )
            val newFlow =
                statsDao
                    .observeDailyStatsFromLogs(
                        localDay = clockProvider.localDay(now),
                        bookCodes = bookCodes,
                    ).map { stats -> stats?.newCount ?: 0 }
                    .flatMapLatest { learnedToday ->
                        val remaining = (dailyNewLimit - learnedToday).coerceAtLeast(0)
                        if (remaining == 0) flowOf(emptyList()) else reviewDao.observeNewQueue(bookCodes, remaining)
                    }
            return combine(dueFlow, newFlow) { dueRows, newRows ->
                TodayQueue(
                    dueItems = dueRows.map { queueRowToItem(it, now) },
                    newItems = newRows.map { queueRowToItem(it, now) },
                )
            }.onStart {
                repairMissingCardsFromLogs(now)
            }
        }

        override suspend fun submitFeedback(command: SubmitFeedbackCommand): ReviewResult =
            try {
                database.withTransaction {
                    val currentItem = readQueueItem(command.cardId, command.reviewedAt)
                    validateReviewChronology(
                        currentLastReviewAt = currentItem.card.lastReviewAt,
                        expectedLastReviewAt = command.expectedLastReviewAt,
                        currentReviewCount = currentItem.card.reviewCount,
                        expectedReviewCount = command.expectedReviewCount,
                        lastReviewAt = currentItem.card.lastReviewAt,
                        reviewedAt = command.reviewedAt,
                    )
                    val pendingLog = preparePendingReviewLog(currentItem, command)
                    val scheduleResult = scheduleReviewResult(currentItem.card, command)
                    val finalizedLog = pendingLog.applyScheduleSnapshot(scheduleResult)
                    reviewDao.insertLog(finalizedLog.toEntity())
                    reviewDao.upsertCard(scheduleResult.nextCard.toEntity())
                    refreshDailyStats(finalizedLog.localDay, command.reviewedAt)
                    ReviewResult(
                        reviewedItem = currentItem,
                        nextCard = scheduleResult.nextCard,
                        log = finalizedLog,
                    )
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

        private fun preparePendingReviewLog(
            currentItem: ReviewQueueItem,
            command: SubmitFeedbackCommand,
        ): ReviewLog {
            val canonicalCardId = currentItem.card.id
            return ReviewLog(
                id = logId(canonicalCardId, command.reviewedAt.toString()),
                cardId = canonicalCardId,
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
                scheduledDaysBefore = currentItem.card.scheduledDays,
                scheduledDaysAfter = null,
                difficultyBefore = currentItem.card.difficulty,
                difficultyAfter = null,
                stabilityBefore = currentItem.card.stability,
                stabilityAfter = null,
                retrievabilityBefore = currentItem.card.retrievability,
                retrievabilityAfter = null,
                durationMs = command.durationMs,
                targetRetention = command.targetRetention,
                algorithm = scheduler.algorithm.name.lowercase(),
                algorithmVersion = PENDING_REVIEW_LOG_ALGORITHM_VERSION,
            )
        }

        private fun scheduleReviewResult(
            card: ReviewCard,
            command: SubmitFeedbackCommand,
        ) = scheduleOrThrow(
            ScheduleInput(
                card = card,
                rating = command.rating,
                reviewedAt = command.reviewedAt,
                zoneId = clockProvider.zoneId(),
                targetRetention = command.targetRetention,
                durationMs = command.durationMs,
            ),
        )

        override suspend fun replayLogs(cardId: String) =
            database.withTransaction {
                val replaySource = resolveReplaySource(cardId)
                replayLogsToCard(
                    cardId = replaySource.resolvedCardId,
                    logs = replaySource.logs,
                    now = clockProvider.now(),
                )
            }

        override suspend fun getQueueItem(cardId: String): ReviewQueueItem = readQueueItem(cardId, clockProvider.now())

        override suspend fun inspectReviewDataIntegrity(): ReviewDataIntegrityReport = integrityService.inspect()

        override suspend fun repairReviewDataCache(): ReviewDataRepairResult = integrityService.repair()

        private suspend fun readQueueItem(
            cardId: String,
            now: Instant,
        ): ReviewQueueItem {
            reviewDao.getQueueItem(cardId)?.let { row ->
                return queueRowToItem(row, now)
            }
            val replaySource = resolveReplaySource(cardId)
            if (replaySource.logs.isNotEmpty()) {
                val repairedCard = replayLogsToCard(replaySource.resolvedCardId, replaySource.logs, now)
                reviewDao.upsertCard(repairedCard.toEntity())
                return reviewDao.getQueueItem(repairedCard.id)?.toQueueItem(now)
                    ?: readNewQueueItem(
                        wordId = repairedCard.wordId,
                        bookCode = repairedCard.bookCode.name,
                        now = now,
                    ).copy(card = repairedCard, isNew = false)
            }
            return readNewQueueItem(cardId, now)
        }

        private suspend fun readNewQueueItem(
            cardId: String,
            now: Instant,
        ): ReviewQueueItem {
            val parts = parseCardId(cardId)
            if (parts == null) {
                throw AppException(AppError.DatabaseWriteFailed("Unknown card id: $cardId"))
            }
            return readNewQueueItem(
                wordId = parts.wordId,
                bookCode = parts.bookCode,
                now = now,
                cardIdOverride = cardId,
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

        private suspend fun repairMissingCardsFromLogs(now: Instant): Int {
            val missingCardIds = reviewDao.getCardIdsMissingCacheFromLogs()
            if (missingCardIds.isEmpty()) return 0
            val repairedCards =
                missingCardIds.mapNotNull { cardId ->
                    val replaySource = resolveReplaySource(cardId)
                    if (replaySource.logs.isEmpty()) return@mapNotNull null
                    val logicalKey = parseLogicalCardKey(cardId)
                    val targetId = logicalKey?.let { canonicalCardId(it) } ?: cardId
                    runCatching {
                        replayLogsToCard(targetId, replaySource.logs, now).toEntity()
                    }.getOrElse { error ->
                        when (error) {
                            is CancellationException -> throw error
                            is AppException -> null
                            else -> null
                        }
                    }
                }
            if (repairedCards.isNotEmpty()) {
                reviewDao.upsertCards(repairedCards)
            }
            return repairedCards.size
        }

        private suspend fun refreshDailyStats(
            localDay: String,
            updatedAt: Instant,
        ) {
            val stats = statsDao.dailyStatsFromLogs(localDay, updatedAt) ?: return
            statsDao.upsertDailyStats(stats)
        }

        private fun validateReviewChronology(
            currentLastReviewAt: Instant?,
            expectedLastReviewAt: Instant?,
            currentReviewCount: Int,
            expectedReviewCount: Int?,
            lastReviewAt: Instant?,
            reviewedAt: Instant,
        ) {
            val hasStaleCardSnapshot =
                expectedReviewCount != null &&
                    (
                        currentReviewCount != expectedReviewCount ||
                            expectedLastReviewAt != currentLastReviewAt
                    )
            if (hasStaleCardSnapshot) {
                throw AppException(
                    AppError.DatabaseWriteFailed("当前卡片状态已更新，请刷新后重试"),
                )
            }
            if (lastReviewAt != null && reviewedAt.isBefore(lastReviewAt)) {
                throw AppException(
                    AppError.DatabaseWriteFailed("系统时间早于上次复习，请校准设备时间后再试"),
                )
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
        }

        private suspend fun resolveReplaySource(cardId: String): ReplaySource {
            val directLogs = reviewDao.getLogs(cardId).map { it.toModel() }
            val logicalKey = parseLogicalCardKey(cardId) ?: directLogs.firstOrNull()?.toLogicalCardKey()
            if (logicalKey == null) {
                return ReplaySource(
                    resolvedCardId = cardId,
                    logs = directLogs,
                )
            }
            val logicalLogs = loadLogicalLogs(logicalKey)
            return ReplaySource(
                resolvedCardId = canonicalCardId(logicalKey),
                logs = logicalLogs.ifEmpty { directLogs },
            )
        }

        private suspend fun loadLogicalLogs(key: LogicalCardKey): List<ReviewLog> =
            reviewDao
                .getValidLogsByWordAndBook(key.wordId, key.bookCode.name)
                .map { it.toModel() }

        private suspend fun queueRowToItem(
            row: ReviewQueueRow,
            now: Instant,
        ): ReviewQueueItem {
            val cachedCardId = row.cardId
            val canonicalCardId = cardId(row.wordId, row.bookCode)
            if (cachedCardId == null || cachedCardId == canonicalCardId) {
                return row.toQueueItem(now)
            }
            val repairedCard = repairMalformedCardId(cachedCardId, canonicalCardId, row, now)
            return reviewDao.getQueueItem(repairedCard.id)?.toQueueItem(now)
                ?: row.toQueueItem(now).copy(card = repairedCard, isNew = false)
        }

        private suspend fun repairMalformedCardId(
            malformedCardId: String,
            canonicalCardId: String,
            row: ReviewQueueRow,
            now: Instant,
        ): com.zzz.androidvocab.core.model.ReviewCard {
            val replaySource = resolveReplaySource(malformedCardId)
            return if (replaySource.logs.isNotEmpty()) {
                replayLogsToCard(canonicalCardId, replaySource.logs, now)
            } else {
                row.toQueueItem(now).card.copy(id = canonicalCardId)
            }.also { repairedCard ->
                reviewDao.upsertCard(repairedCard.toEntity())
                reviewDao.deleteCard(malformedCardId)
            }
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

private const val DIFFICULTY_PRIORITY_WINDOW_SECONDS = 30L * 24L * 60L * 60L
private const val PENDING_REVIEW_LOG_ALGORITHM_VERSION = "<pending>"

private data class LogicalCardKey(
    val wordId: String,
    val bookCode: BookCode,
)

private data class ParsedCardId(
    val bookCode: String,
    val wordId: String,
)

private data class ReplaySource(
    val resolvedCardId: String,
    val logs: List<ReviewLog>,
)

private fun canonicalCardId(key: LogicalCardKey): String = cardId(key.wordId, key.bookCode.name)

private fun parseLogicalCardKey(cardId: String): LogicalCardKey? {
    val parts = parseCardId(cardId) ?: return null
    val bookCode = BookCode.entries.firstOrNull { it.name == parts.bookCode } ?: return null
    return LogicalCardKey(
        wordId = parts.wordId,
        bookCode = bookCode,
    )
}

private fun parseCardId(cardId: String): ParsedCardId? {
    val parts = cardId.split("|")
    if (parts.size != 3 || parts[0] != CARD_ID_PREFIX) {
        return null
    }
    return ParsedCardId(
        bookCode = parts[1],
        wordId = parts[2],
    )
}

private const val CARD_ID_PREFIX = "card"

private fun ReviewLog.toLogicalCardKey(): LogicalCardKey =
    LogicalCardKey(
        wordId = wordId,
        bookCode = bookCode,
    )

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

private fun ReviewLog.applyScheduleSnapshot(scheduleResult: com.zzz.androidvocab.core.scheduler.ScheduleResult) =
    copy(
        scheduledDaysAfter = scheduleResult.logPatch.scheduledDaysAfter,
        difficultyAfter = scheduleResult.logPatch.difficultyAfter,
        stabilityAfter = scheduleResult.logPatch.stabilityAfter,
        retrievabilityAfter = scheduleResult.logPatch.retrievabilityAfter,
        algorithmVersion = scheduleResult.algorithmVersion,
        stateAfter = scheduleResult.nextCard.state,
        dueAtAfter = scheduleResult.nextCard.dueAt,
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
