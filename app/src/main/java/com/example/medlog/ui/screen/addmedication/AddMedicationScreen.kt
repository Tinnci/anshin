package com.example.medlog.ui.screen.addmedication

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.medlog.R
import com.example.medlog.data.model.TimePeriod
import java.text.SimpleDateFormat
import java.util.*

private data class FormOption(val key: String, val label: String, val icon: ImageVector)

// FORM_OPTIONS and DOSE_UNITS moved inside AddMedicationScreen composable

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
    val enableTimePeriodMode by viewModel.enableTimePeriodMode.collectAsState()

    val formOptions = listOf(
        FormOption("tablet",  stringResource(R.string.add_form_tablet), Icons.Rounded.Medication),
        FormOption("capsule", stringResource(R.string.add_form_capsule), Icons.Rounded.Science),
        FormOption("liquid",  stringResource(R.string.add_form_liquid), Icons.Rounded.LocalDrink),
        FormOption("powder",  stringResource(R.string.add_form_powder), Icons.Rounded.WaterDrop),
        FormOption("patch",   stringResource(R.string.add_form_patch), Icons.Rounded.Healing),
        FormOption("other",   stringResource(R.string.add_form_other), Icons.Rounded.MoreHoriz),
    )
    val doseUnits = listOf(
        stringResource(R.string.add_unit_tablet),
        stringResource(R.string.add_unit_capsule),
        "ml",
        "mg",
        stringResource(R.string.add_unit_drop),
        stringResource(R.string.add_unit_bag),
        stringResource(R.string.add_unit_tube),
        stringResource(R.string.add_unit_patch_unit),
    )
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(medicationId) {
        if (medicationId != null) viewModel.loadExisting(medicationId)
    }
    LaunchedEffect(drugName) {
        if (!drugName.isNullOrEmpty() && medicationId == null) {
            viewModel.prefillFromDrug(drugName, drugCategory.orEmpty())
        }
    }
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(if (medicationId == null) stringResource(R.string.add_title_new) else stringResource(R.string.add_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.add_back_cd))
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { viewModel.save(medicationId) },
                        enabled = !uiState.isSaving,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.add_save))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── 基本信息 ─────────────────────────────────────────
            FormSection(title = stringResource(R.string.add_section_basic), icon = Icons.Rounded.Info) {
                // 药品名称：带数据库搜索建议的下拉输入框
                ExposedDropdownMenuBox(
                    expanded = uiState.showDrugSuggestions,
                    onExpandedChange = { if (!it) viewModel.dismissDrugSuggestions() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = viewModel::onNameChange,
                        label = { Text(stringResource(R.string.add_name_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable),
                        isError = uiState.error != null && uiState.name.isBlank(),
                        supportingText = {
                            if (uiState.error != null && uiState.name.isBlank())
                                Text(uiState.error ?: "", color = MaterialTheme.colorScheme.error)
                        },
                        leadingIcon = { Icon(Icons.Rounded.Medication, null) },
                        trailingIcon = {
                            if (uiState.name.isNotBlank()) {
                                IconButton(onClick = { viewModel.onNameChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.add_clear_cd))
                                }
                            }
                        },
                        singleLine = true,
                    )
                    if (uiState.drugSuggestions.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = uiState.showDrugSuggestions,
                            onDismissRequest = viewModel::dismissDrugSuggestions,
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
                                                text = drug.category + if (drug.isTcm) stringResource(R.string.add_tcm_suffix) else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (drug.isTcm) Icons.Rounded.LocalFlorist else Icons.Rounded.Medication,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    onClick = { viewModel.onDrugSelected(drug) },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.category,
                    onValueChange = viewModel::onCategoryChange,
                    label = { Text(stringResource(R.string.add_category_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.add_category_placeholder)) },
                    leadingIcon = { Icon(Icons.Rounded.Category, null) },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.PriorityHigh, null, tint = MaterialTheme.colorScheme.error)
                        Column {
                            Text(stringResource(R.string.add_high_priority), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.add_high_priority_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = uiState.isHighPriority,
                        onCheckedChange = viewModel::onHighPriorityChange,
                    )
                }
            }

            // ── 药品剂型 ─────────────────────────────────────────
            FormSection(title = stringResource(R.string.add_section_form), icon = Icons.Rounded.Healing) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false,
                ) {
                    items(formOptions) { option ->
                        val isSelected = uiState.form == option.key
                        Card(
                            onClick = { viewModel.onFormChange(option.key) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    option.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // ── 每次剂量 ─────────────────────────────────────────
            FormSection(title = stringResource(R.string.add_section_dose), icon = Icons.Rounded.MonitorWeight) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        uiState.doseQuantity.let {
                            if (it == it.toLong().toDouble()) it.toLong().toString()
                            else "%.1f".format(it)
                        },
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Slider(
                        value = uiState.doseQuantity.toFloat(),
                        onValueChange = { viewModel.onDoseQuantityChange(it.toDouble()) },
                        valueRange = 0.5f..10f,
                        steps = 18,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.add_dose_slide_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.add_unit_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    doseUnits.forEach { unit ->
                        FilterChip(
                            selected = uiState.doseUnit == unit,
                            onClick = { viewModel.onDoseUnitChange(unit) },
                            label = { Text(unit) },
                        )
                    }
                }
            }

            // ── 按需用药 ─────────────────────────────────────────
            FormSection(title = stringResource(R.string.add_section_usage), icon = Icons.Rounded.EventRepeat) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.HourglassBottom, null, tint = MaterialTheme.colorScheme.secondary)
                        Column {
                            Text(stringResource(R.string.add_prn_label), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.add_prn_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(checked = uiState.isPRN, onCheckedChange = viewModel::onIsPRNChange)
                }
                AnimatedVisibility(visible = uiState.isPRN, enter = expandVertically(), exit = shrinkVertically()) {
                    OutlinedTextField(
                        value = uiState.maxDailyDose,
                        onValueChange = viewModel::onMaxDailyDoseChange,
                        label = { Text(stringResource(R.string.add_max_daily_dose)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        suffix = { Text(stringResource(R.string.add_times_per_day)) },
                        singleLine = true,
                    )
                }

                // ── 服药频率（非PRN才显示）──────────────────────
                AnimatedVisibility(visible = !uiState.isPRN, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(stringResource(R.string.add_freq_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val freqOptions = listOf("daily" to stringResource(R.string.add_freq_daily), "interval" to stringResource(R.string.add_freq_interval), "specific_days" to stringResource(R.string.add_freq_specific))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            freqOptions.forEachIndexed { index, (key, label) ->
                                SegmentedButton(
                                    selected = uiState.frequencyType == key,
                                    onClick = { viewModel.onFrequencyTypeChange(key) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = freqOptions.size),
                                ) { Text(label) }
                            }
                        }
                        AnimatedVisibility(uiState.frequencyType == "interval") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.add_freq_every_n), style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(
                                    value = uiState.frequencyInterval.toString(),
                                    onValueChange = { viewModel.onFrequencyIntervalChange(it.toIntOrNull() ?: 1) },
                                    modifier = Modifier.width(80.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                )
                                Text(stringResource(R.string.add_freq_interval_suffix), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        AnimatedVisibility(uiState.frequencyType == "specific_days") {
                            val days = uiState.frequencyDays.split(",").filter { it.isNotBlank() }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val weekLabels = listOf(
                                    stringResource(R.string.history_weekday_1) to 1,
                                    stringResource(R.string.history_weekday_2) to 2,
                                    stringResource(R.string.history_weekday_3) to 3,
                                    stringResource(R.string.history_weekday_4) to 4,
                                    stringResource(R.string.history_weekday_5) to 5,
                                    stringResource(R.string.history_weekday_6) to 6,
                                    stringResource(R.string.history_weekday_7) to 7,
                                )
                                weekLabels.forEach { (label, day) ->
                                    FilterChip(
                                        selected = days.contains(day.toString()),
                                        onClick = { viewModel.toggleFrequencyDay(day) },
                                        label = { Text(label) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 服药时段 & 提醒（PRN 时作为可选提醒时间）─────────────────
            FormSection(
                title = if (uiState.isPRN) stringResource(R.string.add_section_reminder_optional) else stringResource(R.string.add_section_time_period),
                icon = Icons.Rounded.Schedule,
            ) {
                Text(
                    text = if (uiState.isPRN)
                        stringResource(R.string.add_prn_reminder_hint)
                    else
                        stringResource(R.string.add_select_reminder_mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // ── 精确时间 / 作息时间 模式切换 ─────────────────────────
                // 若用户在设置中关闭了作息时间段模式，强制为精确时间模式
                val isExactMode = !enableTimePeriodMode || uiState.timePeriod == TimePeriod.EXACT
                AnimatedVisibility(
                    visible = enableTimePeriodMode,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !isExactMode,
                            onClick = { if (isExactMode) viewModel.onTimePeriodChange(TimePeriod.MORNING) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = !isExactMode) {
                                    Icon(Icons.Rounded.WbSunny, null, Modifier.size(16.dp))
                                }
                            },
                        ) { Text(stringResource(R.string.add_time_period_mode)) }
                        SegmentedButton(
                            selected = isExactMode,
                            onClick = { if (!isExactMode) viewModel.onTimePeriodChange(TimePeriod.EXACT) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = isExactMode) {
                                    Icon(Icons.Rounded.Schedule, null, Modifier.size(16.dp))
                                }
                            },
                        ) { Text(stringResource(R.string.add_exact_time_mode)) }
                    }
                }
                // 作息时间模式：时段选择芯片 + 自动提醒时间提示
                AnimatedVisibility(
                    visible = !isExactMode,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TimePeriod.entries.filter { it != TimePeriod.EXACT }.forEach { tp ->
                                FilterChip(
                                    selected = tp == uiState.timePeriod,
                                    onClick = { viewModel.onTimePeriodChange(tp) },
                                    label = { Text(stringResource(tp.labelRes), style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = {
                                        Icon(tp.icon, null, Modifier.size(FilterChipDefaults.IconSize))
                                    },
                                )
                            }
                        }
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    stringResource(R.string.add_reminder_hint_format, uiState.reminderTimes.firstOrNull().orEmpty()),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            icon = {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(SuggestionChipDefaults.IconSize),
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                iconContentColor = MaterialTheme.colorScheme.secondary,
                            ),
                        )
                    }
                }
                // 精确时间模式：用户手动设置多个提醒时间 + 可选间隔给药
                AnimatedVisibility(visible = isExactMode, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReminderTimesRow(
                            times = uiState.reminderTimes,
                            onAdd = viewModel::addReminderTime,
                            onRemove = viewModel::removeReminderTime,
                        )
                        // 间隔给药开关（适用于旅行跨时区、抗生素等需精确间隔的场景）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column {
                                    Text(stringResource(R.string.add_interval_dosing), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        stringResource(R.string.add_interval_dosing_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = uiState.intervalHours > 0,
                                onCheckedChange = { on ->
                                    viewModel.onIntervalHoursChange(if (on) 8 else 0)
                                },
                            )
                        }
                        AnimatedVisibility(
                            visible = uiState.intervalHours > 0,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            OutlinedTextField(
                                value = if (uiState.intervalHours > 0) uiState.intervalHours.toString() else "",
                                onValueChange = { v -> v.toIntOrNull()?.let { viewModel.onIntervalHoursChange(it) } },
                                label = { Text(stringResource(R.string.add_interval_hours_label)) },
                                suffix = { Text(stringResource(R.string.add_interval_hours_unit)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                supportingText = { Text(stringResource(R.string.add_interval_hours_hint)) },
                            )
                        }
                    }
                }
            }

            // ── 起止日期 ─────────────────────────────────────────
            FormSection(title = stringResource(R.string.add_section_dates), icon = Icons.Rounded.DateRange) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            // ── 库存管理 ─────────────────────────────────────────
            FormSection(title = stringResource(R.string.add_section_stock), icon = Icons.Rounded.Inventory) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val offLabel = stringResource(R.string.add_refill_off)
                    val refillDaysLabel7 = pluralStringResource(R.plurals.history_streak_max_days, 7, 7)
                    val refillDaysLabel14 = pluralStringResource(R.plurals.history_streak_max_days, 14, 14)
                    val refillDaysLabel30 = pluralStringResource(R.plurals.history_streak_max_days, 30, 30)
                    listOf(0 to offLabel, 7 to refillDaysLabel7, 14 to refillDaysLabel14, 30 to refillDaysLabel30).forEach { (days, label) ->
                        FilterChip(
                            selected = uiState.refillReminderDays == days,
                            onClick = { viewModel.onRefillReminderDaysChange(days) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // ── 备注 ─────────────────────────────────────────────
            FormSection(title = stringResource(R.string.add_section_notes), icon = Icons.AutoMirrored.Rounded.Notes) {
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = { Text(stringResource(R.string.add_notes_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    placeholder = { Text(stringResource(R.string.add_notes_placeholder)) },
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── 辅助组件 ─────────────────────────────────────────────────────────────────

@Composable
private fun FormSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimesRow(
    times: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val cal = remember { Calendar.getInstance() }
    val timePickerState = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE),
        is24Hour = true,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.add_reminder_time_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            times.forEach { hhmm ->
                InputChip(
                    selected = true,
                    onClick = {},
                    label = { Text(hhmm) },
                    trailingIcon = {
                        IconButton(onClick = { onRemove(hhmm) }, modifier = Modifier.size(18.dp)) {
                            Icon(Icons.Filled.Close, null, Modifier.size(14.dp))
                        }
                    },
                )
            }
            AssistChip(
                onClick = { showPicker = !showPicker },
                label = { Text(if (showPicker) stringResource(R.string.add_reminder_collapse) else stringResource(R.string.add_reminder_add_btn)) },
                leadingIcon = {
                    Icon(
                        if (showPicker) Icons.Filled.ExpandLess else Icons.Filled.Add,
                        null,
                        Modifier.size(18.dp),
                    )
                },
            )
        }
        // 内联内嵌时间输入（无对话框）
        AnimatedVisibility(showPicker, enter = expandVertically(), exit = shrinkVertically()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        Spacer(Modifier.width(8.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    timestamp: Long?,
    onPick: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    nullable: Boolean = false,
) {
    val fmt = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }
    val displayText = timestamp?.let { fmt.format(Date(it)) } ?: stringResource(R.string.add_date_unset)
    var expanded by remember { mutableStateOf(false) }
    val state = rememberDatePickerState(initialSelectedDateMillis = timestamp)

    Column(modifier = modifier) {
        OutlinedCard(onClick = { expanded = !expanded }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(displayText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (nullable && timestamp != null) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { onPick(null) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, null, Modifier.size(14.dp))
                    }
                }
            }
        }
        // 内联内嵌日期选择器（无对话框）
        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
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
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.cancel)) }
                        Spacer(Modifier.width(8.dp))
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

