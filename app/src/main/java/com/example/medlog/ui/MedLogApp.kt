package com.example.medlog.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
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
import com.example.medlog.ui.screen.symptom.SymptomDiaryScreen
import com.example.medlog.ui.screen.welcome.WelcomeScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedLogApp(openAddMedication: Boolean = false) {
    val appViewModel: MedLogAppViewModel = hiltViewModel()
    val startDest by appViewModel.startDestination.collectAsStateWithLifecycle()

    // DataStore 加载期间显示居中加载指示器
    if (startDest == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // ── POST_NOTIFICATIONS 权限请求（Android 13+）────────────────────
    val context = LocalContext.current
    var showNotifRationale by remember { mutableStateOf(false) }
    val requestNotifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 系统对话框结果由 OS 管理，无需额外处理 */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            showNotifRationale = true
        }
    }

    if (showNotifRationale) {
        AlertDialog(
            onDismissRequest = { showNotifRationale = false },
            icon = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
            title = { Text("开启通知提醒") },
            text = {
                Text("MedLog 需要通知权限才能在服药时间准时提醒您。建议开启以确保不会错过用药计划。")
            },
            confirmButton = {
                Button(onClick = {
                    showNotifRationale = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text("开启通知") }
            },
            dismissButton = {
                TextButton(onClick = { showNotifRationale = false }) { Text("暂不") }
            },
        )
    }

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
                Route.Diary     -> featureFlags.enableSymptomDiary
                Route.Drugs     -> featureFlags.enableDrugDatabase
                else            -> true  // Home / History / Settings 始终可见
            }
        }
    }

    // Decide whether to show the main navigation wrapper
    // Welcome 屏不展示导航栏
    val isOnWelcome = currentDestination?.hasRoute(Route.Welcome::class) == true
    val showMainNav = !isOnWelcome && (currentDestination == null ||
        TOP_LEVEL_DESTINATIONS.any { currentDestination.hasRoute(it.route::class) })

    if (showMainNav) {
        MedLogNavigationWrapper(
            currentDestination = currentDestination,
            navigateToTopLevel = navigateToTopLevel,
            destinations = enabledDestinations,
        ) {
            MedLogNavHost(navController = navController, startDest = startDest!!)
        }
    } else {
        MedLogNavHost(navController = navController, startDest = startDest!!)
    }
}

@Composable
private fun MedLogNavHost(
    navController: androidx.navigation.NavHostController,
    startDest: Route,
) {
    NavHost(
        navController = navController,
        startDestination = startDest,
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
        // ── 欢迎引导（首次启动）───────────────────────────
        composable<Route.Welcome>(
            enterTransition = { fadeIn() },
            exitTransition  = { fadeOut() },
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
        composable<Route.Diary>(
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            SymptomDiaryScreen()
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
