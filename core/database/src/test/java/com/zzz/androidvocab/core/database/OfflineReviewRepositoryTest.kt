package com.zzz.androidvocab.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zzz.androidvocab.core.common.AppError
import com.zzz.androidvocab.core.common.AppException
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
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class OfflineReviewRepositoryTest {
    private lateinit var database: VocabDatabase
    private lateinit var repository: OfflineReviewRepository
    private val clock = FixedClock()
    private val scheduler = DeterministicScheduler()

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    VocabDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        repository =
            OfflineReviewRepository(
                database = database,
                reviewDao = database.reviewDao(),
                statsDao = database.statsDao(),
                scheduler = scheduler,
                clockProvider = clock,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replayLogsRebuildsCurrentCardFromReviewLogs() =
        runTest {
            seedWord()
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
            seedWord()
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val firstReviewAt = Instant.parse("2026-05-16T08:00:00Z")
            val secondReviewAt = Instant.parse("2026-05-16T09:00:00Z")

            repository.submitFeedback(command(id, firstReviewAt, ReviewRating.Good, 0.82))
            repository.submitFeedback(command(id, secondReviewAt, ReviewRating.Again, 0.91))
            val current = database.reviewDao().getCard(id)!!.toModel()

            database.reviewDao().deleteCard(id)
            val snapshotRepository =
                OfflineReviewRepository(
                    database = database,
                    reviewDao = database.reviewDao(),
                    statsDao = database.statsDao(),
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
            seedWord()
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val firstReviewAt = Instant.parse("2026-05-16T08:00:00Z")
            val secondReviewAt = Instant.parse("2026-05-16T09:00:00Z")

            repository.submitFeedback(command(id, firstReviewAt, ReviewRating.Good, 0.82))
            repository.submitFeedback(command(id, secondReviewAt, ReviewRating.Again, 0.91))
            val current = database.reviewDao().getCard(id)!!.toModel()

            database.reviewDao().deleteCard(id)
            val snapshotRepository =
                OfflineReviewRepository(
                    database = database,
                    reviewDao = database.reviewDao(),
                    statsDao = database.statsDao(),
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
            seedWord()
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
    fun todayQueueRepairsMissingReviewCardCacheBeforeLoading() =
        runTest {
            seedWord()
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
    fun inspectReviewDataIntegrityReportsMissingInconsistentAndLegacyCards() =
        runTest {
            val firstReviewAt = Instant.parse("2026-05-16T08:00:00Z")
            seedWord(wordId = "missing-word", value = "missing", orderIndex = 0)
            seedWord(wordId = "stale-word", value = "stale", orderIndex = 1)
            seedWord(wordId = "legacy-word", value = "legacy", orderIndex = 2)
            val missingId = cardId("missing-word", BookCode.CET4.name)
            val staleId = cardId("stale-word", BookCode.CET4.name)
            val legacyId = cardId("legacy-word", BookCode.CET4.name)

            repository.submitFeedback(command(missingId, firstReviewAt, ReviewRating.Good, 0.82))
            repository.submitFeedback(command(staleId, firstReviewAt, ReviewRating.Good, 0.82))
            database.reviewDao().deleteCard(missingId)
            val staleCard = database.reviewDao().getCard(staleId)!!.toModel()
            database.reviewDao().upsertCard(staleCard.copy(scheduledDays = staleCard.scheduledDays + 1).toEntity())
            insertLegacyLog(legacyId, "legacy-word", firstReviewAt)
            database.reviewDao().upsertCard(reviewCard("legacy-word", clock.now(), ReviewState.Review, 7, due = true))

            val report = repository.inspectReviewDataIntegrity()

            assertEquals(3, report.cardsWithLogs)
            assertEquals(1, report.missingCacheCount)
            assertEquals(1, report.inconsistentCacheCount)
            assertEquals(1, report.legacyLogCardCount)
            assertEquals(3, report.issueCount)
            assertEquals(2, report.repairableIssueCount)
        }

    @Test
    fun repairReviewDataCacheFixesSnapshotBackedMissingAndInconsistentCards() =
        runTest {
            val firstReviewAt = Instant.parse("2026-05-16T08:00:00Z")
            seedWord(wordId = "missing-word", value = "missing", orderIndex = 0)
            seedWord(wordId = "stale-word", value = "stale", orderIndex = 1)
            val missingId = cardId("missing-word", BookCode.CET4.name)
            val staleId = cardId("stale-word", BookCode.CET4.name)

            repository.submitFeedback(command(missingId, firstReviewAt, ReviewRating.Good, 0.82))
            repository.submitFeedback(command(staleId, firstReviewAt, ReviewRating.Easy, 0.91))
            val expectedMissing = database.reviewDao().getCard(missingId)!!.toModel()
            val expectedStale = database.reviewDao().getCard(staleId)!!.toModel()
            database.reviewDao().deleteCard(missingId)
            database.reviewDao().upsertCard(expectedStale.copy(lapseCount = 9).toEntity())

            val result = repository.repairReviewDataCache()
            val repairedMissing = database.reviewDao().getCard(missingId)!!.toModel()
            val repairedStale = database.reviewDao().getCard(staleId)!!.toModel()

            assertEquals(1, result.before.missingCacheCount)
            assertEquals(1, result.before.inconsistentCacheCount)
            assertEquals(2, result.before.repairableIssueCount)
            assertEquals(2, result.repairedCount)
            assertEquals(0, result.after.issueCount)
            assertEquals(0, result.after.repairableIssueCount)
            assertEquals(expectedMissing.scheduledDays, repairedMissing.scheduledDays)
            assertEquals(expectedMissing.dueAt, repairedMissing.dueAt)
            assertEquals(expectedStale.lapseCount, repairedStale.lapseCount)
            assertEquals(expectedStale.scheduledDays, repairedStale.scheduledDays)
        }

    @Test
    fun repairReviewDataCacheDoesNotRewriteLegacyLogsWithoutSnapshots() =
        runTest {
            val reviewedAt = Instant.parse("2026-05-16T08:00:00Z")
            seedWord(wordId = "legacy-word", value = "legacy", orderIndex = 0)
            val legacyId = cardId("legacy-word", BookCode.CET4.name)
            insertLegacyLog(legacyId, "legacy-word", reviewedAt)

            val result = repository.repairReviewDataCache()

            assertEquals(1, result.before.cardsWithLogs)
            assertEquals(1, result.before.missingCacheCount)
            assertEquals(1, result.before.legacyLogCardCount)
            assertEquals(2, result.before.issueCount)
            assertEquals(0, result.before.repairableIssueCount)
            assertEquals(0, result.repairedCount)
            assertEquals(1, result.after.missingCacheCount)
            assertEquals(null, database.reviewDao().getCard(legacyId))
        }

    @Test
    fun inspectReviewDataIntegrityReportsOrphanLogs() =
        runTest {
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "orphan-log",
                    cardId = cardId("orphan-word", BookCode.CET4.name),
                    wordId = "orphan-word",
                    bookCode = BookCode.CET4.name,
                    rating = ReviewRating.Good.wireName,
                    reviewedAt = Instant.parse("2026-05-16T08:00:00Z"),
                    localDay = "2026-05-16",
                    elapsedDays = null,
                    scheduledDaysBefore = 0,
                    scheduledDaysAfter = 1,
                    difficultyBefore = null,
                    difficultyAfter = 5.0,
                    stabilityBefore = null,
                    stabilityAfter = 1.0,
                    retrievabilityBefore = null,
                    retrievabilityAfter = 0.9,
                    durationMs = 500,
                    targetRetention = 0.9,
                    algorithm = "fsrs",
                    algorithmVersion = "test",
                ),
            )

            val report = repository.inspectReviewDataIntegrity()

            assertEquals(0, report.cardsWithLogs)
            assertEquals(1, report.orphanLogCount)
            assertEquals(1, report.issueCount)
            assertEquals(0, report.repairableIssueCount)
        }

    @Test
    fun replayLogsFallsBackToSchedulerWhenLogSnapshotsAreMissing() =
        runTest {
            seedWord()
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val reviewedAt = Instant.parse("2026-05-16T08:00:00Z")
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "legacy-log",
                    cardId = id,
                    wordId = WORD_ID,
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

            val replayed = repository.replayLogs(id)

            assertEquals(1, replayed.reviewCount)
            assertEquals(0, replayed.lapseCount)
            assertEquals(0.83, replayed.difficulty)
            assertEquals(reviewedAt, replayed.lastReviewAt)
        }

    @Test
    fun replayLogsDisablesFuzzingForLegacyLogs() =
        runTest {
            RecordingScheduler.enableFuzzingValues.clear()
            seedWord()
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val reviewedAt = Instant.parse("2026-05-16T08:00:00Z")
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "legacy-log-no-fuzz",
                    cardId = id,
                    wordId = WORD_ID,
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
            val recordingRepository =
                OfflineReviewRepository(
                    database = database,
                    reviewDao = database.reviewDao(),
                    statsDao = database.statsDao(),
                    scheduler = RecordingScheduler(),
                    clockProvider = clock,
                )

            recordingRepository.replayLogs(id)

            assertEquals(listOf(false), RecordingScheduler.enableFuzzingValues)
        }

    @Test
    fun todayCompletedCountUsesEveryReviewLog() =
        runTest {
            seedWord()
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val firstReviewAt = Instant.parse("2026-05-16T08:00:00Z")
            val secondReviewAt = Instant.parse("2026-05-16T09:00:00Z")

            repository.submitFeedback(command(id, firstReviewAt, ReviewRating.Good, 0.9))
            repository.submitFeedback(command(id, secondReviewAt, ReviewRating.Again, 0.9))

            assertEquals(2, database.statsDao().observeDailyCompleted("2026-05-16").first())
            assertEquals(
                2,
                database
                    .statsDao()
                    .observeDailyActivityRows("2026-05-16", "2026-05-16")
                    .first()
                    .single()
                    .reviewCount,
            )
            assertEquals(1, database.statsDao().observeDailyNewCount("2026-05-16").first())
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
            seedWord()
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val firstReviewAt = Instant.parse("2026-05-16T08:00:00Z")
            val secondReviewAt = Instant.parse("2026-05-17T08:00:00Z")

            repository.submitFeedback(command(id, firstReviewAt, ReviewRating.Good, 0.9))
            repository.submitFeedback(command(id, secondReviewAt, ReviewRating.Hard, 0.9))

            assertEquals(1, database.statsDao().dailyNewCount("2026-05-16"))
            assertEquals(0, database.statsDao().dailyNewCount("2026-05-17"))

            val cachedStats = database.exportDao().dailyStats().associateBy { it.localDay }
            val firstDay = cachedStats.getValue("2026-05-16")
            val secondDay = cachedStats.getValue("2026-05-17")

            assertEquals(1, firstDay.newCount)
            assertEquals(0, firstDay.reviewCount)
            assertEquals(1, firstDay.completedCount)
            assertEquals(0, secondDay.newCount)
            assertEquals(1, secondDay.reviewCount)
            assertEquals(1, secondDay.completedCount)
            assertEquals(1, secondDay.hardCount)
        }

    @Test
    fun duplicateReviewLogRollsBackCardAndStatsUpdates() =
        runTest {
            seedWord()
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
    fun schedulerFailureDoesNotWriteLearningData() =
        runTest {
            seedWord()
            val id = cardId(WORD_ID, BookCode.CET4.name)
            val failingRepository =
                OfflineReviewRepository(
                    database = database,
                    reviewDao = database.reviewDao(),
                    statsDao = database.statsDao(),
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
            assertEquals(null, database.reviewDao().getCard(id))
            assertEquals(0, database.statsDao().dailyCompleted("2026-05-16"))
        }

    @Test
    fun todayQueuePrioritizesRecentlyDifficultDueCardsWhenDueAtTies() =
        runTest {
            val now = clock.now()
            seedWord(wordId = "easy-word", value = "easy", orderIndex = 0)
            seedWord(wordId = "hard-word", value = "hard", orderIndex = 1)
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

    private suspend fun seedWord(
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

    private fun command(
        cardId: String,
        reviewedAt: Instant,
        rating: ReviewRating,
        targetRetention: Double,
    ) = SubmitFeedbackCommand(
        cardId = cardId,
        rating = rating,
        reviewedAt = reviewedAt,
        durationMs = 500,
        targetRetention = targetRetention,
    )

    private fun reviewCard(
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

    private suspend fun insertLegacyLog(
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

    private class FixedClock : ClockProvider {
        private val fixedNow = Instant.parse("2026-05-16T07:00:00Z")
        private val fixedZone = ZoneId.of("Asia/Shanghai")

        override fun now(): Instant = fixedNow

        override fun zoneId(): ZoneId = fixedZone
    }

    private class DeterministicScheduler : ReviewScheduler {
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

    private class RecordingScheduler : ReviewScheduler {
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

    private class FailingScheduler : ReviewScheduler {
        override val algorithm: SchedulerAlgorithm = SchedulerAlgorithm.Fsrs

        override fun schedule(input: ScheduleInput): ScheduleResult = error("scheduler unavailable")
    }

    private companion object {
        const val WORD_ID = "word-1"
        const val SECONDS_PER_DAY = 86_400L
    }
}
