package com.driezy.medlog.feature.medications.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.ui.components.*
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.util.formatDose
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MyMedicationsScreen(
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onCatalog: (() -> Unit)?,
    onSettings: () -> Unit,
    viewModel: MyMedicationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MyMedicationsContent(state, onAdd, onOpen, onCatalog, onSettings, viewModel::retry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyMedicationsContent(
    state: MyMedicationsState,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onCatalog: (() -> Unit)?,
    onSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    var archived by rememberSaveable { mutableStateOf(false) }
    val visible = remember(state.medications, archived) { state.medications.filter { it.isArchived == archived } }
    MedLogScreenScaffold(
        title = { Text(stringResource(R.string.my_medications)) },
        actions = buildList {
            if (onCatalog != null) {
                add(
                    TopBarAction(
                        "catalog",
                        stringResource(R.string.medication_catalog),
                        MedLogIcons.Search,
                        TopBarActionPriority.Primary,
                    ),
                )
            }
            add(
                TopBarAction(
                    "settings",
                    stringResource(R.string.settings_action_open),
                    MedLogIcons.Settings,
                    TopBarActionPriority.Secondary,
                ),
            )
        },
        chromeState = ScreenChromeState(
            isLoading = state.loading,
            fab = ScreenFab("add", stringResource(R.string.add_title_new), MedLogIcons.Add),
        ),
        onChromeAction = {
            when (it) {
                "add" -> onAdd()
                "catalog" -> onCatalog?.invoke()
                "settings" -> onSettings()
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = MedLogSpacing.ScreenContentWithFab,
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {
            item(key = "filters", contentType = "filters") {
                Row(horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small)) {
                    FilterChip(
                        selected = !archived,
                        onClick = { archived = false },
                        label = { Text(stringResource(R.string.medication_active)) },
                    )
                    FilterChip(
                        selected = archived,
                        onClick = { archived = true },
                        label = { Text(stringResource(R.string.medication_archived)) },
                    )
                }
            }
            if (state.failed) {
                item(key = "error") {
                    MedicationMessageCard(
                        stringResource(R.string.medication_load_failed),
                        isError = true,
                        onRetry = onRetry,
                    )
                }
            }
            if (!state.loading && !state.failed && visible.isEmpty()) {
                item(key = "empty") {
                    MedicationMessageCard(
                        stringResource(
                            if (archived) R.string.medication_archived_empty else R.string.medication_active_empty,
                        ),
                    )
                }
            }
            items(visible, key = { it.id }, contentType = { "medication" }) { med ->
                Card(
                    onClick = { onOpen(med.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(
                        Modifier.padding(MedLogSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    ) {
                        Text(med.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${med.doseQuantity.formatDose()} ${med.doseUnit} · ${scheduleLabel(med)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        med.stock?.let { stock ->
                            Text(
                                stringResource(R.string.medication_stock_value, stock.formatDose(), med.doseUnit),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (stock <= (med.refillThreshold ?: 0.0)) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Text(
                            stringResource(
                                if (archived) R.string.medication_archived_hint else R.string.medication_manage_hint,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun scheduleLabel(medication: Medication): String {
    if (medication.isPRN) return stringResource(R.string.medication_as_needed)
    if (medication.intervalHours > 0) {
        return pluralStringResource(
            R.plurals.medication_every_hours,
            medication.intervalHours,
            medication.intervalHours,
        )
    }
    val frequency = when (medication.frequencyType) {
        "interval" -> pluralStringResource(
            R.plurals.detail_freq_interval,
            medication.frequencyInterval,
            medication.frequencyInterval,
        )
        "specific_days" -> medication.frequencyDays.split(",")
            .mapNotNull { it.toIntOrNull()?.takeIf { day -> day in 1..7 } }
            .joinToString { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        else -> stringResource(R.string.detail_freq_daily)
    }
    return "$frequency · ${medication.reminderTimes.replace(",", ", ")}"
}
