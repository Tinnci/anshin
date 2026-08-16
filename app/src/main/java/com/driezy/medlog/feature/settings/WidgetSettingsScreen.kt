package com.driezy.medlog.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WidgetSettingsScreen(onBack: () -> Unit, viewModel: SettingsWidgetViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScaffold(
        mode = SettingsScreenMode.WIDGETS,
        onBack = onBack,
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}
