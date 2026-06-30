package com.zzz.androidvocab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zzz.androidvocab.core.domain.ObserveSettingsUseCase
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        observeSettingsUseCase: ObserveSettingsUseCase,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings?> =
            observeSettingsUseCase()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val themeMode =
            settings
                .map { it?.themeMode ?: ThemeMode.System }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.System)
    }
