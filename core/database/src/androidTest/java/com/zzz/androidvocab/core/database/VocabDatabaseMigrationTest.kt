package com.zzz.androidvocab.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VocabDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            VocabDatabase::class.java,
        )

    @Test
    fun migratesVersionOneToTwo() {
        helper
            .createDatabase(TEST_DATABASE, 1)
            .apply {
                execSQL(
                    """
                    INSERT INTO word_entries (
                        id,
                        word,
                        meaning,
                        phonetic,
                        partOfSpeech,
                        definition,
                        cefrLevel,
                        cefrRank,
                        frequency,
                        sourceFlagsJson,
                        coverageTier,
                        createdAt,
                        updatedAt
                    ) VALUES (
                        'word-access',
                        'access',
                        '入口；通道',
                        NULL,
                        NULL,
                        NULL,
                        'A1',
                        1,
                        0.42,
                        '[]',
                        NULL,
                        '2026-05-16T00:00:00Z',
                        '2026-05-16T00:00:00Z'
                    )
                    """.trimIndent(),
                )
                close()
            }

        helper
            .runMigrationsAndValidate(TEST_DATABASE, 2, true, MIGRATION_1_2)
            .apply {
                query("SELECT cefrRank FROM word_entries WHERE id = 'word-access'").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1.0, cursor.getDouble(0), 0.0)
                }
                query("PRAGMA table_info(word_entries)").use { cursor ->
                    val typeColumnIndex = cursor.getColumnIndexOrThrow("type")
                    val nameColumnIndex = cursor.getColumnIndexOrThrow("name")
                    var foundCefrRank = false
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameColumnIndex) == "cefrRank") {
                            foundCefrRank = true
                            assertEquals("REAL", cursor.getString(typeColumnIndex))
                        }
                    }
                    assertTrue(foundCefrRank)
                }
                close()
            }
    }

    @Test
    fun migratesVersionTwoToThree() {
        helper
            .createDatabase(TEST_DATABASE, 2)
            .apply {
                execSQL(
                    """
                    INSERT INTO review_logs (
                        id,
                        cardId,
                        wordId,
                        bookCode,
                        rating,
                        reviewedAt,
                        localDay,
                        elapsedDays,
                        scheduledDaysBefore,
                        scheduledDaysAfter,
                        difficultyBefore,
                        difficultyAfter,
                        stabilityBefore,
                        stabilityAfter,
                        retrievabilityBefore,
                        retrievabilityAfter,
                        durationMs,
                        targetRetention,
                        algorithm,
                        algorithmVersion
                    ) VALUES (
                        'log-1',
                        'card|CET4|word-access',
                        'word-access',
                        'CET4',
                        'good',
                        '2026-05-16T00:00:00Z',
                        '2026-05-16',
                        NULL,
                        0,
                        1,
                        NULL,
                        5.0,
                        NULL,
                        1.0,
                        NULL,
                        0.9,
                        500,
                        0.9,
                        'fsrs',
                        'test'
                    )
                    """.trimIndent(),
                )
                close()
            }

        helper
            .runMigrationsAndValidate(TEST_DATABASE, 3, true, MIGRATION_2_3)
            .apply {
                query("SELECT stateAfter, dueAtAfter FROM review_logs WHERE id = 'log-1'").use { cursor ->
                    cursor.moveToFirst()
                    assertTrue(cursor.isNull(0))
                    assertTrue(cursor.isNull(1))
                }
                close()
            }
    }

    private companion object {
        const val TEST_DATABASE = "vocab-migration-test"
    }
}
