package com.driezy.medlog.feature.medications.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.emphasizedTypography
import com.driezy.medlog.ui.util.formatDosePrecise
import com.driezy.medlog.ui.util.icon
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicationBasicInfoSection(
    uiState: AddMedicationUiState,
    onAction: (AddMedicationUiAction) -> Unit,
    onOpenOcrScanner: () -> Unit,
) {
    // ── 基本信息 ─────────────────────────────────────────
    FormSection(title = stringResource(R.string.add_section_basic), icon = MedLogIcons.Info) {
        // 药品名称：带数据库搜索建议的下拉输入框
        ExposedDropdownMenuBox(
            expanded = uiState.showDrugSuggestions,
            onExpandedChange = { if (!it) onAction(AddMedicationUiAction.DismissDrugSuggestions) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { onAction(AddMedicationUiAction.NameChanged(it)) },
                label = { Text(stringResource(R.string.add_name_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                isError = (uiState.errorRes != null || uiState.error != null) && uiState.name.isBlank(),
                supportingText = {
                    if (uiState.name.isBlank()) {
                        val msg = uiState.errorRes?.let { stringResource(it) } ?: uiState.error
                        if (msg != null) Text(msg, color = MaterialTheme.colorScheme.error)
                    }
                },
                leadingIcon = { MedLogIcon(MedLogIcons.Medication, null) },
                trailingIcon = {
                    Row {
                        if (uiState.name.isNotBlank()) {
                            IconButton(onClick = { onAction(AddMedicationUiAction.NameChanged("")) }) {
                                MedLogIcon(
                                    MedLogIcons.Close,
                                    contentDescription = stringResource(R.string.add_clear_cd),
                                )
                            }
                        }
                        FilledTonalIconButton(
                            onClick = onOpenOcrScanner,
                            modifier = Modifier.size(40.dp),
                        ) {
                            MedLogIcon(
                                MedLogIcons.DocumentScanner,
                                contentDescription = stringResource(R.string.ocr_scan_title),
                            )
                        }
                    }
                },
                singleLine = true,
            )
            if (uiState.drugSuggestions.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = uiState.showDrugSuggestions,
                    onDismissRequest = { onAction(AddMedicationUiAction.DismissDrugSuggestions) },
                ) {
                    uiState.drugSuggestions.forEach { drug ->
                        DropdownMenuItem(
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = drug.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text =
                                        drug.category +
                                            if (drug.isTcm) stringResource(R.string.add_tcm_suffix) else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            leadingIcon = {
                                MedLogIcon(
                                    if (drug.isTcm) MedLogIcons.LocalFlorist else MedLogIcons.Medication,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = { onAction(AddMedicationUiAction.DrugSelected(drug)) },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = uiState.category,
            onValueChange = { onAction(AddMedicationUiAction.CategoryChanged(it)) },
            label = { Text(stringResource(R.string.add_category_label)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.add_category_placeholder)) },
            leadingIcon = { MedLogIcon(MedLogIcons.Category, null) },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            ) {
                MedLogIcon(MedLogIcons.PriorityHigh, null, tint = MaterialTheme.colorScheme.error)
                Column {
                    Text(stringResource(R.string.add_high_priority), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.add_high_priority_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = uiState.isHighPriority,
                onCheckedChange = { onAction(AddMedicationUiAction.HighPriorityChanged(it)) },
            )
        }
    }
}

@Composable
internal fun MedicationFormChoiceSection(
    uiState: AddMedicationUiState,
    formOptions: List<FormOption>,
    onAction: (AddMedicationUiAction) -> Unit,
) {
    // ── 药品剂型 ─────────────────────────────────────────
    FormSection(title = stringResource(R.string.add_section_form), icon = MedLogIcons.Healing) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            userScrollEnabled = false,
        ) {
            items(formOptions, key = { it.key }) { option ->
                val isSelected = uiState.form == option.key
                Card(
                    onClick = { onAction(AddMedicationUiAction.FormChanged(option.key)) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(MedLogSpacing.Small)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                    ) {
                        MedLogIcon(
                            option.icon,
                            contentDescription = null,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MedicationDoseSection(
    uiState: AddMedicationUiState,
    doseUnits: List<String>,
    onAction: (AddMedicationUiAction) -> Unit,
    onEditCustomDose: (String) -> Unit,
) {
    // ── 每次剂量 ─────────────────────────────────────────
    FormSection(title = stringResource(R.string.add_section_dose), icon = MedLogIcons.MonitorWeight) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    uiState.doseQuantity.formatDosePrecise(),
                    style = MaterialTheme.emphasizedTypography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(
                    onClick = {
                        onEditCustomDose(uiState.doseQuantity.formatDosePrecise())
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    MedLogIcon(
                        MedLogIcons.Edit,
                        contentDescription = stringResource(R.string.add_dose_custom_input),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Slider(
                value = uiState.doseQuantity.toFloat().coerceIn(0.25f, 20f),
                onValueChange = { onAction(AddMedicationUiAction.DoseQuantityChanged(it.toDouble())) },
                valueRange = 0.25f..10f,
                steps = 38,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.add_dose_slide_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(MedLogSpacing.Tiny))
        Text(
            stringResource(R.string.add_unit_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            doseUnits.forEach { unit ->
                FilterChip(
                    selected = uiState.doseUnit == unit,
                    onClick = { onAction(AddMedicationUiAction.DoseUnitChanged(unit)) },
                    label = { Text(unit) },
                )
            }
        }
    }
}
