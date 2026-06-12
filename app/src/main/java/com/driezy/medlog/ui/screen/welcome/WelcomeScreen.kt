package com.driezy.medlog.ui.screen.welcome

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.ThemeMode
import androidx.compose.ui.res.stringResource
import com.driezy.medlog.ui.components.MedLogScreenScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    // 页面列表：根据 enableTimePeriodMode 动态决定是否包含作息时间页
    val pageList = remember(uiState.enableTimePeriodMode) {
        buildList {
            add("splash")
            add("intro")
            add("features")       // 个性化功能优先显示
            if (uiState.enableTimePeriodMode) add("timePeriod")  // 关闭时跳过
            add("notification")
            add("final")
        }
    }
    val pagerState = rememberPagerState(pageCount = { pageList.size })
    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme

    // Pager snap uses the product-level Material motion scheme.
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = motionScheme.defaultSpatialSpec(),
    )

    MedLogScreenScaffold(
        title = {},
        showTopBar = false,
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
                when (pageList[page]) {
                    "splash" -> WelcomePage0(isCurrentPage = isCurrentPage)
                    "intro"  -> WelcomePage1(isCurrentPage = isCurrentPage)
                    "features" -> WelcomePage4(
                        uiState       = uiState,
                        isCurrentPage = isCurrentPage,
                        onToggleSymptomDiary         = viewModel::onToggleSymptomDiary,
                        onToggleDrugInteractionCheck = viewModel::onToggleDrugInteractionCheck,
                        onToggleDrugDatabase         = viewModel::onToggleDrugDatabase,
                        onToggleHealthModule         = viewModel::onToggleHealthModule,
                        onToggleTimePeriodMode       = viewModel::onToggleTimePeriodMode,
                        onThemeModeChange            = viewModel::onThemeModeChange,
                    )
                    "timePeriod" -> WelcomePage2(
                        uiState       = uiState,
                        isCurrentPage = isCurrentPage,
                        onTimeChange  = viewModel::onTimeChange,
                    )
                    "notification" -> WelcomeNotificationPage(
                        isCurrentPage = isCurrentPage,
                        notifGranted  = notifGranted,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                    "final" -> WelcomePage3(isCurrentPage = isCurrentPage)
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
                    repeat(pageList.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue   = if (isSelected) 24.dp else 8.dp,
                            animationSpec = motionScheme.fastSpatialSpec(),
                            label         = "dotWidth",
                        )
                        val dotColor by animateColorAsState(
                            targetValue   = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant,
                            animationSpec = motionScheme.fastEffectsSpec(),
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
                val isLastPage = pagerState.currentPage == pageList.size - 1
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
                        if (isLastPage) stringResource(R.string.welcome_btn_start) else stringResource(R.string.welcome_btn_next),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (isLastPage) {
                        Spacer(Modifier.width(8.dp))
                        MedLogIcon(MedLogIcons.ArrowForward, contentDescription = null, Modifier.size(18.dp))
                    }
                }

                // 跳过（最后一页隐藏）
                AnimatedVisibility(
                    visible = !isLastPage,
                    enter = fadeIn(motionScheme.fastEffectsSpec()),
                    exit = fadeOut(motionScheme.fastEffectsSpec()),
                ) {
                    TextButton(
                        onClick = { viewModel.finishWelcome() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.welcome_btn_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
