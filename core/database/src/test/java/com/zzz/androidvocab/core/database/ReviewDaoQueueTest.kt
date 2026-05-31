package com.zzz.androidvocab.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zzz.androidvocab.core.common.cardId
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.ReviewState
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
class ReviewDaoQueueTest {
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
    fun dueQueueDifficultyPriorityIgnoresOrphanLogs() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            seedWord(wordId = "easy-word", value = "easy", orderIndex = 0, now = now)
            seedWord(wordId = "hard-word", value = "hard", orderIndex = 1, now = now)
            database.reviewDao().upsertCard(reviewCard("easy-word", now))
            database.reviewDao().upsertCard(reviewCard("hard-word", now))
            database.reviewDao().insertLog(
                reviewLog(
                    id = "log-hard-valid",
                    cardId = cardId("hard-word", BookCode.CET4.name),
                    wordId = "hard-word",
                    rating = ReviewRating.Hard,
                    now = now,
                ),
            )
            database.reviewDao().insertLog(
                reviewLog(
                    id = "log-easy-orphan",
                    cardId = cardId("easy-word", BookCode.CET4.name),
                    wordId = "orphan-word",
                    rating = ReviewRating.Again,
                    now = now,
                ),
            )

            val queue =
                database
                    .reviewDao()
                    .observeDueQueue(
                        now = now,
                        bookCodes = listOf(BookCode.CET4.name),
                        difficultySince = now.minusSeconds(3_600),
                    ).first()

            assertEquals(listOf("hard", "easy"), queue.map { it.word })
            assertEquals(1, database.reviewDao().countOrphanLogs())
        }

    @Test
    fun validLogWithoutCardCacheIsRepairableAndNotNew() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            seedWord(wordId = "known-word", value = "known", orderIndex = 0, now = now)
            val knownCardId = cardId("known-word", BookCode.CET4.name)
            database.reviewDao().insertLog(
                reviewLog(
                    id = "log-known-valid",
                    cardId = knownCardId,
                    wordId = "known-word",
                    rating = ReviewRating.Good,
                    now = now,
                ),
            )

            val newQueue =
                database
                    .reviewDao()
                    .observeNewQueue(bookCodes = listOf(BookCode.CET4.name), limit = 10)
                    .first()

            assertEquals(emptyList<String>(), newQueue.map { it.word })
            assertEquals(listOf(knownCardId), database.reviewDao().getCardIdsWithLogs())
            assertEquals(listOf(knownCardId), database.reviewDao().getCardIdsMissingCacheFromLogs())
        }

    @Test
    fun reviewLogsUseIdTieBreakerForSameReviewedAt() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            val reviewedAt = now.minusSeconds(1_800)
            seedWord(wordId = "known-word", value = "known", orderIndex = 0, now = now)
            val knownCardId = cardId("known-word", BookCode.CET4.name)
            database.reviewDao().insertLog(
                reviewLog(
                    id = "log-b",
                    cardId = knownCardId,
                    wordId = "known-word",
                    rating = ReviewRating.Easy,
                    now = now,
                    reviewedAt = reviewedAt,
                ),
            )
            database.reviewDao().insertLog(
                reviewLog(
                    id = "log-a",
                    cardId = knownCardId,
                    wordId = "known-word",
                    rating = ReviewRating.Again,
                    now = now,
                    reviewedAt = reviewedAt,
                ),
            )

            assertEquals(listOf("log-a", "log-b"), database.reviewDao().getLogs(knownCardId).map { it.id })
            assertEquals(listOf("log-a", "log-b"), database.reviewDao().getLogsForMissingCacheCards().map { it.id })
            assertEquals(listOf("log-a", "log-b"), database.exportDao().reviewLogs().map { it.id })
        }

    private suspend fun seedWord(
        wordId: String,
        value: String,
        orderIndex: Int,
        now: Instant,
    ) {
        database.wordDao().upsertWords(
            listOf(
                WordEntryEntity(
                    id = wordId,
                    word = value,
                    meaning = value,
                    phonetic = null,
                    partOfSpeech = "verb",
                    definition = null,
                    cefrLevel = null,
                    cefrRank = 0.0,
                    frequency = 1.0,
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

    private fun reviewCard(
        wordId: String,
        now: Instant,
    ) = ReviewCardEntity(
        id = cardId(wordId, BookCode.CET4.name),
        wordId = wordId,
        bookCode = BookCode.CET4.name,
        state = ReviewState.Review.name,
        difficulty = 0.5,
        stability = 3.0,
        retrievability = 0.7,
        scheduledDays = 3,
        dueAt = now.minusSeconds(60),
        lastReviewAt = now.minusSeconds(86_400),
        reviewCount = 1,
        lapseCount = 0,
        firstReviewedAt = now.minusSeconds(86_400),
        createdAt = now.minusSeconds(86_400),
        updatedAt = now,
    )

    private fun reviewLog(
        id: String,
        cardId: String,
        wordId: String,
        rating: ReviewRating,
        now: Instant,
        reviewedAt: Instant = now.minusSeconds(1_800),
    ) = ReviewLogEntity(
        id = id,
        cardId = cardId,
        wordId = wordId,
        bookCode = BookCode.CET4.name,
        rating = rating.wireName,
        reviewedAt = reviewedAt,
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
    )
}
