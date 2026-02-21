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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.medlog.data.model.TimePeriod
import java.text.SimpleDateFormat
import java.util.*

private data class FormOption(val key: String, val label: String, val icon: ImageVector)

private val FORM_OPTIONS = listOf(
    FormOption("tablet",  "片剂", Icons.Rounded.Medication),
    FormOption("capsule", "胶囊", Icons.Rounded.Science),
    FormOption("liquid",  "液体", Icons.Rounded.LocalDrink),
    FormOption("powder",  "粉末", Icons.Rounded.WaterDrop),
    FormOption("patch",   "贴片", Icons.Rounded.Healing),
    FormOption("other",   "其他", Icons.Rounded.MoreHoriz),
)

private val DOSE_UNITS = listOf("片", "粒", "ml", "mg", "滴", "袋", "支", "贴")

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
                title = { Text(if (medicationId == null) "新增药品" else "编辑药品") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                        Text("保存")
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
            FormSection(title = "基本信息", icon = Icons.Rounded.Info) {
                // 药品名称：带数据库搜索建议的下拉输入框
                ExposedDropdownMenuBox(
                    expanded = uiState.showDrugSuggestions,
                    onExpandedChange = { if (!it) viewModel.dismissDrugSuggestions() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = viewModel::onNameChange,
                        label = { Text("药品名称 *") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable),
                        isError = uiState.error != null && uiState.name.isBlank(),
                        supportingText = {
                            if (uiState.error != null && uiState.name.isBlank())
                                Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                        },
                        leadingIcon = { Icon(Icons.Rounded.Medication, null) },
                        trailingIcon = {
                            if (uiState.name.isNotBlank()) {
                                IconButton(onClick = { viewModel.onNameChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "清除")
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
                                                text = drug.category + if (drug.isTcm) "（中成药）" else "",
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
                    label = { Text("分类（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("如：心血管、消化、抗感染…") },
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
                            Text("高优先级", style = MaterialTheme.typography.bodyMedium)
                            Text("将在列表顶部显示", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = uiState.isHighPriority,
                        onCheckedChange = viewModel::onHighPriorityChange,
                    )
                }
            }

            // ── 药品剂型 ─────────────────────────────────────────
            FormSection(title = "药品剂型", icon = Icons.Rounded.Healing) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false,
                ) {
                    items(FORM_OPTIONS) { option ->
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
            FormSection(title = "每次剂量", icon = Icons.Rounded.MonitorWeight) {
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
                        "滑动选择每次服药数量",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("单位", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DOSE_UNITS.forEach { unit ->
                        FilterChip(
                            selected = uiState.doseUnit == unit,
                            onClick = { viewModel.onDoseUnitChange(unit) },
                            label = { Text(unit) },
                        )
                    }
                }
            }

            // ── 按需用药 ─────────────────────────────────────────
            FormSection(title = "用药方式", icon = Icons.Rounded.EventRepeat) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.HourglassBottom, null, tint = MaterialTheme.colorScheme.secondary)
                        Column {
                            Text("按需用药 (PRN)", style = MaterialTheme.typography.bodyMedium)
                            Text("用于镇痛药等非定时药物", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(checked = uiState.isPRN, onCheckedChange = viewModel::onIsPRNChange)
                }
                AnimatedVisibility(visible = uiState.isPRN, enter = expandVertically(), exit = shrinkVertically()) {
                    OutlinedTextField(
                        value = uiState.maxDailyDose,
                        onValueChange = viewModel::onMaxDailyDoseChange,
                        label = { Text("每日最大剂量（可选）") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        suffix = { Text("次/天") },
                        singleLine = true,
                    )
                }

                // ── 服药频率（非PRN才显示）──────────────────────
                AnimatedVisibility(visible = !uiState.isPRN, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("服药频率", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val freqOptions = listOf("daily" to "每天", "interval" to "间隔", "specific_days" to "指定天")
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
                                Text("每隔", style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(
                                    value = uiState.frequencyInterval.toString(),
                                    onValueChange = { viewModel.onFrequencyIntervalChange(it.toIntOrNull() ?: 1) },
                                    modifier = Modifier.width(80.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                )
                                Text("天服用一次", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        AnimatedVisibility(uiState.frequencyType == "specific_days") {
                            val days = uiState.frequencyDays.split(",").filter { it.isNotBlank() }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                listOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7).forEach { (label, day) ->
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
                title = if (uiState.isPRN) "提醒时间（可选）" else "服药时段",
                icon = Icons.Rounded.Schedule,
            ) {
                Text(
                    text = if (uiState.isPRN)
                        "PRN 药品可设置可选提醒，到时系统会提示您是否需要服药。"
                    else
                        "选择服药的时间段",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TimePeriod.entries.forEach { tp ->
                            FilterChip(
                                selected = tp == uiState.timePeriod,
                                onClick = { viewModel.onTimePeriodChange(tp) },
                                label = { Text(tp.label, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(tp.icon, null, Modifier.size(FilterChipDefaults.IconSize))
                                },
                            )
                        }
                    }
                    // 非 EXACT 时段：显示自动带入的时间提示
                    AnimatedVisibility(
                        visible = uiState.timePeriod != TimePeriod.EXACT,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    "提醒时间：${uiState.reminderTimes.firstOrNull() ?: ""}（来自作息设置）",
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
                // EXACT 时段：用户手动设置多个提醒时间 + 可选间隔给药
                AnimatedVisibility(visible = uiState.timePeriod == TimePeriod.EXACT, enter = expandVertically(), exit = shrinkVertically()) {
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
                                    Text("按间隔给药", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "忽略时钟，按固定小时数给药（旅行/跨时区）",
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
                                label = { Text("间隔小时数") },
                                suffix = { Text("小时") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                supportingText = { Text("常见值：每 4 h / 6 h / 8 h / 12 h / 24 h") },
                            )
                        }
                    }
                }
            }

            // ── 起止日期 ─────────────────────────────────────────
            FormSection(title = "起止日期", icon = Icons.Rounded.DateRange) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField(
                        label = "开始日期",
                        timestamp = uiState.startDate,
                        onPick = { it?.let { ms -> viewModel.onStartDateChange(ms) } },
                        modifier = Modifier.weight(1f),
                    )
                    DatePickerField(
                        label = "结束（可选）",
                        timestamp = uiState.endDate,
                        onPick = viewModel::onEndDateChange,
                        nullable = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── 库存管理 ─────────────────────────────────────────
            FormSection(title = "库存管理（可选）", icon = Icons.Rounded.Inventory) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.stock,
                        onValueChange = viewModel::onStockChange,
                        label = { Text("当前库存") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        suffix = { Text(uiState.doseUnit) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = uiState.refillThreshold,
                        onValueChange = viewModel::onRefillThresholdChange,
                        label = { Text("补药提醒") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        suffix = { Text(uiState.doseUnit) },
                        singleLine = true,
                    )
                }
                // ── 按天数估算备货提醒 ──────────────────────────────
                Text(
                    "时间估算备货提醒",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "根据当前库存和每日用量，在预计剩余 N 天时提醒你提前购药",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0 to "关闭", 7 to "7 天", 14 to "14 天", 30 to "30 天").forEach { (days, label) ->
                        FilterChip(
                            selected = uiState.refillReminderDays == days,
                            onClick = { viewModel.onRefillReminderDaysChange(days) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // ── 备注 ─────────────────────────────────────────────
            FormSection(title = "备注", icon = Icons.AutoMirrored.Rounded.Notes) {
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    placeholder = { Text("用法说明、注意事项…") },
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
        Text("提醒时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                label = { Text(if (showPicker) "收起" else "添加") },
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
                        "添加提醒时间",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TimeInput(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showPicker = false }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = {
                            onAdd("%02d:%02d".format(timePickerState.hour, timePickerState.minute))
                            showPicker = false
                        }) { Text("确认") }
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
    val displayText = timestamp?.let { fmt.format(Date(it)) } ?: "未设置"
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
                        TextButton(onClick = { expanded = false }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = {
                            onPick(state.selectedDateMillis)
                            expanded = false
                        }) { Text("确定") }
                    }
                }
            }
        }
    }
}

