package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ThemeMode
import javax.inject.Inject

/**
 * Updates user settings and preferences.
 *
 * This use case provides methods to modify various application settings such as
 * daily review limits, selected books, theme mode, and reminder configuration.
 * Each method persists the change to the settings repository.
 *
 * @param settingsRepository Repository for accessing and updating user preferences.
 */
class UpdateSettingsUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        /**
         * Updates the daily limit for new words.
         *
         * @param value The new daily new word limit.
         */
        suspend fun dailyNewLimit(value: Int) = settingsRepository.updateDailyNewLimit(value)

        /**
         * Updates the set of selected books for learning.
         *
         * @param bookCodes The new set of book codes to study.
         */
        suspend fun selectedBooks(bookCodes: Set<BookCode>) = settingsRepository.updateSelectedBooks(bookCodes)

        /**
         * Toggles a book's selection state.
         *
         * @param bookCode The book code to toggle.
         */
        suspend fun toggleBook(bookCode: BookCode) = settingsRepository.toggleBook(bookCode)

        /**
         * Updates the target retention rate for spaced repetition.
         *
         * @param value The new target retention rate (0.0 to 1.0).
         */
        suspend fun targetRetention(value: Double) = settingsRepository.updateTargetRetention(value)

        /**
         * Updates the application theme mode.
         *
         * @param themeMode The new theme mode (Light/Dark/System).
         */
        suspend fun themeMode(themeMode: ThemeMode) = settingsRepository.updateThemeMode(themeMode)

        /**
         * Updates the reminder configuration.
         *
         * @param enabled Whether the reminder is enabled.
         * @param hour The hour of the reminder (0-23).
         * @param minute The minute of the reminder (0-59).
         */
        suspend fun reminder(
            enabled: Boolean,
            hour: Int,
            minute: Int,
        ) = settingsRepository.updateReminder(enabled, hour, minute)

        /**
         * Updates whether the reminder is enabled.
         *
         * @param enabled Whether the reminder should be enabled.
         */
        suspend fun reminderEnabled(enabled: Boolean) = settingsRepository.updateReminderEnabled(enabled)

        /**
         * Updates the reminder time.
         *
         * @param hour The hour of the reminder (0-23).
         * @param minute The minute of the reminder (0-59).
         */
        suspend fun reminderTime(
            hour: Int,
            minute: Int,
        ) = settingsRepository.updateReminderTime(hour, minute)
    }
