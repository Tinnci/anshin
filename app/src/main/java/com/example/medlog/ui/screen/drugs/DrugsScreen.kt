package com.example.medlog.ui.screen.drugs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("药品数据库") },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("自定义添加") },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = onAddCustomDrug,
            )
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
                placeholder = { Text("搜索药品名称、分类、标签或拼音…") },
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

            // ── 搜索结果计数 + 模糊匹配提示 ────────────────
            AnimatedVisibility(
                visible = uiState.query.isNotBlank(),
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "找到 ${uiState.drugs.size} 条结果",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (uiState.hasFuzzyResults) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                            Text(
                                text = "含模糊/语义匹配",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }

            // ── 西药 / 中药 筛选 + 分类 Chip ───────────────
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                item {
                    FilterChip(
                        selected = uiState.showTcm == null && uiState.selectedCategory == null,
                        onClick = { viewModel.onToggleTcm(null); viewModel.onCategorySelect(null) },
                        label = { Text("全部") },
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.showTcm == false,
                        onClick = { viewModel.onToggleTcm(false); viewModel.onCategorySelect(null) },
                        label = { Text("西药") },
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.showTcm == true,
                        onClick = { viewModel.onToggleTcm(true); viewModel.onCategorySelect(null) },
                        label = { Text("中成药") },
                    )
                }
                if (uiState.categories.isNotEmpty()) {
                    item {
                        VerticalDivider(
                            modifier = Modifier
                                .height(32.dp)
                                .padding(horizontal = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    items(uiState.categories.take(12)) { cat ->
                        FilterChip(
                            selected = uiState.selectedCategory == cat,
                            onClick = {
                                viewModel.onCategorySelect(if (uiState.selectedCategory == cat) null else cat)
                                viewModel.onToggleTcm(null)
                            },
                            label = { Text(cat) },
                        )
                    }
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (uiState.query.isNotEmpty()) "未找到「${uiState.query}」相关药品"
                                else "暂无药品数据",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (uiState.query.isNotEmpty()) {
                                OutlinedButton(onClick = { viewModel.onQueryChange("") }) {
                                    Text("清除搜索")
                                }
                            }
                        }
                    }
                }
                // 有搜索或分类筛选时，显示平铺列表
                uiState.query.isNotBlank() || uiState.selectedCategory != null -> {
                    DrugFlatList(
                        drugs = uiState.drugs,
                        query = uiState.query,
                        onDrugSelect = onDrugSelect,
                    )
                }
                // 默认显示首字母分组列表
                else -> {
                    DrugGroupedList(
                        groupedDrugs = uiState.groupedDrugs,
                        onDrugSelect = onDrugSelect,
                    )
                }
            }
        }
    }
}

// ─── 分组列表（带首字母标题） ────────────────────────────────

@Composable
private fun DrugGroupedList(
    groupedDrugs: Map<String, List<Drug>>,
    onDrugSelect: (Drug) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        groupedDrugs.forEach { (letter, drugs) ->
            stickyHeader(key = "header_$letter") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            items(drugs, key = { it.name + it.fullPath }) { drug ->
                DrugListItem(drug = drug, query = "", onClick = { onDrugSelect(drug) })
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

// ─── 平铺列表（搜索结果） ─────────────────────────────────────

@Composable
private fun DrugFlatList(
    drugs: List<Drug>,
    query: String,
    onDrugSelect: (Drug) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        items(drugs, key = { it.name + it.fullPath }) { drug ->
            DrugListItem(drug = drug, query = query, onClick = { onDrugSelect(drug) })
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        }
    }
}

// ─── 单个药品 Item ────────────────────────────────────────────

@Composable
private fun DrugListItem(drug: Drug, query: String, onClick: () -> Unit) {
    // 标签匹配提示：当名称不包含 query 但标签匹配时显示
    val tagMatchHint = if (query.isNotBlank() && !drug.nameLower.contains(query.lowercase())) {
        drug.tags.firstOrNull { it.lowercase().contains(query.lowercase()) }
    } else null

    ListItem(
        headlineContent = { Text(drug.name) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = buildString {
                        append(drug.category)
                        if (drug.isTcm) append("  ·  中成药")
                        if (drug.tags.isNotEmpty()) append("  ·  ${drug.tags.take(2).joinToString(", ")}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 语义/模糊匹配原因提示
                if (tagMatchHint != null) {
                    Text(
                        text = "标签：$tagMatchHint",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
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

