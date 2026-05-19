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
import com.zzz.androidvocab.core.domain.VocabularyRepository
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.ImportResult
import com.zzz.androidvocab.core.model.SourceInfo
import com.zzz.androidvocab.core.model.WordDetail
import com.zzz.androidvocab.core.model.WordEntry
import com.zzz.androidvocab.core.model.WordStatusFilter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

class AssetVocabularyRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: VocabDatabase,
        private val wordDao: WordDao,
        private val reviewDao: ReviewDao,
        private val clockProvider: ClockProvider,
    ) : VocabularyRepository {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        override fun observeBookProgress(now: Instant): Flow<List<BookProgress>> =
            wordDao.observeBookProgress(now).map { rows -> rows.map { it.toModel() } }

        override fun searchWords(
            query: String,
            bookCodes: Set<BookCode>,
            statusFilter: WordStatusFilter,
            now: Instant,
        ): Flow<List<WordEntry>> =
            wordDao
                .searchWords(escapeLikeWildcards(query.trim()), bookCodes.toSearchNames(), statusFilter.name, now)
                .map { rows -> rows.map { it.toModel() } }

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
                    if (manifest.buildTarget != "publish") {
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
                        val sourcesText = readAsset("vocab/sources.json")
                        val sourceManifest = json.decodeFromString<AssetSourceManifest>(sourcesText)
                        val rows =
                            BookCode.entries.associateWith { book ->
                                val bookManifest =
                                    manifest.books[book.name]
                                        ?: throw AppException(
                                            AppError.VocabularyImportFailed("Missing manifest entry: ${book.name}"),
                                        )
                                val entries =
                                    json.decodeFromString<List<AssetWordEntry>>(
                                        readAsset("vocab/${bookManifest.file}"),
                                    )
                                validateBook(book, bookManifest, entries)
                                entries
                            }
                        database.withTransaction {
                            wordDao.deleteAliases()
                            wordDao.deleteMemberships()
                            wordDao.deleteWords()
                            val words = mutableMapOf<String, WordEntryEntity>()
                            val memberships = mutableListOf<WordBookMembershipEntity>()
                            val aliases = mutableListOf<WordAliasEntity>()
                            rows.forEach { (book, entries) ->
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
                                    generatedAt = sourceManifest.generatedAt,
                                    json = sourcesText,
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
                    }
                }.getOrElse { error ->
                    if (error is AppException) throw error
                    throw AppException(
                        AppError.VocabularyImportFailed(error.message ?: "Import failed"),
                        error,
                    )
                }
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
        ) {
            val errorReason =
                bookValidationError(
                    book = book,
                    manifest = manifest,
                    entries = entries,
                )
            if (errorReason != null) {
                throw AppException(AppError.VocabularyImportFailed(errorReason))
            }
        }

        private fun bookValidationError(
            book: BookCode,
            manifest: VocabManifestBook,
            entries: List<AssetWordEntry>,
        ): String? {
            if (
                manifest.count != book.expectedPublishSafeCount ||
                entries.size != book.expectedPublishSafeCount
            ) {
                return "${book.name} count mismatch"
            }
            val blocking =
                entries.firstOrNull { row ->
                    row.sourceFlags.any { it == "kylebing" || it == "netem" }
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

private fun Set<BookCode>.toSearchNames(): List<String> = ifEmpty { BookCode.entries.toSet() }.map { it.name }

private fun escapeLikeWildcards(input: String): String =
    input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

internal fun isCurrentPublishSafeImport(
    manifest: VocabManifest,
    latestImportRun: VocabularyImportRunEntity?,
    hasSourceManifest: Boolean,
    bookCounts: Map<BookCode, Int>,
): Boolean {
    if (!hasSourceManifest || latestImportRun == null) return false
    if (manifest.buildTarget != "publish" || latestImportRun.buildTarget != "publish") return false
    if (latestImportRun.generatedAt != manifest.generatedAt) return false
    return BookCode.entries.all { book ->
        manifest.books[book.name]?.count == book.expectedPublishSafeCount &&
            bookCounts[book] == book.expectedPublishSafeCount
    }
}

private fun String.toBookCodeOrNull(): BookCode? = BookCode.entries.firstOrNull { it.name == this }

private const val UTF8_BOM = "\uFEFF"
private const val MAX_ALIAS_LENGTH = 80

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
