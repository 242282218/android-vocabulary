package com.zzz.androidvocab.core.domain

import javax.inject.Inject

/**
 * Observes user settings and preferences.
 *
 * This use case provides a reactive stream of the current application settings,
 * allowing UI components to react to setting changes automatically.
 *
 * @param settingsRepository Repository for accessing user preferences.
 */
class ObserveSettingsUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        /**
         * Returns a Flow that emits the current settings whenever they change.
         */
        operator fun invoke() = settingsRepository.settings
    }
