package com.driezy.medlog.feature.medications.catalog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.data.model.Drug
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.util.DrugCategoryLabels
import com.driezy.medlog.ui.util.medicationNameLanguage

@Composable
internal fun SubcategoryGrid(
    subcategories: List<Pair<String, Int>>,
    categoryLabel: (String) -> String,
    onSubcategoryClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(MedLogSpacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(subcategories, key = { it.first }) { (sub, count) ->
            Card(
                onClick = { onSubcategoryClick(sub) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = MedLogSpacing.Medium, vertical = MedLogSpacing.Small),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = categoryLabel(sub),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.drugs_count_suffix, count, count),
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DrugCategoryBrowser(
    westernCategories: List<Pair<String, Int>>,
    tcmCategories: List<Pair<String, Int>>,
    includeTcm: Boolean,
    categoryLabels: DrugCategoryLabels,
    onCategoryClick: (String, Boolean) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.drugs_tab_western_br) to MedLogIcons.Medication,
        stringResource(R.string.drugs_tab_tcm) to MedLogIcons.LocalFlorist,
    )
    val motionScheme = MaterialTheme.motionScheme

    Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
        if (includeTcm) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .padding(start = MedLogSpacing.Medium, top = MedLogSpacing.Small, end = MedLogSpacing.Medium)
                    .clip(MaterialTheme.shapes.large),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                divider = {},
            ) {
                tabs.forEachIndexed { index, (label, icon) ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) },
                        icon = { MedLogIcon(icon, null, Modifier.size(16.dp)) },
                    )
                }
            }
        }
        AnimatedContent(
            targetState = if (includeTcm) selectedTab else 0,
            transitionSpec = {
                fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                    fadeOut(motionScheme.fastEffectsSpec())
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
                    LoadingIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(MedLogSpacing.Medium),
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(categories, key = { it.first }) { (cat, count) ->
                        CategoryGridCard(
                            category = categoryLabels.label(cat, isTcm),
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
internal fun CategoryGridCard(category: String, count: Int, isTcm: Boolean, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isTcm) {
                colorScheme.tertiaryContainer.copy(alpha = 0.6f)
            } else {
                colorScheme.secondaryContainer.copy(alpha = 0.6f)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MedLogSpacing.Medium),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            MedLogIcon(
                icon = if (isTcm) MedLogIcons.LocalFlorist else MedLogIcons.Medication,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isTcm) colorScheme.tertiary else colorScheme.secondary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline)) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.drugs_drug_count, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── 分组列表（带首字母标题） ────────────────────────────────

@Composable
internal fun DrugGroupedList(
    groupedDrugs: Map<String, List<Drug>>,
    categoryLabels: DrugCategoryLabels,
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
                        modifier = Modifier.padding(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Small),
                    )
                }
            }
            items(drugs, key = { it.name + it.fullPath }) { drug ->
                Column(modifier = Modifier.animateItem()) {
                    DrugListItem(
                        drug = drug,
                        query = "",
                        categoryLabels = categoryLabels,
                        onClick = { onDrugSelect(drug) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = MedLogSpacing.Large))
                }
            }
        }
    }
}

// ─── 平铺列表（搜索结果） ─────────────────────────────────────

@Composable
internal fun DrugFlatList(
    drugs: List<Drug>,
    query: String,
    categoryLabels: DrugCategoryLabels,
    onDrugSelect: (Drug) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        items(drugs, key = { it.name + it.fullPath }) { drug ->
            Column(modifier = Modifier.animateItem()) {
                DrugListItem(
                    drug = drug,
                    query = query,
                    categoryLabels = categoryLabels,
                    onClick = { onDrugSelect(drug) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = MedLogSpacing.Large))
            }
        }
    }
}

// ─── 单个药品 Item ────────────────────────────────────────────

@Composable
private fun DrugListItem(drug: Drug, query: String, categoryLabels: DrugCategoryLabels, onClick: () -> Unit) {
    val language = medicationNameLanguage()
    val localizedName = drug.localizedName(language)
    val aliasMatchHint = drug.alternateNameMatching(query.trim(), language)

    // 多路径药品：提取所有唯一的一级分类作为 badge 列表
    val extraCategories = if (drug.allPaths.size > 1) {
        drug.allPaths
            .mapNotNull { it.split(" > ").firstOrNull()?.trim() }
            .distinct()
            .filter { it != drug.category }
    } else {
        emptyList()
    }

    val tcmBadge = stringResource(R.string.drugs_tcm_badge)
    ListItem(
        headlineContent = { Text(localizedName) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            MedLogIcon(
                icon = if (drug.isTcm) MedLogIcons.LocalFlorist else MedLogIcons.Medication,
                contentDescription = null,
                modifier = androidx.compose.ui.Modifier.size(20.dp),
                tint = if (drug.isTcm) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny)) {
                Text(
                    text = buildString {
                        if (!drug.hasLocalizedName(language)) {
                            append(stringResource(R.string.drugs_original_name))
                            append(" · ")
                        }
                        append(categoryLabels.label(drug.category, drug.isTcm))
                        if (drug.isTcm) append(tcmBadge)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 多系统归类 badge 行（如"神经系统 + 消化道及代谢"）
                if (extraCategories.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        extraCategories.forEach { cat ->
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        categoryLabels.label(cat, drug.isTcm),
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
                // 别名匹配原因提示
                if (aliasMatchHint != null) {
                    Text(
                        text = stringResource(R.string.drugs_tag_hint, aliasMatchHint),
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
                    label = {
                        Text(stringResource(R.string.drugs_compound), style = MaterialTheme.typography.labelSmall)
                    },
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
