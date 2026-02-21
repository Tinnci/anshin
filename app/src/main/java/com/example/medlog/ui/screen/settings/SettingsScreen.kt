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

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text("设置") },
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
                                "精确闹钟权限未授予",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                "提醒可能最多延迟数分钟，请前往系统设置开启『允许精确闹钟』",
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
                            Text("前往授权")
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
                                "通知权限未开启",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                "无法收到服药提醒，请在系统设置中开启通知权限",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                )
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ) {
                            Text("前往设置")
                        }
                    }
                }
            }
            // ── 外观 ───────────────────────────────────────────────
            SettingsCard(title = "外观", icon = Icons.Rounded.Palette) {
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
                            "主题",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    val themeModes = listOf(
                        ThemeMode.SYSTEM to "跟随系统",
                        ThemeMode.LIGHT  to "浅色",
                        ThemeMode.DARK   to "深色",
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
                        title = "Material You 动态颜色",
                        subtitle = "根据壁纸自动匹配应用配色（Android 12+）",
                        checked = uiState.useDynamicColor,
                        onCheckedChange = viewModel::setUseDynamicColor,
                        icon = Icons.Rounded.ColorLens,
                    )
                }
            }
            // ── 今日页面 ─────────────────────────────────────────
            SettingsCard(title = "今日页面", icon = Icons.Rounded.ViewAgenda) {
                SettingsSwitchRow(
                    title = "已完成分组默认折叠",
                    subtitle = "今日页面中已全部服用的时段将自动折叠，节省屏幕空间",
                    checked = uiState.autoCollapseCompletedGroups,
                    onCheckedChange = viewModel::setAutoCollapseCompletedGroups,
                    icon = Icons.Rounded.UnfoldLess,
                )
            }

            // ── 提醒设置 ─────────────────────────────────────────
            SettingsCard(title = "提醒设置", icon = Icons.Rounded.Notifications) {
                SettingsSwitchRow(
                    title = "持续提醒",
                    subtitle = "服药未确认时定期重复提醒",
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
                                "提醒间隔",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${uiState.persistentIntervalMinutes} 分钟",
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
                                    label = { Text("$minutes 分钟") },
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
                                "提前预告提醒",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                if (uiState.earlyReminderMinutes > 0)
                                    "在服药时间前 ${uiState.earlyReminderMinutes} 分钟发送预告"
                                else "关闭（仅在服药时间精确提醒）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0 to "关闭", 15 to "15 分钟", 30 to "30 分钟", 60 to "1 小时").forEach { (mins, label) ->
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
            SettingsCard(title = "作息时间", icon = Icons.Rounded.Schedule) {
                // ── 模式开关 ──────────────────────────────────────
                SettingsSwitchRow(
                    title = "作息时段提醒模式",
                    subtitle = if (uiState.enableTimePeriodMode)
                        "添加药品时可选「早餐后」「睡前」等时段自动换算时间"
                    else
                        "已关闭，添加药品时仅使用精确时间",
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
                            "用于模糊时段（如「早餐后」「睡前」）的提醒计算",
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
                                Triple(Icons.Rounded.WbSunny,      "起床", "%02d:%02d".format(uiState.wakeHour,      uiState.wakeMinute)),
                                Triple(Icons.Rounded.Coffee,       "早餐", "%02d:%02d".format(uiState.breakfastHour, uiState.breakfastMinute)),
                                Triple(Icons.Rounded.LunchDining,  "午餐", "%02d:%02d".format(uiState.lunchHour,     uiState.lunchMinute)),
                                Triple(Icons.Rounded.DinnerDining, "晚餐", "%02d:%02d".format(uiState.dinnerHour,    uiState.dinnerMinute)),
                                Triple(Icons.Rounded.Bedtime,      "睡觉", "%02d:%02d".format(uiState.bedHour,       uiState.bedMinute)),
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
                        RoutineTimeRow("起床", uiState.wakeHour, uiState.wakeMinute,
                            Icons.Rounded.WbSunny) { h, m -> viewModel.updateRoutineTime("wake", h, m) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        RoutineTimeRow("早餐", uiState.breakfastHour, uiState.breakfastMinute,
                            Icons.Rounded.Coffee) { h, m -> viewModel.updateRoutineTime("breakfast", h, m) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        RoutineTimeRow("午餐", uiState.lunchHour, uiState.lunchMinute,
                            Icons.Rounded.LunchDining) { h, m -> viewModel.updateRoutineTime("lunch", h, m) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        RoutineTimeRow("晚餐", uiState.dinnerHour, uiState.dinnerMinute,
                            Icons.Rounded.DinnerDining) { h, m -> viewModel.updateRoutineTime("dinner", h, m) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        RoutineTimeRow("睡觉", uiState.bedHour, uiState.bedMinute,
                            Icons.Rounded.Bedtime) { h, m -> viewModel.updateRoutineTime("bed", h, m) }
                    }
                }
            }

            // ── 旅行模式 ─────────────────────────────────────────
            SettingsCard(title = "旅行模式", icon = Icons.Rounded.FlightTakeoff) {
                Text(
                    "跨时区旅行时，保持按家乡时钟提醒服药，避免因时差打乱用药规律。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp),
                )
                SettingsSwitchRow(
                    title = "保持家乡时区提醒",
                    subtitle = if (uiState.travelMode && uiState.homeTimeZoneId.isNotBlank())
                        "基准：${uiState.homeTimeZoneId}"
                    else
                        "关闭时跟随设备时区",
                    checked = uiState.travelMode,
                    onCheckedChange = viewModel::setTravelMode,
                    icon = Icons.Rounded.Schedule,
                )
            }

            // ── 功能配置 ─────────────────────────────────────────
            SettingsCard(title = "功能配置", icon = Icons.Rounded.Tune) {
                Text(
                    "主要功能（今日用药、历史记录、设置）始终启用。以下为可选模块，关闭后相应标签将从导航栏隐藏。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp),
                )
                SettingsSwitchRow(
                    title = "症状日记",
                    subtitle = "记录每日症状、心情与备注",
                    checked = uiState.enableSymptomDiary,
                    onCheckedChange = viewModel::setEnableSymptomDiary,
                    icon = Icons.Rounded.EditNote,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsSwitchRow(
                    title = "药品数据库",
                    subtitle = "浏览内置西药 / 中成药数据库",
                    checked = uiState.enableDrugDatabase,
                    onCheckedChange = viewModel::setEnableDrugDatabase,
                    icon = Icons.Rounded.MedicalServices,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsSwitchRow(
                    title = "健康体征模块",
                    subtitle = "记录血压、血糖、体重等健康数据",
                    checked = uiState.enableHealthModule,
                    onCheckedChange = viewModel::setEnableHealthModule,
                    icon = Icons.Rounded.MonitorHeart,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsSwitchRow(
                    title = "药物相互作用检测",
                    subtitle = "在首页自动检测配伍风险并提示",
                    checked = uiState.enableDrugInteractionCheck,
                    onCheckedChange = viewModel::setEnableDrugInteractionCheck,
                    icon = Icons.Rounded.Warning,
                )
            }

            // ── 药品管理 ─────────────────────────────────────────
            SettingsCard(title = "药品管理", icon = Icons.Rounded.MedicalServices) {
                ArchivedMedicationsRow(
                    archived = uiState.archivedMedications,
                    onRestore = viewModel::unarchiveMedication,
                )
            }

            // ── 桌面小组件 ────────────────────────────────────────
            SettingsCard(title = "桌面小组件", icon = Icons.Rounded.Widgets) {
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
                                    "您的桌面不支持直接固定小组件。${OemWidgetHelper.manualAddGuidance}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    } else {
                        // 可以固定——显示通用提示
                        Text(
                            "点击添加按钮，将小组件固定到桌面",
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
                                            OemWidgetHelper.permissionNote,
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
                                        Text("前往授予「桌面快捷方式」权限", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    // 今日进度小组件
                    WidgetPickerCard(
                        previewRes = R.drawable.widget_preview_today,
                        name = "今日进度",
                        description = "显示今日服药进度，支持直接点击打卡确认",
                        sizes = listOf("2×2", "4×2", "4×4"),
                        canPin = canPin,
                    ) {
                        if (canPin) {
                            widgetManager.requestPinAppWidget(
                                ComponentName(context, MedLogWidgetReceiver::class.java), null, null,
                            )
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (oemNeedsPermission)
                                        "若未弹出对话框，请先点击上方「前往授权」开启桌面快捷方式权限"
                                    else
                                        "请在弹出的对话框中点击「添加」完成安装",
                                    duration = SnackbarDuration.Long,
                                )
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    OemWidgetHelper.manualAddGuidance,
                                    duration = SnackbarDuration.Long,
                                )
                            }
                        }
                    }

                    // 下次服药小组件
                    WidgetPickerCard(
                        previewRes = R.drawable.widget_preview_next_dose,
                        name = "下次服药",
                        description = "显示下次服药时间及倒计时，支持直接打卡",
                        sizes = listOf("2×2", "4×2"),
                        canPin = canPin,
                    ) {
                        if (canPin) {
                            widgetManager.requestPinAppWidget(
                                ComponentName(context, NextDoseWidgetReceiver::class.java), null, null,
                            )
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (oemNeedsPermission)
                                        "若未弹出对话框，请先点击上方「前往授权」开启桌面快捷方式权限"
                                    else
                                        "请在弹出的对话框中点击「添加」完成安装",
                                    duration = SnackbarDuration.Long,
                                )
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    OemWidgetHelper.manualAddGuidance,
                                    duration = SnackbarDuration.Long,
                                )
                            }
                        }
                    }

                    // 连续打卡小组件
                    WidgetPickerCard(
                        previewRes = R.drawable.widget_preview_streak,
                        name = "连续打卡",
                        description = "显示连续打卡天数及最近 7 天完成情况",
                        sizes = listOf("2×2", "4×2"),
                        canPin = canPin,
                    ) {
                        if (canPin) {
                            widgetManager.requestPinAppWidget(
                                ComponentName(context, StreakWidgetReceiver::class.java), null, null,
                            )
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (oemNeedsPermission)
                                        "若未弹出对话框，请先点击上方「前往授权」开启桌面快捷方式权限"
                                    else
                                        "请在弹出的对话框中点击「添加」完成安装",
                                    duration = SnackbarDuration.Long,
                                )
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    OemWidgetHelper.manualAddGuidance,
                                    duration = SnackbarDuration.Long,
                                )
                            }
                        }
                    }
                }
            }

            // ── 关于 ─────────────────────────────────────────────
            SettingsCard(title = "关于", icon = Icons.Rounded.Info) {
                ListItem(
                    headlineContent = { Text("Anshin") },
                    supportingContent = {
                        Text("版本 ${BuildConfig.VERSION_NAME}（构建 ${BuildConfig.VERSION_CODE}）")
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
                    headlineContent = { Text("重新查看新手引导") },
                    supportingContent = { Text("重新浏览功能介绍、作息设置等引导流程") },
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
            contentDescription = "$name 小组件预览",
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
                    if (canPin) "添加到桌面" else "前往设置授权",
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
                    TextButton(onClick = { expanded = false }) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = {
                        onTimeSelected(timeState.hour, timeState.minute)
                        expanded = false
                    }) { Text("确定") }
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
            headlineContent = { Text("已归档药品") },
            supportingContent = {
                Text(
                    if (archived.isEmpty()) "暂无归档药品"
                    else "${archived.size} 种药品已归档",
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
                                med.isTcm && catText != null -> "$catText · 中成药"
                                med.isTcm -> "中成药"
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
                                Text("恢复", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}


