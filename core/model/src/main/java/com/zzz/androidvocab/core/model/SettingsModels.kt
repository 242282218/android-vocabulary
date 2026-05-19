package com.zzz.androidvocab.core.model

enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    fun displayName(): String =
        when (this) {
            System -> "跟随系统"
            Light -> "浅色"
            Dark -> "深色"
        }
}

data class AppSettings(
    val dailyNewLimit: Int = 20,
    val selectedBooks: Set<BookCode> = setOf(BookCode.CET4),
    val targetRetention: Double = 0.9,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val themeMode: ThemeMode = ThemeMode.System,
)

data class ImportResult(
    val importedWords: Int,
    val memberships: Int,
    val bookCounts: Map<BookCode, Int>,
    val generatedAt: String,
)

data class ExportResult(
    val fileName: String,
    val absolutePath: String,
)

data class SourceInfo(
    val name: String,
    val url: String,
    val license: String,
    val licenseStatus: String,
    val redistributable: Boolean,
    val publishBlocking: Boolean,
    val notes: String,
)
