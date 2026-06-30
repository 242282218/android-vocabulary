package com.zzz.androidvocab.core.vocabulary

import android.content.Context
import androidx.room.withTransaction
import com.zzz.androidvocab.core.common.AppError
import com.zzz.androidvocab.core.common.AppException
import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.common.stableId
import com.zzz.androidvocab.core.common.wordId
import com.zzz.androidvocab.core.database.ReviewDao
import com.zzz.androidvocab.core.database.SourceManifestEntity
import com.zzz.androidvocab.core.database.VocabDatabase
import com.zzz.androidvocab.core.database.VocabularyImportRunEntity
import com.zzz.androidvocab.core.database.WordAliasEntity
import com.zzz.androidvocab.core.database.WordBookMembershipEntity
import com.zzz.androidvocab.core.database.WordDao
import com.zzz.androidvocab.core.database.WordEntryEntity
import com.zzz.androidvocab.core.database.toModel
import com.zzz.androidvocab.core.domain.SettingsRepository
import com.zzz.androidvocab.core.domain.VocabularyRepository
import com.zzz.androidvocab.core.domain.calculateBookProgress
import com.zzz.androidvocab.core.domain.filterWordsByStatus
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.ImportResult
import com.zzz.androidvocab.core.model.SourceInfo
import com.zzz.androidvocab.core.model.WordDetail
import com.zzz.androidvocab.core.model.WordEntry
import com.zzz.androidvocab.core.model.WordStatusFilter
import com.zzz.androidvocab.core.model.effectiveSelectedBookCodeNames
import com.zzz.androidvocab.core.model.effectiveSelectedBookCodes
import com.zzz.androidvocab.core.model.toBookCodeOrNull
import com.zzz.androidvocab.core.scheduler.ReviewScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val BUILD_TARGET_PUBLISH = "publish"

class AssetVocabularyRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: VocabDatabase,
        private val wordDao: WordDao,
        private val reviewDao: ReviewDao,
        private val scheduler: ReviewScheduler,
        private val clockProvider: ClockProvider,
        private val settingsRepository: SettingsRepository,
    ) : VocabularyRepository {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        override fun observeBookProgress(now: Instant): Flow<List<BookProgress>> =
            combine(
                wordDao.observeMembershipCounts(),
                wordDao.observeValidReviewCards(),
                settingsRepository.settings,
            ) { totals, cards, settings ->
                val totalByBook =
                    totals
                        .mapNotNull { row ->
                            row.bookCode.toBookCodeOrNull()?.let { book -> book to row.count }
                        }.toMap()
                calculateBookProgress(
                    totals = totalByBook,
                    cards = cards.map { it.toModel() },
                    now = now,
                    retrievability = { card -> scheduler.retrievability(card, now, settings.targetRetention) },
                )
            }

        override fun searchWords(
            query: String,
            bookCodes: Set<BookCode>,
            statusFilter: WordStatusFilter,
            now: Instant,
        ): Flow<List<WordEntry>> =
            combine(
                wordDao.searchWords(escapeLikeWildcards(query.trim()), bookCodes.effectiveSelectedBookCodeNames()),
                wordDao.observeValidReviewCards(),
                settingsRepository.settings,
            ) { rows, cards, settings ->
                filterWordsByStatus(
                    words = rows.map { it.toModel() },
                    cards = cards.map { it.toModel() },
                    selectedBooks = bookCodes.effectiveSelectedBookCodes().toSet(),
                    statusFilter = statusFilter,
                    now = now,
                    retrievability = { card -> scheduler.retrievability(card, now, settings.targetRetention) },
                ).take(SEARCH_RESULT_LIMIT)
            }

        override fun observeWordDetail(wordId: String): Flow<WordDetail?> =
            combine(
                wordDao.observeWord(wordId),
                wordDao.observeMemberships(wordId),
                wordDao.observeAliases(wordId),
            ) { word, memberships, aliases ->
                word?.let {
                    WordDetail(
                        entry = it.toModel(),
                        memberships = memberships.map { membership -> membership.toModel() },
                        aliases = aliases.map { alias -> alias.value },
                    )
                }
            }

        override fun observeSourceInfo(): Flow<List<SourceInfo>> =
            wordDao.observeSourceManifest().map { entity ->
                entity?.json?.let { parseSourceInfo(it) }.orEmpty()
            }

        override suspend fun importPublishSafeVocabulary(): ImportResult =
            withContext(Dispatchers.IO) {
                runCatching {
                    val now = clockProvider.now()
                    val manifestText = readAsset("vocab/manifest.json")
                    val manifest = json.decodeFromString<VocabManifest>(manifestText)
                    if (manifest.buildTarget != BUILD_TARGET_PUBLISH) {
                        throw AppException(
                            AppError.VocabularyImportFailed("Vocabulary manifest is not publish-safe"),
                        )
                    }
                    val existingBookCounts =
                        wordDao
                            .membershipCounts()
                            .mapNotNull { row ->
                                row.bookCode.toBookCodeOrNull()?.let { it to row.count }
                            }.toMap()
                    if (
                        isCurrentPublishSafeImport(
                            manifest = manifest,
                            latestImportRun = wordDao.latestImportRun(),
                            hasSourceManifest = wordDao.sourceManifest() != null,
                            bookCounts = existingBookCounts,
                        )
                    ) {
                        ImportResult(
                            importedWords = wordDao.wordCount(),
                            memberships = existingBookCounts.values.sum(),
                            bookCounts = existingBookCounts,
                            generatedAt = manifest.generatedAt,
                        )
                    } else {
                        val validated = validateAndLoadAssets(manifest)
                        rebuildDatabase(now, manifest, validated)
                    }
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    if (error is AppException) throw error
                    throw AppException(
                        AppError.VocabularyImportFailed(error.message ?: "Import failed"),
                        error,
                    )
                }
            }

        private fun validateAndLoadAssets(manifest: VocabManifest): ValidatedAssets {
            val sourcesText = readAsset("vocab/sources.json")
            val sourcesHash = validateSourcesHash(manifest, sourcesText)
            val sourceManifest = json.decodeFromString<AssetSourceManifest>(sourcesText)
            val bookHashes = mutableMapOf<BookCode, String>()
            val rows =
                BookCode.entries.associateWith { book ->
                    val bookManifest =
                        manifest.books[book.name]
                            ?: throw AppException(
                                AppError.VocabularyImportFailed("Missing manifest entry: ${book.name}"),
                            )
                    val entriesText = readAsset("vocab/${bookManifest.file}")
                    val entries = json.decodeFromString<List<AssetWordEntry>>(entriesText)
                    val bookHash = sha256Hex(entriesText)
                    validateBook(book, bookManifest, entries, bookHash)
                    bookHashes[book] = bookHash
                    entries
                }
            validateAssetFingerprint(manifest, bookHashes, sourcesHash)
            return ValidatedAssets(rows, sourceManifest, sourcesText)
        }

        private suspend fun rebuildDatabase(
            now: java.time.Instant,
            manifest: VocabManifest,
            validated: ValidatedAssets,
        ): ImportResult =
            database.withTransaction {
                wordDao.deleteAliases()
                wordDao.deleteMemberships()
                wordDao.deleteWords()
                val words = mutableMapOf<String, WordEntryEntity>()
                val memberships = mutableListOf<WordBookMembershipEntity>()
                val aliases = mutableListOf<WordAliasEntity>()
                validated.rows.forEach { (book, entries) ->
                    entries.forEachIndexed { index, entry ->
                        val normalized = entry.word.trim().lowercase()
                        val id = wordId(normalized)
                        words.putIfAbsent(id, entry.toWordEntity(id, normalized, now))
                        memberships += entry.toMembershipEntity(id, book, index)
                        aliases += entry.searchAliasValues().map { WordAliasEntity(id, it) }
                    }
                }
                wordDao.upsertWords(words.values.toList())
                wordDao.upsertMemberships(memberships)
                wordDao.upsertAliases(aliases.distinct())
                wordDao.upsertSourceManifest(
                    SourceManifestEntity(
                        generatedAt = validated.sourceManifest.generatedAt,
                        json = validated.sourcesText,
                        importedAt = now,
                    ),
                )
                val bookCounts =
                    BookCode.entries.associateWith {
                        wordDao.membershipCount(it.name)
                    }
                validateImportedCounts(bookCounts)
                reviewDao.deleteCardsWithoutMembership()
                wordDao.insertImportRun(
                    VocabularyImportRunEntity(
                        id = stableId("import", now.toString()),
                        buildTarget = manifest.buildTarget,
                        generatedAt = manifest.generatedAt,
                        bookCountsJson =
                            json.encodeToString(
                                bookCounts.mapKeys { it.key.name },
                            ),
                        assetFingerprint = manifest.assetFingerprint,
                        importedWords = wordDao.wordCount(),
                        memberships = bookCounts.values.sum(),
                        importedAt = now,
                    ),
                )
                ImportResult(
                    importedWords = wordDao.wordCount(),
                    memberships = bookCounts.values.sum(),
                    bookCounts = bookCounts,
                    generatedAt = manifest.generatedAt,
                )
            }

        private fun readAsset(path: String): String =
            context.assets
                .open(path)
                .bufferedReader()
                .use { it.readText().removePrefix(UTF8_BOM) }

        private fun validateBook(
            book: BookCode,
            manifest: VocabManifestBook,
            entries: List<AssetWordEntry>,
            fileHash: String,
        ) {
            val errorReason =
                bookValidationError(
                    book = book,
                    manifest = manifest,
                    entries = entries,
                    fileHash = fileHash,
                )
            if (errorReason != null) {
                throw AppException(AppError.VocabularyImportFailed(errorReason))
            }
        }

        private fun validateSourcesHash(
            manifest: VocabManifest,
            sourcesText: String,
        ): String {
            if (manifest.sourcesHash.isBlank()) {
                throw AppException(AppError.VocabularyImportFailed("sources hash is missing"))
            }
            val sourcesHash = sha256Hex(sourcesText)
            if (!manifest.sourcesHash.equals(sourcesHash, ignoreCase = true)) {
                throw AppException(AppError.VocabularyImportFailed("sources hash mismatch"))
            }
            return sourcesHash
        }

        private fun validateAssetFingerprint(
            manifest: VocabManifest,
            bookHashes: Map<BookCode, String>,
            sourcesHash: String,
        ) {
            if (manifest.assetFingerprint.isBlank()) {
                throw AppException(AppError.VocabularyImportFailed("asset fingerprint is missing"))
            }
            val actualFingerprint = assetFingerprint(bookHashes, sourcesHash)
            if (!manifest.assetFingerprint.equals(actualFingerprint, ignoreCase = true)) {
                throw AppException(AppError.VocabularyImportFailed("asset fingerprint mismatch"))
            }
        }

        private fun bookValidationError(
            book: BookCode,
            manifest: VocabManifestBook,
            entries: List<AssetWordEntry>,
            fileHash: String,
        ): String? {
            if (
                manifest.count != book.expectedPublishSafeCount ||
                entries.size != book.expectedPublishSafeCount
            ) {
                return "${book.name} count mismatch"
            }
            if (manifest.hash.isBlank()) {
                return "${book.name} hash is missing"
            }
            if (!manifest.hash.equals(fileHash, ignoreCase = true)) {
                return "${book.name} hash mismatch"
            }
            val blocking =
                entries.firstOrNull { row ->
                    row.sourceFlags.any { it.trim().lowercase() in PUBLISH_BLOCKING_SOURCE_FLAGS }
                }
            if (blocking != null) {
                return "${book.name} contains publish-blocking source flags"
            }
            val invalid =
                entries.firstOrNull {
                    it.word.isBlank() || it.meaning.isBlank()
                }
            if (invalid != null) {
                return "${book.name} contains invalid word entry"
            }
            return null
        }

        private fun validateImportedCounts(bookCounts: Map<BookCode, Int>) {
            BookCode.entries.forEach { book ->
                if (bookCounts[book] != book.expectedPublishSafeCount) {
                    throw AppException(AppError.VocabularyImportFailed("${book.name} imported count mismatch"))
                }
            }
        }

        private fun parseSourceInfo(payload: String): List<SourceInfo> =
            json.decodeFromString<AssetSourceManifest>(payload).sources.map {
                SourceInfo(
                    name = it.name,
                    url = it.url,
                    license = it.license,
                    licenseStatus = it.licenseStatus,
                    redistributable = it.redistributable,
                    publishBlocking = it.publishBlocking,
                    notes = it.notes,
                )
            }
    }

private fun escapeLikeWildcards(input: String): String =
    input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

internal fun isCurrentPublishSafeImport(
    manifest: VocabManifest,
    latestImportRun: VocabularyImportRunEntity?,
    hasSourceManifest: Boolean,
    bookCounts: Map<BookCode, Int>,
): Boolean {
    if (!hasSourceManifest || latestImportRun == null) return false
    if (manifest.buildTarget != BUILD_TARGET_PUBLISH ||
        latestImportRun.buildTarget != BUILD_TARGET_PUBLISH
    ) {
        return false
    }
    if (latestImportRun.generatedAt != manifest.generatedAt) return false
    if (manifest.assetFingerprint.isBlank()) return false
    if (latestImportRun.assetFingerprint != manifest.assetFingerprint) return false
    return BookCode.entries.all { book ->
        manifest.books[book.name]?.count == book.expectedPublishSafeCount &&
            manifest.books[book.name]?.hash?.isNotBlank() == true &&
            bookCounts[book] == book.expectedPublishSafeCount
    }
}

private data class ValidatedAssets(
    val rows: Map<BookCode, List<AssetWordEntry>>,
    val sourceManifest: AssetSourceManifest,
    val sourcesText: String,
)

private const val UTF8_BOM = "\uFEFF"
private const val MAX_ALIAS_LENGTH = 80
private const val SEARCH_RESULT_LIMIT = 80
private val PUBLISH_BLOCKING_SOURCE_FLAGS = setOf("kylebing", "netem")

private fun sha256Hex(value: String): String {
    val normalized = value.replace("\r\n", "\n").replace("\r", "\n")
    val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

private fun assetFingerprint(
    bookHashes: Map<BookCode, String>,
    sourcesHash: String,
): String {
    val input =
        BookCode.entries.joinToString(separator = ";") { book ->
            "${book.name}=${bookHashes.getValue(book)}"
        } + ";sources=$sourcesHash"
    return sha256Hex(input)
}

internal fun AssetWordEntry.searchAliasValues(): List<String> =
    (aliases + altMeanings)
        .map { it.trim().take(MAX_ALIAS_LENGTH) }
        .filter { it.isNotEmpty() }
        .distinct()

private fun AssetWordEntry.toWordEntity(
    id: String,
    normalized: String,
    now: Instant,
) = WordEntryEntity(
    id = id,
    word = normalized,
    meaning = meaning.trim(),
    phonetic = phonetic.takeIf { it.isNotBlank() },
    partOfSpeech = partOfSpeech.takeIf { it.isNotBlank() },
    definition = definition.takeIf { it.isNotBlank() },
    cefrLevel = cefrLevel.takeIf { it.isNotBlank() },
    cefrRank = cefrRank,
    frequency = frequency,
    sourceFlagsJson = Json.encodeToString(sourceFlags),
    coverageTier = coverageTier.takeIf { it.isNotBlank() },
    createdAt = now,
    updatedAt = now,
)

private fun AssetWordEntry.toMembershipEntity(
    id: String,
    book: BookCode,
    index: Int,
) = WordBookMembershipEntity(
    wordId = id,
    bookCode = book.name,
    orderIndex = index,
    examFrequencyScore = examFrequencyScore,
    examPriorityScore = examPriorityScore,
    isPhraseBacked = isPhraseBacked,
    phraseCount = phraseCount,
)

@Module
@InstallIn(SingletonComponent::class)
abstract class VocabularyModule {
    @Binds
    @Singleton
    abstract fun bindVocabularyRepository(repository: AssetVocabularyRepository): VocabularyRepository
}
