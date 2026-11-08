package com.driezy.medlog.feature.medications.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.emphasizedTypography
import com.driezy.medlog.ui.util.formatDosePrecise
import com.driezy.medlog.ui.util.icon
import com.driezy.medlog.ui.util.medicationNameLanguage
import com.driezy.medlog.ui.util.rememberDrugCategoryLabels
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicationBasicInfoSection(
    uiState: AddMedicationUiState,
    onAction: (AddMedicationUiAction) -> Unit,
    onOpenOcrScanner: () -> Unit,
) {
    val language = medicationNameLanguage()
    val categoryLabels = rememberDrugCategoryLabels()
    // ── 基本信息 ─────────────────────────────────────────
    FormSection(title = stringResource(R.string.add_section_basic), icon = MedLogIcons.Info) {
        // 药品名称：带数据库搜索建议的下拉输入框
        ExposedDropdownMenuBox(
            expanded = uiState.showDrugSuggestions,
            onExpandedChange = { expanded ->
                onAction(
                    if (expanded) {
                        AddMedicationUiAction.DrugSuggestionsRequested(language)
                    } else {
                        AddMedicationUiAction.DismissDrugSuggestions
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { onAction(AddMedicationUiAction.NameChanged(it, language)) },
                label = { Text(stringResource(R.string.add_name_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (state.isFocused) onAction(AddMedicationUiAction.DrugSuggestionsRequested(language))
                    }
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
                            IconButton(onClick = { onAction(AddMedicationUiAction.NameChanged("", language)) }) {
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
                minLines = 1,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            if (uiState.drugSuggestions.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = uiState.showDrugSuggestions,
                    onDismissRequest = { onAction(AddMedicationUiAction.DismissDrugSuggestions) },
                    shape = MaterialTheme.shapes.large,
                ) {
                    uiState.drugSuggestions.forEach { drug ->
                        val localizedName = drug.localizedName(language)
                        val alternate = drug.alternateNameMatching(uiState.name.trim(), language)
                        val subtitle = when {
                            alternate != null -> stringResource(R.string.drugs_tag_hint, alternate)
                            !drug.hasLocalizedName(language) -> stringResource(R.string.drugs_original_name)
                            else -> categoryLabels.label(drug.category, drug.isTcm)
                        }
                        val tcmSuffix = if (drug.isTcm) stringResource(R.string.add_tcm_suffix) else ""
                        DropdownMenuItem(
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = localizedName,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = subtitle + tcmSuffix,
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
                            onClick = {
                                onAction(
                                    AddMedicationUiAction.DrugSelected(
                                        drug = drug,
                                        language = language,
                                        localizedCategory = categoryLabels.label(drug.category, drug.isTcm),
                                    ),
                                )
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
        }

        val categoryLabel = categoryLabels.label(uiState.category, uiState.isTcm)
        OutlinedTextField(
            value = uiState.category,
            onValueChange = { onAction(AddMedicationUiAction.CategoryChanged(it)) },
            label = { Text(stringResource(R.string.add_category_label)) },
            supportingText = if (categoryLabel != uiState.category) ({ Text(categoryLabel) }) else null,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.add_category_placeholder)) },
            leadingIcon = { MedLogIcon(MedLogIcons.Category, null) },
            minLines = 1,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
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
            Spacer(Modifier.width(MedLogSpacing.Small))
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
        val enlargedText = LocalDensity.current.fontScale > 1.3f
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (enlargedText) 2 else 3),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (enlargedText) 480.dp else 200.dp),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            userScrollEnabled = false,
        ) {
            items(formOptions, key = { it.key }) { option ->
                val isSelected = uiState.form == option.key
                Card(
                    onClick = { onAction(AddMedicationUiAction.FormChanged(option.key)) },
                    shape = MaterialTheme.shapes.large,
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
