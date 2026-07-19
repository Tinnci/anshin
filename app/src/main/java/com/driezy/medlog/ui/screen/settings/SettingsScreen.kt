package com.driezy.medlog.ui.screen.settings

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.driezy.medlog.ui.theme.MedLogSpacing
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.driezy.medlog.BuildConfig
import com.driezy.medlog.R
import com.driezy.medlog.ai.CloudAiEndpointPreset
import com.driezy.medlog.ai.CloudAiEndpointProtocol
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.repository.AiUsageSummaryRow
import com.driezy.medlog.data.repository.AppTextScale
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.FontMode
import com.driezy.medlog.data.repository.OpenAiCompatibleCloudAuthMode
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.data.repository.OcrModelType
import com.driezy.medlog.data.repository.UiDensityScale
import com.driezy.medlog.ui.theme.ThemePalette
import com.driezy.medlog.widget.MedLogWidgetReceiver
import com.driezy.medlog.widget.NextDoseWidgetReceiver
import com.driezy.medlog.widget.StreakWidgetReceiver
import com.driezy.medlog.ui.utils.OemWidgetHelper
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.text.KeyboardOptions
import androidx.annotation.StringRes

private enum class SettingsScreenMode(@param:StringRes val titleRes: Int) {
    HOME(R.string.tab_settings),
    REMINDERS(R.string.settings_group_reminders_routine),
    INTELLIGENCE(R.string.settings_group_intelligence),
    CLOUD_API(R.string.settings_ai_config_title),
    WIDGETS(R.string.settings_card_widgets),
    DATA(R.string.settings_group_data_about),
}

@Composable
fun SettingsScreen(
    onNavigateToWelcome: () -> Unit = {},
    onNavigateToReminderSettings: () -> Unit = {},
    onNavigateToIntelligenceSettings: () -> Unit = {},
    onNavigateToBpx1Settings: () -> Unit = {},
    onNavigateToWidgetSettings: () -> Unit = {},
    onNavigateToDataSettings: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    SettingsScaffold(
        mode = SettingsScreenMode.HOME,
        onNavigateToWelcome = onNavigateToWelcome,
        onNavigateToReminderSettings = onNavigateToReminderSettings,
        onNavigateToIntelligenceSettings = onNavigateToIntelligenceSettings,
        onNavigateToBpx1Settings = onNavigateToBpx1Settings,
        onNavigateToWidgetSettings = onNavigateToWidgetSettings,
        onNavigateToDataSettings = onNavigateToDataSettings,
        viewModel = viewModel,
    )
}

@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    SettingsScaffold(
        mode = SettingsScreenMode.REMINDERS,
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
fun CloudApiSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    SettingsScaffold(
        mode = SettingsScreenMode.CLOUD_API,
        onBack = onBack,
        viewModel = viewModel,
    )
}

@Composable
fun WidgetSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsScaffold(
    mode: SettingsScreenMode,
    onBack: (() -> Unit)? = null,
    onNavigateToWelcome: () -> Unit = {},
    onNavigateToReminderSettings: () -> Unit = {},
    onNavigateToIntelligenceSettings: () -> Unit = {},
    onNavigateToCloudApiSettings: () -> Unit = {},
    onNavigateToBpx1Settings: () -> Unit = {},
    onNavigateToWidgetSettings: () -> Unit = {},
    onNavigateToDataSettings: () -> Unit = {},
    viewModel: SettingsViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motionScheme = MaterialTheme.motionScheme
    LaunchedEffect(Unit) {
        viewModel.refreshAiUsageSummary()
    }

    // 精确闹钟权限检测（Android 12+）
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var canScheduleExactAlarms by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            else true
        )
    }
    // 通知权限检测（Android 13+）
    var canPostNotifications by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            else true
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
                        context, Manifest.permission.POST_NOTIFICATIONS
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
        ActivityResultContracts.RequestPermission()
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
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) viewModel.backup(uri) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
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
            LargeTopAppBar(
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

            // ── Android 12+ 精确闹钟权限警告卡片 ────────────────────
            item(key = "exact-alarm-warning", contentType = "permission-warning") {
                AnimatedVisibility(
                visible = !canScheduleExactAlarms,
                enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(MedLogSpacing.Large),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                    ) {
                        MedLogIcon(
                            MedLogIcons.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline)) {
                            Text(
                                stringResource(R.string.settings_alarm_perm_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                stringResource(R.string.settings_alarm_perm_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                contentColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Text(stringResource(R.string.settings_alarm_perm_btn))
                        }
                    }
                }
                }
            }

            // ── Android 13+ 通知权限警告卡片 ─────────────────────
            item(key = "notification-warning", contentType = "permission-warning") {
                AnimatedVisibility(
                visible = !canPostNotifications,
                enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(MedLogSpacing.Large),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                    ) {
                        MedLogIcon(
                            MedLogIcons.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline)) {
                            Text(
                                stringResource(R.string.settings_notif_perm_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                stringResource(R.string.settings_notif_perm_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val activity = context as? android.app.Activity
                                    val shouldShowRationale = activity != null &&
                                        ActivityCompat.shouldShowRequestPermissionRationale(
                                            activity, Manifest.permission.POST_NOTIFICATIONS)
                                    if (!hasRequestedNotifPerm || shouldShowRationale) {
                                        // 首次或系统允许再次请求 → 弹系统权限对话框
                                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        // 被永久拒绝 → 引导跳转系统设置
                                        context.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                            }
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ) {
                            Text(stringResource(R.string.settings_notif_perm_btn))
                        }
                    }
                }
                }
            }
            when (mode) {
                SettingsScreenMode.HOME -> {
                    item(key = "home-overview", contentType = "settings-section") {
                        SettingsHomeOverviewPanel(
                            uiState = uiState,
                            canScheduleExactAlarms = canScheduleExactAlarms,
                            canPostNotifications = canPostNotifications,
                            onNavigateToReminderSettings = onNavigateToReminderSettings,
                            onNavigateToIntelligenceSettings = onNavigateToIntelligenceSettings,
                            onNavigateToDataSettings = onNavigateToDataSettings,
                        )
                    }
                    item(key = "home-appearance", contentType = "settings-section") {
                        SettingsHomeAppearanceContent(
                            uiState = uiState,
                            viewModel = viewModel,
                            palettePreviewDark = palettePreviewDark,
                        )
                    }
                    item(key = "home-modules", contentType = "settings-section") {
                        SettingsHomeModulesContent(
                            uiState = uiState,
                            viewModel = viewModel,
                            onNavigateToReminderSettings = onNavigateToReminderSettings,
                            onNavigateToIntelligenceSettings = onNavigateToIntelligenceSettings,
                            onNavigateToBpx1Settings = onNavigateToBpx1Settings,
                            onNavigateToWidgetSettings = onNavigateToWidgetSettings,
                            onNavigateToDataSettings = onNavigateToDataSettings,
                        )
                    }
                }
                SettingsScreenMode.REMINDERS -> item(key = "reminders", contentType = "settings-section") {
                    SettingsReminderContent(
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
