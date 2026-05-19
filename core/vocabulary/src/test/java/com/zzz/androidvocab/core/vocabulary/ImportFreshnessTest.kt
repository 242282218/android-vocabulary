package com.zzz.androidvocab.core.vocabulary

import com.zzz.androidvocab.core.database.VocabularyImportRunEntity
import com.zzz.androidvocab.core.model.BookCode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ImportFreshnessTest {
    @Test
    fun assetWordEntryAcceptsFractionalCefrRank() {
        val payload =
            """
            [
              {
                "word": "access",
                "meaning": "入口；通道",
                "level": "A1",
                "cefrRank": 1.44078279291057
              }
            ]
            """.trimIndent()

        val entries = Json.decodeFromString<List<AssetWordEntry>>(payload)

        assertEquals(1.44078279291057, entries.single().cefrRank, 0.0)
    }

    @Test
    fun searchAliasesIncludeAltMeaningsAndDropBlanks() {
        val entry =
            AssetWordEntry(
                word = "abandon",
                meaning = "放弃",
                level = "CET4",
                aliases = listOf(" 放弃 ", "", "遗弃"),
                altMeanings = listOf("放弃", "中止"),
            )

        assertEquals(listOf("放弃", "遗弃", "中止"), entry.searchAliasValues())
    }

    @Test
    fun currentPublishSafeImportIsFresh() {
        assertTrue(
            isCurrentPublishSafeImport(
                manifest = publishManifest(),
                latestImportRun = latestImportRun(),
                hasSourceManifest = true,
                bookCounts = expectedCounts(),
            ),
        )
    }

    @Test
    fun missingSourceManifestIsNotFresh() {
        assertFalse(
            isCurrentPublishSafeImport(
                manifest = publishManifest(),
                latestImportRun = latestImportRun(),
                hasSourceManifest = false,
                bookCounts = expectedCounts(),
            ),
        )
    }

    @Test
    fun generatedAtMismatchIsNotFresh() {
        assertFalse(
            isCurrentPublishSafeImport(
                manifest = publishManifest(generatedAt = "2026-05-17"),
                latestImportRun = latestImportRun(generatedAt = "2026-05-16"),
                hasSourceManifest = true,
                bookCounts = expectedCounts(),
            ),
        )
    }

    @Test
    fun countMismatchIsNotFresh() {
        assertFalse(
            isCurrentPublishSafeImport(
                manifest = publishManifest(),
                latestImportRun = latestImportRun(),
                hasSourceManifest = true,
                bookCounts = expectedCounts() + (BookCode.CET4 to 1),
            ),
        )
    }

    @Test
    fun nonPublishImportIsNotFresh() {
        assertFalse(
            isCurrentPublishSafeImport(
                manifest = publishManifest(buildTarget = "internal"),
                latestImportRun = latestImportRun(),
                hasSourceManifest = true,
                bookCounts = expectedCounts(),
            ),
        )
    }
}

private fun publishManifest(
    buildTarget: String = "publish",
    generatedAt: String = "2026-05-16",
): VocabManifest =
    VocabManifest(
        buildTarget = buildTarget,
        generatedAt = generatedAt,
        books =
            BookCode.entries.associate { book ->
                book.name to VocabManifestBook(file = book.assetFileName, count = book.expectedPublishSafeCount)
            },
    )

private fun latestImportRun(
    buildTarget: String = "publish",
    generatedAt: String = "2026-05-16",
): VocabularyImportRunEntity =
    VocabularyImportRunEntity(
        id = "import-test",
        buildTarget = buildTarget,
        generatedAt = generatedAt,
        bookCountsJson = "{}",
        importedWords = 1,
        memberships = expectedCounts().values.sum(),
        importedAt = Instant.parse("2026-05-16T00:00:00Z"),
    )

private fun expectedCounts(): Map<BookCode, Int> = BookCode.entries.associateWith { it.expectedPublishSafeCount }
