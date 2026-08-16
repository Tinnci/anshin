package com.driezy.medlog.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    onNavigateToWelcome: () -> Unit = {},
    viewModel: SettingsDataViewModel = hiltViewModel(),
) {
    val inProgress by viewModel.inProgress.collectAsStateWithLifecycle()
    SettingsScaffold(
        mode = SettingsScreenMode.DATA,
        onBack = onBack,
        onNavigateToWelcome = onNavigateToWelcome,
        uiState = SettingsUiState(),
        onAction = viewModel::onAction,
        dataInProgress = inProgress,
        dataEffects = viewModel.effects,
    )
}
