package com.zzz.androidvocab.core.domain

import javax.inject.Inject

class ExportUserDataUseCase
    @Inject
    constructor(
        private val exportRepository: ExportRepository,
    ) {
        suspend operator fun invoke() = exportRepository.exportUserData()
    }
