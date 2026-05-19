package com.zzz.androidvocab.core.vocabulary

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VocabManifest(
    val buildTarget: String,
    val generatedAt: String,
    val books: Map<String, VocabManifestBook>,
)

@Serializable
data class VocabManifestBook(
    val file: String,
    val count: Int,
)

@Serializable
data class AssetWordEntry(
    val word: String,
    val meaning: String,
    val level: String,
    val phonetic: String = "",
    val partOfSpeech: String = "",
    val definition: String = "",
    val cefrLevel: String = "",
    val cefrRank: Double = 0.0,
    val frequency: Double = 0.0,
    val aliases: List<String> = emptyList(),
    val coverageTier: String = "",
    val sourceFlags: List<String> = emptyList(),
    val altMeanings: List<String> = emptyList(),
    val examFrequencyScore: Double = 0.0,
    val examPriorityScore: Double = 0.0,
    val isPhraseBacked: Boolean = false,
    val phraseCount: Int = 0,
)

@Serializable
data class AssetSourceManifest(
    val generatedAt: String,
    val sources: List<AssetSourceInfo>,
)

@Serializable
data class AssetSourceInfo(
    val name: String,
    val url: String,
    val license: String,
    val licenseStatus: String,
    val redistributable: Boolean,
    val publishBlocking: Boolean,
    val notes: String,
    @SerialName("reviewAction") val reviewAction: String = "",
)
