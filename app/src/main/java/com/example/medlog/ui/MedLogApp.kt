package com.example.medlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
fun MedLogApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

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
    ) {
        composable<Route.Home> {
            HomeScreen(
                onAddMedication = { navController.navigate(Route.AddMedication()) },
                onMedicationClick = { id -> navController.navigate(Route.MedDetail(id)) },
            )
        }
        composable<Route.History> {
            HistoryScreen()
        }
        composable<Route.Drugs> {
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
        composable<Route.Settings> {
            SettingsScreen()
        }
        composable<Route.MedDetail> { backStackEntry ->
            val route: Route.MedDetail = backStackEntry.toRoute()
            MedicationDetailScreen(
                medicationId = route.medicationId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Route.AddMedication(id)) },
            )
        }
        composable<Route.AddMedication> { backStackEntry ->
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
