package com.zzz.androidvocab.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zzz.androidvocab.core.common.AppError
import com.zzz.androidvocab.core.common.AppException
import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.common.cardId
import com.zzz.androidvocab.core.domain.ReviewRepository
import com.zzz.androidvocab.core.domain.SettingsRepository
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ReviewCard
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
import com.zzz.androidvocab.core.model.ReviewDataRepairResult
import com.zzz.androidvocab.core.model.ReviewQueueItem
import com.zzz.androidvocab.core.model.ReviewResult
import com.zzz.androidvocab.core.model.ReviewState
import com.zzz.androidvocab.core.model.SubmitFeedbackCommand
import com.zzz.androidvocab.core.model.ThemeMode
import com.zzz.androidvocab.core.model.TodayQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Suppress("LargeClass")
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
        cleanExportsPath()
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
        cleanExportsPath()
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
            assertEquals("SHA-256", payload["checksumAlgorithm"]?.jsonPrimitive?.content)
            assertEquals("payload-json-excluding-checksum", payload["checksumScope"]?.jsonPrimitive?.content)
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
            assertEquals(expectedChecksum(payload), payload["checksum"]?.jsonPrimitive?.content)
            assertTrue(
                database
                    .exportDao()
                    .settingsSnapshot()
                    ?.json
                    ?.contains("dailyNewLimit") == true,
            )
        }

    @Test
    fun exportUserDataFailsClearlyWhenExportsPathIsNotDirectory() =
        runTest {
            cleanExportsPath()
            File(context.filesDir, EXPORTS_DIRECTORY_NAME).writeText("not-a-directory")

            val error = runCatching { repository.exportUserData() }.exceptionOrNull()

            val appException = error as AppException
            val exportError = appException.error as AppError.ExportFailed
            assertTrue(exportError.reason.contains("Export path is not a directory"))
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
            assertEquals("61000", stats?.get("durationMs")?.jsonPrimitive?.content)
            assertEquals("0.5", stats?.get("recallAccuracy")?.jsonPrimitive?.content)
            assertEquals("2", stats?.get("estimatedMinutes")?.jsonPrimitive?.content)
        }

    @Test
    fun exportUserDataDerivesDailyStatsFromReviewLogsWhenCacheIsStale() =
        runTest {
            seedReviewRowsWithoutDailyStats()
            database.statsDao().upsertDailyStats(
                DailyStatsEntity(
                    localDay = "2026-05-16",
                    newCount = 99,
                    reviewCount = 99,
                    againCount = 99,
                    hardCount = 99,
                    goodCount = 99,
                    easyCount = 99,
                    completedCount = 99,
                    recallAccuracy = 0.0,
                    passRate = 0.0,
                    estimatedMinutes = 99,
                    updatedAt = clock.now().minusSeconds(60),
                ),
            )

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
            assertEquals("61000", stats?.get("durationMs")?.jsonPrimitive?.content)
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

    @Test
    fun exportUserDataKeepsMalformedSourceManifestAsString() =
        runTest {
            seedExportRows()
            database.wordDao().upsertSourceManifest(
                SourceManifestEntity(
                    generatedAt = "2026-05-16",
                    json = "{bad-json",
                    importedAt = clock.now().plusSeconds(60),
                ),
            )

            val result = repository.exportUserData()
            val payload = json.parseToJsonElement(File(result.absolutePath).readText()).jsonObject

            assertEquals("{bad-json", payload["vocabularyManifest"]?.jsonPrimitive?.content)
            assertEquals(expectedChecksum(payload), payload["checksum"]?.jsonPrimitive?.content)
        }

    @Test
    fun exportUserDataRecordsUnavailableIntegrityWhenInspectionFails() =
        runTest {
            seedExportRows()
            reviewRepository.inspectFailure = IllegalStateException("integrity check failed")

            val result = repository.exportUserData()
            val payload = json.parseToJsonElement(File(result.absolutePath).readText()).jsonObject
            val dataIntegrity = payload["dataIntegrity"]?.jsonObject

            assertEquals("unavailable", dataIntegrity?.get("status")?.jsonPrimitive?.content)
            assertEquals("integrity check failed", dataIntegrity?.get("error")?.jsonPrimitive?.content)
            assertEquals(expectedChecksum(payload), payload["checksum"]?.jsonPrimitive?.content)
        }

    @Test
    fun exportUserDataSerializesDailyStatsIntegrityFields() =
        runTest {
            seedExportRows()
            reviewRepository.report =
                ReviewDataIntegrityReport(
                    cardsWithLogs = 1,
                    missingCacheCount = 0,
                    inconsistentCacheCount = 0,
                    legacyLogCardCount = 0,
                    dailyStatsDays = 2,
                    missingDailyStatsCount = 1,
                    inconsistentDailyStatsCount = 1,
                    timelineConflictCardCount = 1,
                )

            val result = repository.exportUserData()
            val payload = json.parseToJsonElement(File(result.absolutePath).readText()).jsonObject
            val dataIntegrity = payload["dataIntegrity"]?.jsonObject

            assertEquals("issues", dataIntegrity?.get("status")?.jsonPrimitive?.content)
            assertEquals("2", dataIntegrity?.get("dailyStatsDays")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("missingDailyStatsCount")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("inconsistentDailyStatsCount")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("timelineConflictCardCount")?.jsonPrimitive?.content)
        }

    @Test
    fun exportUserDataKeepsPendingLogIntegrityClassification() =
        runTest {
            seedExportRows()
            reviewRepository.report =
                ReviewDataIntegrityReport(
                    cardsWithLogs = 1,
                    missingCacheCount = 1,
                    inconsistentCacheCount = 0,
                    legacyLogCardCount = 1,
                    repairableIssueCount = 0,
                )

            val result = repository.exportUserData()
            val payload = json.parseToJsonElement(File(result.absolutePath).readText()).jsonObject
            val dataIntegrity = payload["dataIntegrity"]?.jsonObject

            assertEquals("issues", dataIntegrity?.get("status")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("missingCacheCount")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("legacyLogCardCount")?.jsonPrimitive?.content)
            assertEquals("0", dataIntegrity?.get("repairableIssueCount")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("manualReviewIssueCount")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("issueCount")?.jsonPrimitive?.content)
        }

    @Test
    fun exportUserDataIncludesMalformedLogCardIntegrityClassification() =
        runTest {
            seedExportRows()
            reviewRepository.report =
                ReviewDataIntegrityReport(
                    cardsWithLogs = 1,
                    missingCacheCount = 0,
                    inconsistentCacheCount = 0,
                    legacyLogCardCount = 0,
                    malformedLogCardCount = 1,
                    repairableIssueCount = 0,
                )

            val result = repository.exportUserData()
            val payload = json.parseToJsonElement(File(result.absolutePath).readText()).jsonObject
            val dataIntegrity = payload["dataIntegrity"]?.jsonObject

            assertEquals("issues", dataIntegrity?.get("status")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("malformedLogCardCount")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("manualReviewIssueCount")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("issueCount")?.jsonPrimitive?.content)
        }

    @Test
    fun exportUserDataReportsPendingLogIntegrityFromRealRepository() =
        runTest {
            val reviewedAt = Instant.parse("2026-05-16T08:00:00Z")
            seedWord(database, clock, wordId = "pending-word", value = "pending", orderIndex = 0)
            val pendingCardId = cardId("pending-word", BookCode.CET4.name)
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "pending-log",
                    cardId = pendingCardId,
                    wordId = "pending-word",
                    bookCode = BookCode.CET4.name,
                    rating = "good",
                    reviewedAt = reviewedAt,
                    localDay = "2026-05-16",
                    elapsedDays = null,
                    scheduledDaysBefore = 0,
                    scheduledDaysAfter = null,
                    difficultyBefore = null,
                    difficultyAfter = null,
                    stabilityBefore = null,
                    stabilityAfter = null,
                    retrievabilityBefore = null,
                    retrievabilityAfter = null,
                    durationMs = 500,
                    targetRetention = 0.9,
                    algorithm = "fsrs",
                    algorithmVersion = PENDING_REVIEW_LOG_VERSION,
                    stateAfter = null,
                    dueAtAfter = null,
                ),
            )
            val derivedStats = database.statsDao().dailyStatsFromLogs("2026-05-16", clock.now())
            database.statsDao().upsertDailyStats(checkNotNull(derivedStats))
            val exportRepository =
                AndroidExportRepository(
                    context = context,
                    exportDao = database.exportDao(),
                    reviewRepository = newOfflineReviewRepository(database, DeterministicScheduler(), clock),
                    settingsRepository = settingsRepository,
                    clockProvider = clock,
                )

            val result = exportRepository.exportUserData()
            val payload = json.parseToJsonElement(File(result.absolutePath).readText()).jsonObject
            val dataIntegrity = payload["dataIntegrity"]?.jsonObject

            assertEquals(0, payload["reviewCards"]?.jsonArray?.size)
            assertEquals(1, payload["reviewLogs"]?.jsonArray?.size)
            assertEquals(
                PENDING_REVIEW_LOG_VERSION,
                payload["reviewLogs"]
                    ?.jsonArray
                    ?.single()
                    ?.jsonObject
                    ?.get("algorithmVersion")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals("issues", dataIntegrity?.get("status")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("missingCacheCount")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("legacyLogCardCount")?.jsonPrimitive?.content)
            assertEquals("0", dataIntegrity?.get("repairableIssueCount")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("manualReviewIssueCount")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("issueCount")?.jsonPrimitive?.content)
        }

    @Suppress("LongMethod")
    @Test
    fun exportUserDataReportsMalformedLogCardIntegrityFromRealRepository() =
        runTest {
            val reviewedAt = Instant.parse("2026-05-16T08:00:00Z")
            seedWord(database, clock, wordId = "broken-card-word", value = "broken", orderIndex = 0)
            database.reviewDao().insertLog(
                ReviewLogEntity(
                    id = "broken-log",
                    cardId = "broken-card-id",
                    wordId = "broken-card-word",
                    bookCode = BookCode.CET4.name,
                    rating = "good",
                    reviewedAt = reviewedAt,
                    localDay = "2026-05-16",
                    elapsedDays = 0,
                    scheduledDaysBefore = 0,
                    scheduledDaysAfter = 3,
                    difficultyBefore = null,
                    difficultyAfter = 0.82,
                    stabilityBefore = null,
                    stabilityAfter = 3.0,
                    retrievabilityBefore = null,
                    retrievabilityAfter = 0.82,
                    durationMs = 500,
                    targetRetention = 0.82,
                    algorithm = "fsrs",
                    algorithmVersion = "test",
                    stateAfter = ReviewState.Review.name,
                    dueAtAfter = reviewedAt.plusSeconds(3 * SECONDS_PER_DAY),
                ),
            )
            val derivedStats = database.statsDao().dailyStatsFromLogs("2026-05-16", clock.now())
            database.statsDao().upsertDailyStats(checkNotNull(derivedStats))
            val exportRepository =
                AndroidExportRepository(
                    context = context,
                    exportDao = database.exportDao(),
                    reviewRepository = newOfflineReviewRepository(database, DeterministicScheduler(), clock),
                    settingsRepository = settingsRepository,
                    clockProvider = clock,
                )

            val result = exportRepository.exportUserData()
            val payload = json.parseToJsonElement(File(result.absolutePath).readText()).jsonObject
            val dataIntegrity = payload["dataIntegrity"]?.jsonObject

            assertEquals(1, payload["reviewLogs"]?.jsonArray?.size)
            assertEquals(
                "broken-card-id",
                payload["reviewLogs"]
                    ?.jsonArray
                    ?.single()
                    ?.jsonObject
                    ?.get("cardId")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals("issues", dataIntegrity?.get("status")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("missingCacheCount")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("malformedLogCardCount")?.jsonPrimitive?.content)
            assertEquals("1", dataIntegrity?.get("timelineConflictCardCount")?.jsonPrimitive?.content)
            assertEquals("2", dataIntegrity?.get("manualReviewIssueCount")?.jsonPrimitive?.content)
            assertEquals("3", dataIntegrity?.get("issueCount")?.jsonPrimitive?.content)
        }

    @Suppress("LongMethod")
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

    private fun cleanExportsPath() {
        val path = File(context.filesDir, EXPORTS_DIRECTORY_NAME)
        if (path.isDirectory) {
            path.deleteRecursively()
        } else {
            path.delete()
        }
    }

    private fun expectedChecksum(payload: JsonObject): String {
        val payloadWithoutChecksum = JsonObject(payload.filterKeys { it != "checksum" })
        val data = Json.encodeToString(payloadWithoutChecksum)
        val hash = MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val EXPORTS_DIRECTORY_NAME = "exports"
    }
}

private class FixedExportClock : ClockProvider {
    override fun now(): Instant = Instant.parse("2026-05-16T08:09:10Z")

    override fun zoneId(): ZoneId = ZoneId.of("UTC")

    override fun today(): LocalDate = LocalDate.parse("2026-05-16")
}

private const val PENDING_REVIEW_LOG_VERSION = "<pending>"

private class FakeReviewRepository : ReviewRepository {
    var report = ReviewDataIntegrityReport(1, 0, 0, 0)
    var inspectFailure: Throwable? = null

    override fun observeTodayQueue(
        now: Instant,
        selectedBooks: Set<BookCode>,
        dailyNewLimit: Int,
    ): Flow<TodayQueue> = emptyFlow()

    override suspend fun submitFeedback(command: SubmitFeedbackCommand): ReviewResult = unused()

    override suspend fun replayLogs(cardId: String): ReviewCard = unused()

    override suspend fun getQueueItem(cardId: String): ReviewQueueItem = unused()

    override suspend fun inspectReviewDataIntegrity(): ReviewDataIntegrityReport {
        inspectFailure?.let { throw it }
        return report
    }

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
