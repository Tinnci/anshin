package com.example.medlog.ui.screen.drugs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrugsScreen(
    onAddCustomDrug: () -> Unit,
    onMedicationClick: (Long) -> Unit,
    viewModel: DrugsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("药品管理") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCustomDrug) {
                Icon(Icons.Rounded.Add, contentDescription = "添加")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("搜索药品…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )

            if (uiState.medications.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "暂无药品记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(uiState.medications, key = { it.id }) { med ->
                        ListItem(
                            headlineContent = { Text(med.name) },
                            supportingContent = {
                                Text("${med.dose} ${med.doseUnit}  ·  ${med.category}")
                            },
                            modifier = Modifier.clickable { onMedicationClick(med.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
