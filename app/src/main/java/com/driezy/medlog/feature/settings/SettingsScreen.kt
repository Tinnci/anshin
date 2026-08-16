package com.driezy.medlog.feature.settings

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

internal enum class SettingsScreenMode(@param:StringRes val titleRes: Int) {
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
    viewModel: SettingsHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
        uiState = uiState,
        onAction = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScaffold(
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
    uiState: SettingsUiState,
    onAction: (SettingsUiAction) -> Unit,
    dataInProgress: Boolean = false,
    dataEffects: kotlinx.coroutines.flow.Flow<SettingsUiEffect>? = null,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    var showRestartDialog by remember { mutableStateOf(false) }
    fun restartApplication() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
    LaunchedEffect(Unit) {
        onAction(SettingsUiAction.RefreshAiUsage)
    }

    // 精确闹钟权限检测（Android 12+）
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
                    val wasGranted = canScheduleExactAlarms
                    canScheduleExactAlarms =
                        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
                    if (!wasGranted && canScheduleExactAlarms) {
                        onAction(SettingsUiAction.PermissionsRecovered)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val wasGranted = canPostNotifications
                    canPostNotifications = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!wasGranted && canPostNotifications) {
                        onAction(SettingsUiAction.PermissionsRecovered)
                    }
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
        if (isGranted) onAction(SettingsUiAction.PermissionsRecovered)
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
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) onAction(SettingsUiAction.Backup(uri)) }

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
        dataEffects?.collect { effect ->
            when (effect) {
                is SettingsUiEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                SettingsUiEffect.RestartApplication -> {
                    showRestartDialog = true
                }
            }
        }
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text(stringResource(R.string.settings_restart_dialog_title)) },
            text = { Text(stringResource(R.string.settings_restart_dialog_body)) },
            confirmButton = {
                TextButton(onClick = { restartApplication() }) {
                    Text(stringResource(R.string.settings_restart_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            },
        )
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
                        onAction = onAction,
                        palettePreviewDark = palettePreviewDark,
                    )
                }
                SettingsScreenMode.REMINDERS -> item(key = "reminders", contentType = "settings-section") {
                    SettingsReminderContent(
                        uiState = uiState,
                        onAction = onAction,
                    )
                }
                SettingsScreenMode.MODULES -> item(key = "modules", contentType = "settings-section") {
                    SettingsHomeModulesContent(
                        uiState = uiState,
                        onAction = onAction,
                    )
                }
                SettingsScreenMode.INTELLIGENCE -> item(key = "intelligence", contentType = "settings-section") {
                    SettingsIntelligenceContent(
                        uiState = uiState,
                        onAction = onAction,
                        onNavigateToCloudApiSettings = onNavigateToCloudApiSettings,
                    )
                }
                SettingsScreenMode.CLOUD_API -> item(key = "cloud-api", contentType = "settings-section") {
                    CloudApiSettingsContent(
                        uiState = uiState,
                        onAction = onAction,
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
                        onAction = onAction,
                    )
                }
                SettingsScreenMode.DATA -> item(key = "data", contentType = "settings-section") {
                    SettingsDataContent(
                        backupInProgress = dataInProgress,
                        onBackupClick = { backupLauncher.launch(it) },
                        onRestoreClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                        onReplayWelcome = {
                            onAction(SettingsUiAction.ResetWelcome)
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
                    pendingRestoreUri?.let { onAction(SettingsUiAction.Restore(it)) }
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
