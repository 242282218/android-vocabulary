package com.zzz.androidvocab.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultSettingsUseFirstRunValues() =
        runTest {
            val repository = repository()

            assertEquals(AppSettings(), repository.settings.first())
        }

    @Test
    fun settingsPersistWithBoundsApplied() =
        runTest {
            val repository = repository()

            repository.updateDailyNewLimit(120)
            repository.updateSelectedBooks(setOf(BookCode.CET6, BookCode.TOEFL))
            repository.updateTargetRetention(0.5)
            repository.updateReminder(enabled = true, hour = 26, minute = -10)
            repository.updateThemeMode(ThemeMode.Dark)

            val settings = repository.settings.first()
            assertEquals(100, settings.dailyNewLimit)
            assertEquals(setOf(BookCode.CET6, BookCode.TOEFL), settings.selectedBooks)
            assertEquals(0.7, settings.targetRetention, 0.0)
            assertTrue(settings.reminderEnabled)
            assertEquals(23, settings.reminderHour)
            assertEquals(0, settings.reminderMinute)
            assertEquals(ThemeMode.Dark, settings.themeMode)
        }

    @Test
    fun toggleBookKeepsLastSelectedBook() =
        runTest {
            val repository = repository()

            repository.updateSelectedBooks(setOf(BookCode.CET4, BookCode.CET6))
            repository.toggleBook(BookCode.CET6)
            assertEquals(setOf(BookCode.CET4), repository.settings.first().selectedBooks)

            repository.toggleBook(BookCode.CET4)
            assertEquals(setOf(BookCode.CET4), repository.settings.first().selectedBooks)
        }

    @Test
    fun updateSelectedBooksPersistsFallbackWhenEmpty() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create {
                    temporaryFolder.newFile("settings-${System.nanoTime()}.preferences_pb")
                }
            val repository = DataStoreSettingsRepository(dataStore)
            val selectedBooksKey = stringPreferencesKey("selected_books")

            repository.updateSelectedBooks(emptySet())

            assertEquals(setOf(BookCode.CET4), repository.settings.first().selectedBooks)
            assertEquals("CET4", dataStore.data.first()[selectedBooksKey])
        }

    @Test
    fun updateSelectedBooksPersistsInBookOrder() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create {
                    temporaryFolder.newFile("settings-${System.nanoTime()}.preferences_pb")
                }
            val repository = DataStoreSettingsRepository(dataStore)
            val selectedBooksKey = stringPreferencesKey("selected_books")

            repository.updateSelectedBooks(linkedSetOf(BookCode.TOEFL, BookCode.CET4, BookCode.CET6))

            assertEquals("CET4,CET6,TOEFL", dataStore.data.first()[selectedBooksKey])
        }

    @Test
    fun invalidThemeModeFallsBackToSystem() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create {
                    temporaryFolder.newFile("settings-${System.nanoTime()}.preferences_pb")
                }
            val repository = DataStoreSettingsRepository(dataStore)
            val themeModeKey = stringPreferencesKey("theme_mode")

            dataStore.edit { it[stringPreferencesKey("theme_mode")] = "RemovedTheme" }

            assertEquals(ThemeMode.System, repository.settings.first().themeMode)

            dataStore.edit { it[themeModeKey] = " Dark " }
            assertEquals(ThemeMode.Dark, repository.settings.first().themeMode)
        }

    @Test
    fun corruptedSelectedBooksKeepValidEntriesAndFallbackWhenEmpty() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create {
                    temporaryFolder.newFile("settings-${System.nanoTime()}.preferences_pb")
                }
            val repository = DataStoreSettingsRepository(dataStore)
            val selectedBooksKey = stringPreferencesKey("selected_books")

            dataStore.edit { it[selectedBooksKey] = "CET4, RemovedBook, TOEFL" }
            assertEquals(setOf(BookCode.CET4, BookCode.TOEFL), repository.settings.first().selectedBooks)

            dataStore.edit { it[selectedBooksKey] = "RemovedBook, " }
            assertEquals(setOf(BookCode.CET4), repository.settings.first().selectedBooks)
        }

    @Test
    fun corruptedTargetRetentionFallsBackToDefaultAndSanitizesNaN() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create {
                    temporaryFolder.newFile("settings-${System.nanoTime()}.preferences_pb")
                }
            val repository = DataStoreSettingsRepository(dataStore)
            val targetRetentionKey = doublePreferencesKey("target_retention")

            dataStore.edit { it[targetRetentionKey] = Double.NaN }
            assertEquals(0.9, repository.settings.first().targetRetention, 0.0)

            repository.updateTargetRetention(Double.NaN)
            val persistedTargetRetention = requireNotNull(dataStore.data.first()[targetRetentionKey])
            assertEquals(0.9, persistedTargetRetention, 0.0)
        }

    @Test
    fun corruptedNumericPreferencesClampToBoundsOnRead() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create {
                    temporaryFolder.newFile("settings-${System.nanoTime()}.preferences_pb")
                }
            val repository = DataStoreSettingsRepository(dataStore)
            val dailyNewLimitKey = intPreferencesKey("daily_new_limit")
            val targetRetentionKey = doublePreferencesKey("target_retention")
            val reminderHourKey = intPreferencesKey("reminder_hour")
            val reminderMinuteKey = intPreferencesKey("reminder_minute")

            dataStore.edit {
                it[dailyNewLimitKey] = -5
                it[targetRetentionKey] = 0.5
                it[reminderHourKey] = 99
                it[reminderMinuteKey] = -7
            }

            val settings = repository.settings.first()
            assertEquals(0, settings.dailyNewLimit)
            assertEquals(0.7, settings.targetRetention, 0.0)
            assertEquals(23, settings.reminderHour)
            assertEquals(0, settings.reminderMinute)
        }

    private fun repository(): DataStoreSettingsRepository =
        DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create {
                temporaryFolder.newFile("settings-${System.nanoTime()}.preferences_pb")
            },
        )
}
