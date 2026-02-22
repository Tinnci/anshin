package com.example.medlog.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.example.medlog.BuildConfig
import com.example.medlog.R
import com.example.medlog.data.model.Medication
import com.example.medlog.data.repository.ThemeMode
import com.example.medlog.widget.MedLogWidgetReceiver
import com.example.medlog.widget.NextDoseWidgetReceiver
import com.example.medlog.widget.StreakWidgetReceiver
import com.example.medlog.ui.utils.OemWidgetHelper
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateToWelcome: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Android 12+ 精确闹钟权限警告卡片 ────────────────────
            AnimatedVisibility(
                visible = !canScheduleExactAlarms,
                enter = expandVertically(),
                exit = shrinkVertically(),
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
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                enter = expandVertically(),
                exit = shrinkVertically(),
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
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            SettingsCard(title = stringResource(R.string.settings_card_appearance), icon = Icons.Rounded.Palette) {
                // ―― 主题模式 ――
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.DarkMode,
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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themeModes.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = uiState.themeMode == mode,
                                onClick  = { viewModel.setThemeMode(mode) },
                                shape    = SegmentedButtonDefaults.itemShape(
                                    index = index, count = themeModes.size,
                                ),
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                // ―― Material You 动态颜色（Android 12+）――
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_dynamic_color_title),
                        subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                        checked = uiState.useDynamicColor,
                        onCheckedChange = viewModel::setUseDynamicColor,
                        icon = Icons.Rounded.ColorLens,
                    )
                }
            }
            // ── 今日页面 ─────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.settings_card_today), icon = Icons.Rounded.ViewAgenda) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_collapse_title),
                    subtitle = stringResource(R.string.settings_auto_collapse_subtitle),
                    checked = uiState.autoCollapseCompletedGroups,
                    onCheckedChange = viewModel::setAutoCollapseCompletedGroups,
                    icon = Icons.Rounded.UnfoldLess,
                )
            }

            // ── 提醒设置 ─────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.settings_card_reminder), icon = Icons.Rounded.Notifications) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_persistent_title),
                    subtitle = stringResource(R.string.settings_persistent_subtitle),
                    checked = uiState.persistentReminder,
                    onCheckedChange = viewModel::setPersistentReminder,
                    icon = Icons.Rounded.NotificationsActive,
                )
                AnimatedVisibility(
                    visible = uiState.persistentReminder,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Timer,
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
                                stringResource(R.string.settings_minutes, uiState.persistentIntervalMinutes),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(3, 5, 10, 15, 30).forEach { minutes ->
                                FilterChip(
                                    selected = uiState.persistentIntervalMinutes == minutes,
                                    onClick = { viewModel.setPersistentInterval(minutes) },
                                    label = { Text(stringResource(R.string.settings_minutes, minutes)) },
                                )
                            }
                        }
                    }
                }
                // ── 提前预告提醒 ──────────────────────────────────
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.AccessAlarm,
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
                                    stringResource(R.string.settings_early_reminder_body_on, uiState.earlyReminderMinutes)
                                else stringResource(R.string.settings_early_reminder_body_off),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0 to stringResource(R.string.settings_off), 15 to stringResource(R.string.settings_minutes, 15), 30 to stringResource(R.string.settings_minutes, 30), 60 to stringResource(R.string.settings_1hour)).forEach { (mins, label) ->
                            FilterChip(
                                selected = uiState.earlyReminderMinutes == mins,
                                onClick = { viewModel.setEarlyReminderMinutes(mins) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            // ── 作息时间 ─────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.settings_routine), icon = Icons.Rounded.Schedule) {
                // ── 模式开关 ──────────────────────────────────────
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_routine_mode_title),
                    subtitle = if (uiState.enableTimePeriodMode)
                        stringResource(R.string.settings_routine_mode_subtitle_on)
                    else
                        stringResource(R.string.settings_routine_mode_subtitle_off),
                    icon = Icons.Rounded.Schedule,
                    checked = uiState.enableTimePeriodMode,
                    onCheckedChange = { viewModel.setEnableTimePeriodMode(it) },
                )
                // ── 仅在作息模式开启时显示详细时间设置 ────────────
                AnimatedVisibility(
                    visible = uiState.enableTimePeriodMode,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Text(
                            stringResource(R.string.settings_routine_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp, bottom = 4.dp),
                        )
                        // ── 一览行：五个时间快速预览 ──────────────────────
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf(
                                Triple(Icons.Rounded.WbSunny,      stringResource(R.string.settings_routine_wake), "%02d:%02d".format(uiState.wakeHour,      uiState.wakeMinute)),
                                Triple(Icons.Rounded.Coffee,       stringResource(R.string.settings_routine_breakfast), "%02d:%02d".format(uiState.breakfastHour, uiState.breakfastMinute)),
                                Triple(Icons.Rounded.LunchDining,  stringResource(R.string.settings_routine_lunch), "%02d:%02d".format(uiState.lunchHour,     uiState.lunchMinute)),
                                Triple(Icons.Rounded.DinnerDining, stringResource(R.string.settings_routine_dinner), "%02d:%02d".format(uiState.dinnerHour,    uiState.dinnerMinute)),
                                Triple(Icons.Rounded.Bedtime,      stringResource(R.string.settings_routine_bed), "%02d:%02d".format(uiState.bedHour,       uiState.bedMinute)),
                            ).forEach { (icon, label, time) ->
                                SuggestionChip(
                                    onClick = {},
                                    enabled = false,
                                    icon = {
                                        Icon(
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
                            Icons.Rounded.WbSunny) { h, m -> viewModel.updateRoutineTime("wake", h, m) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        RoutineTimeRow(stringResource(R.string.settings_routine_breakfast), uiState.breakfastHour, uiState.breakfastMinute,
                            Icons.Rounded.Coffee) { h, m -> viewModel.updateRoutineTime("breakfast", h, m) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        RoutineTimeRow(stringResource(R.string.settings_routine_lunch), uiState.lunchHour, uiState.lunchMinute,
                            Icons.Rounded.LunchDining) { h, m -> viewModel.updateRoutineTime("lunch", h, m) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        RoutineTimeRow(stringResource(R.string.settings_routine_dinner), uiState.dinnerHour, uiState.dinnerMinute,
                            Icons.Rounded.DinnerDining) { h, m -> viewModel.updateRoutineTime("dinner", h, m) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        RoutineTimeRow(stringResource(R.string.settings_routine_bed), uiState.bedHour, uiState.bedMinute,
                            Icons.Rounded.Bedtime) { h, m -> viewModel.updateRoutineTime("bed", h, m) }
                    }
                }
            }

            // ── 旅行模式 ─────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.settings_card_travel), icon = Icons.Rounded.FlightTakeoff) {
                Text(
                    stringResource(R.string.settings_travel_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp),
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_travel_title),
                    subtitle = if (uiState.travelMode && uiState.homeTimeZoneId.isNotBlank())
                        stringResource(R.string.settings_travel_subtitle_on, uiState.homeTimeZoneId)
                    else
                        stringResource(R.string.settings_travel_subtitle_off),
                    checked = uiState.travelMode,
                    onCheckedChange = viewModel::setTravelMode,
                    icon = Icons.Rounded.Schedule,
                )
            }

            // ── 功能配置 ─────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.settings_card_features), icon = Icons.Rounded.Tune) {
                Text(
                    stringResource(R.string.settings_features_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp),
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_symptom_title),
                    subtitle = stringResource(R.string.settings_symptom_subtitle),
                    checked = uiState.enableSymptomDiary,
                    onCheckedChange = viewModel::setEnableSymptomDiary,
                    icon = Icons.Rounded.EditNote,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_drug_db_title),
                    subtitle = stringResource(R.string.settings_drug_db_subtitle),
                    checked = uiState.enableDrugDatabase,
                    onCheckedChange = viewModel::setEnableDrugDatabase,
                    icon = Icons.Rounded.MedicalServices,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_health_title),
                    subtitle = stringResource(R.string.settings_health_subtitle),
                    checked = uiState.enableHealthModule,
                    onCheckedChange = viewModel::setEnableHealthModule,
                    icon = Icons.Rounded.MonitorHeart,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_interaction_title),
                    subtitle = stringResource(R.string.settings_interaction_subtitle),
                    checked = uiState.enableDrugInteractionCheck,
                    onCheckedChange = viewModel::setEnableDrugInteractionCheck,
                    icon = Icons.Rounded.Warning,
                )
            }

            // ── 药品管理 ─────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.settings_card_meds), icon = Icons.Rounded.MedicalServices) {
                ArchivedMedicationsRow(
                    archived = uiState.archivedMedications,
                    onRestore = viewModel::unarchiveMedication,
                )
            }

            // ── 桌面小组件 ────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.settings_card_widgets), icon = Icons.Rounded.Widgets) {
                val widgetManager = AppWidgetManager.getInstance(context)
                val canPin = widgetManager.isRequestPinAppWidgetSupported
                val oemNeedsPermission = OemWidgetHelper.requiresExtraPermission

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!canPin) {
                        // 桌面不支持直接固定时，显示手动添加引导
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Info,
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
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Icon(
                                            Icons.Rounded.Warning,
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
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
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
                        icon            = Icons.Rounded.TouchApp,
                    )

                    // 今日进度小组件
                    WidgetPickerCard(
                        previewRes = R.drawable.widget_preview_today,
                        name = stringResource(R.string.settings_widget_today_name),
                        description = stringResource(R.string.settings_widget_today_desc),
                        sizes = listOf("2×2", "4×2", "4×4"),
                        canPin = canPin,
                    ) {
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
                    }

                    // 下次服药小组件
                    WidgetPickerCard(
                        previewRes = R.drawable.widget_preview_next_dose,
                        name = stringResource(R.string.settings_widget_next_name),
                        description = stringResource(R.string.settings_widget_next_desc),
                        sizes = listOf("2×2", "4×2"),
                        canPin = canPin,
                    ) {
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
                    }

                    // 连续打卡小组件
                    WidgetPickerCard(
                        previewRes = R.drawable.widget_preview_streak,
                        name = stringResource(R.string.settings_widget_streak_name),
                        description = stringResource(R.string.settings_widget_streak_desc),
                        sizes = listOf("2×2", "4×2"),
                        canPin = canPin,
                    ) {
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
                    }
                }
            }

            // ── 关于 ─────────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.settings_about), icon = Icons.Rounded.Info) {
                ListItem(
                    headlineContent = { Text("Anshin") },
                    supportingContent = {
                        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Medication,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_replay_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_replay_subtitle)) },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Replay,
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
}

// ── 通用设置卡片组（24dp 扁平卡片，含组标题）────────────────────────────────

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
}

// ── 小组件选择卡片（预览图 + 说明 + 添加按钮）────────────────────────────────

@Composable
private fun WidgetPickerCard(
    previewRes: Int,
    name: String,
    description: String,
    sizes: List<String>,
    canPin: Boolean,
    onAdd: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        // 预览区域
        Image(
            painter = painterResource(previewRes),
            contentDescription = stringResource(R.string.settings_widget_preview_cd, name),
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            contentScale = ContentScale.FillWidth,
        )
        // 信息区域
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                // 尺寸徽章
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    sizes.forEach { size ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(size, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp),
                        )
                    }
                }
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 添加按钮
            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.AddToHomeScreen, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (canPin) stringResource(R.string.settings_widget_add_btn) else stringResource(R.string.settings_widget_grant_btn),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ── Switch 行 ─────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

// ── 作息时间行（点击展开内联 TimeInput，无模态对话框）────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineTimeRow(
    label: String,
    hour: Int,
    minute: Int,
    icon: ImageVector,
    onTimeSelected: (Int, Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // 状态始终保持，不随 expanded 重置
    val timeState = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = true,
    )

    Column {
        ListItem(
            headlineContent = { Text(label) },
            supportingContent = {
                Text(
                    "%02d:%02d".format(hour, minute),
                    color = if (expanded) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingContent = {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.clickable { expanded = !expanded },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimeInput(state = timeState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = {
                        onTimeSelected(timeState.hour, timeState.minute)
                        expanded = false
                    }) { Text(stringResource(R.string.confirm)) }
                }
            }
        }
    }
}

// ── 已归档药品可展开列表（替代 ModalBottomSheet）────────────────────────────

@Composable
private fun ArchivedMedicationsRow(
    archived: List<Medication>,
    onRestore: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        ListItem(
            headlineContent = { Text(stringResource(R.string.archived_medications)) },
            supportingContent = {
                Text(
                    if (archived.isEmpty()) stringResource(R.string.settings_archived_empty)
                    else stringResource(R.string.settings_archived_count, archived.size),
                )
            },
            leadingContent = {
                Icon(Icons.Rounded.Archive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                if (archived.isNotEmpty()) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.clickable(enabled = archived.isNotEmpty()) { expanded = !expanded },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        AnimatedVisibility(
            visible = expanded && archived.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                archived.forEach { med ->
                    ListItem(
                        headlineContent = { Text(med.name) },
                        supportingContent = {
                            val catText = med.category.ifBlank { null }
                            val label = when {
                                med.isTcm && catText != null -> stringResource(R.string.tcm_cat_label, catText)
                                med.isTcm -> stringResource(R.string.tcm_label)
                                catText != null -> catText
                                else -> null
                            }
                            if (label != null) Text(label)
                        },
                        leadingContent = {
                            Icon(
                                if (med.isTcm) Icons.Rounded.LocalFlorist else Icons.Rounded.Medication,
                                null,
                                tint = if (med.isTcm)
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                                else
                                    MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingContent = {
                            FilledTonalButton(
                                onClick = { onRestore(med.id) },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            ) {
                                Text(stringResource(R.string.restore), style = MaterialTheme.typography.labelMedium)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}


