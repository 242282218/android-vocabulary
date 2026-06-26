package com.zzz.androidvocab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zzz.androidvocab.core.designsystem.VocabTheme
import com.zzz.androidvocab.feature.review.ReviewRoute
import com.zzz.androidvocab.feature.settings.SettingsRoute
import com.zzz.androidvocab.feature.stats.StatsRoute
import com.zzz.androidvocab.feature.today.TodayRoute
import com.zzz.androidvocab.feature.wordbook.WordbookRoute
import com.zzz.androidvocab.worker.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AppViewModel = hiltViewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val context = LocalContext.current
            var notificationAccess by remember { mutableStateOf(readNotificationAccess(context)) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                // Keep resume sync state-only so we do not push an already pending reminder to tomorrow.
                notificationAccess = readNotificationAccess(context)
            }
            LaunchedEffect(
                settings?.reminderEnabled,
                settings?.reminderHour,
                settings?.reminderMinute,
                notificationAccess,
            ) {
                val loadedSettings = settings ?: return@LaunchedEffect
                when (reminderSyncAction(loadedSettings.reminderEnabled, notificationAccess)) {
                    ReminderSyncAction.Schedule ->
                        reminderScheduler.scheduleDailyReminder(
                            loadedSettings.reminderHour,
                            loadedSettings.reminderMinute,
                        )
                    ReminderSyncAction.Cancel -> reminderScheduler.cancelDailyReminder()
                }
            }
            VocabTheme(themeMode = themeMode) {
                VocabApp()
            }
        }
    }
}

@Composable
private fun VocabApp() {
    val navController = rememberNavController()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { VocabBottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppTab.Today.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(
                AppTab.Today.route,
            ) { TodayRoute(onStartReview = { navController.navigate(AppTab.Review.route) }) }
            composable(AppTab.Review.route) { ReviewRoute() }
            composable(AppTab.Wordbook.route) { WordbookRoute() }
            composable(AppTab.Stats.route) { StatsRoute() }
            composable(AppTab.Settings.route) { SettingsRoute() }
        }
    }
}

@Composable
private fun VocabBottomBar(navController: androidx.navigation.NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    Surface(
        modifier =
            Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(0.65.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            AppTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = current?.hierarchy?.any { it.route == tab.route } == true,
                    onClick = {
                        navController.navigate(tab.route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                        }
                    },
                    icon = { Icon(tab.icon(), contentDescription = null) },
                    label = { Text(tab.label) },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )
            }
        }
    }
}

private enum class AppTab(
    val route: String,
    val label: String,
) {
    Today("today", "今日"),
    Review("review", "复习"),
    Wordbook("wordbook", "词书"),
    Stats("stats", "统计"),
    Settings("settings", "设置"),
}

@Composable
private fun AppTab.icon() =
    when (this) {
        AppTab.Today -> Icons.Outlined.Home
        AppTab.Review -> Icons.Outlined.Style
        AppTab.Wordbook -> Icons.AutoMirrored.Outlined.MenuBook
        AppTab.Stats -> Icons.Outlined.BarChart
        AppTab.Settings -> Icons.Outlined.Settings
    }
