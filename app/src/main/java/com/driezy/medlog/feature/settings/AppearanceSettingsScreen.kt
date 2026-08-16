package com.driezy.medlog.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit, viewModel: SettingsAppearanceViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScaffold(
        mode = SettingsScreenMode.APPEARANCE,
        onBack = onBack,
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}
