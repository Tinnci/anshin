package com.driezy.medlog.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reminderDestinationDispatchesNavigationCallback() {
        var destination: String? = null
        composeRule.setContent {
            MedLogTheme(dynamicColor = false) {
                SettingsHomeDashboard(
                    uiState = SettingsUiState(),
                    onNavigateToAppearanceSettings = { destination = "appearance" },
                    onNavigateToReminderSettings = { destination = "reminders" },
                    onNavigateToModuleSettings = { destination = "modules" },
                    onNavigateToIntelligenceSettings = { destination = "intelligence" },
                    onNavigateToBpx1Settings = { destination = "bpx1" },
                    onNavigateToWidgetSettings = { destination = "widgets" },
                    onNavigateToDataSettings = { destination = "data" },
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.settings_destination_reminders)).performClick()

        composeRule.runOnIdle { assertEquals("reminders", destination) }
    }

    @Test
    fun missingNotificationPermissionDispatchesRouteRequest() {
        var notificationRequests = 0
        composeRule.setContent {
            MedLogTheme(dynamicColor = false) {
                SettingsReminderPermissionAlerts(
                    canScheduleExactAlarms = true,
                    canPostNotifications = false,
                    onRequestExactAlarmPermission = {},
                    onRequestNotificationPermission = { notificationRequests++ },
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.settings_notif_perm_title)).performClick()

        composeRule.runOnIdle { assertEquals(1, notificationRequests) }
    }

    @Test
    fun dataActionRowStacksVerticallyOnCompactWidth() {
        composeRule.setContent {
            MedLogTheme(dynamicColor = false) {
                Box(modifier = Modifier.width(308.dp)) {
                    DataActionRow(
                        title = "Data Backup",
                        subtitle = "Save a local snapshot of all logs and settings.",
                        icon = MedLogIcons.CloudUpload,
                        actionLabel = "Export Backup",
                        enabled = true,
                        onClick = {},
                    )
                }
            }
        }

        val subtitleBounds = composeRule
            .onNodeWithText("Save a local snapshot of all logs and settings.")
            .getUnclippedBoundsInRoot()
        val buttonBounds = composeRule
            .onNodeWithText("Export Backup", useUnmergedTree = false)
            .getUnclippedBoundsInRoot()

        assertTrue(
            "Expected action button (${buttonBounds.top}) to stack below subtitle (${subtitleBounds.bottom}) on 308.dp compact width",
            buttonBounds.top >= subtitleBounds.bottom,
        )
    }

    private fun text(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
