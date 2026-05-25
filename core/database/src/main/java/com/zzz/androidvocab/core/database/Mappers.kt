package com.zzz.androidvocab.core.database

import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.BookStats
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewLog
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.ReviewState
import com.zzz.androidvocab.core.model.WordBookMembership
import com.zzz.androidvocab.core.model.WordEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import com.zzz.androidvocab.core.common.cardId as createCardId

private val mapperJson = Json { ignoreUnknownKeys = true }

private inline fun <reified T : Enum<T>> safeValueOf(
    name: String,
    fallback: T,
): T =
    try {
        enumValueOf<T>(name)
    } catch (_: IllegalArgumentException) {
        fallback
    }

private fun safeBookCode(name: String): BookCode = safeValueOf(name, BookCode.CET4)

private fun safeReviewState(name: String): ReviewState = safeValueOf(name, ReviewState.New)

private fun safeReviewRating(wireName: String): ReviewRating =
    ReviewRating.entries.find { it.wireName == wireName } ?: ReviewRating.Again

private fun parseSourceFlags(payload: String): List<String> {
    if (payload.isBlank()) return emptyList()
    return runCatching {
        mapperJson.parseToJsonElement(payload).jsonArray.map { it.jsonPrimitive.content }
    }.getOrDefault(emptyList())
}

fun WordEntryEntity.toModel(): WordEntry =
    WordEntry(
        id = id,
        word = word,
        meaning = meaning,
        phonetic = phonetic,
        partOfSpeech = partOfSpeech,
        definition = definition,
        cefrLevel = cefrLevel,
        cefrRank = cefrRank,
        frequency = frequency,
        sourceFlags = parseSourceFlags(sourceFlagsJson),
        coverageTier = coverageTier,
    )

fun WordBookMembershipEntity.toModel(): WordBookMembership =
    WordBookMembership(
        wordId = wordId,
        bookCode = safeBookCode(bookCode),
        orderIndex = orderIndex,
        examFrequencyScore = examFrequencyScore,
        examPriorityScore = examPriorityScore,
        isPhraseBacked = isPhraseBacked,
        phraseCount = phraseCount,
    )

fun ReviewCardEntity.toModel(): ReviewCard =
    ReviewCard(
        id = id,
        wordId = wordId,
        bookCode = safeBookCode(bookCode),
        state = safeReviewState(state),
        difficulty = difficulty,
        stability = stability,
        retrievability = retrievability,
        scheduledDays = scheduledDays,
        dueAt = dueAt,
        lastReviewAt = lastReviewAt,
        reviewCount = reviewCount,
        lapseCount = lapseCount,
        firstReviewedAt = firstReviewedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun ReviewCard.toEntity(): ReviewCardEntity =
    ReviewCardEntity(
        id = id,
        wordId = wordId,
        bookCode = bookCode.name,
        state = state.name,
        difficulty = difficulty,
        stability = stability,
        retrievability = retrievability,
        scheduledDays = scheduledDays,
        dueAt = dueAt,
        lastReviewAt = lastReviewAt,
        reviewCount = reviewCount,
        lapseCount = lapseCount,
        firstReviewedAt = firstReviewedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun ReviewLogEntity.toModel(): ReviewLog =
    ReviewLog(
        id = id,
        cardId = cardId,
        wordId = wordId,
        bookCode = safeBookCode(bookCode),
        rating = safeReviewRating(rating),
        reviewedAt = reviewedAt,
        localDay = localDay,
        elapsedDays = elapsedDays,
        scheduledDaysBefore = scheduledDaysBefore,
        scheduledDaysAfter = scheduledDaysAfter,
        difficultyBefore = difficultyBefore,
        difficultyAfter = difficultyAfter,
        stabilityBefore = stabilityBefore,
        stabilityAfter = stabilityAfter,
        retrievabilityBefore = retrievabilityBefore,
        retrievabilityAfter = retrievabilityAfter,
        durationMs = durationMs,
        targetRetention = targetRetention,
        algorithm = algorithm,
        algorithmVersion = algorithmVersion,
        stateAfter = stateAfter?.let(::safeReviewState),
        dueAtAfter = dueAtAfter,
    )

fun ReviewQueueRow.toQueueItem(now: Instant): ReviewQueueItem {
    val actualCardId = cardId ?: createCardId(wordId, bookCode)
    val card =
        ReviewCard(
            id = actualCardId,
            wordId = wordId,
            bookCode = safeBookCode(bookCode),
            state = state?.let(::safeReviewState) ?: ReviewState.New,
            difficulty = difficulty,
            stability = stability,
            retrievability = retrievability,
            scheduledDays = scheduledDays ?: 0,
            dueAt = dueAt ?: now,
            lastReviewAt = lastReviewAt,
            reviewCount = reviewCount ?: 0,
            lapseCount = lapseCount ?: 0,
            firstReviewedAt = firstReviewedAt,
            createdAt = cardCreatedAt ?: now,
            updatedAt = cardUpdatedAt ?: now,
        )
    return ReviewQueueItem(
        card = card,
        word =
            WordEntry(
                id = wordId,
                word = word,
                meaning = meaning,
                phonetic = phonetic,
                partOfSpeech = partOfSpeech,
                definition = definition,
                cefrLevel = cefrLevel,
                cefrRank = cefrRank,
                frequency = frequency,
                sourceFlags = parseSourceFlags(sourceFlagsJson),
                coverageTier = coverageTier,
            ),
        isNew = cardId == null,
    )
}

fun BookProgressRow.toModel() =
    BookProgress(
        bookCode = safeBookCode(bookCode),
        totalCount = totalCount,
        learnedCount = learnedCount,
        masteredCount = masteredCount,
        dueCount = dueCount,
    )

fun BookStatsRow.toModel() =
    BookStats(
        progress =
            BookProgress(
                bookCode = safeBookCode(bookCode),
                totalCount = totalCount,
                learnedCount = learnedCount,
                masteredCount = masteredCount,
                dueCount = dueCount,
            ),
        unlearnedCount = totalCount - learnedCount,
        learningCount = learningCount,
        familiarCount = familiarCount,
    )
