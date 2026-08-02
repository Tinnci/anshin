@file:Suppress("FunctionName")

package com.driezy.medlog.feature.settings

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.capability.bpx1.Bpx1BluetoothAvailability
import com.driezy.medlog.capability.bpx1.Bpx1ConnectionFailure
import com.driezy.medlog.capability.bpx1.Bpx1ConnectionResult
import com.driezy.medlog.capability.bpx1.Bpx1DiscoveredDevice
import com.driezy.medlog.capability.bpx1.Bpx1Measurement
import com.driezy.medlog.capability.bpx1.Bpx1PayloadStatus
import com.driezy.medlog.capability.bpx1.Bpx1Protocol
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

private enum class Bpx1PendingAction { SCAN, CONNECT }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Bpx1DeviceSettingsScreen(onBack: () -> Unit, viewModel: Bpx1DeviceSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    @Suppress("LocalContextResourcesRead") // resources 在组合时捕获，用于 LaunchedEffect 中的消息文案
    val messageResources = context.resources
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingAction by remember { mutableStateOf<Bpx1PendingAction?>(null) }
    var permissionRefresh by remember { mutableIntStateOf(0) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val requiredPermissions = remember { requiredBpx1Permissions() }
    val missingPermissions = remember(permissionRefresh) {
        requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun runPendingAction(action: Bpx1PendingAction) {
        when (action) {
            Bpx1PendingAction.SCAN -> viewModel.onAction(Bpx1SettingsUiAction.ScanStarted)
            Bpx1PendingAction.CONNECT -> viewModel.onAction(Bpx1SettingsUiAction.ConnectionChecked)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionRefresh += 1
        val granted = requiredPermissions.all { permission ->
            result[permission] == true ||
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        val action = pendingAction
        pendingAction = null
        if (granted && action != null) runPendingAction(action)
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onAction(Bpx1SettingsUiAction.AvailabilityRefreshed)
    }

    fun requestOrRun(action: Bpx1PendingAction) {
        if (missingPermissions.isNotEmpty()) {
            pendingAction = action
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            runPendingAction(action)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefresh += 1
                viewModel.onAction(Bpx1SettingsUiAction.AvailabilityRefreshed)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is Bpx1SettingsUiEffect.Message -> snackbarHostState.showSnackbar(
                    messageResources.getString(effect.value.stringRes()),
                )
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bpx1_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        MedLogIcon(
                            MedLogIcons.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Bpx1DeviceSettingsContent(
            uiState = uiState,
            hasMissingPermissions = missingPermissions.isNotEmpty(),
            modifier = Modifier.padding(innerPadding),
            onRequestPermissions = {
                pendingAction = Bpx1PendingAction.SCAN
                permissionLauncher.launch(missingPermissions.toTypedArray())
            },
            onEnableBluetooth = {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            },
            onScan = {
                if (uiState.isScanning) {
                    viewModel.onAction(Bpx1SettingsUiAction.ScanStopped)
                } else {
                    requestOrRun(Bpx1PendingAction.SCAN)
                }
            },
            onConnect = { requestOrRun(Bpx1PendingAction.CONNECT) },
            onAction = viewModel::onAction,
            onClear = { showClearDialog = true },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { MedLogIcon(MedLogIcons.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.bpx1_clear_config)) },
            text = { Text(stringResource(R.string.bpx1_clear_config_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.onAction(Bpx1SettingsUiAction.ConfigurationCleared)
                    },
                ) {
                    Text(
                        stringResource(R.string.bpx1_clear_config),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun Bpx1DeviceSettingsContent(
    uiState: Bpx1DeviceSettingsUiState,
    hasMissingPermissions: Boolean,
    modifier: Modifier = Modifier,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onScan: () -> Unit,
    onConnect: () -> Unit,
    onAction: (Bpx1SettingsUiAction) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MedLogSpacing.Large)
            .padding(bottom = MedLogSpacing.XXLarge),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
    ) {
        Bpx1OverviewPanel(uiState)

        if (hasMissingPermissions) {
            Bpx1AttentionCard(
                title = stringResource(R.string.bpx1_permission_title),
                body = stringResource(R.string.bpx1_permission_body),
                actionLabel = stringResource(R.string.bpx1_permission_action),
                onAction = onRequestPermissions,
            )
        }
        if (uiState.bluetoothAvailability == Bpx1BluetoothAvailability.DISABLED) {
            Bpx1AttentionCard(
                title = stringResource(R.string.bpx1_bluetooth_disabled),
                body = stringResource(R.string.bpx1_actions_desc),
                actionLabel = stringResource(R.string.bpx1_enable_bluetooth),
                onAction = onEnableBluetooth,
            )
        }

        Bpx1ActionsCard(
            uiState = uiState,
            onScan = onScan,
            onConnect = onConnect,
            onChooseDevice = { onAction(Bpx1SettingsUiAction.DeviceSelected(it)) },
        )
        Bpx1ConfigurationCard(
            uiState = uiState,
            onMacChange = { onAction(Bpx1SettingsUiAction.MacChanged(it)) },
            onBindKeyChange = { onAction(Bpx1SettingsUiAction.BindKeyChanged(it)) },
            onSave = { onAction(Bpx1SettingsUiAction.ConfigurationSaved) },
            onClear = onClear,
        )
        Bpx1ImportCard(
            uiState = uiState,
            onAutoImportChange = { onAction(Bpx1SettingsUiAction.AutoImportChanged(it)) },
        )
        Bpx1ProtocolNote()
    }
}

@Composable
private fun Bpx1OverviewPanel(uiState: Bpx1DeviceSettingsUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(MedLogSpacing.Large),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                MedLogIcon(
                    MedLogIcons.MonitorHeart,
                    contentDescription = null,
                    modifier = Modifier.padding(MedLogSpacing.Small).size(28.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline),
            ) {
                Text(
                    stringResource(R.string.bpx1_overview_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.bpx1_overview_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                )
                Text(
                    stringResource(
                        if (uiState.configuration.isConfigured) {
                            R.string.bpx1_status_configured
                        } else {
                            R.string.bpx1_status_not_configured
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun Bpx1AttentionCard(title: String, body: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MedLogIcon(MedLogIcons.Warning, contentDescription = null)
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(body, style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bpx1ConfigurationCard(
    uiState: Bpx1DeviceSettingsUiState,
    onMacChange: (String) -> Unit,
    onBindKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    var revealBindKey by remember { mutableStateOf(false) }
    SettingsCard(
        title = stringResource(R.string.bpx1_config_title),
        subtitle = stringResource(R.string.bpx1_config_desc),
        icon = MedLogIcons.VerifiedUser,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = MedLogSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            OutlinedTextField(
                value = uiState.macInput,
                onValueChange = onMacChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.bpx1_mac_label)) },
                placeholder = { Text(stringResource(R.string.bpx1_mac_hint)) },
                supportingText = {
                    if (uiState.macInputInvalid) Text(stringResource(R.string.bpx1_mac_invalid))
                },
                isError = uiState.macInputInvalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            OutlinedTextField(
                value = uiState.bindKeyInput,
                onValueChange = onBindKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.bpx1_bind_key_label)) },
                placeholder = { Text(stringResource(R.string.bpx1_bind_key_hint)) },
                supportingText = {
                    Text(
                        stringResource(
                            when {
                                uiState.bindKeyInputInvalid -> R.string.bpx1_bind_key_invalid
                                uiState.canRetainStoredBindKey -> R.string.bpx1_bind_key_saved
                                uiState.configuration.hasBindKey -> R.string.bpx1_bind_key_new_device
                                else -> R.string.bpx1_bind_key_hint
                            },
                        ),
                    )
                },
                isError = uiState.bindKeyInputInvalid,
                singleLine = true,
                visualTransformation = if (revealBindKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { revealBindKey = !revealBindKey }) {
                        MedLogIcon(
                            if (revealBindKey) MedLogIcons.VisibilityOff else MedLogIcons.Visibility,
                            contentDescription = stringResource(
                                if (revealBindKey) {
                                    R.string.bpx1_bind_key_hide
                                } else {
                                    R.string.bpx1_bind_key_show
                                },
                            ),
                        )
                    }
                },
            )
            Text(
                stringResource(R.string.bpx1_bind_key_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            ) {
                Button(onClick = onSave) { Text(stringResource(R.string.bpx1_save_config)) }
                if (uiState.configuration.macAddress.isNotBlank() || uiState.configuration.hasBindKey) {
                    OutlinedButton(
                        onClick = onClear,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.bpx1_clear_config))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bpx1ActionsCard(
    uiState: Bpx1DeviceSettingsUiState,
    onScan: () -> Unit,
    onConnect: () -> Unit,
    onChooseDevice: (Bpx1DiscoveredDevice) -> Unit,
) {
    SettingsCard(
        title = stringResource(R.string.bpx1_actions_title),
        subtitle = stringResource(R.string.bpx1_actions_desc),
        icon = MedLogIcons.Search,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = MedLogSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            Text(
                text = stringResource(
                    when (uiState.bluetoothAvailability) {
                        Bpx1BluetoothAvailability.READY -> R.string.bpx1_bluetooth_ready
                        Bpx1BluetoothAvailability.DISABLED -> R.string.bpx1_bluetooth_disabled
                        Bpx1BluetoothAvailability.UNSUPPORTED -> R.string.bpx1_bluetooth_unsupported
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            ) {
                Button(
                    onClick = onScan,
                    enabled = uiState.isScanning ||
                        (uiState.bluetoothAvailability == Bpx1BluetoothAvailability.READY && !uiState.isConnecting),
                ) {
                    MedLogIcon(
                        if (uiState.isScanning) MedLogIcons.Close else MedLogIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(MedLogSpacing.Tiny))
                    Text(
                        stringResource(
                            if (uiState.isScanning) R.string.bpx1_scan_stop else R.string.bpx1_scan_start,
                        ),
                    )
                }
                OutlinedButton(
                    onClick = onConnect,
                    enabled = uiState.bluetoothAvailability == Bpx1BluetoothAvailability.READY &&
                        !uiState.isBusy &&
                        Bpx1Protocol.isValidMac(uiState.macInput),
                ) {
                    Text(stringResource(R.string.bpx1_connection_check))
                }
            }
            if (uiState.isScanning || uiState.isConnecting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(
                        if (uiState.isConnecting) R.string.bpx1_connecting else R.string.bpx1_scanning,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            uiState.connectionResult?.let { Bpx1ConnectionSummary(it) }
            if (uiState.discoveredDevices.isEmpty() &&
                uiState.hasAttemptedScan &&
                !uiState.isScanning &&
                !uiState.isConnecting
            ) {
                Text(
                    stringResource(R.string.bpx1_no_devices),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (uiState.discoveredDevices.isNotEmpty()) {
                uiState.discoveredDevices.forEachIndexed { index, device ->
                    if (index > 0) HorizontalDivider()
                    Bpx1DeviceRow(
                        device = device,
                        isSelected = Bpx1Protocol.normalizeMac(device.macAddress).equals(
                            Bpx1Protocol.normalizeMac(uiState.macInput),
                            ignoreCase = true,
                        ),
                        onChoose = { onChooseDevice(device) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Bpx1DeviceRow(device: Bpx1DiscoveredDevice, isSelected: Boolean, onChoose: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onChoose,
                role = Role.RadioButton,
            ),
        headlineContent = { Text(device.name) },
        supportingContent = {
            Column {
                Text(device.macAddress)
                Text(stringResource(R.string.bpx1_device_signal, device.rssi, payloadStatusLabel(device.payloadStatus)))
            }
        },
        leadingContent = {
            MedLogIcon(
                if (isSelected) MedLogIcons.CheckCircle else MedLogIcons.MonitorHeart,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        trailingContent = {
            Text(
                stringResource(
                    if (isSelected) R.string.bpx1_device_selected else R.string.bpx1_use_device,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
private fun Bpx1ConnectionSummary(result: Bpx1ConnectionResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = when (result) {
            is Bpx1ConnectionResult.Connected -> MaterialTheme.colorScheme.secondaryContainer
            is Bpx1ConnectionResult.Failed -> MaterialTheme.colorScheme.errorContainer
        },
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline),
        ) {
            when (result) {
                is Bpx1ConnectionResult.Connected -> {
                    Text(
                        stringResource(R.string.bpx1_connection_connected, result.serviceCount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (result.hasStandardBloodPressureService) {
                        Text(
                            stringResource(R.string.bpx1_connection_standard_bp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (result.hasMiBeaconService) {
                        Text(stringResource(R.string.bpx1_connection_miot), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        stringResource(R.string.bpx1_connection_reachability_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                is Bpx1ConnectionResult.Failed -> {
                    Text(
                        stringResource(result.reason.stringRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        stringResource(R.string.bpx1_connection_failure_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun Bpx1ImportCard(uiState: Bpx1DeviceSettingsUiState, onAutoImportChange: (Boolean) -> Unit) {
    SettingsCard(
        title = stringResource(R.string.bpx1_import_title),
        subtitle = stringResource(R.string.bpx1_import_desc),
        icon = MedLogIcons.Upload,
    ) {
        SettingsSwitchRow(
            title = stringResource(R.string.bpx1_auto_import_title),
            subtitle = stringResource(
                if (uiState.configuration.isConfigured) {
                    R.string.bpx1_auto_import_subtitle
                } else {
                    R.string.bpx1_auto_import_requires_config
                },
            ),
            checked = uiState.configuration.isConfigured && uiState.configuration.autoImport,
            onCheckedChange = onAutoImportChange,
            icon = MedLogIcons.MonitorHeart,
            enabled = uiState.configuration.isConfigured,
        )
        Column(
            modifier = Modifier.padding(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline),
        ) {
            val measurement = uiState.lastImportedMeasurement
            if (measurement == null) {
                Text(
                    stringResource(R.string.bpx1_last_measurement_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Bpx1MeasurementSummary(measurement)
                Text(
                    pluralStringResource(
                        R.plurals.bpx1_import_count,
                        uiState.importedMeasurementCount,
                        uiState.importedMeasurementCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Bpx1MeasurementSummary(measurement: Bpx1Measurement) {
    Text(
        stringResource(
            R.string.bpx1_measurement_value,
            measurement.systolic,
            measurement.diastolic,
            measurement.heartRate,
        ),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    if (measurement.irregularHeartRhythm) {
        Text(stringResource(R.string.bpx1_flag_irregular), color = MaterialTheme.colorScheme.error)
    }
    if (measurement.movementDetected) {
        Text(stringResource(R.string.bpx1_flag_movement), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun Bpx1ProtocolNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(MedLogSpacing.Large),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            verticalAlignment = Alignment.Top,
        ) {
            MedLogIcon(MedLogIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline)) {
                Text(
                    stringResource(R.string.bpx1_protocol_note_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.bpx1_protocol_note_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun payloadStatusLabel(status: Bpx1PayloadStatus): String = stringResource(
    when (status) {
        Bpx1PayloadStatus.NONE -> R.string.bpx1_payload_none
        Bpx1PayloadStatus.PLAINTEXT -> R.string.bpx1_payload_plaintext
        Bpx1PayloadStatus.DECRYPTED -> R.string.bpx1_payload_decrypted
        Bpx1PayloadStatus.KEY_REQUIRED -> R.string.bpx1_payload_key_required
        Bpx1PayloadStatus.KEY_REJECTED -> R.string.bpx1_payload_key_rejected
        Bpx1PayloadStatus.MALFORMED -> R.string.bpx1_payload_malformed
    },
)

private fun Bpx1ConnectionFailure.stringRes(): Int = when (this) {
    Bpx1ConnectionFailure.INVALID_ADDRESS -> R.string.bpx1_connection_invalid_address
    Bpx1ConnectionFailure.BLUETOOTH_UNAVAILABLE -> R.string.bpx1_connection_bluetooth_unavailable
    Bpx1ConnectionFailure.PERMISSION_DENIED -> R.string.bpx1_connection_permission_denied
    Bpx1ConnectionFailure.CONNECT_FAILED -> R.string.bpx1_connection_failed
    Bpx1ConnectionFailure.SERVICE_DISCOVERY_FAILED -> R.string.bpx1_connection_service_failed
    Bpx1ConnectionFailure.TIMEOUT -> R.string.bpx1_connection_timeout
}

private fun Bpx1SettingsMessage.stringRes(): Int = when (this) {
    Bpx1SettingsMessage.CONFIGURATION_SAVED -> R.string.bpx1_message_saved
    Bpx1SettingsMessage.CONFIGURATION_CLEARED -> R.string.bpx1_message_cleared
    Bpx1SettingsMessage.SCAN_FAILED -> R.string.bpx1_message_scan_failed
    Bpx1SettingsMessage.MEASUREMENT_IMPORTED -> R.string.bpx1_message_imported
}

private fun requiredBpx1Permissions(): List<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    )
    else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
}
