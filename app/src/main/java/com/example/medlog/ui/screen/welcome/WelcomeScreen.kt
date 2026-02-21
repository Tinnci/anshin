package com.example.medlog.ui.screen.welcome

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medlog.data.repository.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onFinished: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 引导完成后由外部处理导航
    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) onFinished()
    }

    // ── 通知权限（Android 13+）──────────────────────────────
    val context = LocalContext.current
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            else true  // <13 默认已有通知权限
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted = granted }

    val pagerState = rememberPagerState(pageCount = { 6 })
    val scope = rememberCoroutineScope()

    // 弹簧翻页 fling 行为，snap 时使用 medium-low 弹簧
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── 页面内容（带弹簧 fling）────────────────────────────
            HorizontalPager(
                state = pagerState,
                flingBehavior = flingBehavior,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                val isCurrentPage = pagerState.settledPage == page
                when (page) {
                    0 -> WelcomePage0(isCurrentPage = isCurrentPage)
                    1 -> WelcomePage1(isCurrentPage = isCurrentPage)
                    2 -> WelcomePage2(
                        uiState       = uiState,
                        isCurrentPage = isCurrentPage,
                        onTimeChange  = viewModel::onTimeChange,
                    )
                    3 -> WelcomePage4(
                        uiState       = uiState,
                        isCurrentPage = isCurrentPage,
                        onToggleSymptomDiary         = viewModel::onToggleSymptomDiary,
                        onToggleDrugInteractionCheck = viewModel::onToggleDrugInteractionCheck,
                        onToggleDrugDatabase         = viewModel::onToggleDrugDatabase,
                        onToggleHealthModule         = viewModel::onToggleHealthModule,
                        onThemeModeChange            = viewModel::onThemeModeChange,
                    )
                    4 -> WelcomeNotificationPage(
                        isCurrentPage = isCurrentPage,
                        notifGranted  = notifGranted,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                    5 -> WelcomePage3(isCurrentPage = isCurrentPage)
                }
            }

            // ── 底部：页点 + 按钮 ─────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // 页面指示点（弹簧宽度 + 颜色过渡）
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(6) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue   = if (isSelected) 24.dp else 8.dp,
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                            label         = "dotWidth",
                        )
                        val dotColor by animateColorAsState(
                            targetValue   = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant,
                            animationSpec = tween(300),
                            label         = "dotColor",
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                    }
                }

                // 下一步 / 开始使用
                val isLastPage = pagerState.currentPage == 5
                Button(
                    onClick = {
                        if (isLastPage) {
                            viewModel.finishWelcome()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        if (isLastPage) "开始使用 MedLog" else "下一步",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (isLastPage) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.ArrowForward, contentDescription = null, Modifier.size(18.dp))
                    }
                }

                // 跳过（最后一页隐藏）
                AnimatedVisibility(visible = !isLastPage, enter = fadeIn(), exit = fadeOut()) {
                    TextButton(
                        onClick = { viewModel.finishWelcome() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("跳过", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// 动画辅助：弹簧入场 Animatable 帮助函数
// ────────────────────────────────────────────────────────────────────────────

/** 弹簧缩放 + 淡入（用于图标/Logo 入场） */
@Composable
private fun rememberSpringEntry(
    isCurrentPage: Boolean,
    initialScale: Float = 0.5f,
    delayMs: Long = 0L,
): Pair<Float, Float> {
    val scale = remember { Animatable(initialScale) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            delay(delayMs)
            launch { scale.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) }
            launch { alpha.animateTo(1f, tween(350)) }
        } else {
            scale.snapTo(initialScale)
            alpha.snapTo(0f)
        }
    }
    return scale.value to alpha.value
}

/** 弹簧上滑 + 淡入（用于标题/正文入场） */
@Composable
private fun rememberSlideEntry(
    isCurrentPage: Boolean,
    initialOffsetY: Float = 40f,
    delayMs: Long = 0L,
): Pair<Float, Float> {
    val offsetY = remember { Animatable(initialOffsetY) }
    val alpha   = remember { Animatable(0f) }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            delay(delayMs)
            launch { offsetY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
            launch { alpha.animateTo(1f, tween(300)) }
        } else {
            offsetY.snapTo(initialOffsetY)
            alpha.snapTo(0f)
        }
    }
    return offsetY.value to alpha.value
}

// ── 第0页：欢迎 ────────────────────────────────────────────────────────────

@Composable
private fun WelcomePage0(isCurrentPage: Boolean) {
    val (iconScale, iconAlpha) = rememberSpringEntry(isCurrentPage, 0.3f, 0L)
    val (titleY, titleAlpha)   = rememberSlideEntry(isCurrentPage,  24f, 150L)
    val (subY,   subAlpha)     = rememberSlideEntry(isCurrentPage,  24f, 250L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale; alpha = iconAlpha },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Medication,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "欢迎使用 MedLog",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationY = titleY; alpha = titleAlpha },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "您的智能用药管理助手\n帮助您按时用药、轻松管理药品库存",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationY = subY; alpha = subAlpha },
        )
    }
}

// ── 第1页：核心功能（错落进场） ────────────────────────────────────────────

private data class Feature(val icon: ImageVector, val title: String, val desc: String)

@Composable
private fun WelcomePage1(isCurrentPage: Boolean) {
    val features = remember {
        listOf(
            Feature(Icons.Rounded.NotificationsActive, "智能提醒",  "根据您的作息时间，在最合适的时机精准推送服药通知"),
            Feature(Icons.Rounded.Inventory2,          "库存管理",  "自动追踪药品余量，低库存时提醒您及时补充"),
            Feature(Icons.Rounded.History,             "用药记录",  "可视化日历展示每日用药完成率，帮助养成健康习惯"),
        )
    }
    val (titleY, titleAlpha) = rememberSlideEntry(isCurrentPage, 20f, 0L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "核心功能",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .graphicsLayer { translationY = titleY; alpha = titleAlpha },
        )
        features.forEachIndexed { index, feature ->
            AnimatedFeatureRow(feature, isCurrentPage, delayMs = 100L + index * 90L)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AnimatedFeatureRow(feature: Feature, isCurrentPage: Boolean, delayMs: Long) {
    val (offsetY, rowAlpha)    = rememberSlideEntry(isCurrentPage, 36f, delayMs)
    val (iconScale, iconAlpha) = rememberSpringEntry(isCurrentPage, 0.6f, delayMs)
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.graphicsLayer { translationY = offsetY; this.alpha = rowAlpha },
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale; this.alpha = iconAlpha },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    feature.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column {
            Text(feature.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(feature.desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 第2页：作息时间设置（错落进场） ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WelcomePage2(
    uiState: WelcomeUiState,
    isCurrentPage: Boolean,
    onTimeChange: (String, Int, Int) -> Unit,
) {
    val (titleY, titleAlpha) = rememberSlideEntry(isCurrentPage, 20f, 0L)
    val (subY,   subAlpha)   = rememberSlideEntry(isCurrentPage, 20f, 80L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            "设置作息时间",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { translationY = titleY; alpha = titleAlpha },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "MedLog 将根据这些时间自动推算服药提醒，可随时在设置中修改",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { translationY = subY; alpha = subAlpha },
        )
        Spacer(Modifier.height(24.dp))

        listOf(
            Triple("wake",      "起床时间", Icons.Rounded.WbSunny),
            Triple("breakfast", "早餐时间", Icons.Rounded.BreakfastDining),
            Triple("lunch",     "午餐时间", Icons.Rounded.LunchDining),
            Triple("dinner",    "晚餐时间", Icons.Rounded.DinnerDining),
            Triple("bed",       "就寝时间", Icons.Rounded.Bedtime),
        ).forEachIndexed { index, (field, label, icon) ->
            val delay = 120L + index * 60L
            val (cardY, cardAlpha) = rememberSlideEntry(isCurrentPage, 24f, delay)
            Box(modifier = Modifier.graphicsLayer { translationY = cardY; alpha = cardAlpha }) {
                val (h, m) = when (field) {
                    "wake"      -> uiState.wakeHour      to uiState.wakeMinute
                    "breakfast" -> uiState.breakfastHour to uiState.breakfastMinute
                    "lunch"     -> uiState.lunchHour     to uiState.lunchMinute
                    "dinner"    -> uiState.dinnerHour    to uiState.dinnerMinute
                    else        -> uiState.bedHour       to uiState.bedMinute
                }
                RoutineTimeField(label, icon, h, m) { nh, nm -> onTimeChange(field, nh, nm) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineTimeField(
    label: String,
    icon: ImageVector,
    hour: Int,
    minute: Int,
    onChanged: (Int, Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(0.dp),
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                "%02d:%02d".format(hour, minute),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(Icons.Rounded.Edit, contentDescription = "修改", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text  = { TimeInput(state = timePickerState) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    onChanged(timePickerState.hour, timePickerState.minute)
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        )
    }
}

// ── 第4页：通知权限 ──────────────────────────────────────────────────────────

@Composable
private fun WelcomeNotificationPage(
    isCurrentPage: Boolean,
    notifGranted: Boolean,
    onRequestPermission: () -> Unit,
) {
    val (iconScale, iconAlpha) = rememberSpringEntry(isCurrentPage, 0.3f, 0L)
    val (titleY, titleAlpha)   = rememberSlideEntry(isCurrentPage, 24f, 150L)
    val (subY,   subAlpha)     = rememberSlideEntry(isCurrentPage, 24f, 240L)
    val (btnY,   btnAlpha)     = rememberSlideEntry(isCurrentPage, 24f, 320L)

    // 未授权时按钮脐冲动画，吸引老年用户注意
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(isCurrentPage, notifGranted) {
        if (isCurrentPage && !notifGranted) {
            while (true) {
                pulseScale.animateTo(1.06f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
                pulseScale.animateTo(1.0f,  spring(Spring.DampingRatioNoBouncy,     Spring.StiffnessMediumLow))
                delay(1200)
            }
        } else {
            pulseScale.snapTo(1f)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (notifGranted) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale; alpha = iconAlpha },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (notifGranted) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = if (notifGranted) MaterialTheme.colorScheme.onTertiaryContainer
                           else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            if (notifGranted) "提醒已就绪 ✓" else "开启服药提醒",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationY = titleY; alpha = titleAlpha },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (notifGranted)
                "MedLog 将在您设定的服药时间准时推送提醒，确保不会错过任何一次用药。"
            else
                "MedLog 需要通知权限，才能在服药时间准时提醒您。\n建议开启以确保不会错过用药计划。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationY = subY; alpha = subAlpha },
        )
        if (!notifGranted) {
            Spacer(Modifier.height(28.dp))
            // 引导文字：明显的操作指引，尤其适合老年用户
            Text(
                "请点击下方按鈕，允许 MedLog 发送服药提醒",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = btnAlpha },
            )
            Spacer(Modifier.height(12.dp))
            // 升级为充填按鈕 + 脐冲缩放动画
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .graphicsLayer {
                        scaleX = pulseScale.value
                        scaleY = pulseScale.value
                        translationY = btnY
                        alpha = btnAlpha
                    },
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("立即开启服药提醒", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "也可稍后在「系统通知设置」或 MedLog「设置」中开启",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = btnAlpha },
            )
        }
    }
}

// ── 第5页：准备完毕 + 快速开始 ──────────────────────────────────────────────

private data class QuickStartStep(val icon: ImageVector, val text: String)

@Composable
private fun WelcomePage3(isCurrentPage: Boolean) {
    val (iconScale, iconAlpha) = rememberSpringEntry(isCurrentPage, 0.2f, 0L)
    val (titleY, titleAlpha)   = rememberSlideEntry(isCurrentPage, 24f, 180L)
    val (subY,   subAlpha)     = rememberSlideEntry(isCurrentPage, 24f, 260L)

    val steps = remember {
        listOf(
            QuickStartStep(Icons.Rounded.Add,               "点击首页 ＋ 按钮，添加您的第一个药品"),
            QuickStartStep(Icons.Rounded.AccessTime,        "设置服药频次和时间，开启准时提醒"),
            QuickStartStep(Icons.Rounded.CheckCircle,       "每次收到提醒后，打开应用完成服药打卡"),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
            .padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale; alpha = iconAlpha },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "一切就绪！",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationY = titleY; alpha = titleAlpha },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "让 MedLog 助您规律用药、守护健康",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationY = subY; alpha = subAlpha },
        )
        Spacer(Modifier.height(32.dp))
        // 快速开始步骤
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = subY; alpha = subAlpha },
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                steps.forEachIndexed { index, step ->
                    val (rowY, rowAlpha) = rememberSlideEntry(isCurrentPage, 20f, 300L + index * 80L)
                    ListItem(
                        headlineContent = {
                            Text(step.text, style = MaterialTheme.typography.bodyMedium)
                        },
                        leadingContent = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        step.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        modifier = Modifier.graphicsLayer { translationY = rowY; alpha = rowAlpha },
                    )
                    if (index < steps.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

// ── 第4页：功能选择 ────────────────────────────────────────────────────────────

@Composable
private fun WelcomePage4(
    uiState: WelcomeUiState,
    isCurrentPage: Boolean,
    onToggleSymptomDiary: (Boolean) -> Unit,
    onToggleDrugInteractionCheck: (Boolean) -> Unit,
    onToggleDrugDatabase: (Boolean) -> Unit,
    onToggleHealthModule: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val (titleY, titleAlpha) = rememberSlideEntry(isCurrentPage, 20f, 0L)
    val (subY, subAlpha)     = rememberSlideEntry(isCurrentPage, 20f, 80L)
    val (cardY, cardAlpha)   = rememberSlideEntry(isCurrentPage, 24f, 160L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "个性化功能",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { translationY = titleY; alpha = titleAlpha },
        )
        Text(
            "以下功能可选择启用，未启用的模块不会出现在导航栏。您也可以随时在「设置」中更改。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { translationY = subY; alpha = subAlpha },
        )

        // 功能选项卡片
        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = cardY; alpha = cardAlpha },
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                FeatureToggleRow(
                    title = "症状日记",
                    description = "记录每日症状、心情与身体状况",
                    icon = Icons.Rounded.EditNote,
                    checked = uiState.enableSymptomDiary,
                    onCheckedChange = onToggleSymptomDiary,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FeatureToggleRow(
                    title = "药品数据库",
                    description = "浏览内置西药 / 中成药查询与添加",
                    icon = Icons.Rounded.MedicalServices,
                    checked = uiState.enableDrugDatabase,
                    onCheckedChange = onToggleDrugDatabase,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FeatureToggleRow(
                    title = "药物相互作用检测",
                    description = "自动检测多药联用的配伍风险",
                    icon = Icons.Rounded.Warning,
                    checked = uiState.enableDrugInteractionCheck,
                    onCheckedChange = onToggleDrugInteractionCheck,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FeatureToggleRow(
                    title = "健康体征模块",
                    description = "记录血压、血糖、体重等健康数据",
                    icon = Icons.Rounded.MonitorHeart,
                    checked = uiState.enableHealthModule,
                    onCheckedChange = onToggleHealthModule,
                )
            }
        }

        Text(
            "⚡ 推荐：所有功能保持开启，随时可以在设置中关闭。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        // 外观主题选择
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = cardY; alpha = cardAlpha },
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text("外观主题", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        ThemeMode.SYSTEM to "跟随系统",
                        ThemeMode.LIGHT  to "浅色",
                        ThemeMode.DARK   to "深色",
                    ).forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = uiState.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        ) { Text(label, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureToggleRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(description, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    )
}
