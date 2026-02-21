package com.example.medlog.ui.screen.drugs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.Drug
import com.example.medlog.data.repository.DrugRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val showTcm: Boolean? = null,   // null = 全部, true = 仅中药, false = 仅西药
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

@OptIn(FlowPreview::class)
@HiltViewModel
class DrugsViewModel @Inject constructor(
    private val drugRepository: DrugRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _selectedSubcategory = MutableStateFlow<String?>(null)
    private val _showTcm = MutableStateFlow<Boolean?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _categories = MutableStateFlow<List<String>>(emptyList())
    private val _isSearchActive = MutableStateFlow(false)
    private val _westernCategories = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    private val _tcmCategories = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    /** 所有药品缓存（init 后填充，用于计算子分类） */
    private var allDrugsCache: List<Drug> = emptyList()

    // ── 中间合并：将"过滤参数"收敛成一个 Flow，确保子分类变更也能触发重算 ──────────
    private val filterParams = combine(
        _query.debounce(200),
        _selectedCategory,
        _selectedSubcategory,
        _showTcm,
    ) { q, cat, sub, tcm -> arrayOf<Any?>(q, cat, sub, tcm) }

    val uiState: StateFlow<DrugsUiState> = combine(
        filterParams,
        combine(_isLoading, _categories) { loading, cats -> loading to cats },
        _isSearchActive,
        combine(_westernCategories, _tcmCategories) { w, t -> w to t },
    ) { fp, (loading, cats), active, (western, tcm) ->
        val query    = fp[0] as String
        val category = fp[1] as String?
        val subcat   = fp[2] as String?
        val showTcm  = fp[3] as Boolean?

        val (filtered, exactCount) = rankedDrugs(query, category, showTcm, subcat)
        // 计算二级子分类列表（仅当选了一级分类、未选二级时显示）
        val subcats = if (category != null && subcat == null && query.isBlank()) {
            allDrugsCache
                .filter { it.category == category }
                .flatMap { drug ->
                    (listOf(drug.fullPath) + drug.allPaths)
                        .filter { it.startsWith(category) }
                        .mapNotNull { path ->
                            path.split(" > ").map { it.trim() }.let { parts ->
                                if (parts.size >= 2) parts[1] else null
                            }
                        }
                }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { it.key to it.value }
        } else emptyList()

        DrugsUiState(
            query = query,
            selectedCategory = category,
            selectedSubcategory = subcat,
            subcategories = subcats,
            showTcm = showTcm,
            isLoading = loading,
            isSearchActive = active,
            drugs = filtered,
            groupedDrugs = if (query.isBlank()) {
                filtered.groupBy { drug ->
                    val initial = drug.initial.ifBlank {
                        drug.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                    }
                    if (initial.first().isLetter()) initial.first().uppercaseChar().toString() else "#"
                }.toSortedMap()
            } else emptyMap(),
            categories = cats,
            westernCategories = western,
            tcmCategories = tcm,
            hasFuzzyResults = query.isNotBlank() && filtered.isNotEmpty() && exactCount < filtered.size,
            exactMatchCount = exactCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrugsUiState())

    init {
        viewModelScope.launch {
            val allDrugs = drugRepository.getAllDrugs()
            _categories.value = allDrugs.map { it.category }.distinct().sorted()
            // 按拼音/字典序排列分类卡片（Collator 支持中文拼音排序）
            val collator = Collator.getInstance(Locale.CHINESE)
            val western = allDrugs.filter { !it.isTcm }
                .groupBy { it.category }
                .entries
                .sortedWith(compareBy(collator) { it.key })
                .map { it.key to it.value.size }
            val tcm = allDrugs.filter { it.isTcm }
                .groupBy { it.category }
                .entries
                .sortedWith(compareBy(collator) { it.key })
                .map { it.key to it.value.size }
            _westernCategories.value = western
            _tcmCategories.value = tcm
            allDrugsCache = allDrugs
            _isLoading.value = false
        }
    }

    fun onQueryChange(q: String) { _query.value = q }
    fun onCategorySelect(cat: String?) {
        _selectedCategory.value = cat
        _selectedSubcategory.value = null  // 切换一级时清空二级
    }
    fun onSubcategorySelect(subcat: String?) { _selectedSubcategory.value = subcat }
    fun onToggleTcm(tcm: Boolean?) { _showTcm.value = tcm }
    fun onSearchActiveChange(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            _query.value = ""
            _selectedCategory.value = null
            _selectedSubcategory.value = null
            _showTcm.value = null
        }
    }

    /**
     * 执行模糊+语义排名搜索，并应用分类/中西药过滤。
     * @return Pair(排序后结果, 精确匹配数)
     */
    private suspend fun rankedDrugs(
        query: String,
        category: String?,
        tcm: Boolean?,
        subcategory: String? = null,
    ): Pair<List<Drug>, Int> {
        var result = drugRepository.searchDrugsRanked(query)
        if (category != null) result = result.filter { it.category == category }
        if (tcm != null) result = result.filter { it.isTcm == tcm }
        // 二级分类过滤：检查路径第二段是否匹配
        if (subcategory != null) {
            result = result.filter { drug ->
                (listOf(drug.fullPath) + drug.allPaths).any { path ->
                    path.split(" > ").map { it.trim() }.getOrNull(1) == subcategory
                }
            }
        }
        val exact = if (query.isNotBlank())
            result.count { it.nameLower.contains(query.lowercase()) || it.categoryLower.contains(query.lowercase()) }
        else result.size
        return result to exact
    }
}

