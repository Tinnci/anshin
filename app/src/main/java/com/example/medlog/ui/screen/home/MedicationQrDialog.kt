package com.example.medlog.ui.screen.home

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.medlog.R
import com.example.medlog.data.model.TimePeriod
import com.example.medlog.domain.PlanExport
import com.example.medlog.domain.PlanExportCodec
import com.example.medlog.ui.utils.generateQrBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── 今日用药 QR 码分享对话框 ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicationQrDialog(
    items: List<MedicationWithStatus>,
    onDismiss: () -> Unit,
    generateExportUri: () -> String?,
    onQrScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val shareIntentTitle = stringResource(R.string.home_share_intent_title)
    val shareChooserTitle = stringResource(R.string.home_share_chooser)
    val takenCount = remember(items) { items.count { it.isTaken } }
    val totalCount = items.size

    var selectedTab by remember { mutableIntStateOf(0) }
    var showScanner by remember { mutableStateOf(false) }

    // Pre-compute period label strings at composition scope
    val periodStrings: Map<String, String> = TimePeriod.entries.associate { tp ->
        tp.key to stringResource(tp.labelRes)
    }

    // ── Tab 0：今日打卡文本 ────────────────────────────────────────────────────
    val todayQrText = remember(items, periodStrings) {
        buildString {
            appendLine("Anshin ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())} [$takenCount/$totalCount]")
            items.forEach { item ->
                val status = when {
                    item.isTaken   -> "✓"
                    item.isSkipped -> "-"
                    else           -> "○"
                }
                val med = item.medication
                val dose = if (med.doseQuantity == med.doseQuantity.toLong().toDouble())
                    "${med.doseQuantity.toLong()}${med.doseUnit}"
                else "%.1f${med.doseUnit}".format(med.doseQuantity)
                val period = periodStrings[med.timePeriod] ?: ""
                appendLine("$status ${med.name} $dose $period")
            }
        }.trimEnd()
    }

    // ── Tab 1：计划导出 URI ────────────────────────────────────────────────────
    val exportUri = remember(selectedTab) {
        if (selectedTab == 1) generateExportUri() else null
    }

    // QR 位图（两个 tab 各自用对应文本）
    val canShowQr = exportUri != null &&
        PlanExportCodec.canDisplayAsQr(exportUri)

    val todayQrBitmap by produceState<android.graphics.Bitmap?>(null, todayQrText) {
        value = withContext(Dispatchers.Default) { generateQrBitmap(todayQrText) }
    }
    val exportQrBitmap by produceState<android.graphics.Bitmap?>(null, exportUri, canShowQr) {
        if (canShowQr && exportUri != null)
            value = withContext(Dispatchers.Default) { generateQrBitmap(exportUri) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_qr_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Tab 切换 ────────────────────────────────────────
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.qr_tab_today)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.qr_tab_export)) },
                    )
                }

                // ── Tab 0 内容 ───────────────────────────────────────
                if (selectedTab == 0) {
                    Text(
                        stringResource(R.string.home_qr_taken_count, takenCount, totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    QrImageBox(bitmap = todayQrBitmap)
                    Text(
                        stringResource(R.string.home_qr_instruction),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                // ── Tab 1 内容 ───────────────────────────────────────
                if (selectedTab == 1) {
                    // 药品数量摘要
                    Text(
                        stringResource(R.string.qr_export_med_count, items.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (exportUri == null) {
                        if (items.isEmpty()) {
                            Text(
                                stringResource(R.string.qr_export_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(40.dp))
                        }
                    } else if (canShowQr) {
                        QrImageBox(bitmap = exportQrBitmap)
                        Text(
                            stringResource(R.string.home_qr_instruction),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        // 数据过大提示 — 使用 Card 展示更友好
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    stringResource(R.string.qr_export_too_large),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                // ── 扫码导入按钮（两个 tab 均可用）───────────────────
                OutlinedButton(
                    onClick = { showScanner = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.qr_scan_import))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val shareText = if (selectedTab == 1 && exportUri != null) exportUri else todayQrText
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    putExtra(Intent.EXTRA_TITLE, shareIntentTitle)
                }
                context.startActivity(Intent.createChooser(intent, shareChooserTitle))
            }) {
                Icon(Icons.Rounded.IosShare, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.home_qr_share_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_close)) }
        },
    )

    // ── 扫描器全屏覆盖层 ───────────────────────────────────────────────────────
    if (showScanner) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
            ),
        ) {
            com.example.medlog.ui.qr.QrScannerPage(
                onResult = { raw ->
                    showScanner = false
                    onQrScanned(raw)
                    onDismiss()
                },
                onBack = { showScanner = false },
            )
        }
    }
}

// ── QR 图像盒子（两个 tab 共用）──────────────────────────────────────────────

@Composable
internal fun QrImageBox(bitmap: android.graphics.Bitmap?) {
    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                painter = BitmapPainter(bitmap.asImageBitmap()),
                contentDescription = stringResource(R.string.home_qr_cd),
                modifier = Modifier.size(200.dp),
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
        }
    }
}

// ── 导入预览对话框 ──────────────────────────────────────────────────────────

@Composable
internal fun ImportPreviewDialog(
    plan: PlanExport,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qr_import_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.qr_import_medication_count, plan.meds.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(plan.meds) { med ->
                        Text(
                            "• ${med.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = { onMerge(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.qr_import_mode_merge))
                }
                OutlinedButton(
                    onClick = { onReplace(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.qr_import_mode_replace))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_close)) }
        },
    )
}
