package com.zzz.androidvocab.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "word_entries", indices = [Index(value = ["word"], unique = true)])
data class WordEntryEntity(
    @PrimaryKey val id: String,
    val word: String,
    val meaning: String,
    val phonetic: String?,
    val partOfSpeech: String?,
    val definition: String?,
    val cefrLevel: String?,
    val cefrRank: Double,
    val frequency: Double,
    val sourceFlagsJson: String,
    val coverageTier: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "wordbook_memberships",
    primaryKeys = ["wordId", "bookCode"],
    indices = [Index(value = ["bookCode", "orderIndex"]), Index(value = ["wordId"])],
)
data class WordBookMembershipEntity(
    val wordId: String,
    val bookCode: String,
    val orderIndex: Int,
    val examFrequencyScore: Double,
    val examPriorityScore: Double,
    val isPhraseBacked: Boolean,
    val phraseCount: Int,
)

@Entity(tableName = "word_aliases", primaryKeys = ["wordId", "value"], indices = [Index(value = ["wordId"])])
data class WordAliasEntity(
    val wordId: String,
    val value: String,
)

@Entity(
    tableName = "review_cards",
    indices = [
        Index(value = ["wordId", "bookCode"], unique = true),
        Index(value = ["bookCode", "dueAt"]),
        Index(value = ["state"]),
    ],
)
data class ReviewCardEntity(
    @PrimaryKey val id: String,
    val wordId: String,
    val bookCode: String,
    val state: String,
    val difficulty: Double?,
    val stability: Double?,
    val retrievability: Double?,
    val scheduledDays: Int,
    val dueAt: Instant?,
    val lastReviewAt: Instant?,
    val reviewCount: Int,
    val lapseCount: Int,
    val firstReviewedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "review_logs",
    indices = [
        Index(value = ["localDay"]),
        Index(value = ["wordId", "reviewedAt"]),
        Index(value = ["cardId", "reviewedAt"]),
    ],
)
data class ReviewLogEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val wordId: String,
    val bookCode: String,
    val rating: String,
    val reviewedAt: Instant,
    val localDay: String,
    val elapsedDays: Int?,
    val scheduledDaysBefore: Int?,
    val scheduledDaysAfter: Int?,
    val difficultyBefore: Double?,
    val difficultyAfter: Double?,
    val stabilityBefore: Double?,
    val stabilityAfter: Double?,
    val retrievabilityBefore: Double?,
    val retrievabilityAfter: Double?,
    val durationMs: Long,
    val targetRetention: Double,
    val algorithm: String,
    val algorithmVersion: String,
    val stateAfter: String? = null,
    val dueAtAfter: Instant? = null,
)

@Entity(tableName = "daily_stats")
data class DailyStatsEntity(
    @PrimaryKey val localDay: String,
    val newCount: Int,
    val reviewCount: Int,
    val againCount: Int,
    val hardCount: Int,
    val goodCount: Int,
    val easyCount: Int,
    val completedCount: Int,
    val recallAccuracy: Double,
    val passRate: Double,
    val estimatedMinutes: Int,
    val updatedAt: Instant,
)

@Entity(tableName = "app_settings_snapshot")
data class AppSettingsSnapshotEntity(
    @PrimaryKey val id: String = "current",
    val json: String,
    val updatedAt: Instant,
)

@Entity(tableName = "source_manifest")
data class SourceManifestEntity(
    @PrimaryKey val id: String = "publish-safe",
    val generatedAt: String,
    val json: String,
    val importedAt: Instant,
)

@Entity(tableName = "vocabulary_import_runs")
data class VocabularyImportRunEntity(
    @PrimaryKey val id: String,
    val buildTarget: String,
    val generatedAt: String,
    val bookCountsJson: String,
    val importedWords: Int,
    val memberships: Int,
    val importedAt: Instant,
)
