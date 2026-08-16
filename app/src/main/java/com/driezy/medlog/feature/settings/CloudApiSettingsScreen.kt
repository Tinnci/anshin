package com.driezy.medlog.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CloudApiSettingsScreen(onBack: () -> Unit, viewModel: SettingsCloudApiViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScaffold(
        mode = SettingsScreenMode.CLOUD_API,
        onBack = onBack,
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}
