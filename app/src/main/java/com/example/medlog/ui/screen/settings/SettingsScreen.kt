package com.example.medlog.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showArchivedSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(vertical = 8.dp),
        ) {
            // ── 提醒设置 ────────────────────────────────────────────────
            SettingsSectionHeader("提醒设置")
            SwitchListTile(
                title = "持续提醒",
                subtitle = "服药未确认时每隔 N 分钟重复提醒",
                checked = uiState.persistentReminder,
                onCheckedChange = viewModel::setPersistentReminder,
                icon = Icons.Rounded.Notifications,
            )
            if (uiState.persistentReminder) {
                ListItem(
                    headlineContent = { Text("提醒间隔：${uiState.persistentIntervalMinutes} 分钟") },
                    supportingContent = {
                        Slider(
                            value = uiState.persistentIntervalMinutes.toFloat(),
                            onValueChange = { viewModel.setPersistentInterval(it.toInt()) },
                            valueRange = 3f..30f,
                            steps = 8,
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.Timer, null) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // ── 作息时间 ────────────────────────────────────────────────
            SettingsSectionHeader("作息时间（用于模糊时段提醒）")
            RoutineTimeTile("起床", uiState.wakeHour, uiState.wakeMinute,
                Icons.Rounded.WbSunny) { h, m -> viewModel.updateRoutineTime("wake", h, m) }
            RoutineTimeTile("早餐", uiState.breakfastHour, uiState.breakfastMinute,
                Icons.Rounded.Coffee) { h, m -> viewModel.updateRoutineTime("breakfast", h, m) }
            RoutineTimeTile("午餐", uiState.lunchHour, uiState.lunchMinute,
                Icons.Rounded.LunchDining) { h, m -> viewModel.updateRoutineTime("lunch", h, m) }
            RoutineTimeTile("晚餐", uiState.dinnerHour, uiState.dinnerMinute,
                Icons.Rounded.DinnerDining) { h, m -> viewModel.updateRoutineTime("dinner", h, m) }
            RoutineTimeTile("睡觉", uiState.bedHour, uiState.bedMinute,
                Icons.Rounded.Bedtime) { h, m -> viewModel.updateRoutineTime("bed", h, m) }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // ── 药品管理 ────────────────────────────────────────────────
            SettingsSectionHeader("药品管理")
            ListItem(
                headlineContent = { Text("已归档药品") },
                supportingContent = { Text("${uiState.archivedMedications.size} 种药品已归档") },
                leadingContent = { Icon(Icons.Rounded.Archive, null) },
                modifier = androidx.compose.ui.Modifier.then(
                    Modifier.clickableSetting { showArchivedSheet = true }
                ),
            )
        }
    }

    if (showArchivedSheet) {
        ArchivedMedicationsSheet(
            archived = uiState.archivedMedications,
            onRestore = viewModel::unarchiveMedication,
            onDismiss = { showArchivedSheet = false },
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SwitchListTile(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineTimeTile(
    label: String,
    hour: Int,
    minute: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onTimeSelected: (Int, Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text("%02d:%02d".format(hour, minute)) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.then(Modifier.clickableSetting { showPicker = true }),
    )
    if (showPicker) {
        val timeState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("选择$label 时间") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeSelected(timeState.hour, timeState.minute)
                    showPicker = false
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivedMedicationsSheet(
    archived: List<com.example.medlog.data.model.Medication>,
    onRestore: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "已归档药品",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        if (archived.isEmpty()) {
            Text(
                "暂无归档药品",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        archived.forEach { med ->
            ListItem(
                headlineContent = { Text(med.name) },
                supportingContent = { Text(med.category) },
                trailingContent = {
                    TextButton(onClick = { onRestore(med.id) }) { Text("恢复") }
                },
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun Modifier.clickableSetting(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
