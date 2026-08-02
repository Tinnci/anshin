package com.driezy.medlog.feature.health.symptom

import app.cash.turbine.test
import com.driezy.medlog.data.repository.FakeSymptomRepository
import com.driezy.medlog.testing.MainDispatcherRule
import com.driezy.medlog.voice.VoiceInputController
import com.driezy.medlog.voice.VoiceInputEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * 身心记录页面的 UDF 契约行为守护。
 *
 * 只通过 [SymptomDiaryUiAction] 驱动 ViewModel，断言 UiState 与仓库副作用，
 * 不依赖对 Composable 源码字符串的检查。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SymptomDiaryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSymptomRepository()
    private val voiceInputController = RecordingVoiceInputController()
    private val clock = Clock.fixed(Instant.parse("2026-08-02T04:00:00Z"), ZoneId.of("Asia/Shanghai"))

    @Test
    fun `start add action opens an empty draft`() = runTest {
        val viewModel = newSubscribedViewModel()

        viewModel.uiState.test {
            assertFalse("The sheet stays closed until an action opens it.", awaitItem().showDialog)
            viewModel.onAction(SymptomDiaryUiAction.StartAdd)

            val state = awaitItem()
            assertTrue("StartAdd must open the entry sheet.", state.showDialog)
            assertEquals(null, state.draft.editingId)
            assertEquals(3, state.draft.rating)
            assertTrue(state.draft.symptoms.isEmpty())
            assertTrue(state.draft.sideEffects.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save action persists the draft and closes the sheet`() = runTest {
        val viewModel = newSubscribedViewModel()

        viewModel.onAction(SymptomDiaryUiAction.StartAdd)
        viewModel.onAction(SymptomDiaryUiAction.RatingChanged(5))
        viewModel.onAction(SymptomDiaryUiAction.ToggleSymptom("头痛"))
        viewModel.onAction(SymptomDiaryUiAction.NoteChanged("  今天好多了  "))
        viewModel.onAction(SymptomDiaryUiAction.Save)
        advanceUntilIdle()

        val stored = repository.currentLogs()
        assertEquals(1, stored.size)
        assertEquals(5, stored.first().overallRating)
        assertEquals("头痛", stored.first().symptoms)
        assertEquals("Notes are trimmed before persisting.", "今天好多了", stored.first().note)
        assertEquals(clock.millis(), stored.first().recordedAt)
        assertFalse("Saving must close the entry sheet.", viewModel.uiState.value.showDialog)
    }

    @Test
    fun `rating stays inside the supported range`() = runTest {
        val viewModel = newSubscribedViewModel()

        viewModel.onAction(SymptomDiaryUiAction.StartAdd)
        viewModel.onAction(SymptomDiaryUiAction.RatingChanged(9))
        advanceUntilIdle()
        assertEquals(5, viewModel.uiState.value.draft.rating)

        viewModel.onAction(SymptomDiaryUiAction.RatingChanged(-2))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.draft.rating)
    }

    @Test
    fun `toggling a symptom twice removes it again`() = runTest {
        val viewModel = newSubscribedViewModel()

        viewModel.onAction(SymptomDiaryUiAction.StartAdd)
        viewModel.onAction(SymptomDiaryUiAction.ToggleSymptom("恶心"))
        advanceUntilIdle()
        assertEquals(setOf("恶心"), viewModel.uiState.value.draft.symptoms)

        viewModel.onAction(SymptomDiaryUiAction.ToggleSymptom("恶心"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.draft.symptoms.isEmpty())
    }

    @Test
    fun `blank custom entries are ignored`() = runTest {
        val viewModel = newSubscribedViewModel()

        viewModel.onAction(SymptomDiaryUiAction.StartAdd)
        viewModel.onAction(SymptomDiaryUiAction.CustomSymptomChanged("   "))
        viewModel.onAction(SymptomDiaryUiAction.AddCustomSymptom)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.draft.symptoms.isEmpty())

        viewModel.onAction(SymptomDiaryUiAction.CustomSymptomChanged("  轻微眩晕  "))
        viewModel.onAction(SymptomDiaryUiAction.AddCustomSymptom)
        advanceUntilIdle()
        assertEquals(setOf("轻微眩晕"), viewModel.uiState.value.draft.symptoms)
        assertEquals("Adding clears the input field.", "", viewModel.uiState.value.draft.customSymptom)
    }

    @Test
    fun `editing an existing log updates it in place instead of inserting`() = runTest {
        val viewModel = newSubscribedViewModel()

        viewModel.onAction(SymptomDiaryUiAction.StartAdd)
        viewModel.onAction(SymptomDiaryUiAction.NoteChanged("初次记录"))
        viewModel.onAction(SymptomDiaryUiAction.Save)
        advanceUntilIdle()

        val saved = repository.currentLogs().single()
        viewModel.onAction(SymptomDiaryUiAction.StartEdit(saved))
        viewModel.onAction(SymptomDiaryUiAction.NoteChanged("修订记录"))
        viewModel.onAction(SymptomDiaryUiAction.Save)
        advanceUntilIdle()

        val logs = repository.currentLogs()
        assertEquals("Editing must not create a second entry.", 1, logs.size)
        assertEquals(saved.id, logs.single().id)
        assertEquals("修订记录", logs.single().note)
    }

    @Test
    fun `dismissing the sheet stops voice capture`() = runTest {
        val viewModel = newSubscribedViewModel()

        viewModel.onAction(SymptomDiaryUiAction.StartAdd)
        viewModel.onAction(SymptomDiaryUiAction.StartVoiceInput)
        advanceUntilIdle()
        assertEquals(1, voiceInputController.startCount)

        viewModel.onAction(SymptomDiaryUiAction.DismissDialog)
        advanceUntilIdle()

        assertTrue("Dismissing must release the microphone.", voiceInputController.stopCount >= 1)
        assertFalse(viewModel.uiState.value.showDialog)
    }

    /**
     * uiState 使用 WhileSubscribed 共享，必须保持一个活跃订阅者，
     * 否则 `.value` 会停留在 initialValue 而使断言失去意义。
     */
    private fun TestScope.newSubscribedViewModel(): SymptomDiaryViewModel {
        val viewModel = SymptomDiaryViewModel(repository, voiceInputController, clock)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    private class RecordingVoiceInputController : VoiceInputController {
        var startCount = 0
            private set
        var stopCount = 0
            private set

        override val events: SharedFlow<VoiceInputEvent> = MutableSharedFlow()

        override fun start() {
            startCount++
        }

        override fun stop() {
            stopCount++
        }
    }
}
