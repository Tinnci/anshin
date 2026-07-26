package com.driezy.medlog.ui.screen.welcome

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.ui.components.MedLogScreenScaffold
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WelcomeScreen(onFinished: () -> Unit, viewModel: WelcomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) onFinished()
    }

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
    val pagerState = rememberPagerState(pageCount = pages::size)
    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = motionScheme.defaultSpatialSpec(),
    )

    MedLogScreenScaffold(title = {}, showTopBar = false) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HorizontalPager(
                state = pagerState,
                flingBehavior = flingBehavior,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                val isCurrent = pagerState.settledPage == page
                when (pages[page]) {
                    "welcome" -> WelcomePage0(isCurrentPage = isCurrent)
                    "benefits" -> WelcomePage1(isCurrentPage = isCurrent)
                    "preferences" -> WelcomePage4(
                        uiState = uiState,
                        isCurrentPage = isCurrent,
                        onToggleSymptomDiary = viewModel::onToggleSymptomDiary,
                        onToggleDrugInteractionCheck = viewModel::onToggleDrugInteractionCheck,
                        onToggleDrugDatabase = viewModel::onToggleDrugDatabase,
                        onToggleHealthModule = viewModel::onToggleHealthModule,
                        onToggleTimePeriodMode = viewModel::onToggleTimePeriodMode,
                        onThemeModeChange = viewModel::onThemeModeChange,
                    )
                    "routine" -> WelcomePage2(
                        uiState = uiState,
                        isCurrentPage = isCurrent,
                        onTimeChange = viewModel::onTimeChange,
                    )
                    "notifications" -> WelcomeNotificationPage(
                        isCurrentPage = isCurrent,
                        notifGranted = notificationGranted,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
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
                onSkip = viewModel::finishWelcome,
                onNext = {
                    if (pagerState.currentPage == pages.lastIndex) {
                        viewModel.finishWelcome()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
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
                .padding(horizontal = if (profile.constrained) 16.dp else 24.dp)
                .padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                Text(
                    stringResource(R.string.welcome_progress, currentPage + 1, pageCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
                if (!isLastPage) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.welcome_btn_skip))
                    }
                }
            }
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    if (isLastPage) {
                        stringResource(R.string.welcome_btn_start)
                    } else {
                        stringResource(R.string.welcome_btn_next)
                    },
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
        }
    }
}
