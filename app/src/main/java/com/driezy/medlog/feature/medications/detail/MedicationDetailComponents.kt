package com.driezy.medlog.feature.medications.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.model.MedicationLog
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.util.formatDose
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 剂型 key → 本地化标签 */

// ─── 库存快捷操作卡 ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StockCard(
    stock: Double,
    refillThreshold: Double?,
    unit: String,
    doseQuantity: Double,
    onAdjustStock: (Double) -> Unit,
    enabled: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isLow = refillThreshold != null && stock <= refillThreshold
    val stockColor = if (isLow) colorScheme.error else colorScheme.tertiary

    val stockDisplay = stock.formatDose()
    val doseDisplay = doseQuantity.formatDose()

    // 常用补药预设量
    val presets = listOf("+10" to 10.0, "+30" to 30.0, "+60" to 60.0, "+90" to 90.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {
            // ── 标题行 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MedLogIcon(MedLogIcons.Inventory, null, tint = stockColor, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.detail_stock_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.primary,
                    )
                }
                Text(
                    "$stockDisplay $unit",
                    style = MaterialTheme.typography.titleSmall,
                    color = stockColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // ── 状态提示 ──
            if (refillThreshold != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (isLow) colorScheme.errorContainer else colorScheme.tertiaryContainer).copy(
                        alpha = 0.7f,
                    ),
                ) {
                    Text(
                        if (isLow) {
                            stringResource(R.string.detail_stock_low_warning, refillThreshold.toString(), unit)
                        } else {
                            stringResource(R.string.detail_stock_ok, refillThreshold.toString(), unit)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLow) colorScheme.onErrorContainer else colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── 快捷调整：M3 Expressive ButtonGroup ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.detail_stock_adjust_hint, doseDisplay, unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedIconButton(
                        enabled = enabled,
                        onClick = { onAdjustStock(-doseQuantity) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        MedLogIcon(
                            MedLogIcons.Remove,
                            stringResource(R.string.detail_stock_decrease_cd),
                            Modifier.size(18.dp),
                        )
                    }
                    FilledIconButton(
                        onClick = { onAdjustStock(+doseQuantity) },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colorScheme.primaryContainer,
                            contentColor = colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        MedLogIcon(
                            MedLogIcons.Add,
                            stringResource(R.string.detail_stock_increase_cd),
                            Modifier.size(18.dp),
                        )
                    }
                }
            }

            // ── 批量补入预设 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.detail_batch_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                )
                presets.forEach { (label, amount) ->
                    SuggestionChip(
                        onClick = { onAdjustStock(amount) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ─── 日志行 ───────────────────────────────────────────────────

@Composable
internal fun DetailLogRow(log: MedicationLog, zone: ZoneId = ZoneId.systemDefault()) {
    val logItemFmt = stringResource(R.string.date_format_log_item)
    val dateFmt = remember(logItemFmt) { DateTimeFormatter.ofPattern(logItemFmt) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val colorScheme = MaterialTheme.colorScheme
    val statusColor = when (log.status) {
        LogStatus.TAKEN -> colorScheme.tertiary
        LogStatus.SKIPPED -> colorScheme.outline
        LogStatus.MISSED -> colorScheme.error
        LogStatus.PARTIAL -> colorScheme.secondary
        LogStatus.PENDING -> colorScheme.outline
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 状态点
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor),
        )
        Column(Modifier.weight(1f)) {
            Text(
                dateFmt.format(Instant.ofEpochMilli(log.scheduledTimeMs).atZone(zone)),
                style = MaterialTheme.typography.bodyMedium,
            )
            log.actualTakenTimeMs?.let {
                if (log.status == LogStatus.TAKEN) {
                    Text(
                        stringResource(
                            R.string.detail_log_actual_time,
                            timeFmt.format(Instant.ofEpochMilli(it).atZone(zone)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (log.notes.isNotBlank()) {
                Text(
                    log.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = statusColor.copy(alpha = 0.12f),
        ) {
            Text(
                text = when (log.status) {
                    LogStatus.TAKEN -> stringResource(R.string.medication_taken)
                    LogStatus.SKIPPED -> stringResource(R.string.medication_skipped)
                    LogStatus.MISSED -> stringResource(R.string.medication_missed)
                    LogStatus.PARTIAL -> stringResource(R.string.history_legend_partial)
                    LogStatus.PENDING -> stringResource(R.string.history_pending)
                },
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
}

// ─── 工具 Composable ──────────────────────────────────────────

@Composable
internal fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
