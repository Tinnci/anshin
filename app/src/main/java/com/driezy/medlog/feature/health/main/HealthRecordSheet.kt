package com.driezy.medlog.feature.health

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.ui.components.VoiceInputTrailingIcon
import com.driezy.medlog.ui.components.messageText
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.emphasizedTypography
import com.driezy.medlog.ui.util.labelRes
import com.driezy.medlog.voice.VoiceInputPhase
import com.driezy.medlog.voice.VoiceInputUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddEditHealthSheet(
    draft: HealthDraftState,
    onDismiss: () -> Unit,
    onTypeChange: (HealthType) -> Unit,
    onValueChange: (String) -> Unit,
    onSecondaryChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onTimeChange: (Long) -> Unit,
    onOcrScan: () -> Unit,
    voiceInput: VoiceInputUiState,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, HealthRecordSheetEnabledStates)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (draft.editingId ==
                    null
                ) {
                    stringResource(R.string.health_sheet_add_title)
                } else {
                    stringResource(R.string.health_sheet_edit_title)
                },
                style = MaterialTheme.emphasizedTypography.titleLarge,
            )

            // ── 记录时间选择器 ─────────────────────────────────────────
            HealthTimePicker(
                timestampMs = draft.timestamp,
                onTimeChange = onTimeChange,
            )

            // ── 类型选择器 ─────────────────────────────────────────────
            Text(
                stringResource(R.string.health_type_selector_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HealthType.entries.forEach { type ->
                    FilterChip(
                        selected = draft.type == type,
                        onClick = { onTypeChange(type) },
                        label = { Text(stringResource(type.labelRes)) },
                        leadingIcon = {
                            MedLogIcon(healthTypeIcon(type), null, Modifier.size(16.dp))
                        },
                    )
                }
            }

            // ── 数值输入 ───────────────────────────────────────────────
            if (draft.type == HealthType.BLOOD_PRESSURE) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = draft.value,
                        onValueChange = onValueChange,
                        label = { Text(stringResource(R.string.health_bp_systolic)) },
                        suffix = { Text("mmHg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = draft.secondaryValue,
                        onValueChange = onSecondaryChange,
                        label = { Text(stringResource(R.string.health_bp_diastolic)) },
                        suffix = { Text("mmHg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                OutlinedTextField(
                    value = draft.value,
                    onValueChange = onValueChange,
                    label = { Text(stringResource(draft.type.labelRes)) },
                    suffix = { Text(draft.type.unit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        val type = draft.type
                        if (type != HealthType.WEIGHT) {
                            Text(
                                stringResource(
                                    R.string.health_normal_range,
                                    type.normalMin.toString(),
                                    type.normalMax.toString(),
                                    type.unit,
                                ),
                            )
                        }
                    },
                )
            }

            // ── OCR 拍照填充 ──────────────────────────────────────────
            AssistChip(
                onClick = onOcrScan,
                label = { Text(stringResource(R.string.ocr_health_scan_chip)) },
                leadingIcon = { MedLogIcon(MedLogIcons.CameraAlt, null, Modifier.size(18.dp)) },
            )

            // ── 备注 ──────────────────────────────────────────────────
            OutlinedTextField(
                value = draft.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.common_notes_hint)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                leadingIcon = { MedLogIcon(MedLogIcons.Notes, null) },
                trailingIcon = {
                    VoiceInputTrailingIcon(
                        voiceInput = voiceInput,
                        onStartVoiceInput = onStartVoiceInput,
                        onStopVoiceInput = onStopVoiceInput,
                    )
                },
                supportingText = {
                    voiceInput.messageText()?.let { Text(it) }
                },
                isError = voiceInput.phase == VoiceInputPhase.ERROR,
            )

            // ── 操作按钮 ──────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.common_action_cancel)) }
                val isValid = draft.value.toDoubleOrNull() != null &&
                    (draft.type != HealthType.BLOOD_PRESSURE || draft.secondaryValue.toDoubleOrNull() != null)
                Button(
                    onClick = onSave,
                    enabled = isValid,
                    modifier = Modifier.weight(2f),
                ) { Text(stringResource(R.string.common_action_save)) }
            }
        }
    }
}

// ─── 时间选择器组件 ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthTimePicker(timestampMs: Long, onTimeChange: (Long) -> Unit) {
    val cal = remember(timestampMs) { Calendar.getInstance().apply { timeInMillis = timestampMs } }
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Text(
        stringResource(R.string.health_record_time),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AssistChip(
            onClick = { showDatePicker = true },
            label = { Text(dateFmt.format(Date(timestampMs))) },
            leadingIcon = { MedLogIcon(MedLogIcons.CalendarMonth, null, Modifier.size(18.dp)) },
            modifier = Modifier.weight(1f),
        )
        AssistChip(
            onClick = { showTimePicker = true },
            label = { Text(timeFmt.format(Date(timestampMs))) },
            leadingIcon = { MedLogIcon(MedLogIcons.Schedule, null, Modifier.size(18.dp)) },
            modifier = Modifier.weight(1f),
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = timestampMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedMs ->
                        val selected = Calendar.getInstance().apply { timeInMillis = selectedMs }
                        val merged = Calendar.getInstance().apply {
                            timeInMillis = timestampMs
                            set(Calendar.YEAR, selected.get(Calendar.YEAR))
                            set(Calendar.MONTH, selected.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, selected.get(Calendar.DAY_OF_MONTH))
                        }
                        onTimeChange(merged.timeInMillis)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }

    if (showTimePicker) {
        val tpState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val merged = Calendar.getInstance().apply {
                        timeInMillis = timestampMs
                        set(Calendar.HOUR_OF_DAY, tpState.hour)
                        set(Calendar.MINUTE, tpState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onTimeChange(merged.timeInMillis)
                    showTimePicker = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = { TimePicker(state = tpState) },
            title = null,
        )
    }
}
