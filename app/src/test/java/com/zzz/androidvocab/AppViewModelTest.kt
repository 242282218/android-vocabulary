package com.zzz.androidvocab

import com.zzz.androidvocab.core.domain.ObserveSettingsUseCase
import com.zzz.androidvocab.core.domain.SettingsRepository
import com.zzz.androidvocab.core.model.AppSettings
import com.zzz.androidvocab.core.model.BookCode
import com.zzz.androidvocab.core.model.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun settingsRemainUnloadedUntilRepositoryEmits() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val repository = FakeSettingsRepository()
            val viewModel = AppViewModel(ObserveSettingsUseCase(repository))
            val collector = launch(dispatcher) { viewModel.settings.collect() }

            advanceUntilIdle()

            assertNull(viewModel.settings.value)

            repository.emit(AppSettings(reminderEnabled = true, reminderHour = 7, reminderMinute = 30))
            advanceUntilIdle()

            assertEquals(true, viewModel.settings.value?.reminderEnabled)
            assertEquals(7, viewModel.settings.value?.reminderHour)
            assertEquals(30, viewModel.settings.value?.reminderMinute)
            collector.cancel()
        }

    @Test
    fun themeModeUsesSystemUntilSettingsLoad() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            val repository = FakeSettingsRepository()
            val viewModel = AppViewModel(ObserveSettingsUseCase(repository))
            val collector = launch(dispatcher) { viewModel.themeMode.collect() }

            advanceUntilIdle()

            assertEquals(ThemeMode.System, viewModel.themeMode.value)

            repository.emit(AppSettings(themeMode = ThemeMode.Dark))
            advanceUntilIdle()

            assertEquals(ThemeMode.Dark, viewModel.themeMode.value)
            collector.cancel()
        }
}

private class FakeSettingsRepository : SettingsRepository {
    private val settingsFlow = MutableSharedFlow<AppSettings>()
    override val settings: Flow<AppSettings> = settingsFlow

    suspend fun emit(settings: AppSettings) {
        settingsFlow.emit(settings)
    }

    override suspend fun updateDailyNewLimit(value: Int) = Unit

    override suspend fun updateSelectedBooks(bookCodes: Set<BookCode>) = Unit

    override suspend fun toggleBook(bookCode: BookCode) = Unit

    override suspend fun updateTargetRetention(value: Double) = Unit

    override suspend fun updateReminder(
        enabled: Boolean,
        hour: Int,
        minute: Int,
    ) = Unit

    override suspend fun updateReminderEnabled(enabled: Boolean) = Unit

    override suspend fun updateReminderTime(
        hour: Int,
        minute: Int,
    ) = Unit

    override suspend fun updateThemeMode(themeMode: ThemeMode) = Unit
}
