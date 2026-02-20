package com.example.medlog.ui.screen.drugs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── 默认浏览视图（非搜索激活状态）────────────────
            if (!uiState.isSearchActive) {
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(top = 72.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text("加载药品数据库…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    // 选了某分类后，展示该分类下的药品平铺列表
                    uiState.selectedCategory != null -> {
                        val selectedCat = uiState.selectedCategory ?: ""
                        Column(modifier = Modifier.fillMaxSize().padding(top = 72.dp)) {
                            // 面包屑标题行
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val catIcon = if (uiState.showTcm == true)
                                    Icons.Rounded.LocalFlorist else Icons.Rounded.Medication
                                Icon(
                                    catIcon, null,
                                    Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    selectedCat,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        viewModel.onCategorySelect(null)
                                        viewModel.onToggleTcm(null)
                                    },
                                ) { Text("返回分类") }
                            }
                            HorizontalDivider()
                            DrugFlatList(
                                drugs = uiState.drugs,
                                query = "",
                                onDrugSelect = onDrugSelect,
                            )
                        }
                    }
                    // 默认：西药/中成药 Tab + 分类卡片网格
                    else -> DrugCategoryBrowser(
                        westernCategories = uiState.westernCategories,
                        tcmCategories = uiState.tcmCategories,
                        onCategoryClick = { cat, isTcm ->
                            viewModel.onCategorySelect(cat)
                            viewModel.onToggleTcm(isTcm)
                        },
                        topPadding = 72.dp,
                    )
                }
            }

            // ── M3 SearchBar ─────────────────────────────────
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = uiState.query,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = {},
                        expanded = uiState.isSearchActive,
                        onExpandedChange = viewModel::onSearchActiveChange,
                        placeholder = { Text("搜索药品名称、分类、标签或拼音…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (uiState.isSearchActive) {
                                IconButton(onClick = {
                                    if (uiState.query.isNotEmpty()) viewModel.onQueryChange("")
                                    else viewModel.onSearchActiveChange(false)
                                }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "关闭搜索")
                                }
                            }
                        },
                    )
                },
                expanded = uiState.isSearchActive,
                onExpandedChange = viewModel::onSearchActiveChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (uiState.isSearchActive) 0.dp else 16.dp),
            ) {
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

                // ── 搜索结果区域 ──────────────────────────────
                when {
                    uiState.drugs.isEmpty() && uiState.query.isNotBlank() -> {
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
                                    "未找到「${uiState.query}」相关药品",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(onClick = { viewModel.onQueryChange("") }) {
                                    Text("清除搜索")
                                }
                            }
                        }
                    }
                    uiState.query.isNotBlank() || uiState.selectedCategory != null -> {
                        DrugFlatList(
                            drugs = uiState.drugs,
                            query = uiState.query,
                            onDrugSelect = {
                                onDrugSelect(it)
                                viewModel.onSearchActiveChange(false)
                            },
                        )
                    }
                    uiState.isSearchActive -> {
                        DrugGroupedList(
                            groupedDrugs = uiState.groupedDrugs,
                            onDrugSelect = {
                                onDrugSelect(it)
                                viewModel.onSearchActiveChange(false)
                            },
                        )
                    }
                }
            }
        }
    }
}

// ─── 分类浏览器（西药/中成药 Tab + 卡片网格） ───────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrugCategoryBrowser(
    westernCategories: List<Pair<String, Int>>,
    tcmCategories: List<Pair<String, Int>>,
    onCategoryClick: (String, Boolean) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("西 药" to Icons.Rounded.Medication, "中成药" to Icons.Rounded.LocalFlorist)

    Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, (label, icon) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(label) },
                    icon = { Icon(icon, null, Modifier.size(16.dp)) },
                )
            }
        }
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "tabContent",
        ) { tab ->
            val categories = if (tab == 0) westernCategories else tcmCategories
            val isTcm = tab == 1
            if (categories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(categories, key = { it.first }) { (cat, count) ->
                        CategoryGridCard(
                            category = cat,
                            count = count,
                            isTcm = isTcm,
                            onClick = { onCategoryClick(cat, isTcm) },
                        )
                    }
                    // 底部 FAB 避让
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryGridCard(
    category: String,
    count: Int,
    isTcm: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTcm)
                colorScheme.tertiaryContainer.copy(alpha = 0.6f)
            else
                colorScheme.secondaryContainer.copy(alpha = 0.6f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = if (isTcm) Icons.Rounded.LocalFlorist else Icons.Rounded.Medication,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isTcm) colorScheme.tertiary else colorScheme.secondary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$count 种药品",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── 分组列表（带首字母标题） ────────────────────────────────

@Composable
private fun DrugGroupedList(
    groupedDrugs: Map<String, List<Drug>>,
    onDrugSelect: (Drug) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    LazyColumn(contentPadding = PaddingValues(top = topPadding, bottom = 88.dp)) {
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
                Column(modifier = Modifier.animateItem()) {
                    DrugListItem(drug = drug, query = "", onClick = { onDrugSelect(drug) })
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
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
                Column(modifier = Modifier.animateItem()) {
                    DrugListItem(drug = drug, query = query, onClick = { onDrugSelect(drug) })
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
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

