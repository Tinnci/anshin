package com.driezy.medlog.feature.medications.catalog

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.driezy.medlog.data.local.DRUG_PATH_SEPARATOR
import com.driezy.medlog.data.local.DrugCatalogAssets
import com.driezy.medlog.data.local.DrugTaxonomy
import com.driezy.medlog.data.model.Drug
import com.driezy.medlog.data.repository.DrugRepository
import com.driezy.medlog.data.repository.FeaturePreferences
import com.driezy.medlog.ui.BaseViewModel
import com.driezy.medlog.ui.util.DrugCategoryLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

data class DrugsUiState(
    val drugs: List<Drug> = emptyList(),
    /** 当前查询结果按首字母分组（仅搜索为空且无分类过滤时） */
    val groupedDrugs: Map<String, List<Drug>> = emptyMap(),
    val query: String = "",
    val isLoading: Boolean = true,
    val selectedCategory: String? = null,
    val includeTcm: Boolean = false,
    val showTcm: Boolean? = null, // null = 全部, true = 仅中药, false = 仅西药
    /** 所有可用分类列表 */
    val categories: List<String> = emptyList(),
    /** 是否包含模糊/语义匹配结果（query 非空时有效） */
    val hasFuzzyResults: Boolean = false,
    /** 精确匹配结果数（名称包含 query 的条数） */
    val exactMatchCount: Int = 0,
    /** SearchBar 展开状态 */
    val isSearchActive: Boolean = false,
    /** 西药一级分类及药品数量（用于分类卡片网格） */
    val westernCategories: List<Pair<String, Int>> = emptyList(),
    /** 中成药一级分类及药品数量（用于分类卡片网格） */
    val tcmCategories: List<Pair<String, Int>> = emptyList(),
    /** 当前选中的二级子分类（null = 未选中，显示二级网格） */
    val selectedSubcategory: String? = null,
    /** 当前一级分类下的二级子分类及药品数量 */
    val subcategories: List<Pair<String, Int>> = emptyList(),
)

sealed interface DrugsUiAction {
    data class QueryChanged(val value: String) : DrugsUiAction
    data class LanguageChanged(val language: String) : DrugsUiAction
    data class CategorySelected(val value: String?) : DrugsUiAction
    data class SubcategorySelected(val value: String?) : DrugsUiAction
    data class TcmFilterChanged(val value: Boolean?) : DrugsUiAction
    data class SearchActiveChanged(val active: Boolean) : DrugsUiAction
}

@OptIn(FlowPreview::class)
@HiltViewModel
class DrugsViewModel @Inject constructor(
    private val drugRepository: DrugRepository,
    preferences: FeaturePreferences,
    @param:ApplicationContext private val context: Context,
) : BaseViewModel() {
    private data class Filters(
        val category: String? = null,
        val subcategory: String? = null,
        val tcm: Boolean? = null,
        val searchActive: Boolean = false,
    )

    private data class Catalog(val drugs: List<Drug>, val taxonomy: DrugTaxonomy?)

    private val query = MutableStateFlow("")
    val searchText: StateFlow<String> = query.asStateFlow()
    private val filters = MutableStateFlow(Filters())
    private val catalog = MutableStateFlow<Catalog?>(null)
    private val language = MutableStateFlow("en")

    val uiState: StateFlow<DrugsUiState> = combine(
        query.debounce(200),
        filters,
        catalog,
        language,
        preferences.features.map { it.showTcmInCatalog }.distinctUntilChanged(),
    ) { text, filter, loaded, locale, includeTcm ->
        if (loaded == null) return@combine DrugsUiState(includeTcm = includeTcm)
        val labels = DrugCategoryLabels(loaded.taxonomy, locale)
        val collator = Collator.getInstance(Locale.forLanguageTag(locale))
        val visible = loaded.drugs.filter { includeTcm || !it.isTcm }
        val visibleCategories = visible.map { it.category }.distinct()
        val category = filter.category?.takeIf { it in visibleCategories }
        val subcategory = filter.subcategory?.takeIf { category != null }
        val tcm = filter.tcm?.takeIf { includeTcm }
        val scoped = visible.filter { drug ->
            (category == null || drug.category == category) &&
                (tcm == null || drug.isTcm == tcm) &&
                (subcategory == null || subcategory in drug.subcategories())
        }
        val matching = rankCatalogDrugs(scoped, text, locale, labels, collator)
        val results = matching.map { it.drug }
        val exact = matching.count { it.directMatch }

        val categoriesByType = visible.groupBy { it.isTcm }.mapValues { (isTcm, drugs) ->
            drugs.groupingBy { it.category }.eachCount().toList()
                .sortedWith(compareBy(collator) { labels.label(it.first, isTcm) })
        }
        val subcategories = if (category != null && subcategory == null && text.isBlank()) {
            scoped.flatMap(Drug::subcategories).groupingBy { it }.eachCount().toList()
                .sortedWith(compareBy(collator) { labels.label("$category$DRUG_PATH_SEPARATOR${it.first}", tcm) })
        } else {
            emptyList()
        }
        DrugsUiState(
            query = text,
            selectedCategory = category,
            selectedSubcategory = subcategory,
            subcategories = subcategories,
            includeTcm = includeTcm,
            showTcm = tcm,
            isLoading = false,
            isSearchActive = filter.searchActive,
            drugs = results,
            groupedDrugs = if (text.isBlank()) results.groupBy { it.displayInitial(locale) } else emptyMap(),
            categories = visibleCategories.sortedWith(compareBy(collator) { labels.label(it) }),
            westernCategories = categoriesByType[false].orEmpty(),
            tcmCategories = categoriesByType[true].orEmpty(),
            hasFuzzyResults = text.isNotBlank() && exact < results.size,
            exactMatchCount = exact,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrugsUiState())

    init {
        viewModelScope.launch {
            val drugs = drugRepository.getAllDrugs()
            val taxonomy = withContext(Dispatchers.IO) {
                runCatching { DrugCatalogAssets.taxonomy(context) }.getOrNull()
            }
            catalog.value = Catalog(drugs, taxonomy)
        }
    }

    fun onAction(action: DrugsUiAction) {
        when (action) {
            is DrugsUiAction.QueryChanged -> query.value = action.value
            is DrugsUiAction.LanguageChanged -> language.value = action.language
            is DrugsUiAction.CategorySelected -> filters.update {
                it.copy(category = action.value, subcategory = null)
            }
            is DrugsUiAction.SubcategorySelected -> filters.update { it.copy(subcategory = action.value) }
            is DrugsUiAction.TcmFilterChanged -> filters.update { it.copy(tcm = action.value) }
            is DrugsUiAction.SearchActiveChanged -> {
                if (!action.active) query.value = ""
                filters.update { if (action.active) it.copy(searchActive = true) else Filters() }
            }
        }
    }
}

private fun Drug.subcategories(): List<String> = allPaths.ifEmpty { listOf(fullPath) }.mapNotNull { path ->
    val parts = path.split(DRUG_PATH_SEPARATOR, limit = 3)
    parts.getOrNull(1)?.takeIf { parts.size == 3 && parts.first() == category }
}.distinct()

internal fun Drug.displayInitial(language: String): String {
    if (language == "zh") return initial
    if (!hasLocalizedName(language)) return "#"
    val first = localizedName(language).firstOrNull()?.takeIf(Char::isLetter) ?: return "#"
    return if (language == "ja" && first in 'ァ'..'ヶ') {
        (first - ('ァ' - 'ぁ')).toString()
    } else {
        first.uppercaseChar().toString()
    }
}

internal data class RankedCatalogDrug(val drug: Drug, val directMatch: Boolean)

internal fun rankCatalogDrugs(
    drugs: List<Drug>,
    query: String,
    language: String,
    labels: DrugCategoryLabels,
    collator: Collator,
): List<RankedCatalogDrug> {
    val byLocalizedName = compareByDescending<Drug> { it.hasLocalizedName(language) }
        .thenBy(collator) { it.localizedName(language) }
        .thenBy { it.name }
    val text = query.trim().lowercase()
    if (text.isEmpty()) {
        return drugs.sortedWith(byLocalizedName).map { RankedCatalogDrug(it, true) }
    }

    data class Match(val drug: Drug, val nameRank: Int, val score: Float, val directMatch: Boolean)

    val categoryMatches = mutableMapOf<Pair<String, Boolean>, Boolean>()
    return drugs.mapNotNull { drug ->
        val nameRank = drug.nameSuggestionRank(text, language)
        val localizedCategory = drug.allPaths.ifEmpty { listOf(drug.fullPath) }.any { path ->
            val categoryPath = path.substringBeforeLast(DRUG_PATH_SEPARATOR, "")
            categoryMatches.getOrPut(categoryPath to drug.isTcm) {
                labels.matchesCategoryPath(categoryPath, drug.isTcm, text)
            }
        }
        val directCategory = localizedCategory || drug.categoryLower.contains(text)
        val score = maxOf(drug.relevanceScore(text), if (directCategory) 0.55f else 0f)
        if (nameRank == 0 && score == 0f) null else Match(drug, nameRank, score, nameRank > 0 || directCategory)
    }.sortedWith(
        compareByDescending<Match> { it.nameRank }.thenByDescending { it.score }
            .thenComparator { first, second -> byLocalizedName.compare(first.drug, second.drug) },
    ).map { RankedCatalogDrug(it.drug, it.directMatch) }
}
