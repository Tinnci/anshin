package com.example.medlog.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.medlog.ui.navigation.MedLogNavigationWrapper
import com.example.medlog.ui.navigation.Route
import com.example.medlog.ui.navigation.TOP_LEVEL_DESTINATIONS
import com.example.medlog.ui.navigation.TopLevelDestination
import com.example.medlog.ui.screen.addmedication.AddMedicationScreen
import com.example.medlog.ui.screen.detail.MedicationDetailScreen
import com.example.medlog.ui.screen.drugs.DrugsScreen
import com.example.medlog.ui.screen.history.HistoryScreen
import com.example.medlog.ui.screen.home.HomeScreen
import com.example.medlog.ui.screen.settings.SettingsScreen

@Composable
fun MedLogApp(openAddMedication: Boolean = false) {
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

    // Decide whether to show the main navigation wrapper
    val showMainNav = TOP_LEVEL_DESTINATIONS.any { dest ->
        currentDestination?.route?.contains(dest.route::class.simpleName ?: "") == true
    }

    if (showMainNav || currentDestination == null) {
        MedLogNavigationWrapper(
            currentDestination = currentDestination,
            navigateToTopLevel = navigateToTopLevel,
        ) {
            MedLogNavHost(navController = navController)
        }
    } else {
        MedLogNavHost(navController = navController)
    }
}

@Composable
private fun MedLogNavHost(
    navController: androidx.navigation.NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = Route.Home,
        // 顶层 Tab 切换：淡入淡出
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        // 深层导航：水平滑动
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut()
        },
    ) {
        // ── 顶层目的地（Tab 切换：只淡入淡出）──────────────
        composable<Route.Home>(
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            HomeScreen(
                onAddMedication = { navController.navigate(Route.AddMedication()) },
                onMedicationClick = { id -> navController.navigate(Route.MedDetail(id)) },
            )
        }
        composable<Route.History>(
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            HistoryScreen()
        }
        composable<Route.Drugs>(
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            DrugsScreen(
                onAddCustomDrug = { navController.navigate(Route.AddMedication()) },
                onDrugSelect = { drug ->
                    navController.navigate(
                        Route.AddMedication(
                            drugName = drug.name,
                            drugCategory = drug.category,
                        )
                    )
                },
            )
        }
        composable<Route.Settings>(
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            SettingsScreen()
        }
        composable<Route.MedDetail>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
        ) { backStackEntry ->
            val route: Route.MedDetail = backStackEntry.toRoute()
            MedicationDetailScreen(
                medicationId = route.medicationId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Route.AddMedication(id)) },
            )
        }
        composable<Route.AddMedication>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
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
