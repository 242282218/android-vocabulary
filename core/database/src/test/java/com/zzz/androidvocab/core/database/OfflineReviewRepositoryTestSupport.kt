package com.zzz.androidvocab.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.common.cardId
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.ReviewState
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import com.zzz.androidvocab.core.scheduler.ReviewLogPatch
import com.zzz.androidvocab.core.scheduler.ReviewScheduler
import com.zzz.androidvocab.core.scheduler.ScheduleInput
import com.zzz.androidvocab.core.scheduler.ScheduleResult
import com.zzz.androidvocab.core.scheduler.SchedulerAlgorithm
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

internal const val WORD_ID = "word-1"
internal const val SECONDS_PER_DAY = 86_400L

internal fun newOfflineReviewDatabase(): VocabDatabase =
    Room
        .inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VocabDatabase::class.java,
        ).allowMainThreadQueries()
        .build()

internal fun newOfflineReviewRepository(
    database: VocabDatabase,
    scheduler: ReviewScheduler,
    clockProvider: ClockProvider,
): OfflineReviewRepository =
    OfflineReviewRepository(
        database = database,
        reviewDao = database.reviewDao(),
        statsDao = database.statsDao(),
        scheduler = scheduler,
        clockProvider = clockProvider,
    )

internal suspend fun seedWord(
    database: VocabDatabase,
    clock: ClockProvider,
    wordId: String = WORD_ID,
    value: String = "abandon",
    orderIndex: Int = 0,
) {
    database.wordDao().upsertWords(
        listOf(
            WordEntryEntity(
                id = wordId,
                word = value,
                meaning = "放弃",
                phonetic = null,
                partOfSpeech = "verb",
                definition = null,
                cefrLevel = null,
                cefrRank = 0.0,
                frequency = 1.0,
                sourceFlagsJson = "[]",
                coverageTier = null,
                createdAt = clock.now(),
                updatedAt = clock.now(),
            ),
        ),
    )
    database.wordDao().upsertMemberships(
        listOf(
            WordBookMembershipEntity(
                wordId = wordId,
                bookCode = BookCode.CET4.name,
                orderIndex = orderIndex,
                examFrequencyScore = 1.0,
                examPriorityScore = 1.0,
                isPhraseBacked = false,
                phraseCount = 0,
            ),
        ),
    )
}

internal fun command(
    cardId: String,
    reviewedAt: Instant,
    rating: ReviewRating,
    targetRetention: Double,
    expectedLastReviewAt: Instant? = null,
    expectedReviewCount: Int? = null,
) = SubmitFeedbackCommand(
    cardId = cardId,
    rating = rating,
    reviewedAt = reviewedAt,
    expectedLastReviewAt = expectedLastReviewAt,
    expectedReviewCount = expectedReviewCount,
    durationMs = 500,
    targetRetention = targetRetention,
)

internal fun reviewCard(
    wordId: String,
    now: Instant,
    state: ReviewState,
    scheduledDays: Int,
    due: Boolean,
) = ReviewCardEntity(
    id = cardId(wordId, BookCode.CET4.name),
    wordId = wordId,
    bookCode = BookCode.CET4.name,
    state = state.name,
    difficulty = 0.5,
    stability = scheduledDays.toDouble(),
    retrievability = if (scheduledDays >= 21) 0.9 else 0.7,
    scheduledDays = scheduledDays,
    dueAt = if (due) now.minusSeconds(60) else now.plusSeconds(SECONDS_PER_DAY),
    lastReviewAt = now.minusSeconds(SECONDS_PER_DAY),
    reviewCount = 1,
    lapseCount = 0,
    firstReviewedAt = now.minusSeconds(SECONDS_PER_DAY),
    createdAt = now.minusSeconds(SECONDS_PER_DAY),
    updatedAt = now,
)

internal suspend fun insertLegacyLog(
    database: VocabDatabase,
    cardId: String,
    wordId: String,
    reviewedAt: Instant,
) {
    database.reviewDao().insertLog(
        ReviewLogEntity(
            id = "legacy-log-$wordId",
            cardId = cardId,
            wordId = wordId,
            bookCode = BookCode.CET4.name,
            rating = ReviewRating.Good.wireName,
            reviewedAt = reviewedAt,
            localDay = "2026-05-16",
            elapsedDays = null,
            scheduledDaysBefore = 0,
            scheduledDaysAfter = 10,
            difficultyBefore = null,
            difficultyAfter = 4.0,
            stabilityBefore = null,
            stabilityAfter = 10.0,
            retrievabilityBefore = null,
            retrievabilityAfter = 0.8,
            durationMs = 500,
            targetRetention = 0.83,
            algorithm = "fsrs",
            algorithmVersion = "legacy",
        ),
    )
}

internal class FixedClock : ClockProvider {
    private val fixedNow = Instant.parse("2026-05-16T07:00:00Z")
    private val fixedZone = ZoneId.of("Asia/Shanghai")

    override fun now(): Instant = fixedNow

    override fun zoneId(): ZoneId = fixedZone
}

internal class CountingClock(
    private val fixedNow: Instant = Instant.parse("2026-05-16T07:00:00Z"),
    private val fixedZone: ZoneId = ZoneId.of("Asia/Shanghai"),
) : ClockProvider {
    var nowCallCount: Int = 0
        private set

    override fun now(): Instant {
        nowCallCount += 1
        return fixedNow
    }

    override fun zoneId(): ZoneId = fixedZone
}

internal class DeterministicScheduler : ReviewScheduler {
    override val algorithm: SchedulerAlgorithm = SchedulerAlgorithm.Fsrs

    override fun schedule(input: ScheduleInput): ScheduleResult {
        val scheduledDays = input.card.scheduledDays + (input.targetRetention * 100).toInt() + input.rating.ordinal
        val nextCard =
            input.card.copy(
                state = ReviewState.Review,
                difficulty = input.targetRetention,
                stability = scheduledDays.toDouble(),
                retrievability = input.targetRetention,
                scheduledDays = scheduledDays,
                dueAt = input.reviewedAt.plusSeconds(scheduledDays.toLong() * SECONDS_PER_DAY),
                lastReviewAt = input.reviewedAt,
                reviewCount = input.card.reviewCount + 1,
                lapseCount = input.card.lapseCount + if (input.rating == ReviewRating.Again) 1 else 0,
                firstReviewedAt = input.card.firstReviewedAt ?: input.reviewedAt,
                updatedAt = input.reviewedAt,
            )
        return ScheduleResult(
            nextCard = nextCard,
            logPatch =
                ReviewLogPatch(
                    scheduledDaysBefore = input.card.scheduledDays,
                    scheduledDaysAfter = scheduledDays,
                    difficultyBefore = input.card.difficulty,
                    difficultyAfter = nextCard.difficulty,
                    stabilityBefore = input.card.stability,
                    stabilityAfter = nextCard.stability,
                    retrievabilityBefore = input.card.retrievability,
                    retrievabilityAfter = nextCard.retrievability,
                ),
            algorithmVersion = "test",
        )
    }
}

internal class RecordingScheduler : ReviewScheduler {
    override val algorithm: SchedulerAlgorithm = SchedulerAlgorithm.Fsrs

    override fun schedule(input: ScheduleInput): ScheduleResult {
        enableFuzzingValues += input.enableFuzzing
        val scheduledDays = 1
        val nextCard =
            input.card.copy(
                state = ReviewState.Review,
                difficulty = input.targetRetention,
                stability = scheduledDays.toDouble(),
                retrievability = input.targetRetention,
                scheduledDays = scheduledDays,
                dueAt = input.reviewedAt.plusSeconds(SECONDS_PER_DAY),
                lastReviewAt = input.reviewedAt,
                reviewCount = input.card.reviewCount + 1,
                lapseCount = input.card.lapseCount + if (input.rating == ReviewRating.Again) 1 else 0,
                firstReviewedAt = input.card.firstReviewedAt ?: input.reviewedAt,
                updatedAt = input.reviewedAt,
            )
        return ScheduleResult(
            nextCard = nextCard,
            logPatch =
                ReviewLogPatch(
                    scheduledDaysBefore = input.card.scheduledDays,
                    scheduledDaysAfter = scheduledDays,
                    difficultyBefore = input.card.difficulty,
                    difficultyAfter = nextCard.difficulty,
                    stabilityBefore = input.card.stability,
                    stabilityAfter = nextCard.stability,
                    retrievabilityBefore = input.card.retrievability,
                    retrievabilityAfter = nextCard.retrievability,
                ),
            algorithmVersion = "test",
        )
    }

    companion object {
        val enableFuzzingValues = mutableListOf<Boolean>()
    }
}

internal class OperationRecordingScheduler(
    private val delegate: ReviewScheduler,
    private val operations: MutableList<String>,
) : ReviewScheduler {
    override val algorithm: SchedulerAlgorithm = delegate.algorithm

    override fun schedule(input: ScheduleInput): ScheduleResult {
        operations += "schedule"
        return delegate.schedule(input)
    }
}

internal class FailingScheduler : ReviewScheduler {
    override val algorithm: SchedulerAlgorithm = SchedulerAlgorithm.Fsrs

    override fun schedule(input: ScheduleInput): ScheduleResult = error("scheduler unavailable")
}

internal class FailingUpsertCardReviewDao(
    private val delegate: ReviewDao,
) : ReviewDao {
    override suspend fun upsertCard(card: ReviewCardEntity) {
        error("card upsert unavailable")
    }

    override suspend fun upsertCards(cards: List<ReviewCardEntity>) = delegate.upsertCards(cards)

    override suspend fun updateCard(card: ReviewCardEntity) = delegate.updateCard(card)

    override suspend fun insertLog(log: ReviewLogEntity) = delegate.insertLog(log)

    override suspend fun updateLog(log: ReviewLogEntity) = delegate.updateLog(log)

    override suspend fun getCard(cardId: String): ReviewCardEntity? = delegate.getCard(cardId)

    override suspend fun getCardByWordAndBook(
        wordId: String,
        bookCode: String,
    ): ReviewCardEntity? = delegate.getCardByWordAndBook(wordId, bookCode)

    override suspend fun deleteCard(cardId: String) = delegate.deleteCard(cardId)

    override suspend fun getLogs(cardId: String): List<ReviewLogEntity> = delegate.getLogs(cardId)

    override suspend fun getCardIdsWithLogs(): List<String> = delegate.getCardIdsWithLogs()

    override suspend fun getValidLogs(): List<ReviewLogEntity> = delegate.getValidLogs()

    override suspend fun countOrphanLogs(): Int = delegate.countOrphanLogs()

    override suspend fun getCardIdsMissingCacheFromLogs(): List<String> = delegate.getCardIdsMissingCacheFromLogs()

    override suspend fun getLogsForMissingCacheCards(): List<ReviewLogEntity> = delegate.getLogsForMissingCacheCards()

    override suspend fun deleteCardsWithoutMembership() = delegate.deleteCardsWithoutMembership()

    override fun observeDueQueue(
        now: Instant,
        bookCodes: List<String>,
        difficultySince: Instant,
    ): Flow<List<ReviewQueueRow>> =
        delegate.observeDueQueue(
            now = now,
            bookCodes = bookCodes,
            difficultySince = difficultySince,
        )

    override fun observeNewQueue(
        bookCodes: List<String>,
        limit: Int,
    ): Flow<List<ReviewQueueRow>> = delegate.observeNewQueue(bookCodes, limit)

    override suspend fun getQueueItem(cardId: String): ReviewQueueRow? = delegate.getQueueItem(cardId)

    override suspend fun getNewQueueItem(
        wordId: String,
        bookCode: String,
    ): ReviewQueueRow? = delegate.getNewQueueItem(wordId, bookCode)
}

internal class FailingUpdateLogReviewDao(
    private val delegate: ReviewDao,
) : ReviewDao {
    override suspend fun upsertCard(card: ReviewCardEntity) = delegate.upsertCard(card)

    override suspend fun upsertCards(cards: List<ReviewCardEntity>) = delegate.upsertCards(cards)

    override suspend fun updateCard(card: ReviewCardEntity) = delegate.updateCard(card)

    override suspend fun insertLog(log: ReviewLogEntity) = delegate.insertLog(log)

    override suspend fun updateLog(log: ReviewLogEntity) {
        error("log update unavailable")
    }

    override suspend fun getCard(cardId: String): ReviewCardEntity? = delegate.getCard(cardId)

    override suspend fun getCardByWordAndBook(
        wordId: String,
        bookCode: String,
    ): ReviewCardEntity? = delegate.getCardByWordAndBook(wordId, bookCode)

    override suspend fun deleteCard(cardId: String) = delegate.deleteCard(cardId)

    override suspend fun getLogs(cardId: String): List<ReviewLogEntity> = delegate.getLogs(cardId)

    override suspend fun getCardIdsWithLogs(): List<String> = delegate.getCardIdsWithLogs()

    override suspend fun getValidLogs(): List<ReviewLogEntity> = delegate.getValidLogs()

    override suspend fun countOrphanLogs(): Int = delegate.countOrphanLogs()

    override suspend fun getCardIdsMissingCacheFromLogs(): List<String> = delegate.getCardIdsMissingCacheFromLogs()

    override suspend fun getLogsForMissingCacheCards(): List<ReviewLogEntity> = delegate.getLogsForMissingCacheCards()

    override suspend fun deleteCardsWithoutMembership() = delegate.deleteCardsWithoutMembership()

    override fun observeDueQueue(
        now: Instant,
        bookCodes: List<String>,
        difficultySince: Instant,
    ): Flow<List<ReviewQueueRow>> =
        delegate.observeDueQueue(
            now = now,
            bookCodes = bookCodes,
            difficultySince = difficultySince,
        )

    override fun observeNewQueue(
        bookCodes: List<String>,
        limit: Int,
    ): Flow<List<ReviewQueueRow>> = delegate.observeNewQueue(bookCodes, limit)

    override suspend fun getQueueItem(cardId: String): ReviewQueueRow? = delegate.getQueueItem(cardId)

    override suspend fun getNewQueueItem(
        wordId: String,
        bookCode: String,
    ): ReviewQueueRow? = delegate.getNewQueueItem(wordId, bookCode)
}

internal class RecordingPersistenceOrderReviewDao(
    private val delegate: ReviewDao,
) : ReviewDao {
    val operations = mutableListOf<String>()

    override suspend fun upsertCard(card: ReviewCardEntity) {
        operations += "upsertCard"
        delegate.upsertCard(card)
    }

    override suspend fun upsertCards(cards: List<ReviewCardEntity>) {
        operations += "upsertCards"
        delegate.upsertCards(cards)
    }

    override suspend fun updateCard(card: ReviewCardEntity) {
        operations += "updateCard"
        delegate.updateCard(card)
    }

    override suspend fun insertLog(log: ReviewLogEntity) {
        operations += "insertLog"
        delegate.insertLog(log)
    }

    override suspend fun updateLog(log: ReviewLogEntity) {
        operations += "updateLog"
        delegate.updateLog(log)
    }

    override suspend fun getCard(cardId: String): ReviewCardEntity? = delegate.getCard(cardId)

    override suspend fun getCardByWordAndBook(
        wordId: String,
        bookCode: String,
    ): ReviewCardEntity? = delegate.getCardByWordAndBook(wordId, bookCode)

    override suspend fun deleteCard(cardId: String) = delegate.deleteCard(cardId)

    override suspend fun getLogs(cardId: String): List<ReviewLogEntity> = delegate.getLogs(cardId)

    override suspend fun getCardIdsWithLogs(): List<String> = delegate.getCardIdsWithLogs()

    override suspend fun getValidLogs(): List<ReviewLogEntity> = delegate.getValidLogs()

    override suspend fun countOrphanLogs(): Int = delegate.countOrphanLogs()

    override suspend fun getCardIdsMissingCacheFromLogs(): List<String> = delegate.getCardIdsMissingCacheFromLogs()

    override suspend fun getLogsForMissingCacheCards(): List<ReviewLogEntity> = delegate.getLogsForMissingCacheCards()

    override suspend fun deleteCardsWithoutMembership() = delegate.deleteCardsWithoutMembership()

    override fun observeDueQueue(
        now: Instant,
        bookCodes: List<String>,
        difficultySince: Instant,
    ): Flow<List<ReviewQueueRow>> =
        delegate.observeDueQueue(
            now = now,
            bookCodes = bookCodes,
            difficultySince = difficultySince,
        )

    override fun observeNewQueue(
        bookCodes: List<String>,
        limit: Int,
    ): Flow<List<ReviewQueueRow>> = delegate.observeNewQueue(bookCodes, limit)

    override suspend fun getQueueItem(cardId: String): ReviewQueueRow? = delegate.getQueueItem(cardId)

    override suspend fun getNewQueueItem(
        wordId: String,
        bookCode: String,
    ): ReviewQueueRow? = delegate.getNewQueueItem(wordId, bookCode)
}
