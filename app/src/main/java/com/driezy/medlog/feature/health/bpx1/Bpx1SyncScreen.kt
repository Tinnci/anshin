package com.driezy.medlog.feature.health.bpx1

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.capability.bpx1.Bpx1Measurement
import com.driezy.medlog.capability.bpx1.application.Bpx1SyncFailure
import com.driezy.medlog.capability.bpx1.missingBpx1Permissions
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bpx1SyncScreen(onDone: () -> Unit, onOpenPairing: () -> Unit, viewModel: Bpx1SyncViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Scanning needs runtime Bluetooth permission. Ask for it here, at the moment
    // it is needed, instead of letting the scan fail with an unexplained error.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (missingBpx1Permissions(context).isEmpty()) viewModel.startSync() else viewModel.onPermissionDenied()
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.startSync()
    }

    fun requestPermissionsOrSync() {
        val missing = missingBpx1Permissions(context)
        if (missing.isEmpty()) viewModel.startSync() else permissionLauncher.launch(missing.toTypedArray())
    }

    LaunchedEffect(Unit) {
        requestPermissionsOrSync()
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Large),
            contentAlignment = Alignment.Center,
        ) {
            val failure = uiState.failure
            when {
                uiState.isNeedsConfiguration -> {
                    Bpx1SyncNeedsConfiguration(onOpenPairing = onOpenPairing, onDone = onDone)
                }
                failure != null -> {
                    Bpx1SyncFailed(
                        failure = failure,
                        onRetry = ::requestPermissionsOrSync,
                        onEnableBluetooth = {
                            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        },
                        onOpenAppSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        },
                        onDone = onDone,
                    )
                }
                uiState.isFinished -> {
                    Bpx1SyncFinished(
                        importedCount = uiState.importedCount,
                        deviceDetected = uiState.deviceDetected,
                        measurement = uiState.lastImportedMeasurement,
                        onKeepListening = ::requestPermissionsOrSync,
                        onDone = onDone,
                    )
                }
                uiState.isScanning -> {
                    Bpx1SyncScanning(
                        uiState = uiState,
                        onCancel = onDone,
                    )
                }
                else -> {
                    // Waiting for the permission result before the scan starts.
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Bpx1SyncStatus(
    icon: Int,
    title: String,
    body: String,
    isError: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        modifier = Modifier.fillMaxWidth(),
    ) {
        MedLogIcon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        content()
    }
}

@Composable
private fun Bpx1SyncNeedsConfiguration(onOpenPairing: () -> Unit, onDone: () -> Unit) {
    Bpx1SyncStatus(
        icon = MedLogIcons.MonitorHeart,
        title = stringResource(R.string.bpx1_sync_needs_config_title),
        body = stringResource(R.string.bpx1_sync_needs_config_body),
    ) {
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
private fun Bpx1SyncScanning(uiState: Bpx1SyncUiState, onCancel: () -> Unit) {
    val measurement = uiState.lastImportedMeasurement
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        modifier = Modifier.fillMaxWidth(),
    ) {
        MedLogIcon(
            MedLogIcons.MonitorHeart,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(
                if (uiState.deviceDetected) R.string.bpx1_sync_detected_title else R.string.bpx1_sync_scanning_title,
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            stringResource(R.string.bpx1_sync_scanning_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val total = uiState.scanDurationSeconds
        if (total > 0) {
            LinearProgressIndicator(
                progress = { uiState.remainingSeconds.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.bpx1_sync_remaining, uiState.remainingSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (measurement != null) {
            Text(
                measurement.label(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (uiState.importedCount > 0) {
            Text(
                stringResource(R.string.bpx1_sync_imported_count, uiState.importedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Something was saved already, so leaving is completing, not cancelling.
            Button(onClick = onCancel) {
                Text(stringResource(R.string.bpx1_sync_done))
            }
        } else {
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.bpx1_sync_cancel))
            }
        }
    }
}

@Composable
private fun Bpx1SyncFinished(
    importedCount: Int,
    deviceDetected: Boolean,
    measurement: Bpx1Measurement?,
    onKeepListening: () -> Unit,
    onDone: () -> Unit,
) {
    val (title, body) = when {
        importedCount > 0 -> stringResource(R.string.bpx1_sync_finished_title) to
            stringResource(R.string.bpx1_sync_finished_body, importedCount)
        deviceDetected -> stringResource(R.string.bpx1_sync_no_new_title) to
            stringResource(R.string.bpx1_sync_no_new_body)
        else -> stringResource(R.string.bpx1_sync_not_found_title) to
            stringResource(R.string.bpx1_sync_not_found_body)
    }
    Bpx1SyncStatus(
        icon = if (importedCount > 0) MedLogIcons.CheckCircle else MedLogIcons.Info,
        title = title,
        body = body,
    ) {
        if (measurement != null) {
            Text(
                measurement.label(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (importedCount > 0) {
            Button(onClick = onDone) {
                Text(stringResource(R.string.bpx1_sync_done))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDone) {
                    Text(stringResource(R.string.bpx1_sync_done))
                }
                FilledTonalButton(onClick = onKeepListening) {
                    Text(stringResource(R.string.bpx1_sync_keep_listening))
                }
            }
        }
    }
}

@Composable
private fun Bpx1SyncFailed(
    failure: Bpx1SyncFailure,
    onRetry: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onDone: () -> Unit,
) {
    val (title, body) = when (failure) {
        Bpx1SyncFailure.BLUETOOTH_DISABLED -> R.string.bpx1_sync_bt_off_title to R.string.bpx1_sync_bt_off_body
        Bpx1SyncFailure.BLUETOOTH_UNSUPPORTED ->
            R.string.bpx1_sync_unsupported_title to R.string.bpx1_sync_unsupported_body
        Bpx1SyncFailure.PERMISSION_DENIED ->
            R.string.bpx1_sync_permission_title to R.string.bpx1_sync_permission_body
        Bpx1SyncFailure.SCAN_FAILED -> R.string.bpx1_sync_failed_title to R.string.bpx1_sync_failed_body
    }
    Bpx1SyncStatus(
        icon = MedLogIcons.Warning,
        title = stringResource(title),
        body = stringResource(body),
        isError = true,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDone) {
                Text(stringResource(R.string.bpx1_sync_cancel))
            }
            when (failure) {
                Bpx1SyncFailure.BLUETOOTH_DISABLED -> FilledTonalButton(onClick = onEnableBluetooth) {
                    Text(stringResource(R.string.bpx1_sync_enable_bluetooth))
                }
                Bpx1SyncFailure.BLUETOOTH_UNSUPPORTED -> Unit
                Bpx1SyncFailure.PERMISSION_DENIED -> {
                    // Retry re-requests; once "Don't ask again" applies, only settings can grant it.
                    FilledTonalButton(onClick = onRetry) {
                        Text(stringResource(R.string.bpx1_sync_grant_permission))
                    }
                }
                Bpx1SyncFailure.SCAN_FAILED -> FilledTonalButton(onClick = onRetry) {
                    Text(stringResource(R.string.bpx1_sync_retry))
                }
            }
        }
        if (failure == Bpx1SyncFailure.PERMISSION_DENIED) {
            OutlinedButton(onClick = onOpenAppSettings) {
                Text(stringResource(R.string.bpx1_sync_open_app_settings))
            }
        }
    }
}

@Composable
private fun Bpx1Measurement.label(): String =
    stringResource(R.string.bpx1_measurement_value, systolic, diastolic, heartRate)
