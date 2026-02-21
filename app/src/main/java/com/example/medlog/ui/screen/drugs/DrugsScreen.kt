package com.example.medlog.ui.screen.drugs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medlog.R
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
                title = { Text(stringResource(R.string.drugs_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.drugs_fab_add)) },
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
            // ── M3 SearchBar ─────────────────────────────────
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = uiState.query,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = {},
                        expanded = uiState.isSearchActive,
                        onExpandedChange = viewModel::onSearchActiveChange,
                        placeholder = { Text(stringResource(R.string.drugs_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (uiState.isSearchActive) {
                                IconButton(onClick = {
                                    if (uiState.query.isNotEmpty()) viewModel.onQueryChange("")
                                    else viewModel.onSearchActiveChange(false)
                                }) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.drugs_close_search_cd))
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
                            label = { Text(stringResource(R.string.drugs_tab_all)) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.showTcm == false,
                            onClick = { viewModel.onToggleTcm(false); viewModel.onCategorySelect(null) },
                            label = { Text(stringResource(R.string.drugs_tab_western)) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.showTcm == true,
                            onClick = { viewModel.onToggleTcm(true); viewModel.onCategorySelect(null) },
                            label = { Text(stringResource(R.string.drugs_tab_tcm)) },
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
                            text = stringResource(R.string.drugs_results_count, uiState.drugs.size),
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
                                    text = stringResource(R.string.drugs_fuzzy_match),
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
                                    stringResource(R.string.drugs_not_found, uiState.query),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(onClick = { viewModel.onQueryChange("") }) {
                                    Text(stringResource(R.string.drugs_clear_search))
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

            // ── 默认浏览视图（非搜索激活状态）────────────────
            if (!uiState.isSearchActive) {
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.drugs_loading), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    // 选了某分类后展示：有二级子分类时显示二级网格，否则直接显示药品列表
                    uiState.selectedCategory != null -> {
                        val selectedCat = uiState.selectedCategory ?: ""
                        val selectedSub = uiState.selectedSubcategory  // 本地 val 避免 smart cast 问题
                        Column(modifier = Modifier.fillMaxSize()) {
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
                                // 面包屑：一级 > 二级（如果已选）
                                if (selectedSub != null) {
                                    Text(
                                        selectedCat,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        " > ",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        selectedSub,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    TextButton(onClick = { viewModel.onSubcategorySelect(null) }) {
                                        Text(stringResource(R.string.drugs_back_subcategory))
                                    }
                                } else {
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
                                    ) { Text(stringResource(R.string.drugs_back_category)) }
                                }
                            }
                            HorizontalDivider()
                            // 有二级子分类 & 尚未选二级 → 显示二级卡片网格
                            if (uiState.subcategories.isNotEmpty() && uiState.selectedSubcategory == null) {
                                SubcategoryGrid(
                                    subcategories = uiState.subcategories,
                                    onSubcategoryClick = { viewModel.onSubcategorySelect(it) },
                                )
                            } else {
                                // 选了二级或该一级无二级子类 → 按首字母分组展示
                                DrugGroupedList(
                                    groupedDrugs = uiState.groupedDrugs,
                                    onDrugSelect = onDrugSelect,
                                    topPadding = 4.dp,
                                )
                            }
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
                    )
                }
            }

        }
    }
}
// ─── 二级子分类网格 ──────────────────────────────────────────────────────────

@Composable
private fun SubcategoryGrid(
    subcategories: List<Pair<String, Int>>,
    onSubcategoryClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(subcategories, key = { it.first }) { (sub, count) ->
            Card(
                onClick = { onSubcategoryClick(sub) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.drugs_count_suffix, count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
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
    val tabs = listOf(stringResource(R.string.drugs_tab_western_br) to Icons.Rounded.Medication, stringResource(R.string.drugs_tab_tcm) to Icons.Rounded.LocalFlorist)

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
                    text = stringResource(R.string.drugs_drug_count, count),
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

    // 多路径药品：提取所有唯一的一级分类作为 badge 列表
    val extraCategories = if (drug.allPaths.size > 1) {
        drug.allPaths
            .mapNotNull { it.split(" > ").firstOrNull()?.trim() }
            .distinct()
            .filter { it != drug.category }
    } else emptyList()

    val tcmBadge = stringResource(R.string.drugs_tcm_badge)
    ListItem(
        headlineContent = { Text(drug.name) },
        leadingContent = {
            Icon(
                imageVector = if (drug.isTcm) Icons.Rounded.LocalFlorist else Icons.Rounded.Medication,
                contentDescription = null,
                modifier = androidx.compose.ui.Modifier.size(20.dp),
                tint = if (drug.isTcm)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.secondary,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = buildString {
                        append(drug.category)
                        if (drug.isTcm) append(tcmBadge)
                        if (drug.tags.isNotEmpty()) append("  ·  ${drug.tags.take(2).joinToString(", ")}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 多系统归类 badge 行（如"神经系统 + 消化道及代谢"）
                if (extraCategories.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        extraCategories.forEach { cat ->
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        cat,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.widthIn(max = 140.dp),
                            )
                        }
                    }
                }
                // 语义/模糊匹配原因提示
                if (tagMatchHint != null) {
                    Text(
                        text = stringResource(R.string.drugs_tag_hint, tagMatchHint),
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
                    label = { Text(stringResource(R.string.drugs_compound), style = MaterialTheme.typography.labelSmall) },
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

