package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.BookStats
import com.zzz.androidvocab.core.model.MASTERED_RETRIEVABILITY
import com.zzz.androidvocab.core.model.MASTERED_SCHEDULED_DAYS
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewState
import com.zzz.androidvocab.core.model.WordEntry
import com.zzz.androidvocab.core.model.WordStatusFilter
import java.time.Instant

fun calculateBookProgress(
    totals: Map<BookCode, Int>,
    cards: List<ReviewCard>,
    now: Instant,
    retrievability: (ReviewCard) -> Double?,
): List<BookProgress> =
    BookCode.entries.filter { it in totals }.map { book ->
        val bookCards = cards.filter { it.bookCode == book }
        BookProgress(
            bookCode = book,
            totalCount = totals[book] ?: 0,
            learnedCount = bookCards.size,
            masteredCount = bookCards.count { it.isCurrentlyMastered(now, retrievability) },
            dueCount = bookCards.count { it.dueAt?.let { dueAt -> dueAt <= now } == true },
        )
    }

fun calculateBookStats(
    totals: Map<BookCode, Int>,
    cards: List<ReviewCard>,
    now: Instant,
    retrievability: (ReviewCard) -> Double?,
): List<BookStats> =
    calculateBookProgress(totals, cards, now, retrievability).map { progress ->
        val bookCards = cards.filter { it.bookCode == progress.bookCode }
        BookStats(
            progress = progress,
            unlearnedCount = (progress.totalCount - progress.learnedCount).coerceAtLeast(0),
            learningCount =
                bookCards.count {
                    it.state == ReviewState.New ||
                        it.state == ReviewState.Learning ||
                        it.state == ReviewState.Relearning
                },
            familiarCount =
                bookCards.count {
                    val due = it.dueAt?.let { dueAt -> dueAt <= now } == true
                    (it.state == ReviewState.Review || it.state == ReviewState.Mastered) &&
                        !due &&
                        !it.isCurrentlyMastered(now, retrievability)
                },
        )
    }

fun filterWordsByStatus(
    words: List<WordEntry>,
    cards: List<ReviewCard>,
    selectedBooks: Set<BookCode>,
    statusFilter: WordStatusFilter,
    now: Instant,
    retrievability: (ReviewCard) -> Double?,
): List<WordEntry> {
    if (statusFilter == WordStatusFilter.All) return words
    val effectiveBooks = selectedBooks.ifEmpty { BookCode.entries.toSet() }
    val selectedCardsByWord =
        cards
            .filter { it.bookCode in effectiveBooks }
            .groupBy { it.wordId }
    return words.filter { word ->
        val wordCards = selectedCardsByWord[word.id].orEmpty()
        when (statusFilter) {
            WordStatusFilter.All -> true
            WordStatusFilter.Unlearned -> wordCards.isEmpty()
            WordStatusFilter.Learning -> wordCards.any { it.isLearning() }
            WordStatusFilter.Due -> wordCards.any { it.isDue(now) }
            WordStatusFilter.Familiar -> wordCards.any { it.isCurrentlyFamiliar(now, retrievability) }
            WordStatusFilter.Mastered -> wordCards.any { it.isCurrentlyMastered(now, retrievability) }
        }
    }
}

private fun ReviewCard.isCurrentlyMastered(
    now: Instant,
    retrievability: (ReviewCard) -> Double?,
): Boolean {
    val currentDueAt = dueAt
    return scheduledDays >= MASTERED_SCHEDULED_DAYS &&
        (retrievability(this) ?: 0.0) >= MASTERED_RETRIEVABILITY &&
        (currentDueAt == null || currentDueAt > now)
}

private fun ReviewCard.isCurrentlyFamiliar(
    now: Instant,
    retrievability: (ReviewCard) -> Double?,
): Boolean =
    (state == ReviewState.Review || state == ReviewState.Mastered) &&
        !isDue(now) &&
        !isCurrentlyMastered(now, retrievability)

private fun ReviewCard.isLearning(): Boolean =
    state == ReviewState.New ||
        state == ReviewState.Learning ||
        state == ReviewState.Relearning

private fun ReviewCard.isDue(now: Instant): Boolean = dueAt?.let { it <= now } == true
