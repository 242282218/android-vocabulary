package com.zzz.androidvocab.core.database

import com.zzz.androidvocab.core.common.AppError
import com.zzz.androidvocab.core.common.AppException
import com.zzz.androidvocab.core.common.cardId
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.ReviewState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class OfflineReviewRepositoryStatsAndRollbackTest {
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
    fun todayCompletedCountUsesEveryReviewLog() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val firstReviewAt = Instant.parse("2026-05-16T08:00:00Z")
            val secondReviewAt = Instant.parse("2026-05-16T09:00:00Z")

            repository.submitFeedback(command(id, firstReviewAt, ReviewRating.Good, 0.9))
            repository.submitFeedback(command(id, secondReviewAt, ReviewRating.Again, 0.9))

            assertEquals(
                2,
                database
                    .exportDao()
                    .dailyStatsFromLogs()
                    .find { it.localDay == "2026-05-16" }
                    ?.completedCount,
            )
            assertEquals(
                2,
                database
                    .statsDao()
                    .observeDailyActivityRows("2026-05-16", "2026-05-16", listOf(BookCode.CET4.name))
                    .first()
                    .single()
                    .reviewCount,
            )
            assertEquals(
                1,
                database
                    .exportDao()
                    .dailyStatsFromLogs()
                    .find { it.localDay == "2026-05-16" }
                    ?.newCount,
            )
            val cachedStats = database.exportDao().dailyStats().single()
            assertEquals("2026-05-16", cachedStats.localDay)
            assertEquals(1, cachedStats.newCount)
            assertEquals(1, cachedStats.reviewCount)
            assertEquals(2, cachedStats.completedCount)
            assertEquals(1, cachedStats.againCount)
            assertEquals(1, cachedStats.goodCount)
        }

    @Test
    fun laterDayReviewDoesNotCountAsNewAgain() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val firstReviewAt = Instant.parse("2026-05-16T08:00:00Z")
            val secondReviewAt = Instant.parse("2026-05-17T08:00:00Z")

            repository.submitFeedback(command(id, firstReviewAt, ReviewRating.Good, 0.9))
            repository.submitFeedback(command(id, secondReviewAt, ReviewRating.Hard, 0.9))

            val cachedStats = database.exportDao().dailyStats().associateBy { it.localDay }
            val firstDay = cachedStats.getValue("2026-05-16")
            val secondDay = cachedStats.getValue("2026-05-17")
            val aggregateStats = database.exportDao().dailyStatsFromLogs().associateBy { it.localDay }

            assertEquals(1, firstDay.newCount)
            assertEquals(0, firstDay.reviewCount)
            assertEquals(1, firstDay.completedCount)
            assertEquals(0, secondDay.newCount)
            assertEquals(1, secondDay.reviewCount)
            assertEquals(1, secondDay.completedCount)
            assertEquals(1, secondDay.hardCount)
            assertEquals(firstDay.newCount, aggregateStats.getValue("2026-05-16").newCount)
            assertEquals(secondDay.newCount, aggregateStats.getValue("2026-05-17").newCount)
        }

    @Test
    fun submitFeedbackStoresStatsUnderLocalDayAtUtcBoundary() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val reviewedAt = Instant.parse("2026-05-16T16:30:00Z")

            repository.submitFeedback(command(id, reviewedAt, ReviewRating.Good, 0.9))

            val log =
                database
                    .reviewDao()
                    .getLogs(id)
                    .single()
                    .toModel()
            assertEquals("2026-05-17", log.localDay)
            assertEquals(
                null,
                database
                    .exportDao()
                    .dailyStatsFromLogs()
                    .find { it.localDay == "2026-05-16" }
                    ?.completedCount,
            )
            assertEquals(
                1,
                database
                    .exportDao()
                    .dailyStatsFromLogs()
                    .find { it.localDay == "2026-05-17" }
                    ?.completedCount,
            )

            val cachedStats = database.exportDao().dailyStats().single()
            assertEquals("2026-05-17", cachedStats.localDay)
            assertEquals(1, cachedStats.newCount)
            assertEquals(1, cachedStats.completedCount)
        }

    @Test
    fun duplicateReviewLogRollsBackCardAndStatsUpdates() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val reviewedAt = Instant.parse("2026-05-16T08:00:00Z")

            repository.submitFeedback(command(id, reviewedAt, ReviewRating.Good, 0.9))
            val cardAfterFirstSubmit = database.reviewDao().getCard(id)!!.toModel()

            val error =
                runCatching {
                    repository.submitFeedback(command(id, reviewedAt, ReviewRating.Hard, 0.7))
                }.exceptionOrNull()

            assertTrue((error as AppException).error is AppError.DatabaseWriteFailed)
            assertEquals(listOf(ReviewRating.Good), database.reviewDao().getLogs(id).map { it.toModel().rating })

            val cardAfterDuplicateSubmit = database.reviewDao().getCard(id)!!.toModel()
            assertEquals(cardAfterFirstSubmit.reviewCount, cardAfterDuplicateSubmit.reviewCount)
            assertEquals(cardAfterFirstSubmit.scheduledDays, cardAfterDuplicateSubmit.scheduledDays)
            assertEquals(cardAfterFirstSubmit.difficulty, cardAfterDuplicateSubmit.difficulty)
            assertEquals(cardAfterFirstSubmit.updatedAt, cardAfterDuplicateSubmit.updatedAt)

            val cachedStats = database.exportDao().dailyStats().single()
            assertEquals(1, cachedStats.completedCount)
            assertEquals(1, cachedStats.newCount)
            assertEquals(0, cachedStats.reviewCount)
            assertEquals(1, cachedStats.goodCount)
            assertEquals(0, cachedStats.hardCount)
        }

    @Test
    fun staleCardVersionRejectsWriteAndKeepsLearningDataClean() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val initialCard = repository.getQueueItem(id).card
            val firstReviewAt = Instant.parse("2026-05-16T08:00:00Z")
            val staleSecondReviewAt = Instant.parse("2026-05-16T08:00:05Z")

            repository.submitFeedback(
                command(
                    id,
                    firstReviewAt,
                    ReviewRating.Good,
                    0.9,
                    expectedLastReviewAt = initialCard.lastReviewAt,
                    expectedReviewCount = initialCard.reviewCount,
                ),
            )
            val cardAfterFirstSubmit = database.reviewDao().getCard(id)!!.toModel()

            val error =
                runCatching {
                    repository.submitFeedback(
                        command(
                            id,
                            staleSecondReviewAt,
                            ReviewRating.Hard,
                            0.9,
                            expectedLastReviewAt = initialCard.lastReviewAt,
                            expectedReviewCount = initialCard.reviewCount,
                        ),
                    )
                }.exceptionOrNull()

            assertTrue((error as AppException).error is AppError.DatabaseWriteFailed)
            assertEquals(
                "当前卡片状态已更新，请刷新后重试",
                (error.error as AppError.DatabaseWriteFailed).reason,
            )
            assertEquals(listOf(ReviewRating.Good), database.reviewDao().getLogs(id).map { it.toModel().rating })

            val cardAfterRejectedSubmit = database.reviewDao().getCard(id)!!.toModel()
            assertEquals(cardAfterFirstSubmit.reviewCount, cardAfterRejectedSubmit.reviewCount)
            assertEquals(cardAfterFirstSubmit.updatedAt, cardAfterRejectedSubmit.updatedAt)
            assertEquals(cardAfterFirstSubmit.lastReviewAt, cardAfterRejectedSubmit.lastReviewAt)

            val cachedStats = database.exportDao().dailyStats().single()
            assertEquals(1, cachedStats.completedCount)
            assertEquals(1, cachedStats.newCount)
            assertEquals(0, cachedStats.reviewCount)
            assertEquals(1, cachedStats.goodCount)
            assertEquals(0, cachedStats.hardCount)
        }

    @Test
    fun schedulerFailureDoesNotWriteLearningData() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val failingRepository =
                newOfflineReviewRepository(
                    database = database,
                    scheduler = FailingScheduler(),
                    clockProvider = clock,
                )

            val error =
                runCatching {
                    failingRepository.submitFeedback(
                        command(
                            cardId = id,
                            reviewedAt = Instant.parse("2026-05-16T08:00:00Z"),
                            rating = ReviewRating.Good,
                            targetRetention = 0.9,
                        ),
                    )
                }.exceptionOrNull()

            assertTrue((error as AppException).error is AppError.SchedulerFailed)
            assertEquals(emptyList<ReviewRating>(), database.reviewDao().getLogs(id).map { it.toModel().rating })
            assertEquals(null, database.reviewDao().getCard(id))
            assertEquals(
                null,
                database
                    .exportDao()
                    .dailyStatsFromLogs()
                    .find { it.localDay == "2026-05-16" }
                    ?.completedCount,
            )
        }

    @Test
    fun submitFeedbackInsertsFinalLogBeforeReviewCard() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val recordingReviewDao = RecordingPersistenceOrderReviewDao(database.reviewDao())
            val operations = recordingReviewDao.operations
            val recordingRepository =
                OfflineReviewRepository(
                    database = database,
                    reviewDao = recordingReviewDao,
                    statsDao = database.statsDao(),
                    scheduler = OperationRecordingScheduler(scheduler, operations),
                    clockProvider = clock,
                    integrityService = newReviewDataIntegrityService(database, scheduler, clock),
                )

            recordingRepository.submitFeedback(command(id, clock.now(), ReviewRating.Good, 0.9))

            assertEquals(
                listOf("schedule", "insertLog", "upsertCard"),
                operations.filter { it in setOf("insertLog", "schedule", "upsertCard") },
            )
            val storedLog = database.reviewDao().getLogs(id).single()
            assertEquals("test", storedLog.algorithmVersion)
            assertEquals(ReviewState.Review.name, storedLog.stateAfter)
            assertTrue(storedLog.dueAtAfter != null)
        }

    @Test
    fun cardWriteFailureRollsBackReviewLogAndStats() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val failingRepository =
                OfflineReviewRepository(
                    database = database,
                    reviewDao = FailingUpsertCardReviewDao(database.reviewDao()),
                    statsDao = database.statsDao(),
                    scheduler = scheduler,
                    clockProvider = clock,
                    integrityService = newReviewDataIntegrityService(database, scheduler, clock),
                )

            val error =
                runCatching {
                    failingRepository.submitFeedback(command(id, clock.now(), ReviewRating.Good, 0.9))
                }.exceptionOrNull()

            assertTrue((error as AppException).error is AppError.DatabaseWriteFailed)
            assertEquals(emptyList<ReviewRating>(), database.reviewDao().getLogs(id).map { it.toModel().rating })
            assertEquals(null, database.reviewDao().getCard(id))
            assertEquals(
                null,
                database
                    .exportDao()
                    .dailyStatsFromLogs()
                    .find { it.localDay == "2026-05-16" }
                    ?.completedCount,
            )
        }

    @Test
    fun earlierReviewedAtThanLastReviewAtRejectsWriteAndKeepsStatsClean() =
        runTest {
            seedWord(database, clock)
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val firstReviewAt = Instant.parse("2026-05-16T10:00:00Z")
            val earlierReviewAt = Instant.parse("2026-05-16T09:00:00Z")

            repository.submitFeedback(command(id, firstReviewAt, ReviewRating.Good, 0.9))
            val cardAfterFirstSubmit = database.reviewDao().getCard(id)!!.toModel()

            val error =
                runCatching {
                    repository.submitFeedback(command(id, earlierReviewAt, ReviewRating.Hard, 0.9))
                }.exceptionOrNull()

            assertTrue((error as AppException).error is AppError.DatabaseWriteFailed)
            assertEquals(
                "系统时间早于上次复习，请校准设备时间后再试",
                (error.error as AppError.DatabaseWriteFailed).reason,
            )
            assertEquals(listOf(ReviewRating.Good), database.reviewDao().getLogs(id).map { it.toModel().rating })

            val cardAfterRejectedSubmit = database.reviewDao().getCard(id)!!.toModel()
            assertEquals(cardAfterFirstSubmit.reviewCount, cardAfterRejectedSubmit.reviewCount)
            assertEquals(cardAfterFirstSubmit.lastReviewAt, cardAfterRejectedSubmit.lastReviewAt)
            assertEquals(cardAfterFirstSubmit.updatedAt, cardAfterRejectedSubmit.updatedAt)

            val cachedStats = database.exportDao().dailyStats().single()
            assertEquals("2026-05-16", cachedStats.localDay)
            assertEquals(1, cachedStats.completedCount)
            assertEquals(1, cachedStats.newCount)
            assertEquals(0, cachedStats.reviewCount)
            assertEquals(1, cachedStats.goodCount)
            assertEquals(0, cachedStats.hardCount)
        }

    @Test
    fun todayQueuePrioritizesRecentlyDifficultDueCardsWhenDueAtTies() =
        runTest {
            val now = clock.now()
            seedWord(database, clock, wordId = "easy-word", value = "easy", orderIndex = 0)
            seedWord(database, clock, wordId = "hard-word", value = "hard", orderIndex = 1)
            database.reviewDao().upsertCard(
                reviewCard("easy-word", now, ReviewState.Review, scheduledDays = 3, due = true),
            )
            database.reviewDao().upsertCard(
                reviewCard("hard-word", now, ReviewState.Review, scheduledDays = 3, due = true),
            )
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "log-hard",
                    cardId = cardId("hard-word", BookCode.CET4.name),
                    wordId = "hard-word",
                    bookCode = BookCode.CET4.name,
                    rating = ReviewRating.Again.wireName,
                    reviewedAt = now.minusSeconds(3_600),
                    localDay = "2026-05-16",
                    elapsedDays = 0,
                    scheduledDaysBefore = 3,
                    scheduledDaysAfter = 1,
                    difficultyBefore = 5.0,
                    difficultyAfter = 6.0,
                    stabilityBefore = 2.0,
                    stabilityAfter = 1.0,
                    retrievabilityBefore = 0.7,
                    retrievabilityAfter = 0.9,
                    durationMs = 800,
                    targetRetention = 0.9,
                    algorithm = "fsrs",
                    algorithmVersion = "test",
                ),
            )

            val queue =
                repository
                    .observeTodayQueue(now, selectedBooks = setOf(BookCode.CET4), dailyNewLimit = 0)
                    .first()

            assertEquals(listOf("hard", "easy"), queue.dueItems.map { it.word.word })
        }
}
