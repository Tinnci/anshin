package com.driezy.medlog.ui.screen.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import kotlinx.coroutines.launch

private enum class SettingsScreenMode(@param:StringRes val titleRes: Int) {
    HOME(R.string.tab_settings),
    APPEARANCE(R.string.settings_group_appearance_home),
    REMINDERS(R.string.settings_group_reminders_routine),
    MODULES(R.string.settings_group_modules_meds),
    INTELLIGENCE(R.string.settings_group_intelligence),
    CLOUD_API(R.string.settings_ai_config_title),
    WIDGETS(R.string.settings_card_widgets),
    DATA(R.string.settings_group_data_about),
}

@Composable
fun SettingsScreen(
    onNavigateToWelcome: () -> Unit = {},
    onNavigateToAppearanceSettings: () -> Unit = {},
    onNavigateToReminderSettings: () -> Unit = {},
    onNavigateToModuleSettings: () -> Unit = {},
    onNavigateToIntelligenceSettings: () -> Unit = {},
    onNavigateToBpx1Settings: () -> Unit = {},
    onNavigateToWidgetSettings: () -> Unit = {},
    onNavigateToDataSettings: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    SettingsScaffold(
        mode = SettingsScreenMode.HOME,
        onNavigateToWelcome = onNavigateToWelcome,
        onNavigateToAppearanceSettings = onNavigateToAppearanceSettings,
        onNavigateToReminderSettings = onNavigateToReminderSettings,
        onNavigateToModuleSettings = onNavigateToModuleSettings,
        onNavigateToIntelligenceSettings = onNavigateToIntelligenceSettings,
        onNavigateToBpx1Settings = onNavigateToBpx1Settings,
        onNavigateToWidgetSettings = onNavigateToWidgetSettings,
        onNavigateToDataSettings = onNavigateToDataSettings,
        viewModel = viewModel,
    )
}

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    SettingsScaffold(
        mode = SettingsScreenMode.APPEARANCE,
        onBack = onBack,
        viewModel = viewModel,
    )
}

@Composable
fun ReminderSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    SettingsScaffold(
        mode = SettingsScreenMode.REMINDERS,
        onBack = onBack,
        viewModel = viewModel,
    )
}

@Composable
fun ModuleSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    SettingsScaffold(
        mode = SettingsScreenMode.MODULES,
        onBack = onBack,
        viewModel = viewModel,
    )
}

@Composable
fun IntelligenceSettingsScreen(
    onBack: () -> Unit,
    onNavigateToCloudApiSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    SettingsScaffold(
        mode = SettingsScreenMode.INTELLIGENCE,
        onBack = onBack,
        onNavigateToCloudApiSettings = onNavigateToCloudApiSettings,
        viewModel = viewModel,
    )
}

@Composable
fun CloudApiSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    SettingsScaffold(
        mode = SettingsScreenMode.CLOUD_API,
        onBack = onBack,
        viewModel = viewModel,
    )
}

@Composable
fun WidgetSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    SettingsScaffold(
        mode = SettingsScreenMode.WIDGETS,
        onBack = onBack,
        viewModel = viewModel,
    )
}

@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    onNavigateToWelcome: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    SettingsScaffold(
        mode = SettingsScreenMode.DATA,
        onBack = onBack,
        onNavigateToWelcome = onNavigateToWelcome,
        viewModel = viewModel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    mode: SettingsScreenMode,
    onBack: (() -> Unit)? = null,
    onNavigateToWelcome: () -> Unit = {},
    onNavigateToAppearanceSettings: () -> Unit = {},
    onNavigateToReminderSettings: () -> Unit = {},
    onNavigateToModuleSettings: () -> Unit = {},
    onNavigateToIntelligenceSettings: () -> Unit = {},
    onNavigateToCloudApiSettings: () -> Unit = {},
    onNavigateToBpx1Settings: () -> Unit = {},
    onNavigateToWidgetSettings: () -> Unit = {},
    onNavigateToDataSettings: () -> Unit = {},
    viewModel: SettingsViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    LaunchedEffect(Unit) {
        viewModel.refreshAiUsageSummary()
    }

    // 精确闹钟权限检测（Android 12+）
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var canScheduleExactAlarms by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            } else {
                true
            },
        )
    }
    // 通知权限检测（Android 13+）
    var canPostNotifications by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    canScheduleExactAlarms =
                        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    canPostNotifications = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 通知权限：优先弹系统对话框，被永久拒绝后才跳转到设置页
    var hasRequestedNotifPerm by rememberSaveable { mutableStateOf(false) }
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        canPostNotifications = isGranted
        hasRequestedNotifPerm = true
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val msgWidgetPinOem = stringResource(R.string.settings_widget_pin_oem)
    val msgWidgetPinOk = stringResource(R.string.settings_widget_pin_ok)
    val systemDarkTheme = isSystemInDarkTheme()
    val palettePreviewDark = when (uiState.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDarkTheme
    }

    // ── 备份/恢复 ─────────────────────────
    val backupInProgress by viewModel.backupInProgress.collectAsStateWithLifecycle()
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) viewModel.backup(uri) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirmDialog = true
        }
    }

    // 收集一次性事件
    LaunchedEffect(Unit) {
        viewModel.backupEvent.collect { event ->
            when (event) {
                is SettingsViewModel.BackupEvent.Success -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is SettingsViewModel.BackupEvent.Error -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is SettingsViewModel.BackupEvent.RestoreSuccess -> {
                    // 恢复成功 → 重启进程
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(mode.titleRes)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            MedLogIcon(
                                MedLogIcons.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MedLogSpacing.Large,
                top = innerPadding.calculateTopPadding(),
                end = MedLogSpacing.Large,
                bottom = innerPadding.calculateBottomPadding() + MedLogSpacing.XXLarge,
            ),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {
            if (mode == SettingsScreenMode.REMINDERS) {
                item(key = "reminder-permission-alerts", contentType = "permission-warning") {
                    SettingsReminderPermissionAlerts(
                        canScheduleExactAlarms = canScheduleExactAlarms,
                        canPostNotifications = canPostNotifications,
                        onRequestExactAlarmPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    },
                                )
                            }
                        },
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val activity = context as? android.app.Activity
                                val shouldShowRationale = activity != null &&
                                    ActivityCompat.shouldShowRequestPermissionRationale(
                                        activity,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                if (!hasRequestedNotifPerm || shouldShowRationale) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
            when (mode) {
                SettingsScreenMode.HOME -> {
                    settingsHomeSectionOrder.forEach { section ->
                        item(key = section.itemKey, contentType = "settings-section") {
                            when (section) {
                                SettingsHomeSection.OVERVIEW -> SettingsHomeOverviewPanel(
                                    uiState = uiState,
                                    canScheduleExactAlarms = canScheduleExactAlarms,
                                    canPostNotifications = canPostNotifications,
                                )
                                SettingsHomeSection.DESTINATIONS -> SettingsHomeDashboard(
                                    uiState = uiState,
                                    onNavigateToAppearanceSettings = onNavigateToAppearanceSettings,
                                    onNavigateToReminderSettings = onNavigateToReminderSettings,
                                    onNavigateToModuleSettings = onNavigateToModuleSettings,
                                    onNavigateToIntelligenceSettings = onNavigateToIntelligenceSettings,
                                    onNavigateToBpx1Settings = onNavigateToBpx1Settings,
                                    onNavigateToWidgetSettings = onNavigateToWidgetSettings,
                                    onNavigateToDataSettings = onNavigateToDataSettings,
                                )
                            }
                        }
                    }
                }
                SettingsScreenMode.APPEARANCE -> item(key = "appearance", contentType = "settings-section") {
                    SettingsAppearanceContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        palettePreviewDark = palettePreviewDark,
                    )
                }
                SettingsScreenMode.REMINDERS -> item(key = "reminders", contentType = "settings-section") {
                    SettingsReminderContent(
                        uiState = uiState,
                        viewModel = viewModel,
                    )
                }
                SettingsScreenMode.MODULES -> item(key = "modules", contentType = "settings-section") {
                    SettingsHomeModulesContent(
                        uiState = uiState,
                        viewModel = viewModel,
                    )
                }
                SettingsScreenMode.INTELLIGENCE -> item(key = "intelligence", contentType = "settings-section") {
                    SettingsIntelligenceContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onNavigateToCloudApiSettings = onNavigateToCloudApiSettings,
                    )
                }
                SettingsScreenMode.CLOUD_API -> item(key = "cloud-api", contentType = "settings-section") {
                    CloudApiSettingsContent(
                        uiState = uiState,
                        viewModel = viewModel,
                    )
                }
                SettingsScreenMode.WIDGETS -> item(key = "widgets", contentType = "settings-section") {
                    SettingsWidgetsContent(
                        context = context,
                        scope = scope,
                        snackbarHostState = snackbarHostState,
                        msgWidgetPinOem = msgWidgetPinOem,
                        msgWidgetPinOk = msgWidgetPinOk,
                        uiState = uiState,
                        viewModel = viewModel,
                    )
                }
                SettingsScreenMode.DATA -> item(key = "data", contentType = "settings-section") {
                    SettingsDataContent(
                        backupInProgress = backupInProgress,
                        onBackupClick = { backupLauncher.launch(it) },
                        onRestoreClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                        onReplayWelcome = {
                            viewModel.resetWelcome()
                            onNavigateToWelcome()
                        },
                    )
                }
            }
        }
    }

    // ── 恢复确认对话框 ───────────────────────────────────────────
    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                pendingRestoreUri = null
            },
            icon = { MedLogIcon(MedLogIcons.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
            text = { Text(stringResource(R.string.settings_restore_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirmDialog = false
                    pendingRestoreUri?.let { viewModel.restore(it) }
                    pendingRestoreUri = null
                }) {
                    Text(
                        stringResource(R.string.settings_restore_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreConfirmDialog = false
                    pendingRestoreUri = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
