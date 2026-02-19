package com.example.medlog.ui.screen.drugs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medlog.data.model.Drug

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrugsScreen(
    onAddCustomDrug: () -> Unit,
    onDrugSelect: (Drug) -> Unit,
    viewModel: DrugsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("药品数据库") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCustomDrug) {
                Icon(Icons.Rounded.Add, contentDescription = "自定义添加")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── 搜索框 ──────────────────────────────────────
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索药品名称或分类…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )

            // ── 西药 / 中药 筛选 Chip ───────────────────────
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                item {
                    FilterChip(
                        selected = uiState.showTcm == null,
                        onClick = { viewModel.onToggleTcm(null) },
                        label = { Text("全部") },
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.showTcm == false,
                        onClick = { viewModel.onToggleTcm(false) },
                        label = { Text("西药") },
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.showTcm == true,
                        onClick = { viewModel.onToggleTcm(true) },
                        label = { Text("中成药") },
                    )
                }
            }

            // ── 内容区域 ─────────────────────────────────────
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("加载药品数据库…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                uiState.drugs.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (uiState.query.isNotEmpty()) "未找到「${uiState.query}」"
                            else "暂无药品数据",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> DrugList(drugs = uiState.drugs, onDrugSelect = onDrugSelect)
            }
        }
    }
}

@Composable
private fun DrugList(
    drugs: List<Drug>,
    onDrugSelect: (Drug) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        items(drugs, key = { it.name + it.fullPath }) { drug ->
            DrugListItem(drug = drug, onClick = { onDrugSelect(drug) })
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        }
    }
}

@Composable
private fun DrugListItem(drug: Drug, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(drug.name) },
        supportingContent = {
            Text(
                text = drug.category + if (drug.isTcm) "  ·  中成药" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            if (drug.isCompound) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("复方", style = MaterialTheme.typography.labelSmall) },
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

