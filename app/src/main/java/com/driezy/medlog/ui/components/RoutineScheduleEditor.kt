package com.driezy.medlog.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.data.model.RoutineSchedule
import com.driezy.medlog.data.model.RoutineTime
import com.driezy.medlog.data.model.RoutineTimeSlot
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

internal data class RoutineTimeSlotVisual(val slot: RoutineTimeSlot, @param:StringRes val labelRes: Int, val icon: Int)

internal val routineTimeSlotVisuals = listOf(
    RoutineTimeSlotVisual(RoutineTimeSlot.WAKE, R.string.settings_routine_wake, MedLogIcons.WbSunny),
    RoutineTimeSlotVisual(
        RoutineTimeSlot.BREAKFAST,
        R.string.settings_routine_breakfast,
        MedLogIcons.BreakfastDining,
    ),
    RoutineTimeSlotVisual(RoutineTimeSlot.LUNCH, R.string.settings_routine_lunch, MedLogIcons.LunchDining),
    RoutineTimeSlotVisual(RoutineTimeSlot.DINNER, R.string.settings_routine_dinner, MedLogIcons.DinnerDining),
    RoutineTimeSlotVisual(RoutineTimeSlot.BED, R.string.settings_routine_bed, MedLogIcons.Bedtime),
)

@Composable
internal fun RoutineScheduleEditor(
    schedule: RoutineSchedule,
    onTimeChange: (RoutineTimeSlot, RoutineTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingSlot by remember { mutableStateOf<RoutineTimeSlot?>(null) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            routineTimeSlotVisuals.forEachIndexed { index, visual ->
                RoutineTimeEditorRow(
                    visual = visual,
                    time = schedule[visual.slot],
                    showConnectorAbove = index > 0,
                    showConnectorBelow = index < routineTimeSlotVisuals.lastIndex,
                    onClick = { editingSlot = visual.slot },
                )
            }
        }
    }

    editingSlot?.let { slot ->
        val visual = routineTimeSlotVisuals.first { it.slot == slot }
        val time = schedule[slot]
        RoutineTimePickerDialog(
            label = stringResource(visual.labelRes),
            time = time,
            onDismiss = { editingSlot = null },
            onConfirm = {
                onTimeChange(slot, it)
                editingSlot = null
            },
        )
    }
}

@Composable
private fun RoutineTimeEditorRow(
    visual: RoutineTimeSlotVisual,
    time: RoutineTime,
    showConnectorAbove: Boolean,
    showConnectorBelow: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(visual.labelRes)
    val formattedTime = time.format24Hour()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            if (showConnectorAbove) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(2.dp)
                        .height(14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                ) {}
            }
            if (showConnectorBelow) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(2.dp)
                        .height(14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                ) {}
            }
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MedLogIcon(
                        visual.icon,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Surface(
            onClick = onClick,
            modifier = Modifier.semantics {
                contentDescription = "$label, $formattedTime"
            },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineTimePickerDialog(
    label: String,
    time: RoutineTime,
    onDismiss: () -> Unit,
    onConfirm: (RoutineTime) -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimeInput(state = pickerState)
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onConfirm(RoutineTime(pickerState.hour, pickerState.minute))
                },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    )
}
