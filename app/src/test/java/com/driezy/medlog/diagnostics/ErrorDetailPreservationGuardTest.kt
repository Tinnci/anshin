package com.driezy.medlog.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ErrorDetailPreservationGuardTest {
    @Test
    fun `voice input detail reaches ui state and presenters`() {
        val voiceContract = source("voice/VoiceInputController.kt")
        val addMedication = source("ui/screen/addmedication/AddMedicationViewModel.kt")
        val health = source("ui/screen/health/HealthViewModel.kt")
        val symptom = source("ui/screen/symptom/SymptomDiaryViewModel.kt")
        val controls = source("ui/components/VoiceInputControls.kt")

        assertTrue(voiceContract.contains("val detail: String = \"\""))
        assertTrue(addMedication.contains("detail = event.detail"))
        assertTrue(health.contains("detail = event.detail"))
        assertTrue(symptom.contains("detail = event.detail"))
        assertTrue(controls.contains("detail.isNotBlank()"))
    }

    @Test
    fun `cross cutting error handlers log before falling back`() {
        val baseViewModel = source("ui/BaseViewModel.kt")
        val keyStore = source("ai/AndroidKeystoreAiApiKeyStore.kt")
        val notificationHelper = source("notification/NotificationHelper.kt")
        val medLogWidget = source("widget/MedLogWidget.kt")
        val nextDoseWidget = source("widget/NextDoseWidget.kt")
        val streakWidget = source("widget/StreakWidget.kt")

        assertTrue(baseViewModel.contains("Log.e(TAG, \"Unhandled ViewModel coroutine failure\", e)"))
        assertTrue(keyStore.contains("Log.w(TAG, \"Failed to decrypt API key for"))
        assertTrue(notificationHelper.contains("Log.w(TAG, \"Failed to read notification preferences\", it)"))
        assertTrue(medLogWidget.contains("Log.w(TAG, \"Failed to read widget preferences\", it)"))
        assertTrue(nextDoseWidget.contains("Log.w(TAG, \"Failed to read widget preferences\", it)"))
        assertTrue(streakWidget.contains("Log.w(TAG, \"Failed to read widget preferences\", it)"))
    }

    @Test
    fun `doubao socket and protocol errors are not reduced to empty messages`() {
        val protocol = source("voice/doubao/DoubaoAsrProtocol.kt")
        val webSocket = source("voice/doubao/DoubaoAsrWebSocketClient.kt")

        assertTrue(protocol.contains("diagnosticMessage"))
        assertFalse(webSocket.contains("errorMessage = t.message.orEmpty()"))
        assertTrue(webSocket.contains("HTTP \${response?.code}"))
        assertTrue(webSocket.contains("onClosed(webSocket: WebSocket, code: Int, reason: String)"))
    }

    private fun source(relativePath: String): String = File("src/main/java/com/driezy/medlog/$relativePath").readText()
}
