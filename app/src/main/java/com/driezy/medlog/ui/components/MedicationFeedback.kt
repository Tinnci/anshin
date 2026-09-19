package com.driezy.medlog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ui.theme.MedLogSpacing

/** Shared empty/error feedback; errors remain readable and expose a consistent retry action. */
@Composable
fun MedicationMessageCard(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = if (isError) colors.errorContainer else colors.surfaceContainerLow,
        contentColor = if (isError) colors.onErrorContainer else colors.onSurfaceVariant,
    ) {
        Column(Modifier.padding(MedLogSpacing.Large), verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

/** History and details display identical quantities, colours and empty-state semantics. */
@Composable
fun MedicationAdherenceCard(taken: Int, partial: Int, total: Int, modifier: Modifier = Modifier) {
    val rate = if (total == 0) 0f else (taken.toFloat() / total).coerceIn(0f, 1f)
    val colors = MaterialTheme.colorScheme
    val tint = when {
        total == 0 -> colors.onSurfaceVariant
        rate >= 0.9f -> colors.tertiary
        rate >= 0.6f -> colors.secondary
        else -> colors.error
    }
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        Column(
            Modifier.padding(MedLogSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {
            Text(stringResource(R.string.detail_adherence_title), style = MaterialTheme.typography.titleSmall)
            if (total == 0) {
                Text(stringResource(R.string.adherence_no_due), style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Large),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { rate },
                            modifier = Modifier.size(72.dp),
                            color = tint,
                            trackColor = colors.surfaceContainerHighest,
                        )
                        Text("${(rate * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, color = tint)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small)) {
                        AdherenceCount(stringResource(R.string.medication_taken), taken)
                        AdherenceCount(stringResource(R.string.history_legend_partial), partial)
                        AdherenceCount(
                            stringResource(R.string.detail_missed_skipped),
                            (total - taken - partial).coerceAtLeast(0),
                        )
                        AdherenceCount(stringResource(R.string.adherence_due_count), total)
                    }
                }
            }
            Text(
                stringResource(R.string.adherence_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdherenceCount(label: String, count: Int) {
    Text(
        "$label · ${pluralStringResource(R.plurals.detail_count_times, count, count)}",
        style = MaterialTheme.typography.bodyMedium,
    )
}
