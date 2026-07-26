package com.driezy.medlog.ui.screen.addmedication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.driezy.medlog.R
import com.driezy.medlog.ui.components.VoiceInputTrailingIcon
import com.driezy.medlog.ui.components.messageText
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.util.icon
import com.driezy.medlog.voice.VoiceInputPhase
import java.util.*

@Composable
internal fun MedicationDateSection(uiState: AddMedicationUiState, viewModel: AddMedicationViewModel) {
    // ── 起止日期 ─────────────────────────────────────────
    FormSection(title = stringResource(R.string.add_section_dates), icon = MedLogIcons.DateRange) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small)) {
            DatePickerField(
                label = stringResource(R.string.add_date_start),
                timestamp = uiState.startDate,
                onPick = { it?.let { ms -> viewModel.onStartDateChange(ms) } },
                modifier = Modifier.weight(1f),
            )
            DatePickerField(
                label = stringResource(R.string.add_date_end),
                timestamp = uiState.endDate,
                onPick = viewModel::onEndDateChange,
                nullable = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun MedicationStockSection(uiState: AddMedicationUiState, viewModel: AddMedicationViewModel) {
    // ── 库存管理 ─────────────────────────────────────────
    FormSection(title = stringResource(R.string.add_section_stock), icon = MedLogIcons.Inventory) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small)) {
            OutlinedTextField(
                value = uiState.stock,
                onValueChange = viewModel::onStockChange,
                label = { Text(stringResource(R.string.add_current_stock)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(uiState.doseUnit) },
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.refillThreshold,
                onValueChange = viewModel::onRefillThresholdChange,
                label = { Text(stringResource(R.string.add_refill_remind_label)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(uiState.doseUnit) },
                singleLine = true,
            )
        }
        // ── 按天数估算备货提醒 ──────────────────────────────
        Text(
            stringResource(R.string.add_refill_time_est_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.add_refill_time_est_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            val offLabel = stringResource(R.string.add_refill_off)
            val refillDaysLabel7 = pluralStringResource(R.plurals.history_streak_max_days, 7, 7)
            val refillDaysLabel14 = pluralStringResource(R.plurals.history_streak_max_days, 14, 14)
            val refillDaysLabel30 = pluralStringResource(R.plurals.history_streak_max_days, 30, 30)
            listOf(
                0 to offLabel,
                7 to refillDaysLabel7,
                14 to refillDaysLabel14,
                30 to refillDaysLabel30,
            ).forEach { (days, label) ->
                FilterChip(
                    selected = uiState.refillReminderDays == days,
                    onClick = { viewModel.onRefillReminderDaysChange(days) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
internal fun MedicationNotesSection(uiState: AddMedicationUiState, viewModel: AddMedicationViewModel) {
    // ── 备注 ─────────────────────────────────────────────
    FormSection(title = stringResource(R.string.add_section_notes), icon = MedLogIcons.Notes) {
        OutlinedTextField(
            value = uiState.notes,
            onValueChange = viewModel::onNotesChange,
            label = { Text(stringResource(R.string.add_notes_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            placeholder = { Text(stringResource(R.string.add_notes_placeholder)) },
            trailingIcon = {
                VoiceInputTrailingIcon(
                    voiceInput = uiState.voiceInput,
                    onStartVoiceInput = viewModel::startVoiceInput,
                    onStopVoiceInput = viewModel::stopVoiceInput,
                )
            },
            supportingText = {
                uiState.voiceInput.messageText()?.let { Text(it) }
            },
            isError = uiState.voiceInput.phase == VoiceInputPhase.ERROR,
        )
    }
}
