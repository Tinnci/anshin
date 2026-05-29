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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.repository.AiUsageSummaryRow
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.OpenAiCompatibleCloudAuthMode
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.data.repository.OcrModelType
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onNavigateToWelcome: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
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
                title = { Text(stringResource(R.string.tab_settings)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = MedLogSpacing.Large)
                .padding(bottom = MedLogSpacing.XXLarge),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {

            // ── Android 12+ 精确闹钟权限警告卡片 ────────────────────
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

            // ── Android 13+ 通知权限警告卡片 ─────────────────────
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
            // ── 外观 ───────────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.settings_card_appearance), icon = MedLogIcons.Palette) {
                // ―― 主题模式 ――
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MedLogSpacing.Large)
                        .padding(top = MedLogSpacing.Medium, bottom = MedLogSpacing.Small),
                    verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    ) {
                        MedLogIcon(
                            MedLogIcons.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            stringResource(R.string.settings_theme),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    val themeModes = listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                        ThemeMode.LIGHT  to stringResource(R.string.settings_theme_light),
                        ThemeMode.DARK   to stringResource(R.string.settings_theme_dark),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    ) {
                        themeModes.forEachIndexed { index, (mode, label) ->
                            ToggleButton(
                                checked = uiState.themeMode == mode,
                                onCheckedChange = { viewModel.setThemeMode(mode) },
                                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    themeModes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                // ―― Material You 动态颜色（Android 12+）――
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_dynamic_color_title),
                        subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                        checked = uiState.useDynamicColor,
                        onCheckedChange = viewModel::setUseDynamicColor,
                        icon = MedLogIcons.ColorLens,
                    )
                }
                SettingsSectionDivider(
                    title = stringResource(R.string.settings_card_today),
                    icon = MedLogIcons.ViewAgenda,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_collapse_title),
                    subtitle = stringResource(R.string.settings_auto_collapse_subtitle),
                    checked = uiState.autoCollapseCompletedGroups,
                    onCheckedChange = viewModel::setAutoCollapseCompletedGroups,
                    icon = MedLogIcons.UnfoldLess,
                )
            }

            // ── 提醒设置 ─────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.settings_card_reminder), icon = MedLogIcons.Notifications) {
                NotificationSettingsOverview(uiState)
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_persistent_title),
                    subtitle = stringResource(R.string.settings_persistent_subtitle),
                    checked = uiState.persistentReminder,
                    onCheckedChange = viewModel::setPersistentReminder,
                    icon = MedLogIcons.NotificationsActive,
                )
                AnimatedVisibility(
                    visible = uiState.persistentReminder,
                    enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MedLogSpacing.Large)
                            .padding(bottom = MedLogSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                    ) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = MedLogSpacing.Tiny))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                        ) {
                            MedLogIcon(
                                MedLogIcons.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                stringResource(R.string.settings_interval_label),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                pluralStringResource(
                                    R.plurals.settings_minutes,
                                    uiState.persistentIntervalMinutes,
                                    uiState.persistentIntervalMinutes,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                        ) {
                            listOf(3, 5, 10, 15, 30).forEach { minutes ->
                                FilterChip(
                                    selected = uiState.persistentIntervalMinutes == minutes,
                                    onClick = { viewModel.setPersistentInterval(minutes) },
                                    label = { Text(pluralStringResource(R.plurals.settings_minutes, minutes, minutes)) },
                                )
                            }
                        }
                    }
                }
                // ── 提前预告提醒 ──────────────────────────────────
                HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MedLogSpacing.Large)
                        .padding(top = MedLogSpacing.Medium, bottom = MedLogSpacing.Tiny),
                    verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                    ) {
                        MedLogIcon(
                            MedLogIcons.AccessAlarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_early_reminder_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                if (uiState.earlyReminderMinutes > 0)
                                    pluralStringResource(
                                        R.plurals.settings_early_reminder_body_on,
                                        uiState.earlyReminderMinutes,
                                        uiState.earlyReminderMinutes,
                                    )
                                else stringResource(R.string.settings_early_reminder_body_off),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    ) {
                        listOf(
                            0 to stringResource(R.string.settings_off),
                            15 to pluralStringResource(R.plurals.settings_minutes, 15, 15),
                            30 to pluralStringResource(R.plurals.settings_minutes, 30, 30),
                            60 to stringResource(R.string.settings_1hour),
                        ).forEach { (mins, label) ->
                            FilterChip(
                                selected = uiState.earlyReminderMinutes == mins,
                                onClick = { viewModel.setEarlyReminderMinutes(mins) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                SettingsSectionDivider(
                    title = stringResource(R.string.settings_follow_up_section),
                    icon = MedLogIcons.NotificationAdd,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_follow_up_enable),
                    subtitle = stringResource(R.string.settings_follow_up_enable_desc),
                    icon = MedLogIcons.AlarmAdd,
                    checked = uiState.followUpReminderEnabled,
                    onCheckedChange = { viewModel.setFollowUpSettings(enabled = it) },
                )
                AnimatedVisibility(
                    visible = uiState.followUpReminderEnabled,
                    enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MedLogSpacing.Large)
                            .padding(bottom = MedLogSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    ) {
                        HorizontalDivider()
                        // ── 再提醒间隔 ────────────────────────────
                        Text(
                            stringResource(R.string.settings_follow_up_delay),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                        ) {
                            listOf(10, 15, 30, 60).forEach { mins ->
                                FilterChip(
                                    selected = uiState.followUpDelayMinutes == mins,
                                    onClick = { viewModel.setFollowUpSettings(delayMinutes = mins) },
                                    label = {
                                        Text(
                                            pluralStringResource(
                                                R.plurals.settings_follow_up_delay_min,
                                                mins,
                                                mins,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                        // ── 最多再提醒次数 ─────────────────────────
                        Text(
                            stringResource(R.string.settings_follow_up_count),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                        ) {
                            listOf(1, 2, 3).forEach { count ->
                                FilterChip(
                                    selected = uiState.followUpMaxCount == count,
                                    onClick = { viewModel.setFollowUpSettings(maxCount = count) },
                                    label = { Text("$count") },
                                )
                            }
                        }
                    }
                }
                SettingsSectionDivider(
                    title = stringResource(R.string.settings_routine),
                    icon = MedLogIcons.Schedule,
                )
                // ── 模式开关 ──────────────────────────────────────
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_routine_mode_title),
                    subtitle = if (uiState.enableTimePeriodMode)
                        stringResource(R.string.settings_routine_mode_subtitle_on)
                    else
                        stringResource(R.string.settings_routine_mode_subtitle_off),
                    icon = MedLogIcons.Schedule,
                    checked = uiState.enableTimePeriodMode,
                    onCheckedChange = { viewModel.setEnableTimePeriodMode(it) },
                )
                // ── 仅在作息模式开启时显示详细时间设置 ────────────
                AnimatedVisibility(
                    visible = uiState.enableTimePeriodMode,
                    enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
                ) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
                        Text(
                            stringResource(R.string.settings_routine_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = MedLogSpacing.Large)
                                .padding(top = MedLogSpacing.Small, bottom = MedLogSpacing.Tiny),
                        )
                        // ── 一览行：五个时间快速预览 ──────────────────────
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MedLogSpacing.Large)
                                .padding(bottom = MedLogSpacing.Small),
                            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                        ) {
                            listOf(
                                Triple(MedLogIcons.WbSunny,      stringResource(R.string.settings_routine_wake), "%02d:%02d".format(uiState.wakeHour,      uiState.wakeMinute)),
                                Triple(MedLogIcons.Coffee,       stringResource(R.string.settings_routine_breakfast), "%02d:%02d".format(uiState.breakfastHour, uiState.breakfastMinute)),
                                Triple(MedLogIcons.LunchDining,  stringResource(R.string.settings_routine_lunch), "%02d:%02d".format(uiState.lunchHour,     uiState.lunchMinute)),
                                Triple(MedLogIcons.DinnerDining, stringResource(R.string.settings_routine_dinner), "%02d:%02d".format(uiState.dinnerHour,    uiState.dinnerMinute)),
                                Triple(MedLogIcons.Bedtime,      stringResource(R.string.settings_routine_bed), "%02d:%02d".format(uiState.bedHour,       uiState.bedMinute)),
                            ).forEach { (icon, label, time) ->
                                SuggestionChip(
                                    onClick = {},
                                    enabled = false,
                                    icon = {
                                        MedLogIcon(
                                            icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    },
                                    label = {
                                        Text(
                                            "$label $time",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                )
                            }
                        }
                        RoutineTimeRow(stringResource(R.string.settings_routine_wake), uiState.wakeHour, uiState.wakeMinute,
                            MedLogIcons.WbSunny) { h, m -> viewModel.updateRoutineTime("wake", h, m) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
                        RoutineTimeRow(stringResource(R.string.settings_routine_breakfast), uiState.breakfastHour, uiState.breakfastMinute,
                            MedLogIcons.Coffee) { h, m -> viewModel.updateRoutineTime("breakfast", h, m) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
                        RoutineTimeRow(stringResource(R.string.settings_routine_lunch), uiState.lunchHour, uiState.lunchMinute,
                            MedLogIcons.LunchDining) { h, m -> viewModel.updateRoutineTime("lunch", h, m) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
                        RoutineTimeRow(stringResource(R.string.settings_routine_dinner), uiState.dinnerHour, uiState.dinnerMinute,
                            MedLogIcons.DinnerDining) { h, m -> viewModel.updateRoutineTime("dinner", h, m) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
                        RoutineTimeRow(stringResource(R.string.settings_routine_bed), uiState.bedHour, uiState.bedMinute,
                            MedLogIcons.Bedtime) { h, m -> viewModel.updateRoutineTime("bed", h, m) }
                    }
                }
                SettingsSectionDivider(
                    title = stringResource(R.string.settings_card_travel),
                    icon = MedLogIcons.FlightTakeoff,
                )
                Text(
                    stringResource(R.string.settings_travel_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = MedLogSpacing.Large)
                        .padding(bottom = MedLogSpacing.Tiny),
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_travel_title),
                    subtitle = if (uiState.travelMode && uiState.homeTimeZoneId.isNotBlank())
                        stringResource(R.string.settings_travel_subtitle_on, uiState.homeTimeZoneId)
                    else
                        stringResource(R.string.settings_travel_subtitle_off),
                    checked = uiState.travelMode,
                    onCheckedChange = viewModel::setTravelMode,
                    icon = MedLogIcons.Schedule,
                )
            }

            // ── OCR 模型配置 ──────────────────────────────────────
            SettingsCard(
                title = stringResource(R.string.settings_group_ocr_health),
                icon = MedLogIcons.Memory
            ) {
                SettingsSectionDivider(
                    title = stringResource(R.string.settings_ocr_model_card_title),
                    icon = MedLogIcons.DocumentScanner,
                    modifier = Modifier.padding(top = 0.dp),
                )
                Text(
                    text = stringResource(R.string.settings_ocr_model_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = MedLogSpacing.Large)
                        .padding(bottom = MedLogSpacing.Medium),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MedLogSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium)
                ) {
                    OcrModelOptionCard(
                        title = stringResource(R.string.settings_ocr_model_light_title),
                        tag = stringResource(R.string.settings_ocr_model_light_tag),
                        description = stringResource(R.string.settings_ocr_model_light_desc),
                        specs = listOf(
                            MedLogIcons.Storage to stringResource(R.string.settings_ocr_model_light_size),
                            MedLogIcons.Speed to stringResource(R.string.settings_ocr_model_light_latency),
                            MedLogIcons.CheckCircle to stringResource(R.string.settings_ocr_model_light_accuracy),
                        ),
                        selected = uiState.ocrModelType == OcrModelType.LIGHT_SVTR,
                        tagContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        tagContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onSelect = { viewModel.setOcrModelType(OcrModelType.LIGHT_SVTR) },
                    )

                    OcrModelOptionCard(
                        title = stringResource(R.string.settings_ocr_model_fastvit_title),
                        tag = stringResource(R.string.settings_ocr_model_fastvit_tag),
                        description = stringResource(R.string.settings_ocr_model_fastvit_desc),
                        specs = listOf(
                            MedLogIcons.Storage to stringResource(R.string.settings_ocr_model_fastvit_size),
                            MedLogIcons.Speed to stringResource(R.string.settings_ocr_model_fastvit_latency),
                            MedLogIcons.CheckCircle to stringResource(R.string.settings_ocr_model_fastvit_accuracy),
                        ),
                        selected = uiState.ocrModelType == OcrModelType.FASTVIT_T8,
                        tagContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        tagContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onSelect = { viewModel.setOcrModelType(OcrModelType.FASTVIT_T8) },
                    )
                }

                SettingsSectionDivider(
                    title = stringResource(R.string.settings_ai_section_title),
                    icon = MedLogIcons.AutoAwesome,
                )
                Text(
                    text = stringResource(R.string.settings_ai_section_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = MedLogSpacing.Large)
                        .padding(bottom = MedLogSpacing.Tiny),
                )
                CloudAiStatusSummary(
                    uiState = uiState,
                    modifier = Modifier
                        .padding(horizontal = MedLogSpacing.Large)
                        .padding(bottom = MedLogSpacing.Small),
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_ai_enable_title),
                    subtitle = stringResource(R.string.settings_ai_enable_subtitle),
                    checked = uiState.cloudAiEnabled,
                    onCheckedChange = { viewModel.setCloudAiSettings(enabled = it) },
                    icon = MedLogIcons.CloudUpload,
                )
                AnimatedVisibility(
                    visible = uiState.cloudAiEnabled,
                    enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
                ) {
                    CloudAiSettingsPanel(
                        uiState = uiState,
                        onProviderChange = { viewModel.setCloudAiSettings(provider = it) },
                        onModelSave = { viewModel.setCloudAiSettings(model = it) },
                        onOpenAiBaseUrlSave = { viewModel.setCloudAiSettings(openAiCompatibleBaseUrl = it) },
                        onOpenAiAuthModeChange = { viewModel.setCloudAiSettings(openAiCompatibleAuthMode = it) },
                        onOpenAiProviderNameSave = { viewModel.setCloudAiSettings(openAiCompatibleProviderName = it) },
                        onImageAnalysisChange = { viewModel.setCloudAiSettings(imageAnalysisEnabled = it) },
                        onHealthInsightsChange = { viewModel.setCloudAiSettings(healthInsightsEnabled = it) },
                        onWifiOnlyChange = { viewModel.setCloudAiSettings(wifiOnly = it) },
                        onApiKeySave = viewModel::setCurrentCloudAiApiKey,
                        onApiKeyClear = viewModel::clearCurrentCloudAiApiKey,
                    )
                }
                SettingsSectionDivider(
                    title = stringResource(R.string.settings_card_features),
                    icon = MedLogIcons.Tune,
                )
                Text(
                    stringResource(R.string.settings_features_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = MedLogSpacing.Large)
                        .padding(bottom = MedLogSpacing.Tiny),
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_symptom_title),
                    subtitle = stringResource(R.string.settings_symptom_subtitle),
                    checked = uiState.enableSymptomDiary,
                    onCheckedChange = viewModel::setEnableSymptomDiary,
                    icon = MedLogIcons.EditNote,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_drug_db_title),
                    subtitle = stringResource(R.string.settings_drug_db_subtitle),
                    checked = uiState.enableDrugDatabase,
                    onCheckedChange = viewModel::setEnableDrugDatabase,
                    icon = MedLogIcons.MedicalServices,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_health_title),
                    subtitle = stringResource(R.string.settings_health_subtitle),
                    checked = uiState.enableHealthModule,
                    onCheckedChange = viewModel::setEnableHealthModule,
                    icon = MedLogIcons.MonitorHeart,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_interaction_title),
                    subtitle = stringResource(R.string.settings_interaction_subtitle),
                    checked = uiState.enableDrugInteractionCheck,
                    onCheckedChange = viewModel::setEnableDrugInteractionCheck,
                    icon = MedLogIcons.Warning,
                )
                SettingsSectionDivider(
                    title = stringResource(R.string.settings_card_meds),
                    icon = MedLogIcons.MedicalServices,
                )
                ArchivedMedicationsRow(
                    archived = uiState.archivedMedications,
                    onRestore = viewModel::unarchiveMedication,
                )
            }

            // ── 桌面小组件 ────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.settings_card_widgets), icon = MedLogIcons.Widgets) {
                val widgetManager = AppWidgetManager.getInstance(context)
                val canPin = widgetManager.isRequestPinAppWidgetSupported
                val oemNeedsPermission = OemWidgetHelper.requiresExtraPermission

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MedLogSpacing.Large)
                        .padding(top = MedLogSpacing.Tiny, bottom = MedLogSpacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                ) {
                    if (!canPin) {
                        // 桌面不支持直接固定时，显示手动添加引导
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(MedLogSpacing.Medium),
                                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MedLogIcon(
                                    MedLogIcons.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    stringResource(R.string.settings_widget_no_pin_hint, OemWidgetHelper.manualAddGuidance(context)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    } else {
                        // 可以固定——显示通用提示
                        Text(
                            stringResource(R.string.settings_widget_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // OEM 专属权限提醒（小米 / OPPO / vivo）
                        if (oemNeedsPermission) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Column(
                                    modifier = Modifier.padding(MedLogSpacing.Medium),
                                    verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        MedLogIcon(
                                            MedLogIcons.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                        OemWidgetHelper.permissionNote(context),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { OemWidgetHelper.openPermissionSettings(context) },
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = MedLogSpacing.Medium, vertical = MedLogSpacing.Small),
                                    ) {
                                        MedLogIcon(
                                            MedLogIcons.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(MedLogSpacing.Small))
                                        Text(stringResource(R.string.settings_widget_oem_btn), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    // 今日进度小组件显示模式开关（SSOT：与小组件长按设置共享同一 DataStore）
                    SettingsSwitchRow(
                        title    = stringResource(R.string.widget_settings_show_actions),
                        subtitle = if (uiState.widgetShowActions)
                            stringResource(R.string.widget_settings_show_actions_body)
                        else
                            stringResource(R.string.widget_settings_status_body),
                        checked         = uiState.widgetShowActions,
                        onCheckedChange = { viewModel.setWidgetShowActions(it) },
                        icon            = MedLogIcons.TouchApp,
                    )

                    WidgetPreviewCarousel(
                        items = listOf(
                            WidgetCarouselItem(
                                previewType = WidgetPreviewType.TODAY,
                                name = stringResource(R.string.settings_widget_today_name),
                                description = stringResource(R.string.settings_widget_today_desc),
                                sizes = listOf("2×2", "4×2", "4×4"),
                                canPin = canPin,
                                showActions = uiState.widgetShowActions,
                                onAdd = {
                                    if (canPin) {
                                        widgetManager.requestPinAppWidget(
                                            ComponentName(context, MedLogWidgetReceiver::class.java), null, null,
                                        )
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (oemNeedsPermission) msgWidgetPinOem else msgWidgetPinOk,
                                                duration = SnackbarDuration.Long,
                                            )
                                        }
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                OemWidgetHelper.manualAddGuidance(context),
                                                duration = SnackbarDuration.Long,
                                            )
                                        }
                                    }
                                },
                            ),
                            WidgetCarouselItem(
                                previewType = WidgetPreviewType.NEXT_DOSE,
                                name = stringResource(R.string.settings_widget_next_name),
                                description = stringResource(R.string.settings_widget_next_desc),
                                sizes = listOf("2×2", "4×2"),
                                canPin = canPin,
                                showActions = uiState.widgetShowActions,
                                onAdd = {
                                    if (canPin) {
                                        widgetManager.requestPinAppWidget(
                                            ComponentName(context, NextDoseWidgetReceiver::class.java), null, null,
                                        )
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (oemNeedsPermission) msgWidgetPinOem else msgWidgetPinOk,
                                                duration = SnackbarDuration.Long,
                                            )
                                        }
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                OemWidgetHelper.manualAddGuidance(context),
                                                duration = SnackbarDuration.Long,
                                            )
                                        }
                                    }
                                },
                            ),
                            WidgetCarouselItem(
                                previewType = WidgetPreviewType.STREAK,
                                name = stringResource(R.string.settings_widget_streak_name),
                                description = stringResource(R.string.settings_widget_streak_desc),
                                sizes = listOf("2×2", "4×2"),
                                canPin = canPin,
                                onAdd = {
                                    if (canPin) {
                                        widgetManager.requestPinAppWidget(
                                            ComponentName(context, StreakWidgetReceiver::class.java), null, null,
                                        )
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (oemNeedsPermission) msgWidgetPinOem else msgWidgetPinOk,
                                                duration = SnackbarDuration.Long,
                                            )
                                        }
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                OemWidgetHelper.manualAddGuidance(context),
                                                duration = SnackbarDuration.Long,
                                            )
                                        }
                                    }
                                },
                            ),
                        ),
                    )
                }
            }

            // ── 备份与恢复 ──────────────────────────────────────────
            SettingsCard(
                title = stringResource(R.string.settings_group_data_about),
                icon = MedLogIcons.CloudUpload,
            ) {
                SettingsSectionDivider(
                    title = stringResource(R.string.settings_backup_restore),
                    icon = MedLogIcons.CloudUpload,
                    modifier = Modifier.padding(top = 0.dp),
                )
                DataSafetyPanel()
                DataActionRow(
                    title = stringResource(R.string.settings_backup_title),
                    subtitle = stringResource(R.string.settings_backup_subtitle),
                    icon = MedLogIcons.Upload,
                    actionLabel = stringResource(R.string.settings_data_backup_action),
                    enabled = !backupInProgress,
                    loading = backupInProgress,
                    onClick = {
                        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                            .format(java.util.Date())
                        backupLauncher.launch("anshin_backup_$ts.db")
                    },
                )
                Spacer(Modifier.height(MedLogSpacing.Small))
                DataActionRow(
                    title = stringResource(R.string.settings_restore_title),
                    subtitle = stringResource(R.string.settings_data_restore_warning_body),
                    icon = MedLogIcons.Warning,
                    actionLabel = stringResource(R.string.settings_data_restore_action),
                    enabled = !backupInProgress,
                    destructive = true,
                    loading = backupInProgress,
                    onClick = {
                        restoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                )
                SettingsSectionDivider(
                    title = stringResource(R.string.settings_about),
                    icon = MedLogIcons.Info,
                )
                ListItem(
                    headlineContent = { Text("Anshin") },
                    supportingContent = {
                        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                    },
                    leadingContent = {
                        MedLogIcon(
                            MedLogIcons.Medication,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_replay_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_replay_subtitle)) },
                    leadingContent = {
                        MedLogIcon(
                            MedLogIcons.Replay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.resetWelcome()
                        onNavigateToWelcome()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )            }
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

private data class WidgetCarouselItem(
    val previewType: WidgetPreviewType,
    val name: String,
    val description: String,
    val sizes: List<String>,
    val canPin: Boolean,
    val showActions: Boolean = true,
    val onAdd: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetPreviewCarousel(items: List<WidgetCarouselItem>) {
    val carouselState = rememberCarouselState { items.size }

    HorizontalCenteredHeroCarousel(
        state = carouselState,
        modifier = Modifier
            .fillMaxWidth()
            .height(296.dp),
        itemSpacing = MedLogSpacing.Small,
        maxItemWidth = 320.dp,
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) { index ->
        val item = items[index]
        WidgetPickerCard(
            previewType = item.previewType,
            name = item.name,
            description = item.description,
            sizes = item.sizes,
            canPin = item.canPin,
            showActions = item.showActions,
            modifier = Modifier
                .fillMaxHeight()
                .maskClip(RoundedCornerShape(24.dp)),
            onAdd = item.onAdd,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CloudAiSettingsPanel(
    uiState: SettingsUiState,
    onProviderChange: (CloudAiProvider) -> Unit,
    onModelSave: (String) -> Unit,
    onOpenAiBaseUrlSave: (String) -> Unit,
    onOpenAiAuthModeChange: (OpenAiCompatibleCloudAuthMode) -> Unit,
    onOpenAiProviderNameSave: (String) -> Unit,
    onImageAnalysisChange: (Boolean) -> Unit,
    onHealthInsightsChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onApiKeySave: (String) -> Unit,
    onApiKeyClear: () -> Unit,
) {
    var modelDraft by rememberSaveable(uiState.cloudAiProvider) { mutableStateOf(uiState.cloudAiModel) }
    var baseUrlDraft by rememberSaveable(uiState.cloudAiProvider) { mutableStateOf(uiState.openAiCompatibleBaseUrl) }
    var providerNameDraft by rememberSaveable(uiState.cloudAiProvider) { mutableStateOf(uiState.openAiCompatibleProviderName) }
    var apiKeyDraft by rememberSaveable(uiState.cloudAiProvider) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(bottom = MedLogSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(MedLogSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
            ) {
                Text(
                    text = stringResource(R.string.settings_ai_provider_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                ) {
                    CloudAiProvider.entries.forEach { provider ->
                        FilterChip(
                            selected = uiState.cloudAiProvider == provider,
                            onClick = { onProviderChange(provider) },
                            label = { Text(provider.providerName) },
                            leadingIcon = if (provider in uiState.cloudAiAvailableProviders) {
                                {
                                    MedLogIcon(
                                        MedLogIcons.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = modelDraft,
                    onValueChange = { modelDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_ai_model_label)) },
                    supportingText = {
                        Text(stringResource(R.string.settings_ai_model_hint, uiState.cloudAiProvider.defaultModel))
                    },
                    trailingIcon = {
                        TextButton(onClick = { onModelSave(modelDraft) }) {
                            Text(stringResource(R.string.common_save))
                        }
                    },
                )
                if (uiState.cloudAiProvider == CloudAiProvider.OPENAI_COMPATIBLE) {
                    OutlinedTextField(
                        value = baseUrlDraft,
                        onValueChange = { baseUrlDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_ai_base_url_label)) },
                        supportingText = { Text(stringResource(R.string.settings_ai_base_url_hint)) },
                        trailingIcon = {
                            TextButton(onClick = { onOpenAiBaseUrlSave(baseUrlDraft) }) {
                                Text(stringResource(R.string.common_save))
                            }
                        },
                    )
                    OutlinedTextField(
                        value = providerNameDraft,
                        onValueChange = { providerNameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_ai_provider_name_label)) },
                        trailingIcon = {
                            TextButton(onClick = { onOpenAiProviderNameSave(providerNameDraft) }) {
                                Text(stringResource(R.string.common_save))
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    ) {
                        OpenAiCompatibleCloudAuthMode.entries.forEachIndexed { index, mode ->
                            ToggleButton(
                                checked = uiState.openAiCompatibleAuthMode == mode,
                                onCheckedChange = { onOpenAiAuthModeChange(mode) },
                                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                },
                            ) {
                                Text(
                                    text = when (mode) {
                                        OpenAiCompatibleCloudAuthMode.BEARER -> stringResource(R.string.settings_ai_auth_bearer)
                                        OpenAiCompatibleCloudAuthMode.API_KEY_HEADER -> stringResource(R.string.settings_ai_auth_api_key)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }

        val imageAnalysisSupported = uiState.cloudAiSupportsImageInput
        SettingsSwitchRow(
            title = stringResource(R.string.settings_ai_image_title),
            subtitle = stringResource(
                if (imageAnalysisSupported) {
                    R.string.settings_ai_image_subtitle
                } else {
                    R.string.settings_ai_image_unsupported_subtitle
                },
            ),
            checked = uiState.cloudAiImageAnalysisEnabled && imageAnalysisSupported,
            onCheckedChange = { enabled ->
                if (imageAnalysisSupported) {
                    onImageAnalysisChange(enabled)
                }
            },
            icon = MedLogIcons.DocumentScanner,
            enabled = imageAnalysisSupported,
        )
        HorizontalDivider()
        SettingsSwitchRow(
            title = stringResource(R.string.settings_ai_insights_title),
            subtitle = stringResource(R.string.settings_ai_insights_subtitle),
            checked = uiState.cloudAiHealthInsightsEnabled,
            onCheckedChange = onHealthInsightsChange,
            icon = MedLogIcons.AutoAwesome,
        )
        HorizontalDivider()
        SettingsSwitchRow(
            title = stringResource(R.string.settings_ai_wifi_only_title),
            subtitle = stringResource(R.string.settings_ai_wifi_only_subtitle),
            checked = uiState.cloudAiWifiOnly,
            onCheckedChange = onWifiOnlyChange,
            icon = MedLogIcons.CloudUpload,
        )
        CloudAiUsageSummaryCard(summary = uiState.aiUsageSummary)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(MedLogSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_ai_api_key_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (uiState.cloudAiProviderHasApiKey) {
                                stringResource(R.string.settings_ai_api_key_configured, uiState.cloudAiProvider.providerName)
                            } else {
                                stringResource(R.string.settings_ai_api_key_missing, uiState.cloudAiProvider.providerName)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (uiState.cloudAiProviderHasApiKey) {
                        OutlinedButton(onClick = onApiKeyClear) {
                            Text(stringResource(R.string.settings_ai_api_key_clear))
                        }
                    }
                }
                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_ai_api_key_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text(stringResource(R.string.settings_ai_api_key_storage_hint)) },
                )
                FilledTonalButton(
                    onClick = {
                        onApiKeySave(apiKeyDraft)
                        apiKeyDraft = ""
                    },
                    enabled = apiKeyDraft.isNotBlank(),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.settings_ai_api_key_save))
                }
            }
        }
    }
}

internal data class CloudAiUsageSummaryPresentation(
    val isEmpty: Boolean,
    val totalCount: Int,
    val successCount: Int,
    val errorCount: Int,
    val cacheHitCount: Int,
    val latestErrorCategory: String?,
) {
    companion object {
        fun from(rows: List<AiUsageSummaryRow>): CloudAiUsageSummaryPresentation =
            CloudAiUsageSummaryPresentation(
                isEmpty = rows.isEmpty(),
                totalCount = rows.sumOf { it.totalCount },
                successCount = rows.sumOf { it.successCount },
                errorCount = rows.sumOf { it.errorCount },
                cacheHitCount = rows.sumOf { it.cacheHitCount },
                latestErrorCategory = rows
                    .filter { it.lastErrorCategory != null }
                    .maxByOrNull { it.lastUsedAt }
                    ?.lastErrorCategory,
            )
    }
}

@Composable
private fun CloudAiUsageSummaryCard(
    summary: List<AiUsageSummaryRow>,
    modifier: Modifier = Modifier,
) {
    val presentation = CloudAiUsageSummaryPresentation.from(summary)
    if (presentation.isEmpty) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MedLogIcon(
                    MedLogIcons.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.settings_ai_usage_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(
                    R.string.settings_ai_usage_summary,
                    presentation.totalCount,
                    presentation.successCount,
                    presentation.errorCount,
                    presentation.cacheHitCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            presentation.latestErrorCategory?.let { latestError ->
                AssistChip(
                    onClick = {},
                    label = {
                        Text(stringResource(R.string.settings_ai_usage_last_error, latestError))
                    },
                    leadingIcon = {
                        MedLogIcon(
                            MedLogIcons.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}

internal enum class CloudAiSettingsVisualState {
    OFF,
    NEEDS_KEY,
    READY,
    TEXT_ONLY,
}

internal data class CloudAiSettingsPresentation(
    val visualState: CloudAiSettingsVisualState,
    @param:StringRes val labelRes: Int,
    @param:StringRes val bodyRes: Int,
) {
    companion object {
        fun from(
            enabled: Boolean,
            hasApiKey: Boolean,
            supportsImageInput: Boolean,
        ): CloudAiSettingsPresentation = when {
            !enabled -> CloudAiSettingsPresentation(
                visualState = CloudAiSettingsVisualState.OFF,
                labelRes = R.string.settings_ai_status_off,
                bodyRes = R.string.settings_ai_status_off_body,
            )
            !hasApiKey -> CloudAiSettingsPresentation(
                visualState = CloudAiSettingsVisualState.NEEDS_KEY,
                labelRes = R.string.settings_ai_status_needs_key,
                bodyRes = R.string.settings_ai_status_needs_key_body,
            )
            supportsImageInput -> CloudAiSettingsPresentation(
                visualState = CloudAiSettingsVisualState.READY,
                labelRes = R.string.settings_ai_status_ready,
                bodyRes = R.string.settings_ai_status_ready_body,
            )
            else -> CloudAiSettingsPresentation(
                visualState = CloudAiSettingsVisualState.TEXT_ONLY,
                labelRes = R.string.settings_ai_status_text_only,
                bodyRes = R.string.settings_ai_status_text_only_body,
            )
        }
    }
}

@Composable
private fun CloudAiStatusSummary(
    uiState: SettingsUiState,
    modifier: Modifier = Modifier,
) {
    val presentation = CloudAiSettingsPresentation.from(
        enabled = uiState.cloudAiEnabled,
        hasApiKey = uiState.cloudAiProviderHasApiKey,
        supportsImageInput = uiState.cloudAiSupportsImageInput,
    )
    val colors = when (presentation.visualState) {
        CloudAiSettingsVisualState.OFF -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        CloudAiSettingsVisualState.NEEDS_KEY -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        CloudAiSettingsVisualState.READY -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        CloudAiSettingsVisualState.TEXT_ONLY -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(MedLogSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MedLogIcon(
                icon = when (presentation.visualState) {
                    CloudAiSettingsVisualState.OFF -> MedLogIcons.CloudUpload
                    CloudAiSettingsVisualState.NEEDS_KEY -> MedLogIcons.Info
                    CloudAiSettingsVisualState.READY -> MedLogIcons.AutoAwesome
                    CloudAiSettingsVisualState.TEXT_ONLY -> MedLogIcons.AutoAwesome
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
            ) {
                Text(
                    text = stringResource(presentation.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(presentation.bodyRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
private fun OcrModelOptionCard(
    title: String,
    tag: String,
    description: String,
    specs: List<Pair<Int, String>>,
    selected: Boolean,
    tagContainerColor: Color,
    tagContentColor: Color,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MedLogSpacing.Medium),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = tagContainerColor,
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = tagContentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.padding(top = MedLogSpacing.Tiny),
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                ) {
                    specs.forEach { (icon, text) ->
                        SpecBadge(icon = icon, text = text)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecBadge(icon: Int, text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MedLogIcon(
                icon = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
