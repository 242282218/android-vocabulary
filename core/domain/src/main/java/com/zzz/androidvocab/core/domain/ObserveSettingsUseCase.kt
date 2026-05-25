package com.zzz.androidvocab.core.domain

import javax.inject.Inject

class ObserveSettingsUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        operator fun invoke() = settingsRepository.settings
    }
