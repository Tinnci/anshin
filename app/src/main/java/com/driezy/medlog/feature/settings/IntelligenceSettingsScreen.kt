package com.driezy.medlog.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun IntelligenceSettingsScreen(
    onBack: () -> Unit,
    onNavigateToCloudApiSettings: () -> Unit,
    viewModel: SettingsIntelligenceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScaffold(
        mode = SettingsScreenMode.INTELLIGENCE,
        onBack = onBack,
        onNavigateToCloudApiSettings = onNavigateToCloudApiSettings,
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}
