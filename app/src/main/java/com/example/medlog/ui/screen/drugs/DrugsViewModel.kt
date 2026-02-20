package com.example.medlog.ui.screen.drugs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.Drug
import com.example.medlog.data.repository.DrugRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
)

@OptIn(FlowPreview::class)
@HiltViewModel
class DrugsViewModel @Inject constructor(
    private val drugRepository: DrugRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _showTcm = MutableStateFlow<Boolean?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _categories = MutableStateFlow<List<String>>(emptyList())
    private val _isSearchActive = MutableStateFlow(false)

    val uiState: StateFlow<DrugsUiState> = combine(
        combine(
            _query.debounce(200),
            _selectedCategory,
            _showTcm,
            _isLoading,
            _categories,
        ) { query, category, tcm, loading, cats ->
            val (filtered, exactCount) = rankedDrugs(query, category, tcm)
            DrugsUiState(
                query = query,
                selectedCategory = category,
                showTcm = tcm,
                isLoading = loading,
                drugs = filtered,
                groupedDrugs = if (query.isBlank() && category == null) {
                    filtered.groupBy { drug ->
                        val initial = drug.initial.ifBlank {
                            drug.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                        }
                        if (initial.first().isLetter()) initial.first().uppercaseChar().toString() else "#"
                    }.toSortedMap()
                } else emptyMap(),
                categories = cats,
                hasFuzzyResults = query.isNotBlank() && filtered.isNotEmpty() && exactCount < filtered.size,
                exactMatchCount = exactCount,
            )
        },
        _isSearchActive,
    ) { state, active ->
        state.copy(isSearchActive = active)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrugsUiState())

    init {
        viewModelScope.launch {
            val allDrugs = drugRepository.getAllDrugs()
            _categories.value = allDrugs.map { it.category }.distinct().sorted()
            _isLoading.value = false
        }
    }

    fun onQueryChange(q: String) { _query.value = q }
    fun onCategorySelect(cat: String?) { _selectedCategory.value = cat }
    fun onToggleTcm(tcm: Boolean?) { _showTcm.value = tcm }
    fun onSearchActiveChange(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            _query.value = ""
            _selectedCategory.value = null
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
    ): Pair<List<Drug>, Int> {
        var result = drugRepository.searchDrugsRanked(query)
        if (category != null) result = result.filter { it.category == category }
        if (tcm != null) result = result.filter { it.isTcm == tcm }
        val exact = if (query.isNotBlank())
            result.count { it.nameLower.contains(query.lowercase()) || it.categoryLower.contains(query.lowercase()) }
        else result.size
        return result to exact
    }
}

