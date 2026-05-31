package com.zzz.androidvocab.core.database

import com.zzz.androidvocab.core.common.cardId
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.ReviewState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MappersTest {
    @Test
    fun wordEntryKeepsValidSourceFlags() {
        val entry = wordEntry(sourceFlagsJson = """["publish-safe","wordfreq"]""")

        assertEquals(listOf("publish-safe", "wordfreq"), entry.toModel().sourceFlags)
    }

    @Test
    fun wordEntryFallsBackToEmptySourceFlagsWhenPayloadIsMalformed() {
        val entry = wordEntry(sourceFlagsJson = "{bad-json")

        assertEquals(emptyList<String>(), entry.toModel().sourceFlags)
    }

    @Test
    fun queueItemFallsBackToEmptySourceFlagsWhenPayloadIsMalformed() {
        val item = reviewQueueRow(sourceFlagsJson = "{bad-json").toQueueItem(NOW)

        assertEquals(emptyList<String>(), item.word.sourceFlags)
    }

    @Test
    fun reviewCardFallsBackWhenStoredEnumsAreInvalid() {
        val card =
            reviewCardEntity(
                bookCode = "RemovedBook",
                state = "RemovedState",
            ).toModel()

        assertEquals(BookCode.CET4, card.bookCode)
        assertEquals(ReviewState.New, card.state)
    }

    @Test
    fun reviewLogFallsBackWhenStoredEnumsAreInvalid() {
        val log =
            reviewLogEntity(
                bookCode = "RemovedBook",
                rating = "removed-rating",
                stateAfter = "RemovedState",
            ).toModel()

        assertEquals(BookCode.CET4, log.bookCode)
        assertEquals(ReviewRating.Again, log.rating)
        assertEquals(ReviewState.New, log.stateAfter)
    }

    @Test
    fun queueItemFallsBackWhenStoredEnumsAreInvalid() {
        val item =
            reviewQueueRow(
                sourceFlagsJson = "[]",
                bookCode = "RemovedBook",
                state = "RemovedState",
            ).toQueueItem(NOW)

        assertEquals(BookCode.CET4, item.card.bookCode)
        assertEquals(ReviewState.New, item.card.state)
    }

    private fun wordEntry(sourceFlagsJson: String) =
        WordEntryEntity(
            id = "word-abandon",
            word = "abandon",
            meaning = "give up",
            phonetic = null,
            partOfSpeech = "verb",
            definition = null,
            cefrLevel = null,
            cefrRank = 0.0,
            frequency = 1.0,
            sourceFlagsJson = sourceFlagsJson,
            coverageTier = null,
            createdAt = NOW,
            updatedAt = NOW,
        )

    private fun reviewCardEntity(
        bookCode: String,
        state: String,
    ) = ReviewCardEntity(
        id = cardId("word-abandon", BookCode.CET4.name),
        wordId = "word-abandon",
        bookCode = bookCode,
        state = state,
        difficulty = null,
        stability = null,
        retrievability = null,
        scheduledDays = 0,
        dueAt = NOW,
        lastReviewAt = null,
        reviewCount = 0,
        lapseCount = 0,
        firstReviewedAt = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun reviewLogEntity(
        bookCode: String,
        rating: String,
        stateAfter: String,
    ) = ReviewLogEntity(
        id = "log-1",
        cardId = cardId("word-abandon", BookCode.CET4.name),
        wordId = "word-abandon",
        bookCode = bookCode,
        rating = rating,
        reviewedAt = NOW,
        localDay = "2026-05-25",
        elapsedDays = 0,
        scheduledDaysBefore = 0,
        scheduledDaysAfter = 1,
        difficultyBefore = null,
        difficultyAfter = 5.0,
        stabilityBefore = null,
        stabilityAfter = 1.0,
        retrievabilityBefore = null,
        retrievabilityAfter = 0.9,
        durationMs = 1_000,
        targetRetention = 0.9,
        algorithm = "fsrs",
        algorithmVersion = "test",
        stateAfter = stateAfter,
        dueAtAfter = NOW,
    )

    private fun reviewQueueRow(
        sourceFlagsJson: String,
        bookCode: String = BookCode.CET4.name,
        state: String? = null,
    ): ReviewQueueRow =
        ReviewQueueRow(
            cardId = cardId("word-abandon", BookCode.CET4.name),
            wordId = "word-abandon",
            bookCode = bookCode,
            state = state,
            difficulty = null,
            stability = null,
            retrievability = null,
            scheduledDays = null,
            dueAt = null,
            lastReviewAt = null,
            reviewCount = null,
            lapseCount = null,
            firstReviewedAt = null,
            cardCreatedAt = null,
            cardUpdatedAt = null,
            word = "abandon",
            meaning = "give up",
            phonetic = null,
            partOfSpeech = "verb",
            definition = null,
            cefrLevel = null,
            cefrRank = 0.0,
            frequency = 1.0,
            sourceFlagsJson = sourceFlagsJson,
            coverageTier = null,
            wordCreatedAt = NOW,
            wordUpdatedAt = NOW,
        )

    private companion object {
        val NOW: Instant = Instant.parse("2026-05-25T08:00:00Z")
    }
}
