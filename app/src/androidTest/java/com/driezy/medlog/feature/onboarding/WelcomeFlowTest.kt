package com.driezy.medlog.feature.onboarding

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
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

    @Test
    fun overflowingPageScrollsToItsLastControlWithDragGestures() {
        composeRule.setContent {
            val density = LocalDensity.current
            // A very large display size makes the page overflow on any test device.
            CompositionLocalProvider(LocalDensity provides Density(density.density * 2.5f, 1.5f)) {
                MedLogTheme(dynamicColor = false) {
                    WelcomeContent(
                        uiState = WelcomeUiState(pageIndex = 2),
                        notificationGranted = false,
                        onRequestNotificationPermission = {},
                        onAction = {},
                    )
                }
            }
        }
        val lastControl = composeRule.onNodeWithText(text(R.string.welcome_p4_theme_dark))
        lastControl.assertIsNotDisplayed()

        repeat(30) {
            composeRule.onRoot().performTouchInput {
                val start = Offset(centerX, height * 0.45f)
                swipe(start, start - Offset(0f, height * 0.35f), durationMillis = 200)
            }
            composeRule.waitForIdle()
        }

        lastControl.assertIsDisplayed()
    }

    private fun text(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
