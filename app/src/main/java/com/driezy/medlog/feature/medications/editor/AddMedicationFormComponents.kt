package com.driezy.medlog.feature.medications.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.driezy.medlog.ui.util.icon
import java.text.SimpleDateFormat
import java.util.*

@Composable
internal fun AddMedicationFormContent(
    paddingValues: PaddingValues,
    uiState: AddMedicationUiState,
    enableTimePeriodMode: Boolean,
    formOptions: List<FormOption>,
    doseUnits: List<String>,
    onAction: (AddMedicationUiAction) -> Unit,
    onOpenOcrScanner: () -> Unit,
    onEditCustomDose: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(bottom = MedLogSpacing.XXLarge),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
    ) {
        MedicationBasicInfoSection(
            uiState = uiState,
            onAction = onAction,
            onOpenOcrScanner = onOpenOcrScanner,
        )
        MedicationFormChoiceSection(
            uiState = uiState,
            formOptions = formOptions,
            onAction = onAction,
        )
        MedicationDoseSection(
            uiState = uiState,
            doseUnits = doseUnits,
            onAction = onAction,
            onEditCustomDose = onEditCustomDose,
        )
        MedicationUsageFrequencySection(uiState = uiState, onAction = onAction)
        MedicationReminderScheduleSection(
            uiState = uiState,
            enableTimePeriodMode = enableTimePeriodMode,
            onAction = onAction,
        )
        MedicationDateSection(uiState = uiState, onAction = onAction)
        MedicationStockSection(uiState = uiState, onAction = onAction)
        MedicationNotesSection(uiState = uiState, onAction = onAction)
        Spacer(Modifier.height(MedLogSpacing.Small))
    }
}

// ── 辅助组件 ─────────────────────────────────────────────────────────────────

@Composable
internal fun FormSection(title: String, icon: Int, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            ) {
                MedLogIcon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    title,
                    style = MaterialTheme.emphasizedTypography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReminderTimesRow(times: List<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    val motionScheme = MaterialTheme.motionScheme
    var showPicker by remember { mutableStateOf(false) }
    val cal = remember { Calendar.getInstance() }
    val timePickerState = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE),
        is24Hour = true,
    )

    Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small)) {
        Text(
            stringResource(R.string.add_reminder_time_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            times.forEach { hhmm ->
                InputChip(
                    selected = true,
                    onClick = {},
                    label = { Text(hhmm) },
                    trailingIcon = {
                        IconButton(onClick = { onRemove(hhmm) }, modifier = Modifier.size(18.dp)) {
                            MedLogIcon(MedLogIcons.Close, null, Modifier.size(14.dp))
                        }
                    },
                )
            }
            AssistChip(
                onClick = { showPicker = !showPicker },
                label = {
                    Text(
                        if (showPicker) {
                            stringResource(
                                R.string.add_reminder_collapse,
                            )
                        } else {
                            stringResource(R.string.add_reminder_add_btn)
                        },
                    )
                },
                leadingIcon = {
                    MedLogIcon(
                        if (showPicker) MedLogIcons.ExpandLess else MedLogIcons.Add,
                        null,
                        Modifier.size(18.dp),
                    )
                },
            )
        }
        // 内联内嵌时间输入（无对话框）
        AnimatedVisibility(
            visible = showPicker,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(MedLogSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                ) {
                    Text(
                        stringResource(R.string.add_reminder_add_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TimeInput(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
                        Spacer(Modifier.width(MedLogSpacing.Small))
                        FilledTonalButton(onClick = {
                            onAdd("%02d:%02d".format(timePickerState.hour, timePickerState.minute))
                            showPicker = false
                        }) { Text(stringResource(R.string.confirm)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DatePickerField(
    label: String,
    timestamp: Long?,
    onPick: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    nullable: Boolean = false,
) {
    val motionScheme = MaterialTheme.motionScheme
    val fmt = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }
    val displayText = timestamp?.let { fmt.format(Date(it)) } ?: stringResource(R.string.add_date_unset)
    var expanded by remember { mutableStateOf(false) }
    val state = rememberDatePickerState(initialSelectedDateMillis = timestamp)

    Column(modifier = modifier) {
        OutlinedCard(onClick = { expanded = !expanded }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MedLogSpacing.Medium, vertical = MedLogSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                MedLogIcon(
                    icon = if (expanded) MedLogIcons.ExpandLess else MedLogIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (nullable && timestamp != null) {
                    Spacer(Modifier.width(MedLogSpacing.Tiny))
                    IconButton(onClick = { onPick(null) }, modifier = Modifier.size(28.dp)) {
                        MedLogIcon(MedLogIcons.Close, null, Modifier.size(14.dp))
                    }
                }
            }
        }
        // 内联内嵌日期选择器（无对话框）
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column {
                    DatePicker(
                        state = state,
                        showModeToggle = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MedLogSpacing.Large)
                            .padding(bottom = MedLogSpacing.Medium),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.cancel)) }
                        Spacer(Modifier.width(MedLogSpacing.Small))
                        FilledTonalButton(onClick = {
                            onPick(state.selectedDateMillis)
                            expanded = false
                        }) { Text(stringResource(R.string.confirm)) }
                    }
                }
            }
        }
    }
}
