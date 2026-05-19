package com.zzz.androidvocab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zzz.androidvocab.core.domain.ObserveSettingsUseCase
import com.zzz.androidvocab.core.domain.UpdateSettingsUseCase
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        observeSettingsUseCase: ObserveSettingsUseCase,
        private val updateSettingsUseCase: UpdateSettingsUseCase,
    ) : ViewModel() {
        val settings =
            observeSettingsUseCase()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

        val themeMode =
            settings
                .map { it.themeMode }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.System)

        fun disableReminder() {
            viewModelScope.launch {
                updateSettingsUseCase.reminderEnabled(false)
            }
        }
    }
