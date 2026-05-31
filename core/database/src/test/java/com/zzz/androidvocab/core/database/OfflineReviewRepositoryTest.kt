package com.zzz.androidvocab.core.database

import com.zzz.androidvocab.core.common.cardId
import com.zzz.androidvocab.core.common.logId
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.ReviewState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Suppress("LargeClass")
class OfflineReviewRepositoryTest {
    private lateinit var database: VocabDatabase
    private lateinit var repository: OfflineReviewRepository
    private val clock = FixedClock()
    private val scheduler = DeterministicScheduler()

    @Before
    fun setUp() {
        database = newOfflineReviewDatabase()
        repository = newOfflineReviewRepository(database, scheduler, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replayLogsRebuildsCurrentCardFromReviewLogs() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val firstReviewAt = Instant.parse("2026-05-16T08:00:00Z")
            val secondReviewAt = Instant.parse("2026-05-16T09:00:00Z")

            repository.submitFeedback(command(id, firstReviewAt, ReviewRating.Good, 0.82))
            repository.submitFeedback(command(id, secondReviewAt, ReviewRating.Easy, 0.91))

            val current = database.reviewDao().getCard(id)!!.toModel()
            val replayed = repository.replayLogs(id)

            assertEquals(current.reviewCount, replayed.reviewCount)
            assertEquals(current.scheduledDays, replayed.scheduledDays)
            assertEquals(current.difficulty, replayed.difficulty)
            assertEquals(current.lastReviewAt, replayed.lastReviewAt)
        }

    @Test
    fun replayLogsRebuildsCardWhenReviewCardCacheIsMissing() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val firstReviewAt = Instant.parse("2026-05-16T08:00:00Z")
            val secondReviewAt = Instant.parse("2026-05-16T09:00:00Z")

            repository.submitFeedback(command(id, firstReviewAt, ReviewRating.Good, 0.82))
            repository.submitFeedback(command(id, secondReviewAt, ReviewRating.Again, 0.91))
            val current = database.reviewDao().getCard(id)!!.toModel()

            database.reviewDao().deleteCard(id)
            val snapshotRepository =
                newOfflineReviewRepository(
                    database = database,
                    scheduler = FailingScheduler(),
                    clockProvider = clock,
                )
            val replayed = snapshotRepository.replayLogs(id)

            assertEquals(current.reviewCount, replayed.reviewCount)
            assertEquals(current.lapseCount, replayed.lapseCount)
            assertEquals(current.scheduledDays, replayed.scheduledDays)
            assertEquals(current.difficulty, replayed.difficulty)
            assertEquals(current.stability, replayed.stability)
            assertEquals(current.retrievability, replayed.retrievability)
            assertEquals(current.dueAt, replayed.dueAt)
            assertEquals(current.lastReviewAt, replayed.lastReviewAt)
        }

    @Test
    fun getQueueItemRepairsMissingReviewCardCacheFromReviewLogs() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val firstReviewAt = Instant.parse("2026-05-16T08:00:00Z")
            val secondReviewAt = Instant.parse("2026-05-16T09:00:00Z")

            repository.submitFeedback(command(id, firstReviewAt, ReviewRating.Good, 0.82))
            repository.submitFeedback(command(id, secondReviewAt, ReviewRating.Again, 0.91))
            val current = database.reviewDao().getCard(id)!!.toModel()

            database.reviewDao().deleteCard(id)
            val snapshotRepository =
                newOfflineReviewRepository(
                    database = database,
                    scheduler = FailingScheduler(),
                    clockProvider = clock,
                )
            val item = snapshotRepository.getQueueItem(id)
            val restored = database.reviewDao().getCard(id)!!.toModel()

            assertEquals(false, item.isNew)
            assertEquals(current.reviewCount, item.card.reviewCount)
            assertEquals(current.scheduledDays, item.card.scheduledDays)
            assertEquals(current.dueAt, item.card.dueAt)
            assertEquals(current.reviewCount, restored.reviewCount)
            assertEquals(current.scheduledDays, restored.scheduledDays)
            assertEquals(current.dueAt, restored.dueAt)
        }

    @Test
    fun submitFeedbackRepairsMissingReviewCardCacheBeforeScheduling() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val firstReviewAt = Instant.parse("2026-05-16T08:00:00Z")
            val secondReviewAt = Instant.parse("2026-05-16T09:00:00Z")
            val thirdReviewAt = Instant.parse("2026-05-16T10:00:00Z")

            repository.submitFeedback(command(id, firstReviewAt, ReviewRating.Good, 0.82))
            repository.submitFeedback(command(id, secondReviewAt, ReviewRating.Easy, 0.91))
            val current = database.reviewDao().getCard(id)!!.toModel()

            database.reviewDao().deleteCard(id)
            val result = repository.submitFeedback(command(id, thirdReviewAt, ReviewRating.Hard, 0.75))

            val expectedScheduledDays =
                current.scheduledDays + (0.75 * 100).toInt() + ReviewRating.Hard.ordinal
            assertEquals(false, result.reviewedItem.isNew)
            assertEquals(current.reviewCount, result.reviewedItem.card.reviewCount)
            assertEquals(secondReviewAt, result.reviewedItem.card.lastReviewAt)
            assertEquals(expectedScheduledDays, result.nextCard.scheduledDays)
            assertEquals(3, result.nextCard.reviewCount)
        }

    @Test
    fun submitFeedbackCanonicalizesNewLogWhenRequestUsesMalformedCachedCardId() =
        runTest {
            val malformedCardId = "broken-card-id"
            val canonicalCardId = cardId("broken-card-word", BookCode.CET4.name)
            val reviewedAt = Instant.parse("2026-05-16T08:00:00Z")
            seedWord(database, clock, wordId = "broken-card-word", value = "broken", orderIndex = 0)
            database.reviewDao().upsertCard(
                ReviewCardEntity(
                    id = malformedCardId,
                    wordId = "broken-card-word",
                    bookCode = BookCode.CET4.name,
                    state = ReviewState.Review.name,
                    difficulty = 0.7,
                    stability = 2.0,
                    retrievability = 0.7,
                    scheduledDays = 2,
                    dueAt = reviewedAt.minusSeconds(60),
                    lastReviewAt = reviewedAt.minusSeconds(SECONDS_PER_DAY),
                    reviewCount = 1,
                    lapseCount = 0,
                    firstReviewedAt = reviewedAt.minusSeconds(SECONDS_PER_DAY),
                    createdAt = reviewedAt.minusSeconds(SECONDS_PER_DAY),
                    updatedAt = reviewedAt.minusSeconds(SECONDS_PER_DAY),
                ),
            )

            val result = repository.submitFeedback(command(malformedCardId, reviewedAt, ReviewRating.Good, 0.9))
            val storedLogs = database.reviewDao().getLogs(canonicalCardId).map { it.toModel() }

            assertEquals(canonicalCardId, result.log.cardId)
            assertEquals(logId(canonicalCardId, reviewedAt.toString()), result.log.id)
            assertEquals(listOf(canonicalCardId), storedLogs.map { it.cardId })
            assertEquals(listOf(logId(canonicalCardId, reviewedAt.toString())), storedLogs.map { it.id })
            assertEquals(emptyList<String>(), database.reviewDao().getLogs(malformedCardId).map { it.cardId })
            assertEquals(null, database.reviewDao().getCard(malformedCardId))
            assertEquals(
                canonicalCardId,
                database
                    .reviewDao()
                    .getCard(canonicalCardId)
                    ?.toModel()
                    ?.id,
            )
        }

    @Test
    fun inspectAndRepairTreatMalformedCurrentCardIdAsRepairableCacheIssue() =
        runTest {
            val malformedCardId = "broken-card-id"
            val canonicalCardId = cardId("broken-card-word", BookCode.CET4.name)
            val reviewedAt = Instant.parse("2026-05-10T08:00:00Z")
            seedWord(database, clock, wordId = "broken-card-word", value = "broken", orderIndex = 0)
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "broken-log",
                    cardId = canonicalCardId,
                    wordId = "broken-card-word",
                    bookCode = BookCode.CET4.name,
                    rating = ReviewRating.Good.wireName,
                    reviewedAt = reviewedAt,
                    localDay = "2026-05-10",
                    elapsedDays = null,
                    scheduledDaysBefore = 0,
                    scheduledDaysAfter = 3,
                    difficultyBefore = null,
                    difficultyAfter = 0.82,
                    stabilityBefore = null,
                    stabilityAfter = 3.0,
                    retrievabilityBefore = null,
                    retrievabilityAfter = 0.82,
                    durationMs = 500,
                    targetRetention = 0.82,
                    algorithm = "fsrs",
                    algorithmVersion = "test",
                    stateAfter = ReviewState.Review.name,
                    dueAtAfter = reviewedAt.plusSeconds(3 * SECONDS_PER_DAY),
                ),
            )
            database.reviewDao().upsertCard(
                ReviewCardEntity(
                    id = malformedCardId,
                    wordId = "broken-card-word",
                    bookCode = BookCode.CET4.name,
                    state = ReviewState.Review.name,
                    difficulty = 0.82,
                    stability = 3.0,
                    retrievability = 0.82,
                    scheduledDays = 3,
                    dueAt = reviewedAt.plusSeconds(3 * SECONDS_PER_DAY),
                    lastReviewAt = reviewedAt,
                    reviewCount = 1,
                    lapseCount = 0,
                    firstReviewedAt = reviewedAt,
                    createdAt = reviewedAt,
                    updatedAt = reviewedAt,
                ),
            )
            val derivedStats = database.statsDao().dailyStatsFromLogs("2026-05-10", reviewedAt)
            database.statsDao().upsertDailyStats(checkNotNull(derivedStats))

            val reportBefore = repository.inspectReviewDataIntegrity()
            val repairResult = repository.repairReviewDataCache()
            val reportAfter = repairResult.after
            val canonicalCard = database.reviewDao().getCard(canonicalCardId)?.toModel()
            val malformedCard = database.reviewDao().getCard(malformedCardId)

            assertEquals(1, reportBefore.cardsWithLogs)
            assertEquals(0, reportBefore.missingCacheCount)
            assertEquals(1, reportBefore.inconsistentCacheCount)
            assertEquals(0, reportBefore.malformedLogCardCount)
            assertEquals(1, reportBefore.repairableIssueCount)
            assertEquals(0, reportBefore.manualReviewIssueCount)
            assertEquals(1, repairResult.repairedCount)
            assertEquals(0, reportAfter.inconsistentCacheCount)
            assertEquals(0, reportAfter.repairableIssueCount)
            assertNotNull(canonicalCard)
            assertEquals(null, malformedCard)
            assertEquals(canonicalCardId, canonicalCard?.id)
            assertEquals(reviewedAt.plusSeconds(3 * SECONDS_PER_DAY), canonicalCard?.dueAt)
            assertEquals(1, canonicalCard?.reviewCount)
        }

    @Test
    fun todayQueueRepairsMissingReviewCardCacheBeforeLoading() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val reviewedAt = Instant.parse("2026-05-10T08:00:00Z")

            repository.submitFeedback(command(id, reviewedAt, ReviewRating.Good, 0.01))
            val current = database.reviewDao().getCard(id)!!.toModel()
            database.reviewDao().deleteCard(id)

            val queue =
                repository
                    .observeTodayQueue(clock.now(), selectedBooks = setOf(BookCode.CET4), dailyNewLimit = 10)
                    .first()
            val restored = database.reviewDao().getCard(id)!!.toModel()

            assertEquals(emptyList<String>(), queue.newItems.map { it.word.word })
            assertEquals(listOf("abandon"), queue.dueItems.map { it.word.word })
            assertEquals(current.scheduledDays, restored.scheduledDays)
            assertEquals(current.dueAt, restored.dueAt)
        }

    @Test
    fun todayQueueRepairsMissingReviewCardCacheAgainAfterEarlierHealthyLoad() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val reviewedAt = Instant.parse("2026-05-10T08:00:00Z")

            repository
                .observeTodayQueue(clock.now(), selectedBooks = setOf(BookCode.CET4), dailyNewLimit = 10)
                .first()
            repository.submitFeedback(command(id, reviewedAt, ReviewRating.Good, 0.01))
            val current = database.reviewDao().getCard(id)!!.toModel()
            database.reviewDao().deleteCard(id)

            val queue =
                repository
                    .observeTodayQueue(clock.now(), selectedBooks = setOf(BookCode.CET4), dailyNewLimit = 10)
                    .first()
            val restored = database.reviewDao().getCard(id)!!.toModel()

            assertEquals(emptyList<String>(), queue.newItems.map { it.word.word })
            assertEquals(listOf("abandon"), queue.dueItems.map { it.word.word })
            assertEquals(current.scheduledDays, restored.scheduledDays)
            assertEquals(current.dueAt, restored.dueAt)
        }

    @Test
    fun todayQueueCanonicalizesMalformedCachedCardIdBeforeExpose() =
        runTest {
            val reviewedAt = Instant.parse("2026-05-10T08:00:00Z")
            val malformedCardId = "broken-card-id"
            val canonicalCardId = cardId("broken-card-word", BookCode.CET4.name)
            seedWord(database, clock, wordId = "broken-card-word", value = "broken", orderIndex = 0)
            database.reviewDao().upsertCard(
                ReviewCardEntity(
                    id = malformedCardId,
                    wordId = "broken-card-word",
                    bookCode = BookCode.CET4.name,
                    state = ReviewState.Review.name,
                    difficulty = 0.82,
                    stability = 3.0,
                    retrievability = 0.82,
                    scheduledDays = 3,
                    dueAt = reviewedAt.plusSeconds(SECONDS_PER_DAY),
                    lastReviewAt = reviewedAt,
                    reviewCount = 1,
                    lapseCount = 0,
                    firstReviewedAt = reviewedAt,
                    createdAt = reviewedAt,
                    updatedAt = reviewedAt,
                ),
            )

            val queue =
                repository
                    .observeTodayQueue(clock.now(), selectedBooks = setOf(BookCode.CET4), dailyNewLimit = 10)
                    .first()
            val canonicalCard = database.reviewDao().getCard(canonicalCardId)?.toModel()
            val malformedCard = database.reviewDao().getCard(malformedCardId)

            assertEquals(listOf(canonicalCardId), queue.dueItems.map { it.card.id })
            assertEquals(listOf("broken"), queue.dueItems.map { it.word.word })
            assertEquals(emptyList<String>(), queue.newItems.map { it.word.word })
            assertNotNull(canonicalCard)
            assertEquals(null, malformedCard)
            assertEquals(canonicalCardId, canonicalCard?.id)
            assertEquals(reviewedAt.plusSeconds(SECONDS_PER_DAY), canonicalCard?.dueAt)
            assertEquals(1, canonicalCard?.reviewCount)
        }

    @Test
    fun todayQueueSkipsLegacyLogRepairWhenReplayFails() =
        runTest {
            val reviewedAt = Instant.parse("2026-05-10T08:00:00Z")
            seedWord(database, clock, wordId = "legacy-word", value = "legacy", orderIndex = 0)
            val legacyId = cardId("legacy-word", BookCode.CET4.name)
            insertLegacyLog(database, legacyId, "legacy-word", reviewedAt)
            val failingRepository =
                newOfflineReviewRepository(
                    database = database,
                    scheduler = FailingScheduler(),
                    clockProvider = clock,
                )

            val queue =
                failingRepository
                    .observeTodayQueue(clock.now(), selectedBooks = setOf(BookCode.CET4), dailyNewLimit = 10)
                    .first()

            assertEquals(emptyList<String>(), queue.dueItems.map { it.word.word })
            assertEquals(emptyList<String>(), queue.newItems.map { it.word.word })
            assertEquals(null, database.reviewDao().getCard(legacyId))
        }

    @Test
    fun todayQueueRepairsLegacyLogsWhenSchedulerCanReplayThem() =
        runTest {
            val reviewedAt = Instant.parse("2026-05-15T07:00:00Z")
            seedWord(database, clock, wordId = "legacy-word", value = "legacy", orderIndex = 0)
            val legacyId = cardId("legacy-word", BookCode.CET4.name)
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "legacy-log-due",
                    cardId = legacyId,
                    wordId = "legacy-word",
                    bookCode = BookCode.CET4.name,
                    rating = ReviewRating.Again.wireName,
                    reviewedAt = reviewedAt,
                    localDay = "2026-05-15",
                    elapsedDays = null,
                    scheduledDaysBefore = 0,
                    scheduledDaysAfter = 1,
                    difficultyBefore = null,
                    difficultyAfter = 4.0,
                    stabilityBefore = null,
                    stabilityAfter = 1.0,
                    retrievabilityBefore = null,
                    retrievabilityAfter = 0.8,
                    durationMs = 500,
                    targetRetention = 0.01,
                    algorithm = "fsrs",
                    algorithmVersion = "legacy",
                ),
            )

            val queue =
                repository
                    .observeTodayQueue(clock.now(), selectedBooks = setOf(BookCode.CET4), dailyNewLimit = 10)
                    .first()
            val restored = database.reviewDao().getCard(legacyId)!!.toModel()

            assertEquals(emptyList<String>(), queue.newItems.map { it.word.word })
            assertEquals(listOf("legacy"), queue.dueItems.map { it.word.word })
            assertEquals(1, restored.reviewCount)
            assertEquals(reviewedAt, restored.lastReviewAt)
            assertEquals(reviewedAt.plusSeconds(SECONDS_PER_DAY), restored.dueAt)
        }

    @Test
    fun inspectAndRepairUseLogWordIdentityWhenCardIdIsMalformed() =
        runTest {
            val reviewedAt = Instant.parse("2026-05-10T08:00:00Z")
            seedWord(database, clock, wordId = "broken-card-word", value = "broken", orderIndex = 0)
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "broken-log",
                    cardId = "broken-card-id",
                    wordId = "broken-card-word",
                    bookCode = BookCode.CET4.name,
                    rating = ReviewRating.Good.wireName,
                    reviewedAt = reviewedAt,
                    localDay = "2026-05-10",
                    elapsedDays = 0,
                    scheduledDaysBefore = 0,
                    scheduledDaysAfter = 3,
                    difficultyBefore = null,
                    difficultyAfter = 0.82,
                    stabilityBefore = null,
                    stabilityAfter = 3.0,
                    retrievabilityBefore = null,
                    retrievabilityAfter = 0.82,
                    durationMs = 500,
                    targetRetention = 0.82,
                    algorithm = "fsrs",
                    algorithmVersion = "test",
                    stateAfter = ReviewState.Review.name,
                    dueAtAfter = reviewedAt.plusSeconds(3 * SECONDS_PER_DAY),
                ),
            )

            val reportBefore = repository.inspectReviewDataIntegrity()
            val repairResult = repository.repairReviewDataCache()
            val repairedCard =
                database.reviewDao().getCard(cardId("broken-card-word", BookCode.CET4.name))?.toModel()

            assertEquals(1, reportBefore.cardsWithLogs)
            assertEquals(1, reportBefore.missingCacheCount)
            assertEquals(1, reportBefore.missingDailyStatsCount)
            assertEquals(1, reportBefore.malformedLogCardCount)
            assertEquals(1, reportBefore.timelineConflictCardCount)
            assertEquals(2, reportBefore.manualReviewIssueCount)
            assertEquals(2, reportBefore.repairableIssueCount)
            assertEquals(4, reportBefore.issueCount)
            assertEquals(2, repairResult.repairedCount)
            assertEquals(0, repairResult.after.missingCacheCount)
            assertEquals(0, repairResult.after.missingDailyStatsCount)
            assertEquals(1, repairResult.after.malformedLogCardCount)
            assertEquals(1, repairResult.after.timelineConflictCardCount)
            assertEquals(2, repairResult.after.manualReviewIssueCount)
            assertEquals(0, repairResult.after.repairableIssueCount)
            assertNotNull(repairedCard)
            assertEquals(cardId("broken-card-word", BookCode.CET4.name), repairedCard?.id)
            assertEquals("broken-card-word", repairedCard?.wordId)
            assertEquals(BookCode.CET4, repairedCard?.bookCode)
            assertEquals(1, repairedCard?.reviewCount)
            assertEquals(reviewedAt, repairedCard?.lastReviewAt)
            assertEquals(reviewedAt.plusSeconds(3 * SECONDS_PER_DAY), repairedCard?.dueAt)
        }

    @Test
    fun getQueueItemCanonicalizesMalformedCardIdToLogicalCard() =
        runTest {
            val reviewedAt = Instant.parse("2026-05-10T08:00:00Z")
            val malformedCardId = "broken-card-id"
            val canonicalCardId = cardId("broken-card-word", BookCode.CET4.name)
            seedWord(database, clock, wordId = "broken-card-word", value = "broken", orderIndex = 0)
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "broken-log",
                    cardId = malformedCardId,
                    wordId = "broken-card-word",
                    bookCode = BookCode.CET4.name,
                    rating = ReviewRating.Good.wireName,
                    reviewedAt = reviewedAt,
                    localDay = "2026-05-10",
                    elapsedDays = 0,
                    scheduledDaysBefore = 0,
                    scheduledDaysAfter = 3,
                    difficultyBefore = null,
                    difficultyAfter = 0.82,
                    stabilityBefore = null,
                    stabilityAfter = 3.0,
                    retrievabilityBefore = null,
                    retrievabilityAfter = 0.82,
                    durationMs = 500,
                    targetRetention = 0.82,
                    algorithm = "fsrs",
                    algorithmVersion = "test",
                    stateAfter = ReviewState.Review.name,
                    dueAtAfter = reviewedAt.plusSeconds(3 * SECONDS_PER_DAY),
                ),
            )

            val item = repository.getQueueItem(malformedCardId)
            val canonicalCard = database.reviewDao().getCard(canonicalCardId)?.toModel()
            val malformedCard = database.reviewDao().getCard(malformedCardId)

            assertEquals(false, item.isNew)
            assertEquals(canonicalCardId, item.card.id)
            assertNotNull(canonicalCard)
            assertEquals(null, malformedCard)
            assertEquals(canonicalCardId, canonicalCard?.id)
            assertEquals(1, canonicalCard?.reviewCount)
            assertEquals(reviewedAt, canonicalCard?.lastReviewAt)
            assertEquals(reviewedAt.plusSeconds(3 * SECONDS_PER_DAY), canonicalCard?.dueAt)
        }

    @Test
    fun getQueueItemCanonicalizesMalformedCachedCardId() =
        runTest {
            val malformedCardId = "broken-card-id"
            val canonicalCardId = cardId("broken-card-word", BookCode.CET4.name)
            val dueAt = Instant.parse("2026-05-19T08:00:00Z")
            seedWord(database, clock, wordId = "broken-card-word", value = "broken", orderIndex = 0)
            database.reviewDao().upsertCard(
                ReviewCardEntity(
                    id = malformedCardId,
                    wordId = "broken-card-word",
                    bookCode = BookCode.CET4.name,
                    state = ReviewState.Review.name,
                    difficulty = 0.82,
                    stability = 3.0,
                    retrievability = 0.82,
                    scheduledDays = 3,
                    dueAt = dueAt,
                    lastReviewAt = Instant.parse("2026-05-16T08:00:00Z"),
                    reviewCount = 1,
                    lapseCount = 0,
                    firstReviewedAt = Instant.parse("2026-05-16T08:00:00Z"),
                    createdAt = Instant.parse("2026-05-16T08:00:00Z"),
                    updatedAt = Instant.parse("2026-05-16T08:00:00Z"),
                ),
            )

            val item = repository.getQueueItem(malformedCardId)
            val canonicalCard = database.reviewDao().getCard(canonicalCardId)?.toModel()
            val malformedCard = database.reviewDao().getCard(malformedCardId)

            assertEquals(false, item.isNew)
            assertEquals(canonicalCardId, item.card.id)
            assertNotNull(canonicalCard)
            assertEquals(null, malformedCard)
            assertEquals(canonicalCardId, canonicalCard?.id)
            assertEquals(dueAt, canonicalCard?.dueAt)
            assertEquals(1, canonicalCard?.reviewCount)
        }

    @Test
    fun replayLogsUsesLogicalCardIdentityAcrossRawCardIds() =
        runTest {
            val firstReviewedAt = Instant.parse("2026-05-16T08:00:00Z")
            val secondReviewedAt = Instant.parse("2026-05-16T09:00:00Z")
            val malformedCardId = "broken-card-id"
            seedWord(database, clock, wordId = "mixed-card-word", value = "mixed", orderIndex = 0)
            val canonicalCardId = cardId("mixed-card-word", BookCode.CET4.name)
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "mixed-log-1",
                    cardId = canonicalCardId,
                    wordId = "mixed-card-word",
                    bookCode = BookCode.CET4.name,
                    rating = ReviewRating.Good.wireName,
                    reviewedAt = firstReviewedAt,
                    localDay = "2026-05-16",
                    elapsedDays = null,
                    scheduledDaysBefore = 0,
                    scheduledDaysAfter = 3,
                    difficultyBefore = null,
                    difficultyAfter = 0.8,
                    stabilityBefore = null,
                    stabilityAfter = 3.0,
                    retrievabilityBefore = null,
                    retrievabilityAfter = 0.8,
                    durationMs = 500,
                    targetRetention = 0.8,
                    algorithm = "fsrs",
                    algorithmVersion = "test",
                    stateAfter = ReviewState.Review.name,
                    dueAtAfter = firstReviewedAt.plusSeconds(3 * SECONDS_PER_DAY),
                ),
            )
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "mixed-log-2",
                    cardId = malformedCardId,
                    wordId = "mixed-card-word",
                    bookCode = BookCode.CET4.name,
                    rating = ReviewRating.Hard.wireName,
                    reviewedAt = secondReviewedAt,
                    localDay = "2026-05-16",
                    elapsedDays = 0,
                    scheduledDaysBefore = 3,
                    scheduledDaysAfter = 4,
                    difficultyBefore = 0.8,
                    difficultyAfter = 0.9,
                    stabilityBefore = 3.0,
                    stabilityAfter = 4.0,
                    retrievabilityBefore = 0.8,
                    retrievabilityAfter = 0.9,
                    durationMs = 500,
                    targetRetention = 0.8,
                    algorithm = "fsrs",
                    algorithmVersion = "test",
                    stateAfter = ReviewState.Review.name,
                    dueAtAfter = secondReviewedAt.plusSeconds(4 * SECONDS_PER_DAY),
                ),
            )

            val replayed = repository.replayLogs(malformedCardId)

            assertEquals(canonicalCardId, replayed.id)
            assertEquals(2, replayed.reviewCount)
            assertEquals(secondReviewedAt, replayed.lastReviewAt)
            assertEquals(secondReviewedAt.plusSeconds(4 * SECONDS_PER_DAY), replayed.dueAt)
        }

    @Test
    fun repairReviewDataCacheUsesSingleTransactionNow() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            repository.submitFeedback(command(id, Instant.parse("2026-05-16T08:00:00Z"), ReviewRating.Good, 0.9))

            val countingClock = CountingClock()
            val countingRepository =
                newOfflineReviewRepository(
                    database = database,
                    scheduler = scheduler,
                    clockProvider = countingClock,
                )

            countingRepository.repairReviewDataCache()

            assertEquals(1, countingClock.nowCallCount)
        }

    @Test
    fun inspectAndRepairIncludeDailyStatsCacheMismatches() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val reviewedAt = Instant.parse("2026-05-16T08:00:00Z")

            repository.submitFeedback(command(id, reviewedAt, ReviewRating.Good, 0.9))
            database.statsDao().upsertDailyStats(
                DailyStatsEntity(
                    localDay = "2026-05-16",
                    newCount = 9,
                    reviewCount = 9,
                    againCount = 9,
                    hardCount = 9,
                    goodCount = 9,
                    easyCount = 9,
                    completedCount = 9,
                    recallAccuracy = 0.0,
                    passRate = 0.0,
                    estimatedMinutes = 9,
                    updatedAt = reviewedAt.minusSeconds(60),
                ),
            )

            val reportBefore = repository.inspectReviewDataIntegrity()
            val repairResult = repository.repairReviewDataCache()
            val reportAfter = repairResult.after

            assertEquals(1, reportBefore.dailyStatsDays)
            assertEquals(1, reportBefore.inconsistentDailyStatsCount)
            assertEquals(1, reportBefore.repairableIssueCount)
            assertEquals(1, repairResult.repairedCount)
            assertEquals(0, reportAfter.inconsistentDailyStatsCount)
            assertEquals(0, reportAfter.missingDailyStatsCount)
            assertEquals(0, reportAfter.issueCount)
            val cachedStats = database.exportDao().dailyStats().single()
            val derivedStats = database.exportDao().dailyStatsFromLogs().single()

            assertEquals(derivedStats.localDay, cachedStats.localDay)
            assertEquals(derivedStats.newCount, cachedStats.newCount)
            assertEquals(derivedStats.reviewCount, cachedStats.reviewCount)
            assertEquals(derivedStats.againCount, cachedStats.againCount)
            assertEquals(derivedStats.hardCount, cachedStats.hardCount)
            assertEquals(derivedStats.goodCount, cachedStats.goodCount)
            assertEquals(derivedStats.easyCount, cachedStats.easyCount)
            assertEquals(derivedStats.completedCount, cachedStats.completedCount)
            assertEquals(derivedStats.recallAccuracy, cachedStats.recallAccuracy, 0.0)
            assertEquals(derivedStats.passRate, cachedStats.passRate, 0.0)
            assertEquals(derivedStats.estimatedMinutes, cachedStats.estimatedMinutes)
        }

    @Test
    fun inspectAndRepairKeepPendingLogsAsManualIssues() =
        runTest {
            val reviewedAt = Instant.parse("2026-05-16T08:00:00Z")
            seedWord(database, clock, wordId = "pending-word", value = "pending", orderIndex = 0)
            val pendingCardId = cardId("pending-word", BookCode.CET4.name)
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "pending-log",
                    cardId = pendingCardId,
                    wordId = "pending-word",
                    bookCode = BookCode.CET4.name,
                    rating = ReviewRating.Good.wireName,
                    reviewedAt = reviewedAt,
                    localDay = "2026-05-16",
                    elapsedDays = null,
                    scheduledDaysBefore = 0,
                    scheduledDaysAfter = null,
                    difficultyBefore = null,
                    difficultyAfter = null,
                    stabilityBefore = null,
                    stabilityAfter = null,
                    retrievabilityBefore = null,
                    retrievabilityAfter = null,
                    durationMs = 500,
                    targetRetention = 0.9,
                    algorithm = "fsrs",
                    algorithmVersion = PENDING_REVIEW_LOG_VERSION,
                    stateAfter = null,
                    dueAtAfter = null,
                ),
            )
            val derivedStats = database.statsDao().dailyStatsFromLogs("2026-05-16", reviewedAt)
            database.statsDao().upsertDailyStats(checkNotNull(derivedStats))

            val reportBefore = repository.inspectReviewDataIntegrity()
            val repairResult = repository.repairReviewDataCache()
            val reportAfter = repairResult.after

            assertEquals(1, reportBefore.cardsWithLogs)
            assertEquals(1, reportBefore.missingCacheCount)
            assertEquals(1, reportBefore.legacyLogCardCount)
            assertEquals(1, reportBefore.manualReviewIssueCount)
            assertEquals(0, reportBefore.repairableIssueCount)
            assertEquals(0, repairResult.repairedCount)
            assertEquals(1, reportAfter.missingCacheCount)
            assertEquals(1, reportAfter.legacyLogCardCount)
            assertEquals(1, reportAfter.manualReviewIssueCount)
            assertEquals(0, reportAfter.repairableIssueCount)
            assertEquals(null, database.reviewDao().getCard(pendingCardId))
        }

    @Test
    fun repairReviewDataCacheCanonicalizesMixedRawCardIdsToSingleLogicalCard() =
        runTest {
            val firstReviewedAt = Instant.parse("2026-05-16T08:00:00Z")
            val secondReviewedAt = Instant.parse("2026-05-16T09:00:00Z")
            seedWord(database, clock, wordId = "mixed-card-word", value = "mixed", orderIndex = 0)
            val canonicalCardId = cardId("mixed-card-word", BookCode.CET4.name)
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "mixed-log-1",
                    cardId = canonicalCardId,
                    wordId = "mixed-card-word",
                    bookCode = BookCode.CET4.name,
                    rating = ReviewRating.Good.wireName,
                    reviewedAt = firstReviewedAt,
                    localDay = "2026-05-16",
                    elapsedDays = null,
                    scheduledDaysBefore = 0,
                    scheduledDaysAfter = 3,
                    difficultyBefore = null,
                    difficultyAfter = 0.8,
                    stabilityBefore = null,
                    stabilityAfter = 3.0,
                    retrievabilityBefore = null,
                    retrievabilityAfter = 0.8,
                    durationMs = 500,
                    targetRetention = 0.8,
                    algorithm = "fsrs",
                    algorithmVersion = "test",
                    stateAfter = ReviewState.Review.name,
                    dueAtAfter = firstReviewedAt.plusSeconds(3 * SECONDS_PER_DAY),
                ),
            )
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "mixed-log-2",
                    cardId = "broken-card-id",
                    wordId = "mixed-card-word",
                    bookCode = BookCode.CET4.name,
                    rating = ReviewRating.Hard.wireName,
                    reviewedAt = secondReviewedAt,
                    localDay = "2026-05-16",
                    elapsedDays = 0,
                    scheduledDaysBefore = 3,
                    scheduledDaysAfter = 4,
                    difficultyBefore = 0.8,
                    difficultyAfter = 0.9,
                    stabilityBefore = 3.0,
                    stabilityAfter = 4.0,
                    retrievabilityBefore = 0.8,
                    retrievabilityAfter = 0.9,
                    durationMs = 500,
                    targetRetention = 0.8,
                    algorithm = "fsrs",
                    algorithmVersion = "test",
                    stateAfter = ReviewState.Review.name,
                    dueAtAfter = secondReviewedAt.plusSeconds(4 * SECONDS_PER_DAY),
                ),
            )
            val derivedStats = database.statsDao().dailyStatsFromLogs("2026-05-16", secondReviewedAt)
            database.statsDao().upsertDailyStats(checkNotNull(derivedStats))

            val reportBefore = repository.inspectReviewDataIntegrity()
            val repairResult = repository.repairReviewDataCache()
            val reportAfter = repairResult.after
            val canonicalCard = database.reviewDao().getCard(canonicalCardId)?.toModel()
            val malformedCard = database.reviewDao().getCard("broken-card-id")

            assertEquals(1, reportBefore.cardsWithLogs)
            assertEquals(1, reportBefore.missingCacheCount)
            assertEquals(1, reportBefore.malformedLogCardCount)
            assertEquals(1, reportBefore.manualReviewIssueCount)
            assertEquals(1, reportBefore.repairableIssueCount)
            assertEquals(1, repairResult.repairedCount)
            assertEquals(0, reportAfter.missingCacheCount)
            assertEquals(1, reportAfter.malformedLogCardCount)
            assertEquals(1, reportAfter.manualReviewIssueCount)
            assertEquals(0, reportAfter.repairableIssueCount)
            assertNotNull(canonicalCard)
            assertEquals(null, malformedCard)
            assertEquals(2, canonicalCard?.reviewCount)
            assertEquals(secondReviewedAt, canonicalCard?.lastReviewAt)
            assertEquals(secondReviewedAt.plusSeconds(4 * SECONDS_PER_DAY), canonicalCard?.dueAt)
        }

    @Test
    fun inspectAndRepairKeepSnapshotTimelineConflictsAsManualIssues() =
        runTest {
            val earlierReviewAt = Instant.parse("2026-05-16T09:00:00Z")
            val laterReviewAt = Instant.parse("2026-05-16T10:00:00Z")
            seedWord(database, clock, wordId = "timeline-word", value = "timeline", orderIndex = 0)
            val timelineCardId = cardId("timeline-word", BookCode.CET4.name)
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "timeline-log-later",
                    cardId = timelineCardId,
                    wordId = "timeline-word",
                    bookCode = BookCode.CET4.name,
                    rating = ReviewRating.Good.wireName,
                    reviewedAt = laterReviewAt,
                    localDay = "2026-05-16",
                    elapsedDays = null,
                    scheduledDaysBefore = 0,
                    scheduledDaysAfter = 3,
                    difficultyBefore = null,
                    difficultyAfter = 0.8,
                    stabilityBefore = null,
                    stabilityAfter = 3.0,
                    retrievabilityBefore = null,
                    retrievabilityAfter = 0.8,
                    durationMs = 500,
                    targetRetention = 0.8,
                    algorithm = "fsrs",
                    algorithmVersion = "test",
                    stateAfter = ReviewState.Review.name,
                    dueAtAfter = laterReviewAt.plusSeconds(3 * SECONDS_PER_DAY),
                ),
            )
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "timeline-log-earlier",
                    cardId = timelineCardId,
                    wordId = "timeline-word",
                    bookCode = BookCode.CET4.name,
                    rating = ReviewRating.Hard.wireName,
                    reviewedAt = earlierReviewAt,
                    localDay = "2026-05-16",
                    elapsedDays = 0,
                    scheduledDaysBefore = 3,
                    scheduledDaysAfter = 4,
                    difficultyBefore = 0.8,
                    difficultyAfter = 0.9,
                    stabilityBefore = 3.0,
                    stabilityAfter = 4.0,
                    retrievabilityBefore = 0.8,
                    retrievabilityAfter = 0.9,
                    durationMs = 500,
                    targetRetention = 0.8,
                    algorithm = "fsrs",
                    algorithmVersion = "test",
                    stateAfter = ReviewState.Review.name,
                    dueAtAfter = earlierReviewAt.plusSeconds(4 * SECONDS_PER_DAY),
                ),
            )

            val repairResult = repository.repairReviewDataCache()
            val reportAfter = repairResult.after
            val repairedCard = database.reviewDao().getCard(timelineCardId)?.toModel()

            assertEquals(1, repairResult.timelineConflictCacheRebuiltCount)
            assertEquals(1, reportAfter.timelineConflictCardCount)
            assertEquals(1, reportAfter.manualReviewIssueCount)
            assertEquals(0, reportAfter.repairableIssueCount)
            assertNotNull(repairedCard)
            assertEquals(laterReviewAt, repairedCard?.lastReviewAt)
            assertEquals(2, repairedCard?.reviewCount)
        }
}

private const val PENDING_REVIEW_LOG_VERSION = "<pending>"
