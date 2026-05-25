package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ThemeMode
import javax.inject.Inject

class UpdateSettingsUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        suspend fun dailyNewLimit(value: Int) = settingsRepository.updateDailyNewLimit(value)

        suspend fun selectedBooks(bookCodes: Set<BookCode>) = settingsRepository.updateSelectedBooks(bookCodes)

        suspend fun toggleBook(bookCode: BookCode) = settingsRepository.toggleBook(bookCode)

        suspend fun targetRetention(value: Double) = settingsRepository.updateTargetRetention(value)

        suspend fun themeMode(themeMode: ThemeMode) = settingsRepository.updateThemeMode(themeMode)

        suspend fun reminder(
            enabled: Boolean,
            hour: Int,
            minute: Int,
        ) = settingsRepository.updateReminder(enabled, hour, minute)

        suspend fun reminderEnabled(enabled: Boolean) = settingsRepository.updateReminderEnabled(enabled)

        suspend fun reminderTime(
            hour: Int,
            minute: Int,
        ) = settingsRepository.updateReminderTime(hour, minute)
    }
