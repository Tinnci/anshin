package com.driezy.medlog.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.driezy.medlog.ui.navigation.MedLogNavigationWrapper
import com.driezy.medlog.ui.navigation.Route
import com.driezy.medlog.ui.navigation.TOP_LEVEL_DESTINATIONS
import com.driezy.medlog.ui.navigation.TopLevelDestination
import com.driezy.medlog.ui.screen.addmedication.AddMedicationScreen
import com.driezy.medlog.ui.screen.detail.MedicationDetailScreen
import com.driezy.medlog.ui.screen.drugs.DrugsScreen
import com.driezy.medlog.ui.screen.health.HealthScreen
import com.driezy.medlog.ui.screen.history.HistoryScreen
import com.driezy.medlog.ui.screen.home.HomeScreen
import com.driezy.medlog.ui.screen.settings.AppearanceSettingsScreen
import com.driezy.medlog.ui.screen.settings.Bpx1DeviceSettingsScreen
import com.driezy.medlog.ui.screen.settings.CloudApiSettingsScreen
import com.driezy.medlog.ui.screen.settings.DataSettingsScreen
import com.driezy.medlog.ui.screen.settings.IntelligenceSettingsScreen
import com.driezy.medlog.ui.screen.settings.ModuleSettingsScreen
import com.driezy.medlog.ui.screen.settings.ReminderSettingsScreen
import com.driezy.medlog.ui.screen.settings.SettingsScreen
import com.driezy.medlog.ui.screen.settings.WidgetSettingsScreen
import com.driezy.medlog.ui.screen.symptom.SymptomDiaryScreen
import com.driezy.medlog.ui.screen.welcome.WelcomeScreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedLogApp(openAddMedication: Boolean = false) {
    val appViewModel: MedLogAppViewModel = hiltViewModel()
    val startDestState by appViewModel.startDestination.collectAsStateWithLifecycle()
    // DataStore 加载期间显示居中加载指示器；捕获到本地 val 以消除后续 !! 需求
    val startDest = startDestState ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 响应快捷方式"添加药品"intent
    LaunchedEffect(openAddMedication) {
        if (openAddMedication) {
            navController.navigate(Route.AddMedication())
        }
    }

    val navigateToTopLevel: (TopLevelDestination) -> Unit = remember(navController) {
        { dest ->
            navController.navigate(dest.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // ── 根据功能开关过滤可见目的地 ─────────────────────────────
    val featureFlags by appViewModel.featureFlags.collectAsStateWithLifecycle()
    val enabledDestinations = remember(featureFlags) {
        TOP_LEVEL_DESTINATIONS.filter { dest ->
            when (dest.route) {
                Route.Diary -> featureFlags.enableSymptomDiary
                Route.Drugs -> featureFlags.enableDrugDatabase
                Route.Health -> featureFlags.enableHealthModule
                else -> true // Home / History / Settings 始终可见
            }
        }
    }
    // Decide whether to show the main navigation wrapper
    // Welcome 屏不展示导航栏
    val isOnWelcome = currentDestination?.hasRoute(Route.Welcome::class) == true
    val showMainNav = !isOnWelcome &&
        (
            currentDestination == null ||
                TOP_LEVEL_DESTINATIONS.any { currentDestination.hasRoute(it.route::class) }
            )

    if (showMainNav) {
        MedLogNavigationWrapper(
            currentDestination = currentDestination,
            navigateToTopLevel = navigateToTopLevel,
            destinations = enabledDestinations,
        ) {
            MedLogNavHost(navController = navController, startDest = startDest)
        }
    } else {
        MedLogNavHost(navController = navController, startDest = startDest)
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun MedLogNavHost(navController: androidx.navigation.NavHostController, startDest: Route) {
    val motionScheme = MaterialTheme.motionScheme
    val navFadeIn = fadeIn(animationSpec = motionScheme.fastEffectsSpec())
    val navFadeOut = fadeOut(animationSpec = motionScheme.fastEffectsSpec())
    fun materialSharedAxisX(forward: Boolean) = slideInHorizontally(
        animationSpec = motionScheme.defaultSpatialSpec<IntOffset>(),
        initialOffsetX = { width -> if (forward) width else -width / 4 },
    ) + navFadeIn
    fun materialSharedAxisXOut(forward: Boolean) = slideOutHorizontally(
        animationSpec = motionScheme.defaultSpatialSpec<IntOffset>(),
        targetOffsetX = { width -> if (forward) width else -width / 4 },
    ) + navFadeOut

    NavHost(
        navController = navController,
        startDestination = startDest,
        // 顶层 Tab 切换：淡入淡出
        enterTransition = { navFadeIn },
        exitTransition = { navFadeOut },
        // 深层导航：水平滑动
        popEnterTransition = {
            materialSharedAxisX(forward = false)
        },
        popExitTransition = {
            materialSharedAxisXOut(forward = true)
        },
    ) {
        // ── 欢迎引导（首次启动）───────────────────────────
        composable<Route.Welcome>(
            enterTransition = { navFadeIn },
            exitTransition = { navFadeOut },
        ) {
            WelcomeScreen(
                onFinished = {
                    navController.navigate(Route.Home) {
                        popUpTo<Route.Welcome> { inclusive = true }
                    }
                },
            )
        }
        // ── 顶层目的地（Tab 切换：只淡入淡出）──────────────
        composable<Route.Home>(
            enterTransition = { navFadeIn },
            exitTransition = { navFadeOut },
        ) {
            HomeScreen(
                onAddMedication = { navController.navigate(Route.AddMedication()) },
                onMedicationClick = { id -> navController.navigate(Route.MedDetail(id)) },
                onOpenSettings = { navController.navigate(Route.Settings) },
            )
        }
        composable<Route.History>(
            enterTransition = { navFadeIn },
            exitTransition = { navFadeOut },
        ) {
            HistoryScreen(onOpenSettings = { navController.navigate(Route.Settings) })
        }
        composable<Route.Drugs>(
            enterTransition = { navFadeIn },
            exitTransition = { navFadeOut },
        ) {
            DrugsScreen(
                onAddCustomDrug = { navController.navigate(Route.AddMedication()) },
                onOpenSettings = { navController.navigate(Route.Settings) },
                onDrugSelect = { drug ->
                    navController.navigate(
                        Route.AddMedication(
                            drugName = drug.name,
                            drugCategory = drug.category,
                        ),
                    )
                },
            )
        }
        composable<Route.Diary>(
            enterTransition = { navFadeIn },
            exitTransition = { navFadeOut },
        ) {
            SymptomDiaryScreen(onOpenSettings = { navController.navigate(Route.Settings) })
        }
        composable<Route.Health>(
            enterTransition = { navFadeIn },
            exitTransition = { navFadeOut },
        ) {
            HealthScreen(onOpenSettings = { navController.navigate(Route.Settings) })
        }
        composable<Route.Settings>(
            enterTransition = { navFadeIn },
            exitTransition = { navFadeOut },
        ) {
            SettingsScreen(
                onNavigateToWelcome = {
                    navController.navigate(Route.Welcome) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = false
                        }
                    }
                },
                onNavigateToAppearanceSettings = { navController.navigate(Route.SettingsAppearance) },
                onNavigateToReminderSettings = { navController.navigate(Route.SettingsReminders) },
                onNavigateToModuleSettings = { navController.navigate(Route.SettingsModules) },
                onNavigateToIntelligenceSettings = { navController.navigate(Route.SettingsIntelligence) },
                onNavigateToBpx1Settings = { navController.navigate(Route.SettingsBpx1) },
                onNavigateToWidgetSettings = { navController.navigate(Route.SettingsWidgets) },
                onNavigateToDataSettings = { navController.navigate(Route.SettingsData) },
            )
        }
        composable<Route.SettingsAppearance>(
            enterTransition = { materialSharedAxisX(forward = true) },
            exitTransition = { navFadeOut },
            popEnterTransition = { navFadeIn },
            popExitTransition = { materialSharedAxisXOut(forward = true) },
        ) {
            AppearanceSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.SettingsReminders>(
            enterTransition = { materialSharedAxisX(forward = true) },
            exitTransition = { navFadeOut },
            popEnterTransition = { navFadeIn },
            popExitTransition = { materialSharedAxisXOut(forward = true) },
        ) {
            ReminderSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.SettingsModules>(
            enterTransition = { materialSharedAxisX(forward = true) },
            exitTransition = { navFadeOut },
            popEnterTransition = { navFadeIn },
            popExitTransition = { materialSharedAxisXOut(forward = true) },
        ) {
            ModuleSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.SettingsIntelligence>(
            enterTransition = { materialSharedAxisX(forward = true) },
            exitTransition = { navFadeOut },
            popEnterTransition = { navFadeIn },
            popExitTransition = { materialSharedAxisXOut(forward = true) },
        ) {
            IntelligenceSettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToCloudApiSettings = { navController.navigate(Route.SettingsCloudApi) },
            )
        }
        composable<Route.SettingsCloudApi>(
            enterTransition = { materialSharedAxisX(forward = true) },
            exitTransition = { navFadeOut },
            popEnterTransition = { navFadeIn },
            popExitTransition = { materialSharedAxisXOut(forward = true) },
        ) {
            CloudApiSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.SettingsBpx1>(
            enterTransition = { materialSharedAxisX(forward = true) },
            exitTransition = { navFadeOut },
            popEnterTransition = { navFadeIn },
            popExitTransition = { materialSharedAxisXOut(forward = true) },
        ) {
            Bpx1DeviceSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.SettingsWidgets>(
            enterTransition = { materialSharedAxisX(forward = true) },
            exitTransition = { navFadeOut },
            popEnterTransition = { navFadeIn },
            popExitTransition = { materialSharedAxisXOut(forward = true) },
        ) {
            WidgetSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.SettingsData>(
            enterTransition = { materialSharedAxisX(forward = true) },
            exitTransition = { navFadeOut },
            popEnterTransition = { navFadeIn },
            popExitTransition = { materialSharedAxisXOut(forward = true) },
        ) {
            DataSettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToWelcome = {
                    navController.navigate(Route.Welcome) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = false
                        }
                    }
                },
            )
        }
        composable<Route.MedDetail>(
            enterTransition = { materialSharedAxisX(forward = true) },
            exitTransition = { navFadeOut },
            popEnterTransition = { navFadeIn },
            popExitTransition = { materialSharedAxisXOut(forward = true) },
        ) { backStackEntry ->
            val route: Route.MedDetail = backStackEntry.toRoute()
            MedicationDetailScreen(
                medicationId = route.medicationId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Route.AddMedication(id)) },
            )
        }
        composable<Route.AddMedication>(
            enterTransition = { materialSharedAxisX(forward = true) },
            exitTransition = { navFadeOut },
            popEnterTransition = { navFadeIn },
            popExitTransition = { materialSharedAxisXOut(forward = true) },
        ) { backStackEntry ->
            val route: Route.AddMedication = backStackEntry.toRoute()
            AddMedicationScreen(
                medicationId = route.medicationId.takeIf { it != -1L },
                drugName = route.drugName.ifEmpty { null },
                drugCategory = route.drugCategory.ifEmpty { null },
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
    }
}
