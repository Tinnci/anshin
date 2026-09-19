package com.driezy.medlog.feature.medications.editor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.driezy.medlog.R
import com.driezy.medlog.data.local.TransactionRunner
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.repository.*
import com.driezy.medlog.voice.VoiceInputController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AddMedicationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-09-19T00:00:00Z"), ZoneOffset.UTC)
    private val transactions = object : TransactionRunner {
        override suspend fun <R> withTransaction(block: suspend () -> R): R = block()
    }

    @Before fun setup() = Dispatchers.setMain(dispatcher)

    @After fun cleanup() = Dispatchers.resetMain()

    private fun vm(
        repository: MedicationRepository,
        handle: SavedStateHandle = SavedStateHandle(),
    ): AddMedicationViewModel {
        val context = mock<Context> { on { getString(R.string.default_dose_unit) } doReturn "片" }
        val prefs = mock<UserPreferencesRepository> { on { settingsFlow } doReturn flowOf(SettingsPreferences()) }
        val voice = mock<VoiceInputController> {
            on { events } doReturn
                MutableSharedFlow<com.driezy.medlog.voice.VoiceInputEvent>()
        }
        val drugs = mock<DrugRepository> {
            onBlocking { searchDrugsRanked(any()) } doReturn emptyList()
        }
        return AddMedicationViewModel(repository, mock(), prefs, drugs, voice, context, clock, handle, transactions)
    }

    @Test fun `late existing medication hydration preserves user draft`() = runTest {
        val ready = CompletableDeferred<Medication>()
        val delayed = object : MedicationRepository by FakeMedicationRepository() {
            override suspend fun getMedicationById(id: Long): Medication = ready.await()
        }
        val model = vm(delayed)
        model.loadExisting(1)
        runCurrent()
        model.onAction(AddMedicationUiAction.NotesChanged("用户刚输入的草稿"))
        ready.complete(Medication(id = 1, name = "旧值", dose = 1.0, doseUnit = "片", notes = "旧备注"))
        advanceUntilIdle()
        assertEquals("用户刚输入的草稿", model.uiState.value.notes)
        assertEquals("旧值", model.uiState.value.name)
        assertFalse(model.uiState.value.isLoading)
    }

    @Test fun `restored editor draft is not replaced by repeated load`() = runTest {
        val repository = FakeMedicationRepository()
        val id = repository.addMedication(Medication(name = "药品", dose = 1.0, doseUnit = "片"))
        val handle = SavedStateHandle()
        val first = vm(repository, handle)
        first.loadExisting(id)
        advanceUntilIdle()
        first.onAction(AddMedicationUiAction.NotesChanged("草稿"))
        val restored = vm(repository, handle)
        restored.loadExisting(id)
        advanceUntilIdle()
        assertEquals("草稿", restored.uiState.value.notes)
        assertTrue(restored.isDirty.value)
    }

    @Test fun `double submit saves once and uses the click snapshot`() = runTest {
        val repository = FakeMedicationRepository()
        val model = vm(repository)
        model.onAction(AddMedicationUiAction.NameChanged("药品"))
        model.onAction(AddMedicationUiAction.Save(null))
        model.onAction(AddMedicationUiAction.Save(null))
        model.onAction(AddMedicationUiAction.NameChanged("稍后输入"))
        assertTrue(model.uiState.value.isSaving)
        advanceUntilIdle()
        assertEquals("药品", repository.getActiveOnce().single().name)
    }

    @Test fun `editing notes preserves concurrent inventory and original metadata`() = runTest {
        val repository = FakeMedicationRepository()
        val id = repository.addMedication(
            Medication(name = "药品", dose = 1.0, doseUnit = "片", stock = 10.0, createdAt = 123, isArchived = true),
        )
        val model = vm(repository)
        model.loadExisting(id)
        advanceUntilIdle()
        model.onAction(AddMedicationUiAction.NotesChanged("说明"))
        repository.updateStock(id, 9.0)
        model.save(id)
        advanceUntilIdle()
        val saved = repository.getMedicationById(id)!!
        assertEquals(9.0, saved.stock!!, 0.0)
        assertEquals(123L, saved.createdAt)
        assertTrue(saved.isArchived)
    }

    @Test fun `wizard and save reject invalid drafts with visible errors`() = runTest {
        val repository = FakeMedicationRepository()
        val model = vm(repository)
        model.onAction(AddMedicationUiAction.NextStep)
        assertEquals(0, model.uiState.value.wizardStep)
        assertEquals(R.string.error_name_required, model.uiState.value.errorRes)
        model.onAction(AddMedicationUiAction.NameChanged("药品"))
        model.onAction(AddMedicationUiAction.StockChanged("NaN"))
        model.save(null)
        advanceUntilIdle()
        assertEquals(R.string.error_stock_invalid, model.uiState.value.errorRes)
        assertTrue(repository.getActiveOnce().isEmpty())
        assertEquals(
            R.string.error_date_order,
            AddMedicationUiState(name = "药", doseUnit = "片", startDate = 86_400_000, endDate = 0).validationError(),
        )
    }
}
