package com.driezy.medlog.feature.medications.catalog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.data.model.Drug
import com.driezy.medlog.ui.components.MedLogScreenScaffold
import com.driezy.medlog.ui.components.ScreenChromeState
import com.driezy.medlog.ui.components.ScreenFab
import com.driezy.medlog.ui.components.TopBarAction
import com.driezy.medlog.ui.components.TopBarActionPriority
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DrugsScreen(
    onAddCustomDrug: () -> Unit,
    onOpenSettings: () -> Unit,
    onDrugSelect: (Drug) -> Unit,
    viewModel: DrugsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DrugsContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onAddCustomDrug = onAddCustomDrug,
        onOpenSettings = onOpenSettings,
        onDrugSelect = onDrugSelect,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DrugsContent(
    uiState: DrugsUiState,
    onAction: (DrugsUiAction) -> Unit,
    onAddCustomDrug: () -> Unit,
    onOpenSettings: () -> Unit,
    onDrugSelect: (Drug) -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme

    MedLogScreenScaffold(
        title = { Text(stringResource(R.string.drugs_title)) },
        actions = listOf(
            TopBarAction(
                id = "settings",
                label = stringResource(R.string.settings_action_open),
                icon = MedLogIcons.Settings,
                priority = TopBarActionPriority.Secondary,
            ),
        ),
        chromeState = ScreenChromeState(
            fab = ScreenFab(
                id = "add",
                label = stringResource(R.string.drugs_fab_add),
                icon = MedLogIcons.Add,
            ),
        ),
        onChromeAction = { id ->
            when (id) {
                "settings" -> onOpenSettings()
                "add" -> onAddCustomDrug()
            }
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
                        onQueryChange = { onAction(DrugsUiAction.QueryChanged(it)) },
                        onSearch = {},
                        expanded = uiState.isSearchActive,
                        onExpandedChange = { onAction(DrugsUiAction.SearchActiveChanged(it)) },
                        placeholder = { Text(stringResource(R.string.drugs_search_placeholder)) },
                        leadingIcon = { MedLogIcon(MedLogIcons.Search, null) },
                        trailingIcon = {
                            if (uiState.isSearchActive) {
                                IconButton(onClick = {
                                    if (uiState.query.isNotEmpty()) {
                                        onAction(DrugsUiAction.QueryChanged(""))
                                    } else {
                                        onAction(DrugsUiAction.SearchActiveChanged(false))
                                    }
                                }) {
                                    MedLogIcon(
                                        MedLogIcons.Close,
                                        contentDescription = stringResource(R.string.drugs_close_search_cd),
                                    )
                                }
                            }
                        },
                    )
                },
                expanded = uiState.isSearchActive,
                onExpandedChange = { onAction(DrugsUiAction.SearchActiveChanged(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (uiState.isSearchActive) 0.dp else MedLogSpacing.Large),
            ) {
                // ── 西药 / 中药 筛选 + 分类 Chip ───────────────
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    contentPadding = PaddingValues(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Tiny),
                ) {
                    item {
                        FilterChip(
                            selected = uiState.showTcm == null && uiState.selectedCategory == null,
                            onClick = {
                                onAction(DrugsUiAction.TcmFilterChanged(null))
                                onAction(DrugsUiAction.CategorySelected(null))
                            },
                            label = { Text(stringResource(R.string.drugs_tab_all)) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.showTcm == false,
                            onClick = {
                                onAction(DrugsUiAction.TcmFilterChanged(false))
                                onAction(DrugsUiAction.CategorySelected(null))
                            },
                            label = { Text(stringResource(R.string.drugs_tab_western)) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.showTcm == true,
                            onClick = {
                                onAction(DrugsUiAction.TcmFilterChanged(true))
                                onAction(DrugsUiAction.CategorySelected(null))
                            },
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
                        items(uiState.categories.take(12), key = { it }) { cat ->
                            FilterChip(
                                selected = uiState.selectedCategory == cat,
                                onClick = {
                                    onAction(
                                        DrugsUiAction.CategorySelected(
                                            if (uiState.selectedCategory == cat) null else cat,
                                        ),
                                    )
                                    onAction(DrugsUiAction.TcmFilterChanged(null))
                                },
                                label = { Text(cat) },
                            )
                        }
                    }
                }

                // ── 搜索结果计数 + 模糊匹配提示 ────────────────
                AnimatedVisibility(
                    visible = uiState.query.isNotBlank(),
                    enter = expandVertically(motionScheme.defaultSpatialSpec()) +
                        fadeIn(motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(motionScheme.fastSpatialSpec()) +
                        fadeOut(motionScheme.fastEffectsSpec()),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Hairline),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.drugs_results_count,
                                uiState.drugs.size,
                                uiState.drugs.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (uiState.hasFuzzyResults) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline),
                            ) {
                                MedLogIcon(
                                    MedLogIcons.AutoAwesome,
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
                                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                            ) {
                                MedLogIcon(
                                    MedLogIcons.SearchOffDisplay48,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    stringResource(R.string.drugs_not_found, uiState.query),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(onClick = { onAction(DrugsUiAction.QueryChanged("")) }) {
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
                                onAction(DrugsUiAction.SearchActiveChanged(false))
                            },
                        )
                    }
                    uiState.isSearchActive -> {
                        DrugGroupedList(
                            groupedDrugs = uiState.groupedDrugs,
                            onDrugSelect = {
                                onDrugSelect(it)
                                onAction(DrugsUiAction.SearchActiveChanged(false))
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
                                LoadingIndicator()
                                Spacer(Modifier.height(MedLogSpacing.Small))
                                Text(
                                    stringResource(R.string.drugs_loading),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    // 选了某分类后展示：有二级子分类时显示二级网格，否则直接显示药品列表
                    uiState.selectedCategory != null -> {
                        val selectedCat = uiState.selectedCategory ?: ""
                        val selectedSub = uiState.selectedSubcategory // 本地 val 避免 smart cast 问题
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 面包屑标题行
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Small),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                            ) {
                                val catIcon = if (uiState.showTcm == true) {
                                    MedLogIcons.LocalFlorist
                                } else {
                                    MedLogIcons.Medication
                                }
                                MedLogIcon(
                                    catIcon,
                                    null,
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
                                    TextButton(onClick = { onAction(DrugsUiAction.SubcategorySelected(null)) }) {
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
                                            onAction(DrugsUiAction.CategorySelected(null))
                                            onAction(DrugsUiAction.TcmFilterChanged(null))
                                        },
                                    ) { Text(stringResource(R.string.drugs_back_category)) }
                                }
                            }
                            HorizontalDivider()
                            // 有二级子分类 & 尚未选二级 → 显示二级卡片网格
                            if (uiState.subcategories.isNotEmpty() && uiState.selectedSubcategory == null) {
                                SubcategoryGrid(
                                    subcategories = uiState.subcategories,
                                    onSubcategoryClick = { onAction(DrugsUiAction.SubcategorySelected(it)) },
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
                            onAction(DrugsUiAction.CategorySelected(cat))
                            onAction(DrugsUiAction.TcmFilterChanged(isTcm))
                        },
                    )
                }
            }
        }
    }
}
