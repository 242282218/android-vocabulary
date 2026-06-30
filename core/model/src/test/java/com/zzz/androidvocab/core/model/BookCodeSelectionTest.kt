package com.zzz.androidvocab.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BookCodeSelectionTest {
    @Test
    fun emptySelectionUsesAllBooksInProductOrder() {
        assertEquals(
            BookCode.entries,
            emptySet<BookCode>().effectiveSelectedBookCodes(),
        )
        assertEquals(
            BookCode.entries.map { it.name },
            emptySet<BookCode>().effectiveSelectedBookCodeNames(),
        )
    }

    @Test
    fun explicitSelectionUsesProductOrder() {
        val selected = linkedSetOf(BookCode.TOEFL, BookCode.CET4, BookCode.IELTS)

        assertEquals(
            listOf(BookCode.CET4, BookCode.IELTS, BookCode.TOEFL),
            selected.effectiveSelectedBookCodes(),
        )
        assertEquals(
            listOf("CET4", "IELTS", "TOEFL"),
            selected.effectiveSelectedBookCodeNames(),
        )
    }
}
