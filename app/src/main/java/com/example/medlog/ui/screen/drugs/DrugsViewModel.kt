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
    val query: String = "",
    val isLoading: Boolean = true,
    val selectedCategory: String? = null,
    val showTcm: Boolean? = null,   // null = 全部, true = 仅中药, false = 仅西药
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

    val uiState: StateFlow<DrugsUiState> = combine(
        _query.debounce(200),
        _selectedCategory,
        _showTcm,
        _isLoading,
    ) { query, category, tcm, loading ->
        DrugsUiState(
            query = query,
            selectedCategory = category,
            showTcm = tcm,
            isLoading = loading,
            drugs = filteredDrugs(query, category, tcm),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrugsUiState())

    init {
        viewModelScope.launch {
            drugRepository.getAllDrugs()   // 预热缓存
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

