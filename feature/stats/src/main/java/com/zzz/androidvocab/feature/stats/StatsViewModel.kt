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
import com.zzz.androidvocab.core.model.effectiveSelectedBookCodes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onStart
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
                getReviewLoadUseCase(REVIEW_LOAD_DAYS).onStart { emit(emptyList()) },
                getDailyActivityUseCase(ACTIVITY_DAYS).onStart { emit(emptyList()) },
                getRetentionStatsUseCase(RETENTION_DAYS).onStart { emit(RetentionStats(0.0, 0.0, 0.0)) },
                getStreakUseCase().onStart { emit(StreakStats(0, 0, emptySet())) },
                getBookProgressUseCase().onStart { emit(emptyList()) },
            ) { load, activity, retention, streak, progress ->
                StatsSnapshot(
                    load = load,
                    activity = activity,
                    retention = retention,
                    streak = streak,
                    progress = progress,
                )
            }.let { snapshotFlow ->
                combine(
                    snapshotFlow,
                    getDifficultWordsUseCase(DIFFICULT_WORDS_DAYS).onStart { emit(emptyList()) },
                    observeSettingsUseCase().onStart { emit(com.zzz.androidvocab.core.model.AppSettings()) },
                ) { snapshot, difficultWords, settings ->
                    StatsUiState(
                        isLoading = false,
                        load = snapshot.load,
                        activity = snapshot.activity,
                        retention = snapshot.retention,
                        streak = snapshot.streak,
                        progress = snapshot.progress,
                        difficultWords = difficultWords,
                        selectedBooks = settings.selectedBooks.effectiveSelectedBookCodes(),
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
    }

private data class StatsSnapshot(
    val load: List<DailyReviewLoad>,
    val activity: List<DailyActivity>,
    val retention: RetentionStats,
    val streak: StreakStats,
    val progress: List<BookProgress>,
)

private const val REVIEW_LOAD_DAYS = 30
private const val ACTIVITY_DAYS = 35
private const val RETENTION_DAYS = 30
private const val DIFFICULT_WORDS_DAYS = 30
