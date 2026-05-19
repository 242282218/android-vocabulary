package com.zzz.androidvocab.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface WordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWords(words: List<WordEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemberships(memberships: List<WordBookMembershipEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAliases(aliases: List<WordAliasEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourceManifest(manifest: SourceManifestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportRun(run: VocabularyImportRunEntity)

    @Query("DELETE FROM word_aliases")
    suspend fun deleteAliases()

    @Query("DELETE FROM wordbook_memberships")
    suspend fun deleteMemberships()

    @Query("DELETE FROM word_entries")
    suspend fun deleteWords()

    @Query("SELECT COUNT(*) FROM wordbook_memberships WHERE bookCode = :bookCode")
    suspend fun membershipCount(bookCode: String): Int

    @Query("SELECT COUNT(*) FROM word_entries")
    suspend fun wordCount(): Int

    @Query("SELECT * FROM source_manifest WHERE id = 'publish-safe'")
    fun observeSourceManifest(): Flow<SourceManifestEntity?>

    @Query("SELECT * FROM source_manifest WHERE id = 'publish-safe'")
    suspend fun sourceManifest(): SourceManifestEntity?

    @Query("SELECT * FROM vocabulary_import_runs ORDER BY importedAt DESC LIMIT 1")
    suspend fun latestImportRun(): VocabularyImportRunEntity?

    @Query("SELECT bookCode, COUNT(*) AS count FROM wordbook_memberships GROUP BY bookCode")
    suspend fun membershipCounts(): List<BookCountRow>

    @Query("SELECT bookCode, COUNT(*) AS count FROM wordbook_memberships GROUP BY bookCode")
    fun observeMembershipCounts(): Flow<List<BookCountRow>>

    @Query(
        """
        WITH selected_word_status AS (
          SELECT
            w.id AS wordId,
            MIN(m.orderIndex) AS firstOrderIndex,
            SUM(CASE WHEN c.id IS NOT NULL THEN 1 ELSE 0 END) AS learnedCount,
            MAX(CASE WHEN c.state IN ('New', 'Learning', 'Relearning') THEN 1 ELSE 0 END) AS hasLearning,
            MAX(CASE WHEN c.dueAt IS NOT NULL AND c.dueAt <= :now THEN 1 ELSE 0 END) AS hasDue,
            MAX(
              CASE
                WHEN c.state IN ('Review', 'Mastered')
                  AND (c.dueAt IS NULL OR c.dueAt > :now)
                  AND NOT (c.scheduledDays >= 21 AND IFNULL(c.retrievability, 0) >= 0.85)
                THEN 1 ELSE 0
              END
            ) AS hasFamiliar,
            MAX(
              CASE
                WHEN c.scheduledDays >= 21
                  AND IFNULL(c.retrievability, 0) >= 0.85
                  AND (c.dueAt IS NULL OR c.dueAt > :now)
                THEN 1 ELSE 0
              END
            ) AS hasMastered
          FROM wordbook_memberships m
          JOIN word_entries w ON w.id = m.wordId
          LEFT JOIN review_cards c ON c.wordId = m.wordId AND c.bookCode = m.bookCode
          WHERE m.bookCode IN (:bookCodes)
          AND (
            :query = ''
            OR w.word LIKE '%' || :query || '%' ESCAPE '\'
            OR w.meaning LIKE '%' || :query || '%' ESCAPE '\'
            OR EXISTS (
              SELECT 1 FROM word_aliases a
              WHERE a.wordId = w.id AND a.value LIKE '%' || :query || '%' ESCAPE '\'
            )
          )
          GROUP BY w.id
        )
        SELECT w.* FROM word_entries w
        JOIN selected_word_status s ON s.wordId = w.id
        WHERE (
          :status = 'All'
          OR (:status = 'Unlearned' AND s.learnedCount = 0)
          OR (:status = 'Learning' AND s.hasLearning = 1)
          OR (:status = 'Due' AND s.hasDue = 1)
          OR (:status = 'Familiar' AND s.hasFamiliar = 1)
          OR (:status = 'Mastered' AND s.hasMastered = 1)
        )
        ORDER BY s.firstOrderIndex ASC, w.frequency DESC, w.word ASC
        LIMIT 80
        """,
    )
    fun searchWords(
        query: String,
        bookCodes: List<String>,
        status: String,
        now: Instant,
    ): Flow<List<WordEntryEntity>>

    @Query("SELECT * FROM word_entries WHERE id = :wordId")
    fun observeWord(wordId: String): Flow<WordEntryEntity?>

    @Query("SELECT * FROM wordbook_memberships WHERE wordId = :wordId ORDER BY bookCode ASC")
    fun observeMemberships(wordId: String): Flow<List<WordBookMembershipEntity>>

    @Query("SELECT * FROM word_aliases WHERE wordId = :wordId ORDER BY value ASC")
    fun observeAliases(wordId: String): Flow<List<WordAliasEntity>>

    @Query(
        """
        SELECT
          m.bookCode AS bookCode,
          COUNT(*) AS totalCount,
          SUM(CASE WHEN c.id IS NOT NULL THEN 1 ELSE 0 END) AS learnedCount,
          SUM(
            CASE
              WHEN c.scheduledDays >= 21
                AND IFNULL(c.retrievability, 0) >= 0.85
                AND (c.dueAt IS NULL OR c.dueAt > :now)
              THEN 1 ELSE 0
            END
          ) AS masteredCount,
          SUM(CASE WHEN c.dueAt IS NOT NULL AND c.dueAt <= :now THEN 1 ELSE 0 END) AS dueCount
        FROM wordbook_memberships m
        LEFT JOIN review_cards c ON c.wordId = m.wordId AND c.bookCode = m.bookCode
        GROUP BY m.bookCode
        """,
    )
    fun observeBookProgress(now: Instant): Flow<List<BookProgressRow>>
}
