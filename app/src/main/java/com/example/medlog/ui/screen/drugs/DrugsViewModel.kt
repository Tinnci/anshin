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
    /** 当前查询结果按首字母分组（仅搜索为空时） */
    val groupedDrugs: Map<String, List<Drug>> = emptyMap(),
    val query: String = "",
    val isLoading: Boolean = true,
    val selectedCategory: String? = null,
    val showTcm: Boolean? = null,   // null = 全部, true = 仅中药, false = 仅西药
    /** 所有可用分类列表 */
    val categories: List<String> = emptyList(),
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

    val uiState: StateFlow<DrugsUiState> = combine(
        _query.debounce(200),
        _selectedCategory,
        _showTcm,
        _isLoading,
        _categories,
    ) { query, category, tcm, loading, cats ->
        val filtered = filteredDrugs(query, category, tcm)
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
        )
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

    private suspend fun filteredDrugs(
        query: String,
        category: String?,
        tcm: Boolean?,
    ): List<Drug> {
        var result = drugRepository.searchDrugs(query)
        if (category != null) result = result.filter { it.category == category }
        if (tcm != null) result = result.filter { it.isTcm == tcm }
        return result
    }
}
