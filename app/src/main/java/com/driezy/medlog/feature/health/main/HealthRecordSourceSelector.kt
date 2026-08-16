package com.driezy.medlog.feature.health

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

enum class HealthRecordEntrySource {
    MANUAL,
    OCR,
    BPX1,
}

@Composable
internal fun HealthRecordSourceSelector(
    selected: HealthRecordEntrySource,
    onSelect: (HealthRecordEntrySource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == HealthRecordEntrySource.MANUAL,
            onClick = { onSelect(HealthRecordEntrySource.MANUAL) },
            label = { Text(stringResource(R.string.health_source_manual)) },
            leadingIcon = { MedLogIcon(MedLogIcons.EditNote, null, Modifier.size(16.dp)) },
        )
        FilterChip(
            selected = selected == HealthRecordEntrySource.OCR,
            onClick = { onSelect(HealthRecordEntrySource.OCR) },
            label = { Text(stringResource(R.string.ocr_health_scan_chip)) },
            leadingIcon = { MedLogIcon(MedLogIcons.CameraAlt, null, Modifier.size(16.dp)) },
        )
        FilterChip(
            selected = selected == HealthRecordEntrySource.BPX1,
            onClick = { onSelect(HealthRecordEntrySource.BPX1) },
            label = { Text(stringResource(R.string.bpx1_sync_chip)) },
            leadingIcon = { MedLogIcon(MedLogIcons.MonitorHeart, null, Modifier.size(16.dp)) },
        )
    }
}
