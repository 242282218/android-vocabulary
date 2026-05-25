package com.zzz.androidvocab.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
            db.execSQL(
                """
                CREATE VIEW IF NOT EXISTS `valid_review_logs` AS
                SELECT l.* FROM review_logs l
                JOIN wordbook_memberships m ON m.wordId = l.wordId AND m.bookCode = l.bookCode
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE VIEW IF NOT EXISTS `word_card_view` AS
                SELECT c.id AS cardId, m.wordId, m.bookCode,
                  c.state, c.difficulty, c.stability, c.retrievability,
                  c.scheduledDays, c.dueAt, c.lastReviewAt,
                  c.reviewCount, c.lapseCount, c.firstReviewedAt,
                  c.createdAt AS cardCreatedAt, c.updatedAt AS cardUpdatedAt,
                  w.word, w.meaning, w.phonetic, w.partOfSpeech, w.definition,
                  w.cefrLevel, w.cefrRank, w.frequency, w.sourceFlagsJson, w.coverageTier,
                  w.createdAt AS wordCreatedAt, w.updatedAt AS wordUpdatedAt,
                  m.orderIndex
                FROM wordbook_memberships m
                JOIN word_entries w ON w.id = m.wordId
                LEFT JOIN review_cards c ON c.wordId = m.wordId AND c.bookCode = m.bookCode
                """.trimIndent(),
            )
        }
    }
