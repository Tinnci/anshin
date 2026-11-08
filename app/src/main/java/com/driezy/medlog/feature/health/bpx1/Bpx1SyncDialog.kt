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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.capability.bpx1.Bpx1Measurement
import com.driezy.medlog.capability.bpx1.application.Bpx1SyncFailure
import com.driezy.medlog.capability.bpx1.missingBpx1Permissions
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.emphasizedTypography

@Composable
internal fun Bpx1SyncDialog(
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    onOpenPairing: () -> Unit,
    viewModel: Bpx1SyncViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Scanning needs runtime Bluetooth permission. Only ask for it once a BPX1
    // monitor is configured so unconfigured users go straight to the pairing prompt.
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
        if (!viewModel.isConfigured()) {
            viewModel.startSync()
            return
        }
        val missing = missingBpx1Permissions(context)
        if (missing.isEmpty()) viewModel.startSync() else permissionLauncher.launch(missing.toTypedArray())
    }

    LaunchedEffect(Unit) {
        requestPermissionsOrSync()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopSync() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(MedLogSpacing.Large)
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = MedLogSpacing.XLarge, top = MedLogSpacing.Small, end = MedLogSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.bpx1_sync_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(onClick = onOpenPairing) {
                        MedLogIcon(
                            MedLogIcons.Settings,
                            contentDescription = stringResource(R.string.bpx1_settings_title),
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        MedLogIcon(MedLogIcons.Close, contentDescription = stringResource(R.string.home_close))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MedLogSpacing.XLarge, vertical = MedLogSpacing.Large),
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
                            Bpx1SyncScanning(uiState = uiState, onCancel = onDone)
                        }
                        else -> {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                strokeCap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bpx1SyncStatusContent(
    icon: Int,
    title: String,
    body: String,
    isError: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                MedLogIcon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.emphasizedTypography.titleLarge,
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
    Bpx1SyncStatusContent(
        icon = MedLogIcons.MonitorHeartDisplay48,
        title = stringResource(R.string.bpx1_sync_needs_config_title),
        body = stringResource(R.string.bpx1_sync_needs_config_body),
    ) {
        Spacer(Modifier.height(MedLogSpacing.Tiny))
        Button(
            onClick = onOpenPairing,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            MedLogIcon(MedLogIcons.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(MedLogSpacing.Small))
            Text(stringResource(R.string.bpx1_sync_open_pairing))
        }
        OutlinedButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Text(stringResource(R.string.bpx1_sync_cancel))
        }
    }
}

@Composable
private fun Bpx1SyncScanning(uiState: Bpx1SyncUiState, onCancel: () -> Unit) {
    val measurement = uiState.lastImportedMeasurement
    Bpx1SyncStatusContent(
        icon = MedLogIcons.MonitorHeartDisplay48,
        title = stringResource(
            if (uiState.deviceDetected) R.string.bpx1_sync_detected_title else R.string.bpx1_sync_scanning_title,
        ),
        body = stringResource(R.string.bpx1_sync_scanning_body),
    ) {
        Surface(
            shape = CircleShape,
            color = if (uiState.deviceDetected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = MedLogSpacing.Medium, vertical = MedLogSpacing.Tiny),
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MedLogIcon(
                    if (uiState.deviceDetected) MedLogIcons.CheckCircle else MedLogIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (uiState.deviceDetected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                val total = uiState.scanDurationSeconds
                if (total > 0) {
                    Text(
                        stringResource(R.string.bpx1_sync_remaining, uiState.remainingSeconds),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.deviceDetected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        val total = uiState.scanDurationSeconds
        if (total > 0) {
            LinearProgressIndicator(
                progress = { uiState.remainingSeconds.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                strokeCap = StrokeCap.Round,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(8.dp),
                strokeCap = StrokeCap.Round,
            )
        }

        if (measurement != null) {
            Bpx1MeasurementPreviewCard(
                measurement = measurement,
                importedCount = uiState.importedCount,
            )
        }

        if (uiState.importedCount > 0) {
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                MedLogIcon(MedLogIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(MedLogSpacing.Small))
                Text(stringResource(R.string.bpx1_sync_done))
            }
        } else {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.large,
            ) {
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
    val (icon, title, body) = when {
        importedCount > 0 -> Triple(
            MedLogIcons.CheckCircleDisplay48,
            stringResource(R.string.bpx1_sync_finished_title),
            pluralStringResource(R.plurals.bpx1_sync_finished_body, importedCount, importedCount),
        )
        deviceDetected -> Triple(
            MedLogIcons.Info,
            stringResource(R.string.bpx1_sync_no_new_title),
            stringResource(R.string.bpx1_sync_no_new_body),
        )
        else -> Triple(
            MedLogIcons.SearchOffDisplay48,
            stringResource(R.string.bpx1_sync_not_found_title),
            stringResource(R.string.bpx1_sync_not_found_body),
        )
    }
    Bpx1SyncStatusContent(
        icon = icon,
        title = title,
        body = body,
    ) {
        if (measurement != null) {
            Bpx1MeasurementPreviewCard(
                measurement = measurement,
                importedCount = importedCount,
            )
        }
        if (importedCount > 0) {
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(stringResource(R.string.bpx1_sync_done))
            }
        } else {
            Button(
                onClick = onKeepListening,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                MedLogIcon(MedLogIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(MedLogSpacing.Small))
                Text(stringResource(R.string.bpx1_sync_keep_listening))
            }
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(stringResource(R.string.bpx1_sync_done))
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
    Bpx1SyncStatusContent(
        icon = MedLogIcons.Warning,
        title = stringResource(title),
        body = stringResource(body),
        isError = true,
    ) {
        when (failure) {
            Bpx1SyncFailure.BLUETOOTH_DISABLED -> Button(
                onClick = onEnableBluetooth,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(stringResource(R.string.bpx1_sync_enable_bluetooth))
            }
            Bpx1SyncFailure.BLUETOOTH_UNSUPPORTED -> Unit
            Bpx1SyncFailure.PERMISSION_DENIED -> {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(stringResource(R.string.bpx1_sync_grant_permission))
                }
                FilledTonalButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(stringResource(R.string.bpx1_sync_open_app_settings))
                }
            }
            Bpx1SyncFailure.SCAN_FAILED -> Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                MedLogIcon(MedLogIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(MedLogSpacing.Small))
                Text(stringResource(R.string.bpx1_sync_retry))
            }
        }
        OutlinedButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Text(stringResource(R.string.bpx1_sync_cancel))
        }
    }
}

@Composable
private fun Bpx1MeasurementPreviewCard(measurement: Bpx1Measurement, importedCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline),
            modifier = Modifier.padding(MedLogSpacing.Medium),
        ) {
            Text(
                stringResource(
                    R.string.bpx1_measurement_value,
                    measurement.systolic,
                    measurement.diastolic,
                    measurement.heartRate,
                ),
                style = MaterialTheme.emphasizedTypography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (measurement.irregularHeartRhythm) {
                Text(
                    stringResource(R.string.bpx1_flag_irregular),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (measurement.movementDetected) {
                Text(
                    stringResource(R.string.bpx1_flag_movement),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (importedCount > 0) {
                Text(
                    pluralStringResource(R.plurals.bpx1_sync_imported_count, importedCount, importedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}
