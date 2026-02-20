package com.example.medlog.ui.screen.welcome

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

    val pagerState = rememberPagerState(pageCount = { 4 })
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
                    3 -> WelcomePage3(isCurrentPage = isCurrentPage)
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
                    repeat(4) { index ->
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
                val isLastPage = pagerState.currentPage == 3
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

// ── 第3页：准备完毕 ──────────────────────────────────────────────────────────

@Composable
private fun WelcomePage3(isCurrentPage: Boolean) {
    val (iconScale, iconAlpha) = rememberSpringEntry(isCurrentPage, 0.2f, 0L)
    val (titleY, titleAlpha)   = rememberSlideEntry(isCurrentPage, 24f, 180L)
    val (subY,   subAlpha)     = rememberSlideEntry(isCurrentPage, 24f, 280L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale; alpha = iconAlpha },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "一切就绪！",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationY = titleY; alpha = titleAlpha },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "现在开始添加您的第一个药品\n让 MedLog 助您规律用药、守护健康",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationY = subY; alpha = subAlpha },
        )
    }
}
