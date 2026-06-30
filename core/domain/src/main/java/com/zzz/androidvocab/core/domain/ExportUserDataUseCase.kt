package com.zzz.androidvocab.core.domain

import javax.inject.Inject

/**
 * Exports user data for backup or migration purposes.
 *
 * This use case aggregates all user-related data (settings, review history,
 * learning progress) into a format suitable for export, backup, or migration
 * to another device.
 *
 * @param exportRepository Repository for managing data export operations.
 */
class ExportUserDataUseCase
    @Inject
    constructor(
        private val exportRepository: ExportRepository,
    ) {
        /**
         * Executes the user data export process.
         *
         * @return The exported user data in a serializable format.
         */
        suspend operator fun invoke() = exportRepository.exportUserData()
    }
