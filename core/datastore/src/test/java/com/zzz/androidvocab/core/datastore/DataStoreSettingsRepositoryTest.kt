package com.zzz.androidvocab.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
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
    fun invalidThemeModeFallsBackToSystem() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create {
                    temporaryFolder.newFile("settings-${System.nanoTime()}.preferences_pb")
                }
            val repository = DataStoreSettingsRepository(dataStore)

            dataStore.edit { it[stringPreferencesKey("theme_mode")] = "RemovedTheme" }

            assertEquals(ThemeMode.System, repository.settings.first().themeMode)
        }

    private fun repository(): DataStoreSettingsRepository =
        DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create {
                temporaryFolder.newFile("settings-${System.nanoTime()}.preferences_pb")
            },
        )
}
