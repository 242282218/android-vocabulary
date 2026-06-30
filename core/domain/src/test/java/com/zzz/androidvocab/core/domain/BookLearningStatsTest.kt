package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewState
import com.zzz.androidvocab.core.model.WordEntry
import com.zzz.androidvocab.core.model.WordStatusFilter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class BookLearningStatsTest {
    private val now = Instant.parse("2026-05-16T08:00:00Z")

    @Test
    fun masteredFilterUsesCurrentRetrievability() {
        val words = listOf(word("stale-mastered"), word("current-mastered"))
        val cards =
            listOf(
                reviewCard("stale-mastered", retrievability = 0.92),
                reviewCard("current-mastered", retrievability = 0.92),
            )

        val result =
            filterWordsByStatus(
                words = words,
                cards = cards,
                selectedBooks = setOf(BookCode.CET4),
                statusFilter = WordStatusFilter.Mastered,
                now = now,
                retrievability = { card ->
                    if (card.wordId == "stale-mastered") 0.40 else card.retrievability
                },
            )

        assertEquals(listOf("current-mastered"), result.map { it.id })
    }

    @Test
    fun familiarFilterExcludesCurrentlyMasteredCards() {
        val words = listOf(word("familiar"), word("mastered"))
        val cards =
            listOf(
                reviewCard("familiar", retrievability = 0.70),
                reviewCard("mastered", retrievability = 0.90),
            )

        val result =
            filterWordsByStatus(
                words = words,
                cards = cards,
                selectedBooks = setOf(BookCode.CET4),
                statusFilter = WordStatusFilter.Familiar,
                now = now,
                retrievability = { it.retrievability },
            )

        assertEquals(listOf("familiar"), result.map { it.id })
    }

    @Test
    fun statusFiltersIgnoreCardsOutsideSelectedBooks() {
        val words = listOf(word("shared"))
        val cards =
            listOf(
                reviewCard(
                    wordId = "shared",
                    bookCode = BookCode.CET6,
                    retrievability = 0.90,
                    dueAt = now.minusSeconds(60),
                ),
            )

        val dueResult =
            filterWordsByStatus(
                words = words,
                cards = cards,
                selectedBooks = setOf(BookCode.CET4),
                statusFilter = WordStatusFilter.Due,
                now = now,
                retrievability = { it.retrievability },
            )
        val unlearnedResult =
            filterWordsByStatus(
                words = words,
                cards = cards,
                selectedBooks = setOf(BookCode.CET4),
                statusFilter = WordStatusFilter.Unlearned,
                now = now,
                retrievability = { it.retrievability },
            )

        assertEquals(emptyList<String>(), dueResult.map { it.id })
        assertEquals(listOf("shared"), unlearnedResult.map { it.id })
    }

    @Test
    fun emptySelectedBooksFiltersAgainstAllBooks() {
        val words = listOf(word("shared"))
        val cards =
            listOf(
                reviewCard(
                    wordId = "shared",
                    bookCode = BookCode.TOEFL,
                    retrievability = 0.90,
                    dueAt = now.minusSeconds(60),
                ),
            )

        val result =
            filterWordsByStatus(
                words = words,
                cards = cards,
                selectedBooks = emptySet(),
                statusFilter = WordStatusFilter.Due,
                now = now,
                retrievability = { it.retrievability },
            )

        assertEquals(listOf("shared"), result.map { it.id })
    }

    @Test
    fun sharedWordIsNotUnlearnedWhenAnySelectedBookHasCard() {
        val words = listOf(word("shared"))
        val cards =
            listOf(
                reviewCard(
                    wordId = "shared",
                    bookCode = BookCode.CET4,
                    retrievability = 0.80,
                ),
            )

        val result =
            filterWordsByStatus(
                words = words,
                cards = cards,
                selectedBooks = setOf(BookCode.CET4, BookCode.CET6),
                statusFilter = WordStatusFilter.Unlearned,
                now = now,
                retrievability = { it.retrievability },
            )

        assertEquals(emptyList<String>(), result.map { it.id })
    }

    private fun word(id: String): WordEntry =
        WordEntry(
            id = id,
            word = id,
            meaning = id,
            phonetic = null,
            partOfSpeech = null,
            definition = null,
            cefrLevel = null,
            cefrRank = 0.0,
            frequency = 0.0,
            sourceFlags = emptyList(),
            coverageTier = null,
        )

    private fun reviewCard(
        wordId: String,
        retrievability: Double,
        bookCode: BookCode = BookCode.CET4,
        dueAt: Instant = now.plusSeconds(86_400),
    ): ReviewCard =
        ReviewCard(
            id = "card-${bookCode.name}-$wordId",
            wordId = wordId,
            bookCode = bookCode,
            state = ReviewState.Review,
            difficulty = 5.0,
            stability = 20.0,
            retrievability = retrievability,
            scheduledDays = 21,
            dueAt = dueAt,
            lastReviewAt = now.minusSeconds(86_400),
            reviewCount = 3,
            lapseCount = 0,
            firstReviewedAt = now.minusSeconds(172_800),
            createdAt = now.minusSeconds(172_800),
            updatedAt = now.minusSeconds(86_400),
        )
}
