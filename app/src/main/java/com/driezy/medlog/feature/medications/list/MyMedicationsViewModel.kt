package com.driezy.medlog.feature.medications.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

internal data class MyMedicationsState(
    val medications: List<Medication> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)

@HiltViewModel
class MyMedicationsViewModel @Inject constructor(repository: MedicationRepository) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val state = refresh.flatMapLatest {
        repository.getAllMedications().map { MyMedicationsState(it, loading = false) }
            .catch { emit(MyMedicationsState(loading = false, failed = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyMedicationsState())

    fun retry() {
        refresh.update { it + 1 }
    }
}
