package com.zzz.androidvocab.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zzz.androidvocab.core.domain.GetBookProgressUseCase
import com.zzz.androidvocab.core.domain.GetDailyActivityUseCase
import com.zzz.androidvocab.core.domain.GetDifficultWordsUseCase
import com.zzz.androidvocab.core.domain.GetRetentionStatsUseCase
import com.zzz.androidvocab.core.domain.GetReviewLoadUseCase
import com.zzz.androidvocab.core.domain.GetStreakUseCase
import com.zzz.androidvocab.core.domain.ObserveSettingsUseCase
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.BookProgress
import com.zzz.androidvocab.core.model.DailyActivity
import com.zzz.androidvocab.core.model.DailyReviewLoad
import com.zzz.androidvocab.core.model.DifficultWord
import com.zzz.androidvocab.core.model.RetentionStats
import com.zzz.androidvocab.core.model.StreakStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatsUiState(
    val isLoading: Boolean = true,
    val load: List<DailyReviewLoad> = emptyList(),
    val activity: List<DailyActivity> = emptyList(),
    val retention: RetentionStats = RetentionStats(0.0, 0.0, 0.0),
    val streak: StreakStats = StreakStats(0, 0, emptySet()),
    val progress: List<BookProgress> = emptyList(),
    val difficultWords: List<DifficultWord> = emptyList(),
    val selectedBooks: List<BookCode> = emptyList(),
)

@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        getReviewLoadUseCase: GetReviewLoadUseCase,
        getDailyActivityUseCase: GetDailyActivityUseCase,
        getRetentionStatsUseCase: GetRetentionStatsUseCase,
        getStreakUseCase: GetStreakUseCase,
        getBookProgressUseCase: GetBookProgressUseCase,
        getDifficultWordsUseCase: GetDifficultWordsUseCase,
        observeSettingsUseCase: ObserveSettingsUseCase,
    ) : ViewModel() {
        val uiState =
            combine(
                combine(getReviewLoadUseCase(30), getDailyActivityUseCase(35)) { load, activity ->
                    load to activity
                },
                getRetentionStatsUseCase(30),
                getStreakUseCase(),
                getBookProgressUseCase(),
                combine(getDifficultWordsUseCase(30), observeSettingsUseCase()) { difficultWords, settings ->
                    difficultWords to settings
                },
            ) { loadAndActivity, retention, streak, progress, difficultWordsAndSettings ->
                val (difficultWords, settings) = difficultWordsAndSettings
                StatsUiState(
                    isLoading = false,
                    load = loadAndActivity.first,
                    activity = loadAndActivity.second,
                    retention = retention,
                    streak = streak,
                    progress = progress,
                    difficultWords = difficultWords,
                    selectedBooks = settings.selectedBooks.toList(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
    }
