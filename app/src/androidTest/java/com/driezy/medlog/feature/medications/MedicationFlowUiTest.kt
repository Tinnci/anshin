package com.driezy.medlog.feature.medications

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.driezy.medlog.R
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.feature.medications.list.MyMedicationsContent
import com.driezy.medlog.feature.medications.list.MyMedicationsState
import com.driezy.medlog.ui.components.MedicationAdherenceCard
import com.driezy.medlog.ui.components.MedicationMessageCard
import com.driezy.medlog.ui.theme.MedLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Text and semantics checks only: these tests never capture or inspect images. */
@RunWith(AndroidJUnit4::class)
class MedicationFlowUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun emptyAdherenceIsNeutralAtLargeFontScale() {
        compose.setContent {
            MedLogTheme(dynamicColor = false) {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.8f),
                ) {
                    MedicationAdherenceCard(taken = 0, partial = 0, total = 0)
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.adherence_no_due)).assertIsDisplayed()
        compose.onNodeWithText("0%").assertDoesNotExist()
    }

    @Test fun errorFeedbackProvidesOneWorkingRetryAction() {
        var retries = 0
        compose.setContent {
            MedLogTheme(dynamicColor = false) {
                MedicationMessageCard("Load failed", isError = true, onRetry = { retries++ })
            }
        }
        compose.onNodeWithText("Load failed").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.action_retry)).assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun ownMedicationsAndCatalogHaveSeparateActionsAndStoppedItemsRemainReachable() {
        var opened = 0L
        var catalog = 0
        val active = Medication(id = 1, name = "Active medicine", dose = 1.0, doseUnit = "tablet")
        val stopped = active.copy(id = 2, name = "Stopped medicine", isArchived = true)
        compose.setContent {
            MedLogTheme(dynamicColor = false) {
                MyMedicationsContent(MyMedicationsState(listOf(active, stopped), loading = false), {
                }, { opened = it }, { catalog++ }, {}, {})
            }
        }
        compose.onNodeWithText(active.name).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1L, opened) }
        compose.onNodeWithText(stopped.name).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.medication_archived)).performClick()
        compose.onNodeWithText(stopped.name).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2L, opened) }
        compose.onNodeWithContentDescription(context.getString(R.string.medication_catalog)).performClick()
        compose.runOnIdle { assertEquals(1, catalog) }
    }

    @Test fun disablingCatalogDoesNotHideMyMedicationManagement() {
        val active = Medication(id = 1, name = "My medicine", dose = 1.0, doseUnit = "tablet")
        compose.setContent {
            MedLogTheme(dynamicColor = false) {
                MyMedicationsContent(MyMedicationsState(listOf(active), loading = false), {}, {}, null, {}, {})
            }
        }
        compose.onNodeWithText(active.name).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.medication_catalog)).assertDoesNotExist()
    }
}
