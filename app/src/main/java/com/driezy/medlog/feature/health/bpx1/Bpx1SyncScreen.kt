package com.driezy.medlog.feature.health.bpx1

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bpx1SyncScreen(onDone: () -> Unit, onOpenPairing: () -> Unit, viewModel: Bpx1SyncViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startSync()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopSync() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bpx1_sync_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        MedLogIcon(
                            MedLogIcons.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = MedLogSpacing.Large),
            contentAlignment = Alignment.Center,
        ) {
            when {
                uiState.isNeedsConfiguration -> {
                    Bpx1SyncNeedsConfiguration(onOpenPairing = onOpenPairing, onDone = onDone)
                }
                uiState.failure != null -> {
                    Bpx1SyncFailed(onRetry = viewModel::startSync, onDone = onDone)
                }
                uiState.isFinished -> {
                    Bpx1SyncFinished(
                        importedCount = uiState.importedCount,
                        measurement = uiState.lastImportedMeasurement,
                        onDone = onDone,
                    )
                }
                else -> {
                    Bpx1SyncScanning(
                        importedCount = uiState.importedCount,
                        measurement = uiState.lastImportedMeasurement,
                        onCancel = onDone,
                    )
                }
            }
        }
    }
}

@Composable
private fun Bpx1SyncNeedsConfiguration(onOpenPairing: () -> Unit, onDone: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
    ) {
        MedLogIcon(
            MedLogIcons.MonitorHeart,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.bpx1_sync_needs_config_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.bpx1_sync_needs_config_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDone) {
                Text(stringResource(R.string.bpx1_sync_cancel))
            }
            Button(onClick = onOpenPairing) {
                Text(stringResource(R.string.bpx1_sync_open_pairing))
            }
        }
    }
}

@Composable
private fun Bpx1SyncScanning(
    importedCount: Int,
    measurement: com.driezy.medlog.capability.bpx1.Bpx1Measurement?,
    onCancel: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        modifier = Modifier.fillMaxWidth(),
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.bpx1_sync_scanning_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.bpx1_sync_scanning_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (measurement != null) {
            Text(
                stringResource(
                    R.string.bpx1_measurement_value,
                    measurement.systolic,
                    measurement.diastolic,
                    measurement.heartRate,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (importedCount > 0) {
            Text(
                stringResource(R.string.bpx1_sync_imported_count, importedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.bpx1_sync_cancel))
        }
    }
}

@Composable
private fun Bpx1SyncFinished(
    importedCount: Int,
    measurement: com.driezy.medlog.capability.bpx1.Bpx1Measurement?,
    onDone: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
    ) {
        MedLogIcon(
            MedLogIcons.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.bpx1_sync_finished_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (measurement != null) {
            Text(
                stringResource(
                    R.string.bpx1_measurement_value,
                    measurement.systolic,
                    measurement.diastolic,
                    measurement.heartRate,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            if (importedCount > 0) {
                stringResource(R.string.bpx1_sync_finished_body, importedCount)
            } else {
                stringResource(R.string.bpx1_sync_finished_empty)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onDone) {
            Text(stringResource(R.string.bpx1_sync_done))
        }
    }
}

@Composable
private fun Bpx1SyncFailed(onRetry: () -> Unit, onDone: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
    ) {
        MedLogIcon(
            MedLogIcons.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            stringResource(R.string.bpx1_sync_failed_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.bpx1_sync_failed_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDone) {
                Text(stringResource(R.string.bpx1_sync_cancel))
            }
            FilledTonalButton(onClick = onRetry) {
                Text(stringResource(R.string.bpx1_sync_retry))
            }
        }
    }
}
