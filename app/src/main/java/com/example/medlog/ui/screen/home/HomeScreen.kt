package com.example.medlog.ui.screen.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medlog.ui.components.MedicationCard
import com.example.medlog.ui.components.ProgressHeader
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddMedication: () -> Unit,
    onMedicationClick: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("今日用药", style = MaterialTheme.typography.titleLarge)
                        Text(
                            todayDateString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (uiState.items.any { !it.isTaken && !it.isSkipped }) {
                        IconButton(onClick = viewModel::takeAll) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = "全部标记已服")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddMedication,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("添加药品") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Progress summary
            item {
                ProgressHeader(
                    taken = uiState.takenCount,
                    total = uiState.totalCount,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }

            if (uiState.items.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "暂无用药计划，点击 + 添加",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(uiState.items, key = { it.medication.id }) { item ->
                    MedicationCard(
                        item = item,
                        onToggleTaken = { viewModel.toggleMedicationStatus(item) },
                        onSkip = { viewModel.skipMedication(item) },
                        onClick = { onMedicationClick(item.medication.id) },
                        modifier = Modifier.animateContentSize(),
                    )
                }
            }

            // Bottom FAB spacer
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private fun todayDateString(): String {
    val sdf = SimpleDateFormat("M月d日 EEEE", Locale.CHINA)
    return sdf.format(Date())
}
