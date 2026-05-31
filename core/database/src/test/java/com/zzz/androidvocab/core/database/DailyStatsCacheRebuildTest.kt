package com.zzz.androidvocab.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zzz.androidvocab.core.common.cardId
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewRating
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class DailyStatsCacheRebuildTest {
    private lateinit var database: VocabDatabase

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    VocabDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun rebuildDailyStatsCacheDerivesCountsFromLogsAndClearsStaleRows() =
        runTest {
            val updatedAt = Instant.parse("2026-05-16T10:00:00Z")
            seedWordMembership(updatedAt)
            database.statsDao().upsertDailyStats(staleDailyStats(updatedAt))
            database.reviewDao().insertLog(
                reviewLog(
                    id = "log-1",
                    rating = ReviewRating.Good,
                    reviewedAt = Instant.parse("2026-05-16T08:00:00Z"),
                    durationMs = 30_000,
                ),
            )
            database.reviewDao().insertLog(
                reviewLog(
                    id = "log-2",
                    rating = ReviewRating.Again,
                    reviewedAt = Instant.parse("2026-05-16T09:00:00Z"),
                    durationMs = 31_000,
                ),
            )

            val observedAggregate =
                database
                    .statsDao()
                    .observeDailyStatsFromLogs("2026-05-16", listOf(BookCode.CET4.name))
                    .first()
            assertEquals(1, observedAggregate?.newCount)
            val aggregate = database.exportDao().dailyStatsFromLogs().single()
            assertEquals(1, aggregate.newCount)
            assertEquals(1, aggregate.reviewCount)
            assertEquals(2, aggregate.completedCount)
            assertEquals(0.5, aggregate.passRate, 0.0)
            assertEquals(2, aggregate.estimatedMinutes)

            val rebuiltDays = database.statsDao().rebuildDailyStatsCache(updatedAt)

            val stats = database.exportDao().dailyStats().single()
            assertEquals(1, rebuiltDays)
            assertEquals("2026-05-16", stats.localDay)
            assertEquals(1, stats.newCount)
            assertEquals(1, stats.reviewCount)
            assertEquals(2, stats.completedCount)
            assertEquals(1, stats.goodCount)
            assertEquals(1, stats.againCount)
            assertEquals(0.5, stats.recallAccuracy, 0.0)
            assertEquals(0.5, stats.passRate, 0.0)
            assertEquals(2, stats.estimatedMinutes)
            assertEquals(updatedAt, stats.updatedAt)
        }

    @Test
    fun rebuildDailyStatsCacheCountsOneNewCardWhenFirstLogsShareTimestamp() =
        runTest {
            val updatedAt = Instant.parse("2026-05-16T10:00:00Z")
            seedWordMembership(updatedAt)
            database.reviewDao().insertLog(
                reviewLog(
                    id = "log-a",
                    rating = ReviewRating.Good,
                    reviewedAt = Instant.parse("2026-05-16T08:00:00Z"),
                    durationMs = 30_000,
                ),
            )
            database.reviewDao().insertLog(
                reviewLog(
                    id = "log-b",
                    rating = ReviewRating.Hard,
                    reviewedAt = Instant.parse("2026-05-16T08:00:00Z"),
                    durationMs = 20_000,
                ),
            )

            val aggregate = database.exportDao().dailyStatsFromLogs().single()
            assertEquals(1, aggregate.newCount)
            assertEquals(1, aggregate.reviewCount)
            assertEquals(2, aggregate.completedCount)
            assertEquals(1, aggregate.goodCount)
            assertEquals(1, aggregate.hardCount)

            val rebuiltDays = database.statsDao().rebuildDailyStatsCache(updatedAt)

            val stats = database.exportDao().dailyStats().single()
            assertEquals(1, rebuiltDays)
            assertEquals(1, stats.newCount)
            assertEquals(1, stats.reviewCount)
            assertEquals(2, stats.completedCount)
        }

    @Test
    fun rebuildDailyStatsCacheTreatsMalformedCardIdAsSameLogicalCard() =
        runTest {
            val updatedAt = Instant.parse("2026-05-16T10:00:00Z")
            seedWordMembership(updatedAt)
            database.reviewDao().insertLog(
                reviewLog(
                    id = "log-valid-card-id",
                    rating = ReviewRating.Good,
                    reviewedAt = Instant.parse("2026-05-16T08:00:00Z"),
                    durationMs = 30_000,
                ),
            )
            database.reviewDao().insertLog(
                reviewLog(
                    id = "log-malformed-card-id",
                    cardIdOverride = "broken-card-id",
                    rating = ReviewRating.Hard,
                    reviewedAt = Instant.parse("2026-05-16T09:00:00Z"),
                    durationMs = 20_000,
                ),
            )

            val observedAggregate =
                database
                    .statsDao()
                    .observeDailyStatsFromLogs("2026-05-16", listOf(BookCode.CET4.name))
                    .first()
            val aggregate = database.exportDao().dailyStatsFromLogs().single()

            assertEquals(1, observedAggregate?.newCount)
            assertEquals(1, observedAggregate?.reviewCount)
            assertEquals(1, aggregate.newCount)
            assertEquals(1, aggregate.reviewCount)
            assertEquals(2, aggregate.completedCount)
            assertEquals(1, aggregate.goodCount)
            assertEquals(1, aggregate.hardCount)

            val rebuiltDays = database.statsDao().rebuildDailyStatsCache(updatedAt)
            val stats = database.exportDao().dailyStats().single()

            assertEquals(1, rebuiltDays)
            assertEquals(1, stats.newCount)
            assertEquals(1, stats.reviewCount)
            assertEquals(2, stats.completedCount)
        }

    @Test
    fun rebuildDailyStatsCacheClearsCacheWhenLogsAreEmpty() =
        runTest {
            val updatedAt = Instant.parse("2026-05-16T10:00:00Z")
            database.statsDao().upsertDailyStats(staleDailyStats(updatedAt))

            val rebuiltDays = database.statsDao().rebuildDailyStatsCache(updatedAt)

            assertEquals(0, rebuiltDays)
            assertEquals(emptyList<DailyStatsEntity>(), database.exportDao().dailyStats())
            assertEquals(emptyList<ReviewDailyStatsView>(), database.exportDao().dailyStatsFromLogs())
        }

    @Test
    fun rebuildDailyStatsCacheIgnoresOrphanReviewLogs() =
        runTest {
            val updatedAt = Instant.parse("2026-05-16T10:00:00Z")
            database.statsDao().upsertDailyStats(staleDailyStats(updatedAt))
            database.reviewDao().insertLog(
                reviewLog(
                    id = "orphan-log",
                    wordId = "orphan-word",
                    rating = ReviewRating.Good,
                    reviewedAt = Instant.parse("2026-05-16T08:00:00Z"),
                    durationMs = 30_000,
                ),
            )

            val rebuiltDays = database.statsDao().rebuildDailyStatsCache(updatedAt)

            assertEquals(0, rebuiltDays)
            assertEquals(emptyList<DailyStatsEntity>(), database.exportDao().dailyStats())
        }

    @Test
    fun cardStatsQueriesIgnoreCardsWithoutMembership() =
        runTest {
            val now = Instant.parse("2026-05-16T10:00:00Z")
            seedWordMembership(now)
            database.reviewDao().upsertCard(reviewCard("valid-card", WORD_ID, now))
            database.reviewDao().upsertCard(reviewCard("orphan-card", "orphan-word", now))

            val dueCards =
                database
                    .statsDao()
                    .observeDueCards(now.plusSeconds(86_400), listOf(BookCode.CET4.name))
                    .first()
            val reviewedCards =
                database
                    .statsDao()
                    .observeReviewedCards(now.minusSeconds(86_400), listOf(BookCode.CET4.name))
                    .first()

            assertEquals(1, dueCards.size)
            assertEquals(listOf("valid-card"), reviewedCards.map { it.id })
        }

    @Test
    fun reviewLoadQueryFiltersSelectedBooks() =
        runTest {
            val now = Instant.parse("2026-05-16T10:00:00Z")
            seedWordMembership(now, wordId = "cet4-word", value = "access", bookCode = BookCode.CET4)
            seedWordMembership(now, wordId = "toefl-word", value = "academic", bookCode = BookCode.TOEFL)
            database.reviewDao().upsertCard(reviewCard("cet4-card", "cet4-word", now, BookCode.CET4))
            database.reviewDao().upsertCard(reviewCard("toefl-card", "toefl-word", now, BookCode.TOEFL))

            val dueCards =
                database
                    .statsDao()
                    .observeDueCards(now.plusSeconds(86_400), listOf(BookCode.CET4.name))
                    .first()

            assertEquals(1, dueCards.size)
        }

    @Test
    fun todayStatsQueriesFilterSelectedBooks() =
        runTest {
            val now = Instant.parse("2026-05-16T10:00:00Z")
            seedWordMembership(now, wordId = "cet4-word", value = "access", bookCode = BookCode.CET4)
            seedWordMembership(now, wordId = "toefl-word", value = "academic", bookCode = BookCode.TOEFL)
            database.reviewDao().insertLog(
                reviewLog(
                    id = "cet4-log",
                    wordId = "cet4-word",
                    bookCode = BookCode.CET4,
                    rating = ReviewRating.Good,
                    reviewedAt = Instant.parse("2026-05-16T08:00:00Z"),
                    durationMs = 30_000,
                ),
            )
            database.reviewDao().insertLog(
                reviewLog(
                    id = "toefl-log",
                    wordId = "toefl-word",
                    bookCode = BookCode.TOEFL,
                    rating = ReviewRating.Again,
                    reviewedAt = Instant.parse("2026-05-16T09:00:00Z"),
                    durationMs = 90_000,
                ),
            )

            val todayStats =
                database
                    .statsDao()
                    .observeDailyStatsFromLogs("2026-05-16", listOf(BookCode.CET4.name))
                    .first()
            val averageDuration =
                database
                    .statsDao()
                    .observeAverageDurationMs(
                        startDay = "2026-05-16",
                        endDay = "2026-05-16",
                        bookCodes = listOf(BookCode.CET4.name),
                    ).first()

            requireNotNull(todayStats)
            requireNotNull(averageDuration)
            assertEquals(1, todayStats.newCount)
            assertEquals(0, todayStats.reviewCount)
            assertEquals(1, todayStats.goodCount)
            assertEquals(0, todayStats.againCount)
            assertEquals(30_000.0, averageDuration, 0.0)
        }

    @Test
    fun scopedStatsQueriesFilterSelectedBooks() =
        runTest {
            val now = Instant.parse("2026-05-16T10:00:00Z")
            seedWordMembership(now, wordId = "cet4-word", value = "access", bookCode = BookCode.CET4)
            seedWordMembership(now, wordId = "toefl-word", value = "academic", bookCode = BookCode.TOEFL)
            database.reviewDao().upsertCard(reviewCard("cet4-card", "cet4-word", now, BookCode.CET4))
            database.reviewDao().upsertCard(reviewCard("toefl-card", "toefl-word", now, BookCode.TOEFL))
            database.reviewDao().insertLog(
                reviewLog(
                    id = "cet4-log",
                    wordId = "cet4-word",
                    bookCode = BookCode.CET4,
                    rating = ReviewRating.Again,
                    reviewedAt = Instant.parse("2026-05-16T08:00:00Z"),
                    durationMs = 30_000,
                ),
            )
            database.reviewDao().insertLog(
                reviewLog(
                    id = "toefl-log",
                    wordId = "toefl-word",
                    bookCode = BookCode.TOEFL,
                    rating = ReviewRating.Hard,
                    reviewedAt = Instant.parse("2026-05-17T08:00:00Z"),
                    durationMs = 45_000,
                ),
            )

            val reviewedCards =
                database
                    .statsDao()
                    .observeReviewedCards(now.minusSeconds(86_400), listOf(BookCode.CET4.name))
                    .first()
            val activeDays =
                database
                    .statsDao()
                    .observeActiveDays(listOf(BookCode.CET4.name))
                    .first()
            val activityRows =
                database
                    .statsDao()
                    .observeDailyActivityRows("2026-05-16", "2026-05-17", listOf(BookCode.CET4.name))
                    .first()
            val difficultWords =
                database
                    .statsDao()
                    .observeDifficultWordRows(
                        from = Instant.parse("2026-05-15T00:00:00Z"),
                        bookCodes = listOf(BookCode.CET4.name),
                    ).first()

            assertEquals(listOf("cet4-card"), reviewedCards.map { it.id })
            assertEquals(listOf("2026-05-16"), activeDays)
            assertEquals(listOf(DailyActivityRow("2026-05-16", 1)), activityRows)
            assertEquals(listOf("cet4-word"), difficultWords.map { it.wordId })
        }

    private fun reviewLog(
        id: String,
        wordId: String = WORD_ID,
        bookCode: BookCode = BookCode.CET4,
        cardIdOverride: String? = null,
        rating: ReviewRating,
        reviewedAt: Instant,
        durationMs: Long,
    ): ReviewLogEntity =
        ReviewLogEntity(
            id = id,
            cardId = cardIdOverride ?: cardId(wordId, bookCode.name),
            wordId = wordId,
            bookCode = bookCode.name,
            rating = rating.wireName,
            reviewedAt = reviewedAt,
            localDay = "2026-05-16",
            elapsedDays = 0,
            scheduledDaysBefore = 0,
            scheduledDaysAfter = 1,
            difficultyBefore = null,
            difficultyAfter = 5.0,
            stabilityBefore = null,
            stabilityAfter = 1.0,
            retrievabilityBefore = null,
            retrievabilityAfter = 0.9,
            durationMs = durationMs,
            targetRetention = 0.9,
            algorithm = "fsrs",
            algorithmVersion = "test",
        )

    private fun staleDailyStats(updatedAt: Instant): DailyStatsEntity =
        DailyStatsEntity(
            localDay = "2026-05-15",
            newCount = 9,
            reviewCount = 9,
            againCount = 9,
            hardCount = 9,
            goodCount = 9,
            easyCount = 9,
            completedCount = 36,
            recallAccuracy = 1.0,
            passRate = 1.0,
            estimatedMinutes = 9,
            updatedAt = updatedAt,
        )

    private fun reviewCard(
        id: String,
        wordId: String,
        now: Instant,
        bookCode: BookCode = BookCode.CET4,
    ): ReviewCardEntity =
        ReviewCardEntity(
            id = id,
            wordId = wordId,
            bookCode = bookCode.name,
            state = "Review",
            difficulty = 5.0,
            stability = 10.0,
            retrievability = 0.9,
            scheduledDays = 10,
            dueAt = now.plusSeconds(3_600),
            lastReviewAt = now.minusSeconds(3_600),
            reviewCount = 1,
            lapseCount = 0,
            firstReviewedAt = now.minusSeconds(3_600),
            createdAt = now.minusSeconds(3_600),
            updatedAt = now,
        )

    private suspend fun seedWordMembership(
        now: Instant,
        wordId: String = WORD_ID,
        value: String = "access",
        bookCode: BookCode = BookCode.CET4,
    ) {
        database.wordDao().upsertWords(
            listOf(
                WordEntryEntity(
                    id = wordId,
                    word = value,
                    meaning = "入口",
                    phonetic = null,
                    partOfSpeech = null,
                    definition = null,
                    cefrLevel = null,
                    cefrRank = 0.0,
                    frequency = 0.0,
                    sourceFlagsJson = "[]",
                    coverageTier = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        database.wordDao().upsertMemberships(
            listOf(
                WordBookMembershipEntity(
                    wordId = wordId,
                    bookCode = bookCode.name,
                    orderIndex = 0,
                    examFrequencyScore = 0.0,
                    examPriorityScore = 0.0,
                    isPhraseBacked = false,
                    phraseCount = 0,
                ),
            ),
        )
    }

    private companion object {
        const val WORD_ID = "word-1"
    }
}
