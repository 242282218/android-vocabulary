package com.zzz.androidvocab.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
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

    @Test
    fun migratesVersionThreeToFour() {
        helper
            .createDatabase(TEST_DATABASE, 3)
            .apply {
                execSQL(
                    """
                    INSERT INTO vocabulary_import_runs (
                        id,
                        buildTarget,
                        generatedAt,
                        bookCountsJson,
                        importedWords,
                        memberships,
                        importedAt
                    ) VALUES (
                        'import-1',
                        'publish',
                        '2026-05-16',
                        '{"CET4":3846}',
                        3846,
                        3846,
                        '2026-05-16T00:00:00Z'
                    )
                    """.trimIndent(),
                )
                close()
            }

        helper
            .runMigrationsAndValidate(TEST_DATABASE, 4, true, MIGRATION_3_4)
            .apply {
                query("SELECT assetFingerprint FROM vocabulary_import_runs WHERE id = 'import-1'").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("", cursor.getString(0))
                }
                close()
            }
    }

    @Test
    fun migratesVersionFourToFive() {
        helper
            .createDatabase(TEST_DATABASE, 4)
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
                        1.0,
                        0.42,
                        '[]',
                        NULL,
                        '2026-05-16T00:00:00Z',
                        '2026-05-16T00:00:00Z'
                    )
                    """.trimIndent(),
                )
                execSQL(
                    """
                    INSERT INTO wordbook_memberships (
                        wordId,
                        bookCode,
                        orderIndex,
                        examFrequencyScore,
                        examPriorityScore,
                        isPhraseBacked,
                        phraseCount
                    ) VALUES (
                        'word-access',
                        'CET4',
                        1,
                        0.7,
                        0.8,
                        0,
                        0
                    )
                    """.trimIndent(),
                )
                execSQL(
                    """
                    INSERT INTO review_cards (
                        id,
                        wordId,
                        bookCode,
                        state,
                        difficulty,
                        stability,
                        retrievability,
                        scheduledDays,
                        dueAt,
                        lastReviewAt,
                        reviewCount,
                        lapseCount,
                        firstReviewedAt,
                        createdAt,
                        updatedAt
                    ) VALUES (
                        'card|CET4|word-access',
                        'word-access',
                        'CET4',
                        'Review',
                        5.0,
                        3.0,
                        0.9,
                        3,
                        '2026-05-19T00:00:00Z',
                        '2026-05-16T00:00:00Z',
                        1,
                        0,
                        '2026-05-16T00:00:00Z',
                        '2026-05-16T00:00:00Z',
                        '2026-05-16T00:00:00Z'
                    )
                    """.trimIndent(),
                )
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
                        algorithmVersion,
                        stateAfter,
                        dueAtAfter
                    ) VALUES (
                        'log-1',
                        'card|CET4|word-access',
                        'word-access',
                        'CET4',
                        'good',
                        '2026-05-16T00:00:00Z',
                        '2026-05-16',
                        0,
                        0,
                        3,
                        NULL,
                        5.0,
                        NULL,
                        3.0,
                        NULL,
                        0.9,
                        500,
                        0.9,
                        'fsrs',
                        'test',
                        'Review',
                        '2026-05-19T00:00:00Z'
                    )
                    """.trimIndent(),
                )
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
                        algorithmVersion,
                        stateAfter,
                        dueAtAfter
                    ) VALUES (
                        'log-orphan',
                        'card|CET4|word-access',
                        'word-orphan',
                        'CET4',
                        'again',
                        '2026-05-17T00:00:00Z',
                        '2026-05-17',
                        1,
                        3,
                        1,
                        5.0,
                        6.0,
                        3.0,
                        2.0,
                        0.7,
                        0.8,
                        700,
                        0.9,
                        'fsrs',
                        'test',
                        'Learning',
                        '2026-05-17T00:05:00Z'
                    )
                    """.trimIndent(),
                )
                close()
            }

        helper
            .runMigrationsAndValidate(TEST_DATABASE, 5, true, MIGRATION_4_5)
            .apply {
                query("SELECT COUNT(*) FROM valid_review_logs").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                }
                query("SELECT COUNT(*) FROM valid_review_logs WHERE id = 'log-orphan'").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(0, cursor.getInt(0))
                }
                query(
                    "SELECT word, state, scheduledDays FROM word_card_view WHERE wordId = 'word-access'",
                ).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("access", cursor.getString(0))
                    assertEquals("Review", cursor.getString(1))
                    assertEquals(3, cursor.getInt(2))
                }
                close()
            }
    }

    @Test
    fun migratesVersionFiveToSix() {
        helper
            .createDatabase(TEST_DATABASE, 5)
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
                        1.0,
                        0.42,
                        '[]',
                        NULL,
                        '2026-05-16T00:00:00Z',
                        '2026-05-16T00:00:00Z'
                    )
                    """.trimIndent(),
                )
                execSQL(
                    """
                    INSERT INTO wordbook_memberships (
                        wordId,
                        bookCode,
                        orderIndex,
                        examFrequencyScore,
                        examPriorityScore,
                        isPhraseBacked,
                        phraseCount
                    ) VALUES (
                        'word-access',
                        'CET4',
                        1,
                        0.7,
                        0.8,
                        0,
                        0
                    )
                    """.trimIndent(),
                )
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
                        algorithmVersion,
                        stateAfter,
                        dueAtAfter
                    ) VALUES (
                        'log-1',
                        'card|CET4|word-access',
                        'word-access',
                        'CET4',
                        'good',
                        '2026-05-16T00:00:00Z',
                        '2026-05-16',
                        0,
                        0,
                        3,
                        NULL,
                        5.0,
                        NULL,
                        3.0,
                        NULL,
                        0.9,
                        500,
                        0.9,
                        'fsrs',
                        'test',
                        'Review',
                        '2026-05-19T00:00:00Z'
                    )
                    """.trimIndent(),
                )
                close()
            }

        helper
            .runMigrationsAndValidate(TEST_DATABASE, 6, true, MIGRATION_5_6)
            .apply {
                query(
                    "SELECT firstReviewedAt FROM first_reviews WHERE cardId = 'card|CET4|word-access'",
                ).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("2026-05-16T00:00:00Z", cursor.getString(0))
                }
                close()
            }
    }

    @Test
    fun migratesVersionSixToSeven() {
        helper
            .createDatabase(TEST_DATABASE, 6)
            .apply {
                seedVersionSixDailyStatsRows()
                close()
            }

        helper
            .runMigrationsAndValidate(TEST_DATABASE, 7, true, MIGRATION_6_7)
            .apply {
                query(
                    """
                    SELECT newCount, reviewCount, completedCount, goodCount, againCount, estimatedMinutes
                    FROM review_daily_stats
                    WHERE localDay = '2026-05-16'
                    """.trimIndent(),
                ).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                    assertEquals(1, cursor.getInt(1))
                    assertEquals(2, cursor.getInt(2))
                    assertEquals(1, cursor.getInt(3))
                    assertEquals(1, cursor.getInt(4))
                    assertEquals(2, cursor.getInt(5))
                }
                close()
            }
    }

    @Test
    fun migratesVersionSevenToEight() {
        helper
            .createDatabase(TEST_DATABASE, 7)
            .apply {
                seedVersionSixDailyStatsRows()
                close()
            }

        helper
            .runMigrationsAndValidate(TEST_DATABASE, 8, true, MIGRATION_7_8)
            .apply {
                query(
                    """
                    SELECT durationMs, estimatedMinutes
                    FROM review_daily_stats
                    WHERE localDay = '2026-05-16'
                    """.trimIndent(),
                ).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(61000, cursor.getInt(0))
                    assertEquals(2, cursor.getInt(1))
                }
                close()
            }
    }

    @Test
    fun migratesVersionEightToNine() {
        helper
            .createDatabase(TEST_DATABASE, 8)
            .apply {
                seedVersionSixDailyStatsRows()
                insertVersionSixReviewLog(
                    id = "log-alpha",
                    rating = "hard",
                    reviewedAt = "2026-05-16T08:00:00Z",
                    durationMs = 10000,
                )
                close()
            }

        helper
            .runMigrationsAndValidate(TEST_DATABASE, 9, true, MIGRATION_8_9)
            .apply {
                query(
                    """
                    SELECT firstLogId, firstReviewedAt
                    FROM first_reviews
                    WHERE cardId = 'card|CET4|word-access'
                    """.trimIndent(),
                ).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("log-alpha", cursor.getString(0))
                    assertEquals("2026-05-16T08:00:00Z", cursor.getString(1))
                }
                query(
                    """
                    SELECT newCount, reviewCount, completedCount, durationMs
                    FROM review_daily_stats
                    WHERE localDay = '2026-05-16'
                    """.trimIndent(),
                ).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                    assertEquals(2, cursor.getInt(1))
                    assertEquals(3, cursor.getInt(2))
                    assertEquals(71000, cursor.getInt(3))
                }
                close()
            }
    }

    @Test
    fun migratesVersionNineToTen() {
        helper
            .createDatabase(TEST_DATABASE, 9)
            .apply {
                seedVersionSixDailyStatsRows()
                insertVersionSixReviewLog(
                    id = "log-broken-card-id",
                    cardId = "broken-card-id",
                    rating = "hard",
                    reviewedAt = "2026-05-16T10:00:00Z",
                    durationMs = 20000,
                )
                close()
            }

        helper
            .runMigrationsAndValidate(TEST_DATABASE, 10, true, MIGRATION_9_10)
            .apply {
                query(
                    """
                    SELECT firstLogId, firstReviewedAt
                    FROM first_reviews
                    WHERE cardId = 'CET4|word-access'
                    """.trimIndent(),
                ).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("log-first", cursor.getString(0))
                    assertEquals("2026-05-16T08:00:00Z", cursor.getString(1))
                }
                query(
                    """
                    SELECT newCount, reviewCount, completedCount, hardCount
                    FROM review_daily_stats
                    WHERE localDay = '2026-05-16'
                    """.trimIndent(),
                ).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                    assertEquals(2, cursor.getInt(1))
                    assertEquals(3, cursor.getInt(2))
                    assertEquals(1, cursor.getInt(3))
                }
                close()
            }
    }

    @Test
    fun migratesVersionOneToTen() {
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
                execSQL(
                    """
                    INSERT INTO wordbook_memberships (
                        wordId,
                        bookCode,
                        orderIndex,
                        examFrequencyScore,
                        examPriorityScore,
                        isPhraseBacked,
                        phraseCount
                    ) VALUES (
                        'word-access',
                        'CET4',
                        1,
                        0.7,
                        0.8,
                        0,
                        0
                    )
                    """.trimIndent(),
                )
                execSQL(
                    """
                    INSERT INTO review_cards (
                        id,
                        wordId,
                        bookCode,
                        state,
                        difficulty,
                        stability,
                        retrievability,
                        scheduledDays,
                        dueAt,
                        lastReviewAt,
                        reviewCount,
                        lapseCount,
                        firstReviewedAt,
                        createdAt,
                        updatedAt
                    ) VALUES (
                        'card|CET4|word-access',
                        'word-access',
                        'CET4',
                        'Review',
                        5.0,
                        3.0,
                        0.9,
                        3,
                        '2026-05-19T00:00:00Z',
                        '2026-05-16T00:00:00Z',
                        1,
                        0,
                        '2026-05-16T00:00:00Z',
                        '2026-05-16T00:00:00Z',
                        '2026-05-16T00:00:00Z'
                    )
                    """.trimIndent(),
                )
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
                        0,
                        0,
                        3,
                        NULL,
                        5.0,
                        NULL,
                        3.0,
                        NULL,
                        0.9,
                        500,
                        0.9,
                        'fsrs',
                        'test'
                    )
                    """.trimIndent(),
                )
                execSQL(
                    """
                    INSERT INTO vocabulary_import_runs (
                        id,
                        buildTarget,
                        generatedAt,
                        bookCountsJson,
                        importedWords,
                        memberships,
                        importedAt
                    ) VALUES (
                        'import-1',
                        'publish',
                        '2026-05-16',
                        '{"CET4":3846}',
                        3846,
                        3846,
                        '2026-05-16T00:00:00Z'
                    )
                    """.trimIndent(),
                )
                close()
            }

        helper
            .runMigrationsAndValidate(
                TEST_DATABASE,
                10,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
            ).apply {
                query("SELECT cefrRank FROM word_entries WHERE id = 'word-access'").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1.0, cursor.getDouble(0), 0.0)
                }
                query("SELECT stateAfter, dueAtAfter FROM review_logs WHERE id = 'log-1'").use { cursor ->
                    cursor.moveToFirst()
                    assertTrue(cursor.isNull(0))
                    assertTrue(cursor.isNull(1))
                }
                query("SELECT assetFingerprint FROM vocabulary_import_runs WHERE id = 'import-1'").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("", cursor.getString(0))
                }
                query("SELECT COUNT(*) FROM valid_review_logs").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                }
                query(
                    """
                    SELECT firstLogId, firstReviewedAt
                    FROM first_reviews
                    WHERE cardId = 'CET4|word-access'
                    """.trimIndent(),
                ).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("log-1", cursor.getString(0))
                    assertEquals("2026-05-16T00:00:00Z", cursor.getString(1))
                }
                query(
                    "SELECT word, state, scheduledDays FROM word_card_view WHERE wordId = 'word-access'",
                ).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("access", cursor.getString(0))
                    assertEquals("Review", cursor.getString(1))
                    assertEquals(3, cursor.getInt(2))
                }
                query(
                    "SELECT newCount, completedCount, durationMs FROM review_daily_stats WHERE localDay = '2026-05-16'",
                ).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                    assertEquals(1, cursor.getInt(1))
                    assertEquals(500, cursor.getInt(2))
                }
                close()
            }
    }

    private companion object {
        const val TEST_DATABASE = "vocab-migration-test"
    }
}

private fun SupportSQLiteDatabase.seedVersionSixDailyStatsRows() {
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
            1.0,
            0.42,
            '[]',
            NULL,
            '2026-05-16T00:00:00Z',
            '2026-05-16T00:00:00Z'
        )
        """.trimIndent(),
    )
    execSQL(
        """
        INSERT INTO wordbook_memberships (
            wordId,
            bookCode,
            orderIndex,
            examFrequencyScore,
            examPriorityScore,
            isPhraseBacked,
            phraseCount
        ) VALUES (
            'word-access',
            'CET4',
            1,
            0.7,
            0.8,
            0,
            0
        )
        """.trimIndent(),
    )
    insertVersionSixReviewLog(
        id = "log-first",
        rating = "good",
        reviewedAt = "2026-05-16T08:00:00Z",
        durationMs = 30000,
    )
    insertVersionSixReviewLog(
        id = "log-second",
        rating = "again",
        reviewedAt = "2026-05-16T09:00:00Z",
        durationMs = 31000,
    )
}

private fun SupportSQLiteDatabase.insertVersionSixReviewLog(
    id: String,
    cardId: String = "card|CET4|word-access",
    rating: String,
    reviewedAt: String,
    durationMs: Int,
) {
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
            algorithmVersion,
            stateAfter,
            dueAtAfter
        ) VALUES (
            '$id',
            '$cardId',
            'word-access',
            'CET4',
            '$rating',
            '$reviewedAt',
            '2026-05-16',
            0,
            0,
            1,
            NULL,
            5.0,
            NULL,
            1.0,
            NULL,
            0.9,
            $durationMs,
            0.9,
            'fsrs',
            'test',
            'Review',
            '2026-05-17T00:00:00Z'
        )
        """.trimIndent(),
    )
}
