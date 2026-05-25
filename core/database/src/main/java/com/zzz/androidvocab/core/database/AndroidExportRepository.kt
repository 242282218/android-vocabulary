package com.zzz.androidvocab.core.database

import android.content.Context
import com.zzz.androidvocab.core.common.AppError
import com.zzz.androidvocab.core.common.AppException
import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.domain.ExportRepository
import com.zzz.androidvocab.core.domain.ReviewRepository
import com.zzz.androidvocab.core.domain.SettingsRepository
import com.zzz.androidvocab.core.domain.firstValue
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.ExportResult
import com.zzz.androidvocab.core.model.ReviewDataIntegrityReport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class AndroidExportRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val exportDao: ExportDao,
        private val reviewRepository: ReviewRepository,
        private val settingsRepository: SettingsRepository,
        private val clockProvider: ClockProvider,
    ) : ExportRepository {
        private val json = Json { prettyPrint = true }

        override suspend fun exportUserData(): ExportResult =
            withContext(Dispatchers.IO) {
                runCatching {
                    val exportedAt = clockProvider.now()
                    val fileName = "android-vocab-export-${DateTimeFormatter.ofPattern(
                        "yyyyMMdd-HHmmss",
                    ).withZone(clockProvider.zoneId()).format(exportedAt)}.json"
                    val exportsDir = exportDirectory()
                    val output = File(exportsDir, fileName)
                    val settings = settingsRepository.settings.firstValue()
                    val settingsJson = settings.toJson()
                    exportDao.upsertSettingsSnapshot(
                        AppSettingsSnapshotEntity(
                            json = json.encodeToString(settingsJson),
                            updatedAt = exportedAt,
                        ),
                    )
                    val payload =
                        buildJsonObject {
                            put("exportedAt", exportedAt.toString())
                            put("appVersion", appVersion())
                            put("databaseVersion", VOCAB_DATABASE_VERSION)
                            put("vocabularyManifest", exportDao.sourceManifest()?.json.toJsonPayload())
                            put("latestImportRun", exportDao.latestImportRun()?.toJson() ?: JsonNull)
                            put("algorithm", "fsrs/java-fsrs-1.0.0")
                            put("settings", settingsJson)
                            put("dataIntegrity", inspectDataIntegrity())
                            put(
                                "reviewCards",
                                buildJsonArray {
                                    exportDao.reviewCards().forEach { add(it.toJson()) }
                                },
                            )
                            put(
                                "reviewLogs",
                                buildJsonArray {
                                    exportDao.reviewLogs().forEach { add(it.toJson()) }
                                },
                            )
                            put(
                                "dailyStats",
                                buildJsonArray {
                                    exportDao.dailyStatsFromLogs().forEach { add(it.toJson(exportedAt)) }
                                },
                            )
                        }
                    output.writeText(json.encodeToString(payload))
                    ExportResult(fileName = fileName, absolutePath = output.absolutePath)
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    if (error is AppException) throw error
                    throw AppException(AppError.ExportFailed(error.message ?: "Export failed"), error)
                }
            }

        private fun exportDirectory(): File {
            val externalDir =
                context.getExternalFilesDir(null)
                    ?: error("External files directory is unavailable")
            val exportsDir = File(externalDir, "exports")
            if (!exportsDir.exists() && !exportsDir.mkdirs()) {
                error("Cannot create exports directory: ${exportsDir.absolutePath}")
            }
            if (!exportsDir.isDirectory) {
                error("Export path is not a directory: ${exportsDir.absolutePath}")
            }
            return exportsDir
        }

        private fun appVersion(): String =
            runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                info.versionName ?: "0.1.0"
            }.getOrDefault("0.1.0")

        private suspend fun inspectDataIntegrity(): JsonObject =
            runCatching { reviewRepository.inspectReviewDataIntegrity().toJson() }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    buildJsonObject {
                        put("status", "unavailable")
                        put("error", error.message ?: "Data integrity inspection failed")
                    }
                }
    }

private fun AppSettings.toJson(): JsonObject =
    buildJsonObject {
        put("dailyNewLimit", dailyNewLimit)
        put(
            "selectedBooks",
            buildJsonArray {
                selectedBooks.forEach { add(JsonPrimitive(it.name)) }
            },
        )
        put("targetRetention", targetRetention)
        put("reminderEnabled", reminderEnabled)
        put("reminderHour", reminderHour)
        put("reminderMinute", reminderMinute)
        put("themeMode", themeMode.name)
    }

private fun VocabularyImportRunEntity.toJson(): JsonObject =
    buildJsonObject {
        put("id", id)
        put("buildTarget", buildTarget)
        put("generatedAt", generatedAt)
        put("bookCounts", bookCountsJson.toJsonPayload())
        put("assetFingerprint", assetFingerprint)
        put("importedWords", importedWords)
        put("memberships", memberships)
        put("importedAt", importedAt.toString())
    }

private fun String?.toJsonPayload(): JsonElement {
    if (isNullOrBlank()) return JsonNull
    return runCatching { Json.parseToJsonElement(this) }.getOrElse { JsonPrimitive(this) }
}

private fun ReviewCardEntity.toJson(): JsonObject =
    buildJsonObject {
        put("id", id)
        put("wordId", wordId)
        put("bookCode", bookCode)
        put("state", state)
        put("difficulty", difficulty)
        put("stability", stability)
        put("retrievability", retrievability)
        put("scheduledDays", scheduledDays)
        put("dueAt", dueAt?.toString())
        put("lastReviewAt", lastReviewAt?.toString())
        put("reviewCount", reviewCount)
        put("lapseCount", lapseCount)
        put("firstReviewedAt", firstReviewedAt?.toString())
        put("createdAt", createdAt.toString())
        put("updatedAt", updatedAt.toString())
    }

private fun ReviewLogEntity.toJson(): JsonObject =
    buildJsonObject {
        put("id", id)
        put("cardId", cardId)
        put("wordId", wordId)
        put("bookCode", bookCode)
        put("rating", rating)
        put("reviewedAt", reviewedAt.toString())
        put("localDay", localDay)
        put("elapsedDays", elapsedDays)
        put("scheduledDaysBefore", scheduledDaysBefore)
        put("scheduledDaysAfter", scheduledDaysAfter)
        put("difficultyBefore", difficultyBefore)
        put("difficultyAfter", difficultyAfter)
        put("stabilityBefore", stabilityBefore)
        put("stabilityAfter", stabilityAfter)
        put("retrievabilityBefore", retrievabilityBefore)
        put("retrievabilityAfter", retrievabilityAfter)
        put("durationMs", durationMs)
        put("targetRetention", targetRetention)
        put("algorithm", algorithm)
        put("algorithmVersion", algorithmVersion)
        put("stateAfter", stateAfter)
        put("dueAtAfter", dueAtAfter?.toString())
    }

private fun ReviewDataIntegrityReport.toJson(): JsonObject =
    buildJsonObject {
        put("status", if (issueCount == 0) "ok" else "issues")
        put("cardsWithLogs", cardsWithLogs)
        put("missingCacheCount", missingCacheCount)
        put("inconsistentCacheCount", inconsistentCacheCount)
        put("legacyLogCardCount", legacyLogCardCount)
        put("orphanLogCount", orphanLogCount)
        put("repairableIssueCount", repairableIssueCount)
        put("manualReviewIssueCount", manualReviewIssueCount)
        put("issueCount", issueCount)
    }

private fun ExportDailyStatsRow.toJson(updatedAt: java.time.Instant): JsonObject {
    val reviewCount = (completedCount - newCount).coerceAtLeast(0)
    val recallAccuracy = if (completedCount == 0) 0.0 else (goodCount + easyCount).toDouble() / completedCount
    val passRate =
        if (completedCount == 0) {
            0.0
        } else {
            (hardCount + goodCount + easyCount).toDouble() / completedCount
        }
    return buildJsonObject {
        put("localDay", localDay)
        put("newCount", newCount)
        put("reviewCount", reviewCount)
        put("againCount", againCount)
        put("hardCount", hardCount)
        put("goodCount", goodCount)
        put("easyCount", easyCount)
        put("completedCount", completedCount)
        put("recallAccuracy", recallAccuracy)
        put("passRate", passRate)
        put("estimatedMinutes", durationMs.toCompletedMinutes())
        put("updatedAt", updatedAt.toString())
    }
}

private fun Long.toCompletedMinutes(): Int {
    if (this <= 0L) return 0
    return ((this + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt()
}

private const val MILLIS_PER_MINUTE = 60_000L
