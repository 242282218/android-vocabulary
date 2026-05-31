package com.zzz.androidvocab.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val VALID_REVIEW_LOGS_VIEW_SQL =
    "SELECT l.* FROM review_logs l JOIN wordbook_memberships m ON m.wordId = l.wordId AND m.bookCode = l.bookCode"

private const val WORD_CARD_VIEW_SQL =
    "SELECT c.id AS cardId, m.wordId, m.bookCode, c.state, c.difficulty, c.stability, c.retrievability, " +
        "c.scheduledDays, c.dueAt, c.lastReviewAt, c.reviewCount, c.lapseCount, c.firstReviewedAt, " +
        "c.createdAt AS cardCreatedAt, c.updatedAt AS cardUpdatedAt, w.word, w.meaning, w.phonetic, " +
        "w.partOfSpeech, w.definition, w.cefrLevel, w.cefrRank, w.frequency, w.sourceFlagsJson, " +
        "w.coverageTier, w.createdAt AS wordCreatedAt, w.updatedAt AS wordUpdatedAt, m.orderIndex " +
        "FROM wordbook_memberships m JOIN word_entries w ON w.id = m.wordId " +
        "LEFT JOIN review_cards c ON c.wordId = m.wordId AND c.bookCode = m.bookCode"

private const val FIRST_REVIEWS_VIEW_SQL_V6_TO_V8 =
    "SELECT cardId, MIN(reviewedAt) AS firstReviewedAt FROM valid_review_logs GROUP BY cardId"

private const val FIRST_REVIEWS_VIEW_SQL_V9 =
    "SELECT l.cardId AS cardId, l.id AS firstLogId, l.reviewedAt AS firstReviewedAt FROM valid_review_logs l " +
        "WHERE l.id = (SELECT candidate.id FROM valid_review_logs candidate WHERE candidate.cardId = l.cardId " +
        "ORDER BY candidate.reviewedAt ASC, candidate.id ASC LIMIT 1)"

private const val REVIEW_DAILY_STATS_VIEW_SQL_V7 =
    "SELECT localDay, newCount, (completedCount - newCount) AS reviewCount, againCount, hardCount, goodCount, " +
        "easyCount, completedCount, CASE WHEN completedCount = 0 THEN 0.0 ELSE CAST(goodCount + easyCount AS REAL) " +
        "/ completedCount END AS recallAccuracy, CASE WHEN completedCount = 0 THEN 0.0 ELSE CAST(hardCount + " +
        "goodCount + easyCount AS REAL) / completedCount END AS passRate, CASE WHEN durationMs <= 0 THEN 0 ELSE " +
        "CAST((durationMs + 59999) / 60000 AS INTEGER) END AS estimatedMinutes FROM (SELECT l.localDay AS localDay, " +
        "SUM(CASE WHEN f.firstReviewedAt = l.reviewedAt THEN 1 ELSE 0 END) AS newCount, SUM(CASE WHEN l.rating = " +
        "'again' THEN 1 ELSE 0 END) AS againCount, SUM(CASE WHEN l.rating = 'hard' THEN 1 ELSE 0 END) AS hardCount, " +
        "SUM(CASE WHEN l.rating = 'good' THEN 1 ELSE 0 END) AS goodCount, SUM(CASE WHEN l.rating = 'easy' THEN 1 " +
        "ELSE 0 END) AS easyCount, COUNT(*) AS completedCount, IFNULL(SUM(l.durationMs), 0) AS durationMs FROM " +
        "valid_review_logs l JOIN first_reviews f ON f.cardId = l.cardId GROUP BY l.localDay) daily"

private const val REVIEW_DAILY_STATS_VIEW_SQL_V8 =
    "SELECT localDay, newCount, (completedCount - newCount) AS reviewCount, againCount, hardCount, goodCount, " +
        "easyCount, completedCount, durationMs, CASE WHEN completedCount = 0 THEN 0.0 ELSE CAST(goodCount + " +
        "easyCount AS REAL) / completedCount END AS recallAccuracy, CASE WHEN completedCount = 0 THEN 0.0 ELSE " +
        "CAST(hardCount + goodCount + easyCount AS REAL) / completedCount END AS passRate, CASE WHEN durationMs " +
        "<= 0 THEN 0 ELSE CAST((durationMs + 59999) / 60000 AS INTEGER) END AS estimatedMinutes FROM (SELECT " +
        "l.localDay AS localDay, SUM(CASE WHEN f.firstReviewedAt = l.reviewedAt THEN 1 ELSE 0 END) AS newCount, " +
        "SUM(CASE WHEN l.rating = 'again' THEN 1 ELSE 0 END) AS againCount, SUM(CASE WHEN l.rating = 'hard' THEN 1 " +
        "ELSE 0 END) AS hardCount, SUM(CASE WHEN l.rating = 'good' THEN 1 ELSE 0 END) AS goodCount, " +
        "SUM(CASE WHEN l.rating = 'easy' THEN 1 ELSE 0 END) AS easyCount, COUNT(*) AS completedCount, " +
        "IFNULL(SUM(l.durationMs), 0) AS durationMs FROM valid_review_logs l JOIN first_reviews f ON f.cardId = " +
        "l.cardId GROUP BY l.localDay) daily"

private const val REVIEW_DAILY_STATS_VIEW_SQL_V9 =
    "SELECT localDay, newCount, (completedCount - newCount) AS reviewCount, againCount, hardCount, goodCount, " +
        "easyCount, completedCount, durationMs, CASE WHEN completedCount = 0 THEN 0.0 ELSE CAST(goodCount + " +
        "easyCount AS REAL) / completedCount END AS recallAccuracy, CASE WHEN completedCount = 0 THEN 0.0 ELSE " +
        "CAST(hardCount + goodCount + easyCount AS REAL) / completedCount END AS passRate, CASE WHEN durationMs " +
        "<= 0 THEN 0 ELSE CAST((durationMs + 59999) / 60000 AS INTEGER) END AS estimatedMinutes FROM (SELECT " +
        "l.localDay AS localDay, SUM(CASE WHEN f.firstLogId = l.id THEN 1 ELSE 0 END) AS newCount, " +
        "SUM(CASE WHEN l.rating = 'again' THEN 1 ELSE 0 END) AS againCount, SUM(CASE WHEN l.rating = 'hard' THEN 1 " +
        "ELSE 0 END) AS hardCount, SUM(CASE WHEN l.rating = 'good' THEN 1 ELSE 0 END) AS goodCount, " +
        "SUM(CASE WHEN l.rating = 'easy' THEN 1 ELSE 0 END) AS easyCount, COUNT(*) AS completedCount, " +
        "IFNULL(SUM(l.durationMs), 0) AS durationMs FROM valid_review_logs l JOIN first_reviews f ON f.cardId = " +
        "l.cardId GROUP BY l.localDay) daily"

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `word_entries_new` (
                    `id` TEXT NOT NULL,
                    `word` TEXT NOT NULL,
                    `meaning` TEXT NOT NULL,
                    `phonetic` TEXT,
                    `partOfSpeech` TEXT,
                    `definition` TEXT,
                    `cefrLevel` TEXT,
                    `cefrRank` REAL NOT NULL,
                    `frequency` REAL NOT NULL,
                    `sourceFlagsJson` TEXT NOT NULL,
                    `coverageTier` TEXT,
                    `createdAt` TEXT NOT NULL,
                    `updatedAt` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `word_entries_new` (
                    `id`,
                    `word`,
                    `meaning`,
                    `phonetic`,
                    `partOfSpeech`,
                    `definition`,
                    `cefrLevel`,
                    `cefrRank`,
                    `frequency`,
                    `sourceFlagsJson`,
                    `coverageTier`,
                    `createdAt`,
                    `updatedAt`
                )
                SELECT
                    `id`,
                    `word`,
                    `meaning`,
                    `phonetic`,
                    `partOfSpeech`,
                    `definition`,
                    `cefrLevel`,
                    CAST(`cefrRank` AS REAL),
                    `frequency`,
                    `sourceFlagsJson`,
                    `coverageTier`,
                    `createdAt`,
                    `updatedAt`
                FROM `word_entries`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `word_entries`")
            db.execSQL("ALTER TABLE `word_entries_new` RENAME TO `word_entries`")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_word_entries_word` ON `word_entries` (`word`)")
        }
    }

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `stateAfter` TEXT")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `dueAtAfter` TEXT")
        }
    }

val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `vocabulary_import_runs` ADD COLUMN `assetFingerprint` TEXT NOT NULL DEFAULT ''",
            )
        }
    }

val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE VIEW `valid_review_logs` AS $VALID_REVIEW_LOGS_VIEW_SQL")
            db.execSQL("CREATE VIEW `word_card_view` AS $WORD_CARD_VIEW_SQL")
        }
    }

val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE VIEW `first_reviews` AS $FIRST_REVIEWS_VIEW_SQL_V6_TO_V8")
        }
    }

val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE VIEW `$REVIEW_DAILY_STATS_VIEW_NAME` AS $REVIEW_DAILY_STATS_VIEW_SQL_V7")
        }
    }

val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP VIEW IF EXISTS `$REVIEW_DAILY_STATS_VIEW_NAME`")
            db.execSQL("CREATE VIEW `$REVIEW_DAILY_STATS_VIEW_NAME` AS $REVIEW_DAILY_STATS_VIEW_SQL_V8")
        }
    }

val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP VIEW IF EXISTS `$REVIEW_DAILY_STATS_VIEW_NAME`")
            db.execSQL("DROP VIEW IF EXISTS `$FIRST_REVIEWS_VIEW_NAME`")
            db.execSQL("CREATE VIEW `$FIRST_REVIEWS_VIEW_NAME` AS $FIRST_REVIEWS_VIEW_SQL_V9")
            db.execSQL("CREATE VIEW `$REVIEW_DAILY_STATS_VIEW_NAME` AS $REVIEW_DAILY_STATS_VIEW_SQL_V9")
        }
    }

val MIGRATION_9_10 =
    object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP VIEW IF EXISTS `$REVIEW_DAILY_STATS_VIEW_NAME`")
            db.execSQL("DROP VIEW IF EXISTS `$FIRST_REVIEWS_VIEW_NAME`")
            db.execSQL("CREATE VIEW `$FIRST_REVIEWS_VIEW_NAME` AS $FIRST_REVIEWS_VIEW_SQL")
            db.execSQL("CREATE VIEW `$REVIEW_DAILY_STATS_VIEW_NAME` AS $REVIEW_DAILY_STATS_VIEW_SQL")
        }
    }
