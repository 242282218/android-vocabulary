package com.zzz.androidvocab.core.database

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zzz.androidvocab.core.common.AppError
import com.zzz.androidvocab.core.common.AppException
import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.domain.ReviewRepository
import com.zzz.androidvocab.core.domain.SettingsRepository
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
import com.zzz.androidvocab.core.model.ReviewDataRepairResult
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewResult
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import com.zzz.androidvocab.core.model.ThemeMode
import com.zzz.androidvocab.core.model.TodayQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class AndroidExportRepositoryTest {
    private lateinit var database: VocabDatabase
    private lateinit var context: Context
    private lateinit var repository: AndroidExportRepository
    private val clock = FixedExportClock()
    private val settingsRepository = FakeSettingsRepository()
    private val reviewRepository = FakeReviewRepository()
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room
                .inMemoryDatabaseBuilder(context, VocabDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            AndroidExportRepository(
                context = context,
                exportDao = database.exportDao(),
                reviewRepository = reviewRepository,
                settingsRepository = settingsRepository,
                clockProvider = clock,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportUserDataWritesStructuredJson() =
        runTest {
            seedExportRows()

            val result = repository.exportUserData()
            val payload = json.parseToJsonElement(File(result.absolutePath).readText()).jsonObject

            assertEquals("android-vocab-export-20260516-080910.json", result.fileName)
            assertEquals("2026-05-16T08:09:10Z", payload["exportedAt"]?.jsonPrimitive?.content)
            assertEquals(VOCAB_DATABASE_VERSION.toString(), payload["databaseVersion"]?.jsonPrimitive?.content)
            assertEquals("fsrs/java-fsrs-1.0.0", payload["algorithm"]?.jsonPrimitive?.content)
            assertEquals(
                "publish",
                payload["vocabularyManifest"]
                    ?.jsonObject
                    ?.get("buildTarget")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "import-1",
                payload["latestImportRun"]
                    ?.jsonObject
                    ?.get("id")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "Dark",
                payload["settings"]
                    ?.jsonObject
                    ?.get("themeMode")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "ok",
                payload["dataIntegrity"]
                    ?.jsonObject
                    ?.get("status")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "1",
                payload["dataIntegrity"]
                    ?.jsonObject
                    ?.get("cardsWithLogs")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "card-1",
                payload["reviewCards"]
                    ?.jsonArray
                    ?.first()
                    ?.jsonObject
                    ?.get("id")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "good",
                payload["reviewLogs"]
                    ?.jsonArray
                    ?.first()
                    ?.jsonObject
                    ?.get("rating")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "Review",
                payload["reviewLogs"]
                    ?.jsonArray
                    ?.first()
                    ?.jsonObject
                    ?.get("stateAfter")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "2026-05-16",
                payload["dailyStats"]
                    ?.jsonArray
                    ?.first()
                    ?.jsonObject
                    ?.get("localDay")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertTrue(
                database
                    .exportDao()
                    .settingsSnapshot()
                    ?.json
                    ?.contains("dailyNewLimit") == true,
            )
        }

    @Test
    fun exportUserDataFailsClearlyWhenExternalFilesDirIsUnavailable() =
        runTest {
            val nullExternalRepository =
                AndroidExportRepository(
                    context = NullExternalFilesContext(context),
                    exportDao = database.exportDao(),
                    reviewRepository = reviewRepository,
                    settingsRepository = settingsRepository,
                    clockProvider = clock,
                )

            val error = runCatching { nullExternalRepository.exportUserData() }.exceptionOrNull()

            val appException = error as AppException
            val exportError = appException.error as AppError.ExportFailed
            assertTrue(exportError.reason.contains("External files directory is unavailable"))
        }

    @Test
    fun exportUserDataDerivesDailyStatsFromReviewLogsWhenCacheIsMissing() =
        runTest {
            seedReviewRowsWithoutDailyStats()

            val result = repository.exportUserData()
            val payload = json.parseToJsonElement(File(result.absolutePath).readText()).jsonObject
            val stats =
                payload["dailyStats"]
                    ?.jsonArray
                    ?.single()
                    ?.jsonObject

            assertEquals("2026-05-16", stats?.get("localDay")?.jsonPrimitive?.content)
            assertEquals("1", stats?.get("newCount")?.jsonPrimitive?.content)
            assertEquals("1", stats?.get("reviewCount")?.jsonPrimitive?.content)
            assertEquals("2", stats?.get("completedCount")?.jsonPrimitive?.content)
            assertEquals("1", stats?.get("againCount")?.jsonPrimitive?.content)
            assertEquals("1", stats?.get("goodCount")?.jsonPrimitive?.content)
            assertEquals("0.5", stats?.get("recallAccuracy")?.jsonPrimitive?.content)
            assertEquals("2", stats?.get("estimatedMinutes")?.jsonPrimitive?.content)
        }

    @Test
    fun exportUserDataExcludesOrphanReviewRows() =
        runTest {
            seedExportRows()
            reviewRepository.report =
                ReviewDataIntegrityReport(
                    cardsWithLogs = 1,
                    missingCacheCount = 0,
                    inconsistentCacheCount = 0,
                    legacyLogCardCount = 0,
                    orphanLogCount = 1,
                )
            val now = clock.now()
            database.reviewDao().upsertCard(
                ReviewCardEntity(
                    id = "orphan-card",
                    wordId = "orphan-word",
                    bookCode = BookCode.CET4.name,
                    state = "Review",
                    difficulty = 5.0,
                    stability = 10.0,
                    retrievability = 0.9,
                    scheduledDays = 10,
                    dueAt = now.plusSeconds(86_400),
                    lastReviewAt = now,
                    reviewCount = 1,
                    lapseCount = 0,
                    firstReviewedAt = now,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.reviewDao().insertLog(
                reviewLog(
                    id = "orphan-log",
                    cardId = "orphan-card",
                    wordId = "orphan-word",
                    rating = "again",
                    reviewedAt = now.plusSeconds(60),
                    durationMs = 1_000,
                ),
            )

            val result = repository.exportUserData()
            val payload = json.parseToJsonElement(File(result.absolutePath).readText()).jsonObject

            assertEquals(1, payload["reviewCards"]?.jsonArray?.size)
            assertEquals(1, payload["reviewLogs"]?.jsonArray?.size)
            assertEquals(
                "card-1",
                payload["reviewCards"]
                    ?.jsonArray
                    ?.single()
                    ?.jsonObject
                    ?.get("id")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "log-1",
                payload["reviewLogs"]
                    ?.jsonArray
                    ?.single()
                    ?.jsonObject
                    ?.get("id")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "issues",
                payload["dataIntegrity"]
                    ?.jsonObject
                    ?.get("status")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "1",
                payload["dataIntegrity"]
                    ?.jsonObject
                    ?.get("orphanLogCount")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun exportUserDataKeepsMalformedImportRunBookCountsAsString() =
        runTest {
            seedExportRows()
            val now = clock.now()
            database.wordDao().insertImportRun(
                VocabularyImportRunEntity(
                    id = "import-malformed",
                    buildTarget = "publish",
                    generatedAt = "2026-05-16",
                    bookCountsJson = "{bad-json",
                    assetFingerprint = "fingerprint-b",
                    importedWords = 1,
                    memberships = 1,
                    importedAt = now.plusSeconds(60),
                ),
            )

            val result = repository.exportUserData()
            val payload = json.parseToJsonElement(File(result.absolutePath).readText()).jsonObject
            val latestImportRun = payload["latestImportRun"]?.jsonObject

            assertEquals("import-malformed", latestImportRun?.get("id")?.jsonPrimitive?.content)
            assertEquals("{bad-json", latestImportRun?.get("bookCounts")?.jsonPrimitive?.content)
        }

    private suspend fun seedExportRows() {
        val now = clock.now()
        seedWordMembership("word-1")
        database.wordDao().upsertSourceManifest(
            SourceManifestEntity(
                generatedAt = "2026-05-16",
                json = """{"buildTarget":"publish"}""",
                importedAt = now,
            ),
        )
        database.wordDao().insertImportRun(
            VocabularyImportRunEntity(
                id = "import-1",
                buildTarget = "publish",
                generatedAt = "2026-05-16",
                bookCountsJson = """{"CET4":3846}""",
                assetFingerprint = "fingerprint-a",
                importedWords = 3846,
                memberships = 3846,
                importedAt = now,
            ),
        )
        database.reviewDao().upsertCard(
            ReviewCardEntity(
                id = "card-1",
                wordId = "word-1",
                bookCode = BookCode.CET4.name,
                state = "Review",
                difficulty = 5.0,
                stability = 10.0,
                retrievability = 0.9,
                scheduledDays = 10,
                dueAt = now.plusSeconds(86_400),
                lastReviewAt = now,
                reviewCount = 1,
                lapseCount = 0,
                firstReviewedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
        database.reviewDao().insertLog(
            ReviewLogEntity(
                id = "log-1",
                cardId = "card-1",
                wordId = "word-1",
                bookCode = BookCode.CET4.name,
                rating = "good",
                reviewedAt = now,
                localDay = "2026-05-16",
                elapsedDays = null,
                scheduledDaysBefore = 0,
                scheduledDaysAfter = 10,
                difficultyBefore = null,
                difficultyAfter = 5.0,
                stabilityBefore = null,
                stabilityAfter = 10.0,
                retrievabilityBefore = null,
                retrievabilityAfter = 0.9,
                durationMs = 700,
                targetRetention = 0.9,
                algorithm = "fsrs",
                algorithmVersion = "test",
                stateAfter = "Review",
                dueAtAfter = now.plusSeconds(86_400),
            ),
        )
        database.statsDao().upsertDailyStats(
            DailyStatsEntity(
                localDay = "2026-05-16",
                newCount = 1,
                reviewCount = 0,
                againCount = 0,
                hardCount = 0,
                goodCount = 1,
                easyCount = 0,
                completedCount = 1,
                recallAccuracy = 1.0,
                passRate = 1.0,
                estimatedMinutes = 1,
                updatedAt = now,
            ),
        )
    }

    private suspend fun seedReviewRowsWithoutDailyStats() {
        val now = clock.now()
        val firstReviewAt = now.minusSeconds(3_600)
        seedWordMembership("word-logs-only")
        database.reviewDao().upsertCard(
            ReviewCardEntity(
                id = "card-logs-only",
                wordId = "word-logs-only",
                bookCode = BookCode.CET4.name,
                state = "Review",
                difficulty = 5.0,
                stability = 10.0,
                retrievability = 0.9,
                scheduledDays = 10,
                dueAt = now.plusSeconds(86_400),
                lastReviewAt = now,
                reviewCount = 2,
                lapseCount = 1,
                firstReviewedAt = firstReviewAt,
                createdAt = firstReviewAt,
                updatedAt = now,
            ),
        )
        database.reviewDao().insertLog(
            reviewLog(
                id = "log-first",
                cardId = "card-logs-only",
                wordId = "word-logs-only",
                rating = "good",
                reviewedAt = firstReviewAt,
                durationMs = 30_000,
            ),
        )
        database.reviewDao().insertLog(
            reviewLog(
                id = "log-second",
                cardId = "card-logs-only",
                wordId = "word-logs-only",
                rating = "again",
                reviewedAt = now,
                durationMs = 31_000,
            ),
        )
    }

    private fun reviewLog(
        id: String,
        cardId: String,
        wordId: String,
        rating: String,
        reviewedAt: Instant,
        durationMs: Long,
    ) = ReviewLogEntity(
        id = id,
        cardId = cardId,
        wordId = wordId,
        bookCode = BookCode.CET4.name,
        rating = rating,
        reviewedAt = reviewedAt,
        localDay = "2026-05-16",
        elapsedDays = null,
        scheduledDaysBefore = 0,
        scheduledDaysAfter = 10,
        difficultyBefore = null,
        difficultyAfter = 5.0,
        stabilityBefore = null,
        stabilityAfter = 10.0,
        retrievabilityBefore = null,
        retrievabilityAfter = 0.9,
        durationMs = durationMs,
        targetRetention = 0.9,
        algorithm = "fsrs",
        algorithmVersion = "test",
    )

    private suspend fun seedWordMembership(wordId: String) {
        val now = clock.now()
        database.wordDao().upsertWords(
            listOf(
                WordEntryEntity(
                    id = wordId,
                    word = wordId.removePrefix("word-"),
                    meaning = "meaning",
                    phonetic = null,
                    partOfSpeech = null,
                    definition = null,
                    cefrLevel = null,
                    cefrRank = 0.0,
                    frequency = 0.0,
                    sourceFlagsJson = "[]",
                    coverageTier = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        database.wordDao().upsertMemberships(
            listOf(
                WordBookMembershipEntity(
                    wordId = wordId,
                    bookCode = BookCode.CET4.name,
                    orderIndex = 0,
                    examFrequencyScore = 0.0,
                    examPriorityScore = 0.0,
                    isPhraseBacked = false,
                    phraseCount = 0,
                ),
            ),
        )
    }
}

private class NullExternalFilesContext(
    base: Context,
) : ContextWrapper(base) {
    override fun getExternalFilesDir(type: String?): File? = null
}

private class FixedExportClock : ClockProvider {
    override fun now(): Instant = Instant.parse("2026-05-16T08:09:10Z")

    override fun zoneId(): ZoneId = ZoneId.of("UTC")

    override fun today(): LocalDate = LocalDate.parse("2026-05-16")
}

private class FakeReviewRepository : ReviewRepository {
    var report = ReviewDataIntegrityReport(1, 0, 0, 0)

    override fun observeTodayQueue(
        now: Instant,
        selectedBooks: Set<BookCode>,
        dailyNewLimit: Int,
    ): Flow<TodayQueue> = emptyFlow()

    override suspend fun submitFeedback(command: SubmitFeedbackCommand): ReviewResult = unused()

    override suspend fun replayLogs(cardId: String): ReviewCard = unused()

    override suspend fun getQueueItem(cardId: String): ReviewQueueItem = unused()

    override suspend fun inspectReviewDataIntegrity(): ReviewDataIntegrityReport = report

    override suspend fun repairReviewDataCache(): ReviewDataRepairResult =
        ReviewDataRepairResult(before = report, after = report, repairedCount = 0)
}

private class FakeSettingsRepository : SettingsRepository {
    override val settings: Flow<AppSettings> =
        MutableStateFlow(
            AppSettings(
                selectedBooks = setOf(BookCode.CET4, BookCode.CET6),
                reminderEnabled = true,
                reminderHour = 21,
                reminderMinute = 30,
                themeMode = ThemeMode.Dark,
            ),
        )

    override suspend fun updateDailyNewLimit(value: Int) = Unit

    override suspend fun updateSelectedBooks(bookCodes: Set<BookCode>) = Unit

    override suspend fun toggleBook(bookCode: BookCode) = Unit

    override suspend fun updateTargetRetention(value: Double) = Unit

    override suspend fun updateReminder(
        enabled: Boolean,
        hour: Int,
        minute: Int,
    ) = Unit

    override suspend fun updateReminderEnabled(enabled: Boolean) = Unit

    override suspend fun updateReminderTime(
        hour: Int,
        minute: Int,
    ) = Unit

    override suspend fun updateThemeMode(themeMode: ThemeMode) = Unit
}

private fun unused(): Nothing = error("unused")
