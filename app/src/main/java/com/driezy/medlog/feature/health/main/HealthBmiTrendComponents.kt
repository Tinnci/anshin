package com.driezy.medlog.feature.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.driezy.medlog.R
import com.driezy.medlog.data.model.BmiClassification
import com.driezy.medlog.data.model.HealthRecord
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.emphasizedTypography
import com.driezy.medlog.ui.util.labelRes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun BmiCard(
    bmi: Double?,
    bmiClass: BmiClassification?,
    userHeightCm: Float,
    onUpdateHeight: (Float) -> Unit,
) {
    var showHeightDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedLogIcon(MedLogIcons.Monitor, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.health_bmi_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showHeightDialog = true }) {
                    Text(
                        if (userHeightCm >
                            0f
                        ) {
                            "${userHeightCm.toInt()} cm"
                        } else {
                            stringResource(R.string.health_height_label)
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (bmi != null && bmiClass != null) {
                val classLabel = stringResource(bmiClass.labelRes)
                Text(
                    stringResource(R.string.health_bmi_value, bmi, classLabel),
                    style = MaterialTheme.emphasizedTypography.headlineSmall,
                    color = when (bmiClass) {
                        BmiClassification.NORMAL -> MaterialTheme.colorScheme.primary
                        BmiClassification.UNDERWEIGHT -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            } else {
                Text(
                    stringResource(R.string.health_bmi_no_height),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showHeightDialog) {
        var heightInput by remember { mutableStateOf(if (userHeightCm > 0f) userHeightCm.toInt().toString() else "") }
        AlertDialog(
            onDismissRequest = { showHeightDialog = false },
            title = { Text(stringResource(R.string.health_height_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = heightInput,
                    onValueChange = { heightInput = it },
                    label = { Text(stringResource(R.string.health_height_dialog_hint)) },
                    suffix = { Text("cm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        heightInput.toFloatOrNull()?.let { onUpdateHeight(it) }
                        showHeightDialog = false
                    },
                    enabled = heightInput.toFloatOrNull()?.let { it in 50f..300f } == true,
                ) { Text(stringResource(R.string.common_action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showHeightDialog = false }) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            },
        )
    }
}

// ─── 趋势折线图 ──────────────────────────────────────────────────────────────

@Composable
internal fun HealthTrendChart(type: HealthType, points: List<HealthRecord>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val secondaryLineColor = MaterialTheme.colorScheme.tertiary
    val normalBandColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val dateFormat = remember { DateTimeFormatter.ofPattern("M/d") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedLogIcon(MedLogIcons.TrendingUp, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.health_chart_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            ) {
                val leftPadding = 48.dp.toPx()
                val bottomPadding = 28.dp.toPx()
                val chartWidth = size.width - leftPadding - 8.dp.toPx()
                val chartHeight = size.height - bottomPadding - 8.dp.toPx()

                if (points.isEmpty() || chartWidth <= 0 || chartHeight <= 0) return@Canvas

                val sorted = points.sortedBy { it.timestamp }
                val minTime = sorted.first().timestamp.toFloat()
                val maxTime = sorted.last().timestamp.toFloat()
                val timeRange = (maxTime - minTime).coerceAtLeast(1f)

                // 计算值域（主值 + 正常范围）
                val allValues = sorted.map { it.value }
                val secondaryValues = sorted.mapNotNull { it.secondaryValue }
                val dataMin = (allValues + secondaryValues).min()
                val dataMax = (allValues + secondaryValues).max()
                val rangeMin = minOf(dataMin, type.normalMin).let { it - (it * 0.05).coerceAtLeast(1.0) }
                val rangeMax = maxOf(dataMax, type.normalMax).let { it + (it * 0.05).coerceAtLeast(1.0) }
                val valueRange = (rangeMax - rangeMin).coerceAtLeast(1.0)

                fun xOf(timestamp: Long) = leftPadding + ((timestamp - minTime) / timeRange) * chartWidth
                fun yOf(value: Double) = 8.dp.toPx() + chartHeight * (1 - ((value - rangeMin) / valueRange)).toFloat()

                // 绘制正常范围带
                if (type != HealthType.WEIGHT) {
                    val normalTop = yOf(type.normalMax)
                    val normalBottom = yOf(type.normalMin)
                    drawRect(
                        color = normalBandColor,
                        topLeft = Offset(leftPadding, normalTop),
                        size = androidx.compose.ui.geometry.Size(chartWidth, normalBottom - normalTop),
                    )

                    // 正常范围虚线边界
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    drawLine(
                        gridColor,
                        Offset(leftPadding, normalTop),
                        Offset(leftPadding + chartWidth, normalTop),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect,
                    )
                    drawLine(
                        gridColor,
                        Offset(leftPadding, normalBottom),
                        Offset(leftPadding + chartWidth, normalBottom),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect,
                    )
                }

                // Y 轴标签
                val textPaint = android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = with(density) { 10.sp.toPx() }
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                for (i in 0..4) {
                    val v = rangeMin + valueRange * i / 4.0
                    val y = yOf(v)
                    drawContext.canvas.nativeCanvas.drawText(
                        if (type == HealthType.TEMPERATURE ||
                            type == HealthType.BLOOD_GLUCOSE ||
                            type == HealthType.BODY_FAT
                        ) {
                            "%.1f".format(v)
                        } else {
                            "${v.toInt()}"
                        },
                        leftPadding - 6.dp.toPx(),
                        y + 4.dp.toPx(),
                        textPaint,
                    )
                    drawLine(
                        gridColor.copy(alpha = 0.3f),
                        Offset(leftPadding, y),
                        Offset(leftPadding + chartWidth, y),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                }

                // X 轴日期标签
                val xTextPaint = android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = with(density) { 9.sp.toPx() }
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val labelCount = minOf(sorted.size, 5)
                for (i in 0 until labelCount) {
                    val idx = i * (sorted.size - 1) / (labelCount - 1).coerceAtLeast(1)
                    val x = xOf(sorted[idx].timestamp)
                    drawContext.canvas.nativeCanvas.drawText(
                        dateFormat.format(
                            Instant.ofEpochMilli(sorted[idx].timestamp).atZone(ZoneId.systemDefault()).toLocalDate(),
                        ),
                        x,
                        size.height - 4.dp.toPx(),
                        xTextPaint,
                    )
                }

                // 绘制主值折线
                val path = Path()
                sorted.forEachIndexed { i, rec ->
                    val x = xOf(rec.timestamp)
                    val y = yOf(rec.value)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))

                // 绘制数据点
                sorted.forEach { rec ->
                    drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(xOf(rec.timestamp), yOf(rec.value)))
                }

                // 血压：绘制舒张压折线
                if (type == HealthType.BLOOD_PRESSURE && secondaryValues.isNotEmpty()) {
                    val secPath = Path()
                    sorted.forEachIndexed { i, rec ->
                        val sv = rec.secondaryValue ?: return@forEachIndexed
                        val x = xOf(rec.timestamp)
                        val y = yOf(sv)
                        if (i == 0) secPath.moveTo(x, y) else secPath.lineTo(x, y)
                    }
                    drawPath(secPath, secondaryLineColor, style = Stroke(width = 2.dp.toPx()))
                    sorted.forEach { rec ->
                        rec.secondaryValue?.let {
                            drawCircle(
                                secondaryLineColor,
                                radius = 3.dp.toPx(),
                                center = Offset(xOf(rec.timestamp), yOf(it)),
                            )
                        }
                    }
                }
            }
        }
    }
}
