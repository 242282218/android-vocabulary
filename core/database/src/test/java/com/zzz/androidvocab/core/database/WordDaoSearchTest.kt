package com.zzz.androidvocab.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zzz.androidvocab.core.common.cardId
import com.zzz.androidvocab.core.model.BookCode
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
class WordDaoSearchTest {
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
    fun searchReturnsSelectedCandidatesInBookOrder() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            seedWords(now)
            database.reviewDao().upsertCard(
                reviewCard("due-word", now, ReviewState.Review, scheduledDays = 3, due = true),
            )
            database.reviewDao().upsertCard(
                reviewCard("mastered-word", now, ReviewState.Review, scheduledDays = 30, due = false),
            )
            database.reviewDao().upsertCard(
                reviewCard("overdue-mastered-word", now, ReviewState.Review, scheduledDays = 30, due = true),
            )

            assertEquals(
                listOf("fresh", "due", "mastered", "overdue-mastered"),
                search(),
            )
        }

    @Test
    fun searchUsesAllSelectedBooks() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            val cet4Word = word("cet4-word", "alpha", now)
            val cet6Word = word("cet6-word", "beta", now)
            database.wordDao().upsertWords(listOf(cet4Word, cet6Word))
            database.wordDao().upsertMemberships(
                listOf(
                    membership(cet4Word.id, BookCode.CET4, orderIndex = 0),
                    membership(cet6Word.id, BookCode.CET6, orderIndex = 1),
                ),
            )

            assertEquals(
                listOf("alpha"),
                search(listOf(BookCode.CET4.name)),
            )
            assertEquals(
                listOf("alpha", "beta"),
                search(listOf(BookCode.CET4.name, BookCode.CET6.name)),
            )
        }

    @Test
    fun searchMatchesWordAliases() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            val entry = word("abandon-word", "abandon", now)
            database.wordDao().upsertWords(listOf(entry))
            database.wordDao().upsertMemberships(listOf(membership(entry.id, BookCode.CET4, orderIndex = 0)))
            database.wordDao().upsertAliases(listOf(WordAliasEntity(entry.id, "抛弃")))

            val result =
                database
                    .wordDao()
                    .searchWords("抛弃", listOf(BookCode.CET4.name))
                    .first()
                    .map { it.word }

            assertEquals(listOf("abandon"), result)
        }

    @Test
    fun searchCandidatesIncludeSharedWordsOnceAcrossSelectedBooks() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            val shared = word("shared-word", "shared", now)
            database.wordDao().upsertWords(listOf(shared))
            database.wordDao().upsertMemberships(
                listOf(
                    membership(shared.id, BookCode.CET4, orderIndex = 0),
                    membership(shared.id, BookCode.CET6, orderIndex = 0),
                ),
            )
            database.reviewDao().upsertCard(
                reviewCard(shared.id, now, ReviewState.Review, scheduledDays = 3, due = true),
            )

            val selectedBooks = listOf(BookCode.CET4.name, BookCode.CET6.name)

            assertEquals(listOf("shared"), search(selectedBooks))
        }

    @Test
    fun deleteCardsWithoutMembershipRemovesOrphanCards() =
        runTest {
            val now = Instant.parse("2026-05-16T08:00:00Z")
            val entry = word("stale-word", "stale", now)
            database.wordDao().upsertWords(listOf(entry))
            database.wordDao().upsertMemberships(listOf(membership(entry.id, BookCode.CET4, orderIndex = 0)))
            database.wordDao().upsertAliases(listOf(WordAliasEntity(entry.id, "old alias")))
            val card = reviewCard(entry.id, now, ReviewState.Review, scheduledDays = 3, due = false)
            database.reviewDao().upsertCard(card)

            database.wordDao().deleteAliases()
            database.wordDao().deleteMemberships()
            database.wordDao().deleteWords()
            database.reviewDao().deleteCardsWithoutMembership()

            assertEquals(0, database.wordDao().wordCount())
            assertEquals(0, database.wordDao().membershipCount(BookCode.CET4.name))
            assertEquals(emptyList<WordAliasEntity>(), database.wordDao().observeAliases(entry.id).first())
            assertEquals(null, database.reviewDao().getCard(card.id))
        }

    private suspend fun seedWords(now: Instant) {
        val entries =
            listOf(
                word("fresh-word", "fresh", now),
                word("due-word", "due", now),
                word("mastered-word", "mastered", now),
                word("overdue-mastered-word", "overdue-mastered", now),
            )
        database.wordDao().upsertWords(entries)
        database.wordDao().upsertMemberships(
            entries.mapIndexed { index, entry ->
                membership(entry.id, BookCode.CET4, index)
            },
        )
    }

    private suspend fun search(bookCodes: List<String> = listOf(BookCode.CET4.name)): List<String> =
        database
            .wordDao()
            .searchWords("", bookCodes)
            .first()
            .map { it.word }

    private fun membership(
        wordId: String,
        bookCode: BookCode,
        orderIndex: Int,
    ) = WordBookMembershipEntity(
        wordId = wordId,
        bookCode = bookCode.name,
        orderIndex = orderIndex,
        examFrequencyScore = 1.0,
        examPriorityScore = 1.0,
        isPhraseBacked = false,
        phraseCount = 0,
    )

    private fun word(
        id: String,
        value: String,
        now: Instant,
    ) = WordEntryEntity(
        id = id,
        word = value,
        meaning = value,
        phonetic = null,
        partOfSpeech = null,
        definition = null,
        cefrLevel = null,
        cefrRank = 0.0,
        frequency = 1.0,
        sourceFlagsJson = "[]",
        coverageTier = null,
        createdAt = now,
        updatedAt = now,
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
        dueAt = if (due) now.minusSeconds(60) else now.plusSeconds(86_400),
        lastReviewAt = now.minusSeconds(86_400),
        reviewCount = 1,
        lapseCount = 0,
        firstReviewedAt = now.minusSeconds(86_400),
        createdAt = now.minusSeconds(86_400),
        updatedAt = now,
    )
}
