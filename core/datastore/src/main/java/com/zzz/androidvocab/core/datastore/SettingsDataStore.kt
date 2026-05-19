package com.zzz.androidvocab.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zzz.androidvocab.core.domain.SettingsRepository
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ThemeMode
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vocabDataStore by preferencesDataStore(name = "vocab_settings")

class DataStoreSettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.vocabDataStore)

    override val settings: Flow<AppSettings> =
        dataStore.data.map { prefs ->
            AppSettings(
                dailyNewLimit = (prefs[Keys.dailyNewLimit] ?: 20).coerceIn(0, 100),
                selectedBooks = prefs[Keys.selectedBooks].orEmpty().toBookCodes(),
                targetRetention = (prefs[Keys.targetRetention] ?: 0.9).coerceIn(0.7, 0.98),
                reminderEnabled = prefs[Keys.reminderEnabled] ?: false,
                reminderHour = (prefs[Keys.reminderHour] ?: 20).coerceIn(0, 23),
                reminderMinute = (prefs[Keys.reminderMinute] ?: 0).coerceIn(0, 59),
                themeMode = prefs[Keys.themeMode].toThemeMode(),
            )
        }

    override suspend fun updateDailyNewLimit(value: Int) {
        dataStore.edit { it[Keys.dailyNewLimit] = value.coerceIn(0, 100) }
    }

    override suspend fun updateSelectedBooks(bookCodes: Set<BookCode>) {
        dataStore.edit { it[Keys.selectedBooks] = bookCodes.joinToString(",") { book -> book.name } }
    }

    override suspend fun toggleBook(bookCode: BookCode) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.selectedBooks].orEmpty().toBookCodes()
            val next = if (bookCode in current && current.size > 1) current - bookCode else current + bookCode
            prefs[Keys.selectedBooks] = next.joinToString(",") { it.name }
        }
    }

    override suspend fun updateTargetRetention(value: Double) {
        dataStore.edit { it[Keys.targetRetention] = value.coerceIn(0.7, 0.98) }
    }

    override suspend fun updateReminder(
        enabled: Boolean,
        hour: Int,
        minute: Int,
    ) {
        dataStore.edit {
            it[Keys.reminderEnabled] = enabled
            it[Keys.reminderHour] = hour.coerceIn(0, 23)
            it[Keys.reminderMinute] = minute.coerceIn(0, 59)
        }
    }

    override suspend fun updateReminderEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.reminderEnabled] = enabled
        }
    }

    override suspend fun updateReminderTime(
        hour: Int,
        minute: Int,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.reminderHour] = hour.coerceIn(0, 23)
            prefs[Keys.reminderMinute] = minute.coerceIn(0, 59)
        }
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        dataStore.edit { it[Keys.themeMode] = themeMode.name }
    }

    private fun String.toBookCodes(): Set<BookCode> {
        val parsed = split(",").mapNotNull { raw -> runCatching { BookCode.valueOf(raw) }.getOrNull() }.toSet()
        return parsed.ifEmpty { setOf(BookCode.CET4) }
    }
}

internal fun String?.toThemeMode(): ThemeMode =
    this?.let { raw -> runCatching { ThemeMode.valueOf(raw) }.getOrNull() } ?: ThemeMode.System

private object Keys {
    val dailyNewLimit = intPreferencesKey("daily_new_limit")
    val selectedBooks = stringPreferencesKey("selected_books")
    val targetRetention = doublePreferencesKey("target_retention")
    val reminderEnabled = booleanPreferencesKey("reminder_enabled")
    val reminderHour = intPreferencesKey("reminder_hour")
    val reminderMinute = intPreferencesKey("reminder_minute")
    val themeMode = stringPreferencesKey("theme_mode")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DatastoreModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(repository: DataStoreSettingsRepository): SettingsRepository
}
