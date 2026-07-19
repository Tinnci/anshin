package com.driezy.medlog.ui.screen.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.repository.HomeHeroStyle
import com.driezy.medlog.ui.theme.MedLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeHeroTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun actionStyleDisplaysAndPerformsTakeAndSkipActions() {
        assertInteractiveStyle(
            style = HomeHeroStyle.ACTION,
            styleTag = "homeHeroAction",
            expectedSecondary = "skip",
        )
    }

    @Test
    fun progressStyleDisplaysAndPerformsTakeAndDetailActions() {
        assertInteractiveStyle(
            style = HomeHeroStyle.PROGRESS,
            styleTag = "homeHeroProgress",
            expectedSecondary = "details",
        )
    }

    @Test
    fun timelineStyleDisplaysAndPerformsTakeAndSkipActions() {
        assertInteractiveStyle(
            style = HomeHeroStyle.TIMELINE,
            styleTag = "homeHeroTimeline",
            expectedSecondary = "skip",
        )

        composeRule.onNodeWithText("12:00").assertIsDisplayed()
        composeRule.onNodeWithText("18:00").assertIsDisplayed()
        composeRule.onNodeWithText("22:00").assertIsDisplayed()
    }

    @Test
    fun compactPlanRowKeepsDetailAndTakeActionsReachableWithoutAButtonStack() {
        var detailClicks = 0
        var toggleClicks = 0
        val item = testItem()
        composeRule.setContent {
            MedLogTheme(dynamicColor = false) {
                CompactMedicationPlanRow(
                    item = item,
                    onToggleTaken = { toggleClicks++ },
                    onClick = { detailClicks++ },
                )
            }
        }

        composeRule.onNodeWithTag("homeCompactPlanRow").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("homeCompactPlanToggle").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(1, detailClicks)
            assertEquals(1, toggleClicks)
        }
    }

    private fun assertInteractiveStyle(style: HomeHeroStyle, styleTag: String, expectedSecondary: String) {
        var primaryAction = ""
        var secondaryAction = ""
        val item = testItem()
        composeRule.setContent {
            MedLogTheme(dynamicColor = false) {
                HomeHero(
                    presentation = HomeHeroPresentation.from(listOf(item)),
                    style = style,
                    currentStreak = 0,
                    onTakeNext = { primaryAction = "take" },
                    onSkipNext = { secondaryAction = "skip" },
                    onViewDetails = { secondaryAction = "details" },
                    onAddMedication = { secondaryAction = "add" },
                )
            }
        }

        composeRule.onNodeWithTag(styleTag).assertIsDisplayed()
        composeRule.onNodeWithTag("homeHeroPrimary").performClick()
        composeRule.onNodeWithTag("homeHeroSecondary").performClick()

        composeRule.runOnIdle {
            assertEquals("take", primaryAction)
            assertEquals(expectedSecondary, secondaryAction)
        }
    }

    private fun testItem(): MedicationWithStatus = MedicationWithStatus(
        medication = Medication(
            id = 42L,
            name = "Aspirin",
            dose = 1.0,
            doseUnit = "tablet",
            doseQuantity = 1.0,
            reminderTimes = "08:00",
        ),
        scheduledTime = "08:00",
    )
}
