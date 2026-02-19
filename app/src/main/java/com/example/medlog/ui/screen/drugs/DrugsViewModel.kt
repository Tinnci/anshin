package com.example.medlog.ui.screen.drugs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.Medication
import com.example.medlog.data.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DrugsUiState(
    val medications: List<Medication> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
)

@HiltViewModel
class DrugsViewModel @Inject constructor(
    private val repository: MedicationRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _uiState = MutableStateFlow(DrugsUiState())
    val uiState: StateFlow<DrugsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getActiveMedications(),
                _query,
            ) { meds, query ->
                val filtered = if (query.isBlank()) meds
                else meds.filter { it.name.contains(query, ignoreCase = true) }
                DrugsUiState(medications = filtered, query = query, isLoading = false)
            }.collect { _uiState.value = it }
        }
    }

    fun onQueryChange(q: String) { _query.value = q }
}
