package com.zzz.androidvocab.core.model

enum class BookCode(
    val displayName: String,
    val assetFileName: String,
    val expectedPublishSafeCount: Int,
) {
    CET4("CET4", "cet4.json", 3846),
    CET6("CET6", "cet6.json", 5406),
    KAOYAN("考研", "kaoyan.json", 4801),
    IELTS("IELTS", "ielts.json", 5038),
    TOEFL("TOEFL", "toefl.json", 6970),
}

fun String.toBookCodeOrNull(): BookCode? = BookCode.entries.firstOrNull { it.name == this }

fun Set<BookCode>.effectiveSelectedBookCodes(): List<BookCode> =
    ifEmpty { BookCode.entries.toSet() }
        .sortedBy { it.ordinal }

fun Set<BookCode>.effectiveSelectedBookCodeNames(): List<String> = effectiveSelectedBookCodes().map { it.name }

data class WordEntry(
    val id: String,
    val word: String,
    val meaning: String,
    val phonetic: String?,
    val partOfSpeech: String?,
    val definition: String?,
    val cefrLevel: String?,
    val cefrRank: Double,
    val frequency: Double,
    val sourceFlags: List<String>,
    val coverageTier: String?,
)

enum class WordStatusFilter(
    val displayName: String,
) {
    All("全部"),
    Unlearned("未学"),
    Learning("学习中"),
    Due("待复习"),
    Familiar("熟悉"),
    Mastered("掌握"),
}

data class WordBookMembership(
    val wordId: String,
    val bookCode: BookCode,
    val orderIndex: Int,
    val examFrequencyScore: Double,
    val examPriorityScore: Double,
    val isPhraseBacked: Boolean,
    val phraseCount: Int,
)

data class WordAlias(
    val wordId: String,
    val value: String,
)

data class WordDetail(
    val entry: WordEntry,
    val memberships: List<WordBookMembership>,
    val aliases: List<String>,
)
