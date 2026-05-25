package com.zzz.androidvocab.core.database

import com.zzz.androidvocab.core.common.cardId
import com.zzz.androidvocab.core.model.BookCode
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

    private fun reviewQueueRow(sourceFlagsJson: String) =
        ReviewQueueRow(
            cardId = cardId("word-abandon", BookCode.CET4.name),
            wordId = "word-abandon",
            bookCode = BookCode.CET4.name,
            state = null,
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
