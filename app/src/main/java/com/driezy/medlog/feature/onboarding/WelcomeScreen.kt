package com.driezy.medlog.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.ui.components.MedLogScreenScaffold
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WelcomeScreen(onFinished: () -> Unit, viewModel: WelcomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationGranted = granted }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                WelcomeUiEffect.Finished -> onFinished()
            }
        }
    }
    WelcomeContent(
        uiState = uiState,
        notificationGranted = notificationGranted,
        onRequestNotificationPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WelcomeContent(
    uiState: WelcomeUiState,
    notificationGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onAction: (WelcomeUiAction) -> Unit,
) {
    val pages = remember(uiState.enableTimePeriodMode) {
        buildList {
            add("welcome")
            add("benefits")
            add("preferences")
            if (uiState.enableTimePeriodMode) add("routine")
            add("notifications")
            add("ready")
        }
    }
    val pagerState = rememberPagerState(
        initialPage = uiState.pageIndex.coerceIn(0, pages.lastIndex),
        pageCount = pages::size,
    )
    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = motionScheme.defaultSpatialSpec(),
    )
    LaunchedEffect(pagerState, pages.size) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { onAction(WelcomeUiAction.PageChanged(it)) }
    }

    MedLogScreenScaffold(title = {}, showTopBar = false) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val horizontalInsets = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
        )
        // The action bar draws behind the side and bottom insets and pads its own content.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            HorizontalPager(
                state = pagerState,
                flingBehavior = flingBehavior,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontalInsets),
            ) { page ->
                val isCurrent = pagerState.settledPage == page
                when (pages[page]) {
                    "welcome" -> WelcomePage0(isCurrentPage = isCurrent)
                    "benefits" -> WelcomePage1(isCurrentPage = isCurrent)
                    "preferences" -> WelcomePage4(
                        uiState = uiState,
                        isCurrentPage = isCurrent,
                        onToggleSymptomDiary = { onAction(WelcomeUiAction.SymptomDiaryChanged(it)) },
                        onToggleDrugInteractionCheck = { onAction(WelcomeUiAction.DrugInteractionChanged(it)) },
                        onToggleDrugDatabase = { onAction(WelcomeUiAction.DrugDatabaseChanged(it)) },
                        onToggleHealthModule = { onAction(WelcomeUiAction.HealthModuleChanged(it)) },
                        onToggleTimePeriodMode = { onAction(WelcomeUiAction.TimePeriodModeChanged(it)) },
                        onThemeModeChange = { onAction(WelcomeUiAction.ThemeModeChanged(it)) },
                    )
                    "routine" -> WelcomePage2(
                        uiState = uiState,
                        isCurrentPage = isCurrent,
                        onTimeChange = { slot, time -> onAction(WelcomeUiAction.TimeChanged(slot, time)) },
                    )
                    "notifications" -> WelcomeNotificationPage(
                        isCurrentPage = isCurrent,
                        notifGranted = notificationGranted,
                        onRequestPermission = onRequestNotificationPermission,
                    )
                    "ready" -> WelcomePage3(isCurrentPage = isCurrent)
                }
            }

            WelcomeNavigationBar(
                currentPage = pagerState.currentPage,
                pageCount = pages.size,
                onBack = {
                    scope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                    }
                },
                onSkip = { onAction(WelcomeUiAction.Submit) },
                onNext = {
                    if (pagerState.currentPage == pages.lastIndex) {
                        onAction(WelcomeUiAction.Submit)
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                isSaving = uiState.isSaving,
                errorMessage = uiState.errorMessage,
                emphasizeNext = pages[pagerState.currentPage] != "notifications" || notificationGranted,
                horizontalInsets = horizontalInsets,
            )
        }
    }
}

@Composable
private fun WelcomeNavigationBar(
    currentPage: Int,
    pageCount: Int,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    isSaving: Boolean,
    errorMessage: String?,
    emphasizeNext: Boolean,
    horizontalInsets: PaddingValues,
) {
    val profile = rememberWelcomeLayoutProfile()
    val isLastPage = currentPage == pageCount - 1
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontalInsets)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = if (profile.inlineActions) WelcomeMaxInlineActionsWidth else WelcomeMaxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = if (profile.constrained) 16.dp else 24.dp)
                .padding(top = 8.dp, bottom = if (profile.inlineActions) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (profile.inlineActions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WelcomeProgressRow(
                        currentPage = currentPage,
                        pageCount = pageCount,
                        showStepCount = profile.showStepCount,
                        onBack = onBack,
                        onSkip = onSkip,
                        modifier = Modifier.weight(1f),
                    )
                    WelcomePrimaryAction(
                        isLastPage = isLastPage,
                        emphasized = emphasizeNext,
                        enabled = !isSaving,
                        onClick = onNext,
                        modifier = Modifier.widthIn(min = 160.dp),
                    )
                }
            } else {
                WelcomeProgressRow(
                    currentPage = currentPage,
                    pageCount = pageCount,
                    showStepCount = profile.showStepCount,
                    onBack = onBack,
                    onSkip = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                )
                WelcomePrimaryAction(
                    isLastPage = isLastPage,
                    emphasized = emphasizeNext,
                    enabled = !isSaving,
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WelcomeProgressRow(
    currentPage: Int,
    pageCount: Int,
    showStepCount: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            enabled = currentPage > 0,
            modifier = Modifier.size(40.dp),
        ) {
            MedLogIcon(
                MedLogIcons.ArrowBack,
                contentDescription = stringResource(R.string.common_back_cd),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { (currentPage + 1f) / pageCount },
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
        )
        Spacer(Modifier.width(10.dp))
        if (showStepCount) {
            Text(
                stringResource(R.string.welcome_progress, currentPage + 1, pageCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
        if (currentPage < pageCount - 1) {
            TextButton(onClick = onSkip) {
                Text(
                    stringResource(R.string.welcome_btn_skip),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun WelcomePrimaryAction(
    isLastPage: Boolean,
    emphasized: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable RowScope.() -> Unit = {
        Text(
            if (isLastPage) {
                stringResource(R.string.welcome_btn_start)
            } else {
                stringResource(R.string.welcome_btn_next)
            },
            textAlign = TextAlign.Center,
        )
        if (isLastPage) {
            Spacer(Modifier.width(8.dp))
            MedLogIcon(
                MedLogIcons.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    val sizedModifier = modifier.heightIn(min = 52.dp)
    if (emphasized) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = sizedModifier,
            shape = MaterialTheme.shapes.large,
            content = content,
        )
    } else {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = sizedModifier,
            shape = MaterialTheme.shapes.large,
            content = content,
        )
    }
}
