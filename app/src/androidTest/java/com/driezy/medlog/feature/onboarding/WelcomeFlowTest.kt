package com.driezy.medlog.feature.onboarding

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.driezy.medlog.R
import com.driezy.medlog.ui.theme.MedLogTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun finalPageDispatchesSubmitThroughUdfContract() {
        val actions = mutableListOf<WelcomeUiAction>()
        composeRule.setContent {
            MedLogTheme(dynamicColor = false) {
                WelcomeContent(
                    uiState = WelcomeUiState(pageIndex = 5),
                    notificationGranted = false,
                    onRequestNotificationPermission = {},
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.welcome_btn_start)).performClick()

        composeRule.runOnIdle { assertTrue(actions.contains(WelcomeUiAction.Submit)) }
    }

    @Test
    fun notificationPageDelegatesPermissionRequestToRoute() {
        var permissionRequests = 0
        composeRule.setContent {
            MedLogTheme(dynamicColor = false) {
                WelcomeContent(
                    uiState = WelcomeUiState(pageIndex = 4),
                    notificationGranted = false,
                    onRequestNotificationPermission = { permissionRequests++ },
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.welcome_notif_grant_btn)).performClick()

        composeRule.runOnIdle { assertTrue(permissionRequests == 1) }
    }

    private fun text(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
