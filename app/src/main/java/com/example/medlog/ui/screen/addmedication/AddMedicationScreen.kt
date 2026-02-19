package com.example.medlog.ui.screen.addmedication

import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.medlog.data.model.TimePeriod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationScreen(
    medicationId: Long?,
    drugName: String? = null,
    drugCategory: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddMedicationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Load existing medication once
    LaunchedEffect(medicationId) {
        if (medicationId != null) viewModel.loadExisting(medicationId)
    }

    // Pre-fill from drug database selection
    LaunchedEffect(drugName) {
        if (!drugName.isNullOrEmpty() && medicationId == null) {
            viewModel.prefillFromDrug(drugName, drugCategory.orEmpty())
        }
    }

    // Navigate away after save
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (medicationId == null) "新增药品" else "编辑药品") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save(medicationId) },
                        enabled = !uiState.isSaving,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "保存")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Basic Info Section ---
            SectionTitle("基本信息")

            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("药品名称 *") },
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.error != null && uiState.name.isBlank(),
                supportingText = {
                    if (uiState.error != null && uiState.name.isBlank()) Text(uiState.error!!)
                },
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.dose,
                    onValueChange = viewModel::onDoseChange,
                    label = { Text("剂量") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.doseUnit,
                    onValueChange = viewModel::onDoseUnitChange,
                    label = { Text("单位") },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("片/粒/ml") },
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = uiState.category,
                onValueChange = viewModel::onCategoryChange,
                label = { Text("分类（可选）") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("例：心血管、消化、抗感染…") },
                singleLine = true,
            )

            // --- Reminder Section ---
            SectionTitle("服药时间")

            TimePeriodSelector(
                selected = uiState.timePeriod,
                onSelect = viewModel::onTimePeriodChange,
            )

            if (uiState.timePeriod == TimePeriod.EXACT) {
                TimePickerRow(
                    hour = uiState.reminderHour,
                    minute = uiState.reminderMinute,
                    onPick = viewModel::onReminderTimeChange,
                )
            }

            // --- Stock Section ---
            SectionTitle("库存（可选）")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.stock,
                    onValueChange = viewModel::onStockChange,
                    label = { Text("当前库存") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.refillThreshold,
                    onValueChange = viewModel::onRefillThresholdChange,
                    label = { Text("补药提醒阈值") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }

            // --- Notes Section ---
            SectionTitle("备注")

            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )

            // Bottom save button
            Button(
                onClick = { viewModel.save(medicationId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                enabled = !uiState.isSaving,
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("保存")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePeriodSelector(
    selected: TimePeriod,
    onSelect: (TimePeriod) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimePeriod.values().forEach { tp ->
            FilterChip(
                selected = tp == selected,
                onClick = { onSelect(tp) },
                label = { Text(tp.label, style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(
                        imageVector = tp.icon,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerRow(hour: Int, minute: Int, onPick: (Int, Int) -> Unit) {
    val context = LocalContext.current
    val label = "%02d:%02d".format(hour, minute)
    var showPicker by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("具体时间：", style = MaterialTheme.typography.bodyMedium)
        AssistChip(
            onClick = { showPicker = true },
            label = { Text(label) },
        )
    }

    if (showPicker) {
        val dialog = TimePickerDialog(
            context,
            { _, h, m -> onPick(h, m); showPicker = false },
            hour, minute, true,
        )
        dialog.setOnDismissListener { showPicker = false }
        dialog.show()
        DisposableEffect(Unit) {
            onDispose { dialog.dismiss() }
        }
    }
}
