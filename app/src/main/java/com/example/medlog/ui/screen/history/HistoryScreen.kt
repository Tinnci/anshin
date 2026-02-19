package com.example.medlog.ui.screen.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.MedicationLog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("服药历史") })
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (uiState.groupedLogs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("暂无服药记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            uiState.groupedLogs.forEach { (dateStr, logs) ->
                item(key = dateStr) {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(logs, key = { it.id }) { log ->
                    HistoryLogItem(log = log)
                }
            }
        }
    }
}

@Composable
fun HistoryLogItem(log: MedicationLog) {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = when (log.status) {
                LogStatus.TAKEN   -> Icons.Rounded.CheckCircle
                LogStatus.SKIPPED -> Icons.Rounded.SkipNext
                LogStatus.MISSED  -> Icons.Rounded.Cancel
            },
            contentDescription = null,
            tint = when (log.status) {
                LogStatus.TAKEN   -> MaterialTheme.colorScheme.tertiary
                LogStatus.SKIPPED -> MaterialTheme.colorScheme.outline
                LogStatus.MISSED  -> MaterialTheme.colorScheme.error
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "药品 ID: ${log.medicationId}",
                style = MaterialTheme.typography.bodyMedium,
            )
            val takenTime = log.actualTakenTimeMs?.let { "实际：${timeFmt.format(Date(it))}" } ?: ""
            if (takenTime.isNotEmpty()) {
                Text(
                    text = takenTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = when (log.status) {
                LogStatus.TAKEN   -> "已服用"
                LogStatus.SKIPPED -> "已跳过"
                LogStatus.MISSED  -> "漏服"
            },
            style = MaterialTheme.typography.labelMedium,
            color = when (log.status) {
                LogStatus.TAKEN   -> MaterialTheme.colorScheme.tertiary
                LogStatus.SKIPPED -> MaterialTheme.colorScheme.outline
                LogStatus.MISSED  -> MaterialTheme.colorScheme.error
            },
        )
    }
}
