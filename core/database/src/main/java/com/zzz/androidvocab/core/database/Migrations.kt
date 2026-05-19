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
